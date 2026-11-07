package com.aerial.buddy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Pushes an APK to a TCL / 雷鸟 TV through 多屏互动 (T-Cast, TCP 6553).
 *
 * <p>The TV shows a 同意 prompt, downloads the APK from the phone's local HTTP server and installs it
 * with its own system privileges — the only way to install on firmware that blocks {@code pm install}.
 */
public final class Tcast {

    public interface Cb {
        void onPrompt();                 // TV is asking the viewer to confirm

        void onProgress(int stage, int percent);

        void onInstalled();

        void onFailed(String reason);

        void onLog(String line);
    }

    private static final int PORT = 6553;

    private Tcast() {
    }

    public static void push(final String tvIp, final String url, final String pkg,
                            final String label, final int versionCode, final Cb cb) {
        new Thread(() -> {
            Socket sock = null;
            try {
                sock = new Socket();
                sock.connect(new InetSocketAddress(tvIp, PORT), 4000);
                sock.setSoTimeout(1000);
                DataOutputStream out = new DataOutputStream(sock.getOutputStream());
                DataInputStream in = new DataInputStream(sock.getInputStream());

                send(out, "159>>1>>0");
                String pathMd5 = ApkServer.md5(url);
                String msg = String.join(">>",
                        "223", "INSTALL", pkg, label, url, "2",
                        String.valueOf(versionCode), "20", "", "", "", pathMd5);
                send(out, msg);
                cb.onLog("已发起推送，等待电视确认…");

                long deadline = System.currentTimeMillis() + 180_000L;
                long lastBeat = System.currentTimeMillis();
                byte[] lenBuf = new byte[4];

                while (System.currentTimeMillis() < deadline) {
                    try {
                        in.readFully(lenBuf);
                        int n = ((lenBuf[0] & 0xff) << 24) | ((lenBuf[1] & 0xff) << 16)
                                | ((lenBuf[2] & 0xff) << 8) | (lenBuf[3] & 0xff);
                        if (n <= 0 || n > 1 << 20) continue;
                        byte[] body = new byte[n];
                        in.readFully(body);
                        String line = new String(body, StandardCharsets.UTF_8);
                        String[] parts = line.split(">>");
                        if (parts.length < 5 || !"223".equals(parts[0]) || !pkg.equals(parts[2])) {
                            continue;
                        }
                        if ("INSTALL".equals(parts[1])) {
                            String st = parts[4];
                            if ("confirm_pending".equals(st)) cb.onPrompt();
                            else if ("denied".equals(st)) {
                                cb.onFailed("电视上点了拒绝");
                                return;
                            }
                        } else if ("PROGRESS".equals(parts[1])) {
                            int stage = parseInt(parts[4]);
                            int pct = parts.length > 5 ? parseInt(parts[5]) : 0;
                            cb.onProgress(stage, pct);
                            if (stage == 5) {
                                cb.onInstalled();
                                return;
                            }
                            if (stage == 2) {
                                cb.onFailed("电视下载或安装失败");
                                return;
                            }
                            if (stage == 4) {
                                cb.onFailed("电视已下载但拒绝安装（声明了 HOME 的 APK 会被 TCL 拒绝）");
                                return;
                            }
                        }
                    } catch (java.net.SocketTimeoutException te) {
                        // heartbeat keeps the session alive
                    }
                    if (System.currentTimeMillis() - lastBeat > 5000) {
                        send(out, "150>>");
                        lastBeat = System.currentTimeMillis();
                    }
                }
                cb.onFailed("等待电视超时");
            } catch (Exception e) {
                cb.onFailed(e.getMessage() == null ? "连接电视失败" : e.getMessage());
            } finally {
                try {
                    if (sock != null) sock.close();
                } catch (Exception ignored) {
                }
            }
        }, "tcast-push").start();
    }

    private static void send(DataOutputStream out, String text) throws Exception {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
        out.flush();
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Best-effort check that a file looks like an APK. */
    public static boolean looksLikeApk(File f) {
        return f != null && f.length() > 1024 && f.getName().toLowerCase().endsWith(".apk");
    }
}
