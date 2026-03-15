# Next Action Spotlight — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 3-task list in Quiet Mode with a single "Next Action" spotlight card that shows the most actionable task with enough context to start immediately.

**Architecture:** New pure function `computeNextAction()` implements a priority cascade algorithm. New composable `NextActionSpotlight` renders the selected task with enriched context (list name, due date, subtask count). The existing `QuietModeContent` is modified to use the spotlight instead of the task list. No ViewModel or data model changes needed — all inputs already exist in `TaskWallScreen`.

**Tech Stack:** Jetpack Compose, existing Task/TaskUrgency models, existing calendar data (optional).

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `ui/screens/TaskWallScreen.kt` | Modify | Replace `quietModeTasks` computation + `QuietModeContent` call |
| `ui/components/NextActionSpotlight.kt` | Create | Spotlight composable + `computeNextAction()` algorithm |

---

## Task 1: Create NextActionSpotlight composable + algorithm

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/ui/components/NextActionSpotlight.kt`

- [ ] **Step 1: Create the spotlight data class and algorithm**

```kotlin
package com.example.todowallapp.ui.components

import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskUrgency
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SpotlightTask(
    val task: Task,
    val listName: String,
    val subtaskCount: Int,
    val subtaskCompleted: Int
)

/**
 * Priority cascade:
 * 1. Overdue tasks (oldest due date first)
 * 2. Due-today tasks
 * 3. Due-soon tasks (within 3 days)
 * 4. Tasks with due dates (nearest first)
 * 5. Tasks without due dates (by position — first in list)
 */
fun computeNextAction(
    allTasks: List<Pair<String, Task>>,  // (listName, task)
    subtaskCounts: Map<String, Pair<Int, Int>>  // taskId -> (total, completed)
): SpotlightTask? {
    if (allTasks.isEmpty()) return null

    val today = LocalDate.now()
    val scored = allTasks
        .filter { (_, task) -> !task.isCompleted && task.parentId == null }
        .sortedWith(
            compareBy<Pair<String, Task>> { (_, task) ->
                when (task.getUrgencyLevel()) {
                    TaskUrgency.OVERDUE -> 0
                    TaskUrgency.DUE_TODAY -> 1
                    TaskUrgency.DUE_SOON -> 2
                    TaskUrgency.NORMAL -> 3
                    TaskUrgency.COMPLETED -> 4
                }
            }.thenBy { (_, task) ->
                task.dueDate?.let { ChronoUnit.DAYS.between(today, it) } ?: Long.MAX_VALUE
            }
        )

    val (listName, bestTask) = scored.firstOrNull() ?: return null
    val (total, completed) = subtaskCounts[bestTask.id] ?: (0 to 0)
    return SpotlightTask(bestTask, listName, total, completed)
}
```

- [ ] **Step 2: Create the spotlight composable**

Below the algorithm in the same file:

```kotlin
@Composable
fun NextActionSpotlight(
    spotlight: SpotlightTask,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val today = LocalDate.now()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label
        Text(
            text = "Your next move",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Spotlight card
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceCard.copy(alpha = 0.12f))
                .border(1.dp, colors.accentPrimary.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Task title
                Text(
                    text = spotlight.task.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Context line: list name + due date + subtasks
                val contextParts = buildList {
                    add(spotlight.listName)
                    spotlight.task.dueDate?.let { due ->
                        val daysUntil = ChronoUnit.DAYS.between(today, due)
                        add(
                            when {
                                daysUntil < 0 -> "${-daysUntil}d overdue"
                                daysUntil == 0L -> "due today"
                                daysUntil == 1L -> "due tomorrow"
                                daysUntil <= 7 -> "due in ${daysUntil}d"
                                else -> "due ${due.format(MonthDayFormatter)}"
                            }
                        )
                    }
                    if (spotlight.subtaskCount > 0) {
                        add("${spotlight.subtaskCompleted}/${spotlight.subtaskCount} subtasks")
                    }
                }

                Text(
                    text = contextParts.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Urgency whisper for overdue
                if (spotlight.task.getUrgencyLevel() == TaskUrgency.OVERDUE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "overdue",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.urgencyOverdue.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
```

Imports needed at top of file:
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.ui.theme.LocalWallColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit
```

- [ ] **Step 3: Build to verify new file compiles**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 2: Wire spotlight into QuietModeContent

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt`

- [ ] **Step 1: Compute spotlight data instead of quietModeTasks**

Replace the `quietModeTasks` computation (around line 383-393) with:

```kotlin
val spotlightTask = remember(sectionModels) {
    val allTasksWithListName = sectionModels.flatMap { model ->
        model.pendingGroups.flatMap { group ->
            listOf(model.taskList.title to group.parent) +
                group.children.map { model.taskList.title to it }
        }
    }
    val subtaskCounts = sectionModels.flatMap { model ->
        model.subtaskProgressByParentId.entries.map { (id, progress) ->
            id to (progress.total to progress.completed)
        }
    }.toMap()
    computeNextAction(allTasksWithListName, subtaskCounts)
}
```

Keep the old `quietModeTasks` as a fallback (rename to `quietModeFallbackTasks`) in case spotlight returns null.

- [ ] **Step 2: Update QuietModeContent to use spotlight**

Change `QuietModeContent` signature to accept both:

```kotlin
@Composable
private fun QuietModeContent(spotlight: SpotlightTask?, fallbackTasks: List<Task>) {
```

Replace the task list rendering inside `QuietModeContent` (the `if (tasks.isEmpty()) ... else ...` block after "Focused Tasks" label) with:

```kotlin
if (spotlight != null) {
    NextActionSpotlight(spotlight = spotlight)
} else if (fallbackTasks.isEmpty()) {
    Text(
        text = "All clear.",
        style = MaterialTheme.typography.displaySmall,
        color = colors.textMuted,
        fontWeight = FontWeight.Light
    )
} else {
    // Fallback: show top tasks as before (shouldn't happen if any pending tasks exist)
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        fallbackTasks.forEach { task ->
            // ... existing whispered task rendering ...
        }
    }
}
```

Remove the "Focused Tasks" label — the spotlight has its own "Your next move" label.

- [ ] **Step 3: Update QuietModeContent call site**

Change the call (around line 897):

```kotlin
ambientTier == AmbientTier.QUIET -> QuietModeContent(
    spotlight = spotlightTask,
    fallbackTasks = quietModeFallbackTasks
)
```

- [ ] **Step 4: Add import for NextActionSpotlight**

Add to imports in TaskWallScreen.kt:
```kotlin
import com.example.todowallapp.ui.components.NextActionSpotlight
import com.example.todowallapp.ui.components.SpotlightTask
import com.example.todowallapp.ui.components.computeNextAction
```

- [ ] **Step 5: Build and verify**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Verification

After all tasks, run full build:
```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp && ./gradlew assembleDebug
```
