package com.aerial.buddy;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 壁纸页：2 列网格缩略图，点选后一键应用（Aerial api/wallpaper）。 */
public class WallpaperActivity extends Activity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> thumbs = new LruCache<>(20);

    private Theme theme;
    private Device device;
    private GridLayout grid;
    private TextView status;
    private String selected;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        device = new Device();
        device.name = getIntent().getStringExtra("name");
        device.url = getIntent().getStringExtra("url");
        theme = Theme.of(getIntent().getIntExtra("theme", Theme.AURORA));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(theme.light ? Color.parseColor("#F2F4FA") : Color.parseColor("#0E1117"));
        int pad = dp(18);
        page.setPadding(pad, dp(14), pad, dp(10));

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
        title.setText("壁纸 · " + (device.name == null ? "" : device.name));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(theme.text);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView refresh = new TextView(this);
        refresh.setText("⟳");
        refresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        refresh.setTextColor(theme.text);
        refresh.setOnClickListener(v -> load());
        top.addView(refresh);
        page.addView(top);

        ScrollView scroll = new ScrollView(this);
        grid = new GridLayout(this);
        grid.setColumnCount(2);
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setText("读取壁纸列表…");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        status.setTextColor(theme.dim);
        status.setPadding(0, dp(8), 0, 0);
        page.addView(status);

        setContentView(page);
        load();
    }

    private void load() {
        grid.removeAllViews();
        status.setText("读取壁纸列表…");
        new Thread(() -> {
            try {
                JSONObject s = AerialApi.state(device);
                JSONArray arr = s.optJSONArray("wallpapers");
                final String sel = s.optString("selected", "");
                main.post(() -> {
                    selected = sel;
                    if (arr == null || arr.length() == 0) {
                        status.setText("电视没有返回壁纸");
                        return;
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject w = arr.optJSONObject(i);
                        if (w == null) continue;
                        addTile(w.optString("id"), w.optString("title", w.optString("id")));
                    }
                    status.setText(arr.length() + " 张壁纸 · 点一下即应用");
                });
            } catch (Exception e) {
                main.post(() -> status.setText("读取失败：" + e.getMessage()));
            }
        }, "wp-load").start();
    }

    private void addTile(final String id, final String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(theme.card);
        bg.setStroke(dp(1), theme.cardStroke);
        card.setBackground(bg);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        card.setLayoutParams(lp);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ph = new GradientDrawable();
        ph.setCornerRadius(dp(12));
        ph.setColor(theme.cardStroke);
        iv.setBackground(ph);
        card.addView(iv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        t.setTextColor(theme.text);
        t.setMaxLines(1);
        t.setPadding(0, dp(6), 0, 0);
        card.addView(t);

        if (id.equals(selected)) {
            bg.setStroke(dp(2), theme.accent);
            t.setText("✔ " + label);
        }

        card.setOnClickListener(v -> apply(id, label));
        grid.addView(card);
        loadThumb(id, iv);
    }

    private void loadThumb(final String id, final ImageView view) {
        Bitmap cached = thumbs.get(id);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        pool.execute(() -> {
            Bitmap bmp = null;
            try {
                String u = device.base() + "thumb/" + android.net.Uri.encode(id);
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(5000);
                if (c.getResponseCode() == 200) {
                    InputStream in = c.getInputStream();
                    bmp = BitmapFactory.decodeStream(in);
                    in.close();
                }
                c.disconnect();
            } catch (Exception ignored) {
            }
            final Bitmap b = bmp;
            if (b != null) {
                thumbs.put(id, b);
                main.post(() -> view.setImageBitmap(b));
            }
        });
    }

    private void apply(final String id, final String label) {
        status.setText("正在应用 " + label + " …");
        new Thread(() -> {
            boolean ok = false;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(device.base() + "api/wallpaper").openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(8000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                java.io.OutputStream out = c.getOutputStream();
                out.write(("id=" + java.net.URLEncoder.encode(id, "UTF-8")).getBytes("UTF-8"));
                out.flush();
                out.close();
                ok = c.getResponseCode() == 200;
                c.disconnect();
                if (!ok) {
                    // 表单不被接受时改发 JSON，兼容不同版本的 Aerial
                    HttpURLConnection c2 = (HttpURLConnection) new URL(device.base() + "api/wallpaper").openConnection();
                    c2.setConnectTimeout(4000);
                    c2.setReadTimeout(8000);
                    c2.setRequestMethod("POST");
                    c2.setDoOutput(true);
                    c2.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    java.io.OutputStream o2 = c2.getOutputStream();
                    o2.write(("{\"id\":\"" + id.replace("\"", "") + "\"}").getBytes("UTF-8"));
                    o2.flush();
                    o2.close();
                    ok = c2.getResponseCode() == 200;
                    c2.disconnect();
                }
            } catch (Exception ignored) {
            }
            final boolean success = ok;
            main.post(() -> {
                status.setText(success ? ("已应用：" + label) : "应用失败，请检查绑定是否有效");
                if (success) {
                    selected = id;
                    load();
                }
            });
        }, "wp-apply").start();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
