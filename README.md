# AkiraStudio

Android WebView browser focused on TikTok web + TikTok Studio upload, with a lightweight extension importer (unpacked extensions / content scripts).

## Build (GitHub Actions)
1. Push this repo to GitHub.
2. Actions → "Build APK" → run workflow (or push to main).
3. Download artifact `AkiraStudio-debug` → `app-debug.apk`.

## Notes
- Login uses the normal TikTok web flow.
- Only TikTok domains (and required login providers) are allowed in the main frame.
- Extensions: import a ZIP containing `manifest.json` (Chrome-style content_scripts) from device storage. Full Chrome extension APIs are not supported; content scripts only.
- Package/name uses "akira"; rename if publishing.
