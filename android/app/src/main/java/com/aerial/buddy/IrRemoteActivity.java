package com.aerial.buddy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 红外遥控：一级页只放按键，码库配置收在二级页（⚙）。
 *
 * <p>没有可用码时只显示电源/音量/频道；保存码之后再显示方向键。
 */
public class IrRemoteActivity extends Activity {

    private static final String PREFS = "aerial_ir";

    private Theme theme;
    private ConsumerIrManager ir;
    private String name;

    private LinearLayout remoteScreen;
    private LinearLayout settingsScreen;
    private LinearLayout dpadBox;
    private TextView statusLine;
    private TextView brandLabel;

    private int brand;
    private int candidate;
    private int goodAddr = -1, goodCmd = -1;
    private volatile boolean scanning;
    private int scanAddr, scanCmd;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        name = getIntent().getStringExtra("name");
        theme = Theme.of(getIntent().getIntExtra("theme", Theme.AURORA));
        ir = Ir.emitter(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(theme.light ? Color.parseColor("#F2F4FA") : Color.parseColor("#0E1117"));
        int pad = dp(20);
        page.setPadding(pad, dp(14), pad, dp(10));

        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        back.setTextColor(theme.text);
        back.setPadding(0, 0, dp(12), 0);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = new TextView(this);
        title.setText("遥控 · " + (name == null ? "电视" : name));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(theme.text);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        gear.setTextColor(theme.text);
        gear.setPadding(dp(10), dp(2), dp(6), dp(2));
        gear.setOnClickListener(v -> showScreen(true));
        top.addView(gear);
        page.addView(top);

        remoteScreen = buildRemote();
        settingsScreen = buildSettings();
        page.addView(remoteScreen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(settingsScreen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(page);
        loadSaved();
        showScreen(false);
    }

    private void showScreen(boolean settings) {
        remoteScreen.setVisibility(settings ? View.GONE : View.VISIBLE);
        settingsScreen.setVisibility(settings ? View.VISIBLE : View.GONE);
    }

    // ---------- 一级：遥控 ----------

    private LinearLayout buildRemote() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, 0);

        if (ir == null) {
            TextView t = new TextView(this);
            t.setText("这台手机没有红外发射器，无法红外遥控。\n\n"
                    + "可改用：电视端助手的音量/主页控制。");
            t.setTextColor(theme.text);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            box.addView(t);
            return box;
        }

        // 顶部六个圆钮：电源 / 输入源 / 菜单
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER);
        r1.addView(circle("⏻", 0x08, Color.parseColor("#38D39F")), circleLp());
        r1.addView(circle("⤶", 0x0B, theme.text), circleLp());
        r1.addView(circle("☰", 0x0E, theme.text), circleLp());
        box.addView(r1);

        // 第二行：主页 / 数字 / 返回
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER);
        r2.addView(circle("⌂", 0x5C, theme.text), circleLp());
        r2.addView(circle("①", 0x16, theme.text), circleLp());
        r2.addView(circle("↩", 0x5B, theme.text), circleLp());
        box.addView(r2);

        // 中间大圆盘
        box.addView(buildDpad(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部：CH / VOL 竖条 + ⋯ / 静音
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);

        LinearLayout ch = rocker("频道", 0x1B, 0x1A);
        bottom.addView(ch, new LinearLayout.LayoutParams(dp(72), dp(150)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.addView(circle("⋯", 0x0F, theme.text), new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout.LayoutParams muteLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        muteLp.topMargin = dp(14);
        mid.addView(circle("🔇", 0x0D, theme.text), muteLp);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bottom.addView(mid, midLp);

        LinearLayout vol = rocker("音量", 0x10, 0x11);
        bottom.addView(vol, new LinearLayout.LayoutParams(dp(72), dp(150)));
        box.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusLine = new TextView(this);
        statusLine.setText("点这里设置码库");
        statusLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        statusLine.setTextColor(theme.dim);
        statusLine.setPadding(0, dp(10), 0, 0);
        statusLine.setGravity(Gravity.CENTER);
        statusLine.setOnClickListener(v -> showScreen(true));
        box.addView(statusLine);
        return box;
    }

    /** 竖条：上键 + 标签 + 下键。 */
    private LinearLayout rocker(final String label, final int upCmd, final int downCmd) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(36));
        bg.setColor(theme.light ? Color.WHITE : theme.card);
        bg.setStroke(dp(1), theme.cardStroke);
        col.setBackground(bg);

        col.addView(pillKey("^", upCmd), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(theme.text);
        col.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        col.addView(pillKey("v", downCmd), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return col;
    }

    private TextView pillKey(String glyph, final int cmd) {
        TextView v = new TextView(this);
        v.setText(glyph);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        v.setTextColor(theme.text);
        v.setOnClickListener(x -> press(cmd));
        return v;
    }

    private LinearLayout.LayoutParams circleLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(74), dp(74));
        lp.leftMargin = dp(10);
        lp.rightMargin = dp(10);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        return lp;
    }

    private TextView circle(String glyph, final int cmd, int textColor) {
        TextView v = new TextView(this);
        v.setText(glyph);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        v.setTextColor(textColor);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(theme.light ? Color.WHITE : theme.card);
        d.setStroke(dp(1), theme.cardStroke);
        v.setBackground(d);
        v.setOnClickListener(x -> press(cmd));
        return v;
    }
    private LinearLayout buildDpad() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        FrameLayout pad = new FrameLayout(this);
        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(dp(260), dp(260));
        padLp.gravity = Gravity.CENTER_HORIZONTAL;
        pad.setLayoutParams(padLp);

        View ring = new View(this);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        ringBg.setColor(theme.light ? Color.WHITE : theme.card);
        ringBg.setStroke(dp(1), theme.cardStroke);
        ring.setBackground(ringBg);
        FrameLayout.LayoutParams ringLp = new FrameLayout.LayoutParams(dp(260), dp(260));
        ringLp.gravity = Gravity.CENTER;
        pad.addView(ring, ringLp);

        // 四个方向小点（点击区域是整块环的四分之一）
        pad.addView(dirDot(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0x53));
        pad.addView(dirDot(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0x52));
        pad.addView(dirDot(Gravity.START | Gravity.CENTER_VERTICAL, 0x51));
        pad.addView(dirDot(Gravity.END | Gravity.CENTER_VERTICAL, 0x50));

        TextView ok = new TextView(this);
        ok.setText("确定");
        ok.setGravity(Gravity.CENTER);
        ok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        ok.setTextColor(theme.text);
        GradientDrawable okBg = new GradientDrawable();
        okBg.setShape(GradientDrawable.OVAL);
        okBg.setColor(theme.light ? Color.parseColor("#F7F8FA") : Color.parseColor("#1E2431"));
        okBg.setStroke(dp(1), theme.cardStroke);
        ok.setBackground(okBg);
        ok.setOnClickListener(v -> press(0x1D));
        FrameLayout.LayoutParams okLp = new FrameLayout.LayoutParams(dp(104), dp(104));
        okLp.gravity = Gravity.CENTER;
        pad.addView(ok, okLp);

        box.addView(pad);
        return box;
    }

    /** 方向热区：大圆点 + 透明点击区。 */
    private View dirDot(int gravity, final int cmd) {
        FrameLayout zone = new FrameLayout(this);
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        dot.setTextColor(Theme.of(theme.id).dim);
        dot.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(28), dp(28));
        dlp.gravity = Gravity.CENTER;
        zone.addView(dot, dlp);
        zone.setOnClickListener(v -> press(cmd));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(110), dp(110));
        lp.gravity = gravity;
        zone.setLayoutParams(lp);
        return zone;
    }
    // ---------- 二级：码库设置 ----------

    private LinearLayout buildSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, 0);

        TextView current = new TextView(this);
        current.setText("当前码：未设置");
        current.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        current.setTextColor(theme.dim);
        current.setPadding(0, 0, 0, dp(8));
        brandLabel = current;
        box.addView(current);

        ScrollView scroll = new ScrollView(this);
        LinearLayout brands = new LinearLayout(this);
        brands.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < Ir.BRANDS.length; i++) {
            final int idx = i;
            TextView row = new TextView(this);
            row.setText(Ir.BRANDS[i] + "   ›");
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            row.setTextColor(theme.text);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(theme.card);
            bg.setStroke(dp(1), theme.cardStroke);
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                brand = idx;
                candidate = 0;
                tryNext();
            });
            brands.addView(row);
        }
        scroll.addView(brands);
        box.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        box.addView(action("试码：逐个发送电源码", theme.card, theme.text, this::tryNext), barLp());
        box.addView(action("自动扫描全部码（约 2 分钟）", theme.card, theme.text, this::autoScan), barLp());
        box.addView(action("保存当前码", theme.accent, theme.accentText, this::save), barLp());
        box.addView(action("返回遥控", theme.card, theme.text, () -> showScreen(false)), barLp());
        return box;
    }

    // ---------- 逻辑 ----------

    private void loadSaved() {
        goodAddr = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("addr", -1);
        goodCmd = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("cmd", -1);
        brand = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("brand", 0);
        refreshStatus();
    }

    private void refreshStatus() {
        if (goodAddr >= 0) {
            String s = "当前码：addr=0x" + Integer.toHexString(goodAddr)
                    + " cmd=0x" + Integer.toHexString(goodCmd);
            if (statusLine != null) statusLine.setText(s + "（点这里改）");
            if (brandLabel != null) brandLabel.setText("已保存 " + s);
            if (dpadBox != null) dpadBox.setVisibility(View.VISIBLE);
        }
        if (ir == null && statusLine != null) statusLine.setText("无红外发射器");
        if (ir != null && brandLabel != null && goodAddr < 0) {
            brandLabel.setText("当前码：未设置\n" + Ir.describe(ir));
        }
    }

    private void tryNext() {
        int[][] codes = Ir.powerCodes(brand);
        int idx = candidate % codes.length;
        int a = codes[idx][0], c = codes[idx][1];
        int used = Ir.send(ir, a, c);
        candidate++;
        if (brandLabel != null) {
            brandLabel.setText("已发 " + (idx + 1) + "/" + codes.length + " 组：addr=0x"
                    + Integer.toHexString(a) + " cmd=0x" + Integer.toHexString(c)
                    + (used > 0 ? ("（载波 " + (used / 1000) + " kHz）") : "（发射被拒绝）"));
        }
    }

    private void autoScan() {
        if (scanning) {
            toast("正在扫描…");
            return;
        }
        scanning = true;
        final int[] addrs = Ir.scanAddresses();
        final int[] cmds = Ir.scanCommands();
        new Thread(() -> {
            int total = addrs.length * cmds.length, n = 0;
            outer:
            for (int a : addrs) {
                for (int c : cmds) {
                    if (!scanning) break outer;
                    scanAddr = a;
                    scanCmd = c;
                    n++;
                    final int idx = n;
                    runOnUiThread(() -> {
                        if (brandLabel != null) brandLabel.setText("扫描中 " + idx + "/" + total
                                + "\naddr=0x" + Integer.toHexString(scanAddr)
                                + " cmd=0x" + Integer.toHexString(scanCmd) + "（有反应就点保存）");
                    });
                    Ir.send(ir, a, c);
                    try {
                        Thread.sleep(900);
                    } catch (InterruptedException e) {
                        break outer;
                    }
                }
            }
            scanning = false;
            runOnUiThread(() -> {
                if (brandLabel != null) brandLabel.setText("扫描结束");
            });
        }, "ir-scan").start();
    }

    private void save() {
        if (scanning) {
            goodAddr = scanAddr;
            goodCmd = scanCmd;
            scanning = false;
        } else {
            int[][] codes = Ir.powerCodes(brand);
            int idx = Math.max(0, candidate - 1) % codes.length;
            goodAddr = codes[idx][0];
            goodCmd = codes[idx][1];
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt("addr", goodAddr).putInt("cmd", goodCmd).putInt("brand", brand).apply();
        refreshStatus();
        toast("已保存");
    }

    private void press(int cmd) {
        if (goodAddr < 0) {
            toast("先到 ⚙ 里设置码库");
            showScreen(true);
            return;
        }
        int used = Ir.send(ir, goodAddr, cmd);
        if (used <= 0) toast("发射失败：驱动拒绝该码");
    }

    // ---------- 控件 ----------

    private LinearLayout.LayoutParams triLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
        lp.rightMargin = dp(10);
        return lp;
    }

    private LinearLayout.LayoutParams halfLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.rightMargin = dp(10);
        return lp;
    }

    private LinearLayout.LayoutParams centerLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(52));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(6);
        return lp;
    }

    private LinearLayout.LayoutParams squareLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(52));
        lp.leftMargin = dp(5);
        lp.rightMargin = dp(5);
        return lp;
    }

    private LinearLayout.LayoutParams barLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.topMargin = dp(8);
        return lp;
    }

    private TextView key(String label, final int cmd, int height, int bgColor, int textColor) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        v.setTextColor(textColor);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(16));
        d.setColor(bgColor);
        d.setStroke(dp(1), theme.cardStroke);
        v.setBackground(d);
        v.setOnClickListener(x -> press(cmd));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(height)));
        return v;
    }

    private TextView action(String label, int bgColor, int textColor, Runnable r) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setTextColor(textColor);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(bgColor);
        d.setStroke(dp(1), theme.cardStroke);
        v.setBackground(d);
        v.setOnClickListener(x -> r.run());
        return v;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
