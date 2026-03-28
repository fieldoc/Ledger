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
import java.time.LocalTime

private const val GEMINI_BASE_URL =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent"

enum class VoiceIntent { ADD, COMPLETE, RESCHEDULE, DELETE, QUERY, AMEND }

enum class PreferredTime { MORNING, AFTERNOON, EVENING }

data class ParsedVoiceTaskItem(
    val title: String,
    val dueDate: LocalDate?,
    val preferredTime: PreferredTime?,
    val targetListId: String?,
    val newListName: String?,
    val parentTaskId: String?,
    val confidence: Float,
    val duplicateOf: String?
)

data class ParsedVoiceResponse(
    val intent: VoiceIntent,
    val tasks: List<ParsedVoiceTaskItem>,
    val clarification: String?,
    val rawTranscript: String
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

    suspend fun parseVoiceInputV2(
        apiKey: String,
        rawText: String,
        existingLists: List<ExistingListRef>,
        existingTasks: List<ExistingTaskRef> = emptyList(),
        todayDate: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): Result<ParsedVoiceResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildVoicePromptV2(
                rawText = rawText,
                existingLists = existingLists,
                existingTasks = existingTasks,
                todayDate = todayDate,
                currentTime = currentTime
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
            parseVoiceResponseJson(responseText, rawText, existingLists, existingTasks)
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

    private fun buildVoicePromptV2(
        rawText: String,
        existingLists: List<ExistingListRef>,
        existingTasks: List<ExistingTaskRef>,
        todayDate: LocalDate,
        currentTime: LocalTime
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
            "\nExisting Tasks:\n" + existingTasks.joinToString(separator = "\n") { task ->
                "- Task: ${task.title} [id=${task.id}] in listId=${task.listId}"
            }
        }
        val hour = currentTime.hour
        val timeOfDay = when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            else -> "evening"
        }

        return """
            You are an expert voice-to-task assistant. You convert natural, conversational speech into structured task operations. Users speak casually and verbosely — your job is to extract precise, actionable intent.

            CURRENT CONTEXT:
            Today: $todayDate (${todayDate.dayOfWeek})
            Current time: $currentTime ($timeOfDay)

            Available lists:
            $listContext$taskContext

            INPUT TRANSCRIPT:
            "$rawText"

            INSTRUCTIONS:

            1) INTENT CLASSIFICATION
               Classify the primary intent of the utterance:
               - "add" — creating new tasks (DEFAULT if ambiguous)
               - "complete" — marking existing tasks as done ("mark X as done", "finished X", "X is done", "did X")
               - "reschedule" — changing due date of existing task ("move X to Friday", "push X back a week")
               - "delete" — removing an existing task ("remove X", "delete X", "get rid of X")
               - "query" — asking about tasks ("what's on my list?", "what do I have today?")
               - "amend" — modifying the most recent voice input ("actually make that Friday", "change it to...")

               For complete/reschedule/delete: match the spoken description to an existing task by semantic similarity. Set the matching task's ID in the first task item's parentTaskId field (repurposed as "target existing task ID").

            2) MULTI-TASK EXTRACTION
               A single utterance may contain multiple tasks. Split compound speech into individual tasks:
               - "Buy milk and call the dentist" → 2 tasks
               - "I need to do laundry, oh and pick up the kids at 3, and remind me about the meeting" → 3 tasks
               - Connectors: "and", "also", "oh and", "plus", "as well as", "remind me to also"
               - Each task gets its own entry in the tasks array with independent fields.
               - For non-add intents, there is typically only 1 task (the target of the action).

            3) CONVERSATIONAL NOISE STRIPPING
               Extract the actionable intent and reduce to the shortest imperative phrase that fully captures the task. Speech is messy — strip aggressively:
               - "Uh so like I was thinking we should probably get around to painting the living room" → "Paint living room"
               - "Hey can you remind me to um pick up my prescription from CVS tomorrow" → "Pick up prescription from CVS"
               - "I really need to at some point figure out what's going on with the water heater" → "Check water heater"
               - Remove: hedging, filler (um, uh, like, you know, so), false starts, self-corrections, politeness wrappers, thinking-out-loud phrasing
               - Keep: specific nouns, locations, people, quantities, deadlines

            4) TEMPORAL REASONING
               Extract due dates from natural language. Use today ($todayDate, ${todayDate.dayOfWeek}) for resolution:
               - "tomorrow" → next day
               - "this weekend" → next Saturday
               - "end of week" / "by end of week" → next Friday
               - "next week" → next Monday
               - "next [dayname]" → the [dayname] in the coming week (not this week)
               - "in a couple days" / "in a few days" → today + 2 / today + 3
               - "in a week" → today + 7
               - "by [dayname]" → the nearest future occurrence of that day
               - "soon" / "sometime" / "eventually" / "whenever" → null (no date)
               - "end of month" → last day of current month

               TIME-OF-DAY PREFERENCE (preferredTime field):
               - "first thing" / "in the morning" / "before noon" → "morning"
               - "after lunch" / "this afternoon" / "in the afternoon" → "afternoon"
               - "tonight" / "this evening" / "after work" / "after dinner" → "evening"
               - No time indication → null

            5) DUPLICATE DETECTION
               Compare each extracted task against Existing Tasks above. If a new task is semantically equivalent to an existing one (same core action, same subject), set duplicateOf to that task's ID.
               - "Buy groceries" is a duplicate of "Get groceries"
               - "Call mom" is a duplicate of "Call Mom"
               - "Buy groceries for the party" is NOT a duplicate of "Buy groceries" (different scope)
               Be conservative — only flag clear semantic matches, not vague similarities.

            6) LIST INFERENCE & CREATION
               Route each task to the most appropriate list, even when no list is explicitly mentioned:
               - Infer from task content: "Buy drill bit" → Hardware/Home Improvement list
               - Infer from semantic domain: "Schedule dentist" → Health/Medical or Personal list
               - Match synonyms: "groceries", "food", "ingredients" → Shopping list
               - If explicitly mentioned ("add to my work list"), use the mentioned list
               - If no confident match, set targetListId to null (app will use the current/default list)

               LIST CREATION: If the user explicitly asks to CREATE or MAKE a new list (e.g., "make a new grocery list", "create a shopping list and add milk"):
               - Set targetListId to null
               - Set newListName to the desired list name
               - If a list with that exact name already exists in the available lists, make the name unique by appending the current date (e.g., "Grocery List - ${todayDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} ${todayDate.monthValue}/${todayDate.dayOfMonth}")
               - All tasks from the utterance that belong to this new list should share the same newListName value
               - If the user does NOT ask to create a new list, set newListName to null

            7) CONFIDENCE SCORING
               Rate confidence from 0.0 to 1.0 for each task:
               - 0.85-1.0: Clear, unambiguous single intent with obvious list/date assignment
               - 0.5-0.84: Reasonable inference but some ambiguity (fuzzy list match, implied date)
               - Below 0.5: Uncertain — consider adding a clarification message
               If overall confidence is below 0.5, return a friendly clarification in the clarification field.

            8) RESPONSE FORMAT
               Return ONLY strict JSON in this schema:
               {
                 "intent": "add|complete|reschedule|delete|query|amend",
                 "tasks": [
                   {
                     "title": "string",
                     "dueDate": "YYYY-MM-DD|null",
                     "preferredTime": "morning|afternoon|evening|null",
                     "targetListId": "string|null",
                     "newListName": "string|null",
                     "parentTaskId": "string|null",
                     "confidence": 0.0,
                     "duplicateOf": "existing-task-id|null"
                   }
                 ],
                 "clarification": "string|null"
               }

               For "query" intent, tasks array may be empty.
               For "amend" intent, return the amended version as a single task.

            9) CLEAN OUTPUT: No markdown, no commentary. Just the JSON.
        """.trimIndent()
    }

    private fun parseVoiceResponseJson(
        rawJson: String,
        rawTranscript: String,
        existingLists: List<ExistingListRef>,
        existingTasks: List<ExistingTaskRef>
    ): ParsedVoiceResponse {
        val root = JsonParser.parseString(rawJson).asJsonObject

        val intentStr = root.stringValue("intent") ?: "add"
        val intent = runCatching {
            VoiceIntent.valueOf(intentStr.uppercase())
        }.getOrDefault(VoiceIntent.ADD)

        val clarification = root.stringValue("clarification")

        val tasksArray = root.getAsJsonArray("tasks")
        val tasks = if (tasksArray != null && tasksArray.size() > 0) {
            tasksArray.map { element ->
                val obj = element.asJsonObject
                val title = obj.stringValue("title")?.trim()?.takeIf { it.isNotEmpty() } ?: ""
                val dueDate = obj.stringValue("dueDate")?.let { raw ->
                    runCatching { LocalDate.parse(raw) }.getOrNull()
                }
                val preferredTime = obj.stringValue("preferredTime")?.let { raw ->
                    runCatching { PreferredTime.valueOf(raw.uppercase()) }.getOrNull()
                }
                val targetListId = obj.stringValue("targetListId")
                    ?.takeIf { candidateId -> existingLists.any { it.id == candidateId } }
                val newListName = obj.stringValue("newListName")
                val parentTaskId = obj.stringValue("parentTaskId")
                    ?.takeIf { pId -> existingTasks.any { it.id == pId } }
                val confidence = runCatching {
                    obj.get("confidence")?.asFloat
                }.getOrNull() ?: 0.5f
                val duplicateOf = obj.stringValue("duplicateOf")
                    ?.takeIf { dId -> existingTasks.any { it.id == dId } }

                ParsedVoiceTaskItem(
                    title = title,
                    dueDate = dueDate,
                    preferredTime = preferredTime,
                    targetListId = targetListId,
                    newListName = newListName,
                    parentTaskId = parentTaskId,
                    confidence = confidence.coerceIn(0f, 1f),
                    duplicateOf = duplicateOf
                )
            }
        } else {
            emptyList()
        }

        return ParsedVoiceResponse(
            intent = intent,
            tasks = tasks,
            clarification = clarification,
            rawTranscript = rawTranscript
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
        val maxRetries = 2
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                Thread.sleep(attempt * 1000L) // 1s, 2s backoff
            }
            try {
                val result = doRequest(apiKey, requestBody)
                return result
            } catch (e: RetryableGeminiException) {
                lastException = e
                if (attempt == maxRetries) break
                // retry on 429, 500, 503
            } catch (e: Exception) {
                throw e // non-retryable, throw immediately
            }
        }
        throw lastException ?: IllegalStateException("Gemini request failed after retries")
    }

    private fun doRequest(apiKey: String, requestBody: JsonObject): String {
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

                val sanitized = sanitizeErrorMessage(errorMessage, apiKey)

                if (responseCode in listOf(429, 500, 503)) {
                    throw RetryableGeminiException("Gemini request failed ($responseCode): $sanitized")
                }
                error("Gemini request failed ($responseCode): $sanitized")
            }

            body
        }
    }

    private fun sanitizeErrorMessage(message: String, apiKey: String): String {
        if (apiKey.isBlank()) return message
        return message.replace(apiKey, "***")
    }

    private inline fun <T> HttpURLConnection.useAndDisconnect(block: HttpURLConnection.() -> T): T {
        return try {
            block()
        } finally {
            disconnect()
        }
    }

    private class RetryableGeminiException(message: String) : Exception(message)
}
