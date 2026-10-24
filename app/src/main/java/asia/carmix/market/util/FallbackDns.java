package asia.carmix.market.util;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import okhttp3.Dns;

/**
 * DNS 回落：车机休眠唤醒 / 弱网下系统解析器经常抽风（返回 UnknownHostException），
 * 失败时直接向公共 DNS（223.5.5.5 阿里 / 119.29.29.29 腾讯）发 UDP A 查询兜底。
 * 仅做解析，证书校验/SNI 仍由 OkHttp 按主机名进行，不降低安全性。
 */
public class FallbackDns implements Dns {

    private static final String[] FALLBACK_SERVERS = {"223.5.5.5", "119.29.29.29"};
    private static final int TIMEOUT_MS = 3000;

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        try {
            return Dns.SYSTEM.lookup(hostname);
        } catch (UnknownHostException firstError) {
            for (String server : FALLBACK_SERVERS) {
                List<InetAddress> r = queryA(server, hostname);
                if (!r.isEmpty()) return r;
            }
            throw firstError;
        }
    }

    /** 极简 UDP A 记录查询，任何异常都吞掉返回空列表 */
    private static List<InetAddress> queryA(String server, String host) {
        List<InetAddress> out = new ArrayList<InetAddress>();
        DatagramSocket socket = null;
        try {
            if (!isAscii(host)) return out;
            byte[] q = buildQuery(host);
            socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_MS);
            socket.send(new DatagramPacket(q, q.length, InetAddress.getByName(server), 53));
            byte[] buf = new byte[1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);
            parseAnswers(buf, resp.getLength(), out);
        } catch (Throwable ignored) {
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) return false;
        }
        return true;
    }

    /** 标准查询报文：header(12B) + qname + qtype(A) + qclass(IN) */
    private static byte[] buildQuery(String host) {
        Random rnd = new Random();
        int len = 12 + host.length() + 2 + 4 + 1;
        byte[] p = new byte[len];
        int off = 0;
        p[off++] = (byte) (rnd.nextInt(255) & 0xFF); // id
        p[off++] = (byte) (rnd.nextInt(255) & 0xFF);
        p[off++] = 0x01; // flags: recursion desired
        p[off++] = 0x00;
        p[off++] = 0x00; p[off++] = 0x01; // qdcount=1
        p[off++] = 0x00; p[off++] = 0x00;
        p[off++] = 0x00; p[off++] = 0x00;
        p[off++] = 0x00; p[off++] = 0x00;
        for (String label : host.split("\\.")) {
            byte[] lb;
            try {
                lb = label.getBytes("US-ASCII");
            } catch (java.io.UnsupportedEncodingException e) {
                return new byte[0];
            }
            p[off++] = (byte) lb.length;
            System.arraycopy(lb, 0, p, off, lb.length);
            off += lb.length;
        }
        p[off++] = 0; // root
        p[off++] = 0x00; p[off++] = 0x01; // A
        p[off++] = 0x00; p[off++] = 0x01; // IN
        return p;
    }

    /** 解析 answer 段中的 A 记录（容忍压缩指针与 CNAME） */
    private static void parseAnswers(byte[] p, int n, List<InetAddress> out) {
        if (n < 12) return;
        int qd = ((p[4] & 0xFF) << 8) | (p[5] & 0xFF);
        int an = ((p[6] & 0xFF) << 8) | (p[7] & 0xFF);
        int off = 12;
        for (int i = 0; i < qd && off < n; i++) {
            off = skipName(p, off, n);
            off += 4; // qtype + qclass
        }
        for (int i = 0; i < an && off < n; i++) {
            off = skipName(p, off, n);
            if (off + 10 > n) return;
            int type = ((p[off] & 0xFF) << 8) | (p[off + 1] & 0xFF);
            int rdlen = ((p[off + 8] & 0xFF) << 8) | (p[off + 9] & 0xFF);
            off += 10;
            if (off + rdlen > n) return;
            if (type == 1 && rdlen == 4) { // A record
                try {
                    out.add(InetAddress.getByAddress(
                            new byte[]{p[off], p[off + 1], p[off + 2], p[off + 3]}));
                } catch (UnknownHostException ignored) {
                }
            }
            off += rdlen;
        }
    }

    /** 跳过（可能是压缩指针的）域名，返回下一字段偏移 */
    private static int skipName(byte[] p, int off, int n) {
        while (off < n) {
            int len = p[off] & 0xFF;
            if (len == 0) return off + 1;
            if ((len & 0xC0) == 0xC0) return off + 2; // 指针
            off += 1 + len;
        }
        return off;
    }
}
