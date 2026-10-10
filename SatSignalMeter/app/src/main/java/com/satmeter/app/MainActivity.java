package com.satmeter.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BG = 0xFF101418;
    private static final int CARD = 0xFF1A2027;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA5B1;
    private static final int GOOD = 0xFF3DDC84;
    private static final int BAD = 0xFFFF5252;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    /** key, label, default */
    private static final String[][] FIELDS = {
            {"host", "Receiver IP (tap a device in Finder to fill this)", ""},
            {"port", "Port", "80"},
            {"request", "Request: text to send (TCP/UDP, \\r\\n allowed) or the HTTP path", ""},
            {"reStrength", "Strength regex (one group, e.g. strength=(\\d+))", ""},
            {"reQuality", "Quality regex (one group, e.g. quality=(\\d+))", ""},
            {"reLock", "Lock regex (optional, matches when locked)", ""},
            {"reSat", "Satellite name regex (optional)", ""},
            {"reTp", "TP regex (optional)", ""},
            {"max", "Highest value the receiver reports (100, 255 ...)", "100"},
    };

    private static final String HINT =
            "Demo mode works with no receiver. For your ST-20 Plus, pick TCP, UDP or HTTP and "
                    + "fill the request and regex fields from what the G-MScreen app sends.";

    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Beeper beeper = new Beeper();

    private View meterPanel;
    private View finderPanel;
    private View setupPanel;
    private BarView strengthBar;
    private BarView qualityBar;
    private TextView satText;
    private TextView tpText;
    private TextView lockText;
    private TextView statusText;
    private TextView scanStatus;
    private Button startBtn;
    private Button scanBtn;
    private CheckBox beepBox;
    private LinearLayout deviceList;
    private Spinner modeSpinner;
    private final Map<String, EditText> fields = new LinkedHashMap<>();

    private volatile boolean polling;
    private Thread pollThread;
    private NetScanner scanner;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("satmeter", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(28), dp(16), dp(8));

        root.addView(text("Sat Signal Meter", 22, TEXT, true));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button tMeter = tabButton("Meter");
        Button tFinder = tabButton("Finder");
        Button tSetup = tabButton("Setup");
        tabs.addView(tMeter, new LinearLayout.LayoutParams(0, WRAP, 1f));
        tabs.addView(tFinder, new LinearLayout.LayoutParams(0, WRAP, 1f));
        tabs.addView(tSetup, new LinearLayout.LayoutParams(0, WRAP, 1f));
        root.addView(tabs, new LinearLayout.LayoutParams(MATCH, WRAP));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        meterPanel = buildMeter();
        finderPanel = buildFinder();
        setupPanel = buildSetup();
        content.addView(meterPanel);
        content.addView(finderPanel);
        content.addView(setupPanel);

        tMeter.setOnClickListener(v -> show(meterPanel));
        tFinder.setOnClickListener(v -> show(finderPanel));
        tSetup.setOnClickListener(v -> show(setupPanel));
        show(meterPanel);

        setContentView(root);
    }

    // ---------------------------------------------------------------- Meter tab

    private View buildMeter() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(16), 0, dp(16));

        satText = text("Satellite: —", 18, TEXT, true);
        tpText = text("TP: —", 18, TEXT, false);
        strengthBar = new BarView(this, "Signal strength");
        qualityBar = new BarView(this, "Signal quality");
        lockText = text("NOT RUNNING", 28, MUTED, true);
        lockText.setGravity(Gravity.CENTER);
        statusText = text("Choose Demo in Setup to try it, or set up your receiver.", 14, MUTED, false);
        statusText.setGravity(Gravity.CENTER);

        beepBox = new CheckBox(this);
        beepBox.setText("Beep (higher and faster = better signal)");
        beepBox.setTextColor(TEXT);
        beepBox.setChecked(true);
        beepBox.setOnCheckedChangeListener((cb, on) -> beeper.setEnabled(on));

        startBtn = new Button(this);
        startBtn.setText("Start");
        startBtn.setOnClickListener(v -> {
            if (polling) {
                stopPolling();
            } else {
                startPolling();
            }
        });

        col.addView(satText);
        col.addView(tpText);
        col.addView(spacer(16));
        col.addView(strengthBar, new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(qualityBar, new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(spacer(8));
        col.addView(lockText, new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(statusText, new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(spacer(12));
        col.addView(beepBox);
        col.addView(startBtn, new LinearLayout.LayoutParams(MATCH, WRAP));
        sv.addView(col);
        return sv;
    }

    private void startPolling() {
        if (polling) return;
        saveSettings();
        ReceiverClient c;
        try {
            c = createClient();
        } catch (Exception e) {
            statusText.setText("Setup problem: " + e.getMessage());
            return;
        }
        final ReceiverClient client = c;
        polling = true;
        startBtn.setText("Stop");
        statusText.setText("Connecting…");
        beeper.setEnabled(beepBox.isChecked());
        beeper.start();
        pollThread = new Thread(() -> {
            while (polling) {
                Reading r;
                String err = null;
                try {
                    r = client.read();
                } catch (Exception e) {
                    r = null;
                    err = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                }
                final Reading fr = r;
                final String fe = err;
                ui.post(() -> showReading(fr, fe));
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }, "poll");
        pollThread.start();
    }

    private void stopPolling() {
        polling = false;
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
        beeper.stop();
        if (startBtn != null) {
            startBtn.setText("Start");
            lockText.setText("NOT RUNNING");
            lockText.setTextColor(MUTED);
            statusText.setText("Stopped.");
        }
    }

    private void showReading(Reading r, String err) {
        if (!polling) return;
        if (r == null) {
            strengthBar.setValue(0);
            qualityBar.setValue(0);
            lockText.setText("NO DATA");
            lockText.setTextColor(BAD);
            statusText.setText("Can't read the receiver: " + err);
            beeper.setLevel(0);
            return;
        }
        strengthBar.setValue(r.strength);
        qualityBar.setValue(r.quality);
        lockText.setText(r.locked ? "LOCKED" : "NO LOCK");
        lockText.setTextColor(r.locked ? GOOD : BAD);
        satText.setText("Satellite: " + (r.satellite.isEmpty() ? "—" : r.satellite));
        tpText.setText("TP: " + (r.tp.isEmpty() ? "—" : r.tp));
        statusText.setText("Live");
        beeper.setLevel(r.locked ? Math.max(r.quality, 90) : r.quality);
    }

    private ReceiverClient createClient() {
        String mode = prefs.getString("mode", "Demo");
        if ("Demo".equals(mode)) return new DemoClient();
        String host = prefs.getString("host", "").trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("enter the receiver IP in Setup, or tap it in Finder");
        }
        int port = Integer.parseInt(prefs.getString("port", "80").trim());
        int max = Integer.parseInt(prefs.getString("max", "100").trim());
        return new CustomClient(mode, host, port,
                prefs.getString("request", ""),
                prefs.getString("reStrength", ""),
                prefs.getString("reQuality", ""),
                prefs.getString("reLock", ""),
                prefs.getString("reSat", ""),
                prefs.getString("reTp", ""),
                max);
    }

    // --------------------------------------------------------------- Finder tab

    private View buildFinder() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(12), 0, 0);

        col.addView(text("Make sure your phone and the receiver are on the same Wi-Fi, "
                + "then scan. Tap a device to use it as your receiver.", 14, MUTED, false));

        scanBtn = new Button(this);
        scanBtn.setText("Scan network");
        scanBtn.setOnClickListener(v -> toggleScan());
        col.addView(scanBtn, new LinearLayout.LayoutParams(MATCH, WRAP));

        scanStatus = text("", 14, MUTED, false);
        col.addView(scanStatus);

        ScrollView sv = new ScrollView(this);
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        sv.addView(deviceList, new ViewGroup.LayoutParams(MATCH, WRAP));
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return col;
    }

    private void toggleScan() {
        if (scanner != null) {
            scanner.cancel();
            scanner = null;
            scanBtn.setText("Scan network");
            return;
        }
        deviceList.removeAllViews();
        scanStatus.setText("Scanning your Wi-Fi… (about 30 seconds)");
        scanBtn.setText("Stop");
        final NetScanner s = new NetScanner();
        scanner = s;
        s.start(new NetScanner.Listener() {
            @Override
            public void onUpdate(final List<NetScanner.Device> devices, final int done, final int total) {
                ui.post(() -> {
                    if (scanner != s) return;
                    showDevices(devices);
                    scanStatus.setText("Scanning… " + done + "/" + total);
                });
            }

            @Override
            public void onFinished(final List<NetScanner.Device> devices, final String note) {
                ui.post(() -> {
                    if (scanner != null && scanner != s) return;
                    showDevices(devices);
                    scanStatus.setText(note + " Found " + devices.size() + " device(s). Tap one to use it.");
                    scanner = null;
                    scanBtn.setText("Scan network");
                });
            }
        });
    }

    private void showDevices(List<NetScanner.Device> devices) {
        deviceList.removeAllViews();
        for (final NetScanner.Device d : devices) {
            StringBuilder sb = new StringBuilder(d.ip);
            if (!d.ports.isEmpty()) {
                sb.append("\nOpen ports: ");
                boolean first = true;
                for (int p : d.ports) {
                    if (!first) sb.append(", ");
                    sb.append(p);
                    first = false;
                }
            }
            if (!d.info.isEmpty()) sb.append("\n").append(d.info);

            TextView row = text(sb.toString(), 15, TEXT, false);
            row.setBackgroundColor(CARD);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.setMargins(0, 0, 0, dp(8));
            row.setOnClickListener(v -> {
                EditText host = fields.get("host");
                if (host != null) host.setText(d.ip);
                prefs.edit().putString("host", d.ip).apply();
                Toast.makeText(this, "Receiver set to " + d.ip, Toast.LENGTH_SHORT).show();
            });
            deviceList.addView(row, lp);
        }
    }

    // ---------------------------------------------------------------- Setup tab

    private View buildSetup() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(12), 0, dp(24));

        col.addView(text("Connection type", 14, MUTED, false));
        modeSpinner = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Demo", "TCP", "UDP", "HTTP"});
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(ad);
        String mode = prefs.getString("mode", "Demo");
        for (int i = 0; i < ad.getCount(); i++) {
            if (mode.equals(ad.getItem(i))) modeSpinner.setSelection(i);
        }
        col.addView(modeSpinner, new LinearLayout.LayoutParams(MATCH, WRAP));

        for (String[] f : FIELDS) {
            col.addView(text(f[1], 14, MUTED, false));
            EditText e = new EditText(this);
            e.setSingleLine(true);
            e.setTextColor(TEXT);
            e.setText(prefs.getString(f[0], f[2]));
            fields.put(f[0], e);
            col.addView(e, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        col.addView(spacer(8));
        col.addView(text(HINT, 13, MUTED, false));

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        });
        col.addView(save, new LinearLayout.LayoutParams(MATCH, WRAP));
        sv.addView(col);
        return sv;
    }

    private void saveSettings() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString("mode", (String) modeSpinner.getSelectedItem());
        for (Map.Entry<String, EditText> en : fields.entrySet()) {
            ed.putString(en.getKey(), en.getValue().getText().toString());
        }
        ed.apply();
    }

    // ------------------------------------------------------------------ helpers

    private void show(View v) {
        meterPanel.setVisibility(v == meterPanel ? View.VISIBLE : View.GONE);
        finderPanel.setVisibility(v == finderPanel ? View.VISIBLE : View.GONE);
        setupPanel.setVisibility(v == setupPanel ? View.VISIBLE : View.GONE);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button tabButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        return b;
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(heightDp)));
        return v;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
        if (scanner != null) {
            scanner.cancel();
            scanner = null;
            if (scanBtn != null) scanBtn.setText("Scan network");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}
