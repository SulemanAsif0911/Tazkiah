package com.cyberhayat.tazkiah;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DNS Shield: a local "VPN" that only captures DNS lookups (route = one fake DNS address).
 * Blocked names get NXDOMAIN, everything else is forwarded to Cloudflare Family (1.1.1.3), which also
 * blocks adult sites. No traffic other than DNS ever enters the tunnel and nothing is logged.
 */
public class DnsShieldService extends VpnService {
    static final String ACTION_START = "com.cyberhayat.tazkiah.START_SHIELD";
    static final String ACTION_STOP = "com.cyberhayat.tazkiah.STOP_SHIELD";
    static volatile boolean running = false;

    private static final String VPN_ADDR = "10.111.222.1";
    private static final byte[] DNS_IP = {10, 111, (byte) 222, 2};
    private static final String DNS_ADDR = "10.111.222.2";
    private static final String[] UPSTREAM = {"1.1.1.3", "1.0.0.3", "8.8.8.8"};

    private ParcelFileDescriptor tun;
    private Thread loop;
    private volatile boolean stop;
    private ExecutorService pool;
    private FileOutputStream out;
    private final Object outLock = new Object();
    private int ipId = 1;
    private final Map<String, Long> recent = new HashMap<>();
    private volatile int bestUpstream = 0;

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String a = i == null ? null : i.getAction();
        if (ACTION_STOP.equals(a)) {
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        start();
        return START_STICKY;
    }

    private synchronized void start() {
        if (running) return;
        try {
            Builder b = new Builder();
            b.setSession("Cyber Tazkiah DNS Shield");
            b.addAddress(VPN_ADDR, 24);
            b.addDnsServer(DNS_ADDR);
            b.addRoute(DNS_ADDR, 32);
            b.setMtu(1400);
            b.setBlocking(true);
            try { b.addDisallowedApplication(getPackageName()); } catch (Exception ignored) { }
            tun = b.establish();
            if (tun == null) { Prefs.setDnsWanted(this, false); return; }
            stop = false;
            pool = Executors.newFixedThreadPool(12);
            out = new FileOutputStream(tun.getFileDescriptor());
            final ParcelFileDescriptor pfd = tun;
            loop = new Thread(new Runnable() { @Override public void run() { readLoop(pfd); } }, "tazkiah-dns");
            loop.start();
            running = true;
            Blocklist.init(this);
        } catch (Exception e) {
            running = false;
            Prefs.setDnsWanted(this, false);
        }
    }

    private void readLoop(ParcelFileDescriptor pfd) {
        FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
        byte[] buf = new byte[32767];
        while (!stop) {
            int n;
            try { n = in.read(buf); } catch (IOException e) { break; }
            if (n < 0) break;
            if (n > 0) handlePacket(buf, n);
        }
    }

    private void handlePacket(byte[] buf, int n) {
        if (n < 28 || ((buf[0] >> 4) & 0xF) != 4) return;
        int ihl = (buf[0] & 0xF) * 4;
        if (ihl < 20 || n < ihl + 8 || (buf[9] & 0xFF) != 17) return;
        for (int i = 0; i < 4; i++) if (buf[16 + i] != DNS_IP[i]) return;
        if (DnsUtil.u16(buf, ihl + 2) != 53) return;
        final int srcPort = DnsUtil.u16(buf, ihl);
        int udpLen = DnsUtil.u16(buf, ihl + 4);
        int dnsLen = Math.min(udpLen, n - ihl) - 8;
        if (dnsLen < 12) return;
        final byte[] dns = Arrays.copyOfRange(buf, ihl + 8, ihl + 8 + dnsLen);
        final byte[] srcIp = Arrays.copyOfRange(buf, 12, 16);
        try {
            pool.execute(new Runnable() { @Override public void run() { answer(dns, srcIp, srcPort); } });
        } catch (Exception ignored) { }
    }

    private void answer(byte[] dns, byte[] srcIp, int srcPort) {
        String name = DnsUtil.qname(dns);
        byte[] resp;
        boolean blocked = false;
        if (name != null && Prefs.protection(this) && Blocklist.hostBlocked(name)) {
            resp = DnsUtil.nxdomain(dns);
            blocked = resp != null;
        } else {
            resp = forward(dns); // resolver-side blocks (ads, trackers, adult) pass through silently
        }
        if (resp == null) return;
        writeUdp(resp, srcIp, srcPort);
        if (blocked && name != null && firstRecently(name)) Prefs.recordBlock(this, "dns"); // count only: no pop-up from DNS
    }

    /** Browsers ask A, AAAA and HTTPS for one page load: count a domain once per 20 seconds. */
    private boolean firstRecently(String name) {
        long now = System.currentTimeMillis();
        synchronized (recent) {
            if (recent.size() > 64) recent.clear();
            Long t = recent.get(name);
            if (t != null && now - t < 20000) return false;
            recent.put(name, now);
            return true;
        }
    }

    private byte[] forward(byte[] dns) {
        int n = UPSTREAM.length;
        for (int k = 0; k < n; k++) {
            int idx = (bestUpstream + k) % n;   // start with the resolver that worked last time
            DatagramSocket s = null;
            try {
                s = new DatagramSocket();
                protect(s);
                s.setSoTimeout(k == 0 ? 1500 : 1200);
                s.send(new DatagramPacket(dns, dns.length, InetAddress.getByName(UPSTREAM[idx]), 53));
                byte[] rb = new byte[4096];
                DatagramPacket rp = new DatagramPacket(rb, rb.length);
                s.receive(rp);
                bestUpstream = idx;
                return Arrays.copyOf(rb, rp.getLength());
            } catch (IOException ignored) {
                // try the next resolver
            } finally {
                if (s != null) s.close();
            }
        }
        return null;
    }

    private void writeUdp(byte[] payload, byte[] dstIp, int dstPort) {
        int total = 28 + payload.length;
        byte[] p = new byte[total];
        p[0] = 0x45;
        p[2] = (byte) (total >> 8); p[3] = (byte) total;
        int id; synchronized (outLock) { id = ipId++; }
        p[4] = (byte) (id >> 8); p[5] = (byte) id;
        p[8] = 64; p[9] = 17;
        System.arraycopy(DNS_IP, 0, p, 12, 4);
        System.arraycopy(dstIp, 0, p, 16, 4);
        int ck = DnsUtil.checksum(p, 0, 20);
        p[10] = (byte) (ck >> 8); p[11] = (byte) ck;
        p[20] = 0; p[21] = 53;
        p[22] = (byte) (dstPort >> 8); p[23] = (byte) dstPort;
        int ul = 8 + payload.length;
        p[24] = (byte) (ul >> 8); p[25] = (byte) ul;
        System.arraycopy(payload, 0, p, 28, payload.length);
        try {
            synchronized (outLock) { if (out != null) out.write(p); }
        } catch (IOException ignored) { }
    }

    private synchronized void shutdown() {
        stop = true;
        running = false;
        try { if (pool != null) pool.shutdownNow(); } catch (Exception ignored) { }
        try { if (tun != null) tun.close(); } catch (Exception ignored) { }
        try { if (loop != null) loop.interrupt(); } catch (Exception ignored) { }
        tun = null; out = null; loop = null; pool = null;
    }

    @Override public void onRevoke() {
        // user switched the VPN off (or another VPN took over)
        Prefs.setDnsWanted(this, false);
        shutdown();
        stopSelf();
    }

    @Override public void onDestroy() {
        shutdown();
        super.onDestroy();
    }
}
