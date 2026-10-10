# Sat Signal Meter

Android app with two features for a Wi-Fi satellite receiver (Starbox ST-20 Plus):

- **Finder**: scans your Wi-Fi and lists every device with its IP, open ports and UPnP name. Tap the receiver to select it.
- **Meter**: signal strength and quality bars, LOCK status, satellite and TP, and a satfinder beep (higher and faster = better signal).

Setup has a **Demo** mode (fake signal, no receiver needed) and **TCP / UDP / HTTP** modes. For those, enter the request the receiver expects and a regex for each value (first capture group is used). Fill these in from what the official G-MScreen app sends (a PCAPdroid capture shows it).

## Build the APK

### Option A: GitHub (no PC software needed)
1. Create a free GitHub account and a new repository.
2. Upload every file and folder of this project (keep the `.github` folder).
3. Open the **Actions** tab, then the **Build APK** run, wait about 3 minutes.
4. Open the finished run and download **SatSignalMeter-apk**, then unzip it to get `app-debug.apk`.
5. Install it on your phone (allow "install unknown apps" for your browser or files app).

### Option B: Android Studio
Open this folder in Android Studio, then Build > Build APK(s). The file is at `app/build/outputs/apk/debug/app-debug.apk`.
