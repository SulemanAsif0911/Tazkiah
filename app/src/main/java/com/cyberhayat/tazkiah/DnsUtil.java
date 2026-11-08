package com.cyberhayat.tazkiah;

import java.util.Arrays;
import java.util.Locale;

/** Tiny DNS packet helpers (question parsing, NXDOMAIN reply, sinkhole detection). */
final class DnsUtil {
    private DnsUtil() {}

    static int u16(byte[] b, int o) { return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF); }

    /** Offset right after the first question, or -1 if it cannot be parsed. */
    static int questionEnd(byte[] q) {
        if (q.length < 17 || u16(q, 4) != 1) return -1;
        int p = 12;
        while (true) {
            if (p >= q.length) return -1;
            int l = q[p] & 0xFF;
            if (l == 0) { p++; break; }
            if ((l & 0xC0) != 0) return -1;
            p += l + 1;
        }
        return p + 4 <= q.length ? p + 4 : -1;
    }

    static String qname(byte[] q) {
        if (questionEnd(q) < 0) return null;
        StringBuilder sb = new StringBuilder();
        int p = 12;
        while (true) {
            int l = q[p] & 0xFF;
            if (l == 0) break;
            if (sb.length() > 0) sb.append('.');
            for (int i = 1; i <= l; i++) sb.append((char) (q[p + i] & 0xFF));
            p += l + 1;
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    static byte[] nxdomain(byte[] q) {
        int end = questionEnd(q);
        if (end < 0) return null;
        byte[] r = Arrays.copyOf(q, end);
        r[2] = (byte) (0x80 | (q[2] & 0x79)); // QR=1, keep opcode + RD
        r[3] = (byte) 0x83;                   // RA=1, RCODE=3 (NXDOMAIN)
        r[4] = 0; r[5] = 1;                   // QDCOUNT=1
        for (int i = 6; i < 12; i++) r[i] = 0;
        return r;
    }

    private static int skipName(byte[] r, int p) {
        while (true) {
            if (p >= r.length) return -1;
            int l = r[p] & 0xFF;
            if (l == 0) return p + 1;
            if ((l & 0xC0) == 0xC0) return p + 2;
            p += l + 1;
        }
    }

    /** True when the resolver answered 0.0.0.0 / :: (how Cloudflare Family signals a blocked site). */
    static boolean isSinkholed(byte[] r) {
        if (r == null || r.length < 12) return false;
        int qd = u16(r, 4), an = u16(r, 6), p = 12;
        for (int i = 0; i < qd; i++) { p = skipName(r, p); if (p < 0) return false; p += 4; }
        for (int i = 0; i < an; i++) {
            p = skipName(r, p);
            if (p < 0 || p + 10 > r.length) return false;
            int type = u16(r, p), rdlen = u16(r, p + 8);
            p += 10;
            if (p + rdlen > r.length) return false;
            if ((type == 1 && rdlen == 4) || (type == 28 && rdlen == 16)) {
                boolean zero = true;
                for (int k = 0; k < rdlen; k++) if (r[p + k] != 0) { zero = false; break; }
                if (zero) return true;
            }
            p += rdlen;
        }
        return false;
    }

    static int checksum(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = 0; i + 1 < len; i += 2) sum += ((b[off + i] & 0xFF) << 8) | (b[off + i + 1] & 0xFF);
        if ((len & 1) == 1) sum += (b[off + len - 1] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }
}
