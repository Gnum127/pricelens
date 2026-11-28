package com.pricelens.data.camera

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class CameraXCameraController(private val context: Context) : CameraController {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var previewView: PreviewView? = null

    override suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        view: PreviewView,
    ) {
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(displayRotation())
            .build()

        provider.unbindAll()
        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
        )
        cameraProvider = provider
        imageCapture = capture
        previewView = view
    }

    override suspend fun captureToFile(file: File): Boolean {
        val capture = imageCapture ?: return false
        // Ценник обычно лежит в рамке по центру: фокус по центру снижает число смазанных кадров.
        // PreviewView даёт публичную фабрику точек; создавать MeteringPointFactory вручную нельзя.
        val center = runCatching { previewView?.meteringPointFactory?.createPoint(0.5f, 0.5f) }
            .getOrNull()
        val focus = center?.let { point ->
            camera?.cameraControl?.startFocusAndMetering(
                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .disableAutoCancel()
                    .build(),
            )
        }
        // Фокус не должен задерживать снимок бесконечно; 500 мс достаточно для ценника.
        withContext(Dispatchers.IO) {
            try {
                focus?.get(FOCUS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                null
            }
        }
        return suspendCancellableCoroutine { continuation ->
            val completion = CaptureCompletion(
                file = file,
                isActive = { continuation.isActive },
                resume = { saved -> continuation.resume(saved) },
            )
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            capture.takePicture(
                options,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(results: ImageCapture.OutputFileResults) =
                        completion.finish(true)

                    override fun onError(exception: ImageCaptureException) =
                        completion.finish(false)
                },
            )
            continuation.invokeOnCancellation { completion.cancel() }
        }
    }

    override fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        camera = null
        previewView = null
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: 0
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.rotation
        }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 500L
    }
}
