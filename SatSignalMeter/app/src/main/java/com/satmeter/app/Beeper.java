package com.satmeter.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Satfinder-style beeper: the better the signal (0-100), the higher and faster the beeps.
 * At 90 and above it becomes one steady tone.
 */
public class Beeper {
    private static final int RATE = 22050;

    private volatile boolean running;
    private volatile boolean enabled = true;
    private volatile int level;
    private Thread thread;

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "beeper");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        level = 0;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public void setLevel(int l) {
        level = l;
    }

    public void setEnabled(boolean e) {
        enabled = e;
    }

    private void loop() {
        AudioTrack track = null;
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
            double phase = 0;
            while (running) {
                int l = level;
                if (!enabled || l <= 0) {
                    silence(track, 60);
                    continue;
                }
                double freq = 500 + l * 12.0;
                int toneMs = 80;
                int gapMs = l >= 90 ? 0 : (int) (600 - l * 6);
                int n = RATE * toneMs / 1000;
                short[] buf = new short[n];
                double step = 2 * Math.PI * freq / RATE;
                for (int i = 0; i < n; i++) {
                    double env = gapMs == 0 ? 1.0 : Math.min(1.0, Math.min(i, n - 1 - i) / 120.0);
                    buf[i] = (short) (Math.sin(phase) * 12000 * env);
                    phase += step;
                }
                phase = phase % (2 * Math.PI);
                track.write(buf, 0, n);
                if (gapMs > 0) silence(track, gapMs);
            }
        } catch (Throwable ignored) {
            // No audio available: the meter keeps working silently.
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (Throwable ignored) {
                }
                track.release();
            }
        }
    }

    private static void silence(AudioTrack track, int ms) {
        short[] z = new short[RATE * ms / 1000];
        track.write(z, 0, z.length);
    }
}
