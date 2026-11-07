package com.aerial.buddy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Aerial 管家：扫码绑定电视、一键进入电视管理页、把 APK 推到电视安装。 */
public class MainActivity extends Activity {

    private static final int REQ_QR_PICK = 11;
    private static final int REQ_QR_CAMERA = 12;
    private static final int REQ_APK = 13;

    private final List<Device> devices = new ArrayList<>();
    private Theme theme;
    private Backdrop backdrop;
    private FrameLayout root;
    private LinearLayout list;
    private TextView countView;

    private Uri cameraOutput;
    private Device installing;
    private String pendingScanIp;
    private final Intercom intercom = new Intercom();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        theme = Theme.of(Store.theme(this));

        root = new FrameLayout(this);
        backdrop = new Backdrop(this, theme);
        root.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        page.setPadding(pad, dp(22), pad, dp(18));
        page.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(14), 0, dp(96));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        TextView find = actionButton("扫描", false);
        find.setOnClickListener(v -> scanLan());
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(0, dp(52), 0.9f);
        fLp.rightMargin = dp(10);
        bottom.addView(find, fLp);
        TextView scan = actionButton("扫码绑定", false);
        scan.setOnClickListener(v -> chooseScanSource());
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(52), 1.1f);
        sLp.rightMargin = dp(10);
        bottom.addView(scan, sLp);
        TextView add = actionButton("手动输入", true);
        add.setOnClickListener(v -> showUrlDialog(null));
        bottom.addView(add, new LinearLayout.LayoutParams(0, dp(52), 1f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(pad, 0, pad, dp(22));
        lp.gravity = Gravity.BOTTOM;
        root.addView(bottom, lp);

        setContentView(root);

        devices.addAll(Store.load(this));
        render();
        if (!devices.isEmpty()) refreshAll();
    }

    // ---------- header ----------

    private View buildHeader() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Aerial 管家");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(theme.text);
        row.addView(title);
        countView = new TextView(this);
        countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        countView.setTextColor(theme.dim);
        countView.setPadding(dp(8), 0, 0, 0);
        row.addView(countView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView about = new TextView(this);
        about.setText("ⓘ 信息");
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        about.setTextColor(theme.text);
        about.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable ab = new GradientDrawable();
        ab.setCornerRadius(dp(12));
        ab.setColor(theme.light ? Color.parseColor("#14000000") : Color.parseColor("#22FFFFFF"));
        about.setBackground(ab);
        about.setOnClickListener(v -> showAbout());
        row.addView(about);
        box.addView(row);

        TextView sub = new TextView(this);
        sub.setText("扫电视上的二维码绑定 · 管理壁纸/应用 · 推送安装 APK");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        sub.setTextColor(theme.dim);
        sub.setPadding(0, dp(4), 0, 0);
        box.addView(sub);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(14), 0, 0);
        for (Theme t : Theme.ALL) {
            TextView chip = new TextView(this);
            boolean on = t.id == theme.id;
            chip.setText(t.name);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            chip.setTextColor(on ? theme.accentText : theme.text);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));
            bg.setColor(on ? t.accent : theme.card);
            bg.setStroke(dp(1), on ? t.accent : theme.cardStroke);
            chip.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                theme = t;
                Store.setTheme(MainActivity.this, t.id);
                recreate();
            });
            chips.addView(chip);
        }
        box.addView(chips);
        return box;
    }

    // ---------- device list ----------

    private void render() {
        list.removeAllViews();
        countView.setText("  " + devices.size() + " 台电视");
        if (devices.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("还没有绑定电视\n\n在电视上：控制中心 → 个性化，用手机扫右侧二维码；\n或手动输入二维码下面的地址。");
            e.setTextColor(theme.dim);
            e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(10), dp(70), dp(10), dp(10));
            list.addView(e);
            return;
        }
        for (Device d : devices) list.addView(cardFor(d));
    }

    private View cardFor(final Device d) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(theme.card);
        bg.setStroke(dp(1), theme.cardStroke);
        card.setBackground(bg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(12);
        card.setLayoutParams(clp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        GradientDrawable db = new GradientDrawable();
        db.setShape(GradientDrawable.OVAL);
        db.setColor(d.dot == 1 ? Color.parseColor("#38D39F")
                : d.dot == 0 ? Color.parseColor("#F2555A") : Color.parseColor("#F5A524"));
        dot.setBackground(db);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.rightMargin = dp(9);
        row.addView(dot, dlp);

        TextView name = new TextView(this);
        name.setText(d.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        name.setTextColor(theme.text);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(this);
        badge.setText(d.bound() ? "已绑定" : "未绑定");
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        badge.setTextColor(d.bound() ? Color.parseColor("#38D39F") : theme.dim);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable bb = new GradientDrawable();
        bb.setCornerRadius(dp(9));
        bb.setColor(theme.light ? Color.parseColor("#14000000") : Color.parseColor("#22FFFFFF"));
        badge.setBackground(bb);
        row.addView(badge);
        card.addView(row);

        TextView ip = new TextView(this);
        ip.setText(d.host() + "  ·  管理 8790  ·  对讲 8766  ·  同屏 8768");
        ip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        ip.setTextColor(theme.dim);
        ip.setPadding(0, dp(4), 0, 0);
        card.addView(ip);

        TextView status = new TextView(this);
        status.setText(d.status == null ? "未连接" : d.status);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setTextColor(theme.text);
        status.setPadding(0, dp(8), 0, 0);
        card.addView(status);

        // 双横排：上排是日常操作，下排是维护操作；原有按钮全部保留，只是分成两行不再横向滚动。
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(9), 0, 0);
        row1.addView(chip("壁纸", () -> ifBound(d, () -> showWallpapers(d))));
        row1.addView(chip("应用", () -> ifBound(d, () -> openApps(d))));
        row1.addView(chip("对讲", () -> ifBound(d, () -> requireSponsor(() -> showIntercom(d)))));
        row1.addView(chip("同屏", () -> openMirror(d)));
        card.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(7), 0, 0);
        row2.addView(chip("管理", () -> ifBound(d, () -> openPanel(d))));
        row2.addView(chip("装 Aerial", () -> installAerial(d)));
        row2.addView(chip("装对讲端", () -> installHelper(d)));
        row2.addView(chip("刷新", () -> refresh(d)));
        card.addView(row2);

        card.setOnClickListener(v -> openPanel(d));
        card.setOnLongClickListener(v -> {
            confirmDelete(d);
            return true;
        });
        return card;
    }

    /** 未绑定时引导去扫码，而不是让依赖 token 的功能报错。 */
    private void ifBound(Device d, Runnable r) {
        if (d.bound()) {
            r.run();
            return;
        }
        toast("先扫码绑定这台电视，才能用壁纸/应用/对讲/管理");
        pendingScanIp = d.ip();
        captureQr();
    }

    private TextView chip(String label, Runnable r) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        b.setTextColor(theme.text);
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(theme.light ? Color.parseColor("#66FFFFFF") : Color.parseColor("#22FFFFFF"));
        bg.setStroke(dp(1), theme.cardStroke);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(7);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? theme.accentText : theme.text);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(primary ? theme.accent : theme.card);
        if (!primary) bg.setStroke(dp(1), theme.cardStroke);
        b.setBackground(bg);
        return b;
    }

    // ---------- actions ----------

    private void refreshAll() {
        for (Device d : devices) refresh(d);
    }

    private void refresh(final Device d) {
        if (!d.bound()) {
            d.status = "未绑定 · 可直接「安装 APK」，扫码后才管壁纸/应用/对讲";
            d.dot = 2;
            render();
            return;
        }
        d.status = "连接中…";
        new Thread(() -> {
            String text;
            int dot;
            try {
                JSONObject s = AerialApi.state(d);
                text = describe(s);
                dot = 1;
            } catch (Exception e) {
                text = "连不上：" + (e.getMessage() == null ? "网络错误" : e.getMessage());
                dot = 0;
            }
            final String t = text;
            final int dc = dot;
            runOnUiThread(() -> {
                d.status = t;
                d.dot = dc;
                render();
            });
        }, "state").start();
    }

    /** "在线 · 壁纸 firefly · 24 个应用 · 屏保 视频" */
    private static String describe(JSONObject s) {
        StringBuilder sb = new StringBuilder("在线");
        String sel = s.optString("selected", "");
        org.json.JSONArray ws = s.optJSONArray("wallpapers");
        if (ws != null && !sel.isEmpty()) {
            for (int i = 0; i < ws.length(); i++) {
                JSONObject w = ws.optJSONObject(i);
                if (w != null && sel.equals(w.optString("id"))) {
                    sb.append(" · 壁纸 ").append(w.optString("title", ""));
                    break;
                }
            }
        }
        org.json.JSONArray apps = s.optJSONArray("apps");
        if (apps != null) sb.append(" · ").append(apps.length()).append(" 个应用");
        String ss = s.optString("screensaver", "");
        if (!ss.isEmpty()) sb.append(" · 屏保 ").append("videos".equals(ss) ? "视频" : ss);
        return sb.toString();
    }

    /** Native wallpaper picker: lists state.wallpapers and POSTs api/wallpaper. */
    private void showWallpapers(final Device d) {
        toast("读取壁纸列表…");
        new Thread(() -> {
            try {
                final JSONObject s = AerialApi.state(d);
                final org.json.JSONArray ws = s.optJSONArray("wallpapers");
                if (ws == null || ws.length() == 0) {
                    runOnUiThread(() -> toast("电视没有返回壁纸列表"));
                    return;
                }
                final String[] titles = new String[ws.length()];
                final String[] ids = new String[ws.length()];
                final String sel = s.optString("selected", "");
                for (int i = 0; i < ws.length(); i++) {
                    JSONObject w = ws.optJSONObject(i);
                    ids[i] = w.optString("id");
                    String title = w.optString("title", ids[i]);
                    titles[i] = (ids[i].equals(sel) ? "✓ " : "") + title
                            + ("video".equals(w.optString("kind")) ? "（视频）" : "");
                }
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("换壁纸 · " + d.name)
                        .setItems(titles, (dlg, which) -> {
                            toast("正在切换…");
                            new Thread(() -> {
                                try {
                                    java.util.Map<String, String> f = new java.util.HashMap<>();
                                    f.put("id", ids[which]);
                                    AerialApi.post(d, "api/wallpaper", f);
                                    runOnUiThread(() -> {
                                        toast("已切换");
                                        refresh(d);
                                    });
                                } catch (Exception e) {
                                    runOnUiThread(() -> toast("切换失败：" + e.getMessage()));
                                }
                            }).start();
                        })
                        .setNegativeButton("关闭", null)
                        .show());
            } catch (Exception e) {
                runOnUiThread(() -> toast("获取失败：" + e.getMessage()));
            }
        }, "wallpapers").start();
    }

    /** Lists the apps Aerial reports on this TV and offers T-Cast uninstall. */
    private void showApps(final Device d) {
        toast("读取应用列表…");
        new Thread(() -> {
            try {
                final JSONObject s = AerialApi.state(d);
                final org.json.JSONArray apps = s.optJSONArray("apps");
                if (apps == null || apps.length() == 0) {
                    runOnUiThread(() -> toast("电视没有返回应用列表"));
                    return;
                }
                final String[] labels = new String[apps.length()];
                final String[] keys = new String[apps.length()];
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject a = apps.optJSONObject(i);
                    keys[i] = a.optString("key");
                    String custom = a.optString("name", "");
                    String label = a.optString("label", keys[i]);
                    labels[i] = (custom.isEmpty() ? label : custom) + "\n" + keys[i];
                }
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("已安装应用 · " + apps.length() + " 个")
                        .setItems(labels, (dlg, which) -> confirmUninstall(d, keys[which], labels[which]))
                        .setNegativeButton("关闭", null)
                        .show());
            } catch (Exception e) {
                runOnUiThread(() -> toast("获取失败：" + e.getMessage()));
            }
        }, "apps").start();
    }

    private void confirmUninstall(final Device d, final String pkg, String label) {
        String shown = label.split("\n")[0];
        new AlertDialog.Builder(this)
                .setTitle("卸载 " + shown)
                .setMessage(pkg + "\n\n通过电视的「多屏互动」执行；系统应用可能被固件保护而失败。")
                .setNegativeButton("取消", null)
                .setPositiveButton("卸载", (dlg, w) -> doUninstall(d, pkg, shown))
                .show();
    }

    private void doUninstall(final Device d, final String pkg, final String label) {
        toast("正在卸载…");
        new Thread(() -> {
            int code = Tcast.uninstall(d.ip(), pkg, label);
            try {
                Thread.sleep(1800);
            } catch (InterruptedException ignored) {
            }
            boolean gone = false;
            try {
                JSONObject s = AerialApi.state(d);
                org.json.JSONArray apps = s.optJSONArray("apps");
                if (apps != null) {
                    gone = true;
                    for (int i = 0; i < apps.length(); i++) {
                        if (pkg.equals(apps.optJSONObject(i).optString("key"))) {
                            gone = false;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            final boolean ok = gone || code == 0;
            final int c = code;
            runOnUiThread(() -> {
                if (ok) toast("已卸载 " + label);
                else toast("卸载失败（电视返回 " + c + "）：系统应用或被固件保护");
                refresh(d);
            });
        }, "uninstall").start();
    }

    /** 对讲：电视端小助手负责播放，手机按住说话。 */
    private void showIntercom(final Device d) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(12), p, dp(4));

        TextView tip = new TextView(this);
        tip.setText("按住下面的按钮说话，电视喇叭会同步出声。\n"
                + "首次使用需在电视上装一个「对讲端」（约 98 KB），装好后在电视上打开一次。");
        tip.setTextColor(theme.dim);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        box.addView(tip);

        final TextView probe = new TextView(this);
        probe.setText("正在检测对讲端…");
        probe.setTextColor(theme.dim);
        probe.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        probe.setPadding(0, dp(10), 0, 0);
        box.addView(probe);

        final int[] port = {Intercom.PORT};

        final TextView hold = new TextView(this);
        hold.setText("按住说话");
        hold.setGravity(Gravity.CENTER);
        hold.setTextColor(theme.accentText);
        hold.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        GradientDrawable hb = new GradientDrawable();
        hb.setCornerRadius(dp(18));
        hb.setColor(theme.accent);
        hold.setBackground(hb);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        hlp.topMargin = dp(14);
        box.addView(hold, hlp);

        final TextView helper = new TextView(this);
        helper.setText("① 装对讲端到电视");
        helper.setGravity(Gravity.CENTER);
        helper.setTextColor(theme.text);
        helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable gb = new GradientDrawable();
        gb.setCornerRadius(dp(14));
        gb.setColor(theme.card);
        gb.setStroke(dp(1), theme.cardStroke);
        helper.setBackground(gb);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        glp.topMargin = dp(10);
        box.addView(helper, glp);

        final TextView testBtn = new TextView(this);
        testBtn.setText("② 测试连接");
        testBtn.setGravity(Gravity.CENTER);
        testBtn.setTextColor(theme.text);
        testBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable tb2 = new GradientDrawable();
        tb2.setCornerRadius(dp(14));
        tb2.setColor(theme.card);
        tb2.setStroke(dp(1), theme.cardStroke);
        testBtn.setBackground(tb2);
        LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        tlp2.topMargin = dp(10);
        box.addView(testBtn, tlp2);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("对讲 · " + d.name)
                .setView(box)
                .setNegativeButton("关闭", (d2, w) -> intercom.stop())
                .create();

        helper.setOnClickListener(v -> {
            dlg.dismiss();
            installHelper(d);
        });


        hold.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (!checkMic()) return true;
                    hold.setText("连接中…");
                    intercom.startAsync(d.ip(), port[0], MainActivity.this, new Intercom.Cb() {
                        @Override
                        public void ready() {
                            runOnUiThread(() -> {
                                if (intercom.isActive()) {
                                    hold.setText("正在说话…松开结束");
                                    hold.setBackgroundColor(0xFFB23B3B);
                                }
                            });
                        }

                        @Override
                        public void failed(final String message) {
                            runOnUiThread(() -> {
                                hold.setText("按住说话");
                                toast(message);
                            });
                        }
                    });
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    intercom.stop();
                    hold.setText("按住说话");
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(dp(18));
                    bg.setColor(theme.accent);
                    hold.setBackground(bg);
                    return true;
                default:
                    return false;
            }
        });

        testBtn.setOnClickListener(v -> {
            probe.setText("正在测试连接…");
            new Thread(() -> {
                String msg;
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(d.ip(), port[0]), 2500);
                    msg = "连接成功：" + d.ip() + ":" + port[0];
                } catch (Exception e) {
                    msg = "连接失败：" + e.getClass().getSimpleName() + " " + e.getMessage();
                }
                final String m = msg;
                runOnUiThread(() -> {
                    probe.setText(m);
                    toast(m);
                });
            }, "intercom-test").start();
        });

        dlg.show();

        // 对讲端会每 3 秒发一次 UDP 广播，先听一下它用的是哪个端口；听不到再退回默认端口探测。
        new Thread(() -> {
            int found = Intercom.discover(d.ip(), 2500);
            if (found <= 0) {
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(d.ip(), Intercom.PORT), 1200);
                    found = Intercom.PORT;
                } catch (Exception ignored) {
                }
            }
            final int foundPort = found;
            runOnUiThread(() -> {
                if (foundPort > 0) {
                    port[0] = foundPort;
                    probe.setText("● 对讲端在线（端口 " + foundPort + "）");
                    probe.setTextColor(theme.text);
                } else {
                    probe.setText("○ 没检测到对讲端：先点下面「① 装对讲端到电视」并打开一次");
                    probe.setTextColor(theme.dim);
                }
            });
        }, "intercom-probe").start();
    }

    private boolean checkMic() {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 71);
        toast("已请求麦克风权限，允许后再按住说话");
        return false;
    }

    /** Copies the bundled TV helper and pushes it through 多屏互动. */
    private void installHelper(final Device d) {
        try {
            java.io.File f = new java.io.File(getCacheDir(), "intercom-tv.apk");
            try (java.io.InputStream in = getAssets().open("intercom-tv.apk");
                 java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                byte[] b = new byte[64 * 1024];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            }
            pushApk(d, f);
        } catch (Exception e) {
            toast("读取对讲端失败：" + e.getMessage());
        }
    }

    /** 电视同屏到手机：走对讲端的 MJPEG 流（8768），不需要 Aerial token。 */
    /** 同屏是赞助功能：未解锁先弹赞助页（含支付宝/微信二维码 + 设备码 + 解锁码输入）。 */
    private void openMirror(final Device d) {
        requireSponsor(() -> startMirror(d));
    }

    /** 不再做功能门禁：同屏/对讲直接可用，赞助入口在「信息」页。 */
    private void requireSponsor(Runnable action) {
        action.run();
    }

    /** 「信息」页：版本、开源地址、赞助收款码。 */
    private void showAbout() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        box.setPadding(p, dp(4), p, 0);

        TextView ver = new TextView(this);
        String vn;
        try {
            vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            vn = "?";
        }
        ver.setText("Aerial 管家  v" + vn + "\n手机端管理 Aerial 电视桌面：扫码绑定、壁纸、应用、对讲、同屏");
        ver.setTextColor(theme.text);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        box.addView(ver);

        TextView links = new TextView(this);
        links.setText("\n开源地址（长按复制）\n"
                + "管家：github.com/huli2008/aerial-buddy\n"
                + "Aerial 桌面：github.com/pengchujin/aerial-tv\n"
                + "雷鸟/TCL guard 包：github.com/huli2008/aerial-guard-tcl");
        links.setTextColor(theme.dim);
        links.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        links.setTextIsSelectable(true);
        box.addView(links);

        TextView sponsor = new TextView(this);
        sponsor.setText("\n如果这个 App 帮到了你，欢迎扫码赞助，金额随意 ☕");
        sponsor.setTextColor(theme.text);
        sponsor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        box.addView(sponsor);

        LinearLayout qrs = new LinearLayout(this);
        qrs.setOrientation(LinearLayout.HORIZONTAL);
        qrs.setPadding(0, dp(10), 0, 0);
        qrs.addView(qrImage("sponsor_alipay.jpg", "支付宝"), new LinearLayout.LayoutParams(0, dp(210), 1f));
        qrs.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        qrs.addView(qrImage("sponsor_wechat.jpg", "微信"), new LinearLayout.LayoutParams(0, dp(210), 1f));
        box.addView(qrs);

        new AlertDialog.Builder(this)
                .setTitle("信息与赞助")
                .setView(box)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showSponsorDialog(final Runnable onUnlocked) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        box.setPadding(p, dp(6), p, 0);

        TextView tip = new TextView(this);
        tip.setText("「同屏」和「对讲」是赞助功能。两种开通方式：\n"
                + "· 在线购买兑换码（自动开通，一码一台电视）；\n"
                + "· 或扫码赞助后把「设备码」发我，我回一个专属码。");
        tip.setTextColor(theme.dim);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        box.addView(tip);

        LinearLayout qrs = new LinearLayout(this);
        qrs.setOrientation(LinearLayout.HORIZONTAL);
        qrs.setPadding(0, dp(10), 0, 0);
        qrs.addView(qrImage("sponsor_alipay.jpg", "支付宝"), new LinearLayout.LayoutParams(0, dp(190), 1f));
        qrs.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        qrs.addView(qrImage("sponsor_wechat.jpg", "微信"), new LinearLayout.LayoutParams(0, dp(190), 1f));
        box.addView(qrs);

        final TextView buy = new TextView(this);
        buy.setText("在线购买兑换码（自动开通）");
        buy.setGravity(Gravity.CENTER);
        buy.setTextColor(theme.accentText);
        buy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable bb = new GradientDrawable();
        bb.setCornerRadius(dp(14));
        bb.setColor(theme.accent);
        buy.setBackground(bb);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        blp.topMargin = dp(12);
        box.addView(buy, blp);
        buy.setOnClickListener(v -> {
            String url = storeUrl();
            if (url.isEmpty()) {
                toast("还没配置购买链接：在 store_url.txt 里填，或联系作者");
                return;
            }
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    Uri.parse(url)));
        });

        final TextView code = new TextView(this);
        code.setText("设备码：" + Unlock.deviceCode(this) + "\n（长按复制；长按两秒可设置授权服务器）");
        code.setTextColor(theme.text);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        code.setPadding(0, dp(12), 0, 0);
        code.setTextIsSelectable(true);
        code.setOnLongClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("device", Unlock.deviceCode(this)));
            toast("设备码已复制");
            showServerDialog();
            return true;
        });
        box.addView(code);

        final EditText input = new EditText(this);
        input.setHint("输入兑换码 / 专属码");
        input.setSingleLine(true);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("赞助后开启同屏与对讲")
                .setView(box)
                .setNegativeButton("关闭", null)
                .setPositiveButton("解锁", (dlg, w) -> {
                    final String typed = input.getText().toString();
                    if (Unlock.checkLocal(MainActivity.this, typed)) {
                        Unlock.setUnlocked(MainActivity.this, true);
                        toast("已解锁，感谢赞助！");
                        onUnlocked.run();
                        return;
                    }
                    toast("正在向授权服务器校验…");
                    new Thread(() -> {
                        String err = Unlock.redeem(MainActivity.this, typed);
                        runOnUiThread(() -> {
                            if (err == null) {
                                Unlock.setUnlocked(MainActivity.this, true);
                                toast("兑换成功，已解锁同屏与对讲");
                                onUnlocked.run();
                            } else {
                                toast(err);
                            }
                        });
                    }, "redeem").start();
                })
                .show();
    }

    private String storeUrl() {
        try {
            java.io.InputStream in = getAssets().open("store_url.txt");
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
            String line = r.readLine();
            r.close();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void showServerDialog() {
        final EditText e = new EditText(this);
        e.setHint("http://192.168.8.10:8787");
        e.setText(Unlock.serverUrl(this));
        e.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("授权服务器地址")
                .setMessage("填 tools/license-server.py 跑起来的那台机器的地址；留空则只能用手工专属码。")
                .setView(e)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    Unlock.setServerUrl(this, e.getText().toString());
                    toast("已保存");
                })
                .show();
    }
    /** A QR image with a caption under it. */
    private LinearLayout qrImage(final String asset, String caption) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            java.io.InputStream in = getAssets().open(asset);
            iv.setImageBitmap(BitmapFactory.decodeStream(in));
            in.close();
        } catch (Exception ignored) {
        }
        col.addView(iv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView c = new TextView(this);
        c.setText(caption);
        c.setGravity(Gravity.CENTER);
        c.setTextColor(theme.dim);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        col.addView(c);
        return col;
    }

    private void startMirror(final Device d) {
        new Thread(() -> {
            boolean ok;
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(d.ip(), MirrorServicePort.PORT), 1200);
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            final boolean reachable = ok;
            runOnUiThread(() -> {
                if (!reachable) {
                    new AlertDialog.Builder(this)
                            .setTitle("电视还没开始同屏")
                            .setMessage("先在电视上打开「Aerial 对讲端」→ 点「投屏到手机（同屏）」→ 在电视上选「立即开始」。\n\n"
                                    + "如果电视上还没装对讲端，先点卡片的「装对讲端」。")
                            .setNegativeButton("知道了", null)
                            .setPositiveButton("装对讲端", (dlg, w) -> installHelper(d))
                            .show();
                    return;
                }
                startActivity(new android.content.Intent(this, MirrorActivity.class)
                        .putExtra("ip", d.ip()));
            });
        }, "mirror-probe").start();
    }

    private void openApps(Device d) {
        startActivity(new Intent(this, AppListActivity.class)
                .putExtra("url", d.base())
                .putExtra("name", d.name)
                .putExtra("theme", theme.id));
    }

    /** Copies the bundled Aerial guard build and pushes it through 多屏互动. */
    private void installAerial(final Device d) {
        pushAsset(d, "aerial-guard.apk", true);
    }

    private void pushAsset(final Device d, final String asset, final boolean thenIntercom) {
        try {
            File out = new File(getCacheDir(), asset);
            InputStream in = getAssets().open(asset);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            in.close();
            Installer.push(this, d, out, (ok, msg) -> {
                toast(msg);
                if (ok && thenIntercom) installHelper(d);
                if (ok) refresh(d);
            });
        } catch (Exception e) {
            toast("读取 " + asset + " 失败：" + e.getMessage());
        }
    }

    /** True when the TV already answers Aerial's config port. */
    private static boolean hasAerial(String ip) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(ip, 8790), 900);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * First connection only: probe for the Aerial config port, and if the TV does not answer, ask
     * before pushing the bundled guard build. Later connections stay quiet — 「装 Aerial」 is manual.
     */
    private void autoInstallAerial(final Device d) {
        if (d.autoChecked) return;
        d.autoChecked = true;
        Store.save(this, devices);
        new Thread(() -> {
            boolean present = hasAerial(d.ip());
            runOnUiThread(() -> {
                if (present) {
                    d.status = "已装 Aerial（未绑定，扫码后可管壁纸/应用）";
                    d.dot = 2;
                    render();
                    return;
                }
                d.status = "未检测到 Aerial";
                render();
                new AlertDialog.Builder(this)
                        .setTitle("这台电视还没装 Aerial")
                        .setMessage("检测 " + d.ip() + ":8790 没有响应，说明电视上还没有 Aerial 桌面。\n\n"
                                + "现在通过电视的「多屏互动」推送安装？（安装包已内置，约 3 MB，"
                                + "电视上按遥控器「右键 → 同意」即可）")
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("立即安装", (dlg, w) -> installAerial(d))
                        .show();
            });
        }, "first-check").start();
    }

    private void openPanel(Device d) {
        startActivity(new Intent(this, WebActivity.class)
                .putExtra("url", d.base())
                .putExtra("theme", theme.id));
    }

    private void confirmDelete(Device d) {
        new AlertDialog.Builder(this)
                .setMessage("删除「" + d.name + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dlg, w) -> {
                    devices.remove(d);
                    Store.save(this, devices);
                    render();
                })
                .show();
    }

    // ---------- binding ----------

    private void scanLan() {
        final TextView progress = new TextView(this);
        progress.setText("正在扫描本网段…");
        progress.setTextColor(theme.dim);
        progress.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int p = dp(20);
        progress.setPadding(p, dp(16), p, dp(16));
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("扫描 Aerial 电视")
                .setView(progress)
                .setNegativeButton("取消", null)
                .create();
        dlg.show();

        Scanner.scan(this, new Scanner.Cb() {
            @Override
            public void onProgress(int scanned, int total) {
                progress.setText("已扫描 " + scanned + "/" + total + " …");
            }

            @Override
            public void onFinished(java.util.List<String> aerialIps, java.util.List<String> tcastIps) {
                if (!dlg.isShowing()) return;
                dlg.dismiss();
                int total = aerialIps.size() + tcastIps.size();
                if (total == 0) {
                    toast("没发现电视：确认电视开机、与本机同一 Wi-Fi");
                    return;
                }
                int added = 0;
                for (String ip : aerialIps) {
                    if (findByIp(ip) != null) continue;
                    devices.add(newDevice(ip));
                    added++;
                }
                for (String ip : tcastIps) {
                    if (findByIp(ip) != null) continue;
                    devices.add(newDevice(ip));
                    added++;
                }
                Store.save(MainActivity.this, devices);
                render();
                if (added == 0) {
                    toast("发现 " + total + " 台，都已在列表里");
                } else {
                    toast("已添加 " + added + " 台，正在检查是否已装 Aerial…");
                }
                for (Device d : devices) {
                    if (!d.bound()) autoInstallAerial(d);
                }
            }

            @Override
            public void onError(String message) {
                if (dlg.isShowing()) dlg.dismiss();
                toast(message);
            }
        });
    }

    /** 扫码绑定：直接开相机，不弹菜单；相机不可用时退回相册。 */
    private void chooseScanSource() {
        captureQr();
    }

    private Device findByIp(String ip) {
        for (Device d : devices) if (ip.equals(d.ip())) return d;
        return null;
    }

    /** A TV known only by IP: install works right away, binding comes later (or never). */
    private Device newDevice(String ip) {
        Device d = new Device();
        d.id = String.valueOf(System.currentTimeMillis() + Math.random() * 1000);
        d.name = "电视 " + ip.substring(ip.lastIndexOf('.') + 1);
        d.host = ip;
        d.url = "";
        d.pending = true;
        return d;
    }

    private void pickQr() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "选择含二维码的照片"), REQ_QR_PICK);
    }

    private void captureQr() {
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "aerial_qr_" + System.currentTimeMillis() + ".jpg");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cameraOutput = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutput);
            startActivityForResult(i, REQ_QR_CAMERA);
        } catch (Exception e) {
            toast("相机不可用，改为从相册选二维码");
            pickQr();
        }
    }

    /** 手动输入只要 IP：端口与路径是固定的，不显示也不需要填。 */
    private void showUrlDialog(final String preset) {
        EditText e = new EditText(this);
        e.setHint("192.168.8.192");
        e.setText(preset == null ? "" : preset);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        int p = dp(20);
        e.setPadding(p, dp(10), p, dp(10));
        new AlertDialog.Builder(this)
                .setTitle("电视 IP")
                .setView(e)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", (dlg, w) -> addByIp(e.getText().toString()))
                .show();
    }

    private void addByIp(String raw) {
        String h = raw == null ? "" : raw.trim();
        h = h.replace("http://", "").replace("https://", "");
        int slash = h.indexOf('/');
        if (slash >= 0) h = h.substring(0, slash);
        int colon = h.indexOf(':');
        if (colon > 0) h = h.substring(0, colon);
        if (!h.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            toast("请输入电视 IP，例如 192.168.8.192");
            return;
        }
        Device known = findByIp(h);
        if (known != null) {
            toast("这台电视已经在列表里");
            if (known.bound()) openPanel(known);
            return;
        }
        Device added = newDevice(h);
        devices.add(added);
        Store.save(this, devices);
        render();
        toast("已添加 " + h + "，正在检查是否已装 Aerial…");
        autoInstallAerial(added);
    }

    private void bind(String url) {
        if (url == null || url.isEmpty()) {
            toast("地址为空");
            return;
        }
        String hostPart = url.replace("http://", "").replace("https://", "");
        int slash = hostPart.indexOf('/');
        if (slash > 0) hostPart = hostPart.substring(0, slash);
        String ip = hostPart.contains(":") ? hostPart.substring(0, hostPart.indexOf(':')) : hostPart;

        for (Device d : devices) {
            if (d.bound() && d.base().equals(url)) {
                pendingScanIp = null;
                toast("这台电视已经绑定过了");
                openPanel(d);
                return;
            }
        }
        Device target = findByIp(ip);
        if (target == null) {
            target = newDevice(ip);
            devices.add(target);
        }
        target.host = ip;
        target.url = url;
        target.pending = false;
        pendingScanIp = null;
        Store.save(this, devices);
        render();
        refresh(target);
        openPanel(target);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        if (req == REQ_QR_PICK && data != null && data.getData() != null) {
            decodeUri(data.getData());
        } else if (req == REQ_QR_CAMERA && cameraOutput != null) {
            decodeUri(cameraOutput);
        } else if (req == REQ_APK && data != null && data.getData() != null) {
            handleApk(data.getData());
        }
    }

    private void decodeUri(final Uri uri) {
        toast("正在识别二维码…");
        new Thread(() -> {
            String text = null;
            String error = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                InputStream b1 = getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(b1, null, bounds);
                if (b1 != null) b1.close();
                BitmapFactory.Options opts = new BitmapFactory.Options();
                int big = Math.max(bounds.outWidth, bounds.outHeight);
                int sample = 1;
                while (big / sample > 2400) sample *= 2;
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                InputStream in = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
                if (in != null) in.close();
                text = Qr.decode(bmp);
            } catch (Throwable e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String t = text;
            final String err = error;
            runOnUiThread(() -> {
                if (t == null) {
                    toast(err == null ? "没识别到二维码：把二维码拍满一点、避开反光再试" : ("读取图片失败：" + err));
                    return;
                }
                String url = Qr.toBaseUrl(t);
                if (url == null || !url.contains(":8790")) {
                    toast("二维码内容不是电视地址：" + t);
                    return;
                }
                bind(url);
            });
        }, "qr-decode").start();
    }

    // ---------- install ----------

    private void startInstall(Device d) {
        installing = d;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "选择 APK"), REQ_APK);
    }

    private void handleApk(Uri uri) {
        final Device d = installing;
        if (d == null) return;
        try {
            String name = "install-" + System.currentTimeMillis() + ".apk";
            File f = new File(getCacheDir(), name);
            InputStream in = getContentResolver().openInputStream(uri);
            FileOutputStream out = new FileOutputStream(f);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            pushApk(d, f);
        } catch (Exception e) {
            toast("读取 APK 失败：" + e.getMessage());
        }
    }

    private void pushApk(final Device d, File f) {
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo info = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);
            if (info == null) {
                toast("这不是有效的 APK");
                return;
            }
            String pkg = info.packageName;
            int vc = info.versionCode;
            String label = pkg;
            ApplicationInfo ai = info.applicationInfo;
            if (ai != null) {
                ai.sourceDir = f.getAbsolutePath();
                ai.publicSourceDir = f.getAbsolutePath();
                CharSequence l = pm.getApplicationLabel(ai);
                if (l != null) label = l.toString();
            }

            String ip = ApkServer.localIp();
            if (ip == null) {
                toast("手机没连 Wi-Fi");
                return;
            }
            ApkServer server = new ApkServer();
            server.start(f, 8765, null);
            String url = "http://" + ip + ":8765/" + f.getName();

            final android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
            pd.setTitle("推送到 " + d.name);
            pd.setMessage("正在发起…");
            pd.setCancelable(false);
            pd.show();

            final String finalPkg = pkg, finalLabel = label;
            Tcast.push(d.ip(), url, finalPkg, finalLabel, vc, new Tcast.Cb() {
                @Override
                public void onPrompt() {
                    runOnUiThread(() -> pd.setMessage("电视上弹出安装提示，请按遥控器「右键」选同意"));
                }

                @Override
                public void onProgress(int stage, int percent) {
                    runOnUiThread(() -> pd.setMessage(
                            stage == 3 ? ("正在安装 " + percent + "%") : ("传输中 " + percent + "%")));
                }

                @Override
                public void onInstalled() {
                    server.stop();
                    runOnUiThread(() -> {
                        pd.dismiss();
                        toast("已安装到 " + d.name);
                        refresh(d);
                    });
                }

                @Override
                public void onFailed(String reason) {
                    server.stop();
                    runOnUiThread(() -> {
                        pd.dismiss();
                        toast("安装失败：" + reason);
                    });
                }

                @Override
                public void onLog(String line) {
                    runOnUiThread(() -> pd.setMessage(line));
                }
            });
        } catch (Exception e) {
            toast("处理 APK 失败：" + e.getMessage());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
