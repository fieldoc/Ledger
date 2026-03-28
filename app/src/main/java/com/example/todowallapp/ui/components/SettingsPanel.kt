package com.example.todowallapp.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import com.example.todowallapp.viewmodel.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val SYNC_INTERVAL_OPTIONS = listOf(1, 2, 5, 10, 15, 30)

private enum class SettingsItemType {
    THEME_MODE,
    LIGHT_HOURS,
    SLEEP_SCHEDULE,
    SYNC_INTERVAL,
    GEMINI_KEY,
    WEATHER,
    SWITCH_MODE,
    SIGN_OUT,
    CLOSE
}

@Composable
fun SettingsPanel(
    themeMode: ThemeMode,
    lightStartHour: Int,
    lightEndHour: Int,
    sleepStartHour: Int,
    sleepEndHour: Int,
    syncIntervalMinutes: Int,
    onThemeSettingsChange: (mode: ThemeMode, lightStart: Int, lightEnd: Int) -> Unit,
    onSleepScheduleChange: (startHour: Int, endHour: Int) -> Unit,
    onSyncIntervalChange: (minutes: Int) -> Unit,
    geminiKeyPresent: Boolean = false,
    isValidatingGeminiKey: Boolean = false,
    geminiKeyError: String? = null,
    onSaveGeminiKey: (String) -> Unit = {},
    onClearGeminiKey: () -> Unit = {},
    weatherLocation: String = "",
    weatherApiKeyPresent: Boolean = false,
    onSaveWeatherLocation: (String) -> Unit = {},
    onSaveWeatherApiKey: (String) -> Unit = {},
    onClearWeatherApiKey: () -> Unit = {},
    onSearchCities: (suspend (String) -> List<String>) = { emptyList() },
    onSwitchMode: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val items = remember(themeMode) {
        buildList {
            add(SettingsItemType.THEME_MODE)
            if (themeMode == ThemeMode.AUTO) {
                add(SettingsItemType.LIGHT_HOURS)
            }
            add(SettingsItemType.SLEEP_SCHEDULE)
            add(SettingsItemType.SYNC_INTERVAL)
            add(SettingsItemType.GEMINI_KEY)
            add(SettingsItemType.WEATHER)
            add(SettingsItemType.SWITCH_MODE)
            add(SettingsItemType.SIGN_OUT)
            add(SettingsItemType.CLOSE)
        }
    }

    var focusedIndex by remember { mutableIntStateOf(0) }
    var sleepFieldFocus by remember { mutableIntStateOf(0) }
    var lightHourFieldFocus by remember { mutableIntStateOf(0) }
    var isEditingValue by remember { mutableStateOf(false) }
    var switchModeConfirmPending by remember { mutableStateOf(false) }
    var signOutConfirmPending by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(items.size) {
        focusedIndex = focusedIndex.coerceIn(0, items.lastIndex)
    }

    LaunchedEffect(focusedIndex, items) {
        val currentItem = items.getOrElse(focusedIndex) { SettingsItemType.THEME_MODE }
        if (currentItem != SettingsItemType.SWITCH_MODE) {
            switchModeConfirmPending = false
        }
        if (currentItem != SettingsItemType.SIGN_OUT) {
            signOutConfirmPending = false
        }
    }

    LaunchedEffect(switchModeConfirmPending) {
        if (switchModeConfirmPending) {
            delay(3000)
            switchModeConfirmPending = false
        }
    }

    LaunchedEffect(signOutConfirmPending) {
        if (signOutConfirmPending) {
            delay(3000)
            signOutConfirmPending = false
        }
    }

    val focusedItem = items.getOrElse(focusedIndex) { SettingsItemType.THEME_MODE }
    val focusedItemDescription = when (focusedItem) {
        SettingsItemType.THEME_MODE -> "Theme mode selected"
        SettingsItemType.LIGHT_HOURS -> {
            val activeField = if (lightHourFieldFocus == 0) "start time" else "end time"
            if (isEditingValue) "Light hours selected, editing $activeField" else "Light hours selected"
        }
        SettingsItemType.SLEEP_SCHEDULE -> {
            val activeField = if (sleepFieldFocus == 0) "start time" else "end time"
            if (isEditingValue) "Sleep schedule selected, editing $activeField" else "Sleep schedule selected"
        }
        SettingsItemType.SYNC_INTERVAL -> "Sync interval selected"
        SettingsItemType.GEMINI_KEY -> "Gemini API key selected"
        SettingsItemType.WEATHER -> "Weather settings selected"
        SettingsItemType.SWITCH_MODE -> "Switch mode selected"
        SettingsItemType.SIGN_OUT -> "Sign out selected"
        SettingsItemType.CLOSE -> "Close settings selected"
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun cycleThemeMode(forward: Boolean): ThemeMode {
        val modes = ThemeMode.entries
        val currentIndex = modes.indexOf(themeMode).coerceAtLeast(0)
        val nextIndex = if (forward) {
            (currentIndex + 1) % modes.size
        } else {
            (currentIndex - 1 + modes.size) % modes.size
        }
        return modes[nextIndex]
    }

    fun adjustLightHours(forward: Boolean) {
        if (lightHourFieldFocus == 0) {
            val delta = if (forward) 1 else -1
            onThemeSettingsChange(themeMode, (lightStartHour + delta + 24) % 24, lightEndHour)
        } else {
            val delta = if (forward) 1 else -1
            onThemeSettingsChange(themeMode, lightStartHour, (lightEndHour + delta + 24) % 24)
        }
    }

    fun adjustSleepSchedule(forward: Boolean) {
        if (sleepFieldFocus == 0) {
            val delta = if (forward) 1 else -1
            val newHour = (sleepStartHour + delta + 24) % 24
            onSleepScheduleChange(newHour, sleepEndHour)
        } else {
            val delta = if (forward) 1 else -1
            val newHour = (sleepEndHour + delta + 24) % 24
            onSleepScheduleChange(sleepStartHour, newHour)
        }
    }

    fun adjustSyncInterval(forward: Boolean) {
        val currentIdx = SYNC_INTERVAL_OPTIONS.indexOf(syncIntervalMinutes).coerceAtLeast(0)
        if (forward) {
            if (currentIdx < SYNC_INTERVAL_OPTIONS.size - 1) {
                onSyncIntervalChange(SYNC_INTERVAL_OPTIONS[currentIdx + 1])
            }
        } else if (currentIdx > 0) {
            onSyncIntervalChange(SYNC_INTERVAL_OPTIONS[currentIdx - 1])
        }
    }

    fun triggerSwitchMode() {
        if (switchModeConfirmPending) {
            switchModeConfirmPending = false
            onSwitchMode()
        } else {
            switchModeConfirmPending = true
            signOutConfirmPending = false
        }
    }

    fun triggerSignOut() {
        if (signOutConfirmPending) {
            signOutConfirmPending = false
            onSignOut()
        } else {
            signOutConfirmPending = true
            switchModeConfirmPending = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBlack.copy(alpha = 0.9f))
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionLeft -> {
                        if (isEditingValue && (focusedItem == SettingsItemType.LIGHT_HOURS || focusedItem == SettingsItemType.SLEEP_SCHEDULE)) {
                            when (focusedItem) {
                                SettingsItemType.LIGHT_HOURS -> adjustLightHours(forward = false)
                                SettingsItemType.SLEEP_SCHEDULE -> adjustSleepSchedule(forward = false)
                                else -> Unit
                            }
                        } else if (focusedIndex > 0) {
                            focusedIndex--
                            isEditingValue = false
                        }
                        true
                    }

                    Key.DirectionDown, Key.DirectionRight -> {
                        if (isEditingValue && (focusedItem == SettingsItemType.LIGHT_HOURS || focusedItem == SettingsItemType.SLEEP_SCHEDULE)) {
                            when (focusedItem) {
                                SettingsItemType.LIGHT_HOURS -> adjustLightHours(forward = true)
                                SettingsItemType.SLEEP_SCHEDULE -> adjustSleepSchedule(forward = true)
                                else -> Unit
                            }
                        } else if (focusedIndex < items.lastIndex) {
                            focusedIndex++
                            isEditingValue = false
                        }
                        true
                    }

                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        when (focusedItem) {
                            SettingsItemType.THEME_MODE -> {
                                onThemeSettingsChange(cycleThemeMode(forward = true), lightStartHour, lightEndHour)
                            }

                            SettingsItemType.LIGHT_HOURS -> {
                                if (!isEditingValue) {
                                    isEditingValue = true
                                } else if (lightHourFieldFocus == 0) {
                                    lightHourFieldFocus = 1
                                } else {
                                    lightHourFieldFocus = 0
                                    isEditingValue = false
                                }
                            }

                            SettingsItemType.SLEEP_SCHEDULE -> {
                                if (!isEditingValue) {
                                    isEditingValue = true
                                } else if (sleepFieldFocus == 0) {
                                    sleepFieldFocus = 1
                                } else {
                                    sleepFieldFocus = 0
                                    isEditingValue = false
                                }
                            }

                            SettingsItemType.SYNC_INTERVAL -> adjustSyncInterval(forward = true)
                            SettingsItemType.GEMINI_KEY -> { /* touch-only: text field + buttons */ }
                            SettingsItemType.WEATHER -> { /* touch-only: text fields + buttons */ }
                            SettingsItemType.SWITCH_MODE -> triggerSwitchMode()
                            SettingsItemType.SIGN_OUT -> triggerSignOut()
                            SettingsItemType.CLOSE -> onDismiss()
                        }
                        true
                    }

                    else -> false
                }
            }
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        val cardShape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .clip(cardShape)
                .background(colors.surfaceElevated)
                .border(1.dp, colors.borderColor, cardShape)
                .semantics(mergeDescendants = true) {
                    dialog()
                    contentDescription = "Settings"
                    stateDescription = focusedItemDescription
                }
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSectionHeader("APPEARANCE")

            SettingsItem(
                label = "Theme",
                description = "Auto follows ambient light",
                isSelected = focusedItem == SettingsItemType.THEME_MODE,
                onClick = {
                    onThemeSettingsChange(cycleThemeMode(forward = true), lightStartHour, lightEndHour)
                }
            ) {
                SettingsValuePill(
                    text = when (themeMode) {
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.AUTO -> "Auto"
                        ThemeMode.LIGHT -> "Light"
                    }
                )
            }

            if (themeMode == ThemeMode.AUTO) {
                SettingsDivider()

                SettingsItem(
                    label = "Light Hours",
                    isSelected = focusedItem == SettingsItemType.LIGHT_HOURS
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimeDisplay(
                            hour = lightStartHour,
                            isActive = focusedItem == SettingsItemType.LIGHT_HOURS && lightHourFieldFocus == 0,
                            onClick = {
                                focusedIndex = items.indexOf(SettingsItemType.LIGHT_HOURS).coerceAtLeast(0)
                                lightHourFieldFocus = 0
                                isEditingValue = true
                                adjustLightHours(forward = true)
                            }
                        )
                        Text(
                            text = " \u2192 ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )
                        TimeDisplay(
                            hour = lightEndHour,
                            isActive = focusedItem == SettingsItemType.LIGHT_HOURS && lightHourFieldFocus == 1,
                            onClick = {
                                focusedIndex = items.indexOf(SettingsItemType.LIGHT_HOURS).coerceAtLeast(0)
                                lightHourFieldFocus = 1
                                isEditingValue = true
                                adjustLightHours(forward = true)
                            }
                        )
                    }
                }
            }

            SettingsDivider()

            SettingsSectionHeader("SCHEDULE")

            SettingsItem(
                label = "Sleep",
                description = "Screen goes dark",
                isSelected = focusedItem == SettingsItemType.SLEEP_SCHEDULE
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeDisplay(
                        hour = sleepStartHour,
                        isActive = focusedItem == SettingsItemType.SLEEP_SCHEDULE && sleepFieldFocus == 0,
                        onClick = {
                            focusedIndex = items.indexOf(SettingsItemType.SLEEP_SCHEDULE).coerceAtLeast(0)
                            sleepFieldFocus = 0
                            isEditingValue = true
                            adjustSleepSchedule(forward = true)
                        }
                    )
                    Text(
                        text = " \u2192 ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                    TimeDisplay(
                        hour = sleepEndHour,
                        isActive = focusedItem == SettingsItemType.SLEEP_SCHEDULE && sleepFieldFocus == 1,
                        onClick = {
                            focusedIndex = items.indexOf(SettingsItemType.SLEEP_SCHEDULE).coerceAtLeast(0)
                            sleepFieldFocus = 1
                            isEditingValue = true
                            adjustSleepSchedule(forward = true)
                        }
                    )
                }
            }

            SettingsDivider()

            SettingsSectionHeader("SYNC")

            SettingsItem(
                label = "Sync Interval",
                description = "Pull from Google Tasks",
                isSelected = focusedItem == SettingsItemType.SYNC_INTERVAL,
                onClick = { adjustSyncInterval(forward = true) }
            ) {
                SettingsValuePill(text = "$syncIntervalMinutes min")
            }

            SettingsDivider()

            SettingsSectionHeader("INTELLIGENCE")

            // Gemini API Key
            SettingsApiKeyItem(
                label = "Gemini API Key",
                description = "Powers voice input parsing",
                isSelected = focusedItem == SettingsItemType.GEMINI_KEY,
                keyPresent = geminiKeyPresent,
                isValidating = isValidatingGeminiKey,
                error = geminiKeyError,
                placeholder = if (geminiKeyPresent) "Key is active" else "Paste Gemini API key",
                onSave = onSaveGeminiKey,
                onClear = onClearGeminiKey
            )

            SettingsDivider()

            SettingsSectionHeader("WEATHER")

            // Weather settings
            SettingsWeatherItem(
                isSelected = focusedItem == SettingsItemType.WEATHER,
                weatherLocation = weatherLocation,
                weatherApiKeyPresent = weatherApiKeyPresent,
                onSaveLocation = onSaveWeatherLocation,
                onSaveApiKey = onSaveWeatherApiKey,
                onClearApiKey = onClearWeatherApiKey,
                onSearchCities = onSearchCities,
                scrollState = scrollState
            )

            SettingsDivider()

            SettingsSectionHeader("ACCOUNT")

            SettingsItem(
                label = "Switch Mode",
                isSelected = focusedItem == SettingsItemType.SWITCH_MODE,
                onClick = {
                    focusedIndex = items.indexOf(SettingsItemType.SWITCH_MODE).coerceAtLeast(0)
                    isEditingValue = false
                    triggerSwitchMode()
                }
            ) {
                AnimatedVisibility(visible = switchModeConfirmPending) {
                    Text(
                        text = "Click again to confirm",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.urgencyDueToday
                    )
                }
            }

            SettingsDivider()

            SettingsItem(
                label = "Sign Out",
                isSelected = focusedItem == SettingsItemType.SIGN_OUT,
                labelColor = colors.urgencyOverdue,
                onClick = {
                    focusedIndex = items.indexOf(SettingsItemType.SIGN_OUT).coerceAtLeast(0)
                    isEditingValue = false
                    triggerSignOut()
                }
            ) {
                AnimatedVisibility(visible = signOutConfirmPending) {
                    Text(
                        text = "Click again to confirm",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.urgencyDueToday
                    )
                }
            }

            SettingsDivider()

            SettingsItem(
                label = "Close Settings",
                isSelected = focusedItem == SettingsItemType.CLOSE,
                onClick = {
                    focusedIndex = items.indexOf(SettingsItemType.CLOSE).coerceAtLeast(0)
                    onDismiss()
                }
            ) {}

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Encoder: Up/Down navigate. Click to select. Double-click tasks for actions.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted
            )
        }
    }
}

@Composable
private fun SettingsItem(
    label: String,
    isSelected: Boolean,
    description: String? = null,
    labelColor: Color? = null,
    onClick: (() -> Unit)? = null,
    value: @Composable () -> Unit
) {
    val colors = LocalWallColors.current
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.7f,
        animationSpec = tween(durationMillis = WallAnimations.SHORT),
        label = "settingsItemAlpha"
    )

    val baseLabelColor = labelColor ?: colors.textSecondary
    val activeLabelColor = if (isSelected && labelColor == null) colors.textPrimary else baseLabelColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = activeLabelColor.copy(alpha = alpha)
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textDisabled,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        value()
    }
}

@Composable
private fun TimeDisplay(
    hour: Int,
    isActive: Boolean,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalWallColors.current
    val formatted = String.format(Locale.getDefault(), "%d:00", hour)
    Text(
        text = formatted,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isActive) colors.accentPrimary else colors.textSecondary,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@Composable
private fun SettingsDivider() {
    val colors = LocalWallColors.current
    HorizontalDivider(
        color = colors.dividerColor,
        thickness = 1.dp
    )
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val colors = LocalWallColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            fontSize = 9.sp
        ),
        color = colors.textMuted,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsValuePill(text: String) {
    val colors = LocalWallColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.accentPrimary,
        modifier = Modifier
            .background(colors.accentPrimary.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun SettingsApiKeyItem(
    label: String,
    description: String,
    isSelected: Boolean,
    keyPresent: Boolean,
    isValidating: Boolean,
    error: String?,
    placeholder: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    val colors = LocalWallColors.current
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.7f,
        animationSpec = tween(durationMillis = WallAnimations.SHORT),
        label = "apiKeyItemAlpha"
    )
    var keyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = (if (isSelected) colors.textPrimary else colors.textSecondary).copy(alpha = alpha)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textDisabled,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            SettingsValuePill(text = if (keyPresent) "Active" else "Not set")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            placeholder = {
                Text(placeholder, color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.accentPrimary,
                unfocusedBorderColor = colors.borderColor,
                cursorColor = colors.accentPrimary
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsActionButton(
                text = if (isValidating) "Checking..." else "Save",
                enabled = !isValidating && keyInput.isNotBlank(),
                onClick = { onSave(keyInput) }
            )
            if (keyPresent) {
                SettingsActionButton(
                    text = "Remove",
                    enabled = true,
                    onClick = onClear,
                    isDestructive = true
                )
            }
        }

        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = colors.urgencyOverdue
            )
        }
    }
}

@Composable
private fun SettingsWeatherItem(
    isSelected: Boolean,
    weatherLocation: String,
    weatherApiKeyPresent: Boolean,
    onSaveLocation: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSearchCities: (suspend (String) -> List<String>),
    scrollState: ScrollState
) {
    val colors = LocalWallColors.current
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.7f,
        animationSpec = tween(durationMillis = WallAnimations.SHORT),
        label = "weatherItemAlpha"
    )
    var locationInput by remember(weatherLocation) { mutableStateOf(weatherLocation) }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Weather Tints",
                    style = MaterialTheme.typography.bodyMedium,
                    color = (if (isSelected) colors.textPrimary else colors.textSecondary).copy(alpha = alpha)
                )
                Text(
                    text = "Subtle weather-based tints on calendar",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textDisabled,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            SettingsValuePill(
                text = if (weatherApiKeyPresent && weatherLocation.isNotBlank()) "Active" else "Not set"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Location field
        OutlinedTextField(
            value = locationInput,
            onValueChange = { input ->
                locationInput = input
                locationSaved = false
                if (weatherApiKeyPresent && input.length >= 2) {
                    searchJob?.cancel()
                    searchJob = searchScope.launch {
                        delay(300)
                        val results = onSearchCities(input)
                        citySuggestions = results
                        showSuggestions = results.isNotEmpty()
                    }
                } else {
                    citySuggestions = emptyList()
                    showSuggestions = false
                }
            },
            placeholder = {
                Text("City, Country (e.g. London, UK)", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.accentPrimary,
                unfocusedBorderColor = colors.borderColor,
                cursorColor = colors.accentPrimary
            )
        )

        // Inline city suggestions (no popup — avoids keyboard dismissal on API 23)
        AnimatedVisibility(visible = showSuggestions && citySuggestions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.borderColor, RoundedCornerShape(8.dp))
            ) {
                citySuggestions.forEachIndexed { index, suggestion ->
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                locationInput = suggestion
                                showSuggestions = false
                                locationSaved = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                    if (index < citySuggestions.lastIndex) {
                        HorizontalDivider(color = colors.borderColor.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Extra space so suggestions can be scrolled above the keyboard
        AnimatedVisibility(visible = showSuggestions && citySuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(280.dp))
        }

        // Auto-scroll after spacer expands the scrollable area
        LaunchedEffect(showSuggestions, citySuggestions.size) {
            if (showSuggestions && citySuggestions.isNotEmpty()) {
                delay(100) // let AnimatedVisibility expand first
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        SettingsActionButton(
            text = if (locationSaved) "Saved!" else "Save Location",
            enabled = locationInput.isNotBlank() && !locationSaved,
            onClick = {
                onSaveLocation(locationInput)
                locationSaved = true
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Weather API key field
        OutlinedTextField(
            value = weatherKeyInput,
            onValueChange = { weatherKeyInput = it; keySaved = false },
            placeholder = {
                Text(
                    if (weatherApiKeyPresent) "Key is active" else "OpenWeatherMap API Key",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.accentPrimary,
                unfocusedBorderColor = colors.borderColor,
                cursorColor = colors.accentPrimary
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsActionButton(
                text = if (keySaved) "Saved!" else "Save Key",
                enabled = weatherKeyInput.isNotBlank() && !keySaved,
                onClick = {
                    onSaveApiKey(weatherKeyInput)
                    keySaved = true
                }
            )
            if (weatherApiKeyPresent) {
                SettingsActionButton(
                    text = "Remove",
                    enabled = true,
                    onClick = onClearApiKey,
                    isDestructive = true
                )
            }
        }

        Text(
            text = "Free key at openweathermap.org/api",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = colors.textDisabled,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SettingsActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val colors = LocalWallColors.current
    val bgColor = when {
        !enabled -> colors.borderColor.copy(alpha = 0.3f)
        isDestructive -> colors.urgencyOverdue.copy(alpha = 0.15f)
        else -> colors.accentPrimary.copy(alpha = 0.12f)
    }
    val textColor = when {
        !enabled -> colors.textDisabled
        isDestructive -> colors.urgencyOverdue
        else -> colors.accentPrimary
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = Modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, widthDp = 800, heightDp = 480)
@Composable
private fun SettingsPanelPreview() {
    LedgerTheme {
        SettingsPanel(
            themeMode = ThemeMode.AUTO,
            lightStartHour = 8,
            lightEndHour = 19,
            sleepStartHour = 23,
            sleepEndHour = 7,
            syncIntervalMinutes = 5,
            onThemeSettingsChange = { _, _, _ -> },
            onSleepScheduleChange = { _, _ -> },
            onSyncIntervalChange = {},
            onSwitchMode = {},
            onSignOut = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, widthDp = 800, heightDp = 480)
@Composable
private fun SettingsPanelFocusedSyncPreview() {
    LedgerTheme {
        SettingsPanel(
            themeMode = ThemeMode.DARK,
            lightStartHour = 8,
            lightEndHour = 19,
            sleepStartHour = 22,
            sleepEndHour = 6,
            syncIntervalMinutes = 15,
            onThemeSettingsChange = { _, _, _ -> },
            onSleepScheduleChange = { _, _ -> },
            onSyncIntervalChange = {},
            onSwitchMode = {},
            onSignOut = {},
            onDismiss = {}
        )
    }
}
