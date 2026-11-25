package com.pricelens.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

class AndroidCameraPermissionChecker(private val context: Context) : CameraPermissionChecker {
    override fun isGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
