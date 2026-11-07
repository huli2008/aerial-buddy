package com.aerial.buddy;

import android.app.Activity;
import android.content.Intent;
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

/**
 * 电视画面推送到手机。
 *
 * <p>协议与 0.12 版一致：TCP 8768，每帧 = 4 字节大端长度 + JPEG 数据。
 * 这一版刻意保持最简：连上就读、读到的帧直接显示，不做黑帧判断、不做自动切源，
 * 避免把"画面正常但被判定为黑帧"这类误伤带进来。
 */
public class MirrorActivity extends Activity {

    private ImageView view;
    private TextView status;
    private volatile boolean running = true;
    private String ip;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ip = getIntent().getStringExtra("ip");
        Theme theme = Theme.of(getIntent().getIntExtra("theme", Theme.AURORA));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        status = new TextView(this);
        status.setText("正在连接 " + ip + " …");
        status.setTextColor(Color.WHITE);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, dp(28));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.BOTTOM;
        root.addView(status, slp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView home = chip("切回电视主页");
        home.setOnClickListener(v -> requestHome());
        bar.addView(home);

        TextView close = chip("退出");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(10);
        close.setOnClickListener(v -> finish());
        bar.addView(close, clp);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM | Gravity.END;
        blp.bottomMargin = dp(60);
        blp.rightMargin = dp(12);
        root.addView(bar, blp);

        setContentView(root);
        connect(ip);
    }

    private TextView chip(String label) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        v.setTextColor(Color.WHITE);
        v.setPadding(dp(14), dp(8), dp(14), dp(8));
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(0x66000000);
        v.setBackground(d);
        return v;
    }

    private void connect(final String ip) {
        new Thread(() -> {
            int attempt = 0;
            while (running) {
                attempt++;
                Socket socket = null;
                try {
                    ui("正在连接 " + ip + ":8768（第 " + attempt + " 次）…");
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, 8768), 4000);
                    socket.setSoTimeout(8000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    ui("已连接，等待画面…");
                    while (running) {
                        int len = in.readInt();
                        if (len <= 0 || len > 8 * 1024 * 1024) {
                            throw new java.io.IOException("帧长度异常：" + len);
                        }
                        byte[] data = new byte[len];
                        in.readFully(data);
                        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, len);
                        if (bmp != null) {
                            final Bitmap show = bmp;
                            runOnUiThread(() -> {
                                view.setImageBitmap(show);
                                status.setText("同屏中 · " + ip);
                            });
                        }
                    }
                } catch (Exception e) {
                    if (!running) break;
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    ui("连不上或已断开：" + msg + "\n3 秒后自动重连…");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (attempt >= 5 && running) {
                    break;
                }
            }
        }, "mirror").start();
    }

    private void requestHome() {
        new Thread(() -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, 8769), 1500);
                s.getOutputStream().write(("GET /home HTTP/1.1\r\nHost: " + ip
                        + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                s.getOutputStream().flush();
            } catch (Exception ignored) {
            }
        }, "home").start();
    }

    private void ui(final String text) {
        runOnUiThread(() -> status.setText(text));
    }

    @Override
    protected void onDestroy() {
        running = false;
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
