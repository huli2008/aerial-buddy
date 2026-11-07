package com.aerial.buddy;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;

/** Shared "push an APK to the TV through 多屏互动" flow. */
public final class Installer {

    public interface Done {
        void finished(boolean ok, String message);
    }

    private Installer() {
    }

    /** 有 T-Cast（6553）就走多屏互动，否则尝试 ADB（需要电视开启网络调试）。 */
    public static void push(final Activity act, final Device d, final File apk, final Done done) {
        if (tcastOpen(d.ip())) {
            pushTcast(act, d, apk, done);
        } else {
            pushAdb(act, d, apk, done);
        }
    }

    /**
     * 按通道安装：0=自动（ADB 优先，失败回退多屏互动），1=仅 ADB，2=仅多屏互动。
     */
    public static void pushChannel(final Activity act, final Device d, final File apk,
                                   final int mode, final Done done) {
        if (mode == 1) {
            pushAdb(act, d, apk, done);
            return;
        }
        if (mode == 2) {
            pushTcast(act, d, apk, done);
            return;
        }
        // 自动：先探测 ADB 端口，通了先用 ADB；ADB 失败再回退多屏互动
        new Thread(() -> {
            boolean adbOpen;
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(d.ip(), 5555), 900);
                adbOpen = true;
            } catch (Exception e) {
                adbOpen = false;
            }
            final boolean useAdb = adbOpen;
            act.runOnUiThread(() -> {
                if (useAdb) {
                    pushAdb(act, d, apk, (ok, msg) -> {
                        if (ok) {
                            done.finished(true, msg);
                        } else {
                            pushTcast(act, d, apk, done);
                        }
                    });
                } else {
                    pushTcast(act, d, apk, done);
                }
            });
        }, "probe-adb").start();
    }

    private static boolean tcastOpen(String ip) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(ip, 6553), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void pushAdb(final Activity act, final Device d, final File apk, final Done done) {
        final ProgressDialog pd = new ProgressDialog(act);
        pd.setTitle("ADB 安装到 " + d.name);
        pd.setMessage("正在连接 " + d.ip() + ":5555 …");
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            AdbClient adb = new AdbClient(null);
            String result = null;
            try {
                if (!adb.connect(d.ip(), 5555, 4000)) {
                    result = "连不上 " + d.ip() + ":5555（电视需开启「网络调试/ADB」）";
                } else if (!adb.handshake(act.getFilesDir())) {
                    result = "ADB 认证失败：电视上弹出「允许调试」时请用遥控器选择允许，然后重试";
                } else {
                    byte[] data = new byte[(int) apk.length()];
                    try (java.io.InputStream in = new java.io.FileInputStream(apk)) {
                        new java.io.DataInputStream(in).readFully(data);
                    }
                    // 复用已授权连接：一次授权，后面安装/按键都不再弹窗
                    AdbSession session = AdbSession.get(d.ip());
                    String out = session.ensure(act) ? session.exec("pm install -r -S " + data.length, data) : null;
                    if (out == null || (!out.contains("Success") && !out.contains("成功"))) {
                        // 回退：先 cat 到临时目录再安装
                        session.exec("cat > /data/local/tmp/aerial.apk", data);
                        out = session.exec("pm install -r /data/local/tmp/aerial.apk", null);
                        session.exec("rm -f /data/local/tmp/aerial.apk", null);
                    }
                    result = (out != null && (out.contains("Success") || out.contains("成功")))
                            ? null : ("安装失败：" + (out == null ? "无输出" : out.trim()));
                }
            } catch (Exception e) {
                result = "ADB 异常：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                adb.close();
            }
            final String msg = result;
            act.runOnUiThread(() -> {
                pd.dismiss();
                if (msg == null) done.finished(true, "已通过 ADB 安装");
                else done.finished(false, msg);
            });
        }, "adb-install").start();
    }

    private static void pushTcast(final Activity act, final Device d, final File apk, final Done done) {
        PackageManager pm = act.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null) {
            done.finished(false, "这不是有效的 APK");
            return;
        }
        final String pkg = info.packageName;
        final int vc = info.versionCode;
        String label = pkg;
        ApplicationInfo ai = info.applicationInfo;
        if (ai != null) {
            ai.sourceDir = apk.getAbsolutePath();
            ai.publicSourceDir = apk.getAbsolutePath();
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null) label = l.toString();
        }
        String ip = ApkServer.localIp();
        if (ip == null) {
            done.finished(false, "手机没连 Wi-Fi");
            return;
        }

        final ProgressDialog pd = new ProgressDialog(act);
        pd.setTitle("推送到 " + d.name);
        pd.setMessage("正在发起…");
        pd.setCancelable(false);
        pd.show();

        final ApkServer server = new ApkServer();
        try {
            server.start(apk, 8765, null);
        } catch (Exception e) {
            pd.dismiss();
            done.finished(false, "本地供包失败：" + e.getMessage());
            return;
        }
        String url = "http://" + ip + ":8765/" + apk.getName();
        final String finalLabel = label;

        Tcast.push(d.ip(), url, pkg, finalLabel, vc, new Tcast.Cb() {
            @Override
            public void onPrompt() {
                act.runOnUiThread(() -> pd.setMessage("电视上弹出安装提示，请按遥控器「右键」选同意"));
            }

            @Override
            public void onProgress(int stage, int percent) {
                act.runOnUiThread(() -> pd.setMessage(
                        stage == 3 ? ("正在安装 " + percent + "%") : ("传输中 " + percent + "%")));
            }

            @Override
            public void onInstalled() {
                server.stop();
                act.runOnUiThread(() -> {
                    pd.dismiss();
                    done.finished(true, "已安装 " + finalLabel);
                });
            }

            @Override
            public void onFailed(String reason) {
                server.stop();
                act.runOnUiThread(() -> {
                    pd.dismiss();
                    done.finished(false, "安装失败：" + reason);
                });
            }

            @Override
            public void onLog(String line) {
                act.runOnUiThread(() -> pd.setMessage(line));
            }
        });
    }
}
