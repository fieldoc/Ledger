package com.example.todowallapp.ui.screens

import com.example.todowallapp.ui.theme.LocalWallColors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.todowallapp.data.model.MockData
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.data.model.sortTasksForDisplay
import com.example.todowallapp.ui.components.ClockHeader
import com.example.todowallapp.ui.components.NextActionSpotlight
import com.example.todowallapp.ui.components.SpotlightTask
import com.example.todowallapp.ui.components.computeNextAction
import com.example.todowallapp.ui.components.TaskContextMenu
import com.example.todowallapp.ui.components.TaskContextMenuAction
import com.example.todowallapp.ui.components.ViewSwitcherOption
import com.example.todowallapp.ui.components.ViewSwitcherPill
import com.example.todowallapp.ui.components.SettingsPanel
import com.example.todowallapp.ui.components.TaskItem
import com.example.todowallapp.ui.components.UndoToast
import com.example.todowallapp.ui.components.WaveformVisualizer
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import com.example.todowallapp.ui.utils.AppHapticPattern
import com.example.todowallapp.ui.utils.performAppHaptic
import com.example.todowallapp.ui.utils.rememberLayoutDimensions
import com.example.todowallapp.viewmodel.TaskListWithTasks
import com.example.todowallapp.viewmodel.ThemeMode
import com.example.todowallapp.voice.VoiceInputState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.random.Random

private data class PendingTaskGroup(
    val parent: Task,
    val children: List<Task>,
    val subtaskProgress: SubtaskProgress = SubtaskProgress()
)

data class SubtaskProgress(
    val total: Int = 0,
    val completed: Int = 0
) {
    val fraction: Float
        get() = if (total == 0) 0f else completed.toFloat() / total.toFloat()

    val hasSubtasks: Boolean
        get() = total > 0
}

private data class FolderSectionModel(
    val taskList: TaskList,
    val pendingGroups: List<PendingTaskGroup>,
    val subtaskProgressMap: Map<String, SubtaskProgress>,
    val shownCompletedTasks: List<Task>,
    val completedCount: Int
) {
    val pendingCount: Int = pendingGroups.sumOf { group -> 1 + group.children.size }
}

private enum class FocusNodeType {
    SETTINGS_BUTTON,
    FOLDER_HEADER,
    PENDING_PARENT,
    PENDING_CHILD,
    COMPLETED_HEADER,
    COMPLETED_TASK
}

private data class FocusNode(
    val key: String,
    val folderId: String,
    val type: FocusNodeType,
    val task: Task? = null
)

private fun folderHeaderKey(folderId: String): String = "folder-$folderId"
private fun taskFocusKey(folderId: String, taskId: String): String = "task-$folderId-$taskId"
private fun completedHeaderKey(folderId: String): String = "completed-$folderId"
private const val SettingsButtonKey = "settings-button"

private enum class AmbientTier { ACTIVE, QUIET, SLEEP }
private const val PROMOTE_DELAY_MS = 350L
private const val HOLD_TO_TALK_DELAY_MS = 800L
private const val PositionUpdateThresholdPx = 0.5f
private const val AMBIENT_MODE_THRESHOLD_MS = 30_000L

private fun isInSleepSchedule(startHour: Int, endHour: Int): Boolean {
    val currentHour = LocalTime.now().hour
    return if (startHour > endHour) {
        currentHour >= startHour || currentHour < endHour
    } else {
        currentHour in startHour until endHour
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun TaskWallScreen(
    taskLists: List<TaskListWithTasks>,
    onTaskToggle: (Task) -> Unit,
    onTaskDelete: (Task) -> Unit = {},
    onScheduleTask: (Task) -> Unit = {},
    onRefresh: () -> Unit,
    onSwitchToCalendar: () -> Unit = {},
    selectedViewKey: String = "tasks",
    scheduledTaskIds: Set<String> = emptySet(),
    scheduledTaskTimes: Map<String, LocalDateTime> = emptyMap(),
    onOpenScheduledTask: (Task, LocalDateTime?) -> Unit = { _, _ -> },
    onSelectTaskList: (String) -> Unit = {},
    voiceState: VoiceInputState = VoiceInputState.Idle,
    onStartVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    onCancelVoice: () -> Unit = {},
    onConfirmVoice: (targetListId: String?) -> Unit = {},
    onDismissVoiceError: () -> Unit = {},
    error: String? = null,
    onDismissError: () -> Unit = {},
    isSyncing: Boolean = false,
    isLoading: Boolean = false,
    isOnline: Boolean = true,
    lastSyncTime: LocalDateTime? = null,
    lastSyncSuccess: Boolean? = null,
    undoVisible: Boolean = false,
    undoMessage: String? = null,
    onUndo: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.AUTO,
    lightStartHour: Int = 8,
    lightEndHour: Int = 19,
    sleepStartHour: Int = 23,
    sleepEndHour: Int = 7,
    syncIntervalMinutes: Int = 5,
    onThemeSettingsChange: (ThemeMode, Int, Int) -> Unit = { _, _, _ -> },
    onSleepScheduleChange: (Int, Int) -> Unit = { _, _ -> },
    onSyncIntervalChange: (Int) -> Unit = {},
    onSwitchMode: () -> Unit = {},
    onSignOut: () -> Unit = {},
    ambientLightMonitoringEnabled: Boolean = true,
    onSetBrightness: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = LocalWallColors.current
    val dims = rememberLayoutDimensions()
    var selectedFocusKey by remember { mutableStateOf<String?>(null) }
    var ambientTier by remember { mutableStateOf(AmbientTier.ACTIVE) }
    val isAmbientMode = ambientTier != AmbientTier.ACTIVE
    var lastInteractionTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lowLightStartTimeMs by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var ambientPromptOffsetXDp by remember { mutableIntStateOf(0) }
    var ambientPromptOffsetYDp by remember { mutableIntStateOf(0) }
    val holdToTalkScope = rememberCoroutineScope()
    var holdToTalkKey by remember { mutableStateOf<Key?>(null) }
    var holdToTalkActive by remember { mutableStateOf(false) }
    var promoteTriggered by remember { mutableStateOf(false) }
    var holdToTalkStartJob by remember { mutableStateOf<Job?>(null) }
    var holdProgressJob by remember { mutableStateOf<Job?>(null) }
    var holdProgressFraction by remember { mutableStateOf(0f) }
    var isViewSwitcherFocused by remember { mutableStateOf(false) }
    var contextMenuTask by remember { mutableStateOf<Task?>(null) }
    var contextMenuSelectedIndex by remember { mutableIntStateOf(0) }
    val viewSwitcherOptions = remember {
        listOf(
            ViewSwitcherOption(key = "tasks", label = "Tasks"),
            ViewSwitcherOption(key = "calendar", label = "Calendar")
        )
    }
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
    val contextMenuActions = if (contextMenuTask?.isCompleted == true) completedContextMenuActions else pendingContextMenuActions

    // Focus breadcrumb state (Task 5)
    val breadcrumbVisible = remember { Animatable(0f) }
    val breadcrumbScope = rememberCoroutineScope()

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoice()
        } else {
            Toast.makeText(context, "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    val startVoiceWithPermission = {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
                onStartVoice()
            }
            else -> {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val sectionModels = remember(taskLists) { buildFolderSectionModels(taskLists) }
    var expandedFolderId by remember { mutableStateOf<String?>(null) }
    val ambientPromptAlpha by if (isAmbientMode) {
        val ambientPromptTransition = rememberInfiniteTransition(label = "ambientPromptTransition")
        ambientPromptTransition.animateFloat(
            initialValue = if (ambientTier == AmbientTier.SLEEP) 0.62f else 0.68f,
            targetValue = if (ambientTier == AmbientTier.SLEEP) 0.88f else 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambientPromptAlpha"
        )
    } else {
        rememberUpdatedState(1f)
    }

    val focusOrder = remember(sectionModels, expandedFolderId) {
        buildFocusOrder(sectionModels, expandedFolderId)
    }
    val focusIndexByKey = remember(focusOrder) {
        buildMap(focusOrder.size) {
            focusOrder.forEachIndexed { index, node ->
                put(node.key, index)
            }
        }
    }
    val selectedFolderId by remember(selectedFocusKey, focusOrder, focusIndexByKey) {
        derivedStateOf {
            selectedFocusKey
                ?.let { key -> focusIndexByKey[key] }
                ?.let(focusOrder::getOrNull)
                ?.takeUnless { node -> node.type == FocusNodeType.SETTINGS_BUTTON }
                ?.folderId
        }
    }
    val firstTaskFocusKey by remember(focusOrder) {
        derivedStateOf {
            focusOrder.firstOrNull { node ->
                node.type == FocusNodeType.PENDING_PARENT ||
                    node.type == FocusNodeType.PENDING_CHILD ||
                    node.type == FocusNodeType.COMPLETED_TASK
            }?.key
        }
    }
    val spotlightTask = remember(sectionModels) {
        val allTasksWithListName = sectionModels.flatMap { model ->
            model.pendingGroups.flatMap { group ->
                listOf(model.taskList.title to group.parent) +
                    group.children.map { model.taskList.title to it }
            }
        }
        val subtaskCounts = sectionModels.flatMap { model ->
            model.subtaskProgressMap.entries.map { (id, progress) ->
                id to (progress.total to progress.completed)
            }
        }.toMap()
        computeNextAction(allTasksWithListName, subtaskCounts)
    }

    // Breadcrumb: derive current folder name and task title from focus state
    val breadcrumbInfo by remember(selectedFocusKey, focusOrder, focusIndexByKey, sectionModels) {
        derivedStateOf {
            val node = selectedFocusKey
                ?.let { key -> focusIndexByKey[key] }
                ?.let(focusOrder::getOrNull)
            if (node == null || node.type == FocusNodeType.SETTINGS_BUTTON) {
                null
            } else {
                val folderName = sectionModels.firstOrNull { it.taskList.id == node.folderId }?.taskList?.title ?: ""
                val taskTitle = node.task?.title ?: ""
                folderName to taskTitle
            }
        }
    }

    // Breadcrumb auto-hide: show on focus change, hide after 3s
    LaunchedEffect(selectedFocusKey) {
        if (breadcrumbInfo != null && breadcrumbInfo?.second?.isNotEmpty() == true) {
            breadcrumbVisible.animateTo(1f, tween(WallAnimations.SHORT))
            delay(3000)
            breadcrumbVisible.animateTo(0f, tween(WallAnimations.SHORT))
        }
    }

    LaunchedEffect(sectionModels) {
        if (sectionModels.none { it.taskList.id == expandedFolderId }) {
            expandedFolderId = sectionModels.firstOrNull()?.taskList?.id
        }
    }

    LaunchedEffect(expandedFolderId) {
        expandedFolderId?.let(onSelectTaskList)
    }


    LaunchedEffect(focusOrder, focusIndexByKey) {
        if (focusOrder.isEmpty()) {
            selectedFocusKey = null
            if (!isLoading) {
                isViewSwitcherFocused = true
            }
            return@LaunchedEffect
        }

        if (selectedFocusKey == null) {
            selectedFocusKey = focusOrder.firstOrNull { node ->
                node.type == FocusNodeType.PENDING_PARENT ||
                    node.type == FocusNodeType.PENDING_CHILD ||
                    node.type == FocusNodeType.COMPLETED_TASK
            }?.key ?: focusOrder.first().key
            return@LaunchedEffect
        }

        if (selectedFocusKey !in focusIndexByKey) {
            val fallback = focusOrder.firstOrNull { node ->
                node.folderId == expandedFolderId && (
                    node.type == FocusNodeType.PENDING_PARENT ||
                        node.type == FocusNodeType.PENDING_CHILD ||
                        node.type == FocusNodeType.COMPLETED_TASK
                    )
            }?.key ?: expandedFolderId?.let(::folderHeaderKey)
                ?: focusOrder.first().key
            selectedFocusKey = fallback
        }
    }

    LaunchedEffect(selectedFocusKey, focusOrder, focusIndexByKey, expandedFolderId) {
        val selectedNode = selectedFocusKey
            ?.let { key -> focusIndexByKey[key] }
            ?.let(focusOrder::getOrNull)
            ?: return@LaunchedEffect
        if (selectedNode.type != FocusNodeType.SETTINGS_BUTTON && selectedNode.folderId != expandedFolderId) {
            expandedFolderId = selectedNode.folderId
        }
    }

    LaunchedEffect(selectedFolderId, sectionModels) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        val folderId = selectedFolderId ?: return@LaunchedEffect
        val folderIndex = sectionModels.indexOfFirst { it.taskList.id == folderId }
        if (folderIndex < 0) return@LaunchedEffect

        val folderAlreadyVisible = listState.layoutInfo.visibleItemsInfo.any { item ->
            item.index == folderIndex
        }
        if (!folderAlreadyVisible) {
            listState.animateScrollToItem(folderIndex)
        }
    }

    // Ambient mode timeout
    LaunchedEffect(lastInteractionTimeMs) {
        delay(AMBIENT_MODE_THRESHOLD_MS)
        val now = System.currentTimeMillis()
        if (now - lastInteractionTimeMs >= AMBIENT_MODE_THRESHOLD_MS) {
            ambientTier = if (isInSleepSchedule(sleepStartHour, sleepEndHour)) {
                AmbientTier.SLEEP
            } else {
                AmbientTier.QUIET
            }
        }
    }

    // Periodic sleep schedule check
    LaunchedEffect(sleepStartHour, sleepEndHour) {
        while (true) {
            delay(60_000)
            if (ambientTier == AmbientTier.QUIET && isInSleepSchedule(sleepStartHour, sleepEndHour)) {
                ambientTier = AmbientTier.SLEEP
            }
        }
    }

    LaunchedEffect(isAmbientMode) {
        if (!isAmbientMode) {
            ambientPromptOffsetXDp = 0
            ambientPromptOffsetYDp = 0
            return@LaunchedEffect
        }

        while (true) {
            ambientPromptOffsetXDp = Random.nextInt(-20, 21)
            ambientPromptOffsetYDp = Random.nextInt(-10, 11)
            delay(180_000)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(showSettings) {
        if (!showSettings) {
            focusRequester.requestFocus()
        }
    }

    // Sensor handling
    if (ambientLightMonitoringEnabled) {
        DisposableEffect(Unit) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
            val lightSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
            if (lightSensor == null || sensorManager == null) return@DisposableEffect onDispose {}
            
            val listener = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                    val lux = event?.values?.firstOrNull() ?: return
                    val now = System.currentTimeMillis()
                    if (lux < 10f) {
                        if (lowLightStartTimeMs == null) {
                            lowLightStartTimeMs = now
                        }
                        val lowLightDurationMs = now - (lowLightStartTimeMs ?: now)
                        val inactivityDurationMs = now - lastInteractionTimeMs
                        if (ambientTier == AmbientTier.ACTIVE && lowLightDurationMs >= 30_000L && inactivityDurationMs >= 30_000L) {
                            ambientTier = if (isInSleepSchedule(sleepStartHour, sleepEndHour)) AmbientTier.SLEEP else AmbientTier.QUIET
                        }
                    } else {
                        lowLightStartTimeMs = null
                    }
                }
                override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, lightSensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    fun wakeUp() {
        ambientTier = AmbientTier.ACTIVE
        lastInteractionTimeMs = System.currentTimeMillis()
        lowLightStartTimeMs = null
        onSetBrightness(-1f)
    }

    fun navigateUp() {
        wakeUp()
        if (isViewSwitcherFocused) return
        val firstFolderHeader = focusOrder.firstOrNull { node -> node.type == FocusNodeType.FOLDER_HEADER }
        val settingsNodeKey = focusOrder.firstOrNull { node -> node.type == FocusNodeType.SETTINGS_BUTTON }?.key
        val currentIndex = selectedFocusKey?.let(focusIndexByKey::get) ?: 0
        val selectedNode = focusOrder.getOrNull(currentIndex)
        val isOnFirstHeader = selectedNode?.type == FocusNodeType.FOLDER_HEADER && selectedNode.key == firstFolderHeader?.key
        
        if (isOnFirstHeader) {
            if (settingsNodeKey != null) selectedFocusKey = settingsNodeKey else isViewSwitcherFocused = true

            performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
            return
        }
        if (selectedNode?.type == FocusNodeType.SETTINGS_BUTTON) {
            isViewSwitcherFocused = true

            performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
            return
        }
        if (focusOrder.isNotEmpty()) {
            val nextIndex = (currentIndex - 1).coerceAtLeast(0)
            if (nextIndex != currentIndex) {
                selectedFocusKey = focusOrder[nextIndex].key
    
                performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
            }
        }
    }

    fun navigateDown() {
        wakeUp()
        if (isViewSwitcherFocused) {
            isViewSwitcherFocused = false
            selectedFocusKey = focusOrder.firstOrNull { node -> node.type == FocusNodeType.SETTINGS_BUTTON }?.key
                ?: firstTaskFocusKey ?: selectedFocusKey ?: focusOrder.firstOrNull()?.key

            performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
            return
        }
        val currentIndex = selectedFocusKey?.let(focusIndexByKey::get) ?: 0
        val selectedNode = focusOrder.getOrNull(currentIndex)
        if (selectedNode?.type == FocusNodeType.SETTINGS_BUTTON) {
            selectedFocusKey = focusOrder.firstOrNull { node -> node.type == FocusNodeType.FOLDER_HEADER }?.key
                ?: firstTaskFocusKey ?: selectedFocusKey ?: focusOrder.firstOrNull()?.key

            performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
            return
        }
        if (focusOrder.isNotEmpty()) {
            val nextIndex = (currentIndex + 1).coerceAtMost(focusOrder.size - 1)
            if (nextIndex != currentIndex) {
                selectedFocusKey = focusOrder[nextIndex].key
    
                performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
            }
        }
    }

    fun selectCurrent() {
        wakeUp()
        if (isViewSwitcherFocused) {
            performAppHaptic(view, context, AppHapticPattern.CONFIRM)
            onSwitchToCalendar()
            return
        }
        val selectedNode = selectedFocusKey?.let(focusIndexByKey::get)?.let(focusOrder::getOrNull) ?: return
        when (selectedNode.type) {
            FocusNodeType.SETTINGS_BUTTON -> {
                performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                showSettings = true
            }
            FocusNodeType.FOLDER_HEADER -> {
                expandedFolderId = selectedNode.folderId
                selectedFocusKey = focusOrder.firstOrNull { node ->
                    node.folderId == selectedNode.folderId && (node.type == FocusNodeType.PENDING_PARENT || node.type == FocusNodeType.PENDING_CHILD || node.type == FocusNodeType.COMPLETED_TASK)
                }?.key ?: selectedNode.key
            }
            FocusNodeType.PENDING_PARENT -> {
                val parentTask = selectedNode.task ?: return
                performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                onTaskToggle(parentTask)
            }
            FocusNodeType.PENDING_CHILD, FocusNodeType.COMPLETED_TASK -> {
                selectedNode.task?.let { task ->
                    performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                    onTaskToggle(task)
                }
            }
            else -> Unit
        }
    }

    val screenAlpha by animateFloatAsState(
        targetValue = when (ambientTier) {
            AmbientTier.ACTIVE -> 1f
            AmbientTier.QUIET -> 0.42f
            AmbientTier.SLEEP -> 0f
        },
        animationSpec = tween(durationMillis = 1000),
        label = "screenAlpha"
    )

    // LCD brightness control for ambient tiers
    LaunchedEffect(ambientTier) {
        when (ambientTier) {
            AmbientTier.SLEEP -> onSetBrightness(0.01f)
            AmbientTier.QUIET -> onSetBrightness(0.15f)
            AmbientTier.ACTIVE -> onSetBrightness(-1f)
        }
    }

    BackHandler(enabled = contextMenuTask != null) { contextMenuTask = null }
    BackHandler(enabled = showSettings) { showSettings = false }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalWallColors.current.surfaceBlack)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                // Encoder navigation when context menu is open
                if (contextMenuTask != null) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            when (keyEvent.key) {
                                Key.DirectionUp, Key.DirectionRight -> {
                                    contextMenuSelectedIndex = (contextMenuSelectedIndex - 1).coerceAtLeast(0)
                                    performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
                                }
                                Key.DirectionDown, Key.DirectionLeft -> {
                                    contextMenuSelectedIndex = (contextMenuSelectedIndex + 1).coerceAtMost(contextMenuActions.size - 1)
                                    performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
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
                                }
                                else -> {
                                    contextMenuTask = null
                                    contextMenuSelectedIndex = 0
                                }
                            }
                        }
                        else -> Unit
                    }
                    return@onKeyEvent true
                }
                // Settings panel open — encoder click dismisses
                if (showSettings) {
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)) {
                        showSettings = false
                        performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                    }
                    return@onKeyEvent true
                }
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        when (keyEvent.key) {
                            Key.DirectionUp, Key.DirectionRight -> { navigateUp(); true }
                            Key.DirectionDown, Key.DirectionLeft -> { navigateDown(); true }
                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                if (voiceState is VoiceInputState.Idle) {
                                    if (holdToTalkKey == keyEvent.key) return@onKeyEvent true
                                    holdToTalkKey = keyEvent.key
                                    holdToTalkActive = false
                                    promoteTriggered = false
                                    holdProgressFraction = 0f
                                    holdToTalkStartJob?.cancel()
                                    holdProgressJob?.cancel()
                                    holdProgressJob = holdToTalkScope.launch {
                                        while (holdToTalkKey == keyEvent.key && holdProgressFraction < 1f) {
                                            holdProgressFraction += 0.02f
                                            delay(16)
                                        }
                                    }
                                    holdToTalkStartJob = holdToTalkScope.launch {
                                        delay(PROMOTE_DELAY_MS)
                                        if (holdToTalkKey == keyEvent.key) {
                                            wakeUp()
                                            promoteTriggered = true
                                            // Handle promotion
                                        }
                                        delay(HOLD_TO_TALK_DELAY_MS - PROMOTE_DELAY_MS)
                                        if (holdToTalkKey == keyEvent.key) {
                                            holdToTalkActive = true
                                            startVoiceWithPermission()
                                        }
                                    }
                                    true
                                } else {
                                    selectCurrent(); true
                                }
                            }
                            else -> { wakeUp(); false }
                        }
                    }
                    KeyEventType.KeyUp -> {
                        if (holdToTalkKey == keyEvent.key) {
                            holdToTalkStartJob?.cancel()
                            holdProgressJob?.cancel()
                            holdToTalkKey = null
                            holdProgressFraction = 0f
                            if (holdToTalkActive) {
                                if (voiceState is VoiceInputState.Listening) onStopVoice()
                                holdToTalkActive = false
                            } else if (!promoteTriggered) {
                                selectCurrent()
                            } else {
                                // Medium hold (350-800ms) released — open context menu
                                val focusedNode = selectedFocusKey?.let(focusIndexByKey::get)?.let(focusOrder::getOrNull)
                                val task = focusedNode?.task
                                if (task != null) {
                                    contextMenuTask = task
                                    contextMenuSelectedIndex = 0
                                    performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                                }
                            }
                            promoteTriggered = false
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .alpha(screenAlpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = ambientPromptOffsetXDp.dp, y = ambientPromptOffsetYDp.dp)
                .padding(top = dims.topPadding, start = dims.horizontalPadding, end = dims.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Focus breadcrumb (Task 5)
            if (breadcrumbVisible.value > 0f && breadcrumbInfo != null) {
                val (folderName, taskTitle) = breadcrumbInfo!!
                if (taskTitle.isNotEmpty()) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.accentPrimary)) {
                                append(folderName)
                            }
                            append(" \u203A ")
                            append(taskTitle)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 0.3.sp
                        ),
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .alpha(breadcrumbVisible.value)
                            .padding(bottom = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (dims.contentMaxWidth != Dp.Unspecified)
                            Modifier.widthIn(max = dims.contentMaxWidth)
                        else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ClockHeader(
                    isAmbientMode = isAmbientMode,
                    isOnline = isOnline,
                    lastSyncTime = lastSyncTime,
                    lastSyncSuccess = lastSyncSuccess
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSyncing && !isAmbientMode) SyncIndicator()
                    if (error != null && !isAmbientMode) ErrorBadge(error = error, onDismiss = onDismissError)
                    if (!isAmbientMode) {
                        SettingsShortcutButton(
                            isFocused = !isViewSwitcherFocused && selectedFocusKey == SettingsButtonKey,
                            onClick = {
                                wakeUp()
                                isViewSwitcherFocused = false
                                selectedFocusKey = SettingsButtonKey
                                showSettings = true
                            }
                        )
                    }
                    ViewSwitcherPill(
                        options = viewSwitcherOptions,
                        selectedKey = selectedViewKey,
                        onSelect = { key ->
                            isViewSwitcherFocused = true
                            if (key == "calendar") onSwitchToCalendar()
                        },
                        isAmbientMode = isAmbientMode,
                        modifier = if (isViewSwitcherFocused) {
                            Modifier.border(1.5.dp, colors.accentPrimary.copy(alpha = 0.7f), RoundedCornerShape(999.dp)).padding(1.5.dp)
                        } else Modifier
                    )
                }
            }

            Spacer(modifier = Modifier.height(dims.headerContentGap))

            when {
                isLoading -> LoadingState(onRefresh = onRefresh, onStartVoice = startVoiceWithPermission)
                ambientTier == AmbientTier.QUIET -> QuietModeContent(spotlight = spotlightTask)
                taskLists.isEmpty() -> EmptyState(isAmbientMode = isAmbientMode, onRefresh = onRefresh, onStartVoice = startVoiceWithPermission)
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = dims.bottomContentPadding),
                        verticalArrangement = Arrangement.spacedBy(dims.folderSpacing),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (dims.contentMaxWidth != Dp.Unspecified)
                                    Modifier.widthIn(max = dims.contentMaxWidth)
                                else Modifier
                            )
                    ) {
                        items(
                            items = sectionModels,
                            key = { model -> model.taskList.id }
                        ) { model ->
                            FolderSection(
                                model = model,
                                isExpanded = model.taskList.id == expandedFolderId,
                                selectedFocusKey = if (selectedFolderId == model.taskList.id) selectedFocusKey else null,
                                isAmbientMode = isAmbientMode,
                                scheduledTaskIds = scheduledTaskIds,
                                scheduledTaskTimes = scheduledTaskTimes,
                                holdProgressFraction = holdProgressFraction,
                                onHeaderClick = {
                                    wakeUp()
                                    isViewSwitcherFocused = false
                                    if (expandedFolderId == model.taskList.id) {
                                        expandedFolderId = null
                                    } else {
                                        expandedFolderId = model.taskList.id
                                    }
                                    selectedFocusKey = folderHeaderKey(model.taskList.id)
                                    performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
                                },
                                onParentClick = { parent ->
                                    wakeUp()
                                    isViewSwitcherFocused = false
                                    val parentFocusKey = taskFocusKey(model.taskList.id, parent.id)
                                    selectedFocusKey = parentFocusKey
                                    performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                                    onTaskToggle(parent)
                                },
                                onTaskToggle = { task ->
                                    wakeUp()
                                    isViewSwitcherFocused = false
                                    selectedFocusKey = taskFocusKey(model.taskList.id, task.id)
                                    performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                                    onTaskToggle(task)
                                },
                                onTaskLongClick = { task ->
                                    wakeUp()
                                    isViewSwitcherFocused = false
                                    selectedFocusKey = taskFocusKey(model.taskList.id, task.id)
                                    contextMenuTask = task
                                    contextMenuSelectedIndex = 0
                                }
                            )
                        }
                    }
                }
            }
        }

        // Overlays
        if (showSettings) {
            SettingsPanel(
                themeMode = themeMode,
                lightStartHour = lightStartHour,
                lightEndHour = lightEndHour,
                sleepStartHour = sleepStartHour,
                sleepEndHour = sleepEndHour,
                syncIntervalMinutes = syncIntervalMinutes,
                onThemeSettingsChange = onThemeSettingsChange,
                onSleepScheduleChange = onSleepScheduleChange,
                onSyncIntervalChange = onSyncIntervalChange,
                onSwitchMode = onSwitchMode,
                onSignOut = onSignOut,
                onDismiss = { showSettings = false }
            )
        }

        // Context menu overlay
        TaskContextMenu(
            visible = contextMenuTask != null,
            title = contextMenuTask?.title ?: "",
            actions = contextMenuActions,
            selectedActionIndex = contextMenuSelectedIndex,
            onActionSelected = { action ->
                val task = contextMenuTask ?: return@TaskContextMenu
                when (action.id) {
                    "schedule" -> onScheduleTask(task)
                    "restore" -> onTaskToggle(task)
                    "delete" -> onTaskDelete(task)
                }
                contextMenuTask = null
                contextMenuSelectedIndex = 0
            },
            onDismiss = {
                contextMenuTask = null
                contextMenuSelectedIndex = 0
            }
        )

        // Voice FAB — visible when task list is populated and not in ambient/voice mode
        if (!isAmbientMode && !isLoading && taskLists.isNotEmpty() && voiceState is VoiceInputState.Idle && !showSettings) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 32.dp)
            ) {
                VoiceFab(onClick = { wakeUp(); startVoiceWithPermission() })
            }
        }

        // Undo toast — bottom-center, above tap-to-wake overlay
        UndoToast(
            visible = undoVisible,
            taskTitle = undoMessage,
            onUndo = onUndo,
            onDismiss = onDismissUndo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )

        // Tap-to-wake overlay for ambient modes
        if (isAmbientMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { wakeUp() }
            )
        }
    }
}

@Composable
private fun FolderSection(
    model: FolderSectionModel,
    isExpanded: Boolean,
    selectedFocusKey: String?,
    isAmbientMode: Boolean,
    scheduledTaskIds: Set<String>,
    scheduledTaskTimes: Map<String, LocalDateTime>,
    holdProgressFraction: Float = 0f,
    onHeaderClick: () -> Unit,
    onParentClick: (Task) -> Unit,
    onTaskToggle: (Task) -> Unit,
    onTaskLongClick: (Task) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val folderId = model.taskList.id
    val colors = LocalWallColors.current
    val sectionShape = RoundedCornerShape(32.dp)
    val isHeaderSelected = selectedFocusKey == folderHeaderKey(folderId)
    
    val sectionBackground by animateColorAsState(
        targetValue = when {
            isAmbientMode -> Color.Transparent
            isExpanded -> colors.surfaceCard.copy(alpha = 0.25f)
            isHeaderSelected -> colors.surfaceCard.copy(alpha = 0.4f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "folderSectionBackground"
    )

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "folderChevronRotation"
    )

    // Folder urgency glow (Task 4): collapsed folder with overdue tasks
    val hasOverdue = !isExpanded && model.pendingGroups.any { group ->
        group.parent.getUrgencyLevel() == TaskUrgency.OVERDUE ||
            group.children.any { it.getUrgencyLevel() == TaskUrgency.OVERDUE }
    }

    // Progress bar: completion ratio
    val totalCount = model.pendingCount + model.completedCount
    val progressFraction = if (totalCount > 0) model.completedCount.toFloat() / totalCount else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(WallAnimations.MEDIUM),
        label = "folderProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hasOverdue) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = sectionShape,
                        spotColor = colors.urgencyOverdue.copy(alpha = 0.10f),
                        ambientColor = Color.Transparent
                    )
                } else Modifier
            )
            .clip(sectionShape)
            .background(sectionBackground)
            .then(if (isExpanded && !isAmbientMode) Modifier.border(1.dp, Color(0x1AFFFFFF), sectionShape) else Modifier)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onHeaderClick).padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.taskList.title.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 0.8.sp
                        ),
                        color = when {
                            isAmbientMode -> colors.ambientText
                            isExpanded -> colors.accentPrimary
                            else -> colors.textSecondary
                        },
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Task count (10sp, textMuted)
                    Text(
                        text = "${model.pendingCount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Expand indicator
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.accentPrimary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isExpanded) "\u25BE" else "\u25B8",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = colors.accentPrimary
                        )
                    }
                }
                if (!isAmbientMode) {
                    Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = if (isExpanded) colors.accentPrimary else colors.textMuted, modifier = Modifier.size(28.dp).rotate(chevronRotation))
                }
            }

            // Progress bar (Task 4)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.borderColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animatedProgress)
                        .background(colors.accentSubtle.copy(alpha = 0.3f))
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(tween(WallAnimations.MEDIUM)),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(tween(WallAnimations.SHORT))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 16.dp).animateContentSize(animationSpec = tween(WallAnimations.MEDIUM)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    model.pendingGroups.forEach { group ->
                        ParentChildGroup(group, folderId, selectedFocusKey, isAmbientMode, isExpanded, scheduledTaskIds, scheduledTaskTimes, holdProgressFraction, onParentClick, onTaskToggle, onTaskLongClick)
                    }

                    // Completed section divider + completed tasks (Task 12)
                    if (model.shownCompletedTasks.isNotEmpty()) {
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

                        model.shownCompletedTasks.forEach { completedTask ->
                            val completedKey = taskFocusKey(folderId, completedTask.id)
                            TaskItem(
                                task = completedTask,
                                isSelected = selectedFocusKey == completedKey,
                                isChild = false,
                                isAmbientMode = isAmbientMode,
                                onClick = { onTaskToggle(completedTask) },
                                onLongClick = { onTaskLongClick(completedTask) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentChildGroup(
    group: PendingTaskGroup,
    folderId: String,
    selectedFocusKey: String?,
    isAmbientMode: Boolean,
    isExpanded: Boolean,
    scheduledTaskIds: Set<String>,
    scheduledTaskTimes: Map<String, LocalDateTime>,
    holdProgressFraction: Float,
    onParentClick: (Task) -> Unit,
    onTaskToggle: (Task) -> Unit,
    onTaskLongClick: (Task) -> Unit = {}
) {
    val parentKey = taskFocusKey(folderId, group.parent.id)
    val shouldShowChildren = isExpanded && group.children.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TaskItem(
            task = group.parent,
            isSelected = selectedFocusKey == parentKey,
            isChild = false,
            isAmbientMode = isAmbientMode,
            scheduledTaskIds = scheduledTaskIds,
            scheduledStartTime = scheduledTaskTimes[group.parent.id],
            isExpanded = shouldShowChildren,
            holdProgressFraction = if (selectedFocusKey == parentKey) holdProgressFraction else 0f,
            onClick = { onParentClick(group.parent) },
            onLongClick = { onTaskLongClick(group.parent) }
        )

        if (shouldShowChildren) {
            group.children.forEach { child ->
                val childKey = taskFocusKey(folderId, child.id)
                TaskItem(
                    task = child,
                    isSelected = selectedFocusKey == childKey,
                    isChild = true,
                    isAmbientMode = isAmbientMode,
                    scheduledTaskIds = scheduledTaskIds,
                    scheduledStartTime = scheduledTaskTimes[child.id],
                    holdProgressFraction = if (selectedFocusKey == childKey) holdProgressFraction else 0f,
                    modifier = Modifier.padding(start = 32.dp),
                    onClick = { onTaskToggle(child) },
                    onLongClick = { onTaskLongClick(child) }
                )
            }
        }
    }
}

private fun buildFolderSectionModels(taskLists: List<TaskListWithTasks>): List<FolderSectionModel> {
    return taskLists.map { listWithTasks ->
        val pendingTasks = listWithTasks.tasks.filter { !it.isCompleted }
        val subtaskProgressByParentId = listWithTasks.tasks.filter { it.parentId != null }.groupBy { it.parentId!! }.mapValues { (_, subtasks) ->
            SubtaskProgress(total = subtasks.size, completed = subtasks.count { it.isCompleted })
        }
        val completedTasks = listWithTasks.tasks.filter { it.isCompleted }.sortedByDescending { it.completedAt }
        FolderSectionModel(listWithTasks.taskList, buildPendingTaskGroups(pendingTasks, subtaskProgressByParentId), subtaskProgressByParentId, completedTasks.take(5), completedTasks.size)
    }
}

private fun buildPendingTaskGroups(tasks: List<Task>, subtaskProgressByParentId: Map<String, SubtaskProgress>): List<PendingTaskGroup> {
    val parents = sortTasksForDisplay(tasks.filter { it.parentId == null })
    val childrenByParent = tasks.filter { it.parentId != null }.groupBy { it.parentId }
    val groups = mutableListOf<PendingTaskGroup>()
    val addedTaskIds = mutableSetOf<String>()
    parents.forEach { parent ->
        val children = childrenByParent[parent.id]?.sortedBy { it.position }.orEmpty()
        groups += PendingTaskGroup(parent, children, subtaskProgressByParentId[parent.id] ?: SubtaskProgress(total = children.size, completed = 0))
        addedTaskIds += parent.id
        children.forEach { addedTaskIds += it.id }
    }
    tasks.filter { it.id !in addedTaskIds }.let(::sortTasksForDisplay).forEach { orphan ->
        groups += PendingTaskGroup(orphan, emptyList(), subtaskProgressByParentId[orphan.id] ?: SubtaskProgress())
    }
    return groups
}

private fun buildFocusOrder(models: List<FolderSectionModel>, expandedFolderId: String?): List<FocusNode> {
    val focusOrder = mutableListOf<FocusNode>()
    focusOrder += FocusNode(key = SettingsButtonKey, folderId = "", type = FocusNodeType.SETTINGS_BUTTON)
    models.forEach { model ->
        val folderId = model.taskList.id
        focusOrder += FocusNode(key = folderHeaderKey(folderId), folderId = folderId, type = FocusNodeType.FOLDER_HEADER)
        if (folderId == expandedFolderId) {
            model.pendingGroups.forEach { group ->
                focusOrder += FocusNode(key = taskFocusKey(folderId, group.parent.id), folderId = folderId, type = FocusNodeType.PENDING_PARENT, task = group.parent)
                group.children.forEach { child ->
                    focusOrder += FocusNode(key = taskFocusKey(folderId, child.id), folderId = folderId, type = FocusNodeType.PENDING_CHILD, task = child)
                }
            }
        }
    }
    return focusOrder
}

@Composable
private fun SyncIndicator() {
    val colors = LocalWallColors.current
    val transition = rememberInfiniteTransition(label = "syncPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "syncAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.accentPrimary.copy(alpha = 0.1f))
            .border(1.dp, colors.accentPrimary.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(colors.accentPrimary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Syncing",
            style = MaterialTheme.typography.labelSmall,
            color = colors.accentPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ErrorBadge(error: String, onDismiss: () -> Unit) {
    // Auto-dismiss after 10 seconds — kiosk can't rely on user tapping the badge
    LaunchedEffect(error) {
        delay(10_000)
        onDismiss()
    }
    val colors = LocalWallColors.current
    Surface(
        modifier = Modifier.clickable(onClick = onDismiss),
        shape = RoundedCornerShape(16.dp),
        color = colors.urgencyOverdueSubtle.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, colors.urgencyOverdue.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = colors.urgencyOverdue,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SettingsShortcutButton(isFocused: Boolean, onClick: () -> Unit) {
    val colors = LocalWallColors.current
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) colors.surfaceCard.copy(alpha = 0.6f) else colors.surfaceCard.copy(alpha = 0.2f),
        animationSpec = tween(WallAnimations.SHORT),
        label = "settingsBg"
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, if (isFocused) colors.accentPrimary.copy(alpha = 0.5f) else Color(0x1AFFFFFF), shape)
            .clickable(onClick = onClick, interactionSource = interactionSource, indication = null),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "Settings",
            tint = if (isFocused) colors.accentPrimary else colors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun LoadingState(onRefresh: () -> Unit, onStartVoice: () -> Unit) {
    val colors = LocalWallColors.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = colors.accentPrimary,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Preparing your wall...",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun QuietModeContent(spotlight: SpotlightTask?) {
    val colors = LocalWallColors.current

    // Live clock for quiet mode
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large faint clock — barely visible ambient utility
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                fontSize = 32.sp
            ),
            color = colors.textFaint,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (spotlight != null) {
            NextActionSpotlight(spotlight = spotlight)
        } else {
            Text(
                text = "All clear.",
                style = MaterialTheme.typography.displaySmall,
                color = colors.textMuted,
                fontWeight = FontWeight.Light
            )
        }
    }
}

@Composable
private fun EmptyState(isAmbientMode: Boolean, onRefresh: () -> Unit, onStartVoice: () -> Unit) {
    val colors = LocalWallColors.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(colors.surfaceCard.copy(alpha = 0.2f))
                .border(2.dp, colors.borderColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = colors.accentPrimary.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Nothing pending.",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enjoy the calm or capture something new.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary
        )
        if (!isAmbientMode) {
            Spacer(modifier = Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionPill(text = "Sync Now", onClick = onRefresh)
                ActionPill(text = "Add Task", onClick = onStartVoice, primary = true)
            }
        }
    }
}

@Composable
private fun ActionPill(text: String, onClick: () -> Unit, primary: Boolean = false) {
    val colors = LocalWallColors.current
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (primary) colors.accentPrimary else colors.surfaceCard.copy(alpha = 0.4f),
        border = if (primary) null else BorderStroke(1.dp, Color(0x1AFFFFFF))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) colors.surfaceBlack else colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun VoiceFab(onClick: () -> Unit) {
    val colors = LocalWallColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(colors.accentPrimary.copy(alpha = 0.85f))
            .border(1.dp, colors.accentPrimary, CircleShape)
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Add task with voice"
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = "Voice input",
            tint = colors.surfaceBlack,
            modifier = Modifier.size(24.dp)
        )
    }
}
