package com.pricelens

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pricelens.core.design.AppTheme
import com.pricelens.data.camera.CameraController
import com.pricelens.data.camera.CameraPermissionChecker
import com.pricelens.ui.compare.CompareMessage
import com.pricelens.ui.compare.CompareScreen
import com.pricelens.ui.compare.CompareViewModel
import com.pricelens.ui.compare.UiEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var cameraController: CameraController

    @Inject
    lateinit var permissionChecker: CameraPermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val viewModel: CompareViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.Message -> snackbarHostState.showSnackbar(
                                context.messageFor(event.message),
                            )
                        }
                    }
                }

                CompareScreen(
                    state = state,
                    cameraController = cameraController,
                    permissionChecker = permissionChecker,
                    snackbarHostState = snackbarHostState,
                    onAction = viewModel::onAction,
                )
            }
        }
    }
}

private fun Context.messageFor(message: CompareMessage): String = when (message) {
    CompareMessage.EmptyText -> getString(R.string.error_empty_text)
    is CompareMessage.Unparseable -> getString(R.string.error_unparseable)
    CompareMessage.CameraError -> getString(R.string.error_camera)
    CompareMessage.OcrEngineError -> getString(R.string.error_ocr_engine)
    CompareMessage.PermissionDenied -> getString(R.string.error_permission)
    CompareMessage.LimitReached -> getString(R.string.error_limit)
    CompareMessage.InvalidInput -> getString(R.string.error_invalid_input)
    CompareMessage.StorageError -> getString(R.string.error_storage)
}
