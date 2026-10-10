package com.satmeter.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the receiver with a request you configure (TCP, UDP or HTTP) and pulls the
 * signal numbers out of the reply with regular expressions (first capture group).
 * Fill the request and patterns in once you know what the G-MScreen app sends.
 */
public class CustomClient implements ReceiverClient {
    private final String mode;
    private final String host;
    private final String request;
    private final int port;
    private final int max;
    private final Pattern pStrength;
    private final Pattern pQuality;
    private final Pattern pLock;
    private final Pattern pSat;
    private final Pattern pTp;

    public CustomClient(String mode, String host, int port, String request,
                        String reStrength, String reQuality, String reLock,
                        String reSat, String reTp, int max) {
        this.mode = mode;
        this.host = host;
        this.port = port;
        this.max = max;
        this.request = request.replace("\\r", "\r").replace("\\n", "\n");
        this.pStrength = compile(reStrength);
        this.pQuality = compile(reQuality);
        this.pLock = compile(reLock);
        this.pSat = compile(reSat);
        this.pTp = compile(reTp);
    }

    @Override
    public Reading read() throws Exception {
        String body = fetch();
        Reading r = new Reading();
        r.strength = scale(num(pStrength, body));
        r.quality = scale(num(pQuality, body));
        r.locked = pLock != null ? pLock.matcher(body).find() : r.quality > 0;
        r.satellite = text(pSat, body);
        r.tp = text(pTp, body);
        return r;
    }

    private String fetch() throws Exception {
        switch (mode) {
            case "HTTP":
                return fetchHttp();
            case "UDP":
                return fetchUdp();
            default:
                return fetchTcp();
        }
    }

    private String fetchHttp() throws Exception {
        String path = request.trim();
        if (path.isEmpty()) path = "/";
        if (!path.startsWith("/")) path = "/" + path;
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + host + ":" + port + path).openConnection();
        c.setConnectTimeout(1500);
        c.setReadTimeout(1500);
        try (InputStream in = c.getInputStream()) {
            return readAll(in, 8192);
        } finally {
            c.disconnect();
        }
    }

    private String fetchTcp() throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 1500);
            s.setSoTimeout(700);
            if (!request.isEmpty()) {
                OutputStream o = s.getOutputStream();
                o.write(request.getBytes(StandardCharsets.ISO_8859_1));
                o.flush();
            }
            return readAll(s.getInputStream(), 4096);
        }
    }

    private String fetchUdp() throws Exception {
        try (DatagramSocket ds = new DatagramSocket()) {
            ds.setSoTimeout(1500);
            byte[] data = request.getBytes(StandardCharsets.ISO_8859_1);
            ds.send(new DatagramPacket(data, data.length, InetAddress.getByName(host), port));
            byte[] buf = new byte[4096];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            ds.receive(p);
            return new String(buf, 0, p.getLength(), StandardCharsets.ISO_8859_1);
        }
    }

    private static String readAll(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        try {
            int n;
            while (out.size() < limit && (n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        } catch (SocketTimeoutException e) {
            if (out.size() == 0) throw e;
        }
        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private static Pattern compile(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return Pattern.compile(s.trim(), Pattern.DOTALL);
    }

    private int scale(double v) {
        int m = max <= 0 ? 100 : max;
        int s = (int) Math.round(v * 100.0 / m);
        return Math.max(0, Math.min(100, s));
    }

    private static double num(Pattern p, String body) {
        if (p == null) return 0;
        Matcher m = p.matcher(body);
        if (!m.find() || m.groupCount() < 1) return 0;
        try {
            return Double.parseDouble(m.group(1).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String text(Pattern p, String body) {
        if (p == null) return "";
        Matcher m = p.matcher(body);
        if (!m.find()) return "";
        String g = m.groupCount() >= 1 ? m.group(1) : m.group();
        return g == null ? "" : g.trim();
    }
}
