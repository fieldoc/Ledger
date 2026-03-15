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
 * Gracefully no-ops if Firebase is not configured (no google-services.json).
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
