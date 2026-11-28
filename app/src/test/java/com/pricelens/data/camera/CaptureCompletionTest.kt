package com.pricelens.data.camera

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CaptureCompletionTest {
    @TempDir
    lateinit var cacheDir: File

    private fun photo(): File = File(cacheDir, "price-tag.jpg").also { it.writeBytes(byteArrayOf(1)) }

    @Test
    fun successfulCallbackResumesOnceAndKeepsFileForProvider() {
        val file = photo()
        val results = mutableListOf<Boolean>()
        val completion = CaptureCompletion(file, isActive = { true }, resume = results::add)

        completion.finish(true)
        completion.finish(false)

        assertThat(results).containsExactly(true)
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun failedCallbackResumesFalse() {
        val file = photo()
        val results = mutableListOf<Boolean>()
        val completion = CaptureCompletion(file, isActive = { true }, resume = results::add)

        completion.finish(false)

        assertThat(results).containsExactly(false)
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun cancellationBeforeCallbackDeletesFileAndDoesNotResume() {
        val file = photo()
        val results = mutableListOf<Boolean>()
        val completion = CaptureCompletion(file, isActive = { true }, resume = results::add)

        completion.cancel()
        completion.finish(true)
        completion.finish(false)

        assertThat(results).isEmpty()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun cancellationAfterCallbackDoesNotDeleteFileTwice() {
        val file = photo()
        val results = mutableListOf<Boolean>()
        val completion = CaptureCompletion(file, isActive = { true }, resume = results::add)

        completion.finish(true)
        completion.cancel()

        assertThat(results).containsExactly(true)
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun callbackAfterInactiveCoroutineDeletesFile() {
        val file = photo()
        val results = mutableListOf<Boolean>()
        val completion = CaptureCompletion(file, isActive = { false }, resume = results::add)

        completion.finish(true)

        assertThat(results).isEmpty()
        assertThat(file.exists()).isFalse()
    }
}
