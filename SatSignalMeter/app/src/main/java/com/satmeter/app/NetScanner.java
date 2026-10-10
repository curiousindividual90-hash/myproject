package com.satmeter.app;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds devices on your Wi-Fi (same /24 network as the phone): checks a list of common
 * ports on every address and also listens for SSDP/UPnP replies.
 */
public class NetScanner {

    public static class Device {
        public String ip;
        public final ConcurrentSkipListSet<Integer> ports = new ConcurrentSkipListSet<>();
        public volatile String info = "";
    }

    public interface Listener {
        void onUpdate(List<Device> devices, int done, int total);

        void onFinished(List<Device> devices, String note);
    }

    private static final int[] PORTS = {
            80, 8080, 8000, 8001, 8081, 8090, 8888, 9000, 9999, 7000,
            5000, 5555, 6000, 554, 23, 22, 443, 20000, 1935
    };

    private final Map<String, Device> found = new ConcurrentHashMap<>();
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public void start(final Listener l) {
        new Thread(() -> run(l), "net-scan").start();
    }

    private void run(final Listener l) {
        String[] local = localAddress();
        if (local == null) {
            l.onFinished(snapshot(), "You are not connected to Wi-Fi.");
            return;
        }
        final String prefix = local[0];
        final String me = local[1];

        Thread ssdp = new Thread(this::ssdpScan, "ssdp-scan");
        ssdp.start();

        ExecutorService pool = Executors.newFixedThreadPool(48);
        final AtomicInteger done = new AtomicInteger();
        final int total = 254;
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + i;
            if (ip.equals(me)) {
                done.incrementAndGet();
                continue;
            }
            pool.execute(() -> {
                for (int port : PORTS) {
                    if (cancelled) break;
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(ip, port), 250);
                        add(ip).ports.add(port);
                    } catch (Exception ignored) {
                        // closed or unreachable
                    }
                }
                int d = done.incrementAndGet();
                if (d % 8 == 0) l.onUpdate(snapshot(), d, total);
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(180, TimeUnit.SECONDS);
            ssdp.join(6000);
        } catch (InterruptedException ignored) {
        }
        l.onFinished(snapshot(), cancelled ? "Stopped." : "Scan finished.");
    }

    private Device add(String ip) {
        return found.computeIfAbsent(ip, k -> {
            Device d = new Device();
            d.ip = k;
            return d;
        });
    }

    private List<Device> snapshot() {
        List<Device> list = new ArrayList<>(found.values());
        Collections.sort(list, (a, b) -> lastOctet(a.ip) - lastOctet(b.ip));
        return list;
    }

    private static int lastOctet(String ip) {
        try {
            return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1));
        } catch (Exception e) {
            return 0;
        }
    }

    private void ssdpScan() {
        try (DatagramSocket ds = new DatagramSocket()) {
            ds.setSoTimeout(600);
            String msg = "M-SEARCH * HTTP/1.1\r\n"
                    + "HOST: 239.255.255.250:1900\r\n"
                    + "MAN: \"ssdp:discover\"\r\n"
                    + "MX: 2\r\n"
                    + "ST: ssdp:all\r\n\r\n";
            byte[] data = msg.getBytes();
            ds.send(new DatagramPacket(data, data.length, InetAddress.getByName("239.255.255.250"), 1900));
            long end = System.currentTimeMillis() + 4000;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < end && !cancelled) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    ds.receive(p);
                    if (!(p.getAddress() instanceof Inet4Address)) continue;
                    String text = new String(buf, 0, p.getLength());
                    String server = header(text, "SERVER");
                    Device d = add(p.getAddress().getHostAddress());
                    if (!server.isEmpty() && !d.info.contains(server)) {
                        d.info = (d.info + " " + server).trim();
                    }
                } catch (SocketTimeoutException ignored) {
                    // keep listening until the time is up
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String header(String text, String name) {
        String key = name.toLowerCase() + ":";
        for (String line : text.split("\r\n")) {
            if (line.toLowerCase().startsWith(key)) {
                return line.substring(key.length()).trim();
            }
        }
        return "";
    }

    /** Returns {"192.168.1.", "192.168.1.23"} for the Wi-Fi interface, or null. */
    private static String[] localAddress() {
        for (int pass = 0; pass < 2; pass++) {
            try {
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    if (pass == 0 && !ni.getName().startsWith("wlan")) continue;
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                        if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                            String h = a.getHostAddress();
                            return new String[]{h.substring(0, h.lastIndexOf('.') + 1), h};
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
