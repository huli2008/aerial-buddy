package com.aerial.buddy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 应用管理：图标 + 名称 + 包名 + 卸载按钮；底部「安装应用」。 */
public class AppListActivity extends Activity {

    private static final int REQ_APK = 31;

    private final List<App> apps = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> icons = new LruCache<>(120);

    private Device device;
    private Theme theme;
    private AppAdapter adapter;
    private TextView subtitle;

    static class App {
        String key;
        String label;
        long stamp;
        String pkg;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        device = new Device();
        device.name = getIntent().getStringExtra("name");
        device.url = getIntent().getStringExtra("url");
        theme = Theme.of(getIntent().getIntExtra("theme", Theme.AURORA));

        FrameLayout root = new FrameLayout(this);
        root.addView(new Backdrop(this, theme), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        page.setPadding(pad, dp(18), pad, dp(12));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("应用管理");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(theme.text);
        head.addView(title);
        subtitle = new TextView(this);
        subtitle.setText("  " + device.name);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setTextColor(theme.dim);
        head.addView(subtitle);
        TextView organize = new TextView(this);
        organize.setText("整理");
        organize.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        organize.setTextColor(theme.text);
        organize.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable obg = new GradientDrawable();
        obg.setCornerRadius(dp(12));
        obg.setColor(theme.light ? Color.parseColor("#14000000") : Color.parseColor("#22FFFFFF"));
        organize.setBackground(obg);
        organize.setOnClickListener(v -> toast("整理模式（拖动排序 / 隐藏）开发中，下个版本开放"));
        head.addView(organize);
        page.addView(head);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(8));
        list.setPadding(0, dp(12), 0, dp(8));
        list.setClipToPadding(false);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        page.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView install = new TextView(this);
        install.setText("＋  安装应用");
        install.setGravity(Gravity.CENTER);
        install.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        install.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        install.setTextColor(theme.accentText);
        GradientDrawable ib = new GradientDrawable();
        ib.setCornerRadius(dp(16));
        ib.setColor(theme.accent);
        install.setBackground(ib);
        install.setOnClickListener(v -> pickApk());
        page.addView(install, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        load();
    }

    private void load() {
        subtitle.setText("  " + device.name + " · 读取中…");
        new Thread(() -> {
            try {
                JSONObject s = AerialApi.state(device);
                JSONArray arr = s.optJSONArray("apps");
                List<App> out = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        App a = new App();
                        a.key = o.optString("key");
                        String custom = o.optString("name", "");
                        a.label = custom.isEmpty() ? o.optString("label", a.key) : custom;
                        a.stamp = o.optLong("stamp", 0);
                        a.pkg = a.key;
                        out.add(a);
                    }
                }
                main.post(() -> {
                    apps.clear();
                    apps.addAll(out);
                    adapter.notifyDataSetChanged();
                    subtitle.setText("  " + device.name + " · " + apps.size() + " 个应用");
                });
            } catch (Exception e) {
                // Aerial 没运行 / 没绑定时，改问电视端小助手要包列表（不依赖 Aerial）。
                List<App> viaHelper = loadFromHelper();
                main.post(() -> {
                    if (viaHelper == null || viaHelper.isEmpty()) {
                        subtitle.setText("  " + device.name + " · 读取失败");
                        toast("读取应用列表失败：" + e.getMessage());
                        return;
                    }
                    apps.clear();
                    apps.addAll(viaHelper);
                    adapter.notifyDataSetChanged();
                    subtitle.setText("  " + device.name + " · " + apps.size() + " 个应用（来自电视端）");
                });
            }
        }, "apps-load").start();
    }

    private void pickApk() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "选择 APK"), REQ_APK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_APK || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            File f = new File(getCacheDir(), "install-" + System.currentTimeMillis() + ".apk");
            InputStream in = getContentResolver().openInputStream(data.getData());
            FileOutputStream out = new FileOutputStream(f);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
            Installer.push(this, device, f, (ok, msg) -> {
                toast(msg);
                if (ok) load();
            });
        } catch (Exception e) {
            toast("读取 APK 失败：" + e.getMessage());
        }
    }

    /** 让电视打开某个应用（电视端 8769 /launch）。 */
    private void launchOnTv(final String pkg, final String label) {
        toast("正在电视上打开 " + label);
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL("http://" + device.ip() + ":8769/launch?pkg="
                        + java.net.URLEncoder.encode(pkg, "UTF-8"));
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(4000);
                java.io.InputStream in = c.getInputStream();
                byte[] buf = new byte[2048];
                int n = in.read(buf);
                in.close();
                String body = n > 0 ? new String(buf, 0, n, "UTF-8") : "";
                c.disconnect();
                main.post(() -> toast(body.contains("\"ok\":true") ? ("已在电视上打开 " + label)
                        : "电视端拒绝了打开请求（可能是系统应用或无启动入口）"));
            } catch (Exception e) {
                main.post(() -> toast("打开失败：" + e.getMessage()));
            }
        }, "launch").start();
    }

    /** 电视端 GET :8769/apps —— 任何品牌都能用，只要装了电视端小助手。 */
    private List<App> loadFromHelper() {
        java.net.HttpURLConnection c = null;
        try {
            java.net.URL u = new java.net.URL("http://" + device.ip() + ":8769/apps");
            c = (java.net.HttpURLConnection) u.openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            JSONObject o = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
            JSONArray arr = o.optJSONArray("apps");
            if (arr == null) return null;
            List<App> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject a = arr.optJSONObject(i);
                App app = new App();
                app.key = a.optString("pkg");
                app.pkg = app.key;
                app.label = a.optString("label", app.key);
                app.stamp = 0; // 0 = 没有图标缓存，画首字母
                out.add(app);
            }
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 首字母头像，给没有图标的列表用。 */
    private Bitmap letterIcon(String label) {
        String key = "letter:" + label;
        Bitmap cached = icons.get(key);
        if (cached != null) return cached;
        int size = Math.round(getResources().getDisplayMetrics().density * 44);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        int hue = Math.abs(label.hashCode()) % 360;
        p.setColor(android.graphics.Color.HSVToColor(new float[]{hue, 0.45f, 0.75f}));
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(Color.WHITE);
        p.setTextSize(size * 0.45f);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        String ch = label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase();
        android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(ch, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        icons.put(key, bmp);
        return bmp;
    }

    private void uninstall(final App a) {
        new AlertDialog.Builder(this)
                .setTitle("卸载 " + a.label)
                .setMessage(a.pkg + "\n\n通过电视的「多屏互动」执行；系统应用可能被固件保护而失败。")
                .setNegativeButton("取消", null)
                .setPositiveButton("卸载", (dlg, w) -> {
                    toast("正在卸载…");
                    new Thread(() -> {
                        int code = Tcast.uninstall(device.ip(), a.pkg, a.label);
                        // 电视返回码含义未公开（实测不存在的包也返回 1），所以以"应用列表里还在不在"为准：
                        // 连续轮询 5 次，包消失即成功。
                        boolean gone = false;
                        for (int i = 0; i < 5 && !gone; i++) {
                            try {
                                Thread.sleep(1200);
                            } catch (InterruptedException ignored) {
                            }
                            gone = !stillInstalled(a.pkg);
                        }
                        final boolean ok = gone;
                        final int c = code;
                        main.post(() -> {
                            if (ok) {
                                toast("已卸载 " + a.label);
                            } else {
                                toast("卸载失败（电视返回 " + c + "）：系统应用或被固件保护");
                            }
                            load();
                        });
                    }, "uninstall").start();
                })
                .show();
    }

    /** True when the package still shows up in the TV's app list. */
    private boolean stillInstalled(String pkg) {
        try {
            JSONObject s = AerialApi.state(device);
            JSONArray apps = s.optJSONArray("apps");
            if (apps == null) return true;
            for (int i = 0; i < apps.length(); i++) {
                if (pkg.equals(apps.optJSONObject(i).optString("key"))) return true;
            }
            return false;
        } catch (Exception e) {
            return true; // 读不到就当还在，避免误报成功
        }
    }

    private void icon(final App a, final ImageView view) {
        Bitmap cached = icons.get(a.key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageBitmap(null);
        pool.execute(() -> {
            Bitmap bmp = null;
            try {
                String u = device.base() + "tile/" + Uri.encode(a.key) + "?v=" + a.stamp;
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(4000);
                if (c.getResponseCode() == 200) {
                    bmp = BitmapFactory.decodeStream(c.getInputStream());
                }
                c.disconnect();
            } catch (Exception ignored) {
            }
            final Bitmap b = bmp;
            if (b != null) {
                icons.put(a.key, b);
                main.post(() -> view.setImageBitmap(b));
            }
        });
    }

    private boolean isSystem(String pkg) {
        return pkg.startsWith("com.tcl.") || pkg.startsWith("com.android.")
                || pkg.startsWith("com.google.") || pkg.startsWith("com.mediatek.");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private final class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            final App a = apps.get(position);
            LinearLayout row = new LinearLayout(AppListActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(10), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(theme.card);
            bg.setStroke(dp(1), theme.cardStroke);
            row.setBackground(bg);

            ImageView icon = new ImageView(AppListActivity.this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
            ilp.rightMargin = dp(12);
            row.addView(icon, ilp);
            if (a.stamp == 0) icon.setImageBitmap(letterIcon(a.label));
            else icon(a, icon);

            LinearLayout text = new LinearLayout(AppListActivity.this);
            text.setOrientation(LinearLayout.VERTICAL);
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout nameRow = new LinearLayout(AppListActivity.this);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(AppListActivity.this);
            name.setText(a.label);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
            name.setTextColor(theme.text);
            name.setMaxLines(1);
            nameRow.addView(name);
            if (isSystem(a.pkg)) {
                TextView tag = new TextView(AppListActivity.this);
                tag.setText(" 系统");
                tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                tag.setTextColor(theme.dim);
                nameRow.addView(tag);
            }
            text.addView(nameRow);

            TextView pkg = new TextView(AppListActivity.this);
            pkg.setText(a.pkg);
            pkg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            pkg.setTextColor(theme.dim);
            pkg.setMaxLines(1);
            text.addView(pkg);

            TextView open = new TextView(AppListActivity.this);
            open.setText("打开");
            open.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            open.setTextColor(theme.text);
            open.setPadding(dp(12), dp(7), dp(12), dp(7));
            GradientDrawable ob = new GradientDrawable();
            ob.setCornerRadius(dp(12));
            ob.setColor(theme.light ? Color.parseColor("#66FFFFFF") : Color.parseColor("#22FFFFFF"));
            ob.setStroke(dp(1), theme.accent);
            open.setBackground(ob);
            open.setOnClickListener(v -> launchOnTv(a.pkg, a.label));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            olp.rightMargin = dp(6);
            row.addView(open, olp);

            TextView del = new TextView(AppListActivity.this);
            del.setText("卸载");
            del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            del.setTextColor(theme.text);
            del.setPadding(dp(12), dp(7), dp(12), dp(7));
            GradientDrawable db = new GradientDrawable();
            db.setCornerRadius(dp(12));
            db.setColor(theme.light ? Color.parseColor("#66FFFFFF") : Color.parseColor("#22FFFFFF"));
            db.setStroke(dp(1), Color.parseColor("#66F2555A"));
            del.setBackground(db);
            del.setOnClickListener(v -> uninstall(a));
            row.addView(del);
            return row;
        }
    }
}
