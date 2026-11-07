package com.aerial.buddy;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 统一的 UI 构件：玻璃卡片、主/次按钮、列表行、分组标题。
 *
 * <p>目标是把散落在各个 Activity 里的 dp()/GradientDrawable/按钮样式收口到一处，
 * 以后改圆角、改间距只动这里。
 */
public final class UiKit {

    private UiKit() {
    }

    public static int dp(Context ctx, float v) {
        return Math.round(ctx.getResources().getDisplayMetrics().density * v);
    }

    /** 玻璃卡片背景。 */
    public static GradientDrawable glass(Theme t, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radiusDp);
        d.setColor(t.glass);
        d.setStroke(1, t.glassStroke);
        return d;
    }

    /** 普通卡片背景。 */
    public static GradientDrawable card(Theme t, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radiusDp);
        d.setColor(t.card);
        d.setStroke(1, t.cardStroke);
        return d;
    }

    /** 主按钮（实心主色）。 */
    public static TextView primary(Context ctx, Theme t, String label) {
        return button(ctx, t, label, t.accent, t.accentText, t.accent);
    }

    /** 次按钮（透明/卡片底 + 描边）。 */
    public static TextView secondary(Context ctx, Theme t, String label) {
        return button(ctx, t, label, t.card, t.text, t.cardStroke);
    }

    public static TextView button(Context ctx, Theme t, String label,
                                  int bgColor, int textColor, int strokeColor) {
        TextView v = new TextView(ctx);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setTextColor(textColor);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(ctx, 14));
        d.setColor(bgColor);
        d.setStroke(1, strokeColor);
        v.setBackground(d);
        return v;
    }

    /** 分组标题（小灰字 + 主色短竖线）。 */
    public static View sectionLabel(Context ctx, Theme t, String text) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(ctx, 14), 0, dp(ctx, 6));

        View bar = new View(ctx);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setCornerRadius(dp(ctx, 1.5f));
        barBg.setColor(t.accent);
        bar.setBackground(barBg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(ctx, 3), dp(ctx, 12));
        blp.rightMargin = dp(ctx, 6);
        row.addView(bar, blp);

        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        label.setTextColor(t.dim);
        row.addView(label);
        return row;
    }

    /** 列表行：图标 + 标题 +（可选）副标题 + 右箭头。 */
    public static LinearLayout listRow(Context ctx, Theme t, String icon, String title,
                                       String subtitle, boolean highlight, Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 14), dp(ctx, 13), dp(ctx, 14), dp(ctx, 13));
        GradientDrawable bg = highlight ? glass(t, dp(ctx, 16)) : card(t, dp(ctx, 16));
        row.setBackground(bg);

        if (icon != null && !icon.isEmpty()) {
            TextView ic = new TextView(ctx);
            ic.setText(icon);
            ic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.rightMargin = dp(ctx, 10);
            row.addView(ic, ilp);
        }

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tt = new TextView(ctx);
        tt.setText(title);
        tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tt.setTextColor(t.text);
        tt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(tt);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView st = new TextView(ctx);
            st.setText(subtitle);
            st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            st.setTextColor(t.dim);
            st.setPadding(0, dp(ctx, 2), 0, 0);
            texts.addView(st);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(ctx);
        arrow.setText("›");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        arrow.setTextColor(t.dim);
        row.addView(arrow);

        if (onClick != null) {
            row.setOnClickListener(v -> onClick.run());
            row.setClickable(true);
        }
        return row;
    }

    /** 顶部高光线（玻璃边缘反光）。 */
    public static View highlight(Context ctx, Theme t) {
        View v = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(ctx, 2));
        d.setColor(t.light ? 0x80FFFFFF : 0x4DFFFFFF);
        v.setBackground(d);
        return v;
    }

    /** 状态胶囊。 */
    public static TextView statusPill(Context ctx, Theme t, String text) {
        TextView v = new TextView(ctx);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        v.setTextColor(t.dim);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(ctx, 14), dp(ctx, 7), dp(ctx, 14), dp(ctx, 7));
        v.setBackground(glass(t, dp(ctx, 14)));
        return v;
    }

    /** 占位：颜色工具，便于以后统一调色。 */
    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    /** 深色/浅色通用的分割线。 */
    public static View divider(Context ctx, Theme t) {
        View v = new View(ctx);
        v.setBackgroundColor(t.light ? Color.parseColor("#1A000000") : Color.parseColor("#22FFFFFF"));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(ctx, 0.5f))));
        return v;
    }
}
