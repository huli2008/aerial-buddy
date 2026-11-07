package com.aerial.buddy;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * 复用同一条 ADB 连接。
 *
 * <p>以前每次按键都新建 TCP 连接 → 重新握手 → 电视每次都弹「允许调试」。现在按 IP 缓存一条
 * 已授权的连接，一次授权覆盖整个使用过程。
 */
public final class AdbSession {

    private static final Map<String, AdbSession> SESSIONS = new HashMap<>();

    private final String ip;
    private AdbClient client;
    private boolean authorized;

    private AdbSession(String ip) {
        this.ip = ip;
    }

    public static AdbSession get(String ip) {
        synchronized (SESSIONS) {
            AdbSession s = SESSIONS.get(ip);
            if (s == null) {
                s = new AdbSession(ip);
                SESSIONS.put(ip, s);
            }
            return s;
        }
    }

    public static void drop(String ip) {
        synchronized (SESSIONS) {
            AdbSession s = SESSIONS.remove(ip);
            if (s != null) s.close();
        }
    }

    public synchronized boolean ensure(Context ctx) {
        if (client != null && authorized) return true;
        close();
        AdbClient c = new AdbClient(null);
        if (!c.connect(ip, 5555, 3500)) {
            return false;
        }
        if (!c.handshake(ctx.getFilesDir())) {
            c.close();
            return false;
        }
        client = c;
        authorized = true;
        return true;
    }

    public synchronized String exec(String command, byte[] stdin) {
        if (client == null) return null;
        String out = client.exec(command, stdin, null);
        if (out == null) close(); // 连接断了，下次重新握手
        return out;
    }

    public synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
        authorized = false;
    }
}
