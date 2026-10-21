# Dusk

Dims the screen below the system minimum with a black overlay. Sideloaded; no ads, no analytics, no internet permission.

- **Quick Settings tile** "Dimmer": tap to toggle, long-press for settings.
- **Notification** (low priority, persistent while on): Dimmer −, Dimmer +, Off. Steps are 10%.
- **Settings**: on/off switch, dim-level slider with live preview, overlay-permission button, battery-optimization button.
- Level and on/off state persist in SharedPreferences. If the system kills and restarts the service, it comes back at the saved level.

## How it works

`DimmerService` is a foreground service that adds one `TYPE_APPLICATION_OVERLAY` window: solid black, with the dim level as the window alpha. Flags are `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`, and the window is a square the size of the longest screen side, so it covers the status bar and nav bar in either orientation.

### The 80% limit on Android 12+

Android 12 and later block touches from passing through another app's overlay when its opacity is above `InputManager.getMaximumObscuringOpacityForTouch()`, which is 0.8 by default. Above that, the screen would stop responding. So the slider and the + button stop at that limit (80% on almost every phone); 90% is available only on Android 8–11. The settings screen says so when the cap applies.

## Build

Needs JDK 17+ (21 recommended) and the Android SDK with platform 37 (Android Studio installs it, or `sdkmanager "platforms;android-37.0" "build-tools;37.0.0"`).

```sh
git clone https://github.com/tobijeh/Dusk.git && cd Dusk
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # skip if ANDROID_HOME is set
./gradlew testDebugUnitTest lintDebug                # tests and lint
./gradlew assembleRelease                            # app/build/outputs/apk/release/app-release.apk
```

Or download `dusk-apk` from the latest run on the Actions tab. CI signs with a fresh debug key each run, so a CI build won't install over a locally built one (or another CI run). Uninstall first if Android says the package conflicts.

## Install

With USB debugging on:

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

Or copy the APK to the phone and open it (allow "Install unknown apps" for your file manager).

## First run

1. Open **Dusk** and tap **Grant overlay permission** → enable "Display over other apps".
2. Tap **Battery optimization settings** and set Dusk to "Don't optimize". On ColorOS also go to App info → Battery → **Allow background activity** (and lock Dusk in Recents), or ColorOS will kill the service.
3. Turn the dimmer on once from the app and allow notifications, so the −/+/Off controls show.
4. Pull down Quick Settings → edit (pencil) → drag the **Dimmer** tile into place.

## Phone checks

The overlay, tile, and notification can't run in unit tests. After changes, check on a phone: tile toggles and shows state, long-press opens settings, slider previews live, −/+/Off work, touches pass through at the max level, status and nav bars are covered in both orientations, and the dimmer survives a while with the screen off.
