package mega.privacy.android.app.domain.usecase.sync

import mega.privacy.android.data.repository.MegaNodeRepository
import mega.privacy.android.domain.entity.SortOrder
import mega.privacy.android.domain.entity.sync.RemoteFolder

/**
 * Returns the list of MEGA folders from users root folder.
 */
interface GetRemoteFolders {

    suspend operator fun invoke(): List<RemoteFolder>
}

class GetRemoteFoldersImpl(
    private val megaNodeRepository: MegaNodeRepository,
) : GetRemoteFolders {

    override suspend fun invoke(): List<RemoteFolder> =
        megaNodeRepository
            .getRootNode()
            ?.let {
                megaNodeRepository
                    .getChildrenNode(it, SortOrder.ORDER_NONE)
                    .filter { it.isFolder }
            }
            ?.map { RemoteFolder(it.handle, it.name) }
            ?: emptyList()
}
