package com.example.todowallapp.capture.repository

import com.example.todowallapp.capture.model.ListTarget
import com.example.todowallapp.capture.model.ParsedCapture
import com.example.todowallapp.capture.model.ParsedListDraft
import com.example.todowallapp.capture.model.ParsedTaskDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListRoutingResolverTest {

    private val resolver = ListRoutingResolver()

    @Test
    fun `uses existing id when it matches`() {
        val capture = ParsedCapture(
            lists = listOf(
                ParsedListDraft(
                    name = "Work",
                    target = ListTarget.EXISTING,
                    existingListId = "work-id",
                    tasks = listOf(ParsedTaskDraft(title = "Task A"))
                )
            )
        )

        val resolved = resolver.resolve(
            capture,
            existingLists = listOf(ExistingListRef(id = "work-id", title = "Work"))
        )

        val list = resolved.lists.first()
        assertEquals(ListTarget.EXISTING, list.target)
        assertEquals("work-id", list.existingListId)
    }

    @Test
    fun `falls back to normalized name match when id is missing`() {
        val capture = ParsedCapture(
            lists = listOf(
                ParsedListDraft(
                    name = "Home!",
                    target = ListTarget.EXISTING,
                    existingListId = null,
                    tasks = listOf(ParsedTaskDraft(title = "Task A"))
                )
            )
        )

        val resolved = resolver.resolve(
            capture,
            existingLists = listOf(ExistingListRef(id = "home", title = "Home"))
        )

        val list = resolved.lists.first()
        assertEquals(ListTarget.EXISTING, list.target)
        assertEquals("home", list.existingListId)
    }

    @Test
    fun `falls back to new list when existing target cannot be resolved`() {
        val capture = ParsedCapture(
            lists = listOf(
                ParsedListDraft(
                    name = "Unmatched",
                    target = ListTarget.EXISTING,
                    existingListId = "missing",
                    tasks = listOf(ParsedTaskDraft(title = "Task A"))
                )
            )
        )

        val resolved = resolver.resolve(
            capture,
            existingLists = listOf(ExistingListRef(id = "known", title = "Known"))
        )

        val list = resolved.lists.first()
        assertEquals(ListTarget.NEW_LIST, list.target)
        assertNull(list.existingListId)
    }
}
