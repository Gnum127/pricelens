package com.pricelens.data.camera

fun interface CameraPermissionChecker {
    fun isGranted(): Boolean
}
