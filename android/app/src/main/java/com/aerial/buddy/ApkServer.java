package com.aerial.buddy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

/**
 * Serves one APK over the phone's Wi-Fi IP so the TV can pull it during a T-Cast push.
 * Kept intentionally tiny: GET/HEAD only, single file, no directory listing.
 */
public final class ApkServer {

    public interface Log {
        void line(String text);
    }

    private ServerSocket socket;
    private Thread thread;
    private volatile boolean running;
    private File file;
    private int port;

    public int port() {
        return port;
    }

    public void start(File apk, int port, Log log) throws Exception {
        this.file = apk;
        this.port = port;
        socket = new ServerSocket(port, 8, InetAddress.getByName("0.0.0.0"));
        running = true;
        thread = new Thread(this::loop, "apk-server");
        thread.setDaemon(true);
        thread.start();
        if (log != null) log.line("供包服务已启动 :" + port);
    }

    public void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }

    private void loop() {
        while (running) {
            try (Socket s = socket.accept()) {
                handle(s);
            } catch (Exception e) {
                if (!running) return;
            }
        }
    }

    private void handle(Socket s) throws Exception {
        s.setSoTimeout(15000);
        java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(s.getInputStream(), "UTF-8"));
        String request = in.readLine();
        if (request == null) return;
        String lower = request.toLowerCase(Locale.US);
        boolean head = lower.startsWith("head");
        // drain headers
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            // ignore
        }
        OutputStream out = s.getOutputStream();
        long len = file.length();
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/vnd.android.package-archive\r\n"
                + "Content-Length: " + len + "\r\n"
                + "Accept-Ranges: none\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes("UTF-8"));
        if (!head) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = fis.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
        out.flush();
    }

    /** First non-loopback IPv4 of the phone, or null. */
    public static String localIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                java.net.NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()) {
                        String ip = a.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static byte[] readAll(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = fis.read(b)) > 0) bos.write(b, 0, n);
        }
        return bos.toByteArray();
    }
}
