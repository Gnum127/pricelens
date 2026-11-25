package com.pricelens.ui.capture

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import com.pricelens.R
import com.pricelens.data.camera.CameraController
import com.pricelens.data.camera.CameraPermissionChecker

@Composable
fun CapturePanel(
    cameraController: CameraController,
    permissionChecker: CameraPermissionChecker,
    isBusy: Boolean,
    onCapture: () -> Unit,
    onDismiss: () -> Unit,
    onPermissionDenied: () -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var permissionGranted by remember { mutableStateOf(permissionChecker.isGranted()) }
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) requestPermission.launch(Manifest.permission.CAMERA)
    }

    if (permissionGranted) {
        LaunchedEffect(previewView, lifecycleOwner) {
            previewView?.let { view ->
                runCatching { cameraController.bind(lifecycleOwner, view.surfaceProvider) }
                    .onFailure { failure ->
                        if (failure is CancellationException) throw failure
                        onCameraError()
                    }
            }
        }
    }

    DisposableEffect(cameraController) {
        onDispose { cameraController.unbind() }
    }

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.capture_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(MaterialTheme.shapes.medium),
        ) {
            AndroidView(
                factory = { context -> PreviewView(context).also { previewView = it } },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 220.dp, height = 110.dp)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                    ),
            )
        }
        Text(
            text = stringResource(R.string.capture_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.glare_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
            Button(
                onClick = onCapture,
                enabled = permissionGranted && !isBusy,
            ) {
                Text(
                    stringResource(
                        if (isBusy) R.string.processing else R.string.capture_button,
                    ),
                )
            }
        }
    }
}
