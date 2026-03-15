# UI Enhancement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Elevate phone mode UI with accordion sections, collapsible subtasks, and micro-animations; polish the calendar view with a week strip and smart hour compression; unify visual DNA across both modes.

**Architecture:** Phone mode gets new accordion rendering in PhoneHomeScreen (replacing flat list), enhanced PhoneTaskItem with subtask support, refined PhoneCaptureBar, and a micro-animation system. Calendar view gets a WeekStrip composable and compressed-slot logic in CalendarDayView. All changes use existing WallColors/WallAnimations/WallShapes systems.

**Tech Stack:** Jetpack Compose, Kotlin, existing WallColors theme system, Android Haptics

---

## Phase 1: Phone Accordion Sections

### Task 1: Add expand/collapse state to PhoneCaptureViewModel

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/PhoneCaptureViewModel.kt:40-54`

**Step 1: Add expandedListIds field to PhoneCaptureUiState**

In `PhoneCaptureUiState` (line ~40), add a new field:

```kotlin
data class PhoneCaptureUiState(
    // ... existing fields ...
    val expandedListIds: Set<String> = emptySet(),
    // ... rest of fields ...
)
```

**Step 2: Add toggle function to PhoneCaptureViewModel**

Add a new function after `toggleTaskCompletion()` (around line 188):

```kotlin
fun toggleListExpanded(listId: String) {
    _uiState.update { state ->
        val current = state.expandedListIds
        state.copy(
            expandedListIds = if (listId in current) current - listId else current + listId
        )
    }
}
```

**Step 3: Verify the app still compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/viewmodel/PhoneCaptureViewModel.kt
git commit -m "feat(phone): add expand/collapse state for accordion sections"
```

---

### Task 2: Build PhoneAccordionSection composable

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/ui/components/PhoneAccordionSection.kt`

**Step 1: Create the accordion section header composable**

Create a new file with the section header that shows list title, task count, chevron, and peek text:

```kotlin
package com.example.todowallapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations

@Composable
fun PhoneAccordionHeader(
    title: String,
    taskCount: Int,
    isExpanded: Boolean,
    peekText: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(WallAnimations.SHORT),
        label = "chevron"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$taskCount tasks",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                modifier = Modifier.padding(end = 8.dp)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = colors.textMuted,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation)
            )
        }

        // Peek text when collapsed
        AnimatedVisibility(
            visible = !isExpanded && peekText != null,
            enter = fadeIn(tween(WallAnimations.SHORT)),
            exit = fadeOut(tween(WallAnimations.SHORT))
        ) {
            Text(
                text = peekText ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            )
        }

        Divider(color = colors.borderColor, thickness = 0.5.dp)
    }
}
```

**Step 2: Verify it compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/PhoneAccordionSection.kt
git commit -m "feat(phone): add PhoneAccordionHeader composable"
```

---

### Task 3: Integrate accordion sections into PhoneHomeScreen

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/PhoneHomeScreen.kt:188-203`

**Step 1: Build parent-child task grouping utility**

Add a helper function (either at the bottom of PhoneHomeScreen.kt or in a new util file) that groups tasks into parent-child pairs, mirroring the wall's `PendingTaskGroup` concept:

```kotlin
data class PhoneTaskGroup(
    val parent: Task,
    val children: List<Task>
)

fun groupTasksForPhone(tasks: List<Task>): List<PhoneTaskGroup> {
    val parentIds = tasks.filter { it.parentId == null }.map { it.id }.toSet()
    val childrenByParent = tasks.filter { it.parentId != null }.groupBy { it.parentId!! }
    val topLevel = tasks.filter { it.parentId == null }
    return topLevel.map { parent ->
        PhoneTaskGroup(
            parent = parent,
            children = childrenByParent[parent.id] ?: emptyList()
        )
    }
}
```

**Step 2: Replace flat list rendering with accordion sections**

Replace lines 188-203 in PhoneHomeScreen.kt. The current code iterates `uiState.taskLists.forEach` and renders flat items. Replace with:

```kotlin
uiState.taskLists.forEach { listWithTasks ->
    val isExpanded = listWithTasks.taskList.id in uiState.expandedListIds
    val groups = remember(listWithTasks.tasks) {
        groupTasksForPhone(sortTasksForDisplay(listWithTasks.tasks))
    }
    val pendingGroups = groups.filter { !it.parent.isCompleted }
    val completedGroups = groups.filter { it.parent.isCompleted }

    item(key = "header_${listWithTasks.taskList.id}") {
        PhoneAccordionHeader(
            title = listWithTasks.taskList.title,
            taskCount = pendingGroups.size,
            isExpanded = isExpanded,
            peekText = pendingGroups.firstOrNull()?.parent?.title,
            onToggle = { onToggleListExpanded(listWithTasks.taskList.id) }
        )
    }

    if (isExpanded) {
        items(
            pendingGroups,
            key = { "task_${it.parent.id}" }
        ) { group ->
            PhoneTaskItem(
                task = group.parent,
                onToggleComplete = { onToggleComplete(group.parent) }
            )
            // Subtasks rendered inside PhoneTaskItem (Task 4)
        }

        if (completedGroups.isNotEmpty()) {
            item(key = "completed_header_${listWithTasks.taskList.id}") {
                Text(
                    text = "${completedGroups.size} completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalWallColors.current.textMuted,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            items(
                completedGroups,
                key = { "completed_${it.parent.id}" }
            ) { group ->
                PhoneTaskItem(
                    task = group.parent,
                    onToggleComplete = { onToggleComplete(group.parent) }
                )
            }
        }
    }
}
```

**Step 3: Add onToggleListExpanded callback to PhoneHomeScreen parameters**

Add to the composable signature: `onToggleListExpanded: (String) -> Unit`

Wire it up from wherever PhoneHomeScreen is called (likely MainActivity.kt or a navigation host) to `viewModel.toggleListExpanded(listId)`.

**Step 4: Verify it compiles and runs**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/PhoneHomeScreen.kt
git commit -m "feat(phone): replace flat task list with accordion sections"
```

---

### Task 4: Add collapsible subtask support to PhoneTaskItem

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/PhoneTaskItem.kt`

**Step 1: Add subtask parameters and expand/collapse state**

Update the PhoneTaskItem signature to accept children and handle expand/collapse:

```kotlin
@Composable
fun PhoneTaskItem(
    task: Task,
    onToggleComplete: () -> Unit,
    children: List<Task> = emptyList(),
    onToggleChildComplete: (Task) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var childrenExpanded by remember { mutableStateOf(false) }
    // ... existing card code ...
```

**Step 2: Add subtask toggle indicator to the parent card**

After the existing DueDateBadge in the Row, add a subtask count indicator when children exist:

```kotlin
if (children.isNotEmpty()) {
    Text(
        text = "${children.size}",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textMuted,
        modifier = Modifier
            .padding(start = 4.dp)
            .clickable { childrenExpanded = !childrenExpanded }
    )
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = if (childrenExpanded) "Collapse subtasks" else "Expand subtasks",
        tint = colors.textMuted,
        modifier = Modifier
            .size(16.dp)
            .rotate(if (childrenExpanded) 180f else 0f)
    )
}
```

**Step 3: Render collapsible subtask list below the parent card**

After the parent card's closing brace, add:

```kotlin
AnimatedVisibility(
    visible = childrenExpanded && children.isNotEmpty(),
    enter = expandVertically(tween(250)) + fadeIn(tween(250)),
    exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
) {
    Column(
        modifier = Modifier.padding(start = 24.dp) // indent
    ) {
        // Connecting line drawn via drawBehind or a Box with a thin vertical line
        children.forEachIndexed { index, child ->
            Row {
                // Vertical connecting line
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(colors.borderColor.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Reuse a simplified PhoneTaskItem for child
                PhoneSubtaskItem(
                    task = child,
                    onToggleComplete = { onToggleChildComplete(child) }
                )
            }
        }
    }
}
```

**Step 4: Create PhoneSubtaskItem (simplified child task row)**

Add below PhoneTaskItem in the same file:

```kotlin
@Composable
private fun PhoneSubtaskItem(
    task: Task,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    AnimatedTaskCompletion(isCompleted = task.isCompleted) { checkmarkAlpha, contentAlpha ->
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleComplete)
                .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskStatusIndicator(
                isCompleted = task.isCompleted,
                checkmarkAlpha = checkmarkAlpha,
                ringSize = 24,
                innerSize = 18,
                checkmarkSize = 14
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )
        }
    }
}
```

**Step 5: Update PhoneHomeScreen to pass children to PhoneTaskItem**

Back in PhoneHomeScreen.kt, update the accordion rendering to pass children:

```kotlin
items(pendingGroups, key = { "task_${it.parent.id}" }) { group ->
    PhoneTaskItem(
        task = group.parent,
        onToggleComplete = { onToggleComplete(group.parent) },
        children = group.children,
        onToggleChildComplete = { child -> onToggleComplete(child) }
    )
}
```

**Step 6: Verify it compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/PhoneTaskItem.kt app/src/main/java/com/example/todowallapp/ui/screens/PhoneHomeScreen.kt
git commit -m "feat(phone): add collapsible subtasks with connecting line"
```

---

## Phase 2: Phone Capture Bar Refinement

### Task 5: Redesign PhoneCaptureBar

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/PhoneCaptureBar.kt`

**Step 1: Replace CaptureActionCell with icon-only buttons**

Rewrite the composable. Key changes:
- Remove text labels from each cell
- Increase icon size from 24dp to 28dp
- Change surface from elevated to `surfaceCard` with thin top border
- Add subtle circular accent background on the voice/mic button
- Add haptic feedback on tap
- Reduce overall height to ~56dp

```kotlin
@Composable
fun PhoneCaptureBar(
    onCameraClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceCard,
        tonalElevation = 0.dp
    ) {
        Column {
            // Thin top border
            Divider(color = colors.borderColor, thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CaptureIconButton(
                    icon = Icons.Outlined.CameraAlt,
                    contentDescription = "Camera",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onCameraClick()
                    }
                )
                // Voice gets accent emphasis
                CaptureIconButton(
                    icon = Icons.Outlined.Mic,
                    contentDescription = "Voice",
                    emphasized = true,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onVoiceClick()
                    }
                )
                CaptureIconButton(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onRefreshClick()
                    }
                )
                CaptureIconButton(
                    icon = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSettingsClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun CaptureIconButton(
    icon: ImageVector,
    contentDescription: String,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalWallColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .then(
                if (emphasized) Modifier.background(
                    colors.accentPrimary.copy(alpha = 0.12f),
                    CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (emphasized) colors.accentPrimary else colors.textSecondary,
            modifier = Modifier.size(28.dp)
        )
    }
}
```

**Step 2: Verify it compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/PhoneCaptureBar.kt
git commit -m "feat(phone): redesign capture bar - icons only, thinner, voice emphasis"
```

---

## Phase 3: Micro-Animation System

### Task 6: Add stagger enter animation to PhoneHomeScreen

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/PhoneHomeScreen.kt`

**Step 1: Add stagger enter animation for accordion sections on initial load**

Wrap each accordion section item with an `AnimatedVisibility` that triggers on first composition, with staggered delay based on list index:

```kotlin
uiState.taskLists.forEachIndexed { index, listWithTasks ->
    item(key = "section_$index") {
        val visible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(index * 60L) // 60ms stagger
            visible.value = true
        }
        AnimatedVisibility(
            visible = visible.value,
            enter = fadeIn(tween(WallAnimations.MEDIUM)) + slideInVertically(
                initialOffsetY = { 20 },
                animationSpec = tween(WallAnimations.MEDIUM)
            )
        ) {
            // Accordion header + content here
        }
    }
}
```

**Step 2: Add press scale to PhoneTaskItem**

In PhoneTaskItem.kt, wrap the card with a scale animation on press:

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.98f else 1f,
    animationSpec = tween(150),
    label = "pressScale"
)

// Apply to card modifier:
Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
```

**Step 3: Add stagger to subtask expand animation**

In PhoneTaskItem.kt, when children expand, add per-child stagger delay:

```kotlin
children.forEachIndexed { index, child ->
    val childVisible = remember { mutableStateOf(false) }
    LaunchedEffect(childrenExpanded) {
        if (childrenExpanded) {
            delay(index * WallAnimations.STAGGER_ENTER.toLong())
            childVisible.value = true
        } else {
            delay(index * WallAnimations.STAGGER_EXIT.toLong())
            childVisible.value = false
        }
    }
    AnimatedVisibility(visible = childVisible.value, ...) {
        // child row
    }
}
```

**Step 4: Verify it compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/PhoneHomeScreen.kt app/src/main/java/com/example/todowallapp/ui/components/PhoneTaskItem.kt
git commit -m "feat(phone): add stagger enter, press scale, and subtask expand animations"
```

---

## Phase 4: Calendar View Polish

### Task 7: Create WeekStrip composable

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/ui/components/WeekStrip.kt`

**Step 1: Build the week strip composable**

```kotlin
package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekStrip(
    startDate: LocalDate,
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    taskUrgencyByTaskId: Map<String, TaskUrgency>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val today = LocalDate.now()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        (0 until 7).forEach { offset ->
            val date = startDate.plusDays(offset.toLong())
            val isSelected = date == selectedDate
            val isToday = date == today
            val dayEvents = eventsByDate[date] ?: emptyList()

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) colors.accentPrimary.copy(alpha = 0.15f)
                else androidx.compose.ui.graphics.Color.Transparent,
                animationSpec = tween(WallAnimations.SHORT),
                label = "weekDayBg"
            )

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onDateSelected(date) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.accentPrimary else colors.textMuted
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) colors.accentPrimary else colors.textPrimary
                )
                // Busy dots (max 3)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    dayEvents.take(3).forEach { event ->
                        val dotColor = if (event.isPromotedTask) {
                            val urgency = event.sourceTaskId?.let(taskUrgencyByTaskId::get)
                            when (urgency) {
                                TaskUrgency.OVERDUE -> colors.urgencyOverdue
                                TaskUrgency.DUE_TODAY -> colors.urgencyDueToday
                                TaskUrgency.DUE_SOON -> colors.urgencyDueSoon
                                else -> colors.accentPrimary
                            }
                        } else colors.accentPrimary

                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(dotColor, CircleShape)
                        )
                    }
                }

                // Today underline
                if (isToday && !isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(width = 12.dp, height = 1.5.dp)
                            .background(colors.accentPrimary.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}
```

**Step 2: Verify it compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/WeekStrip.kt
git commit -m "feat(calendar): add WeekStrip composable with busy indicators"
```

---

### Task 8: Integrate WeekStrip into CalendarScreen

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/CalendarScreen.kt:386-395`

**Step 1: Add WeekStrip between the DateAndCalendarBar and CalendarDayView**

Find the area around lines 386-395 where the date bar is rendered. Add the WeekStrip below it:

```kotlin
// After DateAndCalendarBar, before CalendarDayView:
WeekStrip(
    startDate = selectedDate.with(java.time.DayOfWeek.MONDAY), // week start
    selectedDate = selectedDate,
    eventsByDate = eventsByDate, // may need to compute or pass from ViewModel
    taskUrgencyByTaskId = taskUrgencyByTaskId,
    onDateSelected = onSelectDate
)
```

Note: The ViewModel may need a new function to provide `eventsByDate: Map<LocalDate, List<CalendarEvent>>` for the 7-day window. Check what data is already available and add a computed property if needed.

**Step 2: Verify it compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/CalendarScreen.kt
git commit -m "feat(calendar): integrate WeekStrip into CalendarScreen"
```

---

### Task 9: Add smart hour compression to CalendarDayView

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/CalendarDayView.kt:49-69`

**Step 1: Add compressed slot concept to the data model**

Update the slot building logic to identify compressible ranges:

```kotlin
sealed class CalendarSlotItem {
    data class SingleSlot(val slot: CalendarTimeSlot) : CalendarSlotItem()
    data class CompressedRange(
        val startTime: LocalDateTime,
        val endTime: LocalDateTime,
        val slotCount: Int
    ) : CalendarSlotItem()
}

fun buildCompressedSlots(
    date: LocalDate,
    events: List<CalendarEvent>,
    now: LocalDateTime,
    startHour: Int = 7,
    endHourExclusive: Int = 23
): List<CalendarSlotItem> {
    val allSlots = buildHalfHourSlots(date, events, startHour, endHourExclusive)
    val nowWindowStart = now.minusHours(1)
    val nowWindowEnd = now.plusHours(1)
    val isToday = date == now.toLocalDate()

    val result = mutableListOf<CalendarSlotItem>()
    var emptyRun = mutableListOf<CalendarTimeSlot>()

    fun flushEmptyRun() {
        if (emptyRun.size >= 3) {
            result += CalendarSlotItem.CompressedRange(
                startTime = emptyRun.first().start,
                endTime = emptyRun.last().start.plusMinutes(30),
                slotCount = emptyRun.size
            )
        } else {
            emptyRun.forEach { result += CalendarSlotItem.SingleSlot(it) }
        }
        emptyRun = mutableListOf()
    }

    for (slot in allSlots) {
        val inNowWindow = isToday && slot.start >= nowWindowStart && slot.start < nowWindowEnd
        val hasEvents = slot.events.isNotEmpty()

        if (!hasEvents && !inNowWindow) {
            emptyRun += slot
        } else {
            flushEmptyRun()
            result += CalendarSlotItem.SingleSlot(slot)
        }
    }
    flushEmptyRun()

    return result
}
```

**Step 2: Update CalendarDayView to render compressed ranges**

Replace the LazyColumn items call to use `buildCompressedSlots` and render `CompressedRange` as a compact row:

```kotlin
val slotItems = buildCompressedSlots(date = date, events = events, now = now)

LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    items(slotItems, key = { item ->
        when (item) {
            is CalendarSlotItem.SingleSlot -> item.slot.start.toString()
            is CalendarSlotItem.CompressedRange -> "compressed_${item.startTime}"
        }
    }) { item ->
        when (item) {
            is CalendarSlotItem.SingleSlot -> CalendarSlotRow(
                slot = item.slot,
                isSelectedSlot = item.slot.start == selectedSlotStart,
                selectedEventId = selectedEventId,
                taskListTitleByTaskId = taskListTitleByTaskId,
                taskUrgencyByTaskId = taskUrgencyByTaskId,
                now = now,
                onSlotActivated = onSlotActivated,
                onEventActivated = onEventActivated
            )
            is CalendarSlotItem.CompressedRange -> CompressedSlotRow(
                range = item,
                onExpand = { /* expand logic - local state */ }
            )
        }
    }
}
```

**Step 3: Create CompressedSlotRow composable**

```kotlin
@Composable
private fun CompressedSlotRow(
    range: CalendarSlotItem.CompressedRange,
    onExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(onClick = onExpand)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${range.startTime.format(SlotTimeFormatter)} – ${range.endTime.format(SlotTimeFormatter)}",
            style = MaterialTheme.typography.labelMedium,
            color = LocalWallColors.current.textMuted.copy(alpha = 0.5f),
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = "no events",
            style = MaterialTheme.typography.labelSmall,
            color = LocalWallColors.current.textMuted.copy(alpha = 0.3f)
        )
    }
}
```

**Step 4: Verify it compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/CalendarDayView.kt
git commit -m "feat(calendar): add smart hour compression for empty slot ranges"
```

---

### Task 10: Polish event chips and slot styling

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/CalendarDayView.kt:208-268`

**Step 1: Replace [T] prefix with urgency dot**

In `EventChip` composable (line ~208), replace the `[T]` text with a colored dot:

```kotlin
// Replace this block:
if (event.isPromotedTask) {
    Text(text = "[T]", ...)
}

// With:
if (event.isPromotedTask) {
    Box(
        modifier = Modifier
            .size(4.dp)
            .background(urgencyColor, CircleShape)
            .padding(end = 4.dp) // handled via Row spacing instead
    )
    Spacer(modifier = Modifier.width(4.dp))
}
```

**Step 2: Remove fixed 156dp width from chip text**

Replace `Modifier.width(156.dp)` with `Modifier.widthIn(max = 180.dp)` to let chips size to content.

**Step 3: Increase chip vertical padding**

Change `.padding(horizontal = 8.dp, vertical = 3.dp)` to `.padding(horizontal = 8.dp, vertical = 6.dp)`.

Allow 2-line text: Change `maxLines = 1` to `maxLines = 2`.

**Step 4: Add variable slot height**

In `CalendarSlotRow`, change the fixed heights:
- Slot with events: keep 72dp or increase to 80dp
- Empty slot: reduce inner Box height to 48dp

**Step 5: Add current time dot pulse animation**

In the `hasNow` block, add a pulsing opacity:

```kotlin
val pulseAlpha by rememberInfiniteTransition().animateFloat(
    initialValue = 0.4f,
    targetValue = 0.7f,
    animationSpec = infiniteRepeatable(
        animation = tween(2000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse
    ),
    label = "nowPulse"
)

// Apply to the dot:
.background(colors.accentPrimary.copy(alpha = pulseAlpha), CircleShape)
```

**Step 6: Remove dead empty Text placeholder**

Delete the empty state block (lines ~176-182):
```kotlin
// DELETE THIS:
if (slot.events.isEmpty()) {
    Text(text = "", ...)
}
```

**Step 7: Use labelMedium for time labels**

Change `MaterialTheme.typography.bodyMedium` to `MaterialTheme.typography.labelMedium` for time labels.

**Step 8: Verify it compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/CalendarDayView.kt
git commit -m "feat(calendar): polish event chips, variable slot height, time dot pulse"
```

---

## Phase 5: Final Integration & Verification

### Task 11: Full build and manual smoke test

**Step 1: Clean build**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew clean assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 2: Run existing tests**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew test 2>&1 | tail -10`
Expected: All tests pass (existing tests should be unaffected)

**Step 3: Install and verify on device**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew installDebug`

Manual verification checklist:
- [ ] Phone mode: accordion sections expand/collapse on tap
- [ ] Phone mode: subtasks show connecting line when expanded
- [ ] Phone mode: capture bar is thinner, icons-only, voice has accent
- [ ] Phone mode: sections stagger in on initial load
- [ ] Phone mode: task press has subtle scale effect
- [ ] Wall mode: calendar week strip shows 7 days with busy dots
- [ ] Wall mode: calendar compresses empty hour ranges
- [ ] Wall mode: event chips use urgency dots instead of [T]
- [ ] Wall mode: current time dot pulses

**Step 4: Final commit if any adjustments needed**

```bash
git add -A
git commit -m "fix: final adjustments from smoke testing"
```
