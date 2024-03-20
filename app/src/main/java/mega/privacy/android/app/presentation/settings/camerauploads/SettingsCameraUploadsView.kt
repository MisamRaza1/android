package mega.privacy.android.app.presentation.settings.camerauploads

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import de.palm.composestateevents.StateEvent
import mega.privacy.android.app.R
import mega.privacy.android.app.presentation.settings.camerauploads.business.BusinessAccountPromptHandler
import mega.privacy.android.app.presentation.settings.camerauploads.dialogs.FileUploadDialog
import mega.privacy.android.app.presentation.settings.camerauploads.dialogs.HowToUploadDialog
import mega.privacy.android.app.presentation.settings.camerauploads.dialogs.VideoQualityDialog
import mega.privacy.android.app.presentation.settings.camerauploads.model.SettingsCameraUploadsUiState
import mega.privacy.android.app.presentation.settings.camerauploads.model.UploadConnectionType
import mega.privacy.android.app.presentation.settings.camerauploads.model.UploadOptionUiItem
import mega.privacy.android.app.presentation.settings.camerauploads.model.VideoQualityUiItem
import mega.privacy.android.app.presentation.settings.camerauploads.permissions.CameraUploadsPermissionsHandler
import mega.privacy.android.app.presentation.settings.camerauploads.tiles.CameraUploadsTile
import mega.privacy.android.app.presentation.settings.camerauploads.tiles.FileUploadTile
import mega.privacy.android.app.presentation.settings.camerauploads.tiles.HowToUploadTile
import mega.privacy.android.app.presentation.settings.camerauploads.tiles.KeepFileNamesTile
import mega.privacy.android.app.presentation.settings.camerauploads.tiles.VideoQualityTile
import mega.privacy.android.core.ui.controls.appbar.AppBarType
import mega.privacy.android.core.ui.controls.appbar.MegaAppBar
import mega.privacy.android.core.ui.controls.layouts.MegaScaffold
import mega.privacy.android.core.ui.preview.CombinedThemePreviews
import mega.privacy.android.shared.theme.MegaAppTheme

/**
 * A Composable that holds views displaying the main Settings Camera Uploads screen
 *
 * @param uiState The Settings Camera Uploads UI State
 * @param onBusinessAccountPromptDismissed Lambda to execute when the User dismisses the Business
 * Account prompt
 * @param onCameraUploadsStateChanged Lambda to execute when the Camera Uploads state changes
 * @param onHowToUploadPromptOptionSelected Lambda to execute when the User selects a new
 * [UploadConnectionType] from the How to Upload prompt
 * @param onKeepFileNamesStateChanged Lambda to execute when the Keep File Names state changes
 * @param onMediaPermissionsGranted Lambda to execute when the User has granted the Media Permissions
 * @param onRegularBusinessAccountSubUserPromptAcknowledged Lambda to execute when the Business
 * Account Sub-User acknowledges that the Business Account Administrator can access the content
 * in Camera Uploads
 * @param onRequestPermissionsStateChanged Lambda to execute whether a Camera Uploads permissions
 * request should be done (triggered) or not (consumed)
 * @param onSettingsScreenPaused Lambda to execute when the User triggers onPause() in the Settings
 * screen
 * @param onUploadOptionUiItemSelected Lambda to execute when the User selects a new
 * [UploadOptionUiItem] from the File Upload prompt
 * @param onVideoQualityUiItemSelected Lambda to execute when the User selects a new
 * [VideoQualityUiItem] from the Video Quality prompt
 */
@Composable
internal fun SettingsCameraUploadsView(
    uiState: SettingsCameraUploadsUiState,
    onBusinessAccountPromptDismissed: () -> Unit,
    onCameraUploadsStateChanged: (Boolean) -> Unit,
    onHowToUploadPromptOptionSelected: (UploadConnectionType) -> Unit,
    onKeepFileNamesStateChanged: (Boolean) -> Unit,
    onMediaPermissionsGranted: () -> Unit,
    onRegularBusinessAccountSubUserPromptAcknowledged: () -> Unit,
    onRequestPermissionsStateChanged: (StateEvent) -> Unit,
    onSettingsScreenPaused: () -> Unit,
    onUploadOptionUiItemSelected: (UploadOptionUiItem) -> Unit,
    onVideoQualityUiItemSelected: (VideoQualityUiItem) -> Unit,
) {
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current

    var showFileUploadPrompt by rememberSaveable { mutableStateOf(false) }
    var showHowToUploadPrompt by rememberSaveable { mutableStateOf(false) }
    var showVideoQualityPrompt by rememberSaveable { mutableStateOf(false) }

    // When the User triggers the onPause Lifecycle Event, check if Camera Uploads can be started
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                onSettingsScreenPaused.invoke()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MegaScaffold(
        topBar = {
            MegaAppBar(
                modifier = Modifier.testTag(SETTINGS_CAMERA_UPLOADS_TOOLBAR),
                title = stringResource(R.string.section_photo_sync),
                appBarType = AppBarType.BACK_NAVIGATION,
                onNavigationPressed = { onBackPressedDispatcher?.onBackPressed() },
            )
        },
        content = { padding ->
            CameraUploadsPermissionsHandler(
                modifier = Modifier.padding(padding),
                requestPermissions = uiState.requestPermissions,
                onMediaPermissionsGranted = onMediaPermissionsGranted,
                onRequestPermissionsStateChanged = onRequestPermissionsStateChanged,
            )
            BusinessAccountPromptHandler(
                businessAccountPromptType = uiState.businessAccountPromptType,
                onRegularBusinessAccountSubUserPromptAcknowledged = onRegularBusinessAccountSubUserPromptAcknowledged,
                onBusinessAccountPromptDismissed = onBusinessAccountPromptDismissed,
            )
            if (showVideoQualityPrompt) {
                VideoQualityDialog(
                    currentVideoQualityUiItem = uiState.videoQualityUiItem,
                    onOptionSelected = { newVideoQualityUiItem ->
                        showVideoQualityPrompt = false
                        onVideoQualityUiItemSelected.invoke(newVideoQualityUiItem)
                    },
                    onDismissRequest = { showVideoQualityPrompt = false },
                )
            }
            if (showFileUploadPrompt) {
                FileUploadDialog(
                    currentUploadOptionUiItem = uiState.uploadOptionUiItem,
                    onOptionSelected = { newUploadOptionUiItem ->
                        showFileUploadPrompt = false
                        onUploadOptionUiItemSelected.invoke(newUploadOptionUiItem)
                    },
                    onDismissRequest = { showFileUploadPrompt = false },
                )
            }
            if (showHowToUploadPrompt) {
                HowToUploadDialog(
                    currentUploadConnectionType = uiState.uploadConnectionType,
                    onOptionSelected = { newUploadConnectionType ->
                        showHowToUploadPrompt = false
                        onHowToUploadPromptOptionSelected.invoke(newUploadConnectionType)
                    },
                    onDismissRequest = { showHowToUploadPrompt = false },
                )
            }
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                CameraUploadsTile(
                    isChecked = uiState.isCameraUploadsEnabled,
                    onCheckedChange = onCameraUploadsStateChanged,
                )
                if (uiState.isCameraUploadsEnabled) {
                    HowToUploadTile(
                        uploadConnectionType = uiState.uploadConnectionType,
                        onItemClicked = { showHowToUploadPrompt = true },
                    )
                    FileUploadTile(
                        uploadOptionUiItem = uiState.uploadOptionUiItem,
                        onItemClicked = { showFileUploadPrompt = true },
                    )
                    if (uiState.uploadOptionUiItem in listOf(
                            UploadOptionUiItem.VideosOnly,
                            UploadOptionUiItem.PhotosAndVideos,
                        )
                    ) {
                        VideoQualityTile(
                            videoQualityUiItem = uiState.videoQualityUiItem,
                            onItemClicked = { showVideoQualityPrompt = true },
                        )
                    }
                    KeepFileNamesTile(
                        isChecked = uiState.shouldKeepUploadFileNames,
                        onCheckedChange = onKeepFileNamesStateChanged,
                    )
                }
            }
        },
    )
}

/**
 * A Composable Preview for [SettingsCameraUploadsView]
 *
 * @param uiState The [SettingsCameraUploadsUiState]
 */
@CombinedThemePreviews
@Composable
private fun SettingsCameraUploadsViewPreview(
    @PreviewParameter(SettingsCameraUploadsViewParameterProvider::class) uiState: SettingsCameraUploadsUiState,
) {
    MegaAppTheme(isDark = isSystemInDarkTheme()) {
        SettingsCameraUploadsView(
            uiState = uiState,
            onRegularBusinessAccountSubUserPromptAcknowledged = {},
            onBusinessAccountPromptDismissed = {},
            onCameraUploadsStateChanged = {},
            onHowToUploadPromptOptionSelected = {},
            onKeepFileNamesStateChanged = {},
            onMediaPermissionsGranted = {},
            onRequestPermissionsStateChanged = {},
            onSettingsScreenPaused = {},
            onUploadOptionUiItemSelected = {},
            onVideoQualityUiItemSelected = {},
        )
    }
}

private class SettingsCameraUploadsViewParameterProvider
    : PreviewParameterProvider<SettingsCameraUploadsUiState> {
    override val values: Sequence<SettingsCameraUploadsUiState>
        get() = sequenceOf(
            // Initial Configuration - Camera Uploads Disabled
            SettingsCameraUploadsUiState(),
            // Camera Uploads Enabled - Default Configuration
            SettingsCameraUploadsUiState(isCameraUploadsEnabled = true),
            // Camera Uploads Enabled - Video Quality Tile Shown
            SettingsCameraUploadsUiState(
                isCameraUploadsEnabled = true,
                uploadOptionUiItem = UploadOptionUiItem.VideosOnly,
            ),
        )

}

/**
 * Test Tags for Settings Camera Uploads View
 */
internal const val SETTINGS_CAMERA_UPLOADS_TOOLBAR = "settings_camera_uploads_view:mega_app_bar"