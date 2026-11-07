package com.aerial.buddy;

import android.content.Context;
import android.graphics.Bitmap;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Offline OCR for payment screenshots (Tesseract, chi_sim + eng bundled in the APK). */
public final class ReceiptOcr {

    private static final String LANG = "chi_sim+eng";
    private static boolean prepared;

    private ReceiptOcr() {
    }

    /** Copies the traineddata out of assets once, because Tesseract reads it from the filesystem. */
    public static synchronized boolean prepare(Context ctx) {
        if (prepared) return true;
        try {
            File dir = new File(ctx.getFilesDir(), "tessdata");
            if (!dir.exists() && !dir.mkdirs()) return false;
            for (String name : new String[]{"chi_sim.traineddata", "eng.traineddata"}) {
                File out = new File(dir, name);
                if (out.exists() && out.length() > 1024 * 1024) continue;
                InputStream in = ctx.getAssets().open("tessdata/" + name);
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[128 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                fos.close();
                in.close();
            }
            prepared = true;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the recognised text, or null when the engine is unavailable. */
    public static String text(Context ctx, Bitmap bitmap) {
        if (bitmap == null || !prepare(ctx)) return null;
        TessBaseAPI api = new TessBaseAPI();
        try {
            // tess-two appends "tessdata/" itself, so the datapath must be the parent dir.
            if (!api.init(ctx.getFilesDir().getAbsolutePath(), LANG)) return null;
            api.setImage(bitmap);
            String out = api.getUTF8Text();
            return out == null ? "" : out;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                api.end();
            } catch (Throwable ignored) {
            }
        }
    }
}
