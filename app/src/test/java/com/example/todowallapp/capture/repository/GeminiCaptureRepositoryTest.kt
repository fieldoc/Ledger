package com.example.todowallapp.capture.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.ArrayDeque

class GeminiCaptureRepositoryTest {

    @Test
    fun `retries with stricter prompt after malformed response`() = runBlocking {
        val prompts = mutableListOf<String>()
        val responses = ArrayDeque(
            listOf(
                geminiApiResponse("not actually json"),
                geminiApiResponse(
                    """
                    {
                      "lists": [
                        {
                          "name": "Renovation",
                          "target": "new_list",
                          "tasks": [
                            {"title":"Paint walls","dueDate":null,"subtasks":[]}
                          ]
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
        )

        val repository = GeminiCaptureRepository(
            apiClient = object : GeminiApiClient {
                override fun generateContent(apiKey: String, requestBody: JsonObject, config: GeminiRequestConfig): String {
                    prompts += extractPromptText(requestBody)
                    return responses.removeFirst()
                }
            },
            imageEncoder = { "encoded-image" }
        )

        val parsed = repository.parseCapture(
            apiKey = "test-key",
            imageJpegBytes = byteArrayOf(1, 2, 3),
            existingLists = emptyList(),
            todayDate = LocalDate.parse("2026-03-09")
        ).getOrThrow()

        assertEquals(2, prompts.size)
        assertTrue(prompts.first().contains("Return only JSON."))
        assertTrue(prompts.last().contains("previous output was malformed"))
        assertEquals("Renovation", parsed.lists.first().name)
    }

    @Test
    fun `accepts commentary wrapped json from gemini response`() = runBlocking {
        val repository = GeminiCaptureRepository(
            apiClient = object : GeminiApiClient {
                override fun generateContent(apiKey: String, requestBody: JsonObject, config: GeminiRequestConfig): String = geminiApiResponse(
                    """
                    Here is the parsed result:
                    ```json
                    {
                      "lists": [
                        {
                          "name": "Groceries",
                          "target": "new_list",
                          "tasks": [
                            {"title":"Buy milk","dueDate":null,"subtasks":[]}
                          ]
                        }
                      ]
                    }
                    ```
                    """.trimIndent()
                )
            },
            imageEncoder = { "encoded-image" }
        )

        val parsed = repository.parseCapture(
            apiKey = "test-key",
            imageJpegBytes = byteArrayOf(9, 9, 9),
            existingLists = emptyList(),
            todayDate = LocalDate.parse("2026-03-09")
        ).getOrThrow()

        assertEquals("Groceries", parsed.lists.first().name)
        assertEquals("Buy milk", parsed.lists.first().tasks.first().title)
    }

    @Test
    fun `voice parsing groups hierarchical tasks with sharedParentName`() = runBlocking {
        // Simulate Gemini returning a parent + children with sharedParentName
        // for "remind me tomorrow to go to the grocery store and buy bananas and apples"
        val geminiResponse = geminiApiResponse(
            """
            {
              "intent": "add",
              "tasks": [
                {
                  "title": "Go to grocery store",
                  "dueDate": "2026-04-03",
                  "preferredTime": null,
                  "targetListId": null,
                  "newListName": null,
                  "parentTaskId": null,
                  "sharedParentName": null,
                  "confidence": 0.9,
                  "duplicateOf": null,
                  "recurrence": null,
                  "priority": "normal"
                },
                {
                  "title": "Buy bananas",
                  "dueDate": "2026-04-03",
                  "preferredTime": null,
                  "targetListId": null,
                  "newListName": null,
                  "parentTaskId": null,
                  "sharedParentName": "Go to grocery store",
                  "confidence": 0.9,
                  "duplicateOf": null,
                  "recurrence": null,
                  "priority": "normal"
                },
                {
                  "title": "Buy apples",
                  "dueDate": "2026-04-03",
                  "preferredTime": null,
                  "targetListId": null,
                  "newListName": null,
                  "parentTaskId": null,
                  "sharedParentName": "Go to grocery store",
                  "confidence": 0.9,
                  "duplicateOf": null,
                  "recurrence": null,
                  "priority": "normal"
                }
              ],
              "clarification": null
            }
            """.trimIndent()
        )

        val repository = GeminiCaptureRepository(
            apiClient = object : GeminiApiClient {
                override fun generateContent(apiKey: String, requestBody: JsonObject, config: GeminiRequestConfig): String = geminiResponse
            }
        )

        val result = repository.parseVoiceInputV2(
            apiKey = "test-key",
            rawText = "remind me tomorrow to go to the grocery store and buy bananas and apples",
            existingLists = listOf(ExistingListRef("list1", "Shopping")),
            todayDate = LocalDate.parse("2026-04-02"),
            currentTime = LocalTime.of(10, 0)
        ).getOrThrow()

        assertEquals(VoiceIntent.ADD, result.intent)
        assertEquals(3, result.tasks.size)

        // Parent task has no sharedParentName
        val parent = result.tasks[0]
        assertEquals("Go to grocery store", parent.title)
        assertNull(parent.sharedParentName)
        assertEquals(LocalDate.parse("2026-04-03"), parent.dueDate)

        // Children reference parent via sharedParentName
        val child1 = result.tasks[1]
        assertEquals("Buy bananas", child1.title)
        assertEquals("Go to grocery store", child1.sharedParentName)

        val child2 = result.tasks[2]
        assertEquals("Buy apples", child2.title)
        assertEquals("Go to grocery store", child2.sharedParentName)
    }

    @Test
    fun `voice parsing keeps independent tasks without sharedParentName`() = runBlocking {
        // "Buy milk and call the dentist" should remain independent
        val geminiResponse = geminiApiResponse(
            """
            {
              "intent": "add",
              "tasks": [
                {
                  "title": "Buy milk",
                  "dueDate": null,
                  "preferredTime": null,
                  "targetListId": null,
                  "newListName": null,
                  "parentTaskId": null,
                  "sharedParentName": null,
                  "confidence": 0.9,
                  "duplicateOf": null,
                  "recurrence": null,
                  "priority": "normal"
                },
                {
                  "title": "Call the dentist",
                  "dueDate": null,
                  "preferredTime": null,
                  "targetListId": null,
                  "newListName": null,
                  "parentTaskId": null,
                  "sharedParentName": null,
                  "confidence": 0.9,
                  "duplicateOf": null,
                  "recurrence": null,
                  "priority": "normal"
                }
              ],
              "clarification": null
            }
            """.trimIndent()
        )

        val repository = GeminiCaptureRepository(
            apiClient = object : GeminiApiClient {
                override fun generateContent(apiKey: String, requestBody: JsonObject, config: GeminiRequestConfig): String = geminiResponse
            }
        )

        val result = repository.parseVoiceInputV2(
            apiKey = "test-key",
            rawText = "buy milk and call the dentist",
            existingLists = emptyList(),
            todayDate = LocalDate.parse("2026-04-02"),
            currentTime = LocalTime.of(10, 0)
        ).getOrThrow()

        assertEquals(2, result.tasks.size)
        assertNull(result.tasks[0].sharedParentName)
        assertNull(result.tasks[1].sharedParentName)
    }

    @Test
    fun `voice prompt contains hierarchical subtask grouping instructions`() = runBlocking {
        var capturedPrompt = ""
        val repository = GeminiCaptureRepository(
            apiClient = object : GeminiApiClient {
                override fun generateContent(apiKey: String, requestBody: JsonObject, config: GeminiRequestConfig): String {
                    capturedPrompt = extractSystemInstruction(requestBody)
                    return geminiApiResponse("""{"intent":"add","tasks":[{"title":"Test","confidence":0.9,"priority":"normal"}]}""")
                }
            }
        )

        repository.parseVoiceInputV2(
            apiKey = "test-key",
            rawText = "test",
            existingLists = emptyList(),
            todayDate = LocalDate.parse("2026-04-02"),
            currentTime = LocalTime.of(10, 0)
        )

        assertTrue(
            "Prompt should contain hierarchical vs flat detection guidance",
            capturedPrompt.contains("HIERARCHICAL") && capturedPrompt.contains("FLAT")
        )
        assertTrue(
            "Prompt should explain sharedParentName for child tasks",
            capturedPrompt.contains("sharedParentName")
        )
        assertTrue(
            "Prompt should contain examples of location/activity grouping",
            capturedPrompt.contains("grocery store") || capturedPrompt.contains("Location/venue grouping")
        )
    }

    private fun extractSystemInstruction(requestBody: JsonObject): String {
        return requestBody
            .getAsJsonObject("systemInstruction")
            .getAsJsonArray("parts")
            .get(0)
            .asJsonObject
            .get("text")
            .asString
    }

    private fun extractPromptText(requestBody: JsonObject): String {
        return requestBody
            .getAsJsonArray("contents")
            .get(0)
            .asJsonObject
            .getAsJsonArray("parts")
            .get(0)
            .asJsonObject
            .get("text")
            .asString
    }

    private fun geminiApiResponse(text: String): String {
        return """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": ${escapeJsonString(text)}}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun escapeJsonString(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    }
}
