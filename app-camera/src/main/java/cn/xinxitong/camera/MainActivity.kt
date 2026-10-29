package cn.xinxitong.camera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.xinxitong.coreui.components.AppTopBar
import cn.xinxitong.coreui.components.EmptyState
import cn.xinxitong.coreui.theme.SystemAppsTheme
import cn.xinxitong.coreui.theme.ThemeMode
import cn.xinxitong.coreui.theme.ThemePreference
import cn.xinxitong.coreui.theme.resolveDarkTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePreference = ThemePreference(applicationContext)
        setContent {
            val mode by themePreference.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            SystemAppsTheme(darkTheme = resolveDarkTheme(mode, isSystemInDarkTheme())) {
                CameraScreen()
            }
        }
    }
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraAvailable by remember { mutableStateOf(true) }
    var lastCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var lastCaptureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    fun loadLastCaptureThumbnail(uri: Uri) {
        // 两遍解码、按 256px 目标采样：只做 48dp 缩略图，不把原图全尺寸读进内存。
        lastCaptureBitmap = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sample = 1
            val target = 256
            while ((bounds.outWidth / sample) > target || (bounds.outHeight / sample) > target) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        }.getOrNull()
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        val fileName = captureFileName(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, CAPTURE_RELATIVE_PATH)
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build()
        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    lastCaptureUri = uri
                    if (uri != null) loadLastCaptureThumbnail(uri)
                    Toast.makeText(context, "已保存：$fileName", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(context, "拍照失败：${exception.message}", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    Scaffold(topBar = { AppTopBar(title = "相机") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!hasPermission) {
                EmptyState("需要“相机”权限才能预览和拍照。")
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("授予相机权限") }
                return@Column
            }
            val error = cameraError
            if (error != null) {
                EmptyState(error)
                return@Column
            }
            if (!cameraAvailable) {
                EmptyState("此设备没有可用摄像头（例如未配置摄像头的模拟器），无法显示取景器。")
                return@Column
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        val providerFuture = ProcessCameraProvider.getInstance(context)
                        providerFuture.addListener(
                            {
                                runCatching {
                                    val provider = providerFuture.get()
                                    val selector = CameraSelector.Builder()
                                        .requireLensFacing(lensFacing)
                                        .build()
                                    cameraAvailable = provider.hasCamera(selector)
                                    cameraError = null
                                    if (!cameraAvailable) {
                                        return@runCatching
                                    }
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setFlashMode(toImageCaptureFlashMode(flashMode))
                                        .build()
                                    provider.unbindAll()
                                    camera = provider.bindToLifecycle(
                                        lifecycleOwner, selector, preview, capture,
                                    )
                                    imageCapture = capture
                                    hasFlashUnit = camera?.cameraInfo?.hasFlashUnit() == true
                                }.onFailure {
                                    cameraError = "相机初始化失败：${it.message}"
                                }
                            },
                            ContextCompat.getMainExecutor(context),
                        )
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 上一张照片缩略图；点按用系统查看器打开原图。
                val bitmap = lastCaptureBitmap
                val uri = lastCaptureUri
                if (bitmap != null && uri != null) {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "image/jpeg")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(context, "没有能查看图片的应用", Toast.LENGTH_SHORT).show()
                            }
                    }) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "上一张照片",
                            modifier = Modifier.size(48.dp),
                        )
                    }
                } else {
                    Text(
                        "未拍照",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp).padding(8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        if (hasFlashUnit) {
                            flashMode = nextFlashMode(flashMode)
                            imageCapture?.flashMode = toImageCaptureFlashMode(flashMode)
                        } else {
                            Toast.makeText(context, "此摄像头不支持闪光灯", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Icon(
                        if (flashMode == FlashMode.OFF) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                        contentDescription = flashModeLabel(flashMode),
                    )
                }
                IconButton(onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = "切换前后摄像头")
                }
                Button(onClick = ::takePhoto) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Text("拍照")
                }
            }
            Text(
                text = flashModeLabel(flashMode) + if (hasFlashUnit) "" else "（无闪光灯）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
            )
            // 每次镜头/闪光模式变化后，预览用例会在下一次 update 中重新绑定。
            LaunchedEffect(lensFacing, flashMode) {
                imageCapture?.flashMode = toImageCaptureFlashMode(flashMode)
            }
        }
    }
}

/** 把与平台无关的闪光模式映射到 CameraX 的 ImageCapture 常量。 */
private fun toImageCaptureFlashMode(mode: FlashMode): Int = when (mode) {
    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
}
