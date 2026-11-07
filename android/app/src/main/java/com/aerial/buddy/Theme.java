package com.aerial.buddy;

import android.graphics.Color;

/** Three glass themes: Aurora (default), Midnight, Mist. */
public final class Theme {

    public static final int AURORA = 0;
    public static final int MIDNIGHT = 1;
    public static final int MIST = 2;

    public final int id;
    public final String name;
    public final int bgTop, bgBottom, glowA, glowB;
    public final int card, cardStroke, text, dim, accent, accentText;
    public final boolean light;

    private Theme(int id, String name, int bgTop, int bgBottom, int glowA, int glowB,
                  int card, int cardStroke, int text, int dim, int accent, int accentText,
                  boolean light) {
        this.id = id;
        this.name = name;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.glowA = glowA;
        this.glowB = glowB;
        this.card = card;
        this.cardStroke = cardStroke;
        this.text = text;
        this.dim = dim;
        this.accent = accent;
        this.accentText = accentText;
        this.light = light;
    }

    public static final Theme[] ALL = new Theme[]{
            new Theme(AURORA, "极光",
                    Color.parseColor("#241A4F"), Color.parseColor("#0B1020"),
                    Color.parseColor("#7C5CFF"), Color.parseColor("#36C6F4"),
                    Color.parseColor("#26FFFFFF"), Color.parseColor("#3DFFFFFF"),
                    Color.WHITE, Color.parseColor("#B9C0D4"),
                    Color.parseColor("#7C5CFF"), Color.WHITE, false),
            new Theme(MIDNIGHT, "夜幕",
                    Color.parseColor("#101A22"), Color.parseColor("#070A0E"),
                    Color.parseColor("#1F8A70"), Color.parseColor("#2D6CDF"),
                    Color.parseColor("#1FFFFFFF"), Color.parseColor("#33FFFFFF"),
                    Color.WHITE, Color.parseColor("#9AA7B4"),
                    Color.parseColor("#38D39F"), Color.parseColor("#04221A"), false),
            new Theme(MIST, "晨雾",
                    Color.parseColor("#EEF3FA"), Color.parseColor("#D9E3F0"),
                    Color.parseColor("#FFFFFF"), Color.parseColor("#BBD4F5"),
                    Color.parseColor("#B3FFFFFF"), Color.parseColor("#E6FFFFFF"),
                    Color.parseColor("#1B2430"), Color.parseColor("#5A6B7E"),
                    Color.parseColor("#3B82F6"), Color.WHITE, true)
    };

    public static Theme of(int id) {
        for (Theme t : ALL) if (t.id == id) return t;
        return ALL[0];
    }
}
