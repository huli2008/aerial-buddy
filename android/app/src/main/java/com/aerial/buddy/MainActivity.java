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
    private static final int REQ_RECEIPT = 31;

    private final List<Device> devices = new ArrayList<>();
    private Theme theme;
    private Backdrop backdrop;
    private FrameLayout root;
    private LinearLayout list;
    private int currentIndex;
    private static final String PREF_INDEX = "current_device_index";
    private TextView countView;

    private Uri cameraOutput;
    private Runnable unlockContinuation;
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
        scroll.setPadding(0, dp(12), 0, dp(128));
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
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(0, dp(48), 0.9f);
        fLp.rightMargin = dp(10);
        bottom.addView(find, fLp);
        TextView scan = actionButton("扫码绑定", false);
        scan.setOnClickListener(v -> chooseScanSource());
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(48), 1.1f);
        sLp.rightMargin = dp(10);
        bottom.addView(scan, sLp);
        TextView add = actionButton("手动输入", true);
        add.setOnClickListener(v -> showUrlDialog(null));
        bottom.addView(add, new LinearLayout.LayoutParams(0, dp(48), 1f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(pad, 0, pad, dp(22));
        lp.gravity = Gravity.BOTTOM;
        root.addView(bottom, lp);

        Trial.init(this);
        runDueSchedules();
        if (!getSharedPreferences("aerial_buddy", MODE_PRIVATE).getBoolean("help_seen", false)) {
            root.post(() -> showHelp(true));
        }
        setContentView(root);

        devices.addAll(Store.load(this));
        currentIndex = getSharedPreferences("aerial_buddy", MODE_PRIVATE).getInt(PREF_INDEX, 0);
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
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(theme.text);
        row.addView(title);

        countView = new TextView(this);
        countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        countView.setTextColor(theme.dim);
        countView.setPadding(dp(8), 0, 0, 0);
        row.addView(countView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 主题切换收成三个小圆点，省掉一整行
        for (final Theme t : Theme.ALL) {
            boolean on = t.id == theme.id;
            TextView dot = new TextView(this);
            dot.setText(on ? "●" : "○");
            dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            dot.setTextColor(on ? t.accent : theme.dim);
            dot.setPadding(dp(6), 0, dp(6), 0);
            dot.setOnClickListener(v -> {
                theme = t;
                Store.setTheme(MainActivity.this, t.id);
                recreate();
            });
            row.addView(dot);
        }

        TextView about = new TextView(this);
        about.setText("ⓘ");
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        about.setTextColor(theme.text);
        about.setPadding(dp(12), dp(4), dp(4), dp(4));
        about.setOnClickListener(v -> showAbout());
        row.addView(about);

        box.addView(row);
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
        if (currentIndex >= devices.size()) currentIndex = devices.size() - 1;
        if (currentIndex < 0) currentIndex = 0;
        getSharedPreferences("aerial_buddy", MODE_PRIVATE).edit()
                .putInt(PREF_INDEX, currentIndex).apply();
        list.addView(pagerHeader());
        list.addView(cardFor(devices.get(currentIndex)));
    }

    /** 一设备一整页：顶部 ‹ 名称 ▾ ›，点名称弹出设备列表。 */
    private View pagerHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(2), 0, dp(10));

        TextView prev = new TextView(this);
        prev.setText("‹");
        prev.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        prev.setTextColor(currentIndex > 0 ? theme.text : theme.cardStroke);
        prev.setPadding(dp(10), dp(2), dp(14), dp(2));
        prev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                render();
            }
        });
        bar.addView(prev);

        Device d = devices.get(currentIndex);
        TextView name = new TextView(this);
        name.setText(d.name + "  ▾");
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setTextColor(theme.text);
        name.setGravity(Gravity.CENTER);
        name.setOnClickListener(v -> showDevicePicker());
        bar.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView next = new TextView(this);
        next.setText("›");
        next.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        next.setTextColor(currentIndex < devices.size() - 1 ? theme.text : theme.cardStroke);
        next.setPadding(dp(14), dp(2), dp(10), dp(2));
        next.setOnClickListener(v -> {
            if (currentIndex < devices.size() - 1) {
                currentIndex++;
                render();
            }
        });
        bar.addView(next);
        return bar;
    }

    /** 弹出设备列表，点一项切到那一页。 */
    private void showDevicePicker() {
        final String[] items = new String[devices.size() + 1];
        for (int i = 0; i < devices.size(); i++) {
            Device d = devices.get(i);
            String mark = i == currentIndex ? "● " : "○ ";
            items[i] = mark + d.name + "   " + (d.bound() ? "已绑定" : "未绑定") + "   " + d.ip();
        }
        items[devices.size()] = "＋ 添加新电视";
        LinearLayout pbox = new LinearLayout(this);
        pbox.setOrientation(LinearLayout.VERTICAL);
        final android.app.Dialog[] ref = new android.app.Dialog[1];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView row = new TextView(this);
            row.setText(items[i]);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            row.setTextColor(i == devices.size() ? theme.accent : theme.text);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            GradientDrawable rbg = new GradientDrawable();
            rbg.setCornerRadius(dp(14));
            rbg.setColor(i == currentIndex ? theme.glass : theme.card);
            rbg.setStroke(dp(1), i == currentIndex ? theme.accent : theme.cardStroke);
            row.setBackground(rbg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                ref[0].dismiss();
                if (idx == devices.size()) {
                    showUrlDialog(null);
                    return;
                }
                currentIndex = idx;
                render();
            });
            pbox.addView(row);
        }
        ref[0] = new GlassDialog.Builder(this, theme)
                .title(devices.size() + " 台电视")
                .content(pbox)
                .negative("关闭", null)
                .show();
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
        // 分组：常用 2x3 大按钮（原 5 项 + 管理），维护 4 小按钮；一项都不少，只是不再挤成小胶囊。
        card.addView(sectionLabel("常用"));
        LinearLayout g1 = new LinearLayout(this);
        g1.setOrientation(LinearLayout.HORIZONTAL);
        g1.addView(bigBtn("🎛  遥控", "红外遥控电视", () -> startActivity(new Intent(this, IrRemoteActivity.class)
                .putExtra("name", d.name).putExtra("theme", theme.id))), bigLp());
        g1.addView(bigBtn("🖼  壁纸", "换电视壁纸", () -> ifBound(d, () -> startActivity(new Intent(this, WallpaperActivity.class)
                .putExtra("url", d.base()).putExtra("name", d.name).putExtra("theme", theme.id)))), bigLp());
        card.addView(g1);

        LinearLayout g2 = new LinearLayout(this);
        g2.setOrientation(LinearLayout.HORIZONTAL);
        g2.addView(bigBtn("📦  应用", "打开/卸载/安装", () -> ifBound(d, () -> openApps(d))), bigLp());
        g2.addView(bigBtn("💬  互动", "对讲 / 弹幕 / 喊话", () -> ifBound(d, () -> requireSponsor(() -> startActivity(new Intent(this, InteractionActivity.class)
                .putExtra("ip", d.ip()).putExtra("name", d.name).putExtra("theme", theme.id))))), bigLp());
        card.addView(g2);

        LinearLayout g3 = new LinearLayout(this);
        g3.setOrientation(LinearLayout.HORIZONTAL);
        g3.addView(bigBtn("📺  同屏", "看电视画面", () -> openMirror(d)), bigLp());
        g3.addView(bigBtn("⚙️  管理", "电视配置页", () -> ifBound(d, () -> openPanel(d))), bigLp());
        card.addView(g3);

        card.addView(sectionLabel("维护"));
        LinearLayout g4 = new LinearLayout(this);
        g4.setOrientation(LinearLayout.HORIZONTAL);
        g4.addView(smallBtn("⬇ 装Aerial", () -> installAerial(d)), smallLp());
        g4.addView(smallBtn("🔌 对讲端", () -> installHelper(d)), smallLp());
        g4.addView(smallBtn("🩺 自检", () -> startActivity(new Intent(this, DiagActivity.class)
                .putExtra("ip", d.ip()).putExtra("name", d.name).putExtra("theme", theme.id))), smallLp());
        g4.addView(smallBtn("⟳ 刷新", () -> refresh(d)), smallLp());
        card.addView(g4);
        LinearLayout g5 = new LinearLayout(this);
        g5.setOrientation(LinearLayout.HORIZONTAL);
        g5.setPadding(0, dp(8), 0, 0);
        card.addView(g5);
        TextView more = smallBtn("更多 ▾", this::showMoreMenu);
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        moreLp.topMargin = dp(8);
        card.addView(more, moreLp);

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

    /** 更多：玻璃卡片九宫格（图标 + 名称），点开进入各功能。 */
    private void showMoreMenu() {
        final Device d = devices.get(currentIndex);
        String[][] items = {
                {"🎬", "场景模式"}, {"⏰", "定时任务"}, {"🔊", "喊话"},
                {"👨‍👩‍👧", "家长控制"}, {"🖼", "换图标"}, {"📷", "相册直投"},
                {"📋", "设备信息"}, {"🩺", "自检"}
        };
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(10), dp(14), dp(6));

        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                wrap.addView(row);
            }
            final String label = items[i][1];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(theme.card);
            bg.setStroke(dp(1), theme.cardStroke);
            cell.setBackground(bg);

            TextView icon = new TextView(this);
            icon.setText(items[i][0]);
            icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            icon.setGravity(Gravity.CENTER);
            cell.addView(icon);

            TextView name = new TextView(this);
            name.setText(label);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            name.setTextColor(theme.text);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(4), 0, 0);
            cell.addView(name);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(86), 1f);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            cell.setLayoutParams(lp);
            cell.setOnClickListener(v -> onMoreItem(label, d));
            if (row != null) row.addView(cell);
        }

        new AlertDialog.Builder(this)
                .setTitle("更多功能")
                .setView(wrap)
                .setNegativeButton("关闭", null)
                .show();
    }
    private void onMoreItem(String label, Device d) {
        switch (label) {
            case "设备信息":
                showDeviceInfo(d);
                break;
            case "场景模式":
                showScenes(d);
                break;
            case "定时任务":
                showSchedules();
                break;
            case "自检":
                startActivity(new Intent(this, DiagActivity.class)
                        .putExtra("ip", d.ip()).putExtra("name", d.name).putExtra("theme", theme.id));
                break;
            case "喊话":
            case "家长控制":
            case "换图标":
            case "相册直投":
            case "应用整理":
                toast(label + "：界面已预留，功能下个版本开放");
                break;
            default:
                break;
        }
    }

    private void showDeviceInfo(final Device d) {
        StringBuilder sb = new StringBuilder();
        sb.append("名称：").append(d.name).append('\n');
        sb.append("IP：").append(d.ip()).append('\n');
        sb.append("管理端口：8790").append('\n');
        sb.append("对讲端口：8766").append('\n');
        sb.append("同屏端口：8768").append('\n');
        sb.append("助手端口：8769").append('\n');
        sb.append("ADB 端口：5555").append('\n');
        sb.append("绑定状态：").append(d.bound() ? "已绑定" : "未绑定").append('\n');
        sb.append("当前状态：").append(d.status == null ? "未知" : d.status);
        new AlertDialog.Builder(this)
                .setTitle("设备信息")
                .setMessage(sb.toString())
                .setNegativeButton("关闭", null)
                .setPositiveButton("自检", (dlg, w) -> startActivity(new Intent(this, DiagActivity.class)
                        .putExtra("ip", d.ip()).putExtra("name", d.name).putExtra("theme", theme.id)))
                .show();
    }

    /** 全局批量：对所有电视执行同一动作。 */
    private void showBatchMenu() {
        if (devices.isEmpty()) {
            toast("还没有电视");
            return;
        }
        String[] actions = {"🔄  全部刷新", "🔇  全部静音", "⌂  全部回主页", "⬇  全部装 Aerial"};
        LinearLayout bbox = new LinearLayout(this);
        bbox.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < actions.length; i++) {
            final int idx = i;
            TextView row = new TextView(this);
            row.setText(actions[i]);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            row.setTextColor(theme.text);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            GradientDrawable rbg = new GradientDrawable();
            rbg.setCornerRadius(dp(14));
            rbg.setColor(theme.card);
            rbg.setStroke(dp(1), theme.cardStroke);
            row.setBackground(rbg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                if (idx == 3) {
                    GlassDialog.text(this, theme, "全部装 Aerial",
                            "将依次向 " + devices.size() + " 台电视推送 Aerial（3.01 MB/台），每台都需要在电视上按一次「同意」。",
                            "开始推送", () -> runBatch(3));
                } else {
                    runBatch(idx);
                }
            });
            bbox.addView(row);
        }
        new GlassDialog.Builder(this, theme)
                .title("批量操作 · " + devices.size() + " 台电视")
                .content(bbox)
                .negative("取消", null)
                .show();
    }

    private void runBatch(int which) {
        final String[] paths = {null, "/volume?d=mute", "/key?code=HOME", null};
        toast("正在对 " + devices.size() + " 台电视执行…");
        new Thread(() -> {
            for (Device d : devices) {
                if (which == 0) {
                    refresh(d);
                } else if (which == 3) {
                    runOnUiThread(() -> installAerial(d));
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    try (java.net.Socket s = new java.net.Socket()) {
                        s.connect(new java.net.InetSocketAddress(d.ip(), 8769), 900);
                        java.io.OutputStream out = s.getOutputStream();
                        out.write(("GET " + paths[which] + " HTTP/1.1\r\nHost: " + d.ip()
                                + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                        out.flush();
                    } catch (Exception ignored) {
                    }
                }
            }
            runOnUiThread(() -> toast("批量操作已下发"));
        }, "batch").start();
    }

    // ---------- 场景模式 ----------

    private void showScenes(final Device d) {
        String[] items = {"看电影（回 Aerial + 音量适中）", "睡觉（关屏 + 静音）", "回家（回 Aerial + 音量调高）"};
        new AlertDialog.Builder(this)
                .setTitle("场景模式")
                .setItems(items, (dlg, which) -> runScene(d, which))
                .setNegativeButton("取消", null)
                .show();
    }

    private void runScene(final Device d, int which) {
        new Thread(() -> {
            int okCount = 0, total = 0;
            switch (which) {
                case 0:
                    total = 3;
                    if (helperOk(d, "/key?code=HOME")) okCount++;
                    if (helperOk(d, "/volume?d=down")) okCount++;
                    if (helperOk(d, "/volume?d=down")) okCount++;
                    break;
                case 1:
                    total = 2;
                    if (adbKeyOk(d, "KEYCODE_SLEEP")) okCount++;
                    if (helperOk(d, "/volume?d=mute")) okCount++;
                    break;
                default:
                    total = 4;
                    if (helperOk(d, "/key?code=HOME")) okCount++;
                    if (helperOk(d, "/volume?d=up")) okCount++;
                    if (helperOk(d, "/volume?d=up")) okCount++;
                    if (helperOk(d, "/volume?d=up")) okCount++;
                    break;
            }
            final int ok = okCount, all = total;
            runOnUiThread(() -> toast(ok == all ? "场景已完全执行"
                    : ("部分成功 " + ok + "/" + all + "：未授权 ADB 时关屏/音量可能不生效")));
        }, "scene").start();
    }

    private boolean helperOk(Device d, String path) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(d.ip(), 8769), 1200);
            java.io.OutputStream out = s.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: " + d.ip() + "\r\nConnection: close\r\n\r\n")
                    .getBytes("UTF-8"));
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean adbKeyOk(final Device d, final String keycode) {
        try {
            AdbSession s = AdbSession.get(d.ip());
            if (s.ensure(this)) return s.exec("input keyevent " + keycode, null) != null;
        } catch (Exception ignored) {
        }
        return false;
    }

    private void helperGet(Device d, String path) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(d.ip(), 8769), 1200);
            java.io.OutputStream out = s.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: " + d.ip() + "\r\nConnection: close\r\n\r\n")
                    .getBytes("UTF-8"));
            out.flush();
        } catch (Exception ignored) {
        }
    }

    private void adbKey(final Device d, final String keycode) {
        try {
            AdbSession s = AdbSession.get(d.ip());
            if (s.ensure(this)) s.exec("input keyevent " + keycode, null);
        } catch (Exception ignored) {
        }
    }

    // ---------- 定时任务 ----------

    private void showSchedules() {
        final android.content.SharedPreferences p =
                getSharedPreferences("aerial_schedule", MODE_PRIVATE);
        final boolean screenOff = p.getBoolean("screen_off", false);
        final boolean homeMorning = p.getBoolean("home_morning", false);
        final String[] items = {
                "每天 22:00 关屏" + (screenOff ? "（已开）" : "（已关）"),
                "每天 07:00 回 Aerial" + (homeMorning ? "（已开）" : "（已关）")
        };
        LinearLayout sbox = new LinearLayout(this);
        sbox.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView row = new TextView(this);
            row.setText(items[i]);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            row.setTextColor(theme.text);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            GradientDrawable rbg = new GradientDrawable();
            rbg.setCornerRadius(dp(14));
            rbg.setColor(theme.card);
            rbg.setStroke(dp(1), theme.cardStroke);
            row.setBackground(rbg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                boolean now = idx == 0 ? !screenOff : !homeMorning;
                p.edit().putBoolean(idx == 0 ? "screen_off" : "home_morning", now).apply();
                toast(now ? "已开启（App 打开时执行）" : "已关闭");
                showSchedules();
            });
            sbox.addView(row);
        }
        new GlassDialog.Builder(this, theme)
                .title("定时任务")
                .content(sbox)
                .negative("关闭", null)
                .show();
    }

    /** App 启动时补执行错过的定时任务（纯本地，不常驻后台）。 */
    private void runDueSchedules() {
        android.content.SharedPreferences p = getSharedPreferences("aerial_schedule", MODE_PRIVATE);
        if (devices.isEmpty()) return;
        Device d = devices.get(Math.min(currentIndex, devices.size() - 1));
        java.util.Calendar c = java.util.Calendar.getInstance();
        int hour = c.get(java.util.Calendar.HOUR_OF_DAY);
        String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date());
        if (p.getBoolean("screen_off", false) && hour >= 22 && !today.equals(p.getString("last_off", ""))) {
            p.edit().putString("last_off", today).apply();
            runScene(d, 1);
        }
        if (p.getBoolean("home_morning", false) && hour >= 7 && !today.equals(p.getString("last_home", ""))) {
            p.edit().putString("last_home", today).apply();
            helperGet(d, "/key?code=HOME");
        }
    }

    private TextView messageText(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setTextColor(theme.dim);
        v.setPadding(0, dp(2), 0, dp(2));
        return v;
    }

    private View sectionLabel(String text) {
        return UiKit.sectionLabel(this, theme, text);
    }

    private LinearLayout.LayoutParams bigLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(78), 1f);
        lp.rightMargin = dp(10);
        lp.bottomMargin = dp(10);
        return lp;
    }

    private LinearLayout.LayoutParams smallLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.rightMargin = dp(8);
        return lp;
    }

    private LinearLayout bigBtn(String label, String sub, Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(theme.card);
        bg.setStroke(dp(1), theme.cardStroke);
        box.setBackground(bg);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(theme.text);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        s.setTextColor(theme.dim);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(3), 0, 0);
        box.addView(s);
        box.setOnClickListener(v -> action.run());
        box.setClickable(true);
        return box;
    }

    private TextView smallBtn(String label, Runnable action) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        v.setTextColor(theme.text);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(theme.card);
        bg.setStroke(dp(1), theme.cardStroke);
        v.setBackground(bg);
        v.setOnClickListener(x -> action.run());
        return v;
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
                runOnUiThread(() -> GlassDialog.list(MainActivity.this, theme, "换壁纸 · " + d.name, titles, (which) -> {
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
                        }));
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
                runOnUiThread(() -> GlassDialog.list(MainActivity.this, theme,
                        "已安装应用 · " + apps.length() + " 个", labels,
                        which -> confirmUninstall(d, keys[which], labels[which])));
            } catch (Exception e) {
                runOnUiThread(() -> toast("获取失败：" + e.getMessage()));
            }
        }, "apps").start();
    }

    private void confirmUninstall(final Device d, final String pkg, String label) {
        String shown = label.split("\n")[0];
        new GlassDialog.Builder(this, theme)
                .title("卸载 " + shown)
                .content(messageText(pkg + "\n\n通过电视的「多屏互动」执行；系统应用可能被固件保护而失败。"))
                .negative("取消", null)
                .positive("卸载", () -> doUninstall(d, pkg, shown))
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

    /** 请电视端助手自己启动同屏（0.7.4+ 支持），再等待端口打开。 */
    private void askTvToStartMirror(final Device d) {
        toast("正在请电视自动开始同屏…");
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL("http://" + d.ip() + ":8769/mirror/start").openConnection();
                c.setConnectTimeout(2000);
                c.setReadTimeout(4000);
                c.getInputStream().read(new byte[64]);
                c.disconnect();
            } catch (Exception ignored) {
            }
            for (int i = 0; i < 8; i++) {
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    return;
                }
                try (java.net.Socket s = new java.net.Socket()) {
                    s.connect(new java.net.InetSocketAddress(d.ip(), 8768), 800);
                    runOnUiThread(() -> openMirror(d));
                    return;
                } catch (Exception ignored) {
                }
            }
            runOnUiThread(() -> toast("电视仍未开始同屏：请检查电视端助手是否有录屏权限"));
        }, "mirror-start").start();
    }

    /** 助手 8769 是否在线，用来区分「没装助手」和「助手装了但没开同屏」。 */
    /** 8769 端口是否有人监听（旧版助手也有，但功能少）。 */
    private boolean helperPortOpen(final Device d) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(d.ip(), 8769), 900);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean helperHealth(final Device d) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(d.ip(), 8769), 1200);
            java.io.OutputStream out = s.getOutputStream();
            out.write(("GET /health HTTP/1.1\r\nHost: " + d.ip() + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            out.flush();
            byte[] buf = new byte[512];
            int n = s.getInputStream().read(buf);
            return n > 0 && new String(buf, 0, n, "UTF-8").contains("\"ok\":true");
        } catch (Exception e) {
            return false;
        }
    }

    /** 电视同屏到手机：走对讲端的 MJPEG 流（8768），不需要 Aerial token。 */
    /** 同屏是赞助功能：未解锁先弹赞助页（含支付宝/微信二维码 + 设备码 + 解锁码输入）。 */
    private void openMirror(final Device d) {
        requireSponsor(() -> startMirror(d));
    }

    /** 同屏 / 对讲：已解锁直接可用；3 天试用期内可用；否则弹解锁页。 */
    /**
     * 不再做付费解锁：所有功能直接可用。
     * 赞助改为自愿支持（信息页 → 赞助），不影响任何功能。
     */
    private void requireSponsor(Runnable action) {
        action.run();
    }

    /** 解锁页：收款码 + ¥10 + 上传支付截图（本地 OCR 校验）。 */
    private void showUnlockPage(final Runnable onUnlocked) {
        unlockContinuation = onUnlocked;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        box.setPadding(p, dp(4), p, 0);

        TextView trial = new TextView(this);
        trial.setText(Trial.left(this) + "。解锁后「同屏」和「对讲」永久可用。");
        trial.setTextColor(theme.text);
        trial.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        box.addView(trial);

        TextView price = new TextView(this);
        price.setText("¥" + ReceiptCheck.PRICE + " 永久解锁（扫码支付后上传支付成功截图）");
        price.setTextColor(theme.dim);
        price.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        price.setPadding(0, dp(6), 0, 0);
        box.addView(price);

        LinearLayout qrs = new LinearLayout(this);
        qrs.setOrientation(LinearLayout.HORIZONTAL);
        qrs.setPadding(0, dp(10), 0, 0);
        qrs.addView(qrImage("sponsor_alipay.jpg", "支付宝"), new LinearLayout.LayoutParams(0, dp(190), 1f));
        qrs.addView(new View(this), new LinearLayout.LayoutParams(dp(10), 1));
        qrs.addView(qrImage("sponsor_wechat.jpg", "微信"), new LinearLayout.LayoutParams(0, dp(190), 1f));
        box.addView(qrs);

        TextView upload = new TextView(this);
        upload.setText("上传支付截图，自动校验");
        upload.setGravity(Gravity.CENTER);
        upload.setTextColor(theme.accentText);
        upload.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable ub = new GradientDrawable();
        ub.setCornerRadius(dp(14));
        ub.setColor(theme.accent);
        upload.setBackground(ub);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        ulp.topMargin = dp(12);
        box.addView(upload, ulp);

        TextView manualTip = new TextView(this);
        manualTip.setText("\n识别不出来？把「设备码」发我换人工解锁码：");
        manualTip.setTextColor(theme.dim);
        manualTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        box.addView(manualTip);

        final TextView dev = new TextView(this);
        dev.setText("设备码：" + Unlock.deviceCode(this) + "（长按复制）");
        dev.setTextColor(theme.text);
        dev.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dev.setTextIsSelectable(true);
        dev.setOnLongClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("device", Unlock.deviceCode(this)));
            toast("设备码已复制");
            return true;
        });
        box.addView(dev);

        final EditText codeInput = new EditText(this);
        codeInput.setHint("输入人工解锁码，如 K7F2-9QXM");
        codeInput.setSingleLine(true);
        box.addView(codeInput);
        final android.app.Dialog dlg = new GlassDialog.Builder(this, theme)
                .title("解锁同屏与对讲")
                .content(box)
                .negative("稍后", null)
                .positive("用解锁码开通", () -> {
                    if (Unlock.checkLocal(this, codeInput.getText().toString())) {
                        Unlock.setUnlocked(this, true);
                        toast("已解锁，感谢支持！");
                        if (unlockContinuation != null) {
                            Runnable r = unlockContinuation;
                            unlockContinuation = null;
                            r.run();
                        }
                    } else {
                        toast("解锁码不对，请核对设备码是否一致");
                    }
                })
                .show();

        upload.setOnClickListener(v -> {
            dlg.dismiss();
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "选择支付成功截图"), REQ_RECEIPT);
        });

        dlg.show();
    }

    private void handleReceipt(Uri uri) {
        toast("正在识别支付截图…");
        new Thread(() -> {
            boolean ok = false;
            String reason;
            try {
                android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                InputStream b1 = getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(b1, null, bounds);
                if (b1 != null) b1.close();
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                int big = Math.max(bounds.outWidth, bounds.outHeight);
                int sample = 1;
                while (big / sample > 2000) sample *= 2;
                opts.inSampleSize = sample;
                InputStream in = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
                if (in != null) in.close();
                String text = ReceiptOcr.text(this, bmp);
                ReceiptCheck.Result r = ReceiptCheck.check(text);
                ok = r.ok;
                reason = r.ok ? "校验通过，已解锁" : r.reason;
            } catch (Throwable t) {
                reason = "识别失败：" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
            }
            final boolean success = ok;
            final String msg = reason;
            runOnUiThread(() -> {
                toast(msg);
                if (success) {
                    Unlock.setUnlocked(this, true);
                    if (unlockContinuation != null) {
                        Runnable r = unlockContinuation;
                        unlockContinuation = null;
                        r.run();
                    }
                }
            });
        }, "receipt-ocr").start();
    }
    /** 「信息」页：版本、开源地址、赞助收款码。 */
    /** 帮助：用法 + 兼容性，首次启动自动弹一次。 */
    private void showHelp(boolean firstRun) {
        String text = "【怎么用】\n"
                + "1. 手机和电视连同一个 Wi-Fi\n"
                + "2. 点「＋」或顶部「批量」旁的按钮添加电视：扫描 / 扫码 / 手填 IP\n"
                + "3. 卡片上：遥控、壁纸、应用、互动、同屏、管理\n"
                + "4. 「更多」里还有场景模式、定时任务、喊话、设备信息、自检\n\n"
                + "【两种连接方式】\n"
                + "· 绑定（扫码）：能管壁纸、应用、图标、儿童模式，需要电视上 Aerial 正在运行\n"
                + "· 不绑定（只要 IP）：能装 APK、用助手列应用/卸载/对讲/同屏/遥控部分功能\n\n"
                + "【兼容性】\n"
                + "TCL / 雷鸟（Android 9~11）：装 APK 走多屏互动 6553，最稳；同屏在 Android 9 需电视上按一次\n"
                + "小米 / 红米、索尼、飞利浦、海信、创维：装 APK 走 ADB（电视要开网络调试），应用/对讲/同屏可用\n"
                + "华为智慧屏（HarmonyOS）：不是 Android，装不了 APK，本 App 不适用\n"
                + "其它 Android TV：绑定 Aerial 后管理功能都可用；遥控优先 ADB，其次蓝牙 HID\n\n"
                + "【常见问题】\n"
                + "· 扫不到电视：确认同一 Wi-Fi、电视没待机；或直接手填 IP\n"
                + "· 二维码识别失败：把二维码拍满画面、避免反光，或改用「手动输入」\n"
                + "· 同屏全黑：电视在 HDMI/机顶盒输入，Android 截不到该图层，点「切回电视主页」即可\n"
                + "· 遥控没反应：电视需允许 ADB 调试；TCL 部分固件不弹授权框，改用「蓝牙遥控」\n"
                + "· 卸载失败：系统应用受固件保护，属正常\n"
                + "· 应用列表为空：Aerial 没运行时会自动改问电视端助手，若仍为空请先装「装对讲端」\n\n"
                + "【自检】\n"
                + "卡片「自检」会逐项检测 8790 / 6553 / 5555 / 8766 / 8768 / 8769，哪项 ✘ 就按提示处理。";

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tv.setTextColor(theme.text);
        tv.setTextIsSelectable(true);
        int p = dp(18);
        tv.setPadding(p, dp(8), p, dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(tv);

        GlassDialog.Builder gb = new GlassDialog.Builder(this, theme)
                .title(firstRun ? "欢迎使用 Aerial 管家" : "使用说明与兼容性")
                .content(scroll)
                .positive("知道了", () -> getSharedPreferences("aerial_buddy", MODE_PRIVATE)
                        .edit().putBoolean("help_seen", true).apply());
        if (firstRun) {
            gb.negative("不再提示", () -> getSharedPreferences("aerial_buddy", MODE_PRIVATE)
                    .edit().putBoolean("help_seen", true).apply());
        }
        gb.show();
    }

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

        if (!devices.isEmpty()) {
            Device d = devices.get(Math.min(currentIndex, devices.size() - 1));
            TextView dev = new TextView(this);
            dev.setText("\n当前设备：" + d.name + "\n" + d.ip()
                    + "\n管理 8790 · 对讲 8766 · 同屏 8768 · 助手 8769 · ADB 5555"
                    + "\n" + (d.bound() ? "已绑定" : "未绑定") + " · " + (d.status == null ? "未知" : d.status));
            dev.setTextColor(theme.dim);
            dev.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            dev.setTextIsSelectable(true);
            box.addView(dev);
        }

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

        TextView update = new TextView(this);
        update.setText("检查更新（打开 Releases 页）");
        update.setGravity(Gravity.CENTER);
        update.setTextColor(theme.accentText);
        update.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        GradientDrawable ug = new GradientDrawable();
        ug.setCornerRadius(dp(14));
        ug.setColor(theme.accent);
        update.setBackground(ug);
        LinearLayout.LayoutParams ulp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        ulp2.topMargin = dp(12);
        box.addView(update, ulp2);
        update.setOnClickListener(v -> {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/huli2008/aerial-buddy/releases/latest")));
            } catch (Exception e) {
                toast("无法打开浏览器");
            }
        });

        new GlassDialog.Builder(this, theme)
                .title("信息与赞助")
                .content(box)
                .positive("关闭", null)
                .show();
    }

    private void showSponsorDialog(final Runnable onUnlocked) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        box.setPadding(p, dp(6), p, 0);

        TextView tip = new TextView(this);
        tip.setText("这个 App 完全免费，所有功能不设限制。\n"
                + "如果它帮到了你，欢迎自愿赞助支持后续维护（不影响任何功能）。");
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

        new GlassDialog.Builder(this, theme)
                .title("赞助后开启同屏与对讲")
                .content(box)
                .negative("关闭", null)
                .positive("解锁", () -> {
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
        LinearLayout sbox = new LinearLayout(this);
        sbox.setOrientation(LinearLayout.VERTICAL);
        sbox.addView(messageText("填 tools/license-server.py 跑起来的那台机器的地址；留空则只能用手工专属码。"));
        sbox.addView(e);
        new GlassDialog.Builder(this, theme)
                .title("授权服务器地址")
                .content(sbox)
                .negative("取消", null)
                .positive("保存", () -> {
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
                    final boolean helperAlive = helperHealth(d);
                    final boolean outdated = !helperAlive && helperPortOpen(d);
                    if (helperAlive) {
                        // 电视端助手在跑：远程请它自己开始同屏（无需电视上点任何菜单）
                        askTvToStartMirror(d);
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(outdated ? "电视端助手版本过低"
                                    : (helperAlive ? "同屏没有就绪" : "该设备不支持同屏"))
                            .setMessage(outdated
                                    ? ("电视上的助手是旧版（低于 0.6），没有同屏/弹幕/喊话接口。\n\n"
                                        + "点「装对讲端」升级到 0.7.1（约 110 KB，电视上按一次同意）即可。")
                                    : helperAlive
                                    ? ("电视端助手在线，但投屏端口 8768 没开。\n\n"
                                        + "请先在电视上打开「Aerial 对讲端」→ 点「开始同屏」并按 OK。\n"
                                        + "部分 Android 9 机型每次重启都要按一次；按了仍打不开说明该固件不允许录屏，属机型限制。")
                                    : ("这台电视没有安装电视端助手，无法同屏。\n\n"
                                        + "先点「装对讲端」把助手推到电视，装好后再试；"
                                        + "非 TCL/雷鸟且未开调试的机型装不上助手，同屏不可用。"))
                            .setNegativeButton("知道了", null)
                            .setPositiveButton(helperAlive ? "去电视上开一次" : "装对讲端",
                                    (dlg, w) -> {
                                        if (!helperAlive) installHelper(d);
                                    })
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
    /** 装 Aerial：先让用户选通道（自动 / ADB / 多屏互动），再按选择推送。 */
    private void installAerial(final Device d) {
        final String[] items = {
                "自动（ADB 优先，失败回退多屏互动）",
                "仅 ADB（需电视开启网络调试）",
                "仅多屏互动（T-Cast，需电视端确认）"
        };
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final android.app.Dialog[] ref = new android.app.Dialog[1];
        for (int i = 0; i < items.length; i++) {
            final int mode = i;
            LinearLayout row = UiKit.listRow(this, theme, i == 0 ? "⚡" : (i == 1 ? "🔌" : "📡"),
                    items[i], null, false, () -> {
                        if (ref[0] != null) ref[0].dismiss();
                        pushAerialWithMode(d, mode);
                    });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            box.addView(row, lp);
        }
        ref[0] = new GlassDialog.Builder(this, theme)
                .title("安装 Aerial · 选择通道")
                .content(box)
                .negative("取消", null)
                .show();
    }

    private void pushAerialWithMode(final Device d, final int mode) {
        new Thread(() -> {
            java.io.File out = new java.io.File(getCacheDir(), "aerial-guard.apk");
            try {
                long assetLen = -1;
                try {
                    assetLen = getAssets().openFd("aerial-guard.apk").getLength();
                } catch (Exception ignored) {
                }
                if (out.exists() && Math.abs(out.length() - assetLen) <= 2) {
                    runOnUiThread(() -> Installer.pushChannel(this, d, out, mode, (ok, msg) -> {
                        toast(msg);
                        if (ok) installHelper(d);
                        if (ok) refresh(d);
                    }));
                    return;
                }
                InputStream in = getAssets().open("aerial-guard.apk");
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                in.close();
                runOnUiThread(() -> Installer.pushChannel(this, d, out, mode, (ok, msg) -> {
                    toast(msg);
                    if (ok) installHelper(d);
                    if (ok) refresh(d);
                }));
            } catch (Exception e) {
                runOnUiThread(() -> toast("读取安装包失败：" + e.getMessage()));
            }
        }, "aerial-copy").start();
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
                GlassDialog.text(this, theme, "这台电视还没装 Aerial",
                        "检测 " + d.ip() + ":8790 没有响应，说明电视上还没有 Aerial 桌面。\n\n"
                                + "现在通过电视的「多屏互动」推送安装？（安装包已内置，约 3 MB，"
                                + "电视上按遥控器「右键 → 同意」即可）",
                        "立即安装", () -> installAerial(d));
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
        new GlassDialog.Builder(this, theme)
                .title("电视 IP")
                .content(e)
                .negative("取消", null)
                .positive("添加", () -> addByIp(e.getText().toString()))
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
        } else if (req == REQ_RECEIPT && data != null && data.getData() != null) {
            handleReceipt(data.getData());
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
