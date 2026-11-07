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

    public static void push(final Activity act, final Device d, final File apk, final Done done) {
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
