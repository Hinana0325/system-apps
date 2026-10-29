package cn.xinxitong.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLogicTest {
    private fun note(id: String, body: String, updated: Long, pinned: Boolean = false, created: Long = updated) =
        Note(id, noteTitleFromBody(body), body, created, updated, pinned)

    @Test
    fun titleFromBodyUsesFirstNonBlankLine() {
        assertEquals("第一行", noteTitleFromBody("\n  第一行  \n第二行"))
        assertEquals("无标题", noteTitleFromBody("   \n  "))
        assertEquals("无标题", noteTitleFromBody(""))
        assertEquals("a".repeat(30), noteTitleFromBody("a".repeat(50)))
    }

    @Test
    fun countsAreReal() {
        assertEquals(5, countCharacters("你好 世界"))
        assertEquals(0, countCharacters("   "))
        assertEquals(3, countLines("a\nb\nc"))
        assertEquals(0, countLines(""))
        assertEquals(1, countLines("一行"))
    }

    @Test
    fun sortNotesPinnedFirstThenUpdatedDesc() {
        val notes = listOf(
            note("1", "旧", 100),
            note("2", "新", 300),
            note("3", "置顶旧", 50, pinned = true),
            note("4", "置顶新", 200, pinned = true),
        )
        val sorted = sortNotes(notes)
        assertEquals(listOf("4", "3", "2", "1"), sorted.map { it.id })
    }

    @Test
    fun searchNotesMatchesTitleAndBody() {
        val notes = listOf(
            note("1", "购物清单\n买牛奶", 100),
            note("2", "会议\n讨论内核", 200),
            note("3", "随想", 300),
        )
        assertEquals(listOf("1"), searchNotes(notes, "牛奶").map { it.id })
        assertEquals(listOf("2"), searchNotes(notes, "会议").map { it.id })
        assertEquals(3, searchNotes(notes, "").size)
        assertEquals(0, searchNotes(notes, "不存在").size)
    }

    @Test
    fun updateNoteBodyRefreshesTitleAndTime() {
        val original = note("1", "旧标题\n正文", 100)
        val updated = updateNoteBody(original, "新标题\n新正文", 500)
        assertEquals("新标题", updated.title)
        assertEquals(500L, updated.updatedAt)
        assertEquals(100L, updated.createdAt)
        assertEquals("1", updated.id)
    }

    @Test
    fun storeRoundTripPersistsNotes() {
        val temp = java.io.File.createTempFile("notes-test", ".json")
        try {
            val store = NoteStore(temp)
            assertTrue(store.loadAll().isEmpty())
            val created = createNote("标题\n正文内容", 1000)
            store.upsert(created)
            val loaded = store.loadAll()
            assertEquals(1, loaded.size)
            assertEquals("标题", loaded[0].title)
            assertEquals("标题\n正文内容", loaded[0].body)
            store.togglePin(created.id, 2000)
            val pinned = store.loadAll().first()
            assertTrue(pinned.pinned)
            assertEquals(2000L, pinned.updatedAt)
            store.delete(created.id)
            assertTrue(store.loadAll().isEmpty())
        } finally {
            temp.delete()
        }
    }
}
