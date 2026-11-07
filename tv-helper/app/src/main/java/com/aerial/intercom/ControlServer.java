package com.aerial.intercom;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;

/**
 * Brand-independent control channel for the phone (port 8769).
 *
 * <p>Everything here works without Aerial: the TV app itself can enumerate packages and launch other
 * apps, which is what makes the phone's app list survive firmwares where Aerial is not running.
 *
 * <pre>
 * GET /home            -> bring Aerial (or the launcher) to front
 * GET /apps            -> {"ok":true,"apps":[{"pkg":..,"label":..,"system":true|false}, ...]}
 * GET /launch?pkg=..   -> launch a package
 * GET /mirror          -> ask for screen capture permission / start the mirror service
 * GET /health          -> {"ok":true}
 * </pre>
 */
public final class ControlServer {

    public static final int PORT = 8769;
    private static volatile boolean running;
    private static Context appContext;

    private ControlServer() {
    }

    public static void start(Context ctx) {
        appContext = ctx.getApplicationContext();
        if (running) return;
        running = true;
        Thread t = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                while (running) {
                    try (Socket s = server.accept()) {
                        java.io.InputStream rawIn = s.getInputStream();
                        // 按字节解析请求头：BufferedReader 会把 POST 的二进制正文吞进缓冲区，导致图片损坏。
                        java.io.ByteArrayOutputStream headBuf = new java.io.ByteArrayOutputStream();
                        int prev3 = -1, prev2 = -1, prev1 = -1, cur;
                        while ((cur = rawIn.read()) != -1) {
                            headBuf.write(cur);
                            if (prev3 == 13 && prev2 == 10 && prev1 == 13 && cur == 10) break;
                            prev3 = prev2;
                            prev2 = prev1;
                            prev1 = cur;
                            if (headBuf.size() > 16384) break;
                        }
                        String head = new String(headBuf.toByteArray(), "UTF-8");
                        String path = "/";
                        int contentLength = 0;
                        String[] lines = head.split("\r\n");
                        if (lines.length > 0) {
                            String[] parts = lines[0].split(" ");
                            if (parts.length > 1) path = parts[1];
                        }
                        for (String h : lines) {
                            if (h.toLowerCase().startsWith("content-length:")) {
                                try {
                                    contentLength = Integer.parseInt(h.split(":")[1].trim());
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        String body;
                        if (path.startsWith("/image")) {
                            byte[] data = new byte[Math.max(0, Math.min(contentLength, 8 * 1024 * 1024))];
                            int read = 0;
                            while (read < data.length) {
                                int n = rawIn.read(data, read, data.length - read);
                                if (n <= 0) break;
                                read += n;
                            }
                            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(data, 0, read);
                            if (bmp != null) {
                                Overlay.image(appContext, bmp);
                                body = "{\"ok\":true}";
                            } else {
                                body = "{\"ok\":false,\"reason\":\"decode failed\"}";
                            }
                        } else {
                            body = handle(path);
                        }
                        byte[] b = body.getBytes("UTF-8");
                        OutputStream out = s.getOutputStream();
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n"
                                + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                        out.write(b);
                        out.flush();
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
                running = false;
            }
        }, "control-server");
        t.setDaemon(true);
        t.start();
    }

    private static String handle(String path) {
        try {
            if (path.startsWith("/health")) return "{\"ok\":true}";
            if (path.startsWith("/apps")) return apps();
            if (path.startsWith("/launch")) return launch(path);
            if (path.startsWith("/key")) return key(path);
            if (path.startsWith("/volume")) return volume(path);
            if (path.startsWith("/say")) return say(path);
            if (path.startsWith("/danmaku")) return danmaku(path);
            if (path.startsWith("/mirror/start") || path.startsWith("/mirrorstart")) return mirrorStart();
            if (path.startsWith("/mirror")) return mirror();
            if (path.startsWith("/home")) return home();
            return "{\"ok\":false,\"reason\":\"unknown\"}";
        } catch (Exception e) {
            return "{\"ok\":false,\"reason\":\"" + e.getClass().getSimpleName() + "\"}";
        }
    }

    private static String apps() {
        JSONArray arr = new JSONArray();
        try {
            PackageManager pm = appContext.getPackageManager();
            List<ApplicationInfo> list = pm.getInstalledApplications(0);
            Collections.sort(list, (a, b) -> String.valueOf(pm.getApplicationLabel(a))
                    .compareToIgnoreCase(String.valueOf(pm.getApplicationLabel(b))));
            for (ApplicationInfo ai : list) {
                JSONObject o = new JSONObject();
                o.put("pkg", ai.packageName);
                CharSequence label = pm.getApplicationLabel(ai);
                o.put("label", label == null ? ai.packageName : label.toString());
                o.put("system", (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        return "{\"ok\":true,\"apps\":" + arr + "}";
    }

    private static String launch(String path) {
        String pkg = param(path, "pkg");
        if (pkg.isEmpty()) return "{\"ok\":false,\"reason\":\"no pkg\"}";
        try {
            PackageManager pm = appContext.getPackageManager();
            // 电视应用通常只有 LEANBACK_LAUNCHER，getLaunchIntentForPackage 会返回 null。
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return "{\"ok\":false,\"reason\":\"no launch intent\"}";
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(i);
            return "{\"ok\":true}";
        } catch (Exception e) {
            return "{\"ok\":false,\"reason\":\"blocked\"}";
        }
    }

    /** 主页 / 返回 / 最近（无障碍全局动作，不需要 ADB）。 */
    private static String key(String path) {
        String code = param(path, "code").toUpperCase();
        int action;
        switch (code) {
            case "HOME":
                action = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME;
                break;
            case "BACK":
                action = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;
                break;
            case "RECENTS":
                action = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS;
                break;
            default:
                return "{\"ok\":false,\"reason\":\"unsupported key\"}";
        }
        boolean ok = MirrorAutoGrant.globalAction(action);
        return ok ? "{\"ok\":true}" : "{\"ok\":false,\"reason\":\"accessibility off\"}";
    }

    /** 音量 +/-/静音（AudioManager，任何电视都能用）。 */
    private static String volume(String path) {
        String d = param(path, "d");
        try {
            android.media.AudioManager am = (android.media.AudioManager)
                    appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return "{\"ok\":false,\"reason\":\"no audio\"}";
            int stream = android.media.AudioManager.STREAM_MUSIC;
            int dir;
            if ("mute".equals(d)) {
                dir = android.media.AudioManager.ADJUST_TOGGLE_MUTE;
            } else if ("up".equals(d)) {
                dir = android.media.AudioManager.ADJUST_RAISE;
            } else if ("down".equals(d)) {
                dir = android.media.AudioManager.ADJUST_LOWER;
            } else {
                return "{\"ok\":false,\"reason\":\"bad delta\"}";
            }
            String err = null;
            try {
                am.adjustStreamVolume(stream, dir, android.media.AudioManager.FLAG_SHOW_UI);
            } catch (Throwable t1) {
                err = t1.getClass().getSimpleName();
                try {
                    am.adjustVolume(dir, android.media.AudioManager.FLAG_SHOW_UI);
                    err = null;
                } catch (Throwable t2) {
                    err = t2.getClass().getSimpleName();
                    try {
                        int cur = am.getStreamVolume(stream);
                        int max = am.getStreamMaxVolume(stream);
                        int next = dir == android.media.AudioManager.ADJUST_RAISE ? Math.min(max, cur + 1)
                                : dir == android.media.AudioManager.ADJUST_LOWER ? Math.max(0, cur - 1) : 0;
                        am.setStreamVolume(stream, next, android.media.AudioManager.FLAG_SHOW_UI);
                        err = null;
                    } catch (Throwable t3) {
                        err = t3.getClass().getSimpleName();
                    }
                }
            }
            if (err != null) return "{\"ok\":false,\"reason\":\"audio " + err + "\"}";
            return "{\"ok\":true,\"volume\":" + am.getStreamVolume(stream) + "}";
        } catch (Exception e) {
            return "{\"ok\":false,\"reason\":\"audio failed\"}";
        }
    }

    /** 弹幕：文字浮在屏幕上（可带颜色 #RRGGBB）。 */
    private static String danmaku(String path) {
        String text = param(path, "text");
        if (text.isEmpty()) return "{\"ok\":false,\"reason\":\"no text\"}";
        int color = 0xFFFFFFFF;
        try {
            String c = param(path, "color");
            if (!c.isEmpty()) color = 0xFF000000 | Integer.parseInt(c.replace("#", ""), 16);
        } catch (Exception ignored) {
        }
        int bg = 0x66000000;
        try {
            String bgHex = param(path, "bg");
            String alphaHex = param(path, "bgAlpha");
            if (!bgHex.isEmpty()) {
                int rgb = Integer.parseInt(bgHex.replace("#", ""), 16) & 0xFFFFFF;
                int alpha = 0x66;
                if (!alphaHex.isEmpty()) {
                    int a = Integer.parseInt(alphaHex, 16);
                    alpha = Math.max(0, Math.min(255, a));
                }
                bg = (alpha << 24) | rgb;
            }
        } catch (Exception ignored) {
        }
        Overlay.text(appContext, text, color, bg);
        return "{\"ok\":true}";
    }

    /** 电视喊话：把文字念出来。 */
    private static String say(String path) {
        String text = param(path, "text");
        if (text.isEmpty()) return "{\"ok\":false,\"reason\":\"no text\"}";
        try {
            if (Speaker.say(appContext, text)) return "{\"ok\":true}";
            return "{\"ok\":false,\"reason\":\"tts unavailable\"}";
        } catch (Exception e) {
            return "{\"ok\":false,\"reason\":\"tts failed\"}";
        }
    }

    private static String mirror() {
        try {
            Intent i = new Intent(appContext, ConsentActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(i);
            return "{\"ok\":true}";
        } catch (Exception e) {
            return "{\"ok\":false,\"reason\":\"blocked\"}";
        }
    }

    /** 远程启动同屏：拉起透明 ConsentActivity → 无障碍自动点「立即开始」→ 起服务。 */
    private static String mirrorStart() {
        try {
            Intent i = new Intent(appContext, ConsentActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            appContext.startActivity(i);
            return "{\"ok\":true,\"state\":\"starting\"}";
        } catch (Exception e) {
            return "{\"ok\":false,\"reason\":\"blocked\"}";
        }
    }

    private static String home() {
        try {
            Intent aerial = new Intent();
            aerial.setComponent(new android.content.ComponentName("app.orchard.tv", "app.orchard.tv.MainActivity"));
            aerial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(aerial);
            return "{\"ok\":true}";
        } catch (Exception e) {
            try {
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(home);
                return "{\"ok\":true}";
            } catch (Exception e2) {
                return "{\"ok\":false,\"reason\":\"blocked\"}";
            }
        }
    }

    private static String param(String path, String key) {
        int q = path.indexOf('?');
        if (q < 0) return "";
        for (String pair : path.substring(q + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try {
                    return java.net.URLDecoder.decode(kv[1], "UTF-8");
                } catch (Exception e) {
                    return kv[1];
                }
            }
        }
        return "";
    }
}
