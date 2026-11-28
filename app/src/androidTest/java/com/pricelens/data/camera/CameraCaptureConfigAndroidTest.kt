package com.pricelens.data.camera

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraCaptureConfigAndroidTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    @Test
    fun imageCaptureUsesMaximizeQualityAndActivityRotation() {
        activityRule.scenario.onActivity { activity ->
            val view = PreviewView(activity)
            activity.setContentView(view)

            val capture = createImageCapture(view)

            assertEquals(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY, capture.captureMode)
            assertEquals(expectedRotation(activity), capture.targetRotation)
        }
    }

    @Test
    fun detachedPreviewViewUsesZeroRotation() {
        activityRule.scenario.onActivity { activity ->
            val view = PreviewView(activity)

            val capture = createImageCapture(view)

            assertEquals(0, capture.targetRotation)
        }
    }

    @Test
    fun contextDisplayFailureDoesNotAffectConfig() {
        activityRule.scenario.onActivity { activity ->
            val view = PreviewView(ThrowingDisplayContext(activity))
            activity.setContentView(view)

            val capture = createImageCapture(view)

            assertEquals(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY, capture.captureMode)
            assertEquals(expectedRotation(activity), capture.targetRotation)
        }
    }

    @Suppress("DEPRECATION")
    private fun expectedRotation(activity: ComponentActivity): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation ?: 0
        } else {
            activity.windowManager.defaultDisplay.rotation
        }

    private class ThrowingDisplayContext(base: Context) : ContextWrapper(base) {
        override fun getDisplay(): Display = throw UnsupportedOperationException("context has no display")
    }
}
