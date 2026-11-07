package com.aerial.intercom;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * TV side: receives the phone's microphone audio (8766) and streams this screen to the phone (8768).
 *
 * <p>Everything is reachable with the remote: buttons are focusable, the D-pad moves between them and
 * OK activates them.
 */
public class MainActivity extends Activity {

    private static final int REQ_PROJECTION = 91;
    private static final String PREFS = "aerial_intercom";

    private TextView status;
    private TextView castBtn;
    private TextView autoBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#10131A"));
        int pad = dp(40);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Aerial 对讲端");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#9AA7B4"));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(14), 0, dp(6));
        root.addView(status);

        TextView hint = new TextView(this);
        hint.setText("手机「Aerial 管家」→ 该电视卡片 →「对讲」按住说话 /「同屏」看这台电视的画面。\n"
                + "本应用不录音、不上传，画面只在局域网内传输。\n"
                + "遥控器：方向键移动，按 OK 确认。");
        hint.setTextColor(Color.parseColor("#6C7887"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);

        castBtn = button("开始同屏", () -> askProjection());
        root.addView(castBtn, lp(240, 50));

        autoBtn = button("自动同屏：开", () -> {
            boolean now = !autoMirror();
            prefs().edit().putBoolean("autoMirror", now).apply();
            autoBtn.setText("自动同屏：" + (now ? "开" : "关"));
            if (now) askProjection();
        });
        root.addView(autoBtn, lp(240, 46));

        TextView audioBtn = button("对讲接收：开", () -> {
            Intent i = new Intent(this, IntercomService.class);
            i.setAction(IntercomService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
            status.setText(statusText("对讲已开启"));
        });
        root.addView(audioBtn, lp(240, 46));

        setContentView(root);

        startAudio();
        if (autoMirror()) askProjection();
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(w), dp(h));
        p.topMargin = dp(10);
        return p;
    }

    /** Focusable, D-pad friendly button. */
    private TextView button(String label, Runnable action) {
        final TextView v = new TextView(this);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        v.setBackground(stateDrawable(false));
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.setOnClickListener(x -> action.run());
        v.setOnFocusChangeListener((view, has) -> {
            view.setBackground(stateDrawable(has));
            view.setScaleX(has ? 1.04f : 1f);
            view.setScaleY(has ? 1.04f : 1f);
        });
        return v;
    }

    private GradientDrawable stateDrawable(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(16));
        d.setColor(focused ? Color.parseColor("#7C5CFF") : Color.parseColor("#2A3550"));
        if (focused) d.setStroke(dp(2), Color.WHITE);
        return d;
    }

    private void startAudio() {
        Intent i = new Intent(this, IntercomService.class);
        i.setAction(IntercomService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        status.setText(statusText("对讲已开启"));
    }

    private String statusText(String prefix) {
        String ip = localIp();
        return prefix + " · " + (ip == null ? "未知 IP" : ip) + "\n对讲 " + IntercomService.PORT + " · 同屏 " + MirrorService.PORT;
    }

    private void askProjection() {
        try {
            android.media.projection.MediaProjectionManager mgr =
                    (android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mgr.createScreenCaptureIntent(), REQ_PROJECTION);
        } catch (Exception e) {
            status.setText(statusText("无法请求同屏权限"));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PROJECTION) return;
        if (res != RESULT_OK || data == null) {
            status.setText(statusText("同屏未授权"));
            return;
        }
        MirrorService.start(this, res, data);
        status.setText(statusText("同屏中"));
    }

    private boolean autoMirror() {
        return prefs().getBoolean("autoMirror", true);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private String localIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
