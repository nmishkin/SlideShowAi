# SlideShowAi

**SlideShowAi** is a smart digital photo frame application for Android. It pairs with a Python companion script to manage, sync, and optimize your photo library directly from your computer.

## Features

### Android App
-   **Smart Orientation Filtering**: Automatically detects device orientation (Landscape/Portrait) and filters the slideshow to show only matching photos.
-   **Smart Shuffle**: Prioritizes photos that haven't been shown recently.
-   **EXIF Data Display**: Overlays the date taken and location (reverse geocoded) on the photo.
-   **Local Storage**: Photos are stored locally on the device for offline playback.
-   **Direct Push Sync**: Receives photos directly from your computer over a local TCP connection.
-   **Quiet Hours**: Configurable start/end times to dim or pause the slideshow (e.g., at night).
-   **Photo Details**: Swipe up to see detailed metadata about the current photo.

### Companion Script (`manage_app.py`)
-   **Smart Sync**: deep integration with the app to fetch device screen resolution and resize/crop photos to **pixel-perfect** dimensions (Aspect Fill + Center Crop) before transfer. This maximizes quality and minimizes storage usage.
-   **Orientation Reporting**: Scans local directories to report the ratio of Landscape vs. Portrait photos.
-   **Database Management**: View and clear the internal Android databases (`location` cache and `history` logs) remotely.
-   **Remote Management**: Delete individual files or wipe the entire library remotely.

## Getting Started

### 1. Android App
1.  Open the project in **Android Studio**.
2.  Build and Run on your target Android device.
3.  Grant necessary permissions (Storage, etc.) if prompted.
4.  The app will start a TCP server on port `4000` waiting for commands.

### 2. Python Environment
The companion script located in `scripts/manage_app.py` requires Python 3 and a few dependencies.

**Install Dependencies:**
```bash
pip install Pillow pillow-heif piexif
```

## Usage

Use the `manage_app.py` script to interact with the running Android app. Ensure your computer and the Android device are on the same Wi-Fi network.

### Install APK
```bash
python3 scripts/manage_app.py install-apk --adb-port <ADB_PORT> <ANDROID_IP>
```
Get the ADB port from the Android device from the _Settings app > General settings > Developer options > Wireless debugging_. (Note: Tap on the _Wireless debugging_ button to show the port number.)
    
### Sync Photos (Smart Resize)
Syncs photos from local directories to the app. Automatically resizes them to fill the device screen.
```bash
python3 scripts/manage_app.py sync <ANDROID_IP> /path/to/photos /another/path
```

### Report Orientation
Scan local directories to see how many landscape vs. portrait photos you have.
```bash
python3 scripts/manage_app.py report-orientation /path/to/photos
```

### View Database Contents
Inspect the internal state of the app (e.g., see play history or cached locations).
```bash
# View History (Two-column table)
python3 scripts/manage_app.py show-db <ANDROID_IP> --db history

# View Location Cache
python3 scripts/manage_app.py show-db <ANDROID_IP> --db location
```

### Clear Database
Reset the history or location cache.
```bash
python3 scripts/manage_app.py clear-db <ANDROID_IP> --db history
```

### Delete All Photos
**WARNING**: Wipes all photos and data from the app.
```bash
python3 scripts/manage_app.py delete-all <ANDROID_IP>
```

## Architecture

-   **Frontend**: Jetpack Compose (Kotlin).
-   **Data**: Room Database for tracking photo history and location cache.
-   **Networking**: Custom TCP protocol (`TcpCommandServer`) handling JSON commands and binary image streams.
-   **Image Processing**: Python (`Pillow`) handles heavy lifting (resizing/cropping) on the host machine before transfer.

## Building
```bash 
    ./gradlew assembleRelease # (or assembleDebug)
```

## Cheat sheet

### Install APK

Requires the tablet is configured for Wi-Fi debugging and has been paired with the computer (see [this](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi) for details).

* `~/src/SlideShowAi/.venv/bin/python ~/src/SlideShowAi/scripts/manage_app.py install-apk --apk-path ~/src/SlideShowAi/app/build/outputs/apk/release/app-release.apk --adb-port <ADB_PORT> photoframe1`

### Sync Photos
* `pushd "$HOME/Pictures/Misc/For Slide Show"`
* `~/src/SlideShowAi/.venv/bin/python ~/src/SlideShowAi/scripts/manage_app.py sync photoframe1 'Photo Frame' 'Misc. Downloads' 'Photo Frame Base'`
* `~/src/SlideShowAi/.venv/bin/python ~/src/SlideShowAi/scripts/manage_app.py sync photoframe2 'Photo Frame (People)' 'Photo Frame Base (People)'`

### Report Orientation
* `~/src/SlideShowAi/.venv/bin/python ~/src/SlideShowAi/scripts/manage_app.py report-orientation /Users/mishkin/Pictures/SlideShowAi`

### View Database Contents
* `~/src/SlideShowAi/.venv/bin/python ~/src/SlideShowAi/scripts/manage_app.py show-db photoframe1 --db history`
* `~/src/SlideShowAi/.venv/bin/python ~/src/SlideShowAi/scripts/manage_app.py show-db photoframe1 --db location`

### Clear Database
* `~/src/SlideShowAi/.venv/bin/python ~/src/SlideShowAi/scripts/manage_app.py clear-db photoframe1 --db history`

### Delete All Photos
* `~/src/SlideShowAi/.venv/bin/python ~/src/SlideShowAi/scripts/manage_app.py delete-all photoframe1`
