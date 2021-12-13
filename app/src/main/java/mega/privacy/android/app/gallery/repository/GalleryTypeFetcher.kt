package mega.privacy.android.app.gallery.repository

import android.content.Context
import mega.privacy.android.app.DatabaseHandler
import mega.privacy.android.app.MimeTypeThumbnail
import mega.privacy.android.app.gallery.data.GalleryItem
import mega.privacy.android.app.utils.LogUtil
import nz.mega.sdk.MegaApiAndroid
import nz.mega.sdk.MegaApiJava
import nz.mega.sdk.MegaNode
import nz.mega.sdk.MegaNodeList
import java.util.*

class GalleryTypeFetcher(
    context: Context,
    private val megaApi: MegaApiAndroid,
    selectedNodesMap: LinkedHashMap<Any, GalleryItem>,
    zoom: Int,
) : GalleryNodeFetcher(
    context = context,
    megaApi = megaApi,
    selectedNodesMap = selectedNodesMap,
    zoom = zoom
) {

    override fun getMegaNodes(order: Int, type: Int): List<MegaNode> =
        megaApi.searchByType(order, type, MegaApiJava.SEARCH_TARGET_ROOTNODE)

}