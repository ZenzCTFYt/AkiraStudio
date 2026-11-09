# AkiraStudio

Android WebView browser focused on TikTok web + TikTok Studio upload, with a lightweight extension importer (unpacked extensions / content scripts).

## Build (GitHub Actions)
1. Push this repo to GitHub.
2. Actions → "Build APK" → run workflow (or push to main).
3. Download artifact `AkiraStudio-debug` → `app-debug.apk`.

## Notes
- Login uses the normal TikTok web flow.
- Only TikTok domains (and required login providers) are allowed in the main frame.
- Extensions: tap the Extensions (puzzle) button → Manage/Import to import a ZIP containing `manifest.json`. Click an extension icon to open its real popup (action.default_popup). Sample test extension: `AkiraTestExtension.zip` (in Downloads).
- Package/name uses "akira"; rename if publishing.
