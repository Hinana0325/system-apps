package cn.xinxitong.camera

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 拍照保存目录（MediaStore 相对路径）。 */
const val CAPTURE_RELATIVE_PATH = "Pictures/XinXiTong"

/** 闪光模式（与平台无关的枚举；到 ImageCapture 常量的映射在 MainActivity 里）。 */
enum class FlashMode { OFF, AUTO, ON }

/** 按时间戳生成文件名，如 IMG_20261029_103045.jpg。纯函数。 */
fun captureFileName(nowMillis: Long): String {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    return "IMG_${sdf.format(Date(nowMillis))}.jpg"
}

/** 闪光模式循环：关闭 -> 自动 -> 打开 -> 关闭。纯函数。 */
fun nextFlashMode(current: FlashMode): FlashMode = when (current) {
    FlashMode.OFF -> FlashMode.AUTO
    FlashMode.AUTO -> FlashMode.ON
    FlashMode.ON -> FlashMode.OFF
}

fun flashModeLabel(mode: FlashMode): String = when (mode) {
    FlashMode.ON -> "闪光灯：开"
    FlashMode.AUTO -> "闪光灯：自动"
    FlashMode.OFF -> "闪光灯：关"
}
