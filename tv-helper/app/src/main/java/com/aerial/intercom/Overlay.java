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

import java.io.File;
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
    /** 动图（GIF/WebP）：API 28+ 用 AnimatedImageDrawable，低版本退化为静帧。 */
    public static void gif(final Context ctx, final byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        UI.post(() -> {
            try {
                final android.widget.ImageView iv = new android.widget.ImageView(ctx);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(18);
                bg.setColor(0x33000000);
                iv.setBackground(bg);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.Source src =
                            android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes));
                    android.graphics.drawable.Drawable d =
                            android.graphics.ImageDecoder.decodeDrawable(src);
                    iv.setImageDrawable(d);
                    if (d instanceof android.graphics.drawable.AnimatedImageDrawable) {
                        ((android.graphics.drawable.AnimatedImageDrawable) d).start();
                    }
                } else {
                    Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp == null) return;
                    iv.setImageBitmap(bmp);
                }

                final WindowManager w = wm(ctx);
                int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
                int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
                int width = (int) (screenW * 0.6f);
                int height = (int) (screenH * 0.45f);
                WindowManager.LayoutParams p = params(width, height);
                p.x = (screenW - width) / 2;
                p.y = (screenH - height) / 2;
                w.addView(iv, p);

                UI.postDelayed(() -> {
                    try {
                        w.removeView(iv);
                    } catch (Exception ignored) {
                    }
                }, 12000);
            } catch (Exception ignored) {
            }
        });
    }

    /** 视频弹幕：保存到缓存后，用 VideoView 覆盖层播放（带声音，最多 30 秒）。 */
    public static void video(final Context ctx, final File file) {
        if (file == null || !file.exists()) return;
        UI.post(() -> {
            try {
                final android.widget.VideoView vv = new android.widget.VideoView(ctx);
                android.widget.MediaController mc = new android.widget.MediaController(ctx);
                vv.setMediaController(mc);
                vv.setVideoPath(file.getAbsolutePath());

                final WindowManager w = wm(ctx);
                int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
                int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
                int width = (int) (screenW * 0.7f);
                int height = (int) (screenH * 0.5f);
                WindowManager.LayoutParams p = params(width, height);
                p.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                p.x = (screenW - width) / 2;
                p.y = (screenH - height) / 2;
                w.addView(vv, p);

                vv.setOnPreparedListener(mp -> {
                    mp.setLooping(false);
                    vv.start();
                });
                vv.setOnCompletionListener(mp -> {
                    try {
                        w.removeView(vv);
                    } catch (Exception ignored) {
                    }
                });
                vv.setOnErrorListener((mp, what, extra) -> {
                    try {
                        w.removeView(vv);
                    } catch (Exception ignored) {
                    }
                    // 编码不支持时退化为「抽帧播放」：每秒一帧，最多 10 秒
                    playFramesFallback(ctx, file);
                    return true;
                });
                UI.postDelayed(() -> {
                    try {
                        w.removeView(vv);
                    } catch (Exception ignored) {
                    }
                }, 30000);
            } catch (Exception ignored) {
            }
        });
    }

    /** 视频解码失败时：抽帧做成逐帧动画，至少能看到内容。 */
    private static void playFramesFallback(final Context ctx, final File file) {
        try {
            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
            r.setDataSource(file.getAbsolutePath());
            String durStr = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            long dur = durStr == null ? 10000 : Long.parseLong(durStr);
            final java.util.List<Bitmap> frames = new java.util.ArrayList<>();
            long step = Math.max(500, dur / 10);
            for (long ms = 0; ms < Math.min(dur, 10000); ms += step) {
                Bitmap f = r.getFrameAtTime(ms * 1000L,
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (f != null) frames.add(f);
            }
            r.release();
            if (frames.isEmpty()) return;
            UI.post(() -> {
                final android.widget.ImageView iv = new android.widget.ImageView(ctx);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(18);
                bg.setColor(0x33000000);
                iv.setBackground(bg);
                final WindowManager w = wm(ctx);
                int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
                int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
                int width = (int) (screenW * 0.7f);
                int height = (int) (screenH * 0.5f);
                WindowManager.LayoutParams p = params(width, height);
                p.x = (screenW - width) / 2;
                p.y = (screenH - height) / 2;
                w.addView(iv, p);
                final int[] idx = {0};
                final Runnable tick = new Runnable() {
                    @Override
                    public void run() {
                        if (idx[0] >= frames.size()) {
                            try {
                                w.removeView(iv);
                            } catch (Exception ignored) {
                            }
                            return;
                        }
                        iv.setImageBitmap(frames.get(idx[0]++));
                        UI.postDelayed(this, 600);
                    }
                };
                UI.post(tick);
            });
        } catch (Exception ignored) {
        }
    }

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
