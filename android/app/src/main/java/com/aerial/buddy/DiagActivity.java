package com.aerial.buddy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

/**
 * 兼容性自检：把一台电视需要的每个端口都探一遍，并给出下一步该做什么。
 *
 * <p>每条都对应一个真实能力：8790=Aerial 管理、6553=多屏互动安装、5555=ADB 安装、
 * 8766=对讲、8768=同屏、8769=电视端小助手接口。
 */
public class DiagActivity extends Activity {

    private final StringBuilder report = new StringBuilder();
    private Theme theme;
    private LinearLayout list;
    private String ip;
    private String name;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ip = getIntent().getStringExtra("ip");
        name = getIntent().getStringExtra("name");
        theme = Theme.of(getIntent().getIntExtra("theme", Theme.AURORA));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(theme.light ? Color.parseColor("#F4F6FB") : Color.parseColor("#10131A"));
        int pad = dp(16);
        page.setPadding(pad, dp(18), pad, dp(12));

        TextView title = new TextView(this);
        title.setText("兼容性自检 · " + (name == null ? ip : name));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(theme.text);
        page.addView(title);

        TextView sub = new TextView(this);
        sub.setText(ip);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setTextColor(theme.dim);
        sub.setPadding(0, dp(4), 0, dp(10));
        page.addView(sub);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView export = new TextView(this);
        export.setText("导出诊断");
        export.setGravity(Gravity.CENTER);
        export.setTextColor(theme.text);
        export.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable eg = new GradientDrawable();
        eg.setCornerRadius(dp(14));
        eg.setColor(theme.card);
        eg.setStroke(dp(1), theme.cardStroke);
        export.setBackground(eg);
        export.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("text/plain");
            String body;
            synchronized (report) {
                body = "Aerial 管家 兼容性自检\n电视：" + ip + "（" + name + "）\n\n" + report;
            }
            i.putExtra(android.content.Intent.EXTRA_TEXT, body);
            i.putExtra(android.content.Intent.EXTRA_SUBJECT, "Aerial 管家诊断 " + ip);
            startActivity(android.content.Intent.createChooser(i, "导出诊断"));
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        elp.bottomMargin = dp(8);
        page.addView(export, elp);

        TextView again = new TextView(this);
        again.setText("重新检测");
        again.setGravity(Gravity.CENTER);
        again.setTextColor(theme.accentText);
        again.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(theme.accent);
        again.setBackground(g);
        again.setOnClickListener(v -> run());
        page.addView(again, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        setContentView(page);
        run();
    }

    private void run() {
        list.removeAllViews();
        new Thread(() -> {
            checkAerial();
            checkTcast();
            checkControl();
            checkAudio();
            checkMirror();
            checkAdb();
            row("检测完成", "以上 ✔ 的能力都可直接用；✘ 的按提示处理", "", true);
        }, "diag").start();
    }

    private void checkAerial() {
        row("Aerial 管理面板 :8790", "检测中…", "", null);
        new Thread(() -> {
            boolean open = portOpen(8790, 700);
            boolean fingerprint = false;
            if (open) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL("http://" + ip + ":8790/api/state").openConnection();
                    c.setConnectTimeout(800);
                    c.setReadTimeout(1200);
                    int code = c.getResponseCode();
                    String body = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
                    fingerprint = code == 404 && body != null && body.contains("Not found");
                    c.disconnect();
                } catch (Exception ignored) {
                }
            }
            String detail;
            String hint;
            boolean ok = fingerprint;
            if (fingerprint) {
                detail = "已安装 Aerial（未绑定）";
                hint = "点「扫码绑定」拍电视二维码，之后壁纸/应用可用";
            } else if (open) {
                detail = "端口开着，但不是 Aerial 的响应";
                hint = "这台电视可能装了别的东西占用 8790";
            } else {
                detail = "未响应：Aerial 没装或没在运行";
                hint = "点「装 Aerial」推送安装；若已装请确认电视没被系统清后台";
            }
            row("Aerial 管理面板 :8790", detail, hint, ok);
        }, "d-aerial").start();
    }

    private void checkTcast() {
        row("多屏互动 T-Cast :6553", "检测中…", "", null);
        new Thread(() -> {
            boolean ok = portOpen(6553, 700);
            row("多屏互动 T-Cast :6553",
                    ok ? "可用：安装 APK 走这条通道" : "不可用：这台电视不是 TCL/雷鸟，或该服务被禁用",
                    ok ? "安装 APK 时会自动使用它" : "安装会自动改走 ADB（需开启网络调试）",
                    ok);
        }, "d-tcast").start();
    }

    private void checkControl() {
        row("电视端助手 :8769", "检测中…", "", null);
        new Thread(() -> {
            String detail;
            String hint;
            boolean ok = false;
            try {
                String body = httpGet("http://" + ip + ":8769/apps", 2500);
                JSONObject o = new JSONObject(body);
                int n = o.optJSONArray("apps") == null ? 0 : o.optJSONArray("apps").length();
                ok = o.optBoolean("ok", false) && n > 0;
                detail = ok ? ("在线，可列出 " + n + " 个已安装应用") : "在线但返回异常";
                hint = ok ? "应用管理 / 卸载 / 同屏 / 回主页都靠它" : "重装电视端助手";
            } catch (Exception e) {
                detail = "未安装或未启动";
                hint = "点「装对讲端」推送电视端助手（约 110 KB）";
            }
            row("电视端助手 :8769", detail, hint, ok);
        }, "d-ctl").start();
    }

    private void checkAudio() {
        row("对讲 :8766", "检测中…", "", null);
        new Thread(() -> {
            boolean ok = portOpen(8766, 700);
            row("对讲 :8766",
                    ok ? "可用" : "未监听",
                    ok ? "手机上按住说话即可" : "先在电视上打开一次「Aerial 对讲端」",
                    ok);
        }, "d-audio").start();
    }

    private void checkMirror() {
        row("同屏 :8768", "检测中…", "", null);
        new Thread(() -> {
            boolean ok = portOpen(8768, 700);
            row("同屏 :8768",
                    ok ? "可用" : "未监听（未获投屏授权）",
                    ok ? "手机「同屏」可直接看画面"
                            : "在电视上打开「Aerial 对讲端」→ 选「开始同屏」按 OK（Android 9 机型每次重启需一次）",
                    ok);
        }, "d-mirror").start();
    }

    private void checkAdb() {
        row("ADB 调试 :5555", "检测中…", "", null);
        new Thread(() -> {
            String detail;
            String hint;
            boolean ok = false;
            if (!portOpen(5555, 800)) {
                detail = "未开启";
                hint = "电视设置 → 开发者选项 → 打开「USB/网络调试」后可免 T-Cast 安装";
            } else {
                AdbClient adb = new AdbClient(null);
                try {
                    if (!adb.connect(ip, 5555, 3000)) {
                        detail = "端口开着但连不上";
                        hint = "可能被别的调试程序占用";
                    } else if (adb.handshake(getFilesDir())) {
                        ok = true;
                        detail = "已授权：可直接安装/调试";
                        hint = "安装 APK 会自动走 ADB";
                    } else {
                        detail = "未授权";
                        hint = "电视上会弹「允许调试」，用遥控器选允许后点重新检测";
                    }
                } finally {
                    adb.close();
                }
            }
            row("ADB 调试 :5555", detail, hint, ok);
        }, "d-adb").start();
    }

    // ---------- ui ----------

    private void row(String title, String detail, String hint, Boolean ok) {
        synchronized (report) {
            report.append(ok == null ? "... " : (ok ? "OK  " : "FAIL ")).append(title)
                    .append(" | ").append(detail);
            if (!hint.isEmpty()) report.append(" | ").append(hint);
            report.append("\n");
        }
        ui(() -> {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(theme.card);
            bg.setStroke(dp(1), theme.cardStroke);
            card.setBackground(bg);
            card.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            card.setLayoutParams(lp);

            TextView t = new TextView(this);
            String mark = ok == null ? "…" : (ok ? "✔ " : "✘ ");
            t.setText(mark + title);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setTextColor(ok == null ? theme.dim : (ok ? Color.parseColor("#38D39F") : Color.parseColor("#F2555A")));
            card.addView(t);

            if (!detail.isEmpty()) {
                TextView d = new TextView(this);
                d.setText(detail);
                d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                d.setTextColor(theme.text);
                d.setPadding(0, dp(3), 0, 0);
                card.addView(d);
            }
            if (!hint.isEmpty()) {
                TextView h = new TextView(this);
                h.setText(hint);
                h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                h.setTextColor(theme.dim);
                h.setPadding(0, dp(2), 0, 0);
                card.addView(h);
            }
            list.addView(card);
        });
    }

    private void add(String title, String detail, String hint, boolean ok) {
        row(title, detail, hint, ok);
    }

    private void ui(Runnable r) {
        runOnUiThread(r);
    }

    // ---------- net ----------

    private boolean portOpen(int port, int timeout) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String httpGet(String url, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        String body = read(c.getInputStream());
        c.disconnect();
        return body;
    }

    private String read(InputStream in) {
        if (in == null) return "";
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
