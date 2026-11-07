package com.aerial.intercom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Random;

/**
 * 弹幕层：把手机发来的文字/图片浮在电视画面上（需要悬浮窗权限，已通过 appops 授权）。
 */
public final class Overlay {

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final Random RND = new Random();

    private Overlay() {
    }

    private static WindowManager wm(Context ctx) {
        return (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    private static WindowManager.LayoutParams params(int w, int h) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w, h, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    public static void text(final Context ctx, final String text, final int color) {
        text(ctx, text, color, 0x66000000);
    }

    /** 带自定义背景色（含透明度）的弹幕。 */
    public static void text(final Context ctx, final String text, final int color, final int bgColor) {
        if (text == null || text.isEmpty()) return;
        UI.post(() -> {
            try {
                final TextView tv = new TextView(ctx);
                tv.setText(text);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                tv.setTextColor(color);
                tv.setShadowLayer(6f, 0f, 2f, Color.BLACK);
                tv.setGravity(Gravity.CENTER);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(30);
                bg.setColor(bgColor);
                tv.setBackground(bg);
                tv.setPadding(36, 14, 36, 14);

                final WindowManager w = wm(ctx);
                int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
                int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
                WindowManager.LayoutParams p = params(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
                p.y = screenH / 6 + RND.nextInt(Math.max(1, screenH / 3));
                p.x = screenW;
                w.addView(tv, p);

                final long start = System.currentTimeMillis();
                final long duration = 9000;
                final Runnable[] step = new Runnable[1];
                step[0] = () -> {
                    long now = System.currentTimeMillis();
                    float t = Math.min(1f, (now - start) / (float) duration);
                    int width = tv.getWidth() > 0 ? tv.getWidth() : 400;
                    p.x = (int) (screenW - t * (screenW + width));
                    try {
                        w.updateViewLayout(tv, p);
                    } catch (Exception ignored) {
                    }
                    if (t < 1f) UI.postDelayed(step[0], 16);
                    else {
                        try {
                            w.removeView(tv);
                        } catch (Exception ignored) {
                        }
                    }
                };
                UI.postDelayed(step[0], 16);
            } catch (Exception ignored) {
            }
        });
    }

    /** 图片：居中淡入，3 秒后消失。 */
    public static void image(final Context ctx, final Bitmap bmp) {
        if (bmp == null) return;
        UI.post(() -> {
            try {
                final ImageView iv = new ImageView(ctx);
                iv.setImageBitmap(bmp);
                iv.setAdjustViewBounds(true);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(18);
                bg.setColor(0x33000000);
                iv.setBackground(bg);

                final WindowManager w = wm(ctx);
                int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
                int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
                // 适应屏幕：最大占 80% 宽、75% 高，保持比例；小图放大到合适尺寸
                float maxW = screenW * 0.80f;
                float maxH = screenH * 0.75f;
                float scale = Math.min(maxW / bmp.getWidth(), maxH / bmp.getHeight());
                int width = Math.max(1, (int) (bmp.getWidth() * scale));
                int height = Math.max(1, (int) (bmp.getHeight() * scale));
                WindowManager.LayoutParams p = params(width, height);
                p.x = (screenW - width) / 2;
                p.y = (screenH - height) / 2;
                w.addView(iv, p);

                UI.postDelayed(() -> {
                    try {
                        w.removeView(iv);
                    } catch (Exception ignored) {
                    }
                }, 4000);
            } catch (Exception ignored) {
            }
        });
    }
}
