# Build and install Dreamboard on your Android phone

Everything here is free. You need a computer (Windows, macOS or Linux) and your Android phone with a USB cable.

## Option A: Android Studio (recommended)

1. Install **Android Studio** (free) from developer.android.com/studio. Accept the default setup; it installs the Android SDK and a bundled JDK 17+.
2. Unzip `Dreamboard.zip` and choose **File › Open**, then select the `Dreamboard` folder (the one containing `settings.gradle.kts`).
3. Wait for **Gradle sync** to finish. The first sync downloads the dependencies (a few hundred MB) once; after that the project builds offline.
4. If Android Studio asks to install **SDK Platform 35** or build tools, click the link in the message to install them.
5. On your phone, enable developer mode: **Settings › About phone**, tap **Build number** seven times. Then **Settings › System › Developer options › USB debugging** on.
6. Connect the phone by USB and accept the "Allow USB debugging" prompt on the phone.
7. Pick your phone in the device dropdown in the toolbar and press **Run ▶**. Dreamboard installs and opens.

To run the finance unit tests: right-click `app/src/test` › **Run 'Tests in app'**, or run `./gradlew testDebugUnitTest`.

## Option B: build an APK and copy it to the phone

1. In Android Studio choose **Build › Build App Bundle(s) / APK(s) › Build APK(s)**, or from a terminal in the project folder run:
   - macOS/Linux: `./gradlew assembleDebug`
   - Windows: `gradlew.bat assembleDebug`
2. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
3. Copy it to the phone (USB file transfer, or save it to your own Google Drive and open it from the phone).
4. Open the file on the phone. Android asks you to allow installs from that source (Files or Drive) once; allow it and tap **Install**.

The debug build installs as **Dreamboard** with package `com.dreamboard.app.debug`, so it can sit beside a future release build.

## Option C: command line only

1. Install a JDK 17 or newer (e.g. Eclipse Temurin, free).
2. Install the Android command-line tools from developer.android.com/studio#command-tools, then:
   `sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"`
3. Create `local.properties` in the project root containing `sdk.dir=/path/to/Android/sdk` (on Windows use forward slashes or `\\`).
4. Run `./gradlew assembleDebug`, then with the phone connected: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Signed release build (optional, free)

1. **Build › Generate Signed App Bundle / APK › APK**, create a new keystore and keep it (and its password) safe. Losing it means you cannot update the installed app.
2. Choose the `release` variant. The signed APK appears in `app/release/`.
3. Never commit the keystore; `.gitignore` already excludes `*.jks` and `*.keystore`.

## Troubleshooting

- **"SDK location not found"**: open the project in Android Studio once, or add `local.properties` as in Option C.
- **Gradle JDK errors**: in Android Studio, **Settings › Build Tools › Gradle › Gradle JDK**, choose the bundled JBR (17 or 21).
- **Screenshots come out black**: that is screenshot protection, on by default. Turn it off in **Settings › Privacy**.
- **App not installing over an older copy**: uninstall the previous Dreamboard first (Phase 1 holds only demo data).

## Versions used

Gradle 8.11.1 (wrapper), Android Gradle Plugin 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Hilt 2.52, Compose BOM 2024.12.01, Navigation 2.8.5, Lifecycle 2.8.7, DataStore 1.1.1, compileSdk/targetSdk 35, minSdk 26. All are free and open source (Apache 2.0).
