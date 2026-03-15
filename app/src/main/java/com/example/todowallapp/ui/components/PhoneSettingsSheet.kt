package com.example.todowallapp.ui.components

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.viewmodel.ThemeMode

private val PHONE_SYNC_INTERVAL_OPTIONS = listOf(1, 2, 5, 10, 15, 30)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PhoneSettingsSheet(
    visible: Boolean,
    themeMode: ThemeMode,
    syncIntervalMinutes: Int,
    geminiKeyPresent: Boolean,
    isValidatingKey: Boolean,
    error: String?,
    weatherLocation: String = "",
    weatherApiKeyPresent: Boolean = false,
    onDismiss: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit,
    onSaveWeatherLocation: (String) -> Unit = {},
    onSaveWeatherApiKey: (String) -> Unit = {},
    onClearWeatherApiKey: () -> Unit = {},
    onSearchCities: (suspend (String) -> List<String>) = { emptyList() },
    onSwitchMode: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val colors = LocalWallColors.current
    var keyInput by remember { mutableStateOf("") }

    fun cycleThemeMode() {
        val modes = ThemeMode.entries
        val currentIndex = modes.indexOf(themeMode).coerceAtLeast(0)
        val nextIndex = (currentIndex + 1) % modes.size
        onThemeModeChange(modes[nextIndex])
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.surfaceBlack,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.85f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .height(4.dp)
                    .width(36.dp)
                    .background(colors.borderColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            item {
                // Bento Grid Row 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Theme Tile
                    SettingsTile(
                        label = "Appearance",
                        value = themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                        icon = when (themeMode) {
                            ThemeMode.AUTO -> Icons.Outlined.AutoMode
                            ThemeMode.DARK -> Icons.Outlined.DarkMode
                            ThemeMode.LIGHT -> Icons.Outlined.LightMode
                        },
                        modifier = Modifier.weight(1f),
                        onClick = ::cycleThemeMode
                    )

                    // Sync Tile
                    SettingsTile(
                        label = "Sync Refresh",
                        value = "$syncIntervalMinutes min",
                        icon = Icons.Outlined.Refresh,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val currentIndex = PHONE_SYNC_INTERVAL_OPTIONS.indexOf(syncIntervalMinutes).coerceAtLeast(0)
                            val nextIndex = (currentIndex + 1) % PHONE_SYNC_INTERVAL_OPTIONS.size
                            onSyncIntervalChange(PHONE_SYNC_INTERVAL_OPTIONS[nextIndex])
                        }
                    )
                }
            }

            item {
                // Gemini Key Card
                Surface(
                    color = colors.surfaceCard,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Gemini Intelligence",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            placeholder = { 
                                Text(
                                    if (geminiKeyPresent) "Key is active" else "Paste API Key",
                                    color = colors.textMuted
                                ) 
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accentPrimary,
                                unfocusedBorderColor = colors.borderColor,
                                cursorColor = colors.accentPrimary
                            )
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton(
                                text = if (isValidatingKey) "Checking..." else "Save",
                                enabled = !isValidatingKey && keyInput.isNotBlank(),
                                onClick = { onSaveGeminiKey(keyInput) },
                                modifier = Modifier.weight(1f)
                            )
                            if (geminiKeyPresent) {
                                PillButton(
                                    text = "Remove",
                                    enabled = true,
                                    onClick = onClearGeminiKey,
                                    isDestructive = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (!error.isNullOrBlank()) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.urgencyOverdue
                            )
                        }
                    }
                }
            }

            item {
                // Weather Settings Card
                Surface(
                    color = colors.surfaceCard,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var weatherLocationInput by remember(weatherLocation) { mutableStateOf(weatherLocation) }
                    var weatherKeyInput by remember { mutableStateOf("") }
                    var locationSaved by remember { mutableStateOf(false) }
                    var keySaved by remember { mutableStateOf(false) }
                    var citySuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
                    var showSuggestions by remember { mutableStateOf(false) }
                    val searchScope = rememberCoroutineScope()
                    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                    LaunchedEffect(locationSaved) {
                        if (locationSaved) { delay(2000); locationSaved = false }
                    }
                    LaunchedEffect(keySaved) {
                        if (keySaved) { delay(2000); keySaved = false }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Weather Tints",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Adds subtle weather-based tints to calendar days",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )

                        // Location field with autocomplete
                        Box {
                            OutlinedTextField(
                                value = weatherLocationInput,
                                onValueChange = { input ->
                                    weatherLocationInput = input
                                    locationSaved = false
                                    if (weatherApiKeyPresent && input.length >= 2) {
                                        searchJob?.cancel()
                                        searchJob = searchScope.launch {
                                            delay(300) // debounce
                                            val results = onSearchCities(input)
                                            citySuggestions = results
                                            showSuggestions = results.isNotEmpty()
                                        }
                                    } else {
                                        citySuggestions = emptyList()
                                        showSuggestions = false
                                    }
                                },
                                placeholder = { Text("City, Country (e.g. London, UK)", color = colors.textMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    focusedBorderColor = colors.accentPrimary,
                                    unfocusedBorderColor = colors.borderColor,
                                    cursorColor = colors.accentPrimary
                                )
                            )

                            // Suggestions dropdown
                            DropdownMenu(
                                expanded = showSuggestions && citySuggestions.isNotEmpty(),
                                onDismissRequest = { showSuggestions = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(colors.surfaceElevated)
                            ) {
                                citySuggestions.forEach { suggestion ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = suggestion,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.textPrimary
                                            )
                                        },
                                        onClick = {
                                            weatherLocationInput = suggestion
                                            showSuggestions = false
                                            locationSaved = false
                                        }
                                    )
                                }
                            }
                        }

                        PillButton(
                            text = if (locationSaved) "Saved!" else "Save Location",
                            enabled = weatherLocationInput.isNotBlank() && !locationSaved,
                            onClick = {
                                onSaveWeatherLocation(weatherLocationInput)
                                locationSaved = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = weatherKeyInput,
                            onValueChange = { weatherKeyInput = it; keySaved = false },
                            placeholder = {
                                Text(
                                    if (weatherApiKeyPresent) "Key is active" else "OpenWeatherMap API Key",
                                    color = colors.textMuted
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accentPrimary,
                                unfocusedBorderColor = colors.borderColor,
                                cursorColor = colors.accentPrimary
                            )
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton(
                                text = if (keySaved) "Saved!" else "Save Key",
                                enabled = weatherKeyInput.isNotBlank() && !keySaved,
                                onClick = {
                                    onSaveWeatherApiKey(weatherKeyInput)
                                    keySaved = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (weatherApiKeyPresent) {
                                PillButton(
                                    text = "Remove",
                                    enabled = true,
                                    onClick = onClearWeatherApiKey,
                                    isDestructive = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text(
                            text = "Free key at openweathermap.org/api",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textDisabled
                        )
                    }
                }
            }

            item {
                // System Actions
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionRowItem(
                        text = "Switch to Wall Mode",
                        icon = Icons.Outlined.SwapHoriz,
                        onClick = onSwitchMode,
                        tint = colors.accentPrimary
                    )
                    ActionRowItem(
                        text = "Sign Out",
                        icon = Icons.Outlined.Logout,
                        onClick = onSignOut,
                        tint = colors.urgencyOverdue
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTile(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    Surface(
        color = colors.surfaceCard,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderColor.copy(alpha = 0.4f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.accentPrimary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val contentColor = if (isDestructive) colors.urgencyOverdue else colors.accentPrimary
    
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (enabled) contentColor.copy(alpha = 0.12f) else colors.surfaceCard)
            .border(
                1.dp, 
                if (enabled) contentColor.copy(alpha = 0.4f) else colors.borderColor.copy(alpha = 0.2f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else colors.textDisabled,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionRowItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color
) {
    val colors = LocalWallColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint.copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
