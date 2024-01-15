package mega.privacy.android.domain.entity.chat.messages

import mega.privacy.android.domain.entity.chat.ChatMessageStatus

/**
 * Voice clip message
 *
 * @property status Message status
 * @property name name of the voice clip
 * @property size size of the voice clip
 * @property duration duration of the voice clip
 */
data class VoiceClipMessage(
    override val msgId: Long,
    override val time: Long,
    override val isMine: Boolean,
    override val userHandle: Long,
    val status: ChatMessageStatus,
    val name: String,
    val size: Long,
    val duration: Int,
    override val shouldShowAvatar: Boolean,
    override val shouldShowTime: Boolean,
    override val shouldShowDate: Boolean,
) : TypedMessage
