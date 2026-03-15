package com.example.todowallapp.capture.repository

import com.example.todowallapp.capture.model.ListTarget
import com.example.todowallapp.capture.model.ParsedCapture
import com.example.todowallapp.capture.model.ParsedListDraft
import com.example.todowallapp.capture.model.ParsedTaskDraft
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate

class GeminiJsonParser {

    private data class NormalizedListContent(
        val name: String,
        val tasks: List<ParsedTaskDraft>
    )

    fun parse(rawResponse: String): Result<ParsedCapture> = runCatching {
        val payload = extractJsonPayload(rawResponse)
        val jsonElement = JsonParser.parseString(payload)
        val warnings = mutableListOf<String>()
        val listsArray = when {
            jsonElement.isJsonArray -> jsonElement.asJsonArray
            jsonElement.isJsonObject -> jsonElement.asJsonObject.getAsJsonArray("lists")
                ?: error("Gemini response missing 'lists' array")
            else -> error("Unexpected JSON root type")
        }

        val parsedLists = parseLists(listsArray, warnings)
        if (parsedLists.isEmpty()) {
            error("No valid tasks were found in Gemini response")
        }

        ParsedCapture(
            lists = parsedLists,
            warnings = warnings
        )
    }

    private fun parseLists(
        listsArray: JsonArray,
        warnings: MutableList<String>
    ): List<ParsedListDraft> {
        val parsedLists = mutableListOf<ParsedListDraft>()

        forEachIndexed(listsArray) { index, listElement ->
            val listObject = asJsonObjectOrNull(listElement)
            if (listObject == null) {
                warnings += "Skipped list entry #$index because it is not an object"
                return@forEachIndexed
            }

            var listName = stringValue(listObject, "name").trim()
            if (listName.isBlank()) {
                listName = "Extracted Tasks"
                warnings += "List entry #$index had blank name; renamed to '$listName'"
            }

            val target = when (stringValue(listObject, "target").lowercase()) {
                "existing" -> ListTarget.EXISTING
                else -> ListTarget.NEW_LIST
            }

            val tasksArray = listObject.jsonArrayOrEmpty("tasks")
            val parsedTasks = mutableListOf<ParsedTaskDraft>()
            forEachIndexed(tasksArray) { taskIndex, taskElement ->
                val taskObject = asJsonObjectOrNull(taskElement)
                if (taskObject == null) {
                    warnings += "Dropped non-object task in list '$listName' at index $taskIndex"
                    return@forEachIndexed
                }

                parseTaskTree(taskObject, warnings, listName)?.let(parsedTasks::add)
            }

            if (parsedTasks.isEmpty()) {
                warnings += "Dropped empty list '$listName' because no valid tasks remained"
                return@forEachIndexed
            }

            val regroupedTasks = regroupColonHeadings(parsedTasks, warnings, listName)
            val normalizedTasks = regroupedTasks.mapNotNull { task ->
                normalizeToDepthTwo(task, warnings, listName)
            }

            if (normalizedTasks.isEmpty()) {
                warnings += "Dropped empty list '$listName' because no valid tasks remained"
                return@forEachIndexed
            }

            val normalizedList = normalizeListContent(
                listName = listName,
                tasks = normalizedTasks,
                warnings = warnings
            )

            parsedLists += ParsedListDraft(
                name = normalizedList.name,
                target = target,
                existingListId = nullableStringValue(listObject, "existingListId"),
                tasks = normalizedList.tasks
            )
        }

        return parsedLists.filter { it.tasks.isNotEmpty() }
    }

    private fun parseTaskTree(
        taskObject: JsonObject,
        warnings: MutableList<String>,
        listName: String
    ): ParsedTaskDraft? {
        val rawTitle = stringValue(taskObject, "title")
        if (isMarkedComplete(rawTitle)) {
            warnings += "Dropped completed task '$rawTitle' in list '$listName'"
            return null
        }

        val cleanedTitle = stripBulletSymbols(rawTitle).trim()
        if (cleanedTitle.isBlank()) {
            warnings += "Dropped blank task title in list '$listName'"
            return null
        }

        val normalizedTitle = normalizeTaskTitle(cleanedTitle)
        val dueDate = parseDueDate(
            rawDate = nullableStringValue(taskObject, "dueDate"),
            warnings = warnings,
            taskTitle = normalizedTitle
        )

        val subtasks = mutableListOf<ParsedTaskDraft>()
        val subtasksArray = taskObject.jsonArrayOrEmpty("subtasks")
        subtasksArray.forEach { childElement ->
            val childObject = asJsonObjectOrNull(childElement) ?: return@forEach
            parseTaskTree(childObject, warnings, listName)?.let(subtasks::add)
        }

        maybeSplitColonTask(normalizedTitle, dueDate, subtasks, warnings)?.let { return it }

        return ParsedTaskDraft(
            title = normalizedTitle,
            dueDate = dueDate,
            subtasks = subtasks
        )
    }

    private fun regroupColonHeadings(
        tasks: List<ParsedTaskDraft>,
        warnings: MutableList<String>,
        listName: String
    ): List<ParsedTaskDraft> {
        if (tasks.isEmpty()) return tasks

        val regrouped = mutableListOf<ParsedTaskDraft>()
        var index = 0
        while (index < tasks.size) {
            val current = tasks[index]
            if (!canAbsorbFollowingSiblings(current)) {
                regrouped += current
                index += 1
                continue
            }

            val headingTitle = current.title.removeSuffix(":").trim()
            val children = mutableListOf<ParsedTaskDraft>()
            var childIndex = index + 1
            while (childIndex < tasks.size && !canAbsorbFollowingSiblings(tasks[childIndex])) {
                children += tasks[childIndex]
                childIndex += 1
            }

            if (children.isEmpty()) {
                regrouped += current.copy(title = headingTitle)
                index += 1
                continue
            }

            warnings += "Grouped ${children.size} following task(s) under heading '$headingTitle' in list '$listName'"
            regrouped += current.copy(
                title = headingTitle,
                subtasks = current.subtasks + children
            )
            index = childIndex
        }

        return regrouped
    }

    private fun canAbsorbFollowingSiblings(task: ParsedTaskDraft): Boolean {
        return task.subtasks.isEmpty() &&
            task.dueDate == null &&
            task.title.trim().endsWith(":")
    }

    private fun normalizeListContent(
        listName: String,
        tasks: List<ParsedTaskDraft>,
        warnings: MutableList<String>
    ): NormalizedListContent {
        val wrapperTask = tasks.singleOrNull()
            ?.takeIf { it.subtasks.isNotEmpty() && it.dueDate == null }
            ?: return NormalizedListContent(listName, tasks)

        if (titlesEquivalent(wrapperTask.title, listName)) {
            warnings += "Removed duplicate wrapper task '${wrapperTask.title}' under list '$listName'"
            return NormalizedListContent(
                name = listName,
                tasks = wrapperTask.subtasks
            )
        }

        if (isGenericListName(listName) && isHeadingLikeTitle(wrapperTask.title)) {
            warnings += "Promoted heading '${wrapperTask.title}' to list name"
            return NormalizedListContent(
                name = wrapperTask.title,
                tasks = wrapperTask.subtasks
            )
        }

        return NormalizedListContent(listName, tasks)
    }

    private fun maybeSplitColonTask(
        cleanedTitle: String,
        dueDate: LocalDate?,
        subtasks: List<ParsedTaskDraft>,
        warnings: MutableList<String>
    ): ParsedTaskDraft? {
        if (subtasks.isNotEmpty()) return null
        if (cleanedTitle.count { it == ':' } != 1) return null

        val parts = cleanedTitle.split(":", limit = 2)
        if (parts.size != 2) return null

        val parentTitle = parts[0].trim()
        val childTitle = parts[1].trim()
        if (parentTitle.isBlank() || childTitle.isBlank()) return null
        if (!shouldSplitColonTitle(parentTitle)) return null

        val inlineSubtasks = splitInlineSubtasks(childTitle)
        warnings += if (inlineSubtasks.size == 1) {
            "Split task '$cleanedTitle' into parent '$parentTitle' and subtask '$childTitle' based on colon separator"
        } else {
            "Split task '$cleanedTitle' into parent '$parentTitle' and ${inlineSubtasks.size} subtasks based on colon separator"
        }
        return ParsedTaskDraft(
            title = parentTitle,
            dueDate = dueDate,
            subtasks = inlineSubtasks.map { subtaskTitle ->
                ParsedTaskDraft(
                    title = subtaskTitle,
                    dueDate = null,
                    subtasks = emptyList()
                )
            }
        )
    }

    private fun splitInlineSubtasks(childTitle: String): List<String> {
        val separators = listOf(";", "•", "|")
        val separator = separators.firstOrNull { childTitle.contains(it) }
        if (separator == null) {
            return listOf(childTitle)
        }

        return childTitle.split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(childTitle) }
    }

    private fun shouldSplitColonTitle(parentTitle: String): Boolean {
        if (parentTitle.length > 24) return false
        if (parentTitle.any(Char::isDigit)) return false

        val words = parentTitle.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 3) return false
        if (words.size == 1) return true

        return words.all { word ->
            word.firstOrNull()?.isUpperCase() == true
        }
    }

    private fun isHeadingLikeTitle(title: String): Boolean {
        val normalized = normalizeTaskTitle(title)
        if (normalized.length > 32) return false
        if (normalized.any(Char::isDigit)) return false

        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 4) return false
        if (words.size == 1) return true

        return words.count { it.firstOrNull()?.isUpperCase() == true } >= words.size - 1
    }

    private fun isGenericListName(listName: String): Boolean {
        return when (normalizeFreeformText(listName)) {
            "extracted tasks",
            "tasks",
            "task list",
            "to do",
            "todo",
            "to do list",
            "todo list",
            "list" -> true
            else -> false
        }
    }

    private fun titlesEquivalent(first: String, second: String): Boolean {
        return normalizeFreeformText(first) == normalizeFreeformText(second)
    }

    private fun normalizeTaskTitle(title: String): String {
        return title.trim()
    }

    private fun normalizeFreeformText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeToDepthTwo(
        rootTask: ParsedTaskDraft,
        warnings: MutableList<String>,
        listName: String
    ): ParsedTaskDraft? {
        val rootTitle = rootTask.title.trim().removeSuffix(":").trim()
        if (rootTitle.isBlank()) {
            warnings += "Dropped blank root task in list '$listName'"
            return null
        }

        val directChildren = mutableListOf<ParsedTaskDraft>()
        val flattenedChildren = mutableListOf<ParsedTaskDraft>()

        rootTask.subtasks.forEach { child ->
            val childTitle = child.title.trim().removeSuffix(":").trim()
            if (childTitle.isBlank()) {
                warnings += "Dropped blank child task under '$rootTitle'"
                return@forEach
            }

            directChildren += child.copy(
                title = childTitle,
                subtasks = emptyList()
            )

            child.subtasks.forEach { deep ->
                flattenTaskBranch(
                    task = deep,
                    prefix = childTitle,
                    warnings = warnings,
                    sink = flattenedChildren
                )
            }
        }

        return rootTask.copy(
            title = rootTitle,
            subtasks = directChildren + flattenedChildren
        )
    }

    private fun flattenTaskBranch(
        task: ParsedTaskDraft,
        prefix: String,
        warnings: MutableList<String>,
        sink: MutableList<ParsedTaskDraft>
    ) {
        val taskTitle = task.title.trim()
        if (taskTitle.isBlank()) {
            warnings += "Dropped blank nested subtask under '$prefix'"
            return
        }

        val flattenedTitle = "$prefix / $taskTitle"
        sink += ParsedTaskDraft(
            title = flattenedTitle,
            dueDate = task.dueDate,
            subtasks = emptyList()
        )
        warnings += "Flattened deep nested task '$flattenedTitle' to max depth 2"

        task.subtasks.forEach { deeper ->
            flattenTaskBranch(
                task = deeper,
                prefix = flattenedTitle,
                warnings = warnings,
                sink = sink
            )
        }
    }

    private fun parseDueDate(
        rawDate: String?,
        warnings: MutableList<String>,
        taskTitle: String
    ): LocalDate? {
        val trimmed = rawDate?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) {
            return null
        }

        return runCatching { LocalDate.parse(trimmed) }.getOrElse {
            warnings += "Dropped invalid dueDate '$trimmed' for task '$taskTitle'"
            null
        }
    }

    private fun extractJsonPayload(rawResponse: String): String {
        val fencedRegex = Regex("(?is)```(?:json)?\\s*([\\[{][\\s\\S]*[\\]}])\\s*```")
        val fencedMatch = fencedRegex.find(rawResponse)
        if (fencedMatch != null) {
            return fencedMatch.groupValues[1].trim()
        }

        val firstBrace = rawResponse.indexOf('{')
        val firstBracket = rawResponse.indexOf('[')
        val start = when {
            firstBrace >= 0 && firstBracket >= 0 -> minOf(firstBrace, firstBracket)
            firstBrace >= 0 -> firstBrace
            firstBracket >= 0 -> firstBracket
            else -> -1
        }

        val lastBrace = rawResponse.lastIndexOf('}')
        val lastBracket = rawResponse.lastIndexOf(']')
        val end = maxOf(lastBrace, lastBracket)

        if (start >= 0 && end > start) {
            return rawResponse.substring(start, end + 1).trim()
        }

        error("Gemini response did not contain a JSON object or array")
    }

    private fun stripBulletSymbols(rawTitle: String): String {
        return rawTitle
            .replace(Regex("^\\s*(?:\\[[ xX]?\\]|\\([ xX]?\\))\\s*"), "")
            .replace(Regex("^\\s*[-*+o>]+\\s*"), "")
            .replace(Regex("^\\d+[.)]\\s*"), "")
            .replace(Regex("^[a-zA-Z][.)]\\s*"), "")
    }

    private fun isMarkedComplete(rawTitle: String): Boolean {
        val trimmed = rawTitle.trim()
        return trimmed.startsWith("[x]", ignoreCase = true) ||
            trimmed.startsWith("(x)", ignoreCase = true) ||
            trimmed.startsWith("done ", ignoreCase = true) ||
            trimmed.startsWith("completed ", ignoreCase = true) ||
            trimmed.startsWith("done:", ignoreCase = true) ||
            trimmed.startsWith("completed:", ignoreCase = true) ||
            trimmed.startsWith("✓") ||
            trimmed.startsWith("✔")
    }

    private fun stringValue(jsonObject: JsonObject, key: String): String {
        val element = jsonObject.get(key) ?: return ""
        return runCatching { element.asString }.getOrDefault("")
    }

    private fun nullableStringValue(jsonObject: JsonObject, key: String): String? {
        val value = jsonObject.get(key) ?: return null
        if (value.isJsonNull) return null

        val raw = runCatching { value.asString }.getOrNull()?.trim() ?: return null
        return raw.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun asJsonObjectOrNull(element: JsonElement): JsonObject? {
        return runCatching { element.asJsonObject }.getOrNull()
    }

    private fun JsonObject.jsonArrayOrEmpty(key: String): JsonArray {
        return runCatching { getAsJsonArray(key) }.getOrNull() ?: JsonArray()
    }

    private inline fun forEachIndexed(
        array: JsonArray,
        action: (index: Int, JsonElement) -> Unit
    ) {
        for (index in 0 until array.size()) {
            action(index, array[index])
        }
    }
}
