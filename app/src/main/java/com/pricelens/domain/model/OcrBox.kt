package com.pricelens.domain.model

/**
 * Прямоугольник токена в координатах кадра. right/bottom — исключительные границы,
 * как в android.graphics.Rect, но без зависимости от Android SDK.
 */
data class OcrBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}
