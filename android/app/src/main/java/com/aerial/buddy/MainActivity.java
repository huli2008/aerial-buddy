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
        row.addView(countView);
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
        row.addView(name);

        TextView ip = new TextView(this);
        ip.setText("  " + d.host());
        ip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        ip.setTextColor(theme.dim);
        row.addView(ip);
        card.addView(row);

        TextView status = new TextView(this);
        status.setText(d.status == null ? "未连接" : d.status);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setTextColor(theme.dim);
        status.setPadding(0, dp(7), 0, dp(2));
        card.addView(status);

        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setPadding(0, dp(9), 0, 0);
        acts.addView(chip("壁纸", () -> showWallpapers(d)));
        acts.addView(chip("应用", () -> showApps(d)));
        acts.addView(chip("管理", () -> openPanel(d)));
        acts.addView(chip("安装 APK", () -> startInstall(d)));
        acts.addView(chip("刷新", () -> refresh(d)));
        card.addView(acts);

        card.setOnClickListener(v -> {
            if (d.pending) {
                pendingScanIp = d.ip();
                captureQr();
            } else {
                openPanel(d);
            }
        });
        card.setOnLongClickListener(v -> {
            confirmDelete(d);
            return true;
        });
        return card;
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
        if (d.pending) {
            d.status = "待授权：点一下扫电视二维码";
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
            public void onFinished(java.util.List<String> ips) {
                if (!dlg.isShowing()) return;
                dlg.dismiss();
                if (ips.isEmpty()) {
                    toast("没发现 Aerial 电视：确认电视开机、与本机同一 Wi-Fi");
                    return;
                }
                int added = 0;
                for (String ip : ips) {
                    if (findByIp(ip) != null) continue;
                    Device d = new Device();
                    d.id = String.valueOf(System.currentTimeMillis() + added);
                    d.name = "电视 " + ip.substring(ip.lastIndexOf('.') + 1);
                    d.url = "http://" + ip + ":8790/";
                    d.pending = true;
                    devices.add(d);
                    added++;
                }
                Store.save(MainActivity.this, devices);
                render();
                toast(added == 0 ? ("发现 " + ips.size() + " 台，都已在列表里")
                        : ("已添加 " + added + " 台，点一下扫二维码完成授权"));
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
            if (known.pending) {
                pendingScanIp = h;
                captureQr();
            } else {
                toast("这台电视已经在列表里");
                openPanel(known);
            }
            return;
        }
        Device d = new Device();
        d.id = String.valueOf(System.currentTimeMillis());
        d.name = "电视 " + h.substring(h.lastIndexOf('.') + 1);
        d.url = "http://" + h + ":8790/";
        d.pending = true;
        devices.add(d);
        Store.save(this, devices);
        render();
        pendingScanIp = h;
        captureQr();
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
            if (!d.pending && d.base().equals(url)) {
                pendingScanIp = null;
                toast("这台电视已经绑定过了");
                openPanel(d);
                return;
            }
        }
        Device target = findByIp(ip);
        if (target == null) {
            target = new Device();
            target.id = String.valueOf(System.currentTimeMillis());
            target.name = "电视 " + ip.substring(ip.lastIndexOf('.') + 1);
            devices.add(target);
        }
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

    private void decodeUri(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (in != null) in.close();
            String text = Qr.decode(bmp);
            if (text == null) {
                toast("没识别到二维码，靠近一点、对准再拍一次");
                return;
            }
            String url = Qr.toBaseUrl(text);
            if (url == null || !url.contains(":8790")) {
                toast("二维码内容不是电视地址：" + text);
                return;
            }
            bind(url);
        } catch (Exception e) {
            toast("读取图片失败：" + e.getMessage());
        }
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
