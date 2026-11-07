package com.aerial.buddy;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** TV screen on the phone: connects to the TV helper's MJPEG stream on 8768. */
public class MirrorActivity extends Activity {

    private ImageView view;
    private TextView home;
    private TextView status;
    private volatile boolean running = true;
    private int retries;
    private Socket socket;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String ip = getIntent().getStringExtra("ip");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(10));
        status = new TextView(this);
        status.setText("正在连接 " + ip + ":" + MirrorServicePort.PORT + " …");
        status.setTextColor(Color.WHITE);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        bar.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        home = new TextView(this);
        home.setText("切回电视主页");
        home.setTextColor(Color.WHITE);
        home.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        home.setPadding(dp(12), dp(6), dp(12), dp(6));
        home.setBackgroundColor(Color.parseColor("#7C5CFF"));
        home.setVisibility(android.view.View.GONE);
        home.setOnClickListener(v -> requestHome());
        bar.addView(home);

        TextView close = new TextView(this);
        close.setText("关闭");
        close.setTextColor(Color.parseColor("#B9A7FF"));
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        close.setPadding(dp(12), dp(6), dp(6), dp(6));
        close.setOnClickListener(v -> finish());
        bar.addView(close);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        root.addView(bar, blp);

        setContentView(root);
        connect(ip);
    }

    private void connect(final String ip) {
        new Thread(() -> {
            try {
                // 先让电视尝试开启同屏（电视端可能还没开始推送），连不上再重试。
                askTvToStartMirror(ip);
                socket = new Socket();
                for (int i = 0; i < 4 && !running; i++) {
                    try {
                        socket.connect(new InetSocketAddress(ip, MirrorServicePort.PORT), 2000);
                        break;
                    } catch (Exception e) {
                        try {
                            socket.close();
                        } catch (Exception ignored) {
                        }
                        socket = new Socket();
                        if (i < 3) Thread.sleep(1200);
                    }
                }
                socket.setSoTimeout(8000);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                ui("已连接 · 等待电视画面");
                int blackFrames = 0;
                int frameNo = 0;
                boolean warned = false;
                while (running) {
                    int len = in.readInt();
                    if (len <= 0 || len > 8 * 1024 * 1024) break;
                    byte[] jpeg = new byte[len];
                    in.readFully(jpeg);
                    final Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, len);
                    if (bmp == null) continue;
                    boolean dark = (++frameNo % 5 == 0) && isDark(bmp);
                    if (dark) blackFrames++; else blackFrames = 0;
                    final boolean showWarning = blackFrames > 12 && !warned;
                    if (showWarning) warned = true;
                    runOnUiThread(() -> {
                        view.setImageBitmap(bmp);
                        if (showWarning) {
                            status.setText("画面全黑：电视停在 HDMI/电视输入，系统截不到那一路\\n已自动请求切回 Aerial 主页…");
                            home.setVisibility(android.view.View.VISIBLE);
                            autoHome(ip);
                        } else {
                            status.setText("同屏中 · " + ip);
                        }
                    });
                }
            } catch (Exception e) {
                if (retries < 3) {
                    retries++;
                    ui("连不上（第 " + retries + "/3 次），正在请电视重新开始同屏…");
                    autoHome(ip);
                    requestMirrorStart(ip);
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException ignored) {
                    }
                    connect(ip);
                    return;
                }
                ui("同屏连不上：电视端没有有效的录屏授权。\\n"
                        + "请在电视上打开「Aerial 对讲端」→ 按「开始同屏」→ 系统弹窗选「立即开始」，然后重试。");

            }
        }, "mirror").start();
    }

    /** 全黑时自动请电视切回 Aerial 主页（助手 /home），避免一直黑屏。 */
    /** 请电视端助手重新开始同屏。 */
    private void requestMirrorStart(final String ip) {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL("http://" + ip + ":8769/mirror/start").openConnection();
                c.setConnectTimeout(1500);
                c.setReadTimeout(3000);
                c.getInputStream().read(new byte[32]);
                c.disconnect();
            } catch (Exception ignored) {
            }
        }, "mirror-start").start();
    }

    private void autoHome(final String ip) {
        new Thread(() -> {
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(ip, 8769), 1200);
                java.io.OutputStream out = s.getOutputStream();
                out.write(("GET /home HTTP/1.1\r\nHost: " + ip + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                out.flush();
            } catch (Exception ignored) {
            }
        }, "auto-home").start();
    }

    /** 采样 32x18 判断是不是全黑（HDMI 输入时 MediaProjection 只能拿到黑屏）。 */
    private static boolean isDark(Bitmap bmp) {
        try {
            Bitmap small = Bitmap.createScaledBitmap(bmp, 32, 18, false);
            int[] px = new int[32 * 18];
            small.getPixels(px, 0, 32, 0, 0, 32, 18);
            long sum = 0;
            for (int p : px) {
                sum += ((p >> 16 & 0xff) * 299 + (p >> 8 & 0xff) * 587 + (p & 0xff) * 114) / 1000;
            }
            return sum / px.length < 12;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 请求电视端开同屏（8769 /mirror）。 */
    private void askTvToStartMirror(String ip) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(ip, 8769), 1500);
            java.io.OutputStream out = s.getOutputStream();
            out.write(("GET /mirror HTTP/1.1\r\nHost: " + ip + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            out.flush();
        } catch (Exception ignored) {
        }
    }

    /** 让电视回到 Android 主页（Aerial），这样同屏才有画面可截。 */
    private void requestHome() {
        final String ip = getIntent().getStringExtra("ip");
        status.setText("已请求电视切回主页…");
        new Thread(() -> {
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(ip, 8769), 2500);
                java.io.OutputStream out = s.getOutputStream();
                out.write(("GET /home HTTP/1.1\r\nHost: " + ip + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                out.flush();
                ui("电视已切回主页，稍等画面恢复");
            } catch (Exception e) {
                ui("切回失败：" + e.getMessage());
            }
        }, "home").start();
    }

    private void ui(String text) {
        runOnUiThread(() -> status.setText(text));
    }

    @Override
    protected void onDestroy() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
