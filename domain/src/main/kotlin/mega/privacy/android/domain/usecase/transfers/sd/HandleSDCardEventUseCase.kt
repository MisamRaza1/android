package mega.privacy.android.domain.usecase.transfers.sd

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mega.privacy.android.domain.entity.SdTransfer
import mega.privacy.android.domain.entity.transfer.DestinationUriAndSubFolders
import mega.privacy.android.domain.entity.transfer.TransferEvent
import mega.privacy.android.domain.entity.transfer.TransferType
import mega.privacy.android.domain.entity.transfer.isSDCardDownload
import mega.privacy.android.domain.qualifier.ApplicationScope
import mega.privacy.android.domain.repository.FileSystemRepository
import java.io.File
import javax.inject.Inject

/**
 * Handles transfer events in case the event is related to Sd card transfer:
 * - When a transfer to the Sd card starts it inserts the related entity to the database
 * - When a file transfer to the Sd card finishes it moves the file from cache to the final destination
 * - When a root transfer finishes it deletes the related entity from the database
 */
class HandleSDCardEventUseCase @Inject constructor(
    private val insertSdTransferUseCase: InsertSdTransferUseCase,
    private val deleteSdTransferByTagUseCase: DeleteSdTransferByTagUseCase,
    private val moveFileToSdCardUseCase: MoveFileToSdCardUseCase,
    private val fileSystemRepository: FileSystemRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /**
     * Invoke
     */
    suspend operator fun invoke(
        transferEvent: TransferEvent,
        destinationUriAndSubFolders: DestinationUriAndSubFolders?,
    ) {
        val transfer = transferEvent.transfer
        val path = transfer.localPath.takeIf { it.isNotEmpty() } ?: transfer.parentPath
        if (transfer.transferType != TransferType.DOWNLOAD
            || !(transfer.isSDCardDownload() || fileSystemRepository.isSDCardCachePath(path))
        ) return

        when (transferEvent) {
            is TransferEvent.TransferStartEvent -> {
                insertSdTransferUseCase(
                    SdTransfer(
                        transfer.tag,
                        transfer.fileName,
                        transfer.totalBytes.toString(),
                        transfer.nodeHandle.toString(),
                        path,
                        transfer.appData
                    )
                )
            }

            is TransferEvent.TransferFinishEvent -> {
                if (transferEvent.error != null) return
                if (!transfer.isFolderTransfer) {
                    scope.launch {
                        destinationUriAndSubFolders?.let { (path, subFolders) ->
                            moveFileToSdCardUseCase(
                                File(transfer.localPath),
                                path,
                                subFolders
                            )
                        }
                    }
                }
                if (transfer.isRootTransfer && transfer.isSDCardDownload()) {
                    //if it's a root sd card transfer we can remove the sd transfer from the data base
                    deleteSdTransferByTagUseCase(transferEvent.transfer.tag)
                }
            }

            else -> {} //nothing here
        }
    }
}