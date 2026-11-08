# DataSaving

DataSaving is an Android offline-content companion. It saves selected web pages and files while connected, then makes those saved items available from local device storage.

## Features
- Native Android app built with Kotlin and Jetpack Compose
- Connection status indicator (Wi-Fi / online / offline)
- In-app browser with WebView cache and save-page archive
- Wi-Fi-only direct file downloads using Android DownloadManager
- Local saved-file library and storage totals (B / KB / MB / GB / TB)
- DataSaving app network usage for the current app session
- GitHub Actions debug APK build

## Important limitations
This app does **not** store mobile-data credit, create internet access, or convert cached gigabytes into live connectivity. Only content downloaded or cached before going offline can be used offline. Some websites require an active connection, and a saved page may not preserve every interactive feature or remote resource.

Android also restricts access to other apps' private traffic totals. Usage figures in this app are limited to DataSaving's own traffic for the current session.

## Build
The GitHub Actions workflow builds a debug APK on pushes, pull requests, and manual runs.

1. Open the repository's **Actions** tab.
2. Select **Build DataSaving APK**.
3. Open a successful run.
4. Download the **DataSaving-debug-apk** artifact.

For a local build with JDK 17 and Gradle installed:

```bash
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Package
`com.fiesta.datasaving`
