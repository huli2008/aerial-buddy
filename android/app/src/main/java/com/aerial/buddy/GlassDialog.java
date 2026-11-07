package com.aerial.buddy;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 统一的毛玻璃弹窗：圆角 24、半透明底、1px 描边、主题按钮。 */
public final class GlassDialog {

    private GlassDialog() {
    }

    public static final class Builder {
        private final Context ctx;
        private final Theme theme;
        private String title;
        private View content;
        private String positive, negative;
        private Runnable onPositive, onNegative;
        private boolean cancelOutside = true;

        public Builder(Context ctx, Theme theme) {
            this.ctx = ctx;
            this.theme = theme;
        }

        public Builder title(String t) {
            title = t;
            return this;
        }

        public Builder content(View v) {
            content = v;
            return this;
        }

        public Builder positive(String label, Runnable r) {
            positive = label;
            onPositive = r;
            return this;
        }

        public Builder negative(String label, Runnable r) {
            negative = label;
            onNegative = r;
            return this;
        }

        public Dialog show() {
            final Dialog dlg = new Dialog(ctx);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            Window w = dlg.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            LinearLayout glass = new LinearLayout(ctx);
            glass.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(24));
            bg.setColor(theme.glass);
            bg.setStroke(dp(1), theme.glassStroke);
            glass.setBackground(bg);
            int p = dp(20);
            glass.setPadding(p, dp(18), p, dp(16));

            if (title != null && !title.isEmpty()) {
                TextView t = new TextView(ctx);
                t.setText(title);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                t.setTextColor(theme.text);
                t.setPadding(0, 0, 0, dp(10));
                glass.addView(t);
            }

            if (content != null) {
                glass.addView(content, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            LinearLayout buttons = new LinearLayout(ctx);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);
            buttons.setPadding(0, dp(14), 0, 0);

            if (negative != null) {
                TextView n = button(negative, theme.card, theme.text, theme.glassStroke);
                n.setOnClickListener(v -> {
                    dlg.dismiss();
                    if (onNegative != null) onNegative.run();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
                lp.rightMargin = dp(10);
                buttons.addView(n, lp);
            }
            if (positive != null) {
                TextView pos = button(positive, theme.accent, theme.accentText, theme.accent);
                pos.setOnClickListener(v -> {
                    dlg.dismiss();
                    if (onPositive != null) onPositive.run();
                });
                buttons.addView(pos, new LinearLayout.LayoutParams(0, dp(44), 1f));
            }
            if (buttons.getChildCount() > 0) glass.addView(buttons);

            dlg.setContentView(glass);
            dlg.setCanceledOnTouchOutside(cancelOutside);
            dlg.show();
            Window win = dlg.getWindow();
            if (win != null) {
                int width = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.88f);
                win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            return dlg;
        }

        private TextView button(String label, int bgColor, int textColor, int stroke) {
            TextView v = new TextView(ctx);
            v.setText(label);
            v.setGravity(Gravity.CENTER);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            v.setTextColor(textColor);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(14));
            d.setColor(bgColor);
            d.setStroke(dp(1), stroke);
            v.setBackground(d);
            return v;
        }

        private int dp(int v) {
            return Math.round(ctx.getResources().getDisplayMetrics().density * v);
        }
    }

    /** 便捷方法：玻璃列表弹窗；item 里用 \n 分隔主标题与副标题。 */
    public static void list(Context ctx, Theme theme, String title, String[] items,
                            final OnItem onItem) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] ref = new Dialog[1];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            String raw = items[i] == null ? "" : items[i];
            String main = raw;
            String sub = null;
            int nl = raw.indexOf(10);
            if (nl > 0) {
                main = raw.substring(0, nl);
                sub = raw.substring(nl + 1);
            }
            LinearLayout row = UiKit.listRow(ctx, theme, null, main, sub, false, () -> {
                if (ref[0] != null) ref[0].dismiss();
                onItem.on(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Math.round(ctx.getResources().getDisplayMetrics().density * 6);
            box.addView(row, lp);
        }
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(box);
        ref[0] = new Builder(ctx, theme)
                .title(title)
                .content(scroll)
                .negative("关闭", null)
                .show();
    }

    public interface OnItem {
        void on(int index);
    }

    /** 便捷方法：纯文本弹窗。 */
    public static void text(Context ctx, Theme theme, String title, String message,
                            String positive, Runnable onPositive) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(ctx);
        tv.setText(message);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(theme.dim);
        tv.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(box);
        box.addView(tv);
        new Builder(ctx, theme)
                .title(title)
                .content(scroll)
                .positive(positive == null ? "知道了" : positive, onPositive)
                .show();
    }
}
