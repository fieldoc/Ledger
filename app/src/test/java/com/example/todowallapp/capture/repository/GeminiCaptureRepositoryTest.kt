package com.example.todowallapp.capture.repository

import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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
            apiClient = GeminiApiClient { _, requestBody ->
                prompts += extractPromptText(requestBody)
                responses.removeFirst()
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
            apiClient = GeminiApiClient { _, _ ->
                geminiApiResponse(
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
