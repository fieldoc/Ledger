package com.example.todowallapp.ui

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todowallapp.data.model.MockData
import com.example.todowallapp.data.model.TaskList
import com.example.todowallapp.ui.screens.TaskWallScreen
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.viewmodel.TaskListWithTasks
import com.example.todowallapp.voice.VoiceInputState
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class TaskWallScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir: File by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "screenshots/baseline").apply { mkdirs() }
    }

    private val defaultLists = listOf(
        TaskListWithTasks(
            taskList = TaskList(id = "work", title = "Work"),
            tasks = MockData.sampleTasks.take(4)
        ),
        TaskListWithTasks(
            taskList = TaskList(id = "home", title = "Home"),
            tasks = MockData.sampleTasks.drop(2)
        )
    )

    private val collapsedViewLists = listOf(
        TaskListWithTasks(
            taskList = TaskList(id = "work", title = "Work"),
            tasks = MockData.sampleTasks.take(1)
        ),
        TaskListWithTasks(
            taskList = TaskList(id = "home", title = "Home"),
            tasks = MockData.sampleTasks.drop(2)
        )
    )

    @Before
    fun skipOnAndroidSix() {
        assumeTrue(
            "Screenshot capture is skipped on API 23 (Android 6.0.1) due to Compose capture runtime incompatibility",
            Build.VERSION.SDK_INT > Build.VERSION_CODES.M
        )
    }

    @Test
    fun captureExpandedFolder() {
        renderTaskWall(taskLists = defaultLists)
        saveRootScreenshot("01-expanded-folder")
    }

    @Test
    fun captureCollapsedFolderView() {
        renderTaskWall(taskLists = collapsedViewLists)
        saveRootScreenshot("02-collapsed-folder")
    }

    @Test
    fun captureQuietMode() {
        renderTaskWall(taskLists = defaultLists)
        composeRule.waitUntil(timeoutMillis = 45_000) {
            composeRule.onAllNodesWithText("Press any key to wake").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        saveRootScreenshot("03-quiet-mode")
    }

    @Test
    fun captureVoiceOverlay() {
        renderTaskWall(
            taskLists = defaultLists,
            voiceState = VoiceInputState.Listening(amplitudeLevel = 0.75f)
        )
        saveRootScreenshot("04-voice-overlay")
    }

    private fun renderTaskWall(
        taskLists: List<TaskListWithTasks>,
        voiceState: VoiceInputState = VoiceInputState.Idle
    ) {
        composeRule.setContent {
            LedgerTheme {
                TaskWallScreen(
                    taskLists = taskLists,
                    onTaskToggle = {},
                    onRefresh = {},
                    voiceState = voiceState,
                    isSyncing = false,
                    isLoading = false,
                    isOnline = true,
                    ambientLightMonitoringEnabled = false
                )
            }
        }
        composeRule.waitForIdle()
        Thread.sleep(1600)
        composeRule.waitForIdle()
    }

    private fun saveRootScreenshot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(outputDir, "$name.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        Log.i("TaskWallScreenshotTest", "Saved screenshot to ${file.absolutePath}")
    }
}
