package mega.privacy.android.domain.repository

import mega.privacy.android.domain.entity.SortOrder
import mega.privacy.android.domain.entity.node.NodeId
import mega.privacy.android.domain.entity.photos.AlbumPhotoId
import mega.privacy.android.domain.entity.photos.Photo
import java.io.File

/**
 * The repository interface regarding Timeline.
 */
interface PhotosRepository {

    /**
     * Get public links count
     */
    suspend fun getPublicLinksCount(): Int

    /**
     * create default download folder
     */
    suspend fun buildDefaultDownloadDir(): File

    /**
     * Get Camera Upload Folder handle
     *
     * @return
     */
    suspend fun getCameraUploadFolderId(): Long?

    /**
     * Get Media Upload Folder handle
     *
     * @return
     */
    suspend fun getMediaUploadFolderId(): Long?

    /**
     * Search all the Photos in mega
     */
    suspend fun searchMegaPhotos(): List<Photo>

    /**
     * Get photo with [nodeId]
     */
    suspend fun getPhotoFromNodeID(nodeId: NodeId, albumPhotoId: AlbumPhotoId? = null): Photo?

    /**
     * Get Photos from a folder
     */
    suspend fun getPhotosByFolderId(id: Long, order: SortOrder): List<Photo>
}