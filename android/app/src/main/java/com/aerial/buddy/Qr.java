package com.aerial.buddy;

import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Decodes the QR shown on the TV (http://&lt;ip&gt;:8790/&lt;token&gt;/).
 *
 * <p>A photo of a TV panel has glare and moiré that defeat ZXing's own binarizers; a plain global
 * threshold on the grayscale image reads them reliably (verified against a real photo that failed
 * every automatic attempt).
 */
public final class Qr {

    private static final int MAX_SIDE = 1600;
    private static final int[] LEVELS = {120, 140, 150, 160, 170, 180, 190, 200};

    private Qr() {
    }

    public static String decode(Bitmap bmp) {
        if (bmp == null) return null;
        Bitmap b = scale(bmp, MAX_SIDE);
        int w = b.getWidth(), h = b.getHeight();
        if (w < 16 || h < 16) return null;
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);

        String text = attempt(px, w, h, false);
        if (text == null) text = attempt(px, w, h, true);
        if (text != null) return text;

        int otsu = otsu(px);
        text = attempt(threshold(px, otsu), w, h, false);
        if (text != null) return text;
        for (int lv : LEVELS) {
            text = attempt(threshold(px, lv), w, h, false);
            if (text != null) return text;
        }
        return null;
    }

    private static String attempt(int[] px, int w, int h, boolean invert) {
        try {
            LuminanceSource source = new RGBLuminanceSource(w, h, px);
            if (invert) source = source.invert();
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
            MultiFormatReader reader = new MultiFormatReader();
            try {
                Result r = reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints);
                if (r != null && r.getText() != null) return r.getText();
            } catch (Throwable ignored) {
            } finally {
                reader.reset();
            }
            try {
                Result r = reader.decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)), hints);
                if (r != null && r.getText() != null) return r.getText();
            } catch (Throwable ignored) {
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int[] threshold(int[] px, int level) {
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int g = ((p >> 16 & 0xff) * 299 + (p >> 8 & 0xff) * 587 + (p & 0xff) * 114) / 1000;
            int v = g > level ? 0xff : 0x00;
            out[i] = 0xff000000 | (v << 16) | (v << 8) | v;
        }
        return out;
    }

    private static int otsu(int[] px) {
        int[] hist = new int[256];
        for (int p : px) {
            int g = ((p >> 16 & 0xff) * 299 + (p >> 8 & 0xff) * 587 + (p & 0xff) * 114) / 1000;
            hist[g]++;
        }
        long total = px.length, sum = 0;
        for (int i = 0; i < 256; i++) sum += (long) i * hist[i];
        long sumB = 0, wB = 0;
        double best = 0;
        int bestT = 128;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            long wF = total - wB;
            if (wF == 0) break;
            sumB += (long) t * hist[t];
            double mB = (double) sumB / wB;
            double mF = (double) (sum - sumB) / wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) {
                best = between;
                bestT = t;
            }
        }
        return bestT;
    }

    private static Bitmap scale(Bitmap bmp, int max) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int big = Math.max(w, h);
        if (big <= max) return bmp;
        float f = (float) max / big;
        try {
            return Bitmap.createScaledBitmap(bmp, Math.max(1, (int) (w * f)), Math.max(1, (int) (h * f)), true);
        } catch (Throwable t) {
            return bmp;
        }
    }

    /** Extracts the base URL from the QR text, keeping the token path. */
    public static String toBaseUrl(String text) {
        if (text == null) return null;
        String t = text.trim();
        int at = t.indexOf("http");
        if (at > 0) t = t.substring(at);
        if (!t.startsWith("http://") && !t.startsWith("https://")) {
            t = "http://" + t;
        }
        int q = t.indexOf('?');
        if (q > 0) t = t.substring(0, q);
        if (!t.endsWith("/")) t = t + "/";
        return t;
    }
}
