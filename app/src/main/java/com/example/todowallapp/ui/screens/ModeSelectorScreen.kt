package com.example.todowallapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.AppMode
import com.example.todowallapp.ui.theme.LocalWallColors

@Composable
fun ModeSelectorScreen(
    onSelectMode: (AppMode) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalWallColors.current.surfaceBlack)
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
                onClick = { onSelectMode(AppMode.WALL) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            ModeCard(
                title = "Phone Mode",
                subtitle = "Capture-first with camera + voice",
                onClick = { onSelectMode(AppMode.PHONE) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Sign out",
            style = MaterialTheme.typography.labelLarge,
            color = LocalWallColors.current.urgencyOverdue,
            modifier = Modifier.clickable(onClick = onSignOut)
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
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
