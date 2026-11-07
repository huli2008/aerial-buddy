package com.aerial.buddy;

import android.util.Base64;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.io.File;

/**
 * Minimal ADB client: just enough to run {@code pm install} on a TV that has network debugging on.
 *
 * <p>Used for brands without TCL's 多屏互动 (小米 / 索尼 / 海信 / 创维 …). The first connection makes the
 * TV show its "allow debugging" dialog; after that the stored RSA key is trusted.
 */
public final class AdbClient {

    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    private static final int MAX_DATA = 4096 * 16;

    public interface Log {
        void line(String text);
    }

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private int nextLocalId = 1;
    private final Log log;

    public AdbClient(Log log) {
        this.log = log;
    }

    public boolean connect(String host, int port, int timeoutMs) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(12000);
            in = new DataInputStream(socket.getInputStream());
            out = socket.getOutputStream();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }

    /** CNXN + AUTH handshake. Returns false when the TV rejects us. */
    public boolean handshake(File keyDir) {
        try {
            send(A_CNXN, 0x01000000, MAX_DATA,
                    ("host::features=cmd,stat_v2,ls_v2,fixed_push_mkdir,apex\0").getBytes("UTF-8"));
            KeyPair keys = null;
            int tokens = 0;
            for (int i = 0; i < 8; i++) {
                Packet p = read();
                if (p == null) return false;
                if (p.command == A_CNXN) return true;
                if (p.command == A_AUTH && p.arg0 == AUTH_TOKEN) {
                    if (keys == null) keys = loadOrCreateKeys(keyDir);
                    tokens++;
                    if (tokens == 1) {
                        // 先签一次，证明我们有私钥
                        send(A_AUTH, AUTH_SIGNATURE, 0, sign(keys.getPrivate(), p.payload));
                    } else {
                        // 电视不认识这把钥匙：把公钥发过去，电视会弹「允许调试」
                        send(A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKey(keys));
                        if (log != null) log.line("电视上弹出「允许调试」提示，请用遥控器选择允许");
                        // 保持连接等待用户在电视上点「允许」，adbd 同意后会发 CNXN
                        try {
                            socket.setSoTimeout(90000);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Runs a shell command, optionally feeding stdin, and collects its output. */
    public String exec(String command, byte[] stdin, Log sink) {
        try {
            int local = nextLocalId++;
            send(A_OPEN, local, 0, (command + "\u0000").getBytes("UTF-8"));
            int remote = 0;
            // wait for OKAY
            while (true) {
                Packet p = read();
                if (p == null) return null;
                if (p.command == A_OKAY && p.arg1 == local) {
                    remote = p.arg0;
                    break;
                }
                if (p.command == A_CLSE && p.arg1 == local) return "";
            }
            StringBuilder sb = new StringBuilder();
            if (stdin != null && stdin.length > 0) {
                int off = 0;
                while (off < stdin.length) {
                    int n = Math.min(MAX_DATA, stdin.length - off);
                    byte[] chunk = new byte[n];
                    System.arraycopy(stdin, off, chunk, 0, n);
                    send(A_WRTE, remote, local, chunk);
                    off += n;
                    drain(sb, sink, remote, local, true);
                }
            }
            send(A_CLSE, remote, local, new byte[0]);
            drain(sb, sink, remote, local, false);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void drain(StringBuilder sb, Log sink, int remote, int local, boolean untilOkay) {
        try {
            while (true) {
                Packet p = read();
                if (p == null) return;
                if (p.command == A_WRTE && p.arg1 == local) {
                    String chunk = new String(p.payload, "UTF-8");
                    sb.append(chunk);
                    if (sink != null) sink.line(chunk.trim());
                    send(A_OKAY, remote, local, new byte[0]);
                } else if (p.command == A_OKAY && p.arg1 == local) {
                    if (untilOkay) return;
                } else if (p.command == A_CLSE && p.arg1 == local) {
                    return;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static final class Packet {
        int command;
        int arg0;
        int arg1;
        byte[] payload;
    }

    private Packet read() {
        try {
            byte[] head = new byte[24];
            in.readFully(head);
            ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
            Packet p = new Packet();
            p.command = b.getInt();
            p.arg0 = b.getInt();
            p.arg1 = b.getInt();
            int len = b.getInt();
            b.getInt(); // checksum
            b.getInt(); // magic
            if (len > 0) {
                p.payload = new byte[len];
                in.readFully(p.payload);
            } else {
                p.payload = new byte[0];
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private void send(int command, int arg0, int arg1, byte[] payload) throws IOException {
        int len = payload == null ? 0 : payload.length;
        byte[] head = new byte[24];
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(command);
        b.putInt(arg0);
        b.putInt(arg1);
        b.putInt(len);
        int sum = 0;
        if (payload != null) for (byte x : payload) sum += (x & 0xff);
        b.putInt(sum);
        b.putInt(command ^ 0xffffffff);
        out.write(head);
        if (payload != null && len > 0) out.write(payload);
        out.flush();
    }

    // ---------- keys ----------

    private static KeyPair loadOrCreateKeys(File dir) throws Exception {
        File priv = new File(dir, "adb_key.pk8");
        File pub = new File(dir, "adb_key.pub");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.exists() && pub.exists()) {
            byte[] pk = readAll(priv);
            byte[] pb = readAll(pub);
            return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(pb)),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(pk)));
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        writeAll(priv, kp.getPrivate().getEncoded());
        writeAll(pub, kp.getPublic().getEncoded());
        return kp;
    }

    private static byte[] sign(PrivateKey key, byte[] token) throws Exception {
        Signature s = Signature.getInstance("SHA1withRSA");
        s.initSign(key);
        s.update(token);
        return s.sign();
    }

    /** Android's adb public key format: base64(len(n) n len(e) e " user@host"). */
    private static byte[] publicKey(KeyPair kp) throws Exception {
        java.security.interfaces.RSAPublicKey rsa =
                (java.security.interfaces.RSAPublicKey) kp.getPublic();
        byte[] n = trim(rsa.getModulus().toByteArray());
        byte[] e = trim(rsa.getPublicExponent().toByteArray());
        ByteBuffer bb = ByteBuffer.allocate(4 + n.length + 4 + e.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(n.length);
        bb.put(n);
        bb.putInt(e.length);
        bb.put(e);
        String b64 = Base64.encodeToString(bb.array(), Base64.NO_WRAP);
        return (b64 + " aerial-buddy@android\u0000").getBytes("UTF-8");
    }

    private static byte[] trim(byte[] in) {
        int i = 0;
        while (i < in.length - 1 && in[i] == 0) i++;
        byte[] out = new byte[in.length - i];
        System.arraycopy(in, i, out, 0, out.length);
        return out;
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            new DataInputStream(in).readFully(b);
            return b;
        }
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        try (OutputStream out = new java.io.FileOutputStream(f)) {
            out.write(data);
        }
    }
}
