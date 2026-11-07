package com.aerial.buddy;

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

    private Socket socket;
    private AudioRecord record;
    private Thread pump;
    private volatile boolean running;

    public boolean isActive() {
        return running;
    }

    /** Returns null on success, or a message on failure. */
    public String start(String ip) {
        if (running) return null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, PORT), 3000);
            socket.setTcpNoDelay(true);
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int buf = Math.max(min, SAMPLE_RATE / 5 * 2);
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, buf * 2);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                close();
                return "麦克风不可用（检查录音权限）";
            }
            record.startRecording();
            running = true;
            final OutputStream out = socket.getOutputStream();
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
        } catch (Exception e) {
            close();
            return "连不上电视对讲端（" + (e.getMessage() == null ? "端口 8766" : e.getMessage()) + "）";
        }
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
