package com.pricelens.data.camera

import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.pricelens.data.ocr.TextRecognizer
import com.pricelens.domain.model.RawTextResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Proxy

class CameraImageTextProviderTest {
    private val permission = FakePermissionChecker()
    private val camera = FakeCameraController()
    private val ocr = FakeTextRecognizer()
    private val image = CountingImageProxy()

    private val provider = CameraImageTextProvider(camera, ocr, permission)

    @Test
    fun deniedPermissionDoesNotTakePicture() = runBlocking {
        permission.granted = false

        assertThat(provider.readText()).isEqualTo(RawTextResult.PermissionDenied)
        assertThat(camera.takeCalls).isEqualTo(0)
        assertThat(image.closeCount).isEqualTo(0)
    }

    @Test
    fun failedCaptureBecomesCameraError() = runBlocking {
        camera.image = null

        assertThat(provider.readText()).isEqualTo(RawTextResult.CameraError)
        assertThat(camera.takeCalls).isEqualTo(1)
        assertThat(image.closeCount).isEqualTo(0)
    }

    @Test
    fun nullTextBecomesEmptyTextAndClosesImage() = runBlocking {
        camera.image = image.proxy
        ocr.text = null

        assertThat(provider.readText()).isEqualTo(RawTextResult.EmptyText)
        assertThat(image.closeCount).isEqualTo(1)
    }

    @Test
    fun blankTextBecomesEmptyTextAndClosesImage() = runBlocking {
        camera.image = image.proxy
        ocr.text = "   \n\t"

        assertThat(provider.readText()).isEqualTo(RawTextResult.EmptyText)
        assertThat(image.closeCount).isEqualTo(1)
    }

    @Test
    fun recognizedTextBecomesSuccessAndClosesImage() = runBlocking {
        camera.image = image.proxy
        ocr.text = "350 г 240 ₽"

        assertThat(provider.readText()).isEqualTo(RawTextResult.Success("350 г 240 ₽"))
        assertThat(ocr.lastImage).isEqualTo(image.proxy)
        assertThat(image.closeCount).isEqualTo(1)
    }

    @Test
    fun recognizerFailureStillClosesImage() {
        camera.image = image.proxy
        ocr.failure = IllegalStateException("ocr crashed")

        assertThrows<IllegalStateException> { runBlocking { provider.readText() } }

        assertThat(image.closeCount).isEqualTo(1)
    }

    private class FakePermissionChecker : CameraPermissionChecker {
        var granted: Boolean = true
        override fun isGranted(): Boolean = granted
    }

    private class FakeCameraController : CameraController {
        var image: ImageProxy? = null
        var takeCalls: Int = 0

        override suspend fun bind(
            lifecycleOwner: LifecycleOwner,
            surfaceProvider: Preview.SurfaceProvider,
        ) = Unit

        override suspend fun takePicture(): ImageProxy? {
            takeCalls++
            return image
        }

        override fun unbind() = Unit
    }

    private class FakeTextRecognizer : TextRecognizer {
        var text: String? = null
        var failure: Throwable? = null
        var lastImage: ImageProxy? = null

        override fun recognize(image: ImageProxy): String? {
            lastImage = image
            failure?.let { throw it }
            return text
        }
    }

    private class CountingImageProxy {
        var closeCount: Int = 0
        lateinit var proxy: ImageProxy

        init {
            proxy = Proxy.newProxyInstance(
                ImageProxy::class.java.classLoader,
                arrayOf(ImageProxy::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "close" -> {
                        closeCount++
                        null
                    }

                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "FakeImageProxy"
                    "equals" -> args?.get(0) === proxy
                    else -> null
                }
            } as ImageProxy
        }
    }
}
