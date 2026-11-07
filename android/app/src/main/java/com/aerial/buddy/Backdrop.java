package com.aerial.buddy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Gradient backdrop with two soft colour glows — the "frosted glass" base. */
public class Backdrop extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Theme theme;

    public Backdrop(Context c, Theme t) {
        super(c);
        this.theme = t;
    }

    public void setTheme(Theme t) {
        this.theme = t;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        paint.setShader(new LinearGradient(0, 0, w * 0.3f, h,
                theme.bgTop, theme.bgBottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new RadialGradient(w * 0.82f, h * 0.08f, w * 0.75f,
                new int[]{alpha(theme.glowA, 120), alpha(theme.glowA, 0)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new RadialGradient(w * 0.1f, h * 0.92f, w * 0.7f,
                new int[]{alpha(theme.glowB, 90), alpha(theme.glowB, 0)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
