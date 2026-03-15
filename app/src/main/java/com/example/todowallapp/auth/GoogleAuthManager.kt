package com.example.todowallapp.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.tasks.TasksScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manages Google Sign-In authentication for Google Tasks API access
 */
class GoogleAuthManager(private val context: Context) {

    private val tasksScope = Scope(TasksScopes.TASKS)
    private val calendarEventsScope = Scope(CalendarScopes.CALENDAR_EVENTS)

    private val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, createSignInOptions(includeCalendarScope = false))
    }

    private val googleSignInWithCalendarClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, createSignInOptions(includeCalendarScope = true))
    }

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

    /**
     * Check if user is already signed in
     */
    fun isSignedIn(requireCalendarScope: Boolean = false): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        if (account.isExpired) return false
        return !requireCalendarScope || hasCalendarScope(account)
    }

    /**
     * Get the current signed-in account, or null if not signed in
     */
    fun getCurrentAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Get the sign-in intent to launch
     */
    fun getSignInIntent(includeCalendarScope: Boolean = false): Intent {
        return if (includeCalendarScope) {
            googleSignInWithCalendarClient.signInIntent
        } else {
            googleSignInClient.signInIntent
        }
    }

    /**
     * Intent to request calendar scope re-consent from an already signed-in user.
     */
    fun getCalendarReconsentIntent(): Intent {
        return googleSignInWithCalendarClient.signInIntent
    }

    /**
     * Whether the account currently has Calendar Events scope.
     */
    fun hasCalendarScope(account: GoogleSignInAccount? = getCurrentAccount()): Boolean {
        return account != null && GoogleSignIn.hasPermissions(account, calendarEventsScope)
    }

    /**
     * Handle the result from the sign-in intent
     */
    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            Result.success(account)
        } catch (e: ApiException) {
            Result.failure(Exception("Sign-in failed: ${e.statusCode} - ${e.message}"))
        }
    }

    /**
     * Attempt silent sign-in (for returning users)
     */
    suspend fun silentSignIn(requireCalendarScope: Boolean = false): Result<GoogleSignInAccount> = withContext(Dispatchers.IO) {
        try {
            val account = if (requireCalendarScope) {
                googleSignInWithCalendarClient.silentSignIn().await()
            } else {
                googleSignInClient.silentSignIn().await()
            }
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out the current user
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        googleSignInClient.signOut().await()
    }

}

/**
 * Authentication state for the app
 */
sealed class AuthState {
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    data class Authenticated(val account: GoogleSignInAccount) : AuthState()
    data class Error(val message: String) : AuthState()
}
