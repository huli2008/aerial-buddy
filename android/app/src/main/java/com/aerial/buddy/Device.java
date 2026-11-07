package com.aerial.buddy;

/** One bound TV: the base URL of its Aerial config server (host:8790 + token path). */
public class Device {
    public String id;
    public String name;
    public String url;      // e.g. http://192.168.8.192:8790/bjsaawba/
    public String status;   // last status text
    public int dot;         // 0 offline, 1 online, 2 unknown
    public boolean pending; // found by scan, still needs the QR token

    public String host() {
        String u = url == null ? "" : url;
        u = u.replace("http://", "").replace("https://", "");
        int slash = u.indexOf('/');
        return slash > 0 ? u.substring(0, slash) : u;
    }

    public String ip() {
        String h = host();
        int colon = h.indexOf(':');
        return colon > 0 ? h.substring(0, colon) : h;
    }

    public String base() {
        String u = url == null ? "" : url.trim();
        if (!u.startsWith("http")) u = "http://" + u;
        if (!u.endsWith("/")) u = u + "/";
        return u;
    }
}
