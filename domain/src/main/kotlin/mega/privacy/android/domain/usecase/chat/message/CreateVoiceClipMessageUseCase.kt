package mega.privacy.android.domain.usecase.chat.message

import mega.privacy.android.domain.entity.chat.ChatMessage
import mega.privacy.android.domain.entity.chat.messages.VoiceClipMessage
import javax.inject.Inject

internal class CreateVoiceClipMessageUseCase @Inject constructor() : CreateTypedMessageUseCase {

    override fun invoke(message: ChatMessage, isMine: Boolean) = VoiceClipMessage(
        message.msgId,
        message.timestamp,
        isMine = isMine
    )
}