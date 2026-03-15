# P2 Spec Polish — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the wall display to match binding design specs — drift-to-bottom completion animation, encoder context menu for task actions, stronger focus glow for wall distance, LCD brightness control in sleep mode, and settings panel encoder dismissal.

**Architecture:** All changes are UI-layer only. No ViewModel, Repository, or data model changes. Portrait is the primary target; landscape must not break (it uses the same composables with adaptive dimensions from `LayoutDimensions`).

**Tech Stack:** Jetpack Compose animations, Android WindowManager brightness API, existing encoder key handler infrastructure.

---

## File Map

| File | Changes |
|------|---------|
| `ui/screens/TaskWallScreen.kt` | Tasks 1, 3, 4, 5 — drift animation, context menu wiring, settings encoder nav, brightness callback |
| `ui/components/TaskItem.kt` | Task 2 — stronger focus glow |
| `MainActivity.kt` | Task 5 — brightness control via WindowManager |

**Dependency order:** Tasks 2 and 5 are independent of each other and of Tasks 1/3/4. Tasks 1, 3, 4 all modify `TaskWallScreen.kt` and should be done sequentially.

---

## Task 1: Context Menu via Encoder Hold Gesture

**Priority:** Highest — this is the only functional gap (encoder users cannot delete or schedule tasks).

**Design:** The encoder already has a "medium hold" zone (350-800ms) that sets `promoteTriggered = true` but does nothing on release. Repurpose this dead zone to open the context menu for the currently focused task.

- Hold 0-350ms + release → toggle completion (existing behavior, unchanged)
- Hold 350-800ms + release → open context menu for focused task
- Hold 800ms+ → voice input (existing behavior, unchanged)

Context menu actions for **pending** tasks: "Schedule" (calls `onScheduleTask`), "Delete" (calls `onTaskDelete`)
Context menu actions for **completed** tasks: "Restore" (calls `onTaskToggle`), "Delete" (calls `onTaskDelete`)

Once open, the context menu is already rendered by `TaskContextMenu` composable and has `selectedActionIndex`. Wire encoder CW/CCW to navigate actions, click to confirm.

**Files:**
- Modify: `ui/screens/TaskWallScreen.kt:295-300` (context menu actions per task state)
- Modify: `ui/screens/TaskWallScreen.kt:622-654` (selectCurrent — add context menu navigation)
- Modify: `ui/screens/TaskWallScreen.kt:675-736` (key handler — wire encoder when context menu open)
- Modify: `ui/screens/TaskWallScreen.kt:718-731` (KeyUp — open context menu on medium hold release)

- [ ] **Step 1: Build pending/completed action lists**

In the state block near line 295, replace the single `contextMenuActions` with two lists:

```kotlin
val pendingContextMenuActions = remember {
    listOf(
        TaskContextMenuAction(id = "schedule", label = "Schedule Task"),
        TaskContextMenuAction(id = "delete", label = "Delete Task", isDestructive = true)
    )
}
val completedContextMenuActions = remember {
    listOf(
        TaskContextMenuAction(id = "restore", label = "Restore to Pending"),
        TaskContextMenuAction(id = "delete", label = "Delete Task", isDestructive = true)
    )
}
```

Update `contextMenuActions` to be derived from the focused task state:
```kotlin
val contextMenuActions = remember(contextMenuTask?.isCompleted) {
    if (contextMenuTask?.isCompleted == true) completedContextMenuActions else pendingContextMenuActions
}
```

Remove the old static `contextMenuActions` and `contextMenuActionCount`.

- [ ] **Step 2: Open context menu on medium hold release**

In the KeyUp handler (line 718-731), change the dead zone behavior. Replace:
```kotlin
} else if (!promoteTriggered) {
    selectCurrent()
}
```
with:
```kotlin
} else if (!promoteTriggered) {
    selectCurrent()
} else {
    // Medium hold (350-800ms) — open context menu for focused task
    val focusedNode = selectedFocusKey?.let(focusIndexByKey::get)?.let(focusOrder::getOrNull)
    val task = focusedNode?.task
    if (task != null) {
        contextMenuTask = task
        contextMenuSelectedIndex = 0
        performAppHaptic(view, context, AppHapticPattern.CONFIRM)
    }
}
```

- [ ] **Step 3: Wire encoder navigation when context menu is open**

At the top of the `onKeyEvent` block (line 676), replace `if (contextMenuTask != null) return@onKeyEvent false` with:

```kotlin
if (contextMenuTask != null) {
    when (keyEvent.type) {
        KeyEventType.KeyDown -> {
            when (keyEvent.key) {
                Key.DirectionUp, Key.DirectionRight -> {
                    contextMenuSelectedIndex = (contextMenuSelectedIndex - 1).coerceAtLeast(0)
                    performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
                    true
                }
                Key.DirectionDown, Key.DirectionLeft -> {
                    contextMenuSelectedIndex = (contextMenuSelectedIndex + 1).coerceAtMost(contextMenuActions.size - 1)
                    performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
                    true
                }
                Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                    val action = contextMenuActions.getOrNull(contextMenuSelectedIndex)
                    val task = contextMenuTask
                    if (action != null && task != null) {
                        when (action.id) {
                            "schedule" -> onScheduleTask(task)
                            "restore" -> onTaskToggle(task)
                            "delete" -> onTaskDelete(task)
                        }
                        performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                    }
                    contextMenuTask = null
                    contextMenuSelectedIndex = 0
                    true
                }
                else -> { contextMenuTask = null; contextMenuSelectedIndex = 0; true }
            }
        }
        else -> false
    }
    return@onKeyEvent true
}
```

- [ ] **Step 4: Update TaskContextMenu call site for "schedule" action**

At line 917-923, add the "schedule" case:
```kotlin
when (action.id) {
    "schedule" -> onScheduleTask(task)
    "restore" -> onTaskToggle(task)
    "delete" -> onTaskDelete(task)
}
```

- [ ] **Step 5: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 2: Focus Glow Strengthening for Wall Distance

**Priority:** Medium — polish for 6ft+ readability.

**Design:** Current glow uses `shadow(elevation = 24.dp)`. Android's shadow rendering can be subtle depending on GPU. Add a visible border glow via an animated border color when focused, plus increase shadow spot color alpha.

**Files:**
- Modify: `ui/components/TaskItem.kt:251-266` (cardContainerModifier shadow + border)

- [ ] **Step 1: Strengthen shadow and add border glow**

In `TaskItemContent`, modify the `cardContainerModifier` (lines 253-266):

```kotlin
val focusBorderColor by animateColorAsState(
    targetValue = when {
        !isSelected || isAmbientMode -> Color.Transparent
        showUrgencyAccent -> animatedUrgencyColor.copy(alpha = 0.45f)
        else -> colors.accentPrimary.copy(alpha = 0.35f)
    },
    animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
    label = "focusBorderGlow"
)

val cardContainerModifier = if (shouldUseSelectionLayer) {
    Modifier
        .fillMaxWidth()
        .graphicsLayer { translationY = -1f }
        .shadow(
            elevation = glowElevation,
            shape = cardShape,
            spotColor = if (showUrgencyAccent) animatedUrgencyColor.copy(alpha = 0.6f) else colors.accentPrimary.copy(alpha = 0.4f),
            ambientColor = Color.Black.copy(alpha = 0.3f),
            clip = false
        )
        .border(1.5.dp, focusBorderColor, cardShape)
} else {
    Modifier.fillMaxWidth()
}
```

Key changes:
- `spotColor` alpha raised: 0.5f→0.6f (urgency), 0.3f→0.4f (normal)
- Added animated `border` that glows with accent color when focused — this is visible regardless of GPU shadow rendering

- [ ] **Step 2: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 3: Task Completion Drift-to-Bottom Animation

**Priority:** Medium — binding spec #1.

**Design:** When a task is marked complete, animate it sliding down and fading out before the ViewModel removes it from the pending list. Use a short delay + `AnimatedVisibility` exit animation in `ParentChildGroup`.

The flow:
1. User clicks task → `onTaskToggle` fires → ViewModel starts optimistic update
2. The task's `isCompleted` becomes true
3. `AnimatedVisibility` catches the completed state and plays exit animation (slide down + fade)
4. After animation, the recomposition removes it from pending and adds to completed section

Since `AnimatedVisibility` naturally handles this when the item leaves composition, we need to track "recently completed" task IDs with a brief animation window.

**Files:**
- Modify: `ui/screens/TaskWallScreen.kt:1162-1211` (ParentChildGroup — add exit animation)
- Modify: `ui/screens/TaskWallScreen.kt:267-288` (state block — add recently-completed tracking)

- [ ] **Step 1: Add recently-completed state tracking**

Near the state declarations (~line 287):

```kotlin
val recentlyCompletedIds = remember { mutableStateMapOf<String, Boolean>() }
```

- [ ] **Step 2: Track completions in onTaskToggle wrapper**

Wrap the `onTaskToggle` callback to capture completion events. Near where `onTaskToggle` is used in FolderSection call sites:

```kotlin
val onTaskToggleWithDrift = remember(onTaskToggle) {
    { task: Task ->
        if (!task.isCompleted) {
            recentlyCompletedIds[task.id] = true
        }
        onTaskToggle(task)
    }
}
```

Pass `onTaskToggleWithDrift` instead of `onTaskToggle` to FolderSection.

- [ ] **Step 3: Add AnimatedVisibility exit in ParentChildGroup**

Wrap each TaskItem in `ParentChildGroup` with `AnimatedVisibility`:

For the parent task:
```kotlin
AnimatedVisibility(
    visible = !group.parent.isCompleted || recentlyCompletedIds.containsKey(group.parent.id).not(),
    exit = slideOutVertically(
        targetOffsetY = { it / 2 },
        animationSpec = tween(WallAnimations.MEDIUM)
    ) + fadeOut(tween(WallAnimations.MEDIUM))
) {
    TaskItem(...)
}
```

**Note:** This approach is tricky because the task is removed from `pendingGroups` by the ViewModel recomposition before the animation can play. A simpler approach:

Use `AnimatedVisibility` keyed on `task.isCompleted` with the existing rendering. When `isCompleted` toggles to true, the completion ripple + alpha fade already plays (300ms). The task then gets sorted to the completed section on the next state update. The visual gap-closing happens naturally via Column recomposition.

Since the current animations (ripple + alpha fade + strikethrough) already provide satisfying feedback in 300ms, and the ViewModel's state recomposition removes the task from pending list, the "drift" is essentially the card fading out while adjacent cards close the gap. This is already partially working.

**Refined approach:** Add `Modifier.animateItem()` if using LazyColumn, or use `Modifier.animateContentSize()` on the parent Column to smooth gap closing. Since FolderSection uses a regular Column (not LazyColumn), use `AnimatedVisibility` wrapping.

Actually — the simplest and most reliable approach given the Column-based layout:

In `ParentChildGroup`, keep track of which tasks are animating out, and use `AnimatedVisibility` with a `LaunchedEffect` that cleans up after animation completes.

```kotlin
// In ParentChildGroup, for each task:
val isAnimatingOut = group.parent.isCompleted
AnimatedVisibility(
    visible = !isAnimatingOut,
    exit = shrinkVertically(tween(WallAnimations.MEDIUM)) + fadeOut(tween(WallAnimations.SHORT))
) {
    TaskItem(...)
}
```

But since the parent is already filtered into `pendingGroups` (non-completed only), `isCompleted` will be false for all tasks here. The issue is that after optimistic toggle, the ViewModel emits a new state where the task moves from `pendingGroups` to `completedTasks`.

**Final approach (simplest):** Wrap FolderSection's expanded content Column items with `AnimatedVisibility(visible = true)` and use `key()` + `animateEnterExit`. When an item disappears from the list (task completed), the remaining items slide up smoothly because `AnimatedVisibility` collapses the space.

Alternatively, since this is a non-LazyColumn, convert the tasks rendering inside FolderSection to use `forEach` with `key` and `AnimatedVisibility(visible = true, enter = ..., exit = ...)`.

Given the complexity of this animation within the Column-based layout, and that the current completion feedback (ripple + fade + strikethrough) is already satisfying, **defer this to a dedicated session** if the simpler approach doesn't work. Mark as **stretch goal**.

- [ ] **Step 3 (simplified): Add smooth gap-closing via AnimatedVisibility**

The completed task already fades visually (alpha → 0.35) via TaskItem's `completedAlpha`. The ViewModel then removes it from pendingGroups on the next sync/recomposition. To smooth the gap-closing, add `animateContentSize()` to the FolderSection's expanded Column:

In `FolderSection`, line 1110:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
        .animateContentSize(animationSpec = tween(WallAnimations.MEDIUM)),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
```

This smoothly collapses the gap when a task leaves the pending group.

- [ ] **Step 4: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 4: Settings Panel Encoder Dismiss

**Priority:** Low — quality of life for encoder-only navigation.

**Design:** When `showSettings = true`, intercept encoder events to allow dismissing with a click when focus returns to a "Close" action, or simply map a long-press/back-equivalent to close settings.

Simplest approach: When settings panel is open, intercept the encoder. CW/CCW navigates settings options (already handled by SettingsPanel internally via touch). Click on the backdrop area = dismiss. Since BackHandler already handles Escape/Back, just ensure the encoder's click dismisses settings when no settings item is focused.

**Files:**
- Modify: `ui/screens/TaskWallScreen.kt:675-736` (key handler — intercept when showSettings)

- [ ] **Step 1: Add settings dismiss on encoder click**

In the key handler, after the context menu check, add:

```kotlin
if (showSettings) {
    when (keyEvent.type) {
        KeyEventType.KeyDown -> {
            when (keyEvent.key) {
                Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                    showSettings = false
                    performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                    true
                }
                else -> true // consume all other keys while settings open
            }
        }
        else -> false
    }
    return@onKeyEvent true
}
```

This makes encoder click = close settings. CW/CCW are consumed (no-op) since SettingsPanel is touch-only for adjusting values.

- [ ] **Step 2: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 5: LCD Brightness Control in Sleep Mode

**Priority:** Medium — critical for bedroom installations.

**Design:** When sleep mode activates, dim the actual LCD backlight to minimum (not just darken content). Use `WindowManager.LayoutParams.screenBrightness` set to near-zero. When waking, restore to system default (-1f).

This requires the Activity's Window reference, so pass a brightness callback from `MainActivity` → `WallModeContent` → `TaskWallScreen`.

**Files:**
- Modify: `MainActivity.kt:105-128` (add brightness control method)
- Modify: `MainActivity.kt:297-306` (pass brightness callback to WallModeContent)
- Modify: `MainActivity.kt:334-340` (WallModeContent params)
- Modify: `ui/screens/TaskWallScreen.kt:222-265` (add onBrightnessChange param)
- Modify: `ui/screens/TaskWallScreen.kt:556-560` (wakeUp — restore brightness)
- Modify: `ui/screens/TaskWallScreen.kt` (ambient tier change — set brightness)

- [ ] **Step 1: Add brightness control in MainActivity**

In `MainActivity`, add a method:

```kotlin
fun setScreenBrightness(brightness: Float) {
    val lp = window.attributes
    lp.screenBrightness = brightness // -1f = system default, 0f..1f = manual
    window.attributes = lp
}
```

- [ ] **Step 2: Thread the callback through composables**

Add `onSetBrightness: (Float) -> Unit` parameter to:
- `WallModeContent`
- `TaskWallScreen`

In `MainActivity.TaskWallApp` → `WallModeContent` call site, pass:
```kotlin
onSetBrightness = { brightness -> (context as? Activity)?.let {
    val lp = it.window.attributes
    lp.screenBrightness = brightness
    it.window.attributes = lp
} }
```

- [ ] **Step 3: Set brightness on ambient tier change**

In `TaskWallScreen`, add a `LaunchedEffect` that reacts to `ambientTier`:

```kotlin
LaunchedEffect(ambientTier) {
    when (ambientTier) {
        AmbientTier.SLEEP -> onSetBrightness(0.01f) // near-black
        AmbientTier.QUIET -> onSetBrightness(0.15f) // dim but readable
        AmbientTier.ACTIVE -> onSetBrightness(-1f) // system default
    }
}
```

Also call `onSetBrightness(-1f)` in `wakeUp()`.

- [ ] **Step 4: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Execution Order

1. **Task 1** (context menu) — highest priority, functional gap
2. **Task 2** (focus glow) — independent, can parallel with Task 1
3. **Task 3** (drift animation) — depends on Task 1 being done (same file)
4. **Task 4** (settings dismiss) — depends on Task 1 being done (same key handler)
5. **Task 5** (brightness) — independent of all others, can parallel

**Parallelizable pairs:** Task 2 + Task 5 can run alongside Task 1. Tasks 3 and 4 must follow Task 1.

---

## Verification

After all tasks, run full build:
```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug
```

Check for no new lint warnings:
```bash
./gradlew lint
```

Spot-check portrait behavior is primary target; landscape should compile and render with no crashes (same composables, adaptive dimensions).
