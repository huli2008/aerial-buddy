package com.aerial.buddy;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/** Talks to the Aerial config server (host:8790/<token>/api/...). */
public final class AerialApi {

    private AerialApi() {
    }

    public static JSONObject state(Device d) throws Exception {
        return call(d, "api/state", null, null);
    }

    public static JSONObject post(Device d, String path, Map<String, String> form) throws Exception {
        return call(d, path, form, null);
    }

    public static JSONObject post(Device d, String path, Map<String, String> form, String pin) throws Exception {
        return call(d, path, form, pin);
    }

    private static JSONObject call(Device d, String path, Map<String, String> form, String pin)
            throws Exception {
        URL u = new URL(d.base() + path);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Accept", "application/json");
        if (pin != null) c.setRequestProperty("X-Aerial-Pin", pin);
        if (form != null) {
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : form.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
            }
            byte[] body = sb.toString().getBytes("UTF-8");
            c.setFixedLengthStreamingMode(body.length);
            OutputStream os = c.getOutputStream();
            os.write(body);
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = read(in);
        if (code >= 400) throw new Exception("HTTP " + code);
        return new JSONObject(text == null || text.isEmpty() ? "{}" : text);
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }
}
