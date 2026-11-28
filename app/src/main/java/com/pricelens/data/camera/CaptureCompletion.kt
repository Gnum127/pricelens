package com.pricelens.data.camera

import java.io.File

/**
 * Обработка одного колбэка CameraX: возобновляет корутину не более одного раза
 * и удаляет временный файл, если захват был отменён до завершения записи.
 */
internal class CaptureCompletion(
    private val file: File,
    private val isActive: () -> Boolean,
    private val resume: (Boolean) -> Unit,
) {
    @Volatile private var cancelled = false
    @Volatile private var handled = false

    fun cancel() {
        cancelled = true
    }

    fun finish(saved: Boolean) {
        if (handled) return
        handled = true
        if (!cancelled && isActive()) {
            resume(saved)
        } else {
            file.delete()
        }
    }
}
