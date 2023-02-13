package mega.privacy.android.domain.usecase.imageviewer

import kotlinx.coroutines.flow.Flow
import mega.privacy.android.domain.entity.imageviewer.ImageResult

/**
 * The use case interface to get an ImageResult given a Node Chat Room Id and Chat Message Id.
 */
fun interface GetImageForChatMessage {

    /**
     * Get an ImageResult given a Node Chat Room Id and Chat Message Id.
     *
     * @param chatRoomId        Chat Message Room Id
     * @param chatMessageId     Chat Message Id
     * @param fullSize          Flag to request full size image despite data/size requirements.
     * @param highPriority      Flag to request image with high priority.
     *
     * @return Flow<ImageResult>
     */
    suspend operator fun invoke(
        chatRoomId: Long,
        chatMessageId: Long,
        fullSize: Boolean,
        highPriority: Boolean,
    ): Flow<ImageResult>
}