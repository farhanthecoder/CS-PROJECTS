# Partner Doodle 💌

An Android app that lets you draw doodles on your phone and have your partner see them live on **their** lock screen — and vice versa.

---

## How it works

| Step | What happens |
|------|-------------|
| **Draw** | Open the doodle canvas (accessible from the lock screen too). Paint with any colour, adjust brush size, undo/redo, and erase. |
| **Send** | Tap **Send 💌**. The canvas is compressed and uploaded to Firebase Storage; the download URL is written to Firebase Realtime Database. |
| **Partner sees it** | The partner's phone is listening in real time. As soon as the URL lands, Glide downloads the image and the live wallpaper redraws — so the doodle appears on their lock screen instantly. |

---

## Architecture

```
┌───────────────────────────────────────────────────────┐
│                    Your phone                         │
│  DoodleActivity  ──▶  FirebaseManager.uploadDoodle()  │
│       (lock screen overlay drawing canvas)            │
└──────────────────────────┬────────────────────────────┘
                           │  Firebase Storage + RTDB
┌──────────────────────────▼────────────────────────────┐
│                  Partner's phone                      │
│  DoodleWallpaperService  ◀──  ValueEventListener      │
│       (live wallpaper, visible on lock screen)        │
└───────────────────────────────────────────────────────┘
```

### Key components

| File | Purpose |
|------|---------|
| `DoodleView.kt` | Custom `View` — touch-driven Bézier-smoothed drawing canvas with undo/redo |
| `DoodleActivity.kt` | Full-screen drawing UI; shown **over the lock screen** via `setShowWhenLocked(true)` |
| `DoodleWallpaperService.kt` | `WallpaperService` that renders the partner's doodle; visible on the lock screen as the live wallpaper |
| `FirebaseManager.kt` | Singleton wrapping Firebase Auth (anonymous), Realtime Database, and Storage |
| `MainActivity.kt` | Home screen — shows pairing code, partner status, and navigation |
| `PairingActivity.kt` | Enter partner's 6-character code to link accounts |

---

## Setup (required before building)

### 1. Create a Firebase project

1. Go to [console.firebase.google.com](https://console.firebase.google.com) and click **Add project**.
2. Give it any name (e.g. "PartnerDoodle").

### 2. Add an Android app

1. In your Firebase project, click **Add app → Android**.
2. Set the package name exactly to: `com.partnerdoodle.app`
3. Download the generated **`google-services.json`** file.
4. Place it at `app/google-services.json` (next to `app/build.gradle`).

### 3. Enable Firebase services

In the Firebase console:

- **Authentication** → Sign-in method → Enable **Anonymous**
- **Realtime Database** → Create database → Start in **test mode** (you can add security rules later)
- **Storage** → Get started → Start in **test mode**

#### Recommended Realtime Database rules

```json
{
  "rules": {
    "pairingCodes": {
      "$code": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid"
      }
    },
    "doodles": {
      "$uid": {
        ".read": "auth != null",
        ".write": "auth != null && auth.uid == $uid"
      }
    }
  }
}
```

### 4. Build & install

```bash
cd PartnerDoodle
./gradlew assembleDebug
# Then install on device:
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Using the app (first run)

### Person A
1. Open the app → your **6-character code** is displayed (e.g. `AB12CD`).
2. Share that code with your partner.

### Person B
1. Open the app → tap **Enter Partner's Code**.
2. Type Person A's code → tap **Connect**.

### Both phones
- Tap **Set Live Wallpaper** and select **Partner Doodle** from the system wallpaper picker.
- The wallpaper shows your partner's doodle on the lock screen.

### Drawing
- Tap **Open Doodle Canvas** (or open from the notification on the lock screen).
- Draw, then tap **Send 💌**.
- Your partner's wallpaper updates in seconds.

---

## Lock screen access

The `DoodleActivity` is configured with:

```xml
android:showOnLockScreen="true"
android:turnScreenOn="true"
```

and at runtime:

```kotlin
setShowWhenLocked(true)   // API 27+
setTurnScreenOn(true)     // API 27+
```

This lets you draw without unlocking your phone — perfect for a quick love note.

---

## Permissions

| Permission | Reason |
|-----------|--------|
| `INTERNET` | Firebase sync |
| `SET_WALLPAPER` / `BIND_WALLPAPER` | Live wallpaper |
| `SYSTEM_ALERT_WINDOW` | Optional: overlay support |
| `RECEIVE_BOOT_COMPLETED` | Re-attach Firebase listener after reboot |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |
| `FOREGROUND_SERVICE` | Keep sync alive in background |

---

## Extending the app

- **Notification on new doodle** — use Firebase Cloud Messaging (FCM) to ping the partner's device.
- **Multiple doodles / gallery** — store an array of URLs instead of overwriting `latest.jpg`.
- **Animated doodles** — record stroke events and replay them as an animation in the wallpaper.
- **Custom lock screen widget** — on Android 14+ you can use the Lock Screen Widget API.

---

## Tech stack

- **Kotlin** + Android SDK 34
- **Firebase** — Auth (anonymous), Realtime Database, Storage
- **Glide** — image loading in the wallpaper service
- **Material Components** — UI
- **Kotlin Coroutines** — async Firebase calls
