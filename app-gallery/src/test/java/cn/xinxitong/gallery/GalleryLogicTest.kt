package cn.xinxitong.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryLogicTest {
    private fun item(id: Long, bucket: String, name: String, date: Long, isVideo: Boolean = false) =
        MediaItem(
            id = id,
            uriString = "content://media/external/images/media/$id",
            bucket = bucket,
            displayName = name,
            dateMillis = date,
            sizeBytes = 100,
            width = 100,
            height = 100,
            mimeType = "image/jpeg",
            isVideo = isVideo,
            relativePath = "",
        )

    @Test
    fun sortMediaNewestFirstOrdersByDateDescending() {
        val items = listOf(item(1, "A", "a.jpg", 100), item(2, "A", "b.jpg", 300), item(3, "B", "c.jpg", 200))
        assertEquals(listOf(2L, 3L, 1L), sortMediaNewestFirst(items).map { it.id })
    }

    @Test
    fun groupByAlbumGroupsByBucket() {
        val items = listOf(item(1, "Camera", "a.jpg", 100), item(2, "Camera", "b.jpg", 200), item(3, "Screenshots", "c.jpg", 300))
        val grouped = groupByAlbum(items)
        assertEquals(2, grouped.size)
        assertEquals(2, grouped["Camera"]?.size)
        assertEquals(1, grouped["Screenshots"]?.size)
    }

    @Test
    fun filterMediaFiltersByAlbumAndQuery() {
        val items = listOf(item(1, "Camera", "beach.jpg", 100), item(2, "Screenshots", "shot.png", 200))
        assertEquals(1, filterMedia(items, "Camera", "").size)
        assertEquals(1, filterMedia(items, null, "beach").size)
        assertEquals(0, filterMedia(items, "Camera", "shot").size)
    }

    @Test
    fun formatMediaSizeFormatsUnits() {
        assertEquals("0 B", formatMediaSize(0))
        assertEquals("512 B", formatMediaSize(512))
        assertEquals("1.5 KB", formatMediaSize(1536))
        assertEquals("2.0 MB", formatMediaSize(2_097_152))
    }
}
