# Media Downloader (Android wrapper)

The app is app/src/main/assets/www/index.html (unmodified copy of media-downloader-lite-v5.html)
inside a plain Android WebView. No third-party dependencies.

## Build
Android Studio: File > Open this folder > Build > Build APK(s).
Command line (JDK 17 + Android SDK, ANDROID_HOME set):
  gradle wrapper --gradle-version 8.9 && ./gradlew assembleDebug
Output: app/build/outputs/apk/debug/app-debug.apk
Or push to GitHub: the included workflow builds the APK (Actions > Build APK).
