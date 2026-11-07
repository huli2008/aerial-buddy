package com.aerial.buddy;

import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.EnumMap;
import java.util.Map;

/** Decodes the QR shown on the TV (http://<ip>:8790/<token>/). */
public final class Qr {

    private Qr() {
    }

    public static String decode(Bitmap bmp) {
        if (bmp == null) return null;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        RGBLuminanceSource source = new RGBLuminanceSource(w, h, px);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        MultiFormatReader reader = new MultiFormatReader();
        try {
            Result r = reader.decode(bitmap, hints);
            return r == null ? null : r.getText();
        } catch (Exception e) {
            return null;
        } finally {
            reader.reset();
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
