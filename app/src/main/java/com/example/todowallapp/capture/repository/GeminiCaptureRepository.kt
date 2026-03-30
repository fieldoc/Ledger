package com.example.todowallapp.capture.repository

import android.util.Base64
import com.example.todowallapp.capture.model.ParsedCapture
import com.example.todowallapp.data.model.BlockCategory
import com.example.todowallapp.data.model.DayPlan
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.data.model.RecurrenceFrequency
import com.example.todowallapp.data.model.RecurrenceRule
import com.example.todowallapp.data.model.TaskPriority
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
import java.time.format.DateTimeFormatter

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
    val duplicateOf: String?,
    val recurrenceRule: RecurrenceRule? = null,
    val priority: TaskPriority = TaskPriority.NORMAL
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

    /**
     * Re-parse a RESCHEDULE utterance after the user indicated the first parse was wrong.
     * Bundles original transcript + first interpretation + clarification into one prompt.
     */
    suspend fun parseRescheduleRetry(
        apiKey: String,
        originalTranscript: String,
        targetTaskTitle: String,
        firstParsedDate: LocalDate?,
        clarificationTranscript: String,
        existingLists: List<ExistingListRef>,
        existingTasks: List<ExistingTaskRef>,
        todayDate: LocalDate = LocalDate.now()
    ): Result<ParsedVoiceResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildRescheduleRetryPrompt(
                originalTranscript = originalTranscript,
                targetTaskTitle = targetTaskTitle,
                firstParsedDate = firstParsedDate,
                clarificationTranscript = clarificationTranscript,
                existingLists = existingLists,
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
            val response = apiClient.generateContent(apiKey = apiKey, requestBody = requestBody)
            val responseText = extractTextFromGeminiResponse(response)
            parseVoiceResponseJson(responseText, clarificationTranscript, existingLists, existingTasks)
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
                     "duplicateOf": "existing-task-id|null",
                     "recurrence": {
                       "frequency": "daily|weekly|monthly",
                       "interval": 1,
                       "anchor": "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY|null"
                     } or null,
                     "priority": "high|normal"
                   }
                 ],
                 "clarification": "string|null"
               }

               For "query" intent, tasks array may be empty.
               For "amend" intent, return the amended version as a single task.

            9) RECURRENCE DETECTION
               Detect if the user wants a repeating task. Set recurrence only when the user clearly expresses repetition — not for one-off tasks.

               Signals: "every day", "every week", "once a week", "daily", "weekly", "monthly", "every Monday", "twice a week", "every other week", "every month on the 15th", "remind me every Friday"

               - frequency: "daily", "weekly", or "monthly"
               - interval: 1 for "every week/day/month", 2 for "every other week/biweekly", etc.
               - anchor for WEEKLY: full English day name in UPPERCASE ("MONDAY"–"SUNDAY"), or null if no specific day mentioned
               - anchor for MONTHLY: day-of-month as string ("1"–"31"), or null if no specific day mentioned
               - anchor for DAILY: always null

               Examples:
               - "clean the kitchen every Sunday" → {"frequency":"weekly","interval":1,"anchor":"SUNDAY"}
               - "take vitamins every day" → {"frequency":"daily","interval":1,"anchor":null}
               - "pay rent on the first" → {"frequency":"monthly","interval":1,"anchor":"1"}
               - "check in every other week" → {"frequency":"weekly","interval":2,"anchor":null}
               - "buy groceries" (no repetition) → null

               If there is any ambiguity about frequency or the user didn't clearly express repetition, set recurrence to null.

            10) PRIORITY DETECTION
                Detect HIGH priority when the user expresses genuine urgency or strong importance. Set priority to "high" only for clear signals — default is "normal".

                HIGH priority signals:
                - Explicit urgency: "urgent", "ASAP", "right away", "immediately", "as soon as possible"
                - Strong need: "I really need to", "I NEED to make sure to", "I absolutely must", "I can't forget to"
                - Consequence implied: "or we'll miss the deadline", "before it's too late", "really important"
                - Emphasis words: "critical", "important", "priority", "crucial", "must"

                NOT high priority (these are normal):
                - "I should", "I want to", "remind me to", "I need to" (without emphasis)
                - "eventually", "sometime", "when I get a chance"
                - General hedging or casual phrasing

            11) CLEAN OUTPUT: No markdown, no commentary. Just the JSON.
        """.trimIndent()
    }

    private fun buildRescheduleRetryPrompt(
        originalTranscript: String,
        targetTaskTitle: String,
        firstParsedDate: LocalDate?,
        clarificationTranscript: String,
        existingLists: List<ExistingListRef>,
        todayDate: LocalDate
    ): String {
        val listContext = if (existingLists.isEmpty()) "No existing lists." else
            existingLists.joinToString("\n") { "- ${it.title} [id=${it.id}]" }
        val firstDateStr = firstParsedDate?.toString() ?: "no date"

        return """
            You are a voice-to-task assistant. The user spoke a rescheduling request, but your first interpretation was wrong.

            CURRENT CONTEXT:
            Today: $todayDate (${todayDate.dayOfWeek})

            Available lists:
            $listContext

            WHAT HAPPENED:
            Original request: "$originalTranscript"
            You interpreted this as: move task "$targetTaskTitle" to $firstDateStr
            The user said that was incorrect.

            USER'S CLARIFICATION:
            "$clarificationTranscript"

            TASK:
            Re-interpret the user's original request, using the clarification to correct your understanding.
            - intent must be "reschedule"
            - tasks[0].title should be the target task name: "$targetTaskTitle"
            - tasks[0].parentTaskId should remain the same as before (you cannot re-look this up — leave parentTaskId as null if unknown)
            - tasks[0].dueDate should reflect the corrected date from the clarification
            - Apply the same temporal reasoning rules (today=$todayDate)

            Return ONLY strict JSON in this schema:
            {
              "intent": "reschedule",
              "tasks": [
                {
                  "title": "string",
                  "dueDate": "YYYY-MM-DD|null",
                  "preferredTime": "morning|afternoon|evening|null",
                  "targetListId": "string|null",
                  "newListName": "null",
                  "parentTaskId": "null",
                  "confidence": 0.0,
                  "duplicateOf": "null"
                }
              ],
              "clarification": "string|null"
            }

            No markdown. No commentary. Just the JSON.
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

                val recurrenceRule = runCatching {
                    val rec = obj.getAsJsonObject("recurrence")
                    if (rec == null || rec.isJsonNull) null
                    else {
                        val freqStr = rec.stringValue("frequency") ?: return@runCatching null
                        val freq = RecurrenceFrequency.valueOf(freqStr.uppercase())
                        val interval = rec.get("interval")?.asInt ?: 1
                        val anchor = rec.stringValue("anchor")
                        RecurrenceRule(freq, interval, anchor)
                    }
                }.getOrNull()

                val priority = obj.stringValue("priority")?.let { raw ->
                    runCatching { TaskPriority.valueOf(raw.uppercase()) }.getOrNull()
                } ?: TaskPriority.NORMAL

                ParsedVoiceTaskItem(
                    title = title,
                    dueDate = dueDate,
                    preferredTime = preferredTime,
                    targetListId = targetListId,
                    newListName = newListName,
                    parentTaskId = parentTaskId,
                    confidence = confidence.coerceIn(0f, 1f),
                    duplicateOf = duplicateOf,
                    recurrenceRule = recurrenceRule,
                    priority = priority
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

    // ── Day Organizer prompts ──────────────────────────────────────────

    fun buildDayPlanPrompt(
        rawTranscription: String,
        existingEvents: List<String>,
        existingTasks: List<ExistingTaskRef>,
        targetDate: LocalDate,
        currentTime: LocalTime
    ): String {
        val eventsBlock = if (existingEvents.isEmpty()) "No existing events."
            else existingEvents.joinToString("\n") { "- $it" }

        val tasksBlock = if (existingTasks.isEmpty()) "No existing tasks."
            else existingTasks.take(40).joinToString("\n") { "- ${it.title} (list: ${it.listId})" }

        val timeContext = if (targetDate == LocalDate.now()) {
            "Start scheduling from ${currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))} (round to next half-hour)."
        } else {
            "Start scheduling from 07:00 (morning)."
        }

        return """
You are a day planning assistant. The user described what they want to accomplish today.
Produce a time-blocked schedule as a JSON object.

TARGET DATE: $targetDate
CURRENT TIME: ${currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
$timeContext

EXISTING CALENDAR EVENTS (DO NOT move or modify these — schedule around them):
$eventsBlock

EXISTING GOOGLE TASKS (for reference — match by name if the user mentions one):
$tasksBlock

USER'S REQUEST:
"$rawTranscription"

RULES:
1. NEVER move or modify existing calendar events. Include them in the output with isExistingEvent=true.
2. Categorize each NEW block: ACTIVE (cognitively demanding), PASSIVE (low-attention, may need proximity), ERRAND (location-based), SOCIAL (calls, meetings), LEISURE (reading, relaxation).
3. Energy curve: schedule ACTIVE tasks in the morning (before noon), PASSIVE/LEISURE in the evening. ERRAND blocks should cluster together to minimize travel.
4. For PASSIVE tasks with follow-up (e.g., laundry needs changeover), add a notes field and schedule a brief follow-up block at the appropriate time.
5. Estimate durations reasonably. If the user provided durations, use them. Common defaults: dog walk 30min, run 30-45min, bank 30min, grocery 45min, phone call 20-30min.
6. If the user mentioned an existing Google Task by name (fuzzy match), set sourceTaskId to the task ID and sourceTaskListId to the list ID. Otherwise leave null.
7. Leave at least 15 minutes buffer between blocks that require location changes.
8. Return confidence 0.0-1.0 for the overall plan. If confidence < 0.5, add a "warning" field explaining uncertainty.
9. The summary should be concise: "N blocks, M new — key insight" (e.g., "8 blocks, 6 new — errands clustered at 1pm").
10. Do NOT use emoji in titles. Plain text only.

RESPONSE FORMAT (strict JSON):
{
  "targetDate": "YYYY-MM-DD",
  "confidence": 0.0-1.0,
  "warning": null,
  "summary": "string",
  "blocks": [
    {
      "title": "string",
      "startTime": "HH:mm",
      "endTime": "HH:mm",
      "category": "ACTIVE|PASSIVE|ERRAND|SOCIAL|LEISURE|EXISTING_EVENT",
      "isExistingEvent": false,
      "existingEventId": null,
      "notes": null,
      "sourceTaskId": null,
      "sourceTaskListId": null
    }
  ]
}
""".trimIndent()
    }

    fun buildPlanAdjustmentPrompt(
        adjustmentRequest: String,
        previousPlanJson: String,
        existingEvents: List<String>,
        existingTasks: List<ExistingTaskRef>,
        targetDate: LocalDate,
        currentTime: LocalTime
    ): String {
        val basePrompt = buildDayPlanPrompt(
            rawTranscription = adjustmentRequest,
            existingEvents = existingEvents,
            existingTasks = existingTasks,
            targetDate = targetDate,
            currentTime = currentTime
        )

        return """
$basePrompt

IMPORTANT — ADJUSTMENT MODE:
The user already has a plan and wants to modify it. Here is the current plan:

$previousPlanJson

Apply the user's requested changes while keeping the rest of the plan intact.
Re-optimize timing if the change cascades (e.g., moving a block earlier frees a later slot).
Return the COMPLETE updated plan, not just the changed blocks.
""".trimIndent()
    }

    fun parseDayPlanResponse(responseJson: String, targetDate: LocalDate): DayPlan {
        val root = org.json.JSONObject(responseJson)

        val parsedDate = try {
            LocalDate.parse(root.getString("targetDate"))
        } catch (_: Exception) { targetDate }

        val confidence = root.optDouble("confidence", 0.7).toFloat()
        val warning = root.optString("warning", "").ifBlank { null }
        val summary = root.optString("summary", "Plan ready")

        val blocksArray = root.getJSONArray("blocks")
        val blocks = (0 until blocksArray.length()).map { i ->
            val obj = blocksArray.getJSONObject(i)
            val startTime = LocalTime.parse(obj.getString("startTime"), DateTimeFormatter.ofPattern("HH:mm"))
            val endTime = LocalTime.parse(obj.getString("endTime"), DateTimeFormatter.ofPattern("HH:mm"))

            PlanBlock(
                title = obj.getString("title"),
                startTime = parsedDate.atTime(startTime),
                endTime = parsedDate.atTime(endTime),
                category = try {
                    BlockCategory.valueOf(obj.getString("category"))
                } catch (_: Exception) { BlockCategory.ACTIVE },
                isExistingEvent = obj.optBoolean("isExistingEvent", false),
                existingEventId = obj.optString("existingEventId", "").ifBlank { null },
                notes = obj.optString("notes", "").ifBlank { null },
                sourceTaskId = obj.optString("sourceTaskId", "").ifBlank { null },
                sourceTaskListId = obj.optString("sourceTaskListId", "").ifBlank { null }
            )
        }

        return DayPlan(
            targetDate = parsedDate,
            blocks = blocks.sortedBy { it.startTime },
            summary = summary,
            confidence = confidence,
            warning = warning
        )
    }

    /**
     * Call Gemini for day planning. Uses gemini-2.5-flash (stronger reasoning than lite).
     * Returns the raw JSON text from the response.
     */
    suspend fun callGeminiForDayPlan(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        val model = "gemini-2.5-flash"
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestBody = org.json.JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                org.json.JSONObject().put("parts", org.json.JSONArray().put(
                    org.json.JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", org.json.JSONObject().apply {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            })
        }

        var lastException: Exception? = null
        val retryDelays = listOf(2000L, 4000L)

        for (attempt in 0..2) {
            try {
                val connection = (java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode in listOf(429, 500, 503) && attempt < 2) {
                    lastException = Exception("HTTP $responseCode")
                    Thread.sleep(retryDelays[attempt])
                    continue
                }

                val responseBody = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                    throw Exception("Gemini API error $responseCode: ${errorBody.take(200)}")
                }

                return@withContext extractTextFromGeminiResponse(responseBody)

            } catch (e: Exception) {
                lastException = e
                if (attempt < 2 && (e.message?.contains("429") == true || e.message?.contains("50") == true)) {
                    Thread.sleep(retryDelays[attempt])
                    continue
                }
                throw e
            }
        }

        throw lastException ?: Exception("Day plan request failed after retries")
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
