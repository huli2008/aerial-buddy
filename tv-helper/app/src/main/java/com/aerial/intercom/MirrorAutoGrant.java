package com.aerial.intercom;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Clicks the system "start capturing" dialog for us so mirroring can start without touching the remote.
 *
 * <p>The MediaProjection consent dialog is owned by SystemUI and cannot be suppressed by appops on
 * these TCL firmwares, so instead we watch for it and press the positive button ourselves. The service
 * is enabled once via adb (`settings put secure enabled_accessibility_services ...`).
 */
public class MirrorAutoGrant extends AccessibilityService {

    /** 供 ControlServer 调用全局动作（主页/返回/最近）。 */
    private static volatile MirrorAutoGrant instance;

    public static boolean globalAction(int action) {
        MirrorAutoGrant s = instance;
        if (s == null) return false;
        return s.performGlobalAction(action);
    }

    public static boolean available() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    private static final String[] POSITIVE = {
            "立即开始", "开始", "开始录制", "立即开始录制", "允许", "确定",
            "Start now", "Start", "Allow", "OK"
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !"com.android.systemui".equals(pkg.toString())) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        for (String label : POSITIVE) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
            if (nodes == null) continue;
            for (AccessibilityNodeInfo n : nodes) {
                if (n == null) continue;
                AccessibilityNodeInfo target = n;
                // 文本节点常常是按钮的子节点，往上找可点击的父节点
                for (int depth = 0; depth < 3 && target != null && !target.isClickable(); depth++) {
                    target = target.getParent();
                }
                if (target != null && target.isClickable()) {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}
