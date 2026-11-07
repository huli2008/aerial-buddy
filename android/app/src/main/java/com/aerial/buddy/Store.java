package com.aerial.buddy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Bound devices persisted as JSON. */
public final class Store {
    private static final String PREFS = "aerial_buddy";
    private static final String KEY = "devices";
    private static final String THEME = "theme";

    private Store() {
    }

    public static List<Device> load(Context ctx) {
        List<Device> out = new ArrayList<>();
        String raw = prefs(ctx).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Device d = new Device();
                d.id = o.optString("id");
                d.name = o.optString("name");
                d.url = o.optString("url");
                out.add(d);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context ctx, List<Device> devices) {
        JSONArray arr = new JSONArray();
        for (Device d : devices) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", d.id);
                o.put("name", d.name);
                o.put("url", d.url);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply();
    }

    public static int theme(Context ctx) {
        return prefs(ctx).getInt(THEME, Theme.AURORA);
    }

    public static void setTheme(Context ctx, int id) {
        prefs(ctx).edit().putInt(THEME, id).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
