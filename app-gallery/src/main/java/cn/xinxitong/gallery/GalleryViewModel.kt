package cn.xinxitong.gallery

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 同时查图片与视频两个 MediaStore 集合，合并后按时间倒序。 */
    fun loadMedia() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val result = mutableListOf<MediaItem>()
                    result += queryCollection(
                        resolver,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        isVideo = false,
                    )
                    result += queryCollection(
                        resolver,
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        isVideo = true,
                    )
                    sortMediaNewestFirst(result)
                }
            }.onSuccess { _items.value = it }
                .onFailure { _error.value = "读取媒体库失败：${it.message}" }
            _loading.value = false
        }
    }

    private fun queryCollection(
        resolver: android.content.ContentResolver,
        collection: Uri,
        isVideo: Boolean,
    ): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val result = mutableListOf<MediaItem>()
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val bucketIndex = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val widthIndex = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightIndex = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = if (idIndex >= 0) cursor.getLong(idIndex) else continue
                result.add(
                    MediaItem(
                        id = id,
                        uriString = ContentUris.withAppendedId(collection, id).toString(),
                        bucket = if (bucketIndex >= 0) cursor.getString(bucketIndex).orEmpty() else "",
                        displayName = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else "",
                        dateMillis = if (dateIndex >= 0) cursor.getLong(dateIndex) * 1000 else 0L,
                        sizeBytes = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L,
                        width = if (widthIndex >= 0) cursor.getInt(widthIndex) else 0,
                        height = if (heightIndex >= 0) cursor.getInt(heightIndex) else 0,
                        mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else "",
                        isVideo = isVideo,
                        relativePath = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else "",
                    ),
                )
            }
        }
        return result
    }

    /**
     * 删除一条媒体。返回 true 表示已直接删除；若抛出可恢复的安全异常，
     * 调用方应通过 MediaStore.createDeleteRequest 弹出系统确认（见 MainActivity）。
     */
    fun deleteItem(item: MediaItem): Boolean {
        return runCatching {
            val deleted = getApplication<Application>().contentResolver.delete(item.uri, null, null)
            if (deleted > 0) {
                _items.value = _items.value.filterNot { it.uriString == item.uriString }
                true
            } else {
                _error.value = "删除失败：系统未删除该文件"
                false
            }
        }.getOrElse {
            // 可恢复异常不在此消化：调用方据此触发系统删除确认。
            if (it is android.app.RecoverableSecurityException) throw it
            _error.value = "删除失败：${it.message}"
            false
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
