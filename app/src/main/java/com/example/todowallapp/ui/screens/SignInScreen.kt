package com.example.todowallapp.ui.screens

import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SignInScreen(
    isLoading: Boolean,
    error: String?,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContent by remember { mutableStateOf(false) }

    // Animate content appearance
    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(WallAnimations.LONG),
        label = "contentAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalWallColors.current.surfaceBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .alpha(contentAlpha)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App title
            Text(
                text = "Ledger",
                style = MaterialTheme.typography.displayMedium,
                color = LocalWallColors.current.textPrimary,
                fontWeight = FontWeight.Light
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your tasks, held in place",
                style = MaterialTheme.typography.bodyLarge,
                color = LocalWallColors.current.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Sign in button
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = LocalWallColors.current.accentPrimary,
                    strokeWidth = 3.dp
                )
            } else {
                SignInButton(
                    onClick = onSignInClick,
                    enabled = !isLoading
                )
            }

            // Error message
            if (error != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalWallColors.current.urgencyOverdue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Info text
            Text(
                text = "Connect to Google Tasks to sync\nyour tasks automatically",
                style = MaterialTheme.typography.bodySmall,
                color = LocalWallColors.current.textMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonBackground by animateColorAsState(
        targetValue = if (enabled) LocalWallColors.current.accentPrimary.copy(alpha = 0.9f) else LocalWallColors.current.surfaceCard,
        animationSpec = tween(WallAnimations.SHORT),
        label = "signInButtonBackground"
    )
    val buttonBorder by animateColorAsState(
        targetValue = if (enabled) LocalWallColors.current.accentPrimary else LocalWallColors.current.dividerColor,
        animationSpec = tween(WallAnimations.SHORT),
        label = "signInButtonBorder"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(WallShapes.LargeCornerRadius.dp))
            .background(buttonBackground)
            .border(
                width = 1.dp,
                color = buttonBorder,
                shape = RoundedCornerShape(WallShapes.LargeCornerRadius.dp)
            )
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Sign in with Google",
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) LocalWallColors.current.surfaceBlack else LocalWallColors.current.textPrimary
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun SignInScreenPreview() {
    LedgerTheme {
        SignInScreen(
            isLoading = false,
            error = null,
            onSignInClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun SignInScreenLoadingPreview() {
    LedgerTheme {
        SignInScreen(
            isLoading = true,
            error = null,
            onSignInClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun SignInScreenErrorPreview() {
    LedgerTheme {
        SignInScreen(
            isLoading = false,
            error = "Sign-in failed. Please try again.",
            onSignInClick = {}
        )
    }
}


