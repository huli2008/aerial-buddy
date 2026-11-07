package com.aerial.buddy;

import android.content.Context;

/** 3-day trial window, counted from the very first launch of the app. */
public final class Trial {

    private static final String PREFS = "aerial_buddy";
    private static final String KEY = "first_use";
    private static final long DAY = 24L * 60 * 60 * 1000;

    private Trial() {
    }

    public static void init(Context ctx) {
        android.content.SharedPreferences p = prefs(ctx);
        if (p.getLong(KEY, 0L) == 0L) {
            p.edit().putLong(KEY, System.currentTimeMillis()).apply();
        }
    }

    public static long remainingMs(Context ctx) {
        long start = prefs(ctx).getLong(KEY, System.currentTimeMillis());
        return start + 3 * DAY - System.currentTimeMillis();
    }

    public static boolean active(Context ctx) {
        return remainingMs(ctx) > 0;
    }

    /** e.g. "还剩 2 天 5 小时" */
    public static String left(Context ctx) {
        long ms = remainingMs(ctx);
        if (ms <= 0) return "试用已结束";
        long days = ms / DAY;
        long hours = (ms % DAY) / (60L * 60 * 1000);
        if (days > 0) return "试用还剩 " + days + " 天 " + hours + " 小时";
        long minutes = Math.max(1, (ms % (60L * 60 * 1000)) / 60000);
        return "试用还剩 " + hours + " 小时 " + minutes + " 分";
    }

    private static android.content.SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
