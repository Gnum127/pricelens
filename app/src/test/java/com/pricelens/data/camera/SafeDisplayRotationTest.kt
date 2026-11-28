package com.pricelens.data.camera

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SafeDisplayRotationTest {
    @Test
    fun returnsRotationFromAttachedView() {
        assertThat(safeDisplayRotation { 90 }).isEqualTo(90)
    }

    @Test
    fun returnsZeroWhenViewHasNoDisplay() {
        assertThat(safeDisplayRotation { null }).isEqualTo(0)
    }

    @Test
    fun returnsZeroWhenReadingDisplayThrows() {
        assertThat(safeDisplayRotation { error("display is not attached") }).isEqualTo(0)
    }
}
