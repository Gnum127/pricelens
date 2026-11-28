package com.pricelens.data.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.pricelens.data.ocr.OcrEngineUnavailableException
import com.pricelens.data.ocr.TextRecognizer
import com.pricelens.domain.model.OcrFrame
import com.pricelens.domain.model.RawTextResult
import com.pricelens.testing.singleLineFrame
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CameraImageTextProviderTest {
    @TempDir
    lateinit var cacheDir: File

    private val events = mutableListOf<String>()
    private val permission = FakePermissionChecker()
    private lateinit var camera: FakeCameraController
    private lateinit var ocr: FakeTextRecognizer

    @BeforeEach
    fun setUp() {
        camera = FakeCameraController(events)
        ocr = FakeTextRecognizer(events)
    }

    private val provider: CameraImageTextProvider
        get() = CameraImageTextProvider(cacheDir, camera, ocr, permission)

    @Test
    fun deniedPermissionDoesNotCaptureAndLeavesNoFiles() = runBlocking {
        permission.granted = false

        assertThat(provider.readText()).isEqualTo(RawTextResult.PermissionDenied)
        assertThat(events).isEmpty()
        assertThat(cacheDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun failedCaptureBecomesCameraErrorAndUnbinds() = runBlocking {
        camera.saved = false

        assertThat(provider.readText()).isEqualTo(RawTextResult.CameraError)
        assertThat(events).containsExactly("capture", "unbind").inOrder()
        assertThat(cacheDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun nullFrameBecomesEmptyTextAndDeletesPhoto() = runBlocking {
        ocr.frame = null

        assertThat(provider.readText()).isEqualTo(RawTextResult.EmptyText)
        assertThat(events).containsExactly("capture", "unbind", "recognize").inOrder()
        assertThat(ocr.sawExistingFile).isTrue()
        assertThat(cacheDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun frameWithoutTokensBecomesEmptyTextAndDeletesPhoto() = runBlocking {
        ocr.frame = singleLineFrame("350 г 240 ₽").copy(tokens = emptyList())

        assertThat(provider.readText()).isEqualTo(RawTextResult.EmptyText)
        assertThat(events).containsExactly("capture", "unbind", "recognize").inOrder()
        assertThat(cacheDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun recognizedFrameBecomesSuccessAndDeletesPhoto() = runBlocking {
        ocr.frame = singleLineFrame("350 г 240 ₽")

        assertThat(provider.readText()).isEqualTo(RawTextResult.Success(ocr.frame!!))
        assertThat(events).containsExactly("capture", "unbind", "recognize").inOrder()
        assertThat(ocr.sawExistingFile).isTrue()
        assertThat(ocr.lastPhoto?.name).matches("price-tag-.*\\.jpg")
        assertThat(cacheDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun unavailableOcrEngineBecomesErrorAndDeletesPhoto() = runBlocking {
        ocr.failure = OcrEngineUnavailableException("model missing")

        assertThat(provider.readText()).isEqualTo(RawTextResult.OcrEngineError)
        assertThat(events).containsExactly("capture", "unbind", "recognize").inOrder()
        assertThat(cacheDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun recognizerFailureStillUnbindsAndDeletesPhoto() {
        ocr.failure = IllegalStateException("ocr crashed")

        assertThrows<IllegalStateException> { runBlocking { provider.readText() } }

        assertThat(events).containsExactly("capture", "unbind", "recognize").inOrder()
        assertThat(cacheDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun cameraFailureStillUnbindsAndDeletesPhoto() {
        camera.failure = IllegalStateException("camera crashed")

        assertThrows<IllegalStateException> { runBlocking { provider.readText() } }

        assertThat(events).containsExactly("capture", "unbind").inOrder()
        assertThat(cacheDir.listFiles().orEmpty()).isEmpty()
    }

    private class FakePermissionChecker : CameraPermissionChecker {
        var granted: Boolean = true
        override fun isGranted(): Boolean = granted
    }

    private class FakeCameraController(
        private val events: MutableList<String>,
    ) : CameraController {
        var saved: Boolean = true
        var failure: Throwable? = null

        override suspend fun bind(
            lifecycleOwner: LifecycleOwner,
            previewView: PreviewView,
        ) = Unit

        override suspend fun captureToFile(file: File): Boolean {
            events.add("capture")
            file.writeBytes(byteArrayOf(1, 2, 3))
            failure?.let { throw it }
            return saved
        }

        override fun unbind() {
            events.add("unbind")
        }
    }

    private class FakeTextRecognizer(
        private val events: MutableList<String>,
    ) : TextRecognizer {
        var frame: OcrFrame? = null
        var failure: Throwable? = null
        var lastPhoto: File? = null
        var sawExistingFile: Boolean = false

        override fun recognize(photo: File): OcrFrame? {
            events.add("recognize")
            lastPhoto = photo
            sawExistingFile = photo.exists()
            failure?.let { throw it }
            return frame
        }
    }
}
