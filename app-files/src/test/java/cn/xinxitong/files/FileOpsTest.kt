package cn.xinxitong.files

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileOpsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun entry(name: String, isDir: Boolean, size: Long, modified: Long): FileEntry =
        FileEntry(File(tempFolder.root, name), name, isDir, size, modified, false)

    @Test
    fun sortByNamePutsDirectoriesFirst() {
        val entries = listOf(
            entry("b.txt", false, 10, 3),
            entry("Folder", true, 0, 1),
            entry("a.txt", false, 20, 2),
        )
        val sorted = sortEntries(entries, SortMode.NAME)
        assertEquals(listOf("Folder", "a.txt", "b.txt"), sorted.map { it.name })
    }

    @Test
    fun sortByDatePutsNewestFirst() {
        val entries = listOf(
            entry("old.txt", false, 10, 100),
            entry("new.txt", false, 10, 300),
            entry("mid.txt", false, 10, 200),
        )
        assertEquals(listOf("new.txt", "mid.txt", "old.txt"), sortEntries(entries, SortMode.DATE).map { it.name })
    }

    @Test
    fun sortBySizePutsLargestFirst() {
        val entries = listOf(
            entry("small.txt", false, 1, 1),
            entry("big.txt", false, 99, 1),
        )
        assertEquals(listOf("big.txt", "small.txt"), sortEntries(entries, SortMode.SIZE).map { it.name })
    }

    @Test
    fun formatSizeFormatsUnits() {
        assertEquals("2.0 GB", formatSize(2L * 1024 * 1024 * 1024))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("0 B", formatSize(0))
    }

    @Test
    fun copyAndDeleteRoundTrip() {
        val source = tempFolder.newFile("source.txt").apply { writeText("hello") }
        val target = File(tempFolder.root, "copy.txt")
        assertTrue(copyRecursively(source, target).isSuccess)
        assertEquals("hello", target.readText())
        assertTrue(deleteRecursively(target).isSuccess)
        assertTrue(!target.exists())
    }

    @Test
    fun searchFindsByNameRecursively() {
        val sub = tempFolder.newFolder("sub")
        File(sub, "report-2026.txt").writeText("x")
        File(tempFolder.root, "other.txt").writeText("x")
        val results = searchFiles(tempFolder.root, "report")
        assertEquals(listOf("report-2026.txt"), results.map { it.name })
    }
}
