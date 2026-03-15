# Firebase API Key Sync Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sync API keys (Gemini, OpenWeatherMap) from phone to wall tablet via Firebase Realtime Database, so keys entered on the phone automatically appear on the tablet.

**Architecture:** A single `FirebaseKeySync` class handles push (phone) and pull (tablet) operations. Firebase Auth piggybacks on existing Google Sign-In by adding `requestIdToken()` to the sign-in options. Keys are stored at `/users/{uid}/keys/` in Firebase RTDB with user-scoped security rules.

**Tech Stack:** Firebase Auth, Firebase Realtime Database, Google Sign-In (existing), EncryptedSharedPreferences (existing)

**Spec:** `docs/superpowers/specs/2026-03-14-firebase-key-sync-design.md`

---

## Chunk 1: Gradle & Auth Setup

### Task 1: Add Firebase dependencies to Gradle

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (project root)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add Firebase versions and libraries to version catalog**

In `gradle/libs.versions.toml`, add:

```toml
# Under [versions]:
firebaseBom = "33.8.0"
googleServices = "4.4.2"

# Under [libraries]:
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth-ktx" }
firebase-database = { group = "com.google.firebase", name = "firebase-database-ktx" }

# Under [plugins]:
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 2: Add google-services plugin to project-level build.gradle.kts**

Add to `build.gradle.kts` (root):

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false  // NEW
}
```

- [ ] **Step 3: Apply plugin and add dependencies in app/build.gradle.kts**

Add at end of plugins block:
```kotlin
alias(libs.plugins.google.services)
```

Add in dependencies block:
```kotlin
// Firebase
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.auth)
implementation(libs.firebase.database)
```

- [ ] **Step 4: Verify build compiles**

Run: `cd C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -20`

**NOTE:** Build will fail until `google-services.json` is placed in `app/`. That's a manual user step. The code should be written to handle Firebase being unconfigured gracefully. We'll add a safety check in `FirebaseKeySync` to no-op if Firebase isn't initialized.

- [ ] **Step 5: Commit**

```
feat: add Firebase Auth and Realtime Database dependencies
```

---

### Task 2: Add `requestIdToken` to GoogleAuthManager

Firebase Auth requires the Google ID token from sign-in. Currently `GoogleAuthManager.createSignInOptions()` does NOT call `requestIdToken()`. We need to add it.

The web client ID comes from `google-services.json` and is auto-generated as a string resource `default_web_client_id` by the google-services plugin.

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/auth/GoogleAuthManager.kt`

- [ ] **Step 1: Add requestIdToken to createSignInOptions**

In `GoogleAuthManager.kt`, modify `createSignInOptions()` (line 33-44) to include `requestIdToken`:

```kotlin
private fun createSignInOptions(includeCalendarScope: Boolean): GoogleSignInOptions {
    val webClientId = try {
        context.getString(
            context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        )
    } catch (_: Exception) { null }

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(tasksScope)
        .apply {
            if (webClientId != null) {
                requestIdToken(webClientId)
            }
            if (includeCalendarScope) {
                requestScopes(calendarEventsScope)
            }
        }
        .build()
    return gso
}
```

The `try/catch` ensures the app still works if `google-services.json` isn't present (no Firebase = no ID token = no key sync, but sign-in still works).

- [ ] **Step 2: Commit**

```
feat: request Google ID token for Firebase Auth during sign-in
```

---

## Chunk 2: FirebaseKeySync Implementation

### Task 3: Create FirebaseKeySync class

This is the core class. It handles:
- Firebase Auth sign-in (using Google ID token)
- Pushing keys to Firebase RTDB (phone writes)
- Pulling keys from Firebase RTDB (tablet reads)

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/security/FirebaseKeySync.kt`

- [ ] **Step 1: Create the FirebaseKeySync class**

```kotlin
package com.example.todowallapp.security

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Syncs API keys between devices via Firebase Realtime Database.
 * Phone pushes keys after saving locally; tablet pulls on sign-in and periodic sync.
 */
class FirebaseKeySync(
    private val geminiKeyStore: GeminiKeyStore,
    private val weatherKeyStore: WeatherKeyStore
) {
    private val tag = "FirebaseKeySync"

    private val auth: FirebaseAuth?
        get() = try { FirebaseAuth.getInstance() } catch (_: Exception) { null }

    private val database: FirebaseDatabase?
        get() = try { FirebaseDatabase.getInstance() } catch (_: Exception) { null }

    private val isAvailable: Boolean
        get() = auth != null && database != null

    /**
     * Authenticate with Firebase using the Google Sign-In account's ID token.
     * Must be called after Google Sign-In succeeds.
     * No-ops if Firebase is not configured (no google-services.json).
     */
    suspend fun signIn(account: GoogleSignInAccount) {
        if (!isAvailable) {
            Log.d(tag, "Firebase not configured, skipping sign-in")
            return
        }
        val idToken = account.idToken ?: run {
            Log.w(tag, "No ID token available, cannot authenticate with Firebase")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth!!.signInWithCredential(credential).await()
                Log.d(tag, "Firebase auth succeeded for uid=${auth!!.currentUser?.uid}")
            } catch (e: Exception) {
                Log.e(tag, "Firebase auth failed", e)
            }
        }
    }

    /**
     * Push all local keys to Firebase. Called from phone after saving a key.
     */
    suspend fun pushKeys() {
        val uid = auth?.currentUser?.uid ?: return
        val db = database ?: return

        withContext(Dispatchers.IO) {
            try {
                val keysRef = db.getReference("users").child(uid).child("keys")
                val data = mutableMapOf<String, Any>(
                    "updated_at" to System.currentTimeMillis()
                )
                geminiKeyStore.getApiKey()?.let { data["gemini_api_key"] = it }
                weatherKeyStore.getApiKey()?.let { data["owm_api_key"] = it }
                weatherKeyStore.getLocation()?.let { data["weather_location"] = it }

                keysRef.setValue(data).await()
                Log.d(tag, "Keys pushed to Firebase")
            } catch (e: Exception) {
                Log.e(tag, "Failed to push keys", e)
            }
        }
    }

    /**
     * Pull keys from Firebase and save to local key stores.
     * Called from tablet on sign-in and during periodic sync.
     * Returns true if any keys were updated locally.
     */
    suspend fun pullKeys(): Boolean {
        val uid = auth?.currentUser?.uid ?: return false
        val db = database ?: return false

        return withContext(Dispatchers.IO) {
            try {
                val snapshot = db.getReference("users").child(uid).child("keys")
                    .get().await()

                if (!snapshot.exists()) {
                    Log.d(tag, "No keys in Firebase")
                    return@withContext false
                }

                var updated = false

                snapshot.child("gemini_api_key").getValue(String::class.java)?.let { remoteKey ->
                    if (remoteKey.isNotBlank() && remoteKey != geminiKeyStore.getApiKey()) {
                        geminiKeyStore.setApiKey(remoteKey)
                        updated = true
                        Log.d(tag, "Gemini key updated from Firebase")
                    }
                }

                snapshot.child("owm_api_key").getValue(String::class.java)?.let { remoteKey ->
                    if (remoteKey.isNotBlank() && remoteKey != weatherKeyStore.getApiKey()) {
                        weatherKeyStore.setApiKey(remoteKey)
                        updated = true
                        Log.d(tag, "Weather key updated from Firebase")
                    }
                }

                snapshot.child("weather_location").getValue(String::class.java)?.let { remoteLocation ->
                    if (remoteLocation.isNotBlank() && remoteLocation != weatherKeyStore.getLocation()) {
                        weatherKeyStore.setLocation(remoteLocation)
                        updated = true
                        Log.d(tag, "Weather location updated from Firebase")
                    }
                }

                updated
            } catch (e: Exception) {
                Log.e(tag, "Failed to pull keys", e)
                false
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```
feat: add FirebaseKeySync for cross-device API key transfer
```

---

## Chunk 3: Integration — Phone Push

### Task 4: Push keys from PhoneCaptureViewModel after saving

When a key is saved or cleared on the phone, push all keys to Firebase.

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/PhoneCaptureViewModel.kt`
- Modify: `app/src/main/java/com/example/todowallapp/MainActivity.kt`

- [ ] **Step 1: Add FirebaseKeySync as a dependency of PhoneCaptureViewModel**

In `PhoneCaptureViewModel.kt`, add the parameter:

```kotlin
class PhoneCaptureViewModel(
    context: Context,
    private val tasksRepository: GoogleTasksRepository,
    private val geminiRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore,
    private val pendingCaptureStore: PendingCaptureStore,
    private val firebaseKeySync: FirebaseKeySync? = null  // NEW
) : ViewModel() {
```

- [ ] **Step 2: Push keys after Gemini key save (line 354-360)**

After `geminiKeyStore.setApiKey(key)` in `validateAndSaveGeminiKey()`, add the push:

```kotlin
onSuccess = {
    geminiKeyStore.setApiKey(key)
    firebaseKeySync?.let { sync ->
        viewModelScope.launch { sync.pushKeys() }
    }
    _uiState.value = _uiState.value.copy(
        isValidatingKey = false,
        geminiKeyPresent = true,
        infoMessage = "Gemini key saved"
    )
},
```

- [ ] **Step 3: Push keys after Gemini key clear (line 373-378)**

After `geminiKeyStore.clearApiKey()` in `clearGeminiKey()`, add the push:

```kotlin
fun clearGeminiKey() {
    geminiKeyStore.clearApiKey()
    firebaseKeySync?.let { sync ->
        viewModelScope.launch { sync.pushKeys() }
    }
    _uiState.value = _uiState.value.copy(
        geminiKeyPresent = false,
        infoMessage = "Gemini key removed"
    )
}
```

- [ ] **Step 4: Update Factory class to include FirebaseKeySync (line 612-629)**

```kotlin
class Factory(
    private val context: Context,
    private val tasksRepository: GoogleTasksRepository,
    private val geminiRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore,
    private val pendingCaptureStore: PendingCaptureStore,
    private val firebaseKeySync: FirebaseKeySync? = null  // NEW
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PhoneCaptureViewModel(
            context = context,
            tasksRepository = tasksRepository,
            geminiRepository = geminiRepository,
            geminiKeyStore = geminiKeyStore,
            pendingCaptureStore = pendingCaptureStore,
            firebaseKeySync = firebaseKeySync  // NEW
        ) as T
    }
}
```

- [ ] **Step 5: Push keys after weather key/location changes in MainActivity**

In `MainActivity.kt`, the weather key save/clear callbacks (lines 341-349) call `weatherKeyStore` directly. Add Firebase push after each.

First, create a `firebaseKeySync` instance alongside the other remembers (~line 152):

```kotlin
val firebaseKeySync = remember { FirebaseKeySync(geminiKeyStore, weatherKeyStore) }
```

Then modify the weather callbacks in `PhoneModeContent` call site:

```kotlin
onSaveWeatherLocation = { location ->
    weatherKeyStore.setLocation(location)
    scope.launch { firebaseKeySync.pushKeys() }
    wallViewModel.refreshWeather()
},
onSaveWeatherApiKey = { key ->
    weatherKeyStore.setApiKey(key)
    scope.launch { firebaseKeySync.pushKeys() }
    wallViewModel.refreshWeather()
},
onClearWeatherApiKey = {
    weatherKeyStore.clearApiKey()
    scope.launch { firebaseKeySync.pushKeys() }
},
```

And pass `firebaseKeySync` into the PhoneCaptureViewModel factory:

```kotlin
val phoneViewModel: PhoneCaptureViewModel = viewModel(
    factory = remember {
        PhoneCaptureViewModel.Factory(
            context = appContext,
            tasksRepository = tasksRepository,
            geminiRepository = geminiCaptureRepository,
            geminiKeyStore = geminiKeyStore,
            pendingCaptureStore = pendingCaptureStore,
            firebaseKeySync = firebaseKeySync  // NEW
        )
    }
)
```

- [ ] **Step 6: Commit**

```
feat: push API keys to Firebase after save/clear on phone
```

---

## Chunk 4: Integration — Firebase Auth & Tablet Pull

### Task 5: Firebase Auth on sign-in + pull keys on tablet sync cycle

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt`
- Modify: `app/src/main/java/com/example/todowallapp/MainActivity.kt`

- [ ] **Step 1: Add FirebaseKeySync to TaskWallViewModel constructor**

In `TaskWallViewModel.kt` (line 106-114), add parameter:

```kotlin
class TaskWallViewModel(
    private val context: Context,
    private val authManager: GoogleAuthManager,
    private val tasksRepository: GoogleTasksRepository,
    private val calendarRepository: GoogleCalendarRepository,
    private val geminiKeyStore: GeminiKeyStore = GeminiKeyStore(context),
    private val geminiCaptureRepository: GeminiCaptureRepository = GeminiCaptureRepository(),
    private val weatherRepository: WeatherRepository? = null,
    private val firebaseKeySync: FirebaseKeySync? = null  // NEW
) : ViewModel() {
```

- [ ] **Step 2: Add Firebase sign-in and key pull to onSignedIn()**

In `onSignedIn()` (line 267-311), add Firebase auth + pull after `tasksRepository.initialize(account)`:

```kotlin
fun onSignedIn(account: GoogleSignInAccount) {
    viewModelScope.launch {
        consecutiveSyncFailures = 0
        val currentAccount = authManager.getCurrentAccount()
        val calendarAccount = sequenceOf(account, currentAccount)
            .filterNotNull()
            .firstOrNull { authManager.hasCalendarScope(it) }
        val hasCalendarScope = calendarAccount != null
        _uiState.value = _uiState.value.copy(
            authState = AuthState.Authenticated(account),
            isLoading = true,
            hasCalendarScope = hasCalendarScope,
            calendarError = null
        )

        // Initialize the repository
        tasksRepository.initialize(account)

        // Firebase: authenticate and pull keys from cloud
        firebaseKeySync?.signIn(account)
        val keysUpdated = firebaseKeySync?.pullKeys() ?: false
        if (keysUpdated) {
            Log.d("TaskWallViewModel", "API keys synced from Firebase")
        }

        if (calendarAccount != null) {
            calendarRepository.initialize(calendarAccount)
            loadCalendars()
        }

        // ... rest unchanged
```

Add `import android.util.Log` if not already present.

- [ ] **Step 3: Pull keys during periodic sync**

In `performRefresh()` (line 456), add a key pull at the end of a successful sync. Find the section after tasks are loaded and add:

```kotlin
// At the end of performRefresh, after task/calendar sync completes:
firebaseKeySync?.pullKeys()
```

The simplest insertion point: `performRefresh` already calls `loadTaskLists` and calendar refresh. After the `refreshMutex.withLock` block completes successfully, add the pull. This is a background check — if it fails, no harm done.

- [ ] **Step 4: Pass FirebaseKeySync into TaskWallViewModel from MainActivity**

In `MainActivity.kt` (line 154-169), update the factory:

```kotlin
val wallViewModel: TaskWallViewModel = viewModel(
    factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return TaskWallViewModel(
                    context = appContext,
                    authManager = authManager,
                    tasksRepository = tasksRepository,
                    calendarRepository = calendarRepository,
                    weatherRepository = weatherRepository,
                    firebaseKeySync = firebaseKeySync  // NEW
                ) as T
            }
        }
    }
)
```

NOTE: `firebaseKeySync` must be created BEFORE `wallViewModel` in the composable. Move the `firebaseKeySync` remember block above the `wallViewModel` block.

- [ ] **Step 5: Commit**

```
feat: authenticate with Firebase on sign-in and pull keys on sync
```

---

## Chunk 5: Firebase Auth on sign-in in MainActivity

### Task 6: Wire up Firebase sign-in in the sign-in result handler

The `signInLauncher` and `calendarScopeLauncher` in `MainActivity.kt` call `wallViewModel.onSignedIn(account)` after successful sign-in. The Firebase auth happens inside `onSignedIn()` via `firebaseKeySync.signIn(account)`, so this is already covered by Task 5.

However, we also need Firebase sign-in for the phone mode. The phone's `PhoneCaptureViewModel` doesn't call `onSignedIn` — it uses `setSessionReady()`. Firebase sign-in for the phone happens via the `wallViewModel.onSignedIn()` path which is always called first. So the `firebaseKeySync` instance is shared and already authenticated.

No additional wiring needed — this task is implicit in Task 5.

---

## Chunk 6: ProGuard rules (if minify is enabled for release)

### Task 7: Add ProGuard keep rules for Firebase

Release builds have `isMinifyEnabled = true` (app/build.gradle.kts line 23). Firebase classes must not be stripped.

**Files:**
- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: Check current proguard rules and add Firebase keeps**

Read `app/proguard-rules.pro` and append:

```proguard
# Firebase Realtime Database
-keepattributes Signature
-keepclassmembers class com.example.todowallapp.** {
  *;
}
```

Firebase SDK includes its own consumer ProGuard rules, so typically this isn't needed. But the `keepattributes Signature` ensures generic type info (used by Firebase's serialization) is preserved. This is a safety measure.

- [ ] **Step 2: Commit**

```
chore: add ProGuard keep rules for Firebase serialization
```

---

## Summary of all changes

| # | File | Action | Purpose |
|---|------|--------|---------|
| 1 | `gradle/libs.versions.toml` | Modify | Add Firebase BOM, Auth, Database versions |
| 2 | `build.gradle.kts` (root) | Modify | Add google-services plugin |
| 3 | `app/build.gradle.kts` | Modify | Apply plugin, add Firebase deps |
| 4 | `auth/GoogleAuthManager.kt` | Modify | Add `requestIdToken()` for Firebase Auth |
| 5 | `security/FirebaseKeySync.kt` | Create | Core sync class (push/pull/auth) |
| 6 | `viewmodel/PhoneCaptureViewModel.kt` | Modify | Push keys after save/clear |
| 7 | `viewmodel/TaskWallViewModel.kt` | Modify | Firebase auth on sign-in, pull on sync |
| 8 | `MainActivity.kt` | Modify | Create FirebaseKeySync, wire into ViewModels |
| 9 | `app/proguard-rules.pro` | Modify | Firebase keep rules |

## Manual setup (user must do)

1. Create Firebase project in Firebase Console
2. Add Android app with package `com.example.todowallapp`
3. Add SHA-1 fingerprint (same as used for Google Sign-In)
4. Download `google-services.json` into `app/`
5. Deploy security rules:
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
6. Enable Google as a sign-in provider in Firebase Console > Authentication
