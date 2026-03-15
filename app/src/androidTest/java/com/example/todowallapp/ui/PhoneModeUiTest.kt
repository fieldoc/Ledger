package com.example.todowallapp.ui

import androidx.activity.ComponentActivity
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.todowallapp.capture.model.ListTarget
import com.example.todowallapp.capture.model.ParsedCapture
import com.example.todowallapp.capture.model.ParsedListDraft
import com.example.todowallapp.capture.model.ParsedTaskDraft
import com.example.todowallapp.capture.repository.PendingCaptureRecord
import com.example.todowallapp.data.model.AppMode
import com.example.todowallapp.ui.screens.ModeSelectorScreen
import com.example.todowallapp.ui.screens.ParsedCapturePreviewScreen
import com.example.todowallapp.ui.screens.PhoneHomeScreen
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.viewmodel.PhoneCaptureUiState
import com.example.todowallapp.viewmodel.PhoneTaskListWithTasks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.Assume.assumeTrue

@RunWith(AndroidJUnit4::class)
class PhoneModeUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun skipOnAndroidSix() {
        assumeTrue(
            "Compose host tests are skipped on API 23 due known compose host instability",
            Build.VERSION.SDK_INT > Build.VERSION_CODES.M
        )
    }

    @Test
    fun modeSelectorShowsBothModes() {
        composeRule.setContent {
            LedgerTheme {
                ModeSelectorScreen(
                    onSelectMode = { _: AppMode -> },
                    onSignOut = {}
                )
            }
        }

        composeRule.onNodeWithText("Wall Mode").assertIsDisplayed()
        composeRule.onNodeWithText("Phone Mode").assertIsDisplayed()
    }

    @Test
    fun phoneHomeShowsPendingRetrySection() {
        composeRule.setContent {
            LedgerTheme {
                PhoneHomeScreen(
                    uiState = PhoneCaptureUiState(
                        taskLists = listOf(
                            PhoneTaskListWithTasks(
                                taskList = com.example.todowallapp.data.model.TaskList("inbox", "Inbox"),
                                tasks = emptyList()
                            )
                        ),
                        pendingCaptures = listOf(
                            PendingCaptureRecord(
                                id = "pending-1",
                                filePath = "/tmp/pending-1.jpg",
                                createdAtEpochMs = 0L,
                                lastError = "Offline"
                            )
                        )
                    ),
                    onTaskToggle = {},
                    onRetryPendingCapture = {},
                    onRemovePendingCapture = {},
                    onCameraClick = {},
                    onVoiceClick = {},
                    onRefreshClick = {},
                    onSettingsClick = {},
                    onSaveCaptureForRetry = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithText("Pending Retries").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun parsedCapturePreviewShowsAddAllAction() {
        composeRule.setContent {
            LedgerTheme {
                ParsedCapturePreviewScreen(
                    parsedCapture = ParsedCapture(
                        lists = listOf(
                            ParsedListDraft(
                                name = "Inbox",
                                target = ListTarget.NEW_LIST,
                                tasks = listOf(ParsedTaskDraft(title = "Task A"))
                            )
                        )
                    ),
                    existingLists = emptyList(),
                    isCommitting = false,
                    onUpdateListName = { _, _ -> },
                    onAssignListToExisting = { _, _ -> },
                    onUpdateTaskTitle = { _, _ -> },
                    onRemoveTask = {},
                    onCancel = {},
                    onAddAll = {}
                )
            }
        }

        composeRule.onNodeWithText("Add All").assertIsDisplayed()
    }
}
