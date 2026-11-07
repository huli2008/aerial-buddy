package com.aerial.buddy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** 互动页：对讲 / 弹幕 / 喊话，三个带图标的 Tab。 */
public class InteractionActivity extends Activity {

    private static final String[] TEXT_COLORS = {"FFFFFF", "FFD166", "38D39F", "FF6B9D", "5B9DFF", "4DD0E1"};
    private static final String[] TEXT_NAMES = {"白", "黄", "绿", "粉", "蓝", "青"};
    private static final String[] BG_COLORS = {"000000", "1B2430", "7C5CFF", "38D39F", "B23B3B", "FFFFFF"};
    private static final String[] BG_NAMES = {"黑", "深灰", "紫", "绿", "红", "白"};

    private final Intercom intercom = new Intercom();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Theme theme;
    private String ip;
    private String name;
    private TextView status;

    private LinearLayout talkBox, dmBox, sayBox;
    private TextView talkTab, dmTab, sayTab;

    private String dmColor = "FFFFFF";
    private String dmBg = "000000";
    private int dmAlpha = 0x66;
    private TextView preview;
    private LinearLayout presetRow;
    private static final String PREFS = "aerial_dm_preset";
    private final List<TextView> waveBars = new ArrayList<>();
    private boolean waving;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ip = getIntent().getStringExtra("ip");
        name = getIntent().getStringExtra("name");
        theme = Theme.of(getIntent().getIntExtra("theme", Theme.AURORA));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(theme.light ? Color.parseColor("#F2F4FA") : Color.parseColor("#0E1117"));
        int pad = dp(18);
        page.setPadding(pad, dp(14), pad, dp(10));

        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        back.setTextColor(theme.text);
        back.setPadding(0, 0, dp(10), 0);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = new TextView(this);
        title.setText("互动 · " + (name == null ? ip : name));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(theme.text);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView info = new TextView(this);
        info.setText("ⓘ");
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        info.setTextColor(theme.text);
        info.setOnClickListener(v -> toast("对讲走 8766，弹幕/喊话走 8769，需要电视端助手 0.7.2+"));
        top.addView(info);
        page.addView(top);

        // 整块玻璃面板（含顶部高光线）
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(14));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setCornerRadius(dp(24));
        pbg.setColor(theme.glass);
        pbg.setStroke(dp(1), theme.glassStroke);
        panel.setBackground(pbg);
        page.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View highlight = new View(this);
        GradientDrawable hg = new GradientDrawable();
        hg.setCornerRadius(dp(2));
        hg.setColor(theme.light ? 0x80FFFFFF : 0x4DFFFFFF);
        highlight.setBackground(hg);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        panel.addView(highlight, hlp);

        // Tab
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(10), 0, dp(8));
        dmTab = tab("💬 弹幕", true);
        sayTab = tab("📢 播报", false);
        tabs.addView(dmTab, tabLp());
        tabs.addView(sayTab, tabLp());
        talkTab = tab("", false);
        panel.addView(tabs);

        talkBox = buildTalk();
        dmBox = buildDanmaku();
        sayBox = buildSay();
        panel.addView(talkBox, weightLp());
        panel.addView(dmBox, weightLp());
        panel.addView(sayBox, weightLp());
        dmBox.setVisibility(View.VISIBLE);
        talkBox.setVisibility(View.GONE);
        sayBox.setVisibility(View.GONE);
        dmTab.setBackground(tabBg(true));
        sayTab.setBackground(tabBg(false));

        dmTab.setOnClickListener(v -> switchTab(1));
        sayTab.setOnClickListener(v -> switchTab(2));

        status = new TextView(this);
        status.setText("准备就绪");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        status.setTextColor(theme.dim);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setCornerRadius(dp(14));
        sbg.setColor(theme.glass);
        sbg.setStroke(dp(1), theme.glassStroke);
        status.setBackground(sbg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        slp.topMargin = dp(10);
        page.addView(status, slp);

        setContentView(page);
    }

    private void switchTab(int which) {
        talkBox.setVisibility(which == 0 ? View.VISIBLE : View.GONE);
        dmBox.setVisibility(which == 1 ? View.VISIBLE : View.GONE);
        sayBox.setVisibility(which == 2 ? View.VISIBLE : View.GONE);
        talkTab.setBackground(tabBg(which == 0));
        dmTab.setBackground(tabBg(which == 1));
        sayTab.setBackground(tabBg(which == 2));
        talkTab.setTextColor(which == 0 ? theme.accent : theme.text);
        dmTab.setTextColor(which == 1 ? theme.accent : theme.text);
        sayTab.setTextColor(which == 2 ? theme.accent : theme.text);
        if (which != 0) stopWave();
    }

    // ---------- 对讲 ----------

    private LinearLayout buildTalk() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        final TextView hold = new TextView(this);
        hold.setText("🎙\n按住说话");
        hold.setGravity(Gravity.CENTER);
        hold.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        hold.setTextColor(theme.accent);
        hold.setBackground(glassRing(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(200), dp(200));
        lp.topMargin = dp(10);
        box.addView(hold, lp);

        LinearLayout wave = new LinearLayout(this);
        wave.setOrientation(LinearLayout.HORIZONTAL);
        wave.setGravity(Gravity.CENTER);
        wave.setPadding(0, dp(16), 0, 0);
        for (int i = 0; i < 7; i++) {
            TextView bar = new TextView(this);
            GradientDrawable bgb = new GradientDrawable();
            bgb.setCornerRadius(dp(3));
            bgb.setColor(theme.accent);
            bar.setBackground(bgb);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(6), dp(10));
            blp.leftMargin = dp(3);
            blp.rightMargin = dp(3);
            wave.addView(bar, blp);
            waveBars.add(bar);
        }
        box.addView(wave);

        hold.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!hasMic()) {
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 71);
                        }
                        status.setText("需要麦克风权限");
                        return true;
                    }
                    hold.setText("🔴\n正在说话");
                    ScaleAnimation anim = new ScaleAnimation(1f, 1.06f, 1f, 1.06f,
                            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                    anim.setDuration(180);
                    anim.setFillAfter(true);
                    hold.startAnimation(anim);
                    startWave();
                    intercom.startAsync(ip, Intercom.PORT, this, new Intercom.Cb() {
                        @Override
                        public void ready() {
                            runOnUiThread(() -> status.setText("对讲中…松开结束"));
                        }

                        @Override
                        public void failed(String message) {
                            runOnUiThread(() -> {
                                hold.setText("🎙\n按住说话");
                                status.setText(message);
                            });
                        }
                    });
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    intercom.stop();
                    stopWave();
                    hold.clearAnimation();
                    hold.setText("🎙\n按住说话");
                    status.setText("已停止");
                    return true;
                default:
                    return false;
            }
        });
        return box;
    }

    private void startWave() {
        waving = true;
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!waving) return;
                for (TextView bar : waveBars) {
                    int h = 10 + (int) (Math.random() * 34);
                    ViewGroup.LayoutParams lp = bar.getLayoutParams();
                    lp.height = dp(h);
                    bar.setLayoutParams(lp);
                }
                ui.postDelayed(this, 90);
            }
        };
        ui.post(tick);
    }

    private void stopWave() {
        waving = false;
        for (TextView bar : waveBars) {
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.height = dp(10);
            bar.setLayoutParams(lp);
        }
    }

    // ---------- 弹幕 ----------

    private LinearLayout buildDanmaku() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        final EditText input = new EditText(this);
        input.setHint("输入要飘过电视的文字，可换行");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(inputBg());
        box.addView(input);

        box.addView(label("字色"));
        LinearLayout c1 = new LinearLayout(this);
        c1.setOrientation(LinearLayout.HORIZONTAL);
        addColorDots(c1, TEXT_COLORS, false);
        box.addView(c1);

        box.addView(label("底色"));
        LinearLayout c2 = new LinearLayout(this);
        c2.setOrientation(LinearLayout.HORIZONTAL);
        addColorDots(c2, BG_COLORS, true);
        box.addView(c2);

        final TextView alphaLabel = label("不透明度 " + (dmAlpha * 100 / 255) + "%");
        box.addView(alphaLabel);
        SeekBar bar = new SeekBar(this);
        styleSeekBar(bar);
        bar.setMax(100);
        bar.setProgress(dmAlpha * 100 / 255);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                dmAlpha = Math.round(progress * 255f / 100f);
                alphaLabel.setText("不透明度 " + progress + "%");
                refreshPreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        box.addView(bar);

        preview = new TextView(this);
        preview.setText("弹幕预览");
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(12), dp(14), dp(12), dp(14));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(6);
        box.addView(preview, plp);
        refreshPreview();

        // 自定义快捷语
        box.addView(label("自定义快捷"));
        presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(presetRow);
        final EditText dmInput = input;
        TextView saveBtn = action("＋ 存为快捷", theme.card, theme.dim);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(dp(110), dp(34));
        saveLp.topMargin = dp(6);
        saveBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        saveBtn.setOnClickListener(v -> savePreset(dmInput.getText().toString()));
        box.addView(saveBtn, saveLp);
        loadPresets();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        TextView send = action("发送弹幕", theme.accent, theme.accentText);
        send.setOnClickListener(v -> sendDanmaku(input.getText().toString()));
        row.addView(send, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView img = action("发图片", theme.card, theme.text);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        ilp.leftMargin = dp(10);
        img.setOnClickListener(v -> pickImage());
        row.addView(img, ilp);
        box.addView(row);
        return box;
    }

    private void addColorDots(LinearLayout row, final String[] colors, final boolean isBg) {
        final List<TextView> dots = new ArrayList<>();
        String current = isBg ? dmBg : dmColor;
        for (int i = 0; i < colors.length; i++) {
            final String hex = colors[i];
            final TextView dot = new TextView(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.parseColor("#" + hex));
            d.setStroke(dp(1), theme.cardStroke);
            dot.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            lp.topMargin = dp(4);
            dot.setLayoutParams(lp);
            dot.setAlpha(hex.equalsIgnoreCase(current) ? 1f : 0.4f);
            dot.setOnClickListener(v -> {
                if (isBg) dmBg = hex; else dmColor = hex;
                for (TextView d2 : dots) d2.setAlpha(0.4f);
                dot.setAlpha(1f);
                refreshPreview();
            });
            dots.add(dot);
            row.addView(dot);
        }
    }

    private void savePreset(String text) {
        if (text == null || text.trim().isEmpty()) {
            toast("先输入要保存的文字");
            return;
        }
        android.content.SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String json = text.replace("|", " ") + "|" + dmColor + "|" + dmBg + "|" + dmAlpha;
        java.util.Set<String> all = new java.util.LinkedHashSet<>(p.getStringSet("items",
                new java.util.LinkedHashSet<>()));
        if (all.size() >= 8) {
            java.util.Iterator<String> it = all.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        all.add(json);
        p.edit().putStringSet("items", all).apply();
        toast("已存为快捷");
        loadPresets();
    }

    private void loadPresets() {
        if (presetRow == null) return;
        presetRow.removeAllViews();
        java.util.Set<String> all = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet("items", new java.util.LinkedHashSet<>());
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有快捷语，输入文字后点「存为快捷」");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            empty.setTextColor(theme.dim);
            presetRow.addView(empty);
            return;
        }
        for (final String item : all) {
            String[] parts = item.split("\\|");
            if (parts.length < 4) continue;
            final String text = parts[0];
            String c = parts[1], b = parts[2];
            int a;
            try {
                a = Integer.parseInt(parts[3]);
            } catch (Exception e) {
                a = 0x66;
            }
            TextView chip = new TextView(this);
            chip.setText(text.length() > 6 ? text.substring(0, 6) : text);
            chip.setSingleLine(true);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            chip.setTextColor(Color.parseColor("#" + c));
            GradientDrawable cbg = new GradientDrawable();
            cbg.setCornerRadius(dp(12));
            cbg.setColor((a << 24) | (Integer.parseInt(b, 16) & 0xFFFFFF));
            cbg.setStroke(dp(1), theme.cardStroke);
            chip.setBackground(cbg);
            chip.setPadding(dp(10), dp(7), dp(10), dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> sendDanmaku(text));
            chip.setOnLongClickListener(v -> {
                java.util.Set<String> now = new java.util.LinkedHashSet<>(
                        getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet("items",
                                new java.util.LinkedHashSet<>()));
                now.remove(item);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet("items", now).apply();
                toast("已删除快捷");
                loadPresets();
                return true;
            });
            presetRow.addView(chip);
        }
    }

    private void refreshPreview() {
        if (preview == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        int alpha = Math.max(0, Math.min(255, dmAlpha));
        bg.setColor((alpha << 24) | (Integer.parseInt(dmBg, 16) & 0xFFFFFF));
        preview.setBackground(bg);
        preview.setTextColor(Color.parseColor("#" + dmColor));
        preview.setText("弹幕预览 · 效果如下");
    }

    // ---------- 喊话 ----------

    /** 语音播报：录下你的声音，直接从电视喇叭放出来（不依赖 TTS 引擎）。 */
    private LinearLayout buildSay() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        final TextView hold = new TextView(this);
        hold.setText("📢\n按住播报");
        hold.setGravity(Gravity.CENTER);
        hold.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        hold.setTextColor(Color.parseColor("#F5A524"));
        hold.setBackground(glassRing(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(180), dp(180));
        lp.topMargin = dp(12);
        box.addView(hold, lp);

        TextView tip = new TextView(this);
        tip.setText("电视会先调大音量，然后播放你说的内容。\n不需要电视装语音引擎。");
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tip.setTextColor(theme.dim);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(14), 0, 0);
        box.addView(tip);

        hold.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!hasMic()) {
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 71);
                        }
                        status.setText("需要麦克风权限");
                        return true;
                    }
                    hold.setText("🔴\n正在播报");
                    new Thread(() -> {
                        for (int i = 0; i < 3; i++) helperVolume(ip, "up");
                    }, "vol").start();
                    intercom.startAsync(ip, Intercom.PORT, this, new Intercom.Cb() {
                        @Override
                        public void ready() {
                            runOnUiThread(() -> status.setText("正在播报…松开结束"));
                        }

                        @Override
                        public void failed(String message) {
                            runOnUiThread(() -> {
                                hold.setText("📢\n按住播报");
                                status.setText(message);
                            });
                        }
                    });
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    intercom.stop();
                    hold.setText("📢\n按住播报");
                    status.setText("播报结束");
                    return true;
                default:
                    return false;
            }
        });
        return box;
    }

    private void helperVolume(String ip, String dir) {
        try {
            get("http://" + ip + ":8769/volume?d=" + dir);
        } catch (Exception ignored) {
        }
    }
    private void say(String text) {
        if (text == null || text.isEmpty()) {
            toast("先写点内容");
            return;
        }
        try {
            final String q = URLEncoder.encode(text, "UTF-8");
            new Thread(() -> {
                String body = get("http://" + ip + ":8769/say?text=" + q);
                if (body == null) {
                    ui("电视端未响应：需助手 0.6.0+");
                } else if (!body.contains("\"ok\":true")) {
                    ui("电视端拒绝：可能没有语音引擎，改用弹幕");
                } else {
                    ui("已发送，电视正在念");
                }
            }, "say").start();
        } catch (Exception e) {
            toast("编码失败");
        }
    }

    // ---------- 发送 ----------

    private void sendDanmaku(String text) {
        if (text == null || text.isEmpty()) {
            toast("先写点内容");
            return;
        }
        try {
            final String q = URLEncoder.encode(text, "UTF-8");
            new Thread(() -> {
                String body = get("http://" + ip + ":8769/danmaku?text=" + q
                        + "&color=" + dmColor + "&bg=" + dmBg
                        + "&bgAlpha=" + Integer.toHexString(dmAlpha));
                ui(body != null && body.contains("\"ok\":true") ? "弹幕已发送" : "电视端未响应（需助手 0.7.2+）");
            }, "dm").start();
        } catch (Exception e) {
            toast("编码失败");
        }
    }

    private void pickImage() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(android.content.Intent.createChooser(i, "选择要投到电视的图片"), 51);
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 51 || res != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        status.setText("正在发送图片…");
        new Thread(() -> {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                if (in != null) in.close();
                if (bmp == null) {
                    ui("图片读取失败");
                    return;
                }
                int w = Math.min(bmp.getWidth(), 720);
                android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(
                        bmp, w, (int) (bmp.getHeight() * (w / (float) bmp.getWidth())), true);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos);
                byte[] jpeg = bos.toByteArray();
                HttpURLConnection c = (HttpURLConnection) new URL("http://" + ip + ":8769/image").openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(8000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "image/jpeg");
                c.setFixedLengthStreamingMode(jpeg.length);
                java.io.OutputStream out = c.getOutputStream();
                out.write(jpeg);
                out.flush();
                out.close();
                int code = c.getResponseCode();
                c.disconnect();
                ui(code == 200 ? "图片已投到电视" : ("电视端返回 " + code));
            } catch (Exception e) {
                ui("发送失败：" + e.getMessage());
            }
        }, "img").start();
    }

    // ---------- 组件 ----------

    private boolean hasMic() {
        return android.os.Build.VERSION.SDK_INT < 23
                || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == 71 && status != null) {
            boolean ok = results.length > 0
                    && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            status.setText(ok ? "麦克风已授权，按住说话即可" : "麦克风被拒绝");
        }
    }

    private TextView tab(String text, boolean active) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setTextColor(active ? theme.accent : theme.text);
        v.setBackground(tabBg(active));
        return v;
    }

    private GradientDrawable tabBg(boolean active) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(active ? theme.glass : Color.TRANSPARENT);
        d.setStroke(dp(active ? 2 : 1), active ? theme.accent : theme.cardStroke);
        return d;
    }

    private LinearLayout.LayoutParams tabLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.rightMargin = dp(8);
        return lp;
    }

    private LinearLayout.LayoutParams weightLp() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        v.setTextColor(theme.dim);
        v.setPadding(0, dp(10), 0, dp(2));
        return v;
    }

    /** 玻璃圆：外圈主色环 + 内层半透明玻璃。 */
    private GradientDrawable glassRing(int ringDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(theme.glass);
        d.setStroke(dp(ringDp), theme.accent);
        return d;
    }

    /** 自定义滑块：轨道 + 主色已完成段 + 白色滑柄。 */
    private void styleSeekBar(SeekBar bar) {
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(4));
        track.setColor(theme.light ? 0x22000000 : 0x33FFFFFF);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(Color.WHITE);
        thumb.setStroke(dp(2), theme.accent);
        thumb.setSize(dp(20), dp(20));
        try {
            bar.setProgressDrawable(track);
            bar.setThumb(thumb);
            bar.setThumbOffset(0);
        } catch (Exception ignored) {
        }
    }

    private GradientDrawable inputBg() {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(theme.card);
        d.setStroke(dp(1), theme.cardStroke);
        return d;
    }

    private TextView action(String label, int bgColor, int textColor) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        v.setTextColor(textColor);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(bgColor);
        d.setStroke(dp(1), theme.cardStroke);
        v.setBackground(d);
        return v;
    }

    private String get(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(2000);
            c.setReadTimeout(4000);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            c.disconnect();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private void ui(final String text) {
        ui.post(() -> status.setText(text));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
