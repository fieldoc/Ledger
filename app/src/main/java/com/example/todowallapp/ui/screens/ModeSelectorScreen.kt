package com.example.todowallapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.AppMode
import com.example.todowallapp.ui.theme.LocalWallColors

@Composable
fun ModeSelectorScreen(
    onSelectMode: (AppMode) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableIntStateOf(0) } // 0=Wall, 1=Phone, 2=SignOut
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalWallColors.current.surfaceBlack)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionDown, Key.DirectionRight -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(2)
                        true
                    }
                    Key.DirectionUp, Key.DirectionLeft -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        when (selectedIndex) {
                            0 -> onSelectMode(AppMode.WALL)
                            1 -> onSelectMode(AppMode.PHONE)
                            2 -> onSignOut()
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose Mode",
            style = MaterialTheme.typography.headlineMedium,
            color = LocalWallColors.current.textPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "You can switch this anytime from settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalWallColors.current.textSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            ModeCard(
                title = "Wall Mode",
                subtitle = "Immersive always-on board view",
                isFocused = selectedIndex == 0,
                onClick = { onSelectMode(AppMode.WALL) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            ModeCard(
                title = "Phone Mode",
                subtitle = "Capture-first with camera + voice",
                isFocused = selectedIndex == 1,
                onClick = { onSelectMode(AppMode.PHONE) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Sign out",
            style = MaterialTheme.typography.labelLarge,
            color = LocalWallColors.current.urgencyOverdue,
            modifier = Modifier
                .clickable(onClick = onSignOut)
                .then(
                    if (selectedIndex == 2) {
                        Modifier
                            .border(
                                1.5.dp,
                                LocalWallColors.current.urgencyOverdue.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else Modifier
                )
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    isFocused: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (isFocused) {
                    Modifier.border(
                        1.5.dp,
                        LocalWallColors.current.accentPrimary.copy(alpha = 0.4f),
                        RoundedCornerShape(20.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
