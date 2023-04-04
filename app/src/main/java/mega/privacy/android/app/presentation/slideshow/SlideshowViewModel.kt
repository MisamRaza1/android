package mega.privacy.android.app.presentation.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mega.privacy.android.app.imageviewer.data.ImageItem
import mega.privacy.android.app.presentation.slideshow.model.SlideshowViewState
import mega.privacy.android.domain.entity.node.NodeId
import mega.privacy.android.domain.entity.photos.Photo
import mega.privacy.android.domain.entity.slideshow.SlideshowOrder
import mega.privacy.android.domain.entity.slideshow.SlideshowSpeed
import mega.privacy.android.domain.usecase.GetPhotosByIds
import mega.privacy.android.domain.usecase.MonitorSlideshowOrderSettingUseCase
import mega.privacy.android.domain.usecase.MonitorSlideshowRepeatSettingUseCase
import mega.privacy.android.domain.usecase.MonitorSlideshowSpeedSettingUseCase
import mega.privacy.android.domain.usecase.SaveSlideshowOrderSettingUseCase
import mega.privacy.android.domain.usecase.SaveSlideshowRepeatSettingUseCase
import mega.privacy.android.domain.usecase.SaveSlideshowSpeedSettingUseCase
import javax.inject.Inject

/**
 * ViewModel for slideshow
 *
 */
@HiltViewModel
class SlideshowViewModel @Inject constructor(
    private val getPhotosByIds: GetPhotosByIds,
    private val monitorSlideshowOrderSettingUseCase: MonitorSlideshowOrderSettingUseCase,
    private val monitorSlideshowSpeedSettingUseCase: MonitorSlideshowSpeedSettingUseCase,
    private val monitorSlideshowRepeatSettingUseCase: MonitorSlideshowRepeatSettingUseCase,
    private val saveSlideshowOrderSettingUseCase: SaveSlideshowOrderSettingUseCase,
    private val saveSlideshowSpeedSettingUseCase: SaveSlideshowSpeedSettingUseCase,
    private val saveSlideshowRepeatSettingUseCase: SaveSlideshowRepeatSettingUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SlideshowViewState())
    val state = _state.asStateFlow()

    init {
        monitorOrderSetting()
        monitorSpeedSetting()
        monitorRepeatSetting()
    }

    /**
     * Monitor ImageViewer source images
     */
    fun setData(
        items: List<ImageItem>,
    ) {
        playSlideshowItems(items = items)
    }

    /**
     * Play slideshow from ImageViewer source images
     */
    fun playSlideshowItems(items: List<ImageItem>) {
        viewModelScope.launch {
            val chunkedSourceItems = items.chunked(slideshowPlayingUnit)
            val nextPlayingChunkedIndex = chunkedItems(chunkedSourceItems = chunkedSourceItems)
            val ids = chunkedSourceItems[_state.value.currentPlayingChunkedIndex].map { it.id }
            _state.update {
                it.copy(
                    items = getPhotosByIds(ids = ids.map { id -> NodeId(id) })
                        .filterIsInstance<Photo.Image>(),
                    currentPlayingChunkedIndex = nextPlayingChunkedIndex
                )
            }
        }
    }

    private fun chunkedItems(chunkedSourceItems: List<List<ImageItem>>) =
        _state.value.currentPlayingChunkedIndex.also { currentPlayingChunkedIndex ->
            if (currentPlayingChunkedIndex > chunkedSourceItems.size.minus(1)) {
                0
            } else {
                currentPlayingChunkedIndex.inc()
            }
        }

    private fun monitorOrderSetting() = monitorSlideshowOrderSettingUseCase()
        .onEach { order ->
            _state.update {
                it.copy(order = order ?: SlideshowOrder.Shuffle)
            }
        }.launchIn(viewModelScope)

    private fun monitorSpeedSetting() = monitorSlideshowSpeedSettingUseCase()
        .onEach { speed ->
            _state.update {
                it.copy(speed = speed ?: SlideshowSpeed.Normal)
            }
        }.launchIn(viewModelScope)

    private fun monitorRepeatSetting() = monitorSlideshowRepeatSettingUseCase()
        .onEach { isRepeat ->
            _state.update {
                it.copy(repeat = isRepeat ?: true)
            }
        }.launchIn(viewModelScope)

    fun saveOrderSetting(order: SlideshowOrder) = viewModelScope.launch {
        saveSlideshowOrderSettingUseCase(order)
    }

    fun saveSpeedSetting(speed: SlideshowSpeed) = viewModelScope.launch {
        saveSlideshowSpeedSettingUseCase(speed)
    }

    fun saveRepeatSetting(isRepeat: Boolean) = viewModelScope.launch {
        saveSlideshowRepeatSettingUseCase(isRepeat)
    }

    companion object {
        private const val slideshowPlayingUnit = 200
    }
}
