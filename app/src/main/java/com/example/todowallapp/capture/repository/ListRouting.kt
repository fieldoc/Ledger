package com.example.todowallapp.capture.repository

import com.example.todowallapp.capture.model.ListTarget
import com.example.todowallapp.capture.model.ParsedCapture
import com.example.todowallapp.capture.model.ParsedListDraft

data class ExistingListRef(
    val id: String,
    val title: String
)

class ListRoutingResolver {

    fun resolve(parsedCapture: ParsedCapture, existingLists: List<ExistingListRef>): ParsedCapture {
        val mappedLists = parsedCapture.lists.map { listDraft ->
            resolveListDraft(listDraft, existingLists)
        }
        return parsedCapture.copy(lists = mappedLists)
    }

    private fun resolveListDraft(
        listDraft: ParsedListDraft,
        existingLists: List<ExistingListRef>
    ): ParsedListDraft {
        if (listDraft.target != ListTarget.EXISTING) {
            return listDraft
        }

        val directMatch = listDraft.existingListId
            ?.let { expectedId -> existingLists.firstOrNull { it.id == expectedId } }
        if (directMatch != null) {
            return listDraft.copy(existingListId = directMatch.id, name = directMatch.title)
        }

        val normalizedName = normalizeName(listDraft.name)
        if (normalizedName.isEmpty()) {
            return listDraft.copy(target = ListTarget.NEW_LIST, existingListId = null)
        }

        val exact = existingLists.firstOrNull { normalizeName(it.title) == normalizedName }
        if (exact != null) {
            return listDraft.copy(
                target = ListTarget.EXISTING,
                existingListId = exact.id,
                name = exact.title
            )
        }

        val fuzzy = existingLists.firstOrNull { existing ->
            val normalizedExisting = normalizeName(existing.title)
            normalizedExisting.startsWith(normalizedName) ||
                normalizedName.startsWith(normalizedExisting) ||
                normalizedExisting.contains(normalizedName) ||
                normalizedName.contains(normalizedExisting)
        }

        return if (fuzzy != null) {
            listDraft.copy(
                target = ListTarget.EXISTING,
                existingListId = fuzzy.id,
                name = fuzzy.title
            )
        } else {
            listDraft.copy(target = ListTarget.NEW_LIST, existingListId = null)
        }
    }

    fun normalizeName(rawName: String): String {
        return rawName
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
