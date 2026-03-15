package com.example.todowallapp.capture.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiJsonParserTest {

    private val parser = GeminiJsonParser()

    @Test
    fun `parses strict json payload`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "Inbox",
                  "target": "new_list",
                  "existingListId": null,
                  "tasks": [
                    {"title":"Buy milk","dueDate":"2026-02-25","subtasks":[]}
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = parser.parse(raw).getOrThrow()

        assertEquals(1, result.lists.size)
        assertEquals("Inbox", result.lists.first().name)
        assertEquals("Buy milk", result.lists.first().tasks.first().title)
    }

    @Test
    fun `parses fenced json payload`() {
        val raw = """
            ```json
            {
              "lists": [
                {
                  "name": "Home",
                  "target": "existing",
                  "existingListId": "abc",
                  "tasks": [{"title":"Laundry","dueDate":null,"subtasks":[]}]
                }
              ]
            }
            ```
        """.trimIndent()

        val result = parser.parse(raw).getOrThrow()

        assertEquals(1, result.lists.size)
        assertEquals("abc", result.lists.first().existingListId)
    }

    @Test
    fun `fails on malformed json`() {
        val raw = """{"lists":[{"name":"Inbox","tasks":[{"title":"A"}]}"""
        assertTrue(parser.parse(raw).isFailure)
    }

    @Test
    fun `flattens deep nesting to max depth two`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "Deep",
                  "target": "new_list",
                  "existingListId": null,
                  "tasks": [
                    {
                      "title": "Parent",
                      "dueDate": null,
                      "subtasks": [
                        {
                          "title": "Child",
                          "dueDate": null,
                          "subtasks": [
                            {"title":"Grandchild","dueDate":null,"subtasks":[]}
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()
        val root = parsed.lists.first().tasks.first()
        val childTitles = root.subtasks.map { it.title }

        assertEquals(listOf("Child", "Child / Grandchild"), childTitles)
        assertTrue(parsed.warnings.any { it.contains("Flattened deep nested task") })
    }

    @Test
    fun `splits task by colon as fallback`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "Fallback",
                  "target": "new_list",
                  "tasks": [
                    {
                      "title": "Painting: Buy Paint",
                      "dueDate": "2026-03-09",
                      "subtasks": []
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()
        val root = parsed.lists.first().tasks.first()

        assertEquals("Painting", root.title)
        assertEquals("2026-03-09", root.dueDate.toString())
        assertEquals(1, root.subtasks.size)
        assertEquals("Buy Paint", root.subtasks.first().title)
        assertTrue(parsed.warnings.any { it.contains("Split task 'Painting: Buy Paint'") })
    }

    @Test
    fun `does not split descriptive colon title`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "Reading",
                  "target": "new_list",
                  "tasks": [
                    {
                      "title": "Read Chapter 3: Photosynthesis",
                      "dueDate": null,
                      "subtasks": []
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()
        val root = parsed.lists.first().tasks.first()

        assertEquals("Read Chapter 3: Photosynthesis", root.title)
        assertTrue(root.subtasks.isEmpty())
        assertTrue(parsed.warnings.none { it.contains("Split task 'Read Chapter 3: Photosynthesis'") })
    }

    @Test
    fun `promotes sole wrapper heading to list name`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "",
                  "target": "new_list",
                  "tasks": [
                    {
                      "title": "Renovation",
                      "dueDate": null,
                      "subtasks": [
                        {"title":"Painting","dueDate":null,"subtasks":[]},
                        {"title":"Flooring","dueDate":null,"subtasks":[]}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()
        val list = parsed.lists.first()

        assertEquals("Renovation", list.name)
        assertEquals(listOf("Painting", "Flooring"), list.tasks.map { it.title })
        assertTrue(parsed.warnings.any { it.contains("Promoted heading 'Renovation' to list name") })
    }

    @Test
    fun `unwraps duplicate wrapper task matching list name`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "Renovation",
                  "target": "new_list",
                  "tasks": [
                    {
                      "title": "Renovation",
                      "dueDate": null,
                      "subtasks": [
                        {"title":"Painting","dueDate":null,"subtasks":[]}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()
        val list = parsed.lists.first()

        assertEquals("Renovation", list.name)
        assertEquals(listOf("Painting"), list.tasks.map { it.title })
        assertTrue(parsed.warnings.any { it.contains("Removed duplicate wrapper task 'Renovation'") })
    }

    @Test
    fun `strips trailing colon from heading task with subtasks`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "House",
                  "target": "new_list",
                  "tasks": [
                    {
                      "title": "Painting:",
                      "dueDate": null,
                      "subtasks": [
                        {"title":"Buy Paint","dueDate":null,"subtasks":[]}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()
        val root = parsed.lists.first().tasks.first()

        assertEquals("Painting", root.title)
        assertEquals("Buy Paint", root.subtasks.first().title)
    }

    @Test
    fun `splits colon fallback into multiple semicolon subtasks`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "House",
                  "target": "new_list",
                  "tasks": [
                    {
                      "title": "Painting: Buy Paint; Tape Edges; Prime Walls",
                      "dueDate": null,
                      "subtasks": []
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()
        val root = parsed.lists.first().tasks.first()

        assertEquals("Painting", root.title)
        assertEquals(
            listOf("Buy Paint", "Tape Edges", "Prime Walls"),
            root.subtasks.map { it.title }
        )
        assertTrue(parsed.warnings.any { it.contains("3 subtasks") })
    }

    @Test
    fun `strips bullet and numbered prefixes`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "Errands",
                  "target": "new_list",
                  "tasks": [
                    {"title":"- Buy milk","dueDate":null,"subtasks":[]},
                    {"title":"1. Pick up dry cleaning","dueDate":null,"subtasks":[]}
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()

        assertEquals(
            listOf("Buy milk", "Pick up dry cleaning"),
            parsed.lists.first().tasks.map { it.title }
        )
    }

    @Test
    fun `drops invalid due date with warning`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "Inbox",
                  "target": "new_list",
                  "tasks": [
                    {"title":"Buy milk","dueDate":"Fridayish","subtasks":[]}
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()
        val root = parsed.lists.first().tasks.first()

        assertEquals(null, root.dueDate)
        assertTrue(parsed.warnings.any { it.contains("Dropped invalid dueDate 'Fridayish'") })
    }

    @Test
    fun `keeps colon title when subtasks already provided`() {
        val raw = """
            {
              "lists": [
                {
                  "name": "Study",
                  "target": "new_list",
                  "tasks": [
                    {
                      "title": "Read Chapter 3: Photosynthesis",
                      "dueDate": null,
                      "subtasks": [
                        {"title":"Take notes","dueDate":null,"subtasks":[]}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(raw).getOrThrow()
        val root = parsed.lists.first().tasks.first()

        assertEquals("Read Chapter 3: Photosynthesis", root.title)
        assertEquals(listOf("Take notes"), root.subtasks.map { it.title })
        assertFalse(parsed.warnings.any { it.contains("Split task 'Read Chapter 3: Photosynthesis'") })
    }
}
