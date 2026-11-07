package com.aerial.intercom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Keeps the intercom receiver alive after a reboot or an app update. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (action == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        // 先试着静默起同屏（ConsentActivity 无界面），再起对讲接收
        try {
            Intent c = new Intent(context, ConsentActivity.class);
            c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(c);
        } catch (Exception ignored) {
        }
        Intent i = new Intent(context, IntercomService.class);
        i.setAction(IntercomService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
        } catch (Exception ignored) {
        }
    }
}
