package com.aerial.intercom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Streams the TV screen to the phone as JPEG frames.
 *
 * <p>Wire format: 4-byte big-endian length + JPEG bytes, repeated. One TCP connection per viewer on
 * port 8768. The phone has no other way to see the panel, because the TV exposes neither ADB nor any
 * screen API on this firmware.
 */
public class MirrorService extends Service {

    public static final int PORT = 8768;
    public static final String ACTION_START = "com.aerial.intercom.MIRROR_START";
    public static final String ACTION_STOP = "com.aerial.intercom.MIRROR_STOP";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";
    private static final String CHANNEL = "mirror";
    private static final int NOTIFY_ID = 42;

    private final List<Socket> clients = new ArrayList<>();
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private ServerSocket server;
    private Thread acceptor;
    private volatile boolean running;
    private int width, height;

    public static void start(Context ctx, int resultCode, Intent data) {
        Intent i = new Intent(ctx, MirrorService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_RESULT_CODE, resultCode);
        i.putExtra(EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, MirrorService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFY_ID, notification());
        if (intent != null && intent.hasExtra(EXTRA_RESULT_CODE)) {
            int code = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            startCapture(code, data);
        }
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void startCapture(int resultCode, Intent data) {
        if (running) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            int scale = Math.max(2, Math.round(Math.max(dm.widthPixels, dm.heightPixels) / 720f));
            width = dm.widthPixels / scale;
            height = dm.heightPixels / scale;
            int dpi = dm.densityDpi;

            MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mgr.getMediaProjection(resultCode, data);
            if (projection == null) return;

            thread = new HandlerThread("mirror");
            thread.start();
            Handler h = new Handler(thread.getLooper());

            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            display = projection.createVirtualDisplay("aerial-mirror", width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, h);
            reader.setOnImageAvailableListener(this::onFrame, h);

            running = true;
            startServer();
            ControlServer.start(getApplicationContext());
        } catch (Exception ignored) {
        }
    }

    private void startServer() {
        acceptor = new Thread(() -> {
            try {
                server = new ServerSocket(PORT);
                while (running) {
                    Socket s = server.accept();
                    s.setTcpNoDelay(true);
                    synchronized (clients) {
                        clients.add(s);
                    }
                }
            } catch (Exception ignored) {
            }
        }, "mirror-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private long lastFrameAt;

    private void onFrame(ImageReader r) {
        Image image = null;
        try {
            image = r.acquireLatestImage();
            if (image == null) return;
            // 无观看者时不编码；有观看者时限制到 ~8 fps，避免 60fps 全量 JPEG 编码把 CPU 打满。
            long now = System.currentTimeMillis();
            if (clients.isEmpty() || now - lastFrameAt < 80) return;
            lastFrameAt = now;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf);
            Bitmap cropped = Bitmap.createBitmap(bmp, 0, 0, width, height);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            cropped.compress(Bitmap.CompressFormat.JPEG, 62, out);
            byte[] jpeg = out.toByteArray();
            bmp.recycle();
            cropped.recycle();
            broadcast(jpeg);
        } catch (Exception ignored) {
        } finally {
            if (image != null) image.close();
        }
    }

    private void broadcast(byte[] jpeg) {
        synchronized (clients) {
            for (int i = clients.size() - 1; i >= 0; i--) {
                Socket s = clients.get(i);
                try {
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    out.writeInt(jpeg.length);
                    out.write(jpeg);
                    out.flush();
                } catch (Exception e) {
                    try {
                        s.close();
                    } catch (Exception ignored) {
                    }
                    clients.remove(i);
                }
            }
        }
    }

    private Notification notification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "同屏", NotificationManager.IMPORTANCE_LOW));
            }
            return new Notification.Builder(this, CHANNEL)
                    .setContentTitle("Aerial 同屏")
                    .setContentText("正在把电视画面投到手机 · " + PORT)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle("Aerial 同屏")
                .setContentText("正在把电视画面投到手机 · " + PORT)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        synchronized (clients) {
            for (Socket s : clients) {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
            clients.clear();
        }
        try {
            if (display != null) display.release();
        } catch (Exception ignored) {
        }
        try {
            if (reader != null) reader.close();
        } catch (Exception ignored) {
        }
        try {
            if (projection != null) projection.stop();
        } catch (Exception ignored) {
        }
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
