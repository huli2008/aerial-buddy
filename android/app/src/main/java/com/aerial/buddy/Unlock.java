package com.aerial.buddy;

import android.content.Context;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Sponsorship gate for 同屏 / 对讲.
 *
 * <p>Two ways in, both verifiable without a store listing:
 * <ul>
 *   <li><b>设备码</b> — offline, bound to this phone (HMAC of ANDROID_ID).</li>
 *   <li><b>兑换码</b> — a code sold through any auto-delivery shop, redeemed against the small
 *       license server in tools/license-server.py; the server marks it used so one code = one TV.</li>
 * </ul>
 */
public final class Unlock {

    /** Keep in sync with tools/gen-sponsor-code.py. */
    private static final String SALT = "aerial-buddy-2026-sponsor";
    private static final String ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String PREFS = "aerial_buddy";
    private static final String KEY = "mirror_unlocked";
    private static final String SERVER_KEY = "license_server";

    private Unlock() {
    }

    public static boolean isUnlocked(Context ctx) {
        return prefs(ctx).getBoolean(KEY, false);
    }

    public static void setUnlocked(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY, value).apply();
    }

    public static String deviceCode(Context ctx) {
        return androidId(ctx);
    }

    public static String serverUrl(Context ctx) {
        return prefs(ctx).getString(SERVER_KEY, "");
    }

    public static void setServerUrl(Context ctx, String url) {
        prefs(ctx).edit().putString(SERVER_KEY, url == null ? "" : url.trim()).apply();
    }

    public static String codeFor(String androidId) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(SALT.getBytes("UTF-8"), "HmacSHA256"));
            byte[] raw = mac.doFinal(androidId.trim().toLowerCase().getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append(ALPHABET.charAt((raw[i] & 0xff) % ALPHABET.length()));
            }
            return sb.substring(0, 5) + "-" + sb.substring(5);
        } catch (Exception e) {
            return "-----";
        }
    }

    /** Device-bound code check (works offline). */
    public static boolean checkLocal(Context ctx, String input) {
        if (input == null) return false;
        String typed = input.replace("-", "").replace(" ", "").toUpperCase();
        String expected = codeFor(androidId(ctx)).replace("-", "").toUpperCase();
        return typed.length() == expected.length()
                && java.security.MessageDigest.isEqual(typed.getBytes(), expected.getBytes());
    }

    /**
     * Redeems a purchased code against the license server.
     *
     * @return null on success, otherwise a message to show.
     */
    public static String redeem(Context ctx, String code) {
        String base = serverUrl(ctx);
        if (base.isEmpty()) {
            return "还没配置授权服务器：长按「设备码」填写服务器地址（见 tools/license-server.py）";
        }
        if (!base.startsWith("http")) base = "http://" + base;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + "/redeem").openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setDoOutput(true);
            String body = "device=" + androidId(ctx) + "&code=" + code.trim().replace("-", "").toUpperCase();
            OutputStream out = c.getOutputStream();
            out.write(body.getBytes("UTF-8"));
            out.flush();
            out.close();
            int status = c.getResponseCode();
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    status >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject o = new JSONObject(sb.toString());
            if (o.optBoolean("ok", false)) return null;
            return o.optString("reason", "兑换码无效");
        } catch (Exception e) {
            return "连不上授权服务器：" + (e.getMessage() == null ? "网络错误" : e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static android.content.SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String androidId(Context ctx) {
        String id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null ? "0000000000000000" : id.toLowerCase();
    }
}
