# Cyber Tazkiah

Android app by Cyber Hayat PK. It blocks adult content and, when something is detected, covers the screen with an ayah or hadith and a short pause before you go back.

## Layers
1. **Guard** (Accessibility service): notices adult searches, URLs and on-screen text, shows the reminder, then goes Back + Home.
2. **DNS Shield** (local VPN, DNS only): blocks adult domains in every app; the rest goes to Cloudflare Family DNS (1.1.1.3).
3. **Uninstall protection** (optional device admin) and a **think-twice guard** (reminder + 20 s pause before disabling).
4. Daily ayah/hadith notification.

Only counters are stored. No page text, URLs or searches are saved or sent.

## Build
Push to GitHub. `.github/workflows/main.yml` builds `CyberTazkiah.apk` (Actions → Artifacts). Tag `v1.0.1` to also attach it to a Release.

## Install notes
- Android 13+: for a sideloaded APK open **App info → ⋮ → Allow restricted settings** before enabling the Guard.
- Keep the repo **private**: the signing keystore password is in `app/build.gradle`.

## Edit
- `app/src/main/assets/data/reminders.json` — ayat/ahadith (have a scholar verify before release)
- `app/src/main/assets/blocklist/` — keywords and domains
- `tazkiah-app/` — the UI (HTML/CSS/JS, always light)
