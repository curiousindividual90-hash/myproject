package com.satmeter.app;

import java.util.Random;

/** Fake receiver so you can try the meter and the beep without any hardware. */
public class DemoClient implements ReceiverClient {
    private final long start = System.currentTimeMillis();
    private final Random rnd = new Random();

    @Override
    public Reading read() {
        double t = (System.currentTimeMillis() - start) / 1000.0;
        double base = 50 + 40 * Math.sin(t / 4.0);
        Reading r = new Reading();
        r.strength = clamp((int) (base + 8 + rnd.nextInt(7) - 3));
        r.quality = clamp((int) (base - 10 + rnd.nextInt(9) - 4));
        r.locked = r.quality >= 45;
        r.satellite = "Demo Sat 68.5E";
        r.tp = "11555 H 27500";
        return r;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
