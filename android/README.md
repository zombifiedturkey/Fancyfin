# Fancyfin for Android TV

A small Android app that shows Fancyfin full screen on an Android TV box such as the NVIDIA Shield. It
loads the web app from **your own Fancyfin server**, so web updates arrive whenever you update the
container. Only this shell needs updating through the app, and it does that itself.

- Plain `WebView` shell, Kotlin, no third-party libraries (just `androidx.core` for the file provider).
- First launch asks for your Fancyfin address, like `http://192.168.1.20:8282`, and remembers it.
- **Updates**: on start (at most every 6 hours) and from **Settings, TV app, Check for updates**, the app
  looks at this repository's releases for one tagged `tv-v<version>` with an `.apk`, offers it, downloads
  it and opens Android's installer. Android only accepts the update if it is signed with the same key as
  the installed app.
- Menu button on the remote (if it has one): check for updates, change server, reload.

## One-time setup: the signing key

Every release must be signed with the **same key**, or Android refuses to update the app. Make the key once
and keep a copy somewhere safe (if it is lost, you have to uninstall and reinstall the app to change keys).

1. Make the key (Android Studio ships `keytool`; any Java install has it too). In PowerShell:

   ```powershell
   keytool -genkeypair -v -keystore fancyfin.jks -alias fancyfin -keyalg RSA -keysize 2048 -validity 10000
   ```

   Choose a password when asked (use the same one for the key). Never commit `fancyfin.jks`.

2. Turn it into text and copy it:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("fancyfin.jks")) | Set-Clipboard
   ```

3. In the GitHub repository open **Settings, Secrets and variables, Actions, New repository secret** and add:

   | Name | Value |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | what you just copied |
   | `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
   | `ANDROID_KEY_ALIAS` | `fancyfin` |
   | `ANDROID_KEY_PASSWORD` | the key password |

## Releasing

Tag a commit and push the tag. The tag decides the version:

```powershell
git tag tv-v0.1.0
git push origin tv-v0.1.0
```

GitHub Actions builds and signs the APK and attaches `fancyfin-tv.apk` to a release called "Fancyfin TV 0.1.0".
Pushes that touch `android/` also run a debug build, only to check that it compiles.

## Installing on the Shield (first time only)

1. On the Shield: **Settings, Device Preferences, About**, press **Build** seven times (turns on Developer
   options). Then **Developer options, USB debugging** (and **Network debugging** if offered) on.
2. On the PC, with `adb` (Android platform-tools) installed:

   ```powershell
   adb connect <shield-ip>:5555
   adb install fancyfin-tv.apk
   ```

   (Or put the APK on any file server and install it with the Downloader app on the Shield.)
3. Open **Fancyfin** from the Shield's app row, enter your server address, and sign in.

After that, new versions install from inside the app. The first time, Android asks you to allow Fancyfin to
install apps ("Install unknown apps"); turn it on and choose Check for updates again.
