# Accordion Task Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat task picker overlay with an accordion-style hierarchical picker where task lists collapse/expand, reducing visual clutter when scheduling tasks from the calendar Day/3-Day views.

**Architecture:** The existing `TaskPickerOverlay` builds a flat list of all headers + tasks. We replace this with an accordion model where only the focused list's tasks are visible. Focus navigation spans list headers (focusable) and task rows within the expanded list. CalendarScreen's key handler is updated to account for the new focus model and distinguish header-enter (expand) from task-enter (select).

**Tech Stack:** Jetpack Compose, existing WallColors theme tokens, existing animation specs (WallAnimations, spring)

---

### Task 1: Refactor TaskPickerOverlay to accordion model

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/TaskPickerOverlay.kt`

- [ ] **Step 1: Update PickerItem sealed class to include focusable headers**

Replace the existing `PickerItem` sealed class (line 194-197):

```kotlin
private sealed class PickerItem {
    data class ListHeader(val listName: String, val listIndex: Int, val taskCount: Int) : PickerItem()
    data class TaskRow(val task: Task) : PickerItem()
}
```

- [ ] **Step 2: Add expandedListIndex parameter and rebuild flat items with accordion logic**

Update `TaskPickerOverlay` signature to add `expandedListIndex: Int` and `onExpandList: (Int) -> Unit`.

Replace the flat item building (lines 54-61) with accordion logic:

```kotlin
val flatItems = buildList {
    tasksByList.forEachIndexed { listIndex, (listName, tasks) ->
        if (tasks.isNotEmpty()) {
            add(PickerItem.ListHeader(listName, listIndex, tasks.size))
            if (listIndex == expandedListIndex) {
                tasks.forEach { add(PickerItem.TaskRow(it)) }
            }
        }
    }
}
```

- [ ] **Step 3: Update focus mapping to include headers as focusable items**

Replace the `taskRowIndices` mapping (lines 63-64) with a focusable-items mapping that includes BOTH headers and task rows:

```kotlin
val focusableIndices = flatItems.indices.filter {
    flatItems[it] is PickerItem.ListHeader || flatItems[it] is PickerItem.TaskRow
}
// Since all items are focusable now, focusableIndices == flatItems.indices
```

Actually simpler — all items in the flat list are focusable, so `focusedIndex` maps directly to `flatItems[focusedIndex]`.

- [ ] **Step 4: Update LazyColumn rendering for accordion headers**

Replace the LazyColumn rendering (lines 102-132). Headers get focus styling (glow when focused), chevron indicator, task count badge. Task rows keep existing styling but only render within the expanded list.

```kotlin
LazyColumn(...) {
    itemsIndexed(flatItems) { index, item ->
        when (item) {
            is PickerItem.ListHeader -> {
                val isFocused = index == focusedIndex
                PickerListHeader(
                    listName = item.listName,
                    taskCount = item.taskCount,
                    isExpanded = item.listIndex == expandedListIndex,
                    isFocused = isFocused,
                    onClick = { onExpandList(item.listIndex) }
                )
            }
            is PickerItem.TaskRow -> {
                val isFocused = index == focusedIndex
                TaskPickerRow(
                    task = item.task,
                    isFocused = isFocused,
                    onClick = {
                        onFocusIndex(index)
                        onSelectTask(item.task)
                    }
                )
            }
        }
    }
}
```

- [ ] **Step 5: Create PickerListHeader composable**

Add a new private composable for the accordion list header with:
- List name text (titleMedium)
- Task count badge ("4 tasks")
- Animated chevron (rotates on expand/collapse)
- Focus glow matching existing task wall focus pattern
- AnimatedVisibility is NOT needed here — the LazyColumn items already appear/disappear based on `expandedListIndex`

```kotlin
@Composable
private fun PickerListHeader(
    listName: String,
    taskCount: Int,
    isExpanded: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isFocused) LocalWallColors.current.accentPrimary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(WallAnimations.SHORT),
        label = "headerBg"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "chevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(WallShapes.MediumCornerRadius.dp))
            .then(if (isFocused) Modifier.border(1.dp, LocalWallColors.current.accentPrimary.copy(alpha = 0.3f), RoundedCornerShape(WallShapes.MediumCornerRadius.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "▾",
                style = MaterialTheme.typography.bodySmall,
                color = LocalWallColors.current.textMuted,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
            )
            Text(
                text = listName,
                style = MaterialTheme.typography.titleSmall,
                color = if (isFocused) LocalWallColors.current.textPrimary else LocalWallColors.current.textSecondary
            )
        }
        Text(
            text = "$taskCount task${if (taskCount != 1) "s" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = LocalWallColors.current.textMuted
        )
    }
}
```

- [ ] **Step 6: Update taskPickerRowCount to account for accordion**

Replace the existing helper function (lines 147-149):

```kotlin
/**
 * Returns the total number of focusable items in the accordion picker.
 * This includes list headers + tasks in the currently expanded list.
 */
fun taskPickerFocusableCount(tasksByList: List<Pair<String, List<Task>>>, expandedListIndex: Int): Int {
    val nonEmptyLists = tasksByList.filter { it.second.isNotEmpty() }
    val headerCount = nonEmptyLists.size
    val expandedTaskCount = nonEmptyLists.getOrNull(expandedListIndex)?.second?.size ?: 0
    return headerCount + expandedTaskCount
}
```

- [ ] **Step 7: Add helper to resolve focused item type**

Add a helper to determine what kind of item is focused:

```kotlin
/**
 * Resolves whether the focused index points to a list header or a task row.
 * Returns the Task if it's a task row, null if it's a header.
 */
fun taskPickerResolveTask(
    tasksByList: List<Pair<String, List<Task>>>,
    expandedListIndex: Int,
    focusedIndex: Int
): Task? {
    var idx = 0
    val nonEmptyLists = tasksByList.filter { it.second.isNotEmpty() }
    for ((listIdx, pair) in nonEmptyLists.withIndex()) {
        if (idx == focusedIndex) return null // it's a header
        idx++
        if (listIdx == expandedListIndex) {
            for (task in pair.second) {
                if (idx == focusedIndex) return task
                idx++
            }
        }
    }
    return null
}

/**
 * Resolves the list index for the header at the given focus index.
 * Returns -1 if the focused index is not a header.
 */
fun taskPickerResolveHeaderListIndex(
    tasksByList: List<Pair<String, List<Task>>>,
    expandedListIndex: Int,
    focusedIndex: Int
): Int {
    var idx = 0
    val nonEmptyLists = tasksByList.filter { it.second.isNotEmpty() }
    for ((listIdx, _) in nonEmptyLists.withIndex()) {
        if (idx == focusedIndex) return listIdx
        idx++
        if (listIdx == expandedListIndex) {
            idx += nonEmptyLists[listIdx].second.size
        }
    }
    return -1
}
```

---

### Task 2: Update CalendarScreen key handler for accordion navigation

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/CalendarScreen.kt`

- [ ] **Step 1: Add expandedListIndex state**

Add to the existing state declarations near line 165-167:

```kotlin
var taskPickerExpandedList by remember { mutableIntStateOf(0) }
```

- [ ] **Step 2: Replace picker key handler**

Replace the existing picker key handler block (lines 376-412). The new logic:

- **Up/Down**: Navigate through focusable items (headers + expanded tasks)
- **Enter on header**: Expand that list, move focus to its first task
- **Enter on task**: Select the task (schedule it)
- **Escape**: Dismiss

```kotlin
if (showTaskPicker) {
    val rowCount = taskPickerFocusableCount(pendingTasksByList, taskPickerExpandedList)
    when (keyEvent.key) {
        Key.DirectionUp, Key.DirectionRight -> {
            if (taskPickerFocusIndex > 0) {
                taskPickerFocusIndex -= 1
                // Auto-expand list if focus lands on a header
                val headerIdx = taskPickerResolveHeaderListIndex(
                    pendingTasksByList, taskPickerExpandedList, taskPickerFocusIndex
                )
                if (headerIdx >= 0 && headerIdx != taskPickerExpandedList) {
                    taskPickerExpandedList = headerIdx
                    // Recalculate — focus order changed
                    // Stay on the header (same position, new expansion)
                }
            }
            return@onKeyEvent true
        }
        Key.DirectionDown, Key.DirectionLeft -> {
            val newCount = taskPickerFocusableCount(pendingTasksByList, taskPickerExpandedList)
            if (taskPickerFocusIndex < newCount - 1) {
                taskPickerFocusIndex += 1
                val headerIdx = taskPickerResolveHeaderListIndex(
                    pendingTasksByList, taskPickerExpandedList, taskPickerFocusIndex
                )
                if (headerIdx >= 0 && headerIdx != taskPickerExpandedList) {
                    taskPickerExpandedList = headerIdx
                }
            }
            return@onKeyEvent true
        }
        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
            val selectedTask = taskPickerResolveTask(
                pendingTasksByList, taskPickerExpandedList, taskPickerFocusIndex
            )
            if (selectedTask != null) {
                // Task selected — schedule it
                val time = taskPickerSlotTime
                if (time != null) {
                    val dragDuration = pendingDragRange?.durationMinutes
                    if (dragDuration != null && dragDuration > 0) {
                        onScheduleTaskForRange(selectedTask, time, dragDuration)
                    } else {
                        onScheduleTaskAtTime(selectedTask, time)
                    }
                }
                showTaskPicker = false
                taskPickerSlotTime = null
                taskPickerFocusIndex = 0
                taskPickerExpandedList = 0
                pendingDragRange = null
            }
            // If it's a header, focus lands on it and expansion already happened via navigation
            // Click on header = move focus to first task in that list
            val headerIdx = taskPickerResolveHeaderListIndex(
                pendingTasksByList, taskPickerExpandedList, taskPickerFocusIndex
            )
            if (headerIdx >= 0) {
                taskPickerExpandedList = headerIdx
                taskPickerFocusIndex += 1 // move to first task
            }
            return@onKeyEvent true
        }
        Key.Escape, Key.Back -> {
            showTaskPicker = false
            taskPickerSlotTime = null
            taskPickerFocusIndex = 0
            taskPickerExpandedList = 0
            pendingDragRange = null
            return@onKeyEvent true
        }
    }
    return@onKeyEvent true
}
```

- [ ] **Step 3: Update TaskPickerOverlay call site**

Update the TaskPickerOverlay invocation (lines 821-847) to pass the new parameters:

```kotlin
TaskPickerOverlay(
    visible = showTaskPicker,
    tasksByList = pendingTasksByList,
    focusedIndex = taskPickerFocusIndex,
    expandedListIndex = taskPickerExpandedList,
    onFocusIndex = { taskPickerFocusIndex = it },
    onExpandList = { listIdx ->
        taskPickerExpandedList = listIdx
        // Recalculate focus to land on the header of the newly expanded list
        var newFocus = 0
        val nonEmpty = pendingTasksByList.filter { it.second.isNotEmpty() }
        for (i in 0 until listIdx.coerceAtMost(nonEmpty.size)) {
            newFocus++ // header
            if (i == taskPickerExpandedList) {
                newFocus += nonEmpty[i].second.size // tasks
            }
        }
        taskPickerFocusIndex = newFocus
    },
    onSelectTask = { task ->
        val time = taskPickerSlotTime
        if (time != null) {
            val dragDuration = pendingDragRange?.durationMinutes
            if (dragDuration != null && dragDuration > 0) {
                onScheduleTaskForRange(task, time, dragDuration)
            } else {
                onScheduleTaskAtTime(task, time)
            }
        }
        showTaskPicker = false
        taskPickerSlotTime = null
        taskPickerFocusIndex = 0
        taskPickerExpandedList = 0
        pendingDragRange = null
    },
    onDismiss = {
        showTaskPicker = false
        taskPickerSlotTime = null
        taskPickerFocusIndex = 0
        taskPickerExpandedList = 0
        pendingDragRange = null
    }
)
```

- [ ] **Step 4: Reset expandedListIndex when picker opens**

At all locations where `showTaskPicker = true` is set (lines 364-366, 582-584, 747-749, 763-765, 786-788, 805-807), add `taskPickerExpandedList = 0` alongside the existing resets.

---

### Task 3: Build and verify

- [ ] **Step 1: Build the project**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Fix any compilation errors and rebuild**
