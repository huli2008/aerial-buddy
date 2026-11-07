package com.aerial.intercom;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/** 电视喊话：用系统 TTS 念出手机发来的文字。 */
public final class Speaker {

    private static TextToSpeech tts;
    private static boolean ready;
    private static String pending;

    private Speaker() {
    }

    public static synchronized boolean say(Context ctx, String text) {
        if (tts == null) {
            tts = new TextToSpeech(ctx.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true;
                    try {
                        tts.setLanguage(Locale.CHINA);
                    } catch (Exception ignored) {
                    }
                    if (pending != null) {
                        speak(pending);
                        pending = null;
                    }
                }
            });
        }
        if (!ready) {
            pending = text;
            return true; // 首次调用是异步初始化，稍后会自动念
        }
        return speak(text);
    }

    private static boolean speak(String text) {
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aerial");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
