# UI Upgrade Implementation Plan — Soft Graphite Refresh

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the entire visual identity — palette, font, surface treatment, and 10 feature upgrades — across all screens.

**Architecture:** Theme-first approach. Update Color.kt, WallColors.kt, Type.kt, and Theme.kt first (Task 1). All downstream UI files consume colors via `LocalWallColors.current` and typography via `MaterialTheme.typography`, so the palette/font change propagates automatically. Then apply surface treatment and feature upgrades per-screen.

**Tech Stack:** Jetpack Compose, Material3, Google Fonts (downloadable), Canvas API for animations

**Spec:** `docs/superpowers/specs/2026-03-12-ui-upgrade-design.md`

---

## Chunk 1: Design System Foundation

### Task 1: Update Color Palette — Soft Graphite

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/todowallapp/ui/theme/WallColors.kt`
- Modify: `app/src/main/java/com/example/todowallapp/ui/theme/Theme.kt`

- [ ] **Step 1: Replace dark mode surface colors in Color.kt**

Change lines 6–10:
```kotlin
// SURFACES - Soft Graphite neutral palette (no blue/warm bias)
val SurfaceBackground = Color(0xFF121212)     // True neutral base
val SurfaceCard = Color(0xFF1C1C1C)           // Card surface (focus fill)
val SurfaceElevated = Color(0xFF262626)       // Elevated overlays
val SurfaceBlack = Color(0xFF0A0A0A)          // Sleep mode / deepest
val SurfaceExpanded = Color(0xFF1E1E1E)       // Nested/expanded (was SurfaceExpanded)
```

- [ ] **Step 2: Replace dark mode text colors in Color.kt**

Change lines 13–17:
```kotlin
// TEXT - Neutral gray hierarchy
val TextPrimary = Color(0xFFEEEEEE)           // Primary text
val TextSecondary = Color(0xFFBDBDBD)         // Secondary text
val TextMuted = Color(0xFF757575)             // Muted text
val TextDisabled = Color(0xFF555555)          // Disabled/completed text
val AmbientText = Color.White                 // Ambient mode (unchanged)
```

- [ ] **Step 3: Replace dark mode accent colors in Color.kt**

Change lines 20–22:
```kotlin
// ACCENTS - Mint Teal primary + Soft Rose secondary
val AccentPrimary = Color(0xFF80CBC4)         // Teal focus/action accent
val AccentSubtle = Color(0xFFB2DFDB)          // Subtle teal for backgrounds
val AccentWarm = Color(0xFFCF8E8E)            // Soft rose for completed/structural warmth
```

- [ ] **Step 4: Replace dark mode state and border colors in Color.kt**

Change lines 31–37:
```kotlin
// STATES
val StateCompleted = AccentPrimary.copy(alpha = 0.4f)
val StateUrgent = UrgencyOverdue
val StateSubtle = AccentPrimary.copy(alpha = 0.15f)

// STRUCTURE - Neutral borders for Soft Graphite
val BorderColor = Color(0xFF1E1E1E)           // Card border (visible, not 6% white)
val DividerColor = Color(0xFF1A1A1A)          // Subtle divider
```

- [ ] **Step 5: Update light mode surfaces for Soft Graphite**

Change lines 48–53 (light mode surfaces should also shift to neutral warm linen):
```kotlin
// LIGHT MODE SURFACES - Warm linen (neutral, not blue)
val LightSurfaceBackground = Color(0xFFFAFAF8)
val LightSurfaceCard = Color(0xFFF5F4F0)
val LightSurfaceElevated = Color(0xFFEDECE8)
val LightSurfaceExpanded = Color(0xFFF0EFEB)
val LightSurfaceBlack = Color(0xFFE8E7E3)
```

- [ ] **Step 6: Update light mode accents for teal/rose**

Change lines 63–65:
```kotlin
// LIGHT MODE ACCENTS - Teal/rose in daylight
val LightAccentPrimary = Color(0xFF00897B)    // Deeper teal for light bg contrast
val LightAccentSubtle = Color(0xFFB2DFDB)
val LightAccentWarm = Color(0xFFB85555)       // Richer rose for light mode
```

- [ ] **Step 7: Update connectivity colors in Color.kt**

Change lines 45–46:
```kotlin
// CONNECTIVITY
val ConnectivityOnline = AccentPrimary.copy(alpha = 0.6f)
val ConnectivityOffline = AccentWarm               // Rose for offline (was urgency)
```

- [ ] **Step 8: Add new tokens to WallColors data class**

Add `accentDeep`, `textFaint`, `borderFocused` to the data class in WallColors.kt. Insert after line 29 (before `isDark`):
```kotlin
    val accentDeep: Color,
    val textFaint: Color,
    val borderFocused: Color,
```

- [ ] **Step 9: Update darkWallColors() with new tokens**

Add to `darkWallColors()` before `isDark = true`:
```kotlin
    accentDeep = Color(0xFF4DB6AC),
    textFaint = Color(0xFF333333),
    borderFocused = Color(0x3380CBC4),  // rgba(128,203,196,0.2)
```

- [ ] **Step 10: Update lightWallColors() with new tokens**

Add to `lightWallColors()` before `isDark = false`:
```kotlin
    accentDeep = Color(0xFF00796B),
    textFaint = Color(0xFFCCCCCC),
    borderFocused = Color(0x3300897B),
```

- [ ] **Step 11: Update Theme.kt DarkWallDisplayColorScheme**

Update `DarkWallDisplayColorScheme` to use new values:
```kotlin
private val DarkWallDisplayColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = SurfaceBlack,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = TextPrimary,
    secondary = AccentSubtle,
    onSecondary = SurfaceBlack,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentWarm,
    onTertiary = SurfaceBlack,
    tertiaryContainer = SurfaceElevated,
    onTertiaryContainer = TextPrimary,
    background = SurfaceBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceExpanded,
    onSurfaceVariant = TextSecondary,
    surfaceTint = AccentPrimary,
    inverseSurface = Color.White,
    inverseOnSurface = SurfaceBlack,
    inversePrimary = AccentPrimary,
    error = UrgencyOverdue,
    onError = TextPrimary,
    errorContainer = StateSubtle,
    onErrorContainer = TextPrimary,
    outline = DividerColor,
    outlineVariant = BorderColor,
    scrim = Color.Black.copy(alpha = 0.5f)
)
```

- [ ] **Step 12: Reduce CardCornerRadius in WallShapes**

In Theme.kt, change `CardCornerRadius = 16` to `CardCornerRadius = 10` (spec says 10dp for tighter feel).

- [ ] **Step 13: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 14: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/theme/Color.kt app/src/main/java/com/example/todowallapp/ui/theme/WallColors.kt app/src/main/java/com/example/todowallapp/ui/theme/Theme.kt
git commit -m "theme: migrate palette to Soft Graphite + Mint Teal + Soft Rose"
```

---

### Task 2: Swap Font to Plus Jakarta Sans

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/theme/Type.kt`

- [ ] **Step 1: Replace Inter with Plus Jakarta Sans**

Change lines 18–26:
```kotlin
private val jakartaFont = GoogleFont("Plus Jakarta Sans")

private val JakartaFontFamily = FontFamily(
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.Bold),
)
```

- [ ] **Step 2: Replace all InterFontFamily references with JakartaFontFamily**

Find-replace all `InterFontFamily` → `JakartaFontFamily` in Type.kt (13 occurrences across all TextStyle definitions).

- [ ] **Step 3: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/theme/Type.kt
git commit -m "theme: swap Inter for Plus Jakarta Sans"
```

---

## Chunk 2: Wall Display — Surface Treatment & Features

### Task 3: Hybrid Surface Treatment on TaskItem

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/TaskItem.kt`

- [ ] **Step 1: Update card background and border for borderline resting state**

In TaskItem.kt, find the card Surface/Box composable (around lines 160–260). Replace the current solid background + elevation approach with:

For the **unselected** state:
- `background = Color.Transparent`
- `border = BorderStroke(1.dp, colors.borderColor)` (where `colors = LocalWallColors.current`)
- No shadow/elevation

For the **selected/focused** state:
- `background = colors.surfaceCard`
- `border = BorderStroke(1.dp, colors.borderFocused)`
- Shadow: use `Modifier.shadow(elevation = 24.dp, shape = RoundedCornerShape(10.dp), spotColor = colors.accentPrimary.copy(alpha = 0.3f), ambientColor = Color.Black.copy(alpha = 0.3f))`
- `graphicsLayer { translationY = -1.dp.toPx() }` for subtle lift

For **completed** state:
- `opacity = 0.35f`
- `border = BorderStroke(1.dp, colors.dividerColor)` (more faded border)
- Checkbox fill: `colors.accentWarm` (was `accentPrimary` in some paths)

- [ ] **Step 2: Update the glow effect to use teal composite shadow**

Replace the current single `Modifier.shadow(elevation = 24.dp, spotColor = accentPrimary.copy(alpha = 0.4f))` with the three-layer approach. In Compose, this is best done as a single `drawBehind` modifier:

```kotlin
if (isSelected) {
    Modifier.drawBehind {
        // Layer 1: Teal glow
        drawRoundRect(
            color = colors.accentPrimary.copy(alpha = 0.18f),
            cornerRadius = CornerRadius(10.dp.toPx()),
            size = Size(size.width + 28.dp.toPx(), size.height + 28.dp.toPx()),
            topLeft = Offset(-14.dp.toPx(), -14.dp.toPx()),
            style = Fill,
            blendMode = BlendMode.Screen
        )
    }
}
```

Note: The exact implementation may need adjustment. The key is replacing the single `shadow()` with a teal glow that looks like a halo, not a traditional drop shadow. Keep the existing `shadow()` for the physical depth, but change `spotColor` to `accentPrimary.copy(alpha = 0.18f)`.

- [ ] **Step 3: Verify the card border radius uses 10.dp**

Ensure `RoundedCornerShape(10.dp)` (not 12 or 16) is used for task card shapes, matching the spec's tighter radius.

- [ ] **Step 4: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/TaskItem.kt
git commit -m "ui: hybrid surface treatment — borderline rest, elevated focus"
```

---

### Task 4: Folder Headers + Progress Bars + Urgency Glow

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt`

- [ ] **Step 1: Update FolderSection header styling**

In `FolderSection()` (around line 1012–1038), update the folder title text style:
- Font size: use `labelLarge` style (16sp Medium) instead of `headlineMedium`/`headlineSmall`
- Add `textTransform` via `.uppercase()` on the text
- Letter spacing: 0.8sp
- Color: `colors.textSecondary`
- Task count: 10sp, `colors.textMuted`, no uppercase
- Add expand indicator: small 14.dp rounded rect with `accentSurface` background containing ▾ or ▸ arrow

- [ ] **Step 2: Add progress bar below folder header**

After the folder header Row, add:
```kotlin
// List progress bar
val completedCount = model.completedCount
val totalCount = model.pendingCount + completedCount
val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(2.dp)
        .clip(RoundedCornerShape(1.dp))
        .background(colors.borderColor)
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(fraction = animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(WallAnimations.MEDIUM)
            ).value)
            .background(colors.accentSubtle.copy(alpha = 0.3f))
    )
}
```

Note: `model.completedCount` may need to be added to `FolderSectionModel` if it doesn't already exist. Check the model class and add a `completedCount: Int` field if missing, computed from the tasks list.

- [ ] **Step 3: Add folder urgency glow on collapsed folders with overdue tasks**

When the folder is collapsed and contains overdue tasks, add a warm glow to the header:
```kotlin
val hasOverdue = !isExpanded && model.tasks.any { it.urgency == TaskUrgency.OVERDUE }

Modifier.then(
    if (hasOverdue) {
        Modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(32.dp),
            spotColor = colors.urgencyOverdue.copy(alpha = 0.10f),
            ambientColor = Color.Transparent
        )
    } else Modifier
)
```

- [ ] **Step 4: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt
git commit -m "ui: folder headers with progress bars and urgency glow"
```

---

### Task 5: Focus Breadcrumb

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt`

- [ ] **Step 1: Add breadcrumb state tracking**

Near the top of the TaskWallScreen composable, add:
```kotlin
val breadcrumbVisible = remember { Animatable(0f) }
val lastInteractionTime = remember { mutableLongStateOf(0L) }
```

When `focusedIndex` changes (encoder input), set `lastInteractionTime` to current millis and animate breadcrumb alpha to 1f. Launch an effect that hides it after 3 seconds idle:
```kotlin
LaunchedEffect(focusedIndex) {
    lastInteractionTime.longValue = System.currentTimeMillis()
    breadcrumbVisible.animateTo(1f, tween(WallAnimations.SHORT))
    delay(3000)
    breadcrumbVisible.animateTo(0f, tween(WallAnimations.SHORT))
}
```

- [ ] **Step 2: Render breadcrumb text above the clock**

Before the ClockHeader, add:
```kotlin
val breadcrumbText = buildAnnotatedString {
    withStyle(SpanStyle(color = colors.accentPrimary)) {
        append(currentFolderName)
    }
    append(" › ")
    append(currentTaskTitle)
}

AnimatedVisibility(visible = breadcrumbVisible.value > 0f) {
    Text(
        text = breadcrumbText,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            letterSpacing = 0.3.sp
        ),
        color = colors.textMuted,
        modifier = Modifier
            .alpha(breadcrumbVisible.value)
            .padding(bottom = 4.dp)
    )
}
```

Note: `currentFolderName` and `currentTaskTitle` need to be derived from the current focus state. Check how `focusedIndex` maps to the displayed items and extract the folder + task name.

- [ ] **Step 3: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt
git commit -m "ui: focus breadcrumb with auto-hide"
```

---

### Task 6: Completion Ripple Animation

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/TaskItem.kt`

- [ ] **Step 1: Add ripple state and Canvas-based effect**

Add a `completionRippleProgress` Animatable that triggers when a task transitions to completed:

```kotlin
val rippleProgress = remember { Animatable(0f) }

LaunchedEffect(task.isCompleted) {
    if (task.isCompleted) {
        rippleProgress.snapTo(0f)
        rippleProgress.animateTo(1f, tween(WallAnimations.SHORT))
    }
}
```

In the card's `drawBehind` or as an overlay Canvas:
```kotlin
if (rippleProgress.value > 0f && rippleProgress.value < 1f) {
    Canvas(modifier = Modifier.matchParentSize()) {
        val checkboxCenter = Offset(24.dp.toPx(), size.height / 2)
        val maxRadius = size.width
        val currentRadius = maxRadius * rippleProgress.value
        val rippleAlpha = (1f - rippleProgress.value) * 0.15f

        // Teal-to-rose color interpolation
        val rippleColor = lerp(
            colors.accentPrimary,
            colors.accentWarm,
            rippleProgress.value
        )

        drawCircle(
            color = rippleColor.copy(alpha = rippleAlpha),
            radius = currentRadius,
            center = checkboxCenter
        )
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/TaskItem.kt
git commit -m "ui: completion ripple — teal to rose radial effect"
```

---

## Chunk 3: Ambient, Voice, Calendar, Phone, Settings

### Task 7: Quiet Mode Clock + Staged Wake Reveal

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt`

- [ ] **Step 1: Add large faint clock to QuietModeContent**

In `QuietModeContent()` (around line 1270), add a large time display at the top:
```kotlin
val currentTime = remember { mutableStateOf(LocalTime.now()) }
LaunchedEffect(Unit) {
    while (true) {
        currentTime.value = LocalTime.now()
        delay(1000)
    }
}

Text(
    text = currentTime.value.format(DateTimeFormatter.ofPattern("HH:mm")),
    style = MaterialTheme.typography.displaySmall.copy(
        fontWeight = FontWeight.Light,
        letterSpacing = 2.sp
    ),
    color = colors.textFaint,
    modifier = Modifier.padding(bottom = 20.dp)
)
```

- [ ] **Step 2: Add teal left-border accent to whispered tasks**

For each quiet mode task item, add:
```kotlin
Box(
    modifier = Modifier
        .padding(start = 12.dp)
        .drawBehind {
            drawLine(
                color = colors.accentPrimary.copy(alpha = 0.08f),
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
        .padding(start = 10.dp)
)
```

- [ ] **Step 3: Add urgency text to quiet mode overdue items**

Next to overdue task text in quiet mode, append faint urgency label:
```kotlin
if (task.urgency == TaskUrgency.OVERDUE) {
    Text(
        text = "overdue",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
        color = colors.urgencyOverdue.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 6.dp)
    )
}
```

- [ ] **Step 4: Add staged wake reveal animation**

When transitioning from quiet mode to active mode (ambient tier changes), stagger the appearance of tasks:

Find where the active task list renders (the main LazyColumn or Column). Wrap each item with a staggered entrance:

```kotlin
val enterDelay = index * WallAnimations.STAGGER_ENTER
val itemAlpha = remember { Animatable(if (wasInQuietMode) 0f else 1f) }

LaunchedEffect(wasInQuietMode) {
    if (wasInQuietMode) {
        delay(enterDelay.toLong())
        itemAlpha.animateTo(1f, tween(WallAnimations.SHORT))
    }
}
```

The `wasInQuietMode` flag should be derived from a previous ambient tier state. Add a `remember { mutableStateOf(false) }` that tracks the previous quiet mode state.

- [ ] **Step 5: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt
git commit -m "ui: quiet mode clock, urgency whispers, staged wake reveal"
```

---

### Task 8: Radial Voice Pulse Visualizer

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/WaveformVisualizer.kt` (rename conceptually, keep file)

- [ ] **Step 1: Replace bar waveform with radial pulse implementation**

Replace the body of `WaveformVisualizer` composable with radial pulse rings:

```kotlin
@Composable
fun WaveformVisualizer(
    amplitudeLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "voice-pulse")

    // Breathing animation for center dot
    val breathe by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = EaseInOut),
            RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Three pulse rings, staggered
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseOut)),
        label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = EaseOut),
            initialStartOffset = StartOffset(600)
        ),
        label = "ring2"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = EaseOut),
            initialStartOffset = StartOffset(1200)
        ),
        label = "ring3"
    )

    val activeAlpha by animateFloatAsState(
        if (isActive) 1f else 0f,
        tween(WallAnimations.SHORT),
        label = "active"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(200.dp).alpha(activeAlpha)
    ) {
        // Pulse rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = 24.dp.toPx()

            for ((scale, ringAlpha) in listOf(
                ring1 to (1f - (ring1 - 0.8f) / 1.7f) * 0.6f,
                ring2 to (1f - (ring2 - 0.8f) / 1.7f) * 0.6f,
                ring3 to (1f - (ring3 - 0.8f) / 1.7f) * 0.6f,
            )) {
                drawCircle(
                    color = colors.accentPrimary.copy(alpha = ringAlpha.coerceAtLeast(0f) * 0.1f),
                    radius = baseRadius * scale,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Center dot background
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.accentPrimary.copy(alpha = 0.25f),
                        colors.accentPrimary.copy(alpha = 0.08f)
                    ),
                    center = center,
                    radius = 24.dp.toPx()
                ),
                radius = 24.dp.toPx(),
                center = center
            )

            // Center dot border
            drawCircle(
                color = colors.accentPrimary.copy(alpha = 0.2f),
                radius = 24.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Inner breathing dot
            drawCircle(
                color = colors.accentPrimary.copy(alpha = 0.4f * breathe / 1.2f),
                radius = 8.dp.toPx() * breathe,
                center = center
            )
        }

        // "Listening" label below
        Text(
            text = "Listening",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 0.5.sp
            ),
            color = colors.textMuted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/WaveformVisualizer.kt
git commit -m "ui: radial voice pulse — replace bar waveform with concentric rings"
```

---

### Task 9: Calendar Urgency Dots

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/CalendarMonthView.kt`

- [ ] **Step 1: Update eventDotColor function**

Find `eventDotColor()` (around line 203–209) and update:
```kotlin
private fun eventDotColor(event: CalendarEvent, colors: WallColors): Color {
    return when {
        event.isPromotedTask && event.isOverdue -> colors.urgencyOverdue.copy(alpha = 0.6f)
        event.isPromotedTask -> colors.accentPrimary.copy(alpha = 0.6f)
        event.isAllDay -> colors.accentPrimary.copy(alpha = 0.6f)
        else -> colors.textSecondary.copy(alpha = 0.5f)
    }
}
```

Note: Check if `CalendarEvent` has an `isOverdue` property. If not, it may need to be derived from the event's date vs today. Check the `CalendarEvent` data class.

- [ ] **Step 2: Increase dot size from 5.dp to 6.dp**

Find the dot `Box` or `Surface` size (around line 175–197) and change from `5.dp` to `6.dp`.

- [ ] **Step 3: Update today cell styling**

Find the today cell highlight and update to use borderline treatment:
- Border: `BorderStroke(1.dp, colors.borderFocused)`
- Background: `colors.accentPrimary.copy(alpha = 0.04f)`
- Text color: `colors.accentPrimary`

- [ ] **Step 4: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/CalendarMonthView.kt
git commit -m "ui: calendar urgency dots + borderline today cell"
```

---

### Task 10: Phone Companion — Mic-First + Surface Alignment

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/PhoneCaptureHub.kt`
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/PhoneTaskItem.kt`

- [ ] **Step 1: Update PhoneCaptureHub layout — mic prominent, camera secondary**

Find the capture buttons layout. Make the mic button larger (40.dp) with teal accent:
- Mic: `size = 40.dp`, background `colors.accentPrimary.copy(alpha = 0.1f)`, border `colors.borderFocused`, inner dot `colors.accentPrimary`
- Camera: `size = 36.dp`, background `Color.White.copy(alpha = 0.03f)`, border `colors.borderColor`, inner square icon in `colors.textMuted`

- [ ] **Step 2: Update PhoneTaskItem to borderline surface**

Apply same hybrid treatment as wall TaskItem — transparent background with thin border at rest. Since phone doesn't have encoder focus, just apply the borderline resting state universally:
- `background = Color.Transparent`
- `border = BorderStroke(1.dp, colors.borderColor)`
- `shape = RoundedCornerShape(8.dp)` (slightly smaller for phone density)

- [ ] **Step 3: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/PhoneCaptureHub.kt app/src/main/java/com/example/todowallapp/ui/components/PhoneTaskItem.kt
git commit -m "ui: phone companion — mic-first capture bar, borderline cards"
```

---

### Task 11: Settings Panel — Grouped Sections

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/SettingsPanel.kt`

- [ ] **Step 1: Add section group headers**

Before the THEME_MODE setting item, add:
```kotlin
Text(
    text = "APPEARANCE",
    style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        fontSize = 9.sp
    ),
    color = colors.textMuted,
    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
)
```

Add similar group headers:
- `"APPEARANCE"` — before Theme Mode
- `"SCHEDULE"` — before Sleep Schedule
- `"SYNC"` — before Sync Interval
- `"ACCOUNT"` — before Switch Mode / Sign Out

- [ ] **Step 2: Add description text to settings items**

Update the `SettingsItem` composable to accept an optional `description: String? = null` parameter. Render it below the label:
```kotlin
if (description != null) {
    Text(
        text = description,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = colors.textDisabled,
        modifier = Modifier.padding(top = 2.dp)
    )
}
```

Add descriptions:
- Theme: "Auto follows ambient light"
- Sleep Schedule: "Screen goes dark"
- Sync: "Pull from Google Tasks"

- [ ] **Step 3: Update settings item divider colors**

Replace any existing divider colors with `colors.dividerColor` (neutral gray instead of blue-slate).

- [ ] **Step 4: Update value display styling**

Settings values should use teal accent in a subtle pill:
```kotlin
Text(
    text = valueText,
    style = MaterialTheme.typography.labelSmall,
    color = colors.accentPrimary,
    modifier = Modifier
        .background(colors.accentPrimary.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 3.dp)
)
```

- [ ] **Step 5: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/SettingsPanel.kt
git commit -m "ui: settings panel — grouped sections with descriptions"
```

---

## Chunk 4: Completed Section Divider + Final Polish

### Task 12: Completed Section Divider in Task Lists

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt`

- [ ] **Step 1: Add completed divider before first completed task**

In the task list rendering (inside FolderSection content), before the first completed task appears, insert:

```kotlin
// Completed section divider
if (isFirstCompletedTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = colors.accentWarm.copy(alpha = 0.08f)
        )
        Text(
            text = "COMPLETED",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            ),
            color = colors.accentWarm.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = colors.accentWarm.copy(alpha = 0.08f)
        )
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt
git commit -m "ui: completed section divider with rose accent"
```

---

### Task 13: Final Build Verification

- [ ] **Step 1: Full clean build**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew clean assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify no compile warnings related to theme changes**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | grep -i "warning\|error" | head -20`
Expected: No theme-related errors

- [ ] **Step 3: Run unit tests**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew test 2>&1 | tail -10`
Expected: Tests pass (existing tests are boilerplate, should not be affected by theme changes)
