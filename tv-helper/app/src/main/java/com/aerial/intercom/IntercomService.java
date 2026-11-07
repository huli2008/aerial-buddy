package com.aerial.intercom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Receives raw PCM from the phone and plays it on the TV speakers.
 *
 * <p>Wire format (deliberately dumb, lowest latency): one TCP connection per talk session, then
 * 16 kHz / mono / 16-bit little-endian PCM frames — no container, no headers, no codec.
 */
public class IntercomService extends Service {

    public static final int PORT = 8766;
    public static final int SAMPLE_RATE = 16000;
    public static final String ACTION_START = "com.aerial.intercom.START";
    public static final String ACTION_STOP = "com.aerial.intercom.STOP";
    private static final String CHANNEL = "intercom";
    private static final int NOTIFY_ID = 41;

    private volatile boolean running;
    private ServerSocket server;
    private Thread acceptor;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFY_ID, notification());
        if (!running) startServer();
        return START_STICKY;
    }

    private void startServer() {
        running = true;
        acceptor = new Thread(() -> {
            try {
                server = new ServerSocket(PORT);
                while (running) {
                    try (Socket s = server.accept()) {
                        play(s);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }, "intercom-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void play(Socket s) throws Exception {
        int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(minBuf, SAMPLE_RATE / 5 * 2); // ~200 ms
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        track.play();
        try {
            InputStream in = s.getInputStream();
            byte[] buf = new byte[3200]; // 100 ms
            int n;
            while (running && (n = in.read(buf)) > 0) {
                track.write(buf, 0, n);
            }
        } finally {
            try {
                track.stop();
                track.release();
            } catch (Exception ignored) {
            }
        }
    }

    private Notification notification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "对讲", NotificationManager.IMPORTANCE_LOW));
            }
            return new Notification.Builder(this, CHANNEL)
                    .setContentTitle("Aerial 对讲端")
                    .setContentText("正在监听 " + PORT + " 端口")
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle("Aerial 对讲端")
                .setContentText("正在监听 " + PORT + " 端口")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
