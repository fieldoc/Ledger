# Firebase API Key Sync: Phone-to-Tablet

**Date:** 2026-03-14
**Status:** Approved

## Problem

The phone and wall tablet are separate physical devices running the same APK. API keys (Gemini, OpenWeatherMap) are stored in device-local EncryptedSharedPreferences. Keys entered on the phone don't appear on the tablet. The tablet has only a rotary encoder — no keyboard input possible. The tablet runs Android 6.0.1 with no reliable camera (wall-mounted, camera likely obscured).

## Solution

Use Firebase Realtime Database as a sync channel. The phone pushes keys to Firebase after saving them locally. The tablet pulls keys from Firebase on sign-in and during periodic sync. Both devices authenticate to Firebase using the existing Google Sign-In credential.

## Data Model

```
/users/{uid}/keys/
    gemini_api_key: "AIza..."
    owm_api_key: "abc123..."
    weather_location: "Portland, OR"
    updated_at: 1710400000000
```

## Flow

### Phone (writer)
1. User enters API key in PhoneSettingsSheet
2. Key validated and saved to local EncryptedSharedPreferences (existing behavior)
3. Key pushed to Firebase at `/users/{uid}/keys/`

### Tablet (reader)
1. On Google Sign-In, authenticate with Firebase using the Google ID token
2. Read `/users/{uid}/keys/` and populate local KeyStores
3. On each 5-minute sync cycle, re-read from Firebase (piggyback on existing timer)
4. If Firebase has keys that differ from local, update local KeyStores silently

## Authentication

- Firebase Auth initialized with the existing GoogleSignInAccount ID token
- No new sign-in flow — piggybacks on existing Google Sign-In
- Firebase security rules restrict read/write to `auth.uid == $uid`

## Security Rules

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

## New Dependencies

- `com.google.firebase:firebase-bom` (version management)
- `com.google.firebase:firebase-auth`
- `com.google.firebase:firebase-database`
- `com.google.gms:google-services` Gradle plugin

## Setup Requirements (Manual)

- Create Firebase project in Firebase Console
- Download `google-services.json` into `app/`
- Add SHA-1 fingerprint (same one used for Google Sign-In)
- Deploy security rules from above

## Files Changed

| File | Change |
|------|--------|
| `build.gradle.kts` (project) | Add `google-services` plugin |
| `build.gradle.kts` (app) | Add Firebase dependencies, apply plugin |
| **New:** `security/FirebaseKeySync.kt` | Core sync class: push/pull keys, Firebase Auth bridge |
| `PhoneCaptureViewModel.kt` | After saving a key locally, call `FirebaseKeySync.pushKeys()` |
| `TaskWallViewModel.kt` | On sign-in + each sync cycle, call `FirebaseKeySync.pullKeys()` |
| `MainActivity.kt` | Initialize Firebase Auth with Google credential on sign-in |
| `auth/GoogleAuthManager.kt` | Expose ID token for Firebase Auth |

## Files NOT Changed

- `GeminiKeyStore.kt` — FirebaseKeySync calls its existing `setApiKey()`
- `WeatherKeyStore.kt` — same pattern
- Key validation flow on phone — unchanged
- Wall tablet UX — keys appear automatically

## Offline Behavior

- If Firebase is unreachable, the app works with whatever keys are already in local storage
- Push failures on phone are silent (keys are still saved locally)
- Pull failures on tablet are silent (will retry on next sync cycle)

## Direction

- One-way: phone pushes, tablet pulls
- No tablet-to-phone sync
- No real-time listeners — polling on existing sync interval is sufficient
