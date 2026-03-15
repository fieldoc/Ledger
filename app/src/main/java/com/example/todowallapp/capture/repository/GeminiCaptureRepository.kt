package com.example.todowallapp.capture.repository

import android.util.Base64
import com.example.todowallapp.capture.model.ParsedCapture
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private const val GEMINI_BASE_URL =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent"

data class ParsedVoiceTask(
    val title: String,
    val dueDate: LocalDate?,
    val targetListId: String?,
    val parentTaskId: String? = null,
    val clarification: String? = null
)

data class ExistingTaskRef(
    val id: String,
    val title: String,
    val listId: String
)

fun interface GeminiApiClient {
    fun generateContent(apiKey: String, requestBody: JsonObject): String
}

class GeminiCaptureRepository(
    private val jsonParser: GeminiJsonParser = GeminiJsonParser(),
    private val gson: Gson = Gson(),
    private val apiClient: GeminiApiClient = HttpGeminiApiClient(gson),
    private val imageEncoder: (ByteArray) -> String = { bytes ->
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
) {

    suspend fun validateApiKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("text", "Respond with exactly OK.")
                            })
                        })
                    })
                })
            }

            val response = apiClient.generateContent(
                apiKey = apiKey,
                requestBody = requestBody
            )
            val text = extractTextFromGeminiResponse(response)
            if (!text.contains("OK", ignoreCase = true)) {
                error("Gemini key validation failed")
            }
        }
    }

    suspend fun parseCapture(
        apiKey: String,
        imageJpegBytes: ByteArray,
        existingLists: List<ExistingListRef>,
        todayDate: LocalDate = LocalDate.now()
    ): Result<ParsedCapture> = withContext(Dispatchers.IO) {
        runCatching {
            val firstPrompt = buildPrompt(existingLists, todayDate, malformedRetry = false)
            val firstAttemptText = callGeminiForJson(apiKey, firstPrompt, imageJpegBytes)
            val firstParse = jsonParser.parse(firstAttemptText)
            if (firstParse.isSuccess) {
                return@runCatching firstParse.getOrThrow()
            }

            val retryPrompt = buildPrompt(existingLists, todayDate, malformedRetry = true)
            val secondAttemptText = callGeminiForJson(apiKey, retryPrompt, imageJpegBytes)
            jsonParser.parse(secondAttemptText).getOrThrow()
        }
    }

    suspend fun parseVoiceInput(
        apiKey: String,
        rawText: String,
        existingLists: List<ExistingListRef>,
        existingTasks: List<ExistingTaskRef> = emptyList(),
        todayDate: LocalDate = LocalDate.now()
    ): Result<ParsedVoiceTask> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildVoicePrompt(
                rawText = rawText,
                existingLists = existingLists,
                existingTasks = existingTasks,
                todayDate = todayDate
            )
            val requestBody = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", prompt) })
                        })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    addProperty("temperature", 0.1)
                    addProperty("responseMimeType", "application/json")
                })
            }
            val response = apiClient.generateContent(
                apiKey = apiKey,
                requestBody = requestBody
            )
            val responseText = extractTextFromGeminiResponse(response)
            parseVoiceTaskJson(responseText, existingLists, existingTasks)
        }
    }

    private fun callGeminiForJson(
        apiKey: String,
        prompt: String,
        imageJpegBytes: ByteArray
    ): String {
        val encodedImage = imageEncoder(imageJpegBytes)
        val requestBody = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", prompt) })
                        add(JsonObject().apply {
                            add("inlineData", JsonObject().apply {
                                addProperty("mimeType", "image/jpeg")
                                addProperty("data", encodedImage)
                            })
                        })
                    })
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.1)
                addProperty("responseMimeType", "application/json")
            })
        }

        val response = apiClient.generateContent(
            apiKey = apiKey,
            requestBody = requestBody
        )
        return extractTextFromGeminiResponse(response)
    }

    private fun extractTextFromGeminiResponse(responseBody: String): String {
        val root = JsonParser.parseString(responseBody).asJsonObject
        return root.getAsJsonArray("candidates")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.firstOrNull()
            ?.asJsonObject
            ?.get("text")
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Gemini did not return text content")
    }

    private fun extractErrorMessage(responseBody: String): String {
        return runCatching {
            JsonParser.parseString(responseBody).asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
        }.getOrNull().orEmpty().ifBlank { "Unknown error" }
    }

    private fun buildPrompt(
        existingLists: List<ExistingListRef>,
        todayDate: LocalDate,
        malformedRetry: Boolean
    ): String {
        val listContext = if (existingLists.isEmpty()) {
            "No existing lists found."
        } else {
            existingLists.joinToString(separator = "\n") { list ->
                "- ${list.title} [id=${list.id}]"
            }
        }
        val retryLine = if (malformedRetry) {
            "CRITICAL: previous output was malformed. Return ONLY strict JSON, no markdown, no commentary."
        } else {
            "Return only JSON."
        }

        return """
            You are an expert at transcribing handwritten and digital to-do lists from images.
            Today is $todayDate (${todayDate.dayOfWeek}).
            Existing Google Task lists:
            $listContext

            INSTRUCTIONS:
            1) List Identification: 
               - A centered, underlined, bold, or otherwise visually emphasized standalone line is a PRIMARY List Name indicator.
               - If a word like "Renovation" is centered and underlined at the top, it should be the Name of the list.
               - A standalone heading should NOT also be returned as a task.
               - If the page has a generic heading area and then one clear titled section with multiple child items, prefer using that titled section as the list name instead of returning a generic list like "Tasks".
               - Treat distinct blocks or columns as separate lists.
               - Preserve the original top-to-bottom order of tasks within each visual block.
               - Match lists to the provided 'Existing Google Task lists' by semantic meaning (e.g., 'Shopping' maps to 'Groceries'). If no strong match, create a 'new_list'.

            2) Hierarchy & Subtasks:
               - Humans often use colons, indentation, numbering, or symbol shifts to show subtasks.
                * COLON: If a line says "Painting: Buy Paint", "Painting" is the task and "Buy Paint" is its subtask.
                * HEADING COLON: If a line says just "Painting:" and the following lines are related items, "Painting" is the parent task and the following lines are its subtasks.
                * INDENTATION: Items indented further than the one above are subtasks.
                * SYMBOLS: A change in bullet style (e.g., '*' to '-') often indicates a subtask.
                * NUMBERING: A parent may use "1." while children use "-" or "a)".
                * WRAPPER HEADING: If a short heading line is followed by several related items, the heading is a parent task, not a sibling task.
               - CRITICAL: Never combine a parent and child into a single title string (e.g., avoid "Painting: Buy Paint"). Always split them into the 'tasks' and 'subtasks' structure.

            3) Flexible Extraction:
               - NO BULLETS: Even if there are no bullets or checkboxes, if items are on separate lines and spatially grouped, treat each line as a task.
               - SPATIAL GROUPING: Items physically below a heading belong to that heading's list.
                - If a short heading-like task such as "Painting" is followed by indented or tightly grouped child lines, keep "Painting" as the parent task and place the following lines in 'subtasks'.
                - CONTINUATION LINES: If a line is clearly just a wrapped continuation of the previous line's text, merge it into the previous task instead of creating a new task or subtask.
                - INLINE SUBTASKS: If a parent heading is followed inline by several child items separated by semicolons or bullets, return them as multiple subtasks.
                - Visual Cues: Symbols like [ ], ( ), -, *, o, or checkmarks indicate tasks. 
                - Skip completed items, including crossed-out text, checked boxes, or lines clearly marked done.
             
            4) Task Extraction:
                - Keep titles concise. Remove bullet symbols.
                - Keep short notes or side comments out of the title unless they clearly change the task meaning.
                - Do NOT invent due dates. Only extract a due date if it is explicitly written or clearly attached to that task.
                - Extract due dates (e.g., 'Friday', 'tomorrow', '3/15'). Convert to YYYY-MM-DD using today's date ($todayDate) for relative resolution.
            
            5) Response Format:
               - Return ONLY strict JSON in the following schema:
               {
                 "lists": [
                   {
                     "name": "string",
                     "target": "existing|new_list",
                     "existingListId": "string|null",
                     "tasks": [
                       {
                         "title": "string",
                         "dueDate": "YYYY-MM-DD|null",
                         "subtasks": [
                           { "title": "string", "dueDate": "YYYY-MM-DD|null", "subtasks": [] }
                         ]
                       }
                     ]
                   }
                 ]
               }
            
            6) Clean Output: No markdown, no commentary. Just the JSON.
            $retryLine
        """.trimIndent()
    }

    private fun buildVoicePrompt(
        rawText: String,
        existingLists: List<ExistingListRef>,
        existingTasks: List<ExistingTaskRef>,
        todayDate: LocalDate
    ): String {
        val listContext = if (existingLists.isEmpty()) {
            "No existing lists found."
        } else {
            existingLists.joinToString(separator = "\n") { list ->
                "- List: ${list.title} [id=${list.id}]"
            }
        }
        val taskContext = if (existingTasks.isEmpty()) {
            ""
        } else {
            "\nExisting Top-Level Tasks:\n" + existingTasks.joinToString(separator = "\n") { task ->
                "- Task: ${task.title} [id=${task.id}] in listId=${task.listId}"
            }
        }

        return """
            You are a helpful task assistant that converts speech into high-quality to-do tasks.
            Today is $todayDate (${todayDate.dayOfWeek}).
            
            Context:
            $listContext$taskContext

            Input transcript:
            "$rawText"

            INSTRUCTIONS:
            1) Disambiguate Intent:
               - If the user says "add X to my list", look for the most semantically relevant list (e.g., "Add milk" -> "Shopping" list).
               - If the user says "put X under Y", Y might be a List or a Task. 
                 * If Y matches a Task title, set parentTaskId and targetListId (the task's list).
                 * If Y matches a List title, set targetListId and parentTaskId=null.
               - Be adaptive: match synonyms and meanings (e.g. "job" -> "Work", "groceries" -> "Shopping").

            2) Clarification:
               - If you are genuinely unsure about which list or task to target (e.g. multiple strong matches or zero context), return a 'clarification' message instead of a title. 
               - The clarification should be a friendly, natural question (e.g. "I found multiple shopping lists, which one should I use?").

            3) Clean Titles:
               - Strip all filler words ("remind me to", "hey assistant", etc.).
               - Resulting 'title' should be a standalone task (e.g., "Buy carrots").

            4) Response Format:
               - Return ONLY strict JSON in this schema:
               {
                 "title": "string|null",
                 "dueDate": "YYYY-MM-DD|null",
                 "targetListId": "string|null",
                 "parentTaskId": "string|null",
                 "clarification": "string|null"
               }
            
            5) Clean Output: No markdown, no commentary.
        """.trimIndent()
    }

    private fun parseVoiceTaskJson(
        rawJson: String,
        existingLists: List<ExistingListRef>,
        existingTasks: List<ExistingTaskRef>
    ): ParsedVoiceTask {
        val root = JsonParser.parseString(rawJson).asJsonObject
        
        val clarification = root.stringValue("clarification")
        if (clarification != null) {
            return ParsedVoiceTask(
                title = "",
                dueDate = null,
                targetListId = null,
                clarification = clarification
            )
        }

        val title = root.stringValue("title")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: ""
            
        val dueDate = root.stringValue("dueDate")
            ?.let { dueRaw ->
                runCatching { LocalDate.parse(dueRaw) }.getOrNull()
            }
            
        val targetListId = root.stringValue("targetListId")
            ?.takeIf { candidateId -> existingLists.any { it.id == candidateId } }
            
        val parentTaskId = root.stringValue("parentTaskId")
            ?.takeIf { pId -> existingTasks.any { it.id == pId } }

        return ParsedVoiceTask(
            title = title,
            dueDate = dueDate,
            targetListId = targetListId,
            parentTaskId = parentTaskId,
            clarification = null
        )
    }

    private fun JsonObject.stringValue(key: String): String? {
        if (!has(key)) return null
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        val raw = element.asString.trim()
        return raw.takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
    }
}

private class HttpGeminiApiClient(
    private val gson: Gson
) : GeminiApiClient {

    override fun generateContent(apiKey: String, requestBody: JsonObject): String {
        val endpoint = "$GEMINI_BASE_URL?key=$apiKey"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        return connection.useAndDisconnect {
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(gson.toJson(requestBody))
            }

            val responseCode = responseCode
            val body = if (responseCode in 200..299) {
                inputStream.bufferedReader().use { it.readText() }
            } else {
                errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (responseCode !in 200..299) {
                val errorMessage = runCatching {
                    JsonParser.parseString(body).asJsonObject
                        .getAsJsonObject("error")
                        ?.get("message")
                        ?.asString
                }.getOrNull().orEmpty().ifBlank { "Unknown error" }
                error("Gemini request failed ($responseCode): $errorMessage")
            }

            body
        }
    }

    private inline fun <T> HttpURLConnection.useAndDisconnect(block: HttpURLConnection.() -> T): T {
        return try {
            block()
        } finally {
            disconnect()
        }
    }
}
