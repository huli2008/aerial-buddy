package com.aerial.buddy;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Phone side of the intercom: 16 kHz mono PCM straight into a TCP socket to the TV helper. */
public final class Intercom {

    public static final int SAMPLE_RATE = 16000;
    public static final int PORT = 8766;
    public static final int BEACON_PORT = 8767;

    public interface Cb {
        void ready();

        void failed(String message);
    }

    private Socket socket;
    private AudioRecord record;
    private Thread pump;
    private volatile boolean running;

    public boolean isActive() {
        return running;
    }

    /**
     * Listens for the TV helper's UDP beacon ("AERIAL-INTERCOM/1 &lt;port&gt; &lt;model&gt;").
     * Returns the port it advertises, or -1 if nothing showed up in time.
     */
    public static int discover(String ip, int timeoutMs) {
        java.net.DatagramSocket s = null;
        try {
            s = new java.net.DatagramSocket(BEACON_PORT);
            s.setBroadcast(true);
            s.setSoTimeout(Math.max(200, timeoutMs));
            byte[] buf = new byte[256];
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                java.net.DatagramPacket p = new java.net.DatagramPacket(buf, buf.length);
                try {
                    s.receive(p);
                } catch (java.net.SocketTimeoutException te) {
                    continue;
                }
                if (ip != null && !ip.equals(p.getAddress().getHostAddress())) continue;
                String text = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                if (!text.startsWith("AERIAL-INTERCOM/1")) continue;
                String[] parts = text.split(" ");
                if (parts.length >= 2) {
                    try {
                        return Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (s != null) s.close();
        }
        return -1;
    }

    /**
     * Connects and starts capturing on a worker thread — Android forbids sockets on the UI thread,
     * which is what made every press fail with "连不上" before.
     */
    public void startAsync(final String ip, final int port, final Context ctx, final Cb cb) {
        if (running) {
            cb.ready();
            return;
        }
        if (ctx != null && ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cb.failed("没有麦克风权限：先允许录音权限再按住说话");
            return;
        }
        new Thread(() -> {
            String error = connect(ip, port);
            if (error != null) {
                cb.failed(error);
            } else {
                cb.ready();
            }
        }, "intercom-start").start();
    }

    private String connect(String ip, int port) {
        Exception last = null;
        for (int attempt = 0; attempt < 3 && socket == null; attempt++) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(ip, port), 2500);
                s.setTcpNoDelay(true);
                socket = s;
            } catch (Exception e) {
                last = e;
                try {
                    if (socket != null) socket.close();
                } catch (Exception ignored) {
                }
                socket = null;
                if (attempt < 2) {
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        if (socket == null) {
            return "连不上 " + ip + ":" + port + "（" + (last == null ? "超时" : last.getClass().getSimpleName()) + "）";
        }
        try {
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int buf = Math.max(min, SAMPLE_RATE / 5 * 2);
            record = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, buf * 2);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                close();
                return "麦克风不可用（检查录音权限）";
            }
            record.startRecording();
        } catch (Exception e) {
            close();
            return "启动麦克风失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        running = true;
        final OutputStream out;
        try {
            out = socket.getOutputStream();
        } catch (Exception e) {
            close();
            return "写入失败：" + e.getMessage();
        }
        pump = new Thread(() -> {
            byte[] b = new byte[3200]; // 100 ms
            while (running) {
                int n = record.read(b, 0, b.length);
                if (n > 0) {
                    try {
                        out.write(b, 0, n);
                        out.flush();
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        }, "intercom-mic");
        pump.setDaemon(true);
        pump.start();
        return null;
    }

    public void stop() {
        running = false;
        if (pump != null) {
            try {
                pump.join(600);
            } catch (InterruptedException ignored) {
            }
            pump = null;
        }
        close();
    }

    private void close() {
        try {
            if (record != null) {
                if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) record.stop();
                record.release();
            }
        } catch (Exception ignored) {
        }
        record = null;
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
    }
}
