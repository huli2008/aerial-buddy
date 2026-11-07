package com.aerial.buddy;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds Aerial TVs on the current Wi-Fi: scans the /24 around the phone for TCP :8790 and confirms the
 * host by asking a path the Aerial config server does not serve — it answers 404 with "Not found".
 */
public final class Scanner {

    public interface Cb {
        void onProgress(int scanned, int total);

        void onFinished(List<String> ips);

        void onError(String message);
    }

    private static final int PORT = 8790;
    private static final int CONNECT_TIMEOUT_MS = 320;
    private static final int READ_TIMEOUT_MS = 700;
    private static final int THREADS = 64;

    private Scanner() {
    }

    public static void scan(final Context ctx, final Cb cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String self = localIp(ctx);
                if (self == null) {
                    main.post(() -> cb.onError("没有连上 Wi-Fi，或拿不到本机地址"));
                    return;
                }
                final String prefix = self.substring(0, self.lastIndexOf('.') + 1);
                final int total = 254;
                final AtomicInteger done = new AtomicInteger();
                final List<String> found = Collections.synchronizedList(new ArrayList<String>());
                final CountDownLatch latch = new CountDownLatch(total);
                ExecutorService pool = Executors.newFixedThreadPool(THREADS);
                for (int i = 1; i <= total; i++) {
                    final String ip = prefix + i;
                    pool.execute(() -> {
                        try {
                            if (!ip.equals(self) && probe(ip)) {
                                found.add(ip);
                                main.post(() -> cb.onProgress(done.get(), total));
                            }
                        } finally {
                            int n = done.incrementAndGet();
                            if (n % 16 == 0 || n == total) main.post(() -> cb.onProgress(n, total));
                            latch.countDown();
                        }
                    });
                }
                latch.await(30, TimeUnit.SECONDS);
                pool.shutdownNow();
                Collections.sort(found);
                final List<String> result = new ArrayList<>(found);
                main.post(() -> cb.onFinished(result));
            } catch (Exception e) {
                main.post(() -> cb.onError(e.getMessage() == null ? "扫描失败" : e.getMessage()));
            }
        }, "aerial-scan").start();
    }

    private static boolean probe(String ip) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, PORT), CONNECT_TIMEOUT_MS);
        } catch (Exception e) {
            return false;
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + ip + ":" + PORT + "/api/state").openConnection();
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            if (code != 404) return false;
            String body = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            return body != null && body.trim().equals("Not found");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) {
        if (in == null) return null;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    static String localIp(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            if (lp != null) {
                for (LinkAddress la : lp.getLinkAddresses()) {
                    InetAddress a = la.getAddress();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
