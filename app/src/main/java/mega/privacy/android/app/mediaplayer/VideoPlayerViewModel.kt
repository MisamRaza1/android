package mega.privacy.android.app.mediaplayer

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mega.privacy.android.app.MegaOffline
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.R
import mega.privacy.android.app.di.mediaplayer.VideoPlayer
import mega.privacy.android.app.mediaplayer.gateway.MediaPlayerGateway
import mega.privacy.android.app.mediaplayer.mapper.PlaylistItemMapper
import mega.privacy.android.app.mediaplayer.model.MediaPlaySources
import mega.privacy.android.app.mediaplayer.model.PlaybackPositionState
import mega.privacy.android.app.mediaplayer.model.SubtitleDisplayState
import mega.privacy.android.app.mediaplayer.playlist.PlaylistAdapter.Companion.TYPE_NEXT
import mega.privacy.android.app.mediaplayer.playlist.PlaylistAdapter.Companion.TYPE_PLAYING
import mega.privacy.android.app.mediaplayer.playlist.PlaylistAdapter.Companion.TYPE_PREVIOUS
import mega.privacy.android.app.mediaplayer.playlist.PlaylistItem
import mega.privacy.android.app.mediaplayer.playlist.finalizeItem
import mega.privacy.android.app.mediaplayer.playlist.updateNodeName
import mega.privacy.android.app.mediaplayer.service.Metadata
import mega.privacy.android.app.presentation.extensions.getStateFlow
import mega.privacy.android.app.presentation.extensions.parcelableArrayList
import mega.privacy.android.app.search.callback.SearchCallback
import mega.privacy.android.app.usecase.GetGlobalTransferUseCase
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.Constants.CONTACT_FILE_ADAPTER
import mega.privacy.android.app.utils.Constants.FILE_BROWSER_ADAPTER
import mega.privacy.android.app.utils.Constants.FILE_LINK_ADAPTER
import mega.privacy.android.app.utils.Constants.FOLDER_LINK_ADAPTER
import mega.privacy.android.app.utils.Constants.FROM_ALBUM_SHARING
import mega.privacy.android.app.utils.Constants.FROM_CHAT
import mega.privacy.android.app.utils.Constants.FROM_IMAGE_VIEWER
import mega.privacy.android.app.utils.Constants.FROM_MEDIA_DISCOVERY
import mega.privacy.android.app.utils.Constants.INBOX_ADAPTER
import mega.privacy.android.app.utils.Constants.INCOMING_SHARES_ADAPTER
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_ADAPTER_TYPE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_ARRAY_OFFLINE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_FILE_NAME
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_FROM_DOWNLOAD_SERVICE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_HANDLE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_HANDLES_NODES_SEARCH
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_IS_PLAYLIST
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_NEED_STOP_HTTP_SERVER
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_ORDER_GET_CHILDREN
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_PARENT_NODE_HANDLE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_REBUILD_PLAYLIST
import mega.privacy.android.app.utils.Constants.INVALID_SIZE
import mega.privacy.android.app.utils.Constants.INVALID_VALUE
import mega.privacy.android.app.utils.Constants.LINKS_ADAPTER
import mega.privacy.android.app.utils.Constants.NODE_HANDLES
import mega.privacy.android.app.utils.Constants.OFFLINE_ADAPTER
import mega.privacy.android.app.utils.Constants.OUTGOING_SHARES_ADAPTER
import mega.privacy.android.app.utils.Constants.PHOTO_SYNC_ADAPTER
import mega.privacy.android.app.utils.Constants.RECENTS_ADAPTER
import mega.privacy.android.app.utils.Constants.RECENTS_BUCKET_ADAPTER
import mega.privacy.android.app.utils.Constants.RUBBISH_BIN_ADAPTER
import mega.privacy.android.app.utils.Constants.SEARCH_BY_ADAPTER
import mega.privacy.android.app.utils.Constants.VIDEO_BROWSE_ADAPTER
import mega.privacy.android.app.utils.Constants.ZIP_ADAPTER
import mega.privacy.android.app.utils.FileUtil
import mega.privacy.android.app.utils.MegaNodeUtil
import mega.privacy.android.app.utils.OfflineUtils
import mega.privacy.android.app.utils.OfflineUtils.getOfflineFile
import mega.privacy.android.app.utils.TextUtil
import mega.privacy.android.app.utils.ThumbnailUtils
import mega.privacy.android.app.utils.wrapper.GetOfflineThumbnailFileWrapper
import mega.privacy.android.data.mapper.FileDurationMapper
import mega.privacy.android.domain.entity.SortOrder
import mega.privacy.android.domain.entity.mediaplayer.PlaybackInformation
import mega.privacy.android.domain.entity.mediaplayer.RepeatToggleMode
import mega.privacy.android.domain.entity.mediaplayer.SubtitleFileInfo
import mega.privacy.android.domain.entity.node.TypedFileNode
import mega.privacy.android.domain.entity.node.TypedNode
import mega.privacy.android.domain.entity.statistics.MediaPlayerStatisticsEvents
import mega.privacy.android.domain.qualifier.IoDispatcher
import mega.privacy.android.domain.usecase.AreCredentialsNullUseCase
import mega.privacy.android.domain.usecase.GetInboxNodeUseCase
import mega.privacy.android.domain.usecase.GetLocalFilePathUseCase
import mega.privacy.android.domain.usecase.GetLocalFolderLinkFromMegaApiFolderUseCase
import mega.privacy.android.domain.usecase.GetLocalFolderLinkFromMegaApiUseCase
import mega.privacy.android.domain.usecase.GetLocalLinkFromMegaApiUseCase
import mega.privacy.android.domain.usecase.GetNodesByHandlesUseCase
import mega.privacy.android.domain.usecase.GetParentNodeFromMegaApiFolderUseCase
import mega.privacy.android.domain.usecase.GetRootNodeFromMegaApiFolderUseCase
import mega.privacy.android.domain.usecase.GetRootNodeUseCase
import mega.privacy.android.domain.usecase.GetRubbishNodeUseCase
import mega.privacy.android.domain.usecase.GetUserNameByEmailUseCase
import mega.privacy.android.domain.usecase.GetVideoNodesByEmailUseCase
import mega.privacy.android.domain.usecase.GetVideoNodesByParentHandleUseCase
import mega.privacy.android.domain.usecase.GetVideoNodesFromInSharesUseCase
import mega.privacy.android.domain.usecase.GetVideoNodesFromOutSharesUseCase
import mega.privacy.android.domain.usecase.GetVideoNodesFromPublicLinksUseCase
import mega.privacy.android.domain.usecase.GetVideoNodesUseCase
import mega.privacy.android.domain.usecase.GetVideosByParentHandleFromMegaApiFolderUseCase
import mega.privacy.android.domain.usecase.MonitorPlaybackTimesUseCase
import mega.privacy.android.domain.usecase.camerauploads.GetFingerprintUseCase
import mega.privacy.android.domain.usecase.mediaplayer.DeletePlaybackInformationUseCase
import mega.privacy.android.domain.usecase.mediaplayer.GetSRTSubtitleFileListUseCase
import mega.privacy.android.domain.usecase.mediaplayer.MegaApiFolderHttpServerIsRunningUseCase
import mega.privacy.android.domain.usecase.mediaplayer.MegaApiFolderHttpServerSetMaxBufferSizeUseCase
import mega.privacy.android.domain.usecase.mediaplayer.MegaApiFolderHttpServerStartUseCase
import mega.privacy.android.domain.usecase.mediaplayer.MegaApiFolderHttpServerStopUseCase
import mega.privacy.android.domain.usecase.mediaplayer.MegaApiHttpServerIsRunningUseCase
import mega.privacy.android.domain.usecase.mediaplayer.MegaApiHttpServerSetMaxBufferSizeUseCase
import mega.privacy.android.domain.usecase.mediaplayer.MegaApiHttpServerStartUseCase
import mega.privacy.android.domain.usecase.mediaplayer.MegaApiHttpServerStopUseCase
import mega.privacy.android.domain.usecase.mediaplayer.MonitorVideoRepeatModeUseCase
import mega.privacy.android.domain.usecase.mediaplayer.SavePlaybackTimesUseCase
import mega.privacy.android.domain.usecase.mediaplayer.SendStatisticsMediaPlayerUseCase
import mega.privacy.android.domain.usecase.mediaplayer.SetVideoRepeatModeUseCase
import mega.privacy.android.domain.usecase.mediaplayer.TrackPlaybackPositionUseCase
import mega.privacy.android.domain.usecase.node.GetNodeByHandleUseCase
import nz.mega.sdk.MegaApiJava.INVALID_HANDLE
import nz.mega.sdk.MegaCancelToken
import nz.mega.sdk.MegaError
import nz.mega.sdk.MegaTransfer
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for video player.
 *
 * @property ioDispatcher CoroutineDispatcher
 * @property sendStatisticsMediaPlayerUseCase SendStatisticsMediaPlayerUseCase
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @VideoPlayer private val mediaPlayerGateway: MediaPlayerGateway,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val sendStatisticsMediaPlayerUseCase: SendStatisticsMediaPlayerUseCase,
    private val offlineThumbnailFileWrapper: GetOfflineThumbnailFileWrapper,
    private val getGlobalTransferUseCase: GetGlobalTransferUseCase,
    private val playlistItemMapper: PlaylistItemMapper,
    private val trackPlaybackPositionUseCase: TrackPlaybackPositionUseCase,
    private val monitorPlaybackTimesUseCase: MonitorPlaybackTimesUseCase,
    private val savePlaybackTimesUseCase: SavePlaybackTimesUseCase,
    private val deletePlaybackInformationUseCase: DeletePlaybackInformationUseCase,
    private val megaApiFolderHttpServerSetMaxBufferSizeUseCase: MegaApiFolderHttpServerSetMaxBufferSizeUseCase,
    private val megaApiFolderHttpServerIsRunningUseCase: MegaApiFolderHttpServerIsRunningUseCase,
    private val megaApiFolderHttpServerStartUseCase: MegaApiFolderHttpServerStartUseCase,
    private val megaApiFolderHttpServerStopUseCase: MegaApiFolderHttpServerStopUseCase,
    private val megaApiHttpServerSetMaxBufferSizeUseCase: MegaApiHttpServerSetMaxBufferSizeUseCase,
    private val megaApiHttpServerIsRunningUseCase: MegaApiHttpServerIsRunningUseCase,
    private val megaApiHttpServerStartUseCase: MegaApiHttpServerStartUseCase,
    private val megaApiHttpServerStop: MegaApiHttpServerStopUseCase,
    private val areCredentialsNullUseCase: AreCredentialsNullUseCase,
    private val getLocalFilePathUseCase: GetLocalFilePathUseCase,
    private val getLocalFolderLinkFromMegaApiFolderUseCase: GetLocalFolderLinkFromMegaApiFolderUseCase,
    private val getLocalFolderLinkFromMegaApiUseCase: GetLocalFolderLinkFromMegaApiUseCase,
    private val getLocalLinkFromMegaApiUseCase: GetLocalLinkFromMegaApiUseCase,
    private val getInboxNodeUseCase: GetInboxNodeUseCase,
    private val getParentNodeFromMegaApiFolderUseCase: GetParentNodeFromMegaApiFolderUseCase,
    private val getRootNodeUseCase: GetRootNodeUseCase,
    private val getRootNodeFromMegaApiFolderUseCase: GetRootNodeFromMegaApiFolderUseCase,
    private val getRubbishNodeUseCase: GetRubbishNodeUseCase,
    private val getNodeByHandleUseCase: GetNodeByHandleUseCase,
    private val getVideoNodesFromPublicLinksUseCase: GetVideoNodesFromPublicLinksUseCase,
    private val getVideoNodesFromInSharesUseCase: GetVideoNodesFromInSharesUseCase,
    private val getVideoNodesFromOutSharesUseCase: GetVideoNodesFromOutSharesUseCase,
    private val getVideoNodesUseCase: GetVideoNodesUseCase,
    private val getVideoNodesByEmailUseCase: GetVideoNodesByEmailUseCase,
    private val getUserNameByEmailUseCase: GetUserNameByEmailUseCase,
    private val getVideosByParentHandleFromMegaApiFolderUseCase: GetVideosByParentHandleFromMegaApiFolderUseCase,
    private val getVideoNodesByParentHandleUseCase: GetVideoNodesByParentHandleUseCase,
    private val getNodesByHandlesUseCase: GetNodesByHandlesUseCase,
    private val getFingerprintUseCase: GetFingerprintUseCase,
    private val fileDurationMapper: FileDurationMapper,
    private val getSRTSubtitleFileListUseCase: GetSRTSubtitleFileListUseCase,
    private val setVideoRepeatModeUseCase: SetVideoRepeatModeUseCase,
    monitorVideoRepeatModeUseCase: MonitorVideoRepeatModeUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), SearchCallback.Data {

    private val compositeDisposable = CompositeDisposable()

    private var currentIntent: Intent? = null

    private var currentMediaPlayerMediaId: String? = null

    /**
     * The subtitle file info by add subtitles
     */
    internal var subtitleInfoByAddSubtitles: SubtitleFileInfo? = null
        private set

    private val _addSubtitleState = MutableStateFlow(false)

    private val _subtitleDisplayState = MutableStateFlow(SubtitleDisplayState())
    internal val subtitleDisplayState: StateFlow<SubtitleDisplayState> = _subtitleDisplayState

    /**
     * SelectState for updating the background color of add subtitle dialog options
     */
    internal var selectOptionState by mutableStateOf(SUBTITLE_SELECTED_STATE_OFF)
        private set

    private val _screenLockState = MutableStateFlow(false)
    internal val screenLockState: StateFlow<Boolean> = _screenLockState

    private val _playerSourcesState =
        MutableStateFlow(MediaPlaySources(emptyList(), INVALID_VALUE, null))
    internal val playerSourcesState: StateFlow<MediaPlaySources> = _playerSourcesState

    private val _mediaItemToRemoveState = MutableStateFlow<Int?>(null)
    internal val mediaItemToRemoveState: StateFlow<Int?> = _mediaItemToRemoveState

    private val _playlistItemsState =
        MutableStateFlow<Pair<List<PlaylistItem>, Int>>(Pair(emptyList(), 0))
    internal val playlistItemsState: StateFlow<Pair<List<PlaylistItem>, Int>> = _playlistItemsState

    private val _playlistTitleState = MutableStateFlow<String?>(null)
    internal val playlistTitleState: StateFlow<String?> = _playlistTitleState

    private val _retryState = MutableStateFlow<Boolean?>(null)
    internal val retryState: StateFlow<Boolean?> = _retryState

    private val _errorState = MutableStateFlow<Int?>(null)
    internal val errorState: StateFlow<Int?> = _errorState

    private val _actionModeState = MutableStateFlow(false)
    internal val actionModeState: StateFlow<Boolean> = _actionModeState

    private val _itemsSelectedCountState = MutableStateFlow(0)
    internal val itemsSelectedCountState: StateFlow<Int> = _itemsSelectedCountState

    private val _mediaPlaybackState = MutableStateFlow(false)
    internal val mediaPlaybackState: StateFlow<Boolean> = _mediaPlaybackState

    private val _screenOrientationState = MutableStateFlow(ORIENTATION_PORTRAIT)
    internal val screenOrientationState: StateFlow<Int> = _screenOrientationState

    private val _showPlaybackPositionDialogState = MutableStateFlow(PlaybackPositionState())
    internal val showPlaybackPositionDialogState: StateFlow<PlaybackPositionState> =
        _showPlaybackPositionDialogState

    internal var videoPlayType = VIDEO_TYPE_SHOW_PLAYBACK_POSITION_DIALOG
        private set

    private val _metadataState = MutableStateFlow(Metadata(null, null, null, ""))
    internal val metadataState: StateFlow<Metadata> = _metadataState

    private val playlistItems = mutableListOf<PlaylistItem>()
    private val itemsSelectedMap = mutableMapOf<Long, PlaylistItem>()
    private var playlistSearchQuery: String? = null
    private var playingHandle = INVALID_HANDLE
    private var playerRetry = 0
    private var needStopStreamingServer = false
    private var playSourceChanged: MutableList<MediaItem> = mutableListOf()
    private var playlistItemsChanged: MutableList<PlaylistItem> = mutableListOf()
    private var playingPosition = 0

    private var cancelToken: MegaCancelToken? = null

    internal val subtitleDialogShowKey = "SUBTITLE_DIALOG_SHOW"
    internal val subtitleShowKey = "SUBTITLE_SHOW"
    internal val videoPlayerPausedForPlaylistKey = "VIDEO_PLAYER_PAUSED_FOR_PLAYLIST"
    internal val currentSubtitleFileInfoKey = "CURRENT_SUBTITLE_FILE_INFO"

    private val _isSubtitleDialogShown = savedStateHandle.getStateFlow(
        viewModelScope,
        subtitleDialogShowKey,
        false
    )

    private val _isSubtitleShown = savedStateHandle.getStateFlow(
        viewModelScope,
        subtitleShowKey,
        false
    )

    private val _currentSubtitleFileInfo: MutableStateFlow<SubtitleFileInfo?> =
        savedStateHandle.getStateFlow(
            viewModelScope,
            currentSubtitleFileInfoKey,
            null
        )

    private val _videoRepeatToggleMode = monitorVideoRepeatModeUseCase().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        RepeatToggleMode.REPEAT_NONE
    )

    init {
        viewModelScope.launch {
            combine(
                _isSubtitleShown,
                _isSubtitleDialogShown,
                _addSubtitleState,
                _currentSubtitleFileInfo,
                ::mapToSubtitleDisplayState
            ).collectLatest { newState ->
                _subtitleDisplayState.update {
                    newState
                }
            }
        }
        setupTransferListener()
    }

    /**
     * Get video repeat mode
     *
     * @return repeat mode
     */
    internal fun getVideoRepeatMode() = _videoRepeatToggleMode.value

    internal fun updateScreenOrientationState(orientation: Int) =
        _screenOrientationState.update { orientation }

    internal fun updateShowPlaybackPositionDialogState(newState: PlaybackPositionState) =
        _showPlaybackPositionDialogState.update { newState }

    internal fun updateMetadataState(newState: Metadata) = _metadataState.update { newState }

    internal fun setVideoPlayType(type: Int) {
        videoPlayType = type
    }

    /**
     * Update the lock status
     *
     * @param isLock true is screen is locked, otherwise is screen is not locked
     */
    internal fun updateLockStatus(isLock: Boolean) = _screenLockState.update { isLock }


    /**
     * Update media playback state
     * @param newState the media playback state, true is paused, otherwise is false
     */
    internal fun updateMediaPlaybackState(newState: Boolean) {
        _mediaPlaybackState.update { newState }
    }

    private fun mapToSubtitleDisplayState(
        subtitleShown: Boolean,
        subtitleDialogShown: Boolean,
        isAddSubtitle: Boolean,
        subtitleFileInfo: SubtitleFileInfo?,
    ) = SubtitleDisplayState(
        isSubtitleShown = subtitleShown,
        isSubtitleDialogShown = subtitleDialogShown,
        isAddSubtitle = isAddSubtitle,
        subtitleFileInfo = subtitleFileInfo,
    )

    /**
     * Update the subtitle file info by added subtitles
     *
     * @param subtitleFileInfo [SubtitleFileInfo]
     */
    private fun updateSubtitleInfoByAddSubtitles(subtitleFileInfo: SubtitleFileInfo?) {
        subtitleInfoByAddSubtitles = subtitleFileInfo
        subtitleFileInfo?.let { info ->
            if (_currentSubtitleFileInfo.value?.id != info.id) {
                _currentSubtitleFileInfo.update { info }
                _addSubtitleState.update { true }
            } else {
                _addSubtitleState.update { false }
            }
        }
    }

    /**
     * Update the current media player media id
     *
     * @param mediaId media item id
     */
    internal fun updateCurrentMediaId(mediaId: String?) {
        if (currentMediaPlayerMediaId != mediaId) {
            if (currentMediaPlayerMediaId != null) {
                _isSubtitleShown.update { false }
                subtitleInfoByAddSubtitles = null
            }
            currentMediaPlayerMediaId = mediaId
        }
    }

    /**
     * Update isAddSubtitle state
     */
    internal fun updateAddSubtitleState() =
        _addSubtitleState.update {
            subtitleDisplayState.value.isAddSubtitle && subtitleDisplayState.value.subtitleFileInfo == null
        }

    /**
     * Update current subtitle file info
     *
     * @param subtitleFileInfo [SubtitleFileInfo]
     */
    private fun updateCurrentSubtitleFileInfo(subtitleFileInfo: SubtitleFileInfo) {
        if (subtitleFileInfo.id != _currentSubtitleFileInfo.value?.id) {
            _currentSubtitleFileInfo.update { subtitleFileInfo }
            currentMediaPlayerMediaId = subtitleFileInfo.id.toString()
            _addSubtitleState.update { true }
        } else {
            _addSubtitleState.update { false }
        }
    }

    /**
     * The function is for showing the add subtitle dialog
     */
    internal fun showAddSubtitleDialog() {
        _isSubtitleShown.update { true }
        _addSubtitleState.update { false }
        _isSubtitleDialogShown.update { true }
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.SubtitleDialogShownEvent())
    }

    /**
     * The function is for the added subtitle option is clicked
     */
    internal fun onAddedSubtitleOptionClicked() {
        _isSubtitleShown.update { true }
        _isSubtitleDialogShown.update { false }
        selectOptionState = SUBTITLE_SELECTED_STATE_ADD_SUBTITLE_ITEM
    }

    /**
     * The function is for the adding subtitle file
     *
     * @param info the added subtitle file info
     */
    internal fun onAddSubtitleFile(info: SubtitleFileInfo?) {
        info?.let {
            it.url?.let {
                updateSubtitleInfoByAddSubtitles(info)
                selectOptionState = SUBTITLE_SELECTED_STATE_ADD_SUBTITLE_ITEM
            } ?: Timber.d("The subtitle file url is null")
            _isSubtitleShown.update { true }
        } ?: let {
            _addSubtitleState.update { false }
            _isSubtitleShown.update {
                selectOptionState != SUBTITLE_SELECTED_STATE_OFF
            }
        }
        _isSubtitleDialogShown.update { false }
    }

    /**
     * The function is for the off item is clicked
     */
    internal fun onOffItemClicked() {
        // Only when the subtitle file has been loaded and shown, send hide subtitle event if off item is clicked
        if (_currentSubtitleFileInfo.value != null && selectOptionState != SUBTITLE_SELECTED_STATE_OFF) {
            sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.HideSubtitleEvent())
        }
        _addSubtitleState.update { false }
        _isSubtitleShown.update { false }
        _isSubtitleDialogShown.update { false }
        selectOptionState = SUBTITLE_SELECTED_STATE_OFF
    }

    /**
     * The function is for the dialog dismiss request
     */
    internal fun onDismissRequest() {
        _addSubtitleState.update { false }
        _isSubtitleShown.update {
            selectOptionState != SUBTITLE_SELECTED_STATE_OFF
        }
        _isSubtitleDialogShown.update { false }
    }

    /**
     * The function is for the auto matched item is clicked
     *
     * @param info matched subtitle file info
     */
    internal fun onAutoMatchItemClicked(info: SubtitleFileInfo) {
        info.url?.let {
            updateCurrentSubtitleFileInfo(info)
            _isSubtitleShown.update { true }
            updateSubtitleInfoByAddSubtitles(null)
            sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.AutoMatchSubtitleClickedEvent())
            selectOptionState = SUBTITLE_SELECTED_STATE_MATCHED_ITEM
        } ?: Timber.d("The subtitle file url is null")
        _isSubtitleDialogShown.update { false }
    }

    /**
     * Capture the screenshot when video playing
     *
     * @param captureAreaView the view that capture area
     * @param rootFolderPath the root folder path of screenshots
     * @param captureView the view that will be captured
     * @param successCallback the callback after the screenshot is saved successfully
     *
     */
    @SuppressLint("SimpleDateFormat")
    internal fun screenshotWhenVideoPlaying(
        captureAreaView: View,
        rootFolderPath: String,
        captureView: View,
        successCallback: (bitmap: Bitmap) -> Unit,
    ) {
        File(rootFolderPath).apply {
            if (exists().not()) {
                mkdirs()
            }
        }
        val screenshotFileName =
            SimpleDateFormat(DATE_FORMAT_PATTERN).format(Date(System.currentTimeMillis()))
        val filePath =
            "$rootFolderPath${SCREENSHOT_NAME_PREFIX}$screenshotFileName${SCREENSHOT_NAME_SUFFIX}"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val screenshotBitmap = Bitmap.createBitmap(
                    captureView.width,
                    captureView.height,
                    Bitmap.Config.ARGB_8888
                )
                PixelCopy.request(
                    captureView as SurfaceView,
                    Rect(0, 0, captureAreaView.width, captureAreaView.height),
                    screenshotBitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            viewModelScope.launch {
                                saveBitmap(filePath, screenshotBitmap, successCallback)
                            }
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            }
        } catch (e: Exception) {
            Timber.e("Capture screenshot error: ${e.message}")
        }
    }

    /**
     * Build player source from start intent.
     *
     * @param intent intent received from onStartCommand
     * @return if there is no error
     */
    private suspend fun buildPlayerSource(intent: Intent?): Boolean {
        if (intent == null || !intent.getBooleanExtra(INTENT_EXTRA_KEY_REBUILD_PLAYLIST, true)) {
            _retryState.update { false }
            return false
        }

        val type = intent.getIntExtra(INTENT_EXTRA_KEY_ADAPTER_TYPE, INVALID_VALUE)
        val uri = intent.data

        if (type == INVALID_VALUE || uri == null) {
            _retryState.update { false }
            return false
        }

        val samePlaylist = isSamePlaylist(type, intent)
        currentIntent = intent

        val firstPlayHandle = intent.getLongExtra(INTENT_EXTRA_KEY_HANDLE, INVALID_HANDLE)
        if (firstPlayHandle == INVALID_HANDLE) {
            _retryState.update { false }
            return false
        }

        val firstPlayNodeName = intent.getStringExtra(INTENT_EXTRA_KEY_FILE_NAME)
        if (firstPlayNodeName == null) {
            _retryState.update { false }
            return false
        }

        // Because the same instance will be used if user creates another audio playlist,
        // so if we need stop streaming server in previous creation, we still need
        // stop it even if the new creation indicates we don't need to stop it,
        // otherwise the streaming server won't be stopped at the end.
        needStopStreamingServer = needStopStreamingServer || intent.getBooleanExtra(
            INTENT_EXTRA_KEY_NEED_STOP_HTTP_SERVER, false
        )

        playerRetry = 0

        // if we are already playing this music, then the metadata is already
        // in LiveData (_metadata of AudioPlayerService), we don't need (and shouldn't)
        // emit node name.
        val displayNodeNameFirst = !(samePlaylist && firstPlayHandle == playingHandle)


        val firstPlayUri = if (type == FOLDER_LINK_ADAPTER) {
            if (isMegaApiFolder(type)) {
                getLocalFolderLinkFromMegaApiFolderUseCase(firstPlayHandle)
            } else {
                getLocalFolderLinkFromMegaApiUseCase(firstPlayHandle)
            }?.let { url ->
                Uri.parse(url)
            }
        } else {
            uri
        }

        if (firstPlayUri == null) {
            _retryState.update { false }
            return false
        }

        val mediaItem = MediaItem.Builder()
            .setUri(firstPlayUri)
            .setMediaId(firstPlayHandle.toString())
            .build()
        _playerSourcesState.update {
            MediaPlaySources(
                listOf(mediaItem),
                // we will emit a single item list at first, and the current playing item
                // will always be at index 0 in that single item list.
                if (samePlaylist && firstPlayHandle == playingHandle) 0 else INVALID_VALUE,
                if (displayNodeNameFirst) firstPlayNodeName else null
            )
        }

        if (intent.getBooleanExtra(INTENT_EXTRA_KEY_IS_PLAYLIST, true)) {
            if (type != OFFLINE_ADAPTER && type != ZIP_ADAPTER) {
                needStopStreamingServer =
                    needStopStreamingServer || setupStreamingServer(type)
            }
            viewModelScope.launch(ioDispatcher) {
                when (type) {
                    OFFLINE_ADAPTER -> {
                        _playlistTitleState.update {
                            OfflineUtils.getOfflineFolderName(
                                context,
                                firstPlayHandle
                            )
                        }
                        buildPlaylistFromOfflineNodes(intent, firstPlayHandle)
                    }

                    VIDEO_BROWSE_ADAPTER -> {
                        _playlistTitleState.update {
                            context.getString(R.string.sortby_type_video_first)
                        }
                        buildPlaySourcesByTypedNodes(
                            type = type,
                            typedNodes = getVideoNodesUseCase(getSortOrderFromIntent(intent)),
                            firstPlayHandle = firstPlayHandle
                        )
                    }

                    FILE_BROWSER_ADAPTER,
                    RUBBISH_BIN_ADAPTER,
                    INBOX_ADAPTER,
                    LINKS_ADAPTER,
                    INCOMING_SHARES_ADAPTER,
                    OUTGOING_SHARES_ADAPTER,
                    CONTACT_FILE_ADAPTER,
                    FROM_MEDIA_DISCOVERY,
                    FROM_IMAGE_VIEWER,
                    FROM_ALBUM_SHARING,
                    -> {
                        val parentHandle =
                            intent.getLongExtra(INTENT_EXTRA_KEY_PARENT_NODE_HANDLE, INVALID_HANDLE)
                        val order = getSortOrderFromIntent(intent)

                        if (MegaNodeUtil.isInRootLinksLevel(type, parentHandle)) {
                            _playlistTitleState.update {
                                context.getString(R.string.tab_links_shares)
                            }
                            buildPlaySourcesByTypedNodes(
                                type = type,
                                typedNodes = getVideoNodesFromPublicLinksUseCase(order),
                                firstPlayHandle = firstPlayHandle
                            )
                            return@launch
                        }

                        if (type == INCOMING_SHARES_ADAPTER && parentHandle == INVALID_HANDLE) {
                            _playlistTitleState.update {
                                context.getString(R.string.tab_incoming_shares)
                            }
                            buildPlaySourcesByTypedNodes(
                                type = type,
                                typedNodes = getVideoNodesFromInSharesUseCase(order),
                                firstPlayHandle = firstPlayHandle
                            )
                            return@launch
                        }

                        if (type == OUTGOING_SHARES_ADAPTER && parentHandle == INVALID_HANDLE) {
                            _playlistTitleState.update {
                                context.getString(R.string.tab_outgoing_shares)
                            }
                            buildPlaySourcesByTypedNodes(
                                type = type,
                                typedNodes = getVideoNodesFromOutSharesUseCase(
                                    lastHandle = INVALID_HANDLE,
                                    order = order
                                ),
                                firstPlayHandle = firstPlayHandle
                            )
                            return@launch
                        }

                        if (type == CONTACT_FILE_ADAPTER && parentHandle == INVALID_HANDLE) {
                            intent.getStringExtra(Constants.INTENT_EXTRA_KEY_CONTACT_EMAIL)
                                ?.let { email ->
                                    getVideoNodesByEmailUseCase(email)?.let { nodes ->
                                        getUserNameByEmailUseCase(email)?.let {
                                            context.getString(R.string.title_incoming_shares_with_explorer)
                                                .let { sharesTitle ->
                                                    _playlistTitleState.update { "$sharesTitle $it" }
                                                }
                                        }
                                        buildPlaySourcesByTypedNodes(
                                            type = type,
                                            typedNodes = nodes,
                                            firstPlayHandle = firstPlayHandle
                                        )
                                    }
                                }
                            return@launch
                        }

                        if (parentHandle == INVALID_HANDLE) {
                            when (type) {
                                RUBBISH_BIN_ADAPTER -> getRubbishNodeUseCase()
                                INBOX_ADAPTER -> getInboxNodeUseCase()
                                else -> getRootNodeUseCase()
                            }
                        } else {
                            getNodeByHandleUseCase(parentHandle)
                        }?.let { parent ->
                            if (parentHandle == INVALID_HANDLE) {
                                context.getString(
                                    when (type) {
                                        RUBBISH_BIN_ADAPTER -> R.string.section_rubbish_bin
                                        INBOX_ADAPTER -> R.string.home_side_menu_backups_title
                                        else -> R.string.section_cloud_drive
                                    }
                                )
                            } else {
                                parent.name
                            }.let { title ->
                                _playlistTitleState.update { title }
                            }

                            getVideoNodesByParentHandleUseCase(
                                parentHandle = parent.id.longValue,
                                order = getSortOrderFromIntent(intent)
                            )?.let { children ->
                                buildPlaySourcesByTypedNodes(
                                    type = type,
                                    typedNodes = children,
                                    firstPlayHandle = firstPlayHandle
                                )
                            }
                        }
                    }

                    RECENTS_ADAPTER, RECENTS_BUCKET_ADAPTER -> {
                        _playlistTitleState.update {
                            context.getString(R.string.section_recents)
                        }
                        intent.getLongArrayExtra(NODE_HANDLES)?.let { handles ->
                            buildPlaylistFromHandles(
                                type = type,
                                handles = handles.toList(),
                                firstPlayHandle = firstPlayHandle
                            )
                        }
                    }

                    FOLDER_LINK_ADAPTER -> {
                        val parentHandle = intent.getLongExtra(
                            INTENT_EXTRA_KEY_PARENT_NODE_HANDLE,
                            INVALID_HANDLE
                        )
                        val order = getSortOrderFromIntent(intent)

                        (if (parentHandle == INVALID_HANDLE) {
                            getRootNodeFromMegaApiFolderUseCase()
                        } else {
                            getParentNodeFromMegaApiFolderUseCase(parentHandle)
                        })?.let { parent ->
                            _playlistTitleState.update { parent.name }

                            getVideosByParentHandleFromMegaApiFolderUseCase(
                                parentHandle = parent.id.longValue,
                                order = order
                            )?.let { children ->
                                buildPlaySourcesByTypedNodes(
                                    type = type,
                                    typedNodes = children,
                                    firstPlayHandle = firstPlayHandle
                                )
                            }
                        }
                    }

                    ZIP_ADAPTER -> {
                        intent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY)
                            ?.let { zipPath ->
                                _playlistTitleState.update {
                                    File(zipPath).parentFile?.name ?: ""
                                }
                                File(zipPath).parentFile?.listFiles()?.let { files ->
                                    buildPlaySourcesByFiles(
                                        files = files.asList(),
                                        firstPlayHandle = firstPlayHandle
                                    )
                                }
                            }
                    }

                    SEARCH_BY_ADAPTER -> {
                        intent.getLongArrayExtra(INTENT_EXTRA_KEY_HANDLES_NODES_SEARCH)
                            ?.let { handles ->
                                buildPlaylistFromHandles(
                                    type = type,
                                    handles = handles.toList(),
                                    firstPlayHandle = firstPlayHandle
                                )
                            }
                    }
                }
            }
        } else {
            playlistItems.clear()

            val node: TypedFileNode? = getNodeByHandleUseCase(firstPlayHandle) as? TypedFileNode
            val thumbnail = when {
                type == OFFLINE_ADAPTER -> {
                    offlineThumbnailFileWrapper.getThumbnailFile(
                        context,
                        firstPlayHandle.toString()
                    )
                }

                node == null -> {
                    null
                }

                else -> {
                    File(
                        ThumbnailUtils.getThumbFolder(context),
                        node.base64Id.plus(FileUtil.JPG_EXTENSION)
                    )
                }
            }

            val duration = node?.type?.let {
                fileDurationMapper(it)
            } ?: 0

            playlistItemMapper(
                firstPlayHandle,
                firstPlayNodeName,
                thumbnail,
                0,
                TYPE_PLAYING,
                node?.size ?: INVALID_SIZE,
                duration,
            ).let { playlistItem ->
                playlistItems.add(playlistItem)
            }

            recreateAndUpdatePlaylistItems()
        }

        return true
    }

    /**
     * Build play sources by node OfflineNodes
     *
     * @param intent Intent
     * @param firstPlayHandle the index of first playing item
     */
    private fun buildPlaylistFromOfflineNodes(
        intent: Intent,
        firstPlayHandle: Long,
    ) {
        intent.parcelableArrayList<MegaOffline>(INTENT_EXTRA_KEY_ARRAY_OFFLINE)
            ?.let { offlineFiles ->
                playlistItems.clear()

                val mediaItems = mutableListOf<MediaItem>()
                var firstPlayIndex = 0

                offlineFiles.filter {
                    getOfflineFile(context, it).let { file ->
                        FileUtil.isFileAvailable(file) && file.isFile && filterByNodeName(it.name)
                    }
                }.mapIndexed { currentIndex, megaOffline ->
                    mediaItems.add(
                        mediaItemFromFile(
                            getOfflineFile(context, megaOffline),
                            megaOffline.handle
                        )
                    )
                    if (megaOffline.handle.toLong() == firstPlayHandle) {
                        firstPlayIndex = currentIndex
                    }

                    playlistItemMapper(
                        megaOffline.handle.toLong(),
                        megaOffline.name,
                        offlineThumbnailFileWrapper.getThumbnailFile(context, megaOffline),
                        currentIndex,
                        TYPE_NEXT,
                        megaOffline.getSize(context),
                        0
                    )
                        .let { playlistItem ->
                            playlistItems.add(playlistItem)
                        }
                }

                updatePlaySources(mediaItems, playlistItems, firstPlayIndex)
            }
    }

    /**
     * Build play sources by node TypedNodes
     *
     * @param type adapter type
     * @param typedNodes [TypedNode] list
     * @param firstPlayHandle the index of first playing item
     */
    private suspend fun buildPlaySourcesByTypedNodes(
        type: Int,
        typedNodes: List<TypedNode>,
        firstPlayHandle: Long,
    ) {
        playlistItems.clear()

        val mediaItems = ArrayList<MediaItem>()
        var firstPlayIndex = 0

        typedNodes.mapIndexed { currentIndex, typedNode ->
            if (typedNode is TypedFileNode) {
                getLocalFilePathUseCase(typedNode).let { localPath ->
                    if (localPath != null && isLocalFile(typedNode, localPath)) {
                        mediaItemFromFile(File(localPath), typedNode.id.longValue.toString())
                    } else {
                        val url =
                            if (type == FOLDER_LINK_ADAPTER) {
                                if (isMegaApiFolder(type)) {
                                    getLocalFolderLinkFromMegaApiFolderUseCase(typedNode.id.longValue)
                                } else {
                                    getLocalFolderLinkFromMegaApiUseCase(typedNode.id.longValue)
                                }
                            } else {
                                getLocalLinkFromMegaApiUseCase(typedNode.id.longValue)
                            }
                        if (url == null) {
                            null
                        } else {
                            MediaItem.Builder()
                                .setUri(Uri.parse(url))
                                .setMediaId(typedNode.id.longValue.toString())
                                .build()
                        }
                    }?.let {
                        mediaItems.add(it)
                    }
                }

                if (typedNode.id.longValue == firstPlayHandle) {
                    firstPlayIndex = currentIndex
                }
                val thumbnail = typedNode.thumbnailPath?.let { path ->
                    File(path)
                }

                val duration = typedNode.type.let {
                    fileDurationMapper(it) ?: 0
                }

                playlistItemMapper(
                    typedNode.id.longValue,
                    typedNode.name,
                    thumbnail,
                    currentIndex,
                    TYPE_NEXT,
                    typedNode.size,
                    duration,
                ).let { playlistItem ->
                    playlistItems.add(playlistItem)
                }
            }
        }
        updatePlaySources(mediaItems, playlistItems, firstPlayIndex)
    }

    /**
     * Build play sources by node handles
     *
     * @param type adapter type
     * @param handles node handles
     * @param firstPlayHandle the index of first playing item
     */
    private suspend fun buildPlaylistFromHandles(
        type: Int,
        handles: List<Long>,
        firstPlayHandle: Long,
    ) {
        buildPlaySourcesByTypedNodes(
            type = type,
            typedNodes = getNodesByHandlesUseCase(handles),
            firstPlayHandle = firstPlayHandle
        )
    }

    /**
     * Build play sources by files
     *
     * @param files media files
     * @param firstPlayHandle the index of first playing item
     */
    private fun buildPlaySourcesByFiles(
        files: List<File>,
        firstPlayHandle: Long,
    ) {
        playlistItems.clear()

        val mediaItems = ArrayList<MediaItem>()
        var firstPlayIndex = 0

        files.filter {
            it.isFile && filterByNodeName(it.name)
        }.mapIndexed { currentIndex, file ->
            mediaItems.add(mediaItemFromFile(file, file.name.hashCode().toString()))

            if (file.name.hashCode().toLong() == firstPlayHandle) {
                firstPlayIndex = currentIndex
            }

            playlistItemMapper(
                file.name.hashCode().toLong(),
                file.name,
                null,
                currentIndex,
                TYPE_NEXT,
                file.length(),
                0
            )
                .let { playlistItem ->
                    playlistItems.add(playlistItem)
                }
        }
        updatePlaySources(mediaItems, playlistItems, firstPlayIndex)
    }

    /**
     * Update play sources for media player and playlist
     *
     * @param mediaItems media items
     * @param items playlist items
     * @param firstPlayIndex the index of first playing item
     */
    private fun updatePlaySources(
        mediaItems: List<MediaItem>,
        items: List<PlaylistItem>,
        firstPlayIndex: Int,
    ) {
        if (mediaItems.isNotEmpty() && items.isNotEmpty()) {
            _playerSourcesState.update {
                MediaPlaySources(mediaItems, firstPlayIndex, null)
            }
            recreateAndUpdatePlaylistItems(originalItems = items)
        }
    }

    internal fun initVideoSources(intent: Intent?) {
        viewModelScope.launch {
            buildPlayerSource(intent)
            trackPlayback {
                PlaybackInformation(
                    mediaPlayerGateway.getCurrentMediaItem()?.mediaId?.toLong(),
                    mediaPlayerGateway.getCurrentItemDuration(),
                    mediaPlayerGateway.getCurrentPlayingPosition()
                )
            }
        }
    }

    internal fun playSource(mediaPlaySources: MediaPlaySources) {
        Timber.d("playSource ${mediaPlaySources.mediaItems.size} items")

        mediaPlaySources.nameToDisplay?.let { name ->
            _metadataState.update {
                Metadata(title = null, artist = null, album = null, nodeName = name)
            }
        }

        mediaPlayerGateway.buildPlaySources(mediaPlaySources)

        if (_mediaPlaybackState.value) {
            mediaPlayerGateway.setPlayWhenReady(true)
        }

        mediaPlayerGateway.playerPrepare()
    }

    /**
     * Setup transfer listener
     */
    private fun setupTransferListener() {
        getGlobalTransferUseCase.get()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .filter { it is GetGlobalTransferUseCase.Result.OnTransferTemporaryError && it.transfer != null }
            .subscribeBy(
                onNext = { event ->
                    val errorEvent =
                        event as GetGlobalTransferUseCase.Result.OnTransferTemporaryError
                    errorEvent.transfer?.run {
                        onTransferTemporaryError(this, errorEvent.error)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeBy(onError = { Timber.e(it) })
                            .addTo(compositeDisposable)
                    }
                },
                onError = { Timber.e(it) }
            )
            .addTo(compositeDisposable)
    }

    /**
     * Handle player error.
     */
    internal fun onPlayerError() {
        playerRetry++
        _retryState.update { playerRetry <= MAX_RETRY }
    }

    /**
     * Check if the new intent would create the same playlist as current one.
     *
     * @param type new adapter type
     * @param intent new intent
     * @return if the new intent would create the same playlist as current one
     */
    private fun isSamePlaylist(type: Int, intent: Intent): Boolean {
        val oldIntent = currentIntent ?: return false
        val oldType = oldIntent.getIntExtra(INTENT_EXTRA_KEY_ADAPTER_TYPE, INVALID_VALUE)

        if (
            intent.getBooleanExtra(INTENT_EXTRA_KEY_FROM_DOWNLOAD_SERVICE, false)
            && oldIntent.getBooleanExtra(INTENT_EXTRA_KEY_FROM_DOWNLOAD_SERVICE, false)
        ) {
            return true
        }

        when (type) {
            OFFLINE_ADAPTER -> {
                val oldDir =
                    oldIntent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY)
                        ?: return false
                val newDir =
                    intent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY)
                        ?: return false
                return oldDir == newDir
            }

            VIDEO_BROWSE_ADAPTER,
            FROM_CHAT,
            FILE_LINK_ADAPTER,
            PHOTO_SYNC_ADAPTER,
            -> {
                return oldType == type
            }

            FILE_BROWSER_ADAPTER,
            RUBBISH_BIN_ADAPTER,
            INBOX_ADAPTER,
            LINKS_ADAPTER,
            INCOMING_SHARES_ADAPTER,
            OUTGOING_SHARES_ADAPTER,
            CONTACT_FILE_ADAPTER,
            FOLDER_LINK_ADAPTER,
            -> {
                val oldParentHandle =
                    oldIntent.getLongExtra(INTENT_EXTRA_KEY_PARENT_NODE_HANDLE, INVALID_HANDLE)
                val newParentHandle =
                    intent.getLongExtra(INTENT_EXTRA_KEY_PARENT_NODE_HANDLE, INVALID_HANDLE)
                return oldType == type && oldParentHandle == newParentHandle
            }

            RECENTS_ADAPTER, RECENTS_BUCKET_ADAPTER -> {
                val oldHandles = oldIntent.getLongArrayExtra(NODE_HANDLES) ?: return false
                val newHandles = intent.getLongArrayExtra(NODE_HANDLES) ?: return false
                return oldHandles.contentEquals(newHandles)
            }

            ZIP_ADAPTER -> {
                val oldZipPath = oldIntent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY)
                    ?: return false
                val newZipPath =
                    intent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY) ?: return false
                return oldZipPath == newZipPath
            }

            SEARCH_BY_ADAPTER -> {
                val oldHandles = oldIntent.getLongArrayExtra(INTENT_EXTRA_KEY_HANDLES_NODES_SEARCH)
                    ?: return false
                val newHandles =
                    intent.getLongArrayExtra(INTENT_EXTRA_KEY_HANDLES_NODES_SEARCH) ?: return false
                return oldHandles.contentEquals(newHandles)
            }

            else -> {
                return false
            }
        }
    }

    private fun filterByNodeName(name: String): Boolean =
        MimeTypeList.typeForName(name).let { mime ->
            mime.isVideo && mime.isVideoMimeType && !mime.isVideoNotSupported
        }

    private fun getSortOrderFromIntent(intent: Intent): SortOrder {
        val order =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(
                    INTENT_EXTRA_KEY_ORDER_GET_CHILDREN,
                    SortOrder::class.java
                ) ?: SortOrder.ORDER_DEFAULT_ASC
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(INTENT_EXTRA_KEY_ORDER_GET_CHILDREN) as SortOrder?
                    ?: SortOrder.ORDER_DEFAULT_ASC
            }
        return order
    }

    override fun initNewSearch(): MegaCancelToken {
        cancelSearch()
        return MegaCancelToken.createInstance()
    }

    override fun cancelSearch() {
        cancelToken?.cancel()
    }

    private fun mediaItemFromFile(file: File, handle: String): MediaItem =
        MediaItem.Builder()
            .setUri(FileUtil.getUriForFile(context, file))
            .setMediaId(handle)
            .build()

    /**
     * Recreate and update playlist items
     *
     * @param originalItems playlist items
     * @param isScroll true is scroll to target position, otherwise is false.
     */
    private fun recreateAndUpdatePlaylistItems(
        originalItems: List<PlaylistItem?> = playlistItems,
        isScroll: Boolean = true,
    ) {
        viewModelScope.launch(ioDispatcher) {
            Timber.d("recreateAndUpdatePlaylistItems ${originalItems.size} items")
            if (originalItems.isEmpty()) {
                return@launch
            }
            val items = originalItems.filterNotNull()
            playingPosition = items.indexOfFirst { (nodeHandle) ->
                nodeHandle == playingHandle
            }.takeIf { index ->
                index in originalItems.indices
            } ?: 0

            val recreatedItems = items.toMutableList()

            val searchQuery = playlistSearchQuery
            if (!TextUtil.isTextEmpty(searchQuery)) {
                filterPlaylistItems(recreatedItems, searchQuery ?: return@launch)
                return@launch
            }
            for ((index, item) in recreatedItems.withIndex()) {
                val type = when {
                    index < playingPosition -> TYPE_PREVIOUS
                    playingPosition == index -> TYPE_PLAYING
                    else -> TYPE_NEXT
                }
                recreatedItems[index] =
                    item.finalizeItem(
                        index = index,
                        type = type,
                        isSelected = item.isSelected,
                        duration = item.duration,
                    )
            }
            val hasPrevious = playingPosition > 0
            var scrollPosition = playingPosition
            if (hasPrevious) {
                recreatedItems[0] = recreatedItems[0].copy(headerIsVisible = true)
            }
            recreatedItems[playingPosition] =
                recreatedItems[playingPosition].copy(headerIsVisible = true)

            Timber.d("recreateAndUpdatePlaylistItems post ${recreatedItems.size} items")
            if (!isScroll) {
                scrollPosition = -1
            }
            _playlistItemsState.update {
                it.copy(recreatedItems, scrollPosition)
            }
        }
    }

    private fun filterPlaylistItems(items: List<PlaylistItem>, filter: String) {
        if (items.isEmpty()) return

        val filteredItems = ArrayList<PlaylistItem>()
        items.forEachIndexed { index, item ->
            if (item.nodeName.contains(filter, true)) {
                // Filter only affects displayed playlist, it doesn't affect what
                // ExoPlayer is playing, so we still need use the index before filter.
                filteredItems.add(item.finalizeItem(index, TYPE_PREVIOUS))
            }
        }

        _playlistItemsState.update {
            it.copy(filteredItems, 0)
        }
    }

    /**
     * Set new text for playlist search query
     *
     * @param newText the new text string
     */
    internal fun searchQueryUpdate(newText: String?) {
        playlistSearchQuery = newText
        recreateAndUpdatePlaylistItems(
            originalItems = _playlistItemsState.value.first
        )
    }

    /**
     * Get the handle of the current playing item
     *
     * @return the handle of the current playing item
     */
    internal fun getCurrentPlayingHandle() = playingHandle

    /**
     *  Set the handle of the current playing item
     *
     *  @param handle MegaNode handle
     */
    internal fun setCurrentPlayingHandle(handle: Long) {
        playingHandle = handle
        _playlistItemsState.value.first.let { playlistItems ->
            playingPosition = playlistItems.indexOfFirst { (nodeHandle) ->
                nodeHandle == handle
            }.takeIf { index -> index in playlistItems.indices } ?: 0
            recreateAndUpdatePlaylistItems(
                originalItems = _playlistItemsState.value.first
            )
        }
    }

    /**
     * Get playlist item
     *
     * @param handle MegaNode handle
     * @return PlaylistItem
     */
    internal fun getPlaylistItem(handle: String?): PlaylistItem? =
        handle?.let {
            playlistItems.toList().firstOrNull { (nodeHandle) ->
                nodeHandle == handle.toLong()
            }
        }

    /**
     * Remove item
     *
     * @param handle the handle that is removed
     */
    internal fun removeItem(handle: Long) {
        initPlayerSourceChanged()
        val newItems = removeSingleItem(handle)
        if (newItems.isNotEmpty()) {
            resetRetryState()
            recreateAndUpdatePlaylistItems(originalItems = newItems)
        } else {
            _playlistItemsState.update {
                it.copy(emptyList(), 0)
            }
            _errorState.update { MegaError.API_ENOENT }
        }
    }

    private fun removeSingleItem(handle: Long): List<PlaylistItem> =
        _playlistItemsState.value.first.let { items ->
            val newItems = items.toMutableList()
            items.indexOfFirst { (nodeHandle) ->
                nodeHandle == handle
            }.takeIf { index ->
                index in playlistItems.indices
            }?.let { index ->
                _mediaItemToRemoveState.update { index }
                newItems.removeIf { (nodeHandle) ->
                    nodeHandle == handle
                }
                playlistItems.removeIf { (nodeHandle) ->
                    nodeHandle == handle
                }
                playSourceChanged.removeIf { mediaItem ->
                    mediaItem.mediaId.toLong() == handle
                }
            }
            newItems
        }

    /**
     * Remove the selected items
     */
    internal fun removeAllSelectedItems() {
        if (itemsSelectedMap.isNotEmpty()) {
            itemsSelectedMap.forEach {
                removeSingleItem(it.value.nodeHandle).let { newItems ->
                    _playlistItemsState.update { flow ->
                        flow.copy(newItems, playingPosition)
                    }
                }
            }
            itemsSelectedMap.clear()
            _itemsSelectedCountState.update { itemsSelectedMap.size }
            _actionModeState.update { false }
        }
    }

    /**
     * Saved or remove the selected items
     * @param handle node handle of selected item
     */
    internal fun itemSelected(handle: Long) {
        _playlistItemsState.update {
            it.copy(
                it.first.toMutableList().let { playlistItems ->
                    playlistItems.indexOfFirst { (nodeHandle) ->
                        nodeHandle == handle
                    }.takeIf { index ->
                        index in playlistItems.indices
                    }?.let { selectedIndex ->
                        playlistItems[selectedIndex].let { item ->
                            val isSelected = !item.isSelected
                            playlistItems[selectedIndex] = item.copy(isSelected = isSelected)
                            if (playlistItems[selectedIndex].isSelected) {
                                itemsSelectedMap[handle] = item
                            } else {
                                itemsSelectedMap.remove(handle)
                            }
                            _itemsSelectedCountState.update { itemsSelectedMap.size }
                        }
                    }
                    playlistItems
                }
            )
        }
    }

    /**
     * Clear the all selections
     */
    internal fun clearSelections() {
        _playlistItemsState.update {
            it.copy(
                it.first.toMutableList().let { playlistItems ->
                    playlistItems.map { item ->
                        item.copy(isSelected = false)
                    }
                }
            )
        }
        itemsSelectedMap.clear()
        _actionModeState.update { false }

    }

    /**
     * Set the action mode
     * @param isActionMode whether the action mode is activated
     */
    internal fun setActionMode(isActionMode: Boolean) {
        _actionModeState.update { isActionMode }
        if (isActionMode) {
            recreateAndUpdatePlaylistItems(
                originalItems = _playlistItemsState.value.first,
                isScroll = false
            )
        }
    }

    /**
     * Reset retry state
     */
    internal fun resetRetryState() {
        playerRetry = 0
        _retryState.update { true }
    }

    /**
     * Track the playback information
     *
     * @param getCurrentPlaybackInformation get current playback information
     */
    private suspend fun trackPlayback(getCurrentPlaybackInformation: () -> PlaybackInformation) {
        trackPlaybackPositionUseCase(getCurrentPlaybackInformation)
    }

    /**
     * Monitor playback times
     *
     * @param mediaId the media id of target media item
     * @param seekToPosition the callback for seek to playback position history. If the current item contains the playback history,
     * then invoke the callback and the playback position history is parameter
     */
    internal fun monitorPlaybackTimes(
        mediaId: Long?,
        seekToPosition: (positionInMs: Long?) -> Unit,
    ) = viewModelScope.launch {
        seekToPosition(
            monitorPlaybackTimesUseCase().firstOrNull()
                ?.get(mediaId)?.currentPosition
        )
    }

    /**
     * Save the playback times
     */
    internal fun savePlaybackTimes() = viewModelScope.launch {
        savePlaybackTimesUseCase()
    }

    /**
     * Delete playback information
     *
     * @param mediaId the media id of deleted item
     */
    internal fun deletePlaybackInformation(mediaId: Long) = viewModelScope.launch {
        deletePlaybackInformationUseCase(mediaId)
    }

    /**
     * Update item name
     *
     * @param handle MegaNode handle
     * @param newName the new name string
     */
    internal fun updateItemName(handle: Long, newName: String) =
        _playlistItemsState.update {
            it.copy(
                it.first.map { item ->
                    if (item.nodeHandle == handle) {
                        _metadataState.update { metadata ->
                            metadata.copy(nodeName = newName)
                        }
                        item.updateNodeName(newName)
                    } else {
                        item
                    }
                }
            )
        }

    /**
     * Get playlist items
     *
     * @return List<PlaylistItem>
     */
    internal fun getPlaylistItems() = _playlistItemsState.value.first

    /**
     * Get video repeat Mode
     *
     * @return RepeatToggleMode
     */
    internal fun videoRepeatToggleMode() = _videoRepeatToggleMode.value

    /**
     * Set repeat mode for video
     *
     * @param repeatToggleMode RepeatToggleMode
     */
    internal fun setVideoRepeatMode(repeatToggleMode: RepeatToggleMode) {
        viewModelScope.launch {
            setVideoRepeatModeUseCase(repeatToggleMode.ordinal)
        }
    }

    /**
     * Clear the state and flying task of this class, should be called in onDestroy.
     */
    internal fun clear() {
        viewModelScope.launch {
            compositeDisposable.dispose()

            if (needStopStreamingServer) {
                megaApiHttpServerStop()
                megaApiFolderHttpServerStopUseCase()
            }
        }
    }

    private suspend fun isMegaApiFolder(type: Int) =
        type == FOLDER_LINK_ADAPTER && areCredentialsNullUseCase()

    /**
     * Swap the items
     * @param current the position of from item
     * @param target the position of to item
     */
    internal fun swapItems(current: Int, target: Int) {
        if (playlistItemsChanged.isEmpty()) {
            playlistItemsChanged.addAll(_playlistItemsState.value.first)
        }
        Collections.swap(playlistItemsChanged, current, target)
        val index = playlistItemsChanged[current].index
        playlistItemsChanged[current] =
            playlistItemsChanged[current].copy(index = playlistItemsChanged[target].index)
        playlistItemsChanged[target] = playlistItemsChanged[target].copy(index = index)

        initPlayerSourceChanged()
        // Swap the items of play source
        Collections.swap(playSourceChanged, current, target)
    }

    /**
     * Get the index from playlistItems to keep the play order is correct after reordered
     * @param item clicked item
     * @return the index of clicked item in playlistItems or null
     */
    internal fun getIndexFromPlaylistItems(item: PlaylistItem): Int? =
        /* The media items of ExoPlayer are still the original order even the shuffleEnable is true,
         so the index of media item should be got from original playlist items */
        playlistItems.indexOfFirst {
            it.nodeHandle == item.nodeHandle
        }.takeIf { index ->
            index in _playlistItemsState.value.first.indices
        }

    /**
     * Updated the play source of exoplayer after reordered.
     */
    internal fun updatePlaySource() {
        _playlistItemsState.update {
            it.copy(playlistItemsChanged.toList())
        }
        _playerSourcesState.update {
            it.copy(
                mediaItems = playSourceChanged.toList(),
                newIndexForCurrentItem = playingPosition
            )
        }
        playSourceChanged.clear()
        playlistItemsChanged.clear()
    }

    /**
     * Get the position of playing item
     *
     * @return the position of playing item
     */
    internal fun getPlayingPosition(): Int = playingPosition

    /**
     * Scroll to the position of playing item
     */
    internal fun scrollToPlayingPosition() =
        recreateAndUpdatePlaylistItems(
            originalItems = _playlistItemsState.value.first
        )

    /**
     * Get the subtitle file info that is same name as playing media item
     *
     * @return SubtitleFileInfo
     */
    internal suspend fun getMatchedSubtitleFileInfoForPlayingItem(): SubtitleFileInfo? =
        getSRTSubtitleFileListUseCase().firstOrNull { subtitleFileInfo ->
            val subtitleName = subtitleFileInfo.name.let { name ->
                name.substring(0, name.lastIndexOf("."))
            }
            val mediaItemName = playlistItems[playingPosition].nodeName.let { name ->
                name.substring(0, name.lastIndexOf("."))
            }
            subtitleName == mediaItemName
        }

    private fun initPlayerSourceChanged() {
        if (playSourceChanged.isEmpty()) {
            // Get the play source
            playSourceChanged.addAll(_playerSourcesState.value.mediaItems)
        }
    }

    private fun onTransferTemporaryError(transfer: MegaTransfer, e: MegaError): Completable =
        Completable.fromAction {
            if (transfer.nodeHandle != playingHandle) {
                return@fromAction
            }

            if ((e.errorCode == MegaError.API_EOVERQUOTA && !transfer.isForeignOverquota && e.value != 0L)
                || e.errorCode == MegaError.API_EBLOCKED
            ) {
                _errorState.update { e.errorCode }
            }
        }

    private suspend fun setupStreamingServer(type: Int): Boolean {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)

        if (isMegaApiFolder(type)) {
            if (megaApiFolderHttpServerIsRunningUseCase() != 0) {
                return false
            }
            megaApiFolderHttpServerStartUseCase()
            megaApiFolderHttpServerSetMaxBufferSizeUseCase(
                bufferSize = if (memoryInfo.totalMem > Constants.BUFFER_COMP)
                    Constants.MAX_BUFFER_32MB
                else
                    Constants.MAX_BUFFER_16MB
            )
        } else {
            if (megaApiHttpServerIsRunningUseCase() != 0) {
                return false
            }
            megaApiHttpServerStartUseCase()
            megaApiHttpServerSetMaxBufferSizeUseCase(
                bufferSize = if (memoryInfo.totalMem > Constants.BUFFER_COMP)
                    Constants.MAX_BUFFER_32MB
                else
                    Constants.MAX_BUFFER_16MB
            )
        }

        return true
    }

    private suspend fun isLocalFile(node: TypedFileNode, localPath: String?): Boolean =
        node.fingerprint.let { fingerprint ->
            localPath != null &&
                    (isOnMegaDownloads(node) || (fingerprint != null
                            && fingerprint == getFingerprintUseCase(localPath)))
        }

    private fun isOnMegaDownloads(node: TypedFileNode): Boolean =
        File(FileUtil.getDownloadLocation(), node.name).let { file ->
            FileUtil.isFileAvailable(file) && file.length() == node.size
        }

    private suspend fun saveBitmap(
        filePath: String,
        bitmap: Bitmap,
        successCallback: (bitmap: Bitmap) -> Unit,
    ) =
        withContext(ioDispatcher) {
            val screenshotFile = File(filePath)
            try {
                val outputStream = FileOutputStream(screenshotFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY_SCREENSHOT, outputStream)
                outputStream.flush()
                outputStream.close()
                successCallback(bitmap)
            } catch (e: Exception) {
                Timber.e("Bitmap is saved error: ${e.message}")
            }
        }

    /**
     * Format milliseconds to time string
     * @param milliseconds time value that unit is milliseconds
     * @return strings of time
     */
    fun formatMillisecondsToString(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000).toInt()
        return formatSecondsToString(seconds = totalSeconds)
    }

    /**
     * Format seconds to time string
     * @param seconds time value that unit is seconds
     * @return strings of time
     */
    private fun formatSecondsToString(seconds: Int): String {
        val hour = TimeUnit.SECONDS.toHours(seconds.toLong())
        val minutes =
            TimeUnit.SECONDS.toMinutes(seconds.toLong()) - TimeUnit.HOURS.toMinutes(hour)
        val resultSeconds =
            seconds.toLong() - TimeUnit.MINUTES.toSeconds(
                TimeUnit.SECONDS.toMinutes(
                    seconds.toLong()
                )
            )

        return if (hour >= 1) {
            String.format("%2d:%02d:%02d", hour, minutes, resultSeconds)
        } else {
            String.format("%02d:%02d", minutes, resultSeconds)
        }
    }

    /**
     * Send OpenSelectSubtitlePageEvent
     */
    fun sendOpenSelectSubtitlePageEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.OpenSelectSubtitlePageEvent())

    /**
     * Send LoopButtonEnabledEvent
     */
    fun sendLoopButtonEnabledEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.LoopButtonEnabledEvent())

    /**
     * Send ScreenLockedEvent
     */
    fun sendScreenLockedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.ScreenLockedEvent())

    /**
     * Send ScreenUnlockedEvent
     */
    fun sendScreenUnlockedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.ScreenUnlockedEvent())

    /**
     * Send SnapshotButtonClickedEvent
     */
    fun sendSnapshotButtonClickedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.SnapshotButtonClickedEvent())

    /**
     * Send InfoButtonClickedEvent
     */
    fun sendInfoButtonClickedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.InfoButtonClickedEvent())

    /**
     * Send SaveToDeviceButtonClickedEvent
     */
    fun sendSaveToDeviceButtonClickedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.SaveToDeviceButtonClickedEvent())

    /**
     * Send SendToChatButtonClickedEvent
     */
    fun sendSendToChatButtonClickedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.SendToChatButtonClickedEvent())

    /**
     * Send ShareButtonClickedEvent
     */
    fun sendShareButtonClickedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.ShareButtonClickedEvent())

    /**
     * Send GetLinkButtonClickedEvent
     */
    fun sendGetLinkButtonClickedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.GetLinkButtonClickedEvent())

    /**
     * Send RemoveLinkButtonClickedEvent
     */
    fun sendRemoveLinkButtonClickedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.RemoveLinkButtonClickedEvent())

    /**
     * Send VideoPlayerActivatedEvent
     */
    fun sendVideoPlayerActivatedEvent() =
        sendMediaPlayerStatisticsEvent(MediaPlayerStatisticsEvents.VideoPlayerActivatedEvent())

    /**
     * Send MediaPlayerStatisticsEvent
     *
     * @param event MediaPlayerStatisticsEvents
     */
    private fun sendMediaPlayerStatisticsEvent(event: MediaPlayerStatisticsEvents) {
        viewModelScope.launch {
            sendStatisticsMediaPlayerUseCase(event)
        }
    }

    companion object {
        private const val QUALITY_SCREENSHOT = 100
        private const val DATE_FORMAT_PATTERN = "yyyyMMdd-HHmmss"
        private const val SCREENSHOT_NAME_PREFIX = "Screenshot_"
        private const val SCREENSHOT_NAME_SUFFIX = ".jpg"

        /**
         * The state for the off is selected
         */
        const val SUBTITLE_SELECTED_STATE_OFF = 900

        /**
         * The state for the matched item is selected
         */
        const val SUBTITLE_SELECTED_STATE_MATCHED_ITEM = 901

        /**
         * The state for the add subtitle item is selected
         */
        const val SUBTITLE_SELECTED_STATE_ADD_SUBTITLE_ITEM = 902

        private const val MAX_RETRY = 6

        internal const val VIDEO_TYPE_RESUME_PLAYBACK_POSITION = 123
        internal const val VIDEO_TYPE_RESTART_PLAYBACK_POSITION = 124
        internal const val VIDEO_TYPE_SHOW_PLAYBACK_POSITION_DIALOG = 125
    }
}