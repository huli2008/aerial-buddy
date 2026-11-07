package com.aerial.buddy;

/**
 * One TV on the LAN.
 *
 * <p>{@code host} is always the TV's IP, because "push an APK" only needs the IP (T-Cast on 6553).
 * {@code url} is the Aerial config base URL and stays empty until the QR has been scanned — that is
 * what makes it possible to install Aerial on a TV that does not have Aerial yet.
 */
public class Device {
    public String id;
    public String name;
    public String host;     // 192.168.8.192
    public String url;      // http://192.168.8.192:8790/<token>/  (empty until bound)
    public String status;   // last status text
    public int dot;         // 0 offline, 1 online, 2 unknown
    public boolean pending; // not bound yet: needs the QR token before wallpapers/apps work
    public boolean autoChecked; // first-connection "is Aerial installed?" probe already done

    public boolean bound() {
        return url != null && !url.trim().isEmpty();
    }

    public String host() {
        String u = url == null ? "" : url;
        u = u.replace("http://", "").replace("https://", "");
        int slash = u.indexOf('/');
        String h = slash > 0 ? u.substring(0, slash) : u;
        if (h.isEmpty()) h = host == null ? "" : host;
        return h;
    }

    public String ip() {
        String h = host();
        int colon = h.indexOf(':');
        return colon > 0 ? h.substring(0, colon) : h;
    }

    public String base() {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return "";
        if (!u.startsWith("http")) u = "http://" + u;
        if (!u.endsWith("/")) u = u + "/";
        return u;
    }
}
