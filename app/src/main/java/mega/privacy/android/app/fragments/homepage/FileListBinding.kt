package mega.privacy.android.app.fragments.homepage

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.generic.RoundingParams
import com.facebook.drawee.view.SimpleDraweeView
import mega.privacy.android.app.R
import mega.privacy.android.app.utils.Util
import java.io.File

@Suppress("UNCHECKED_CAST")
@BindingAdapter("items")
fun setItems(listView: RecyclerView, items: List<NodeItem>?) {
    items?.let {
        (listView.adapter as ListAdapter<NodeItem, RecyclerView.ViewHolder>).submitList(it)
    }
}

@BindingAdapter("thumbnail", "selected")
fun setGridItemThumbnail(imageView: SimpleDraweeView, file: File?, selected: Boolean) {
    with(imageView) {
        if (file == null || !file.exists()) setImageResource(R.drawable.ic_image_thumbnail) else setImageURI(
            Uri.fromFile(
                file
            )
        )

        if (selected) {
            hierarchy.roundingParams = getRoundingParams(context)
        } else {
            hierarchy.roundingParams = null
        }
    }
}

@BindingAdapter("thumbnail", "item_selected", "defaultThumbnail")
fun setListItemThumbnail(
    imageView: SimpleDraweeView,
    file: File?,
    selected: Boolean,
    defaultThumbnail: Int
) {
    with(imageView) {
        if (selected) {
            setActualImageResource(R.drawable.ic_select_folder)
        } else {
            if (file == null || !file.exists()) {
                setImageResource(defaultThumbnail)
            } else setImageURI(
                Uri.fromFile(
                    file
                )
            )
        }
    }
}

@BindingAdapter("thumbnail", "defaultThumbnail")
fun setNodeGridThumbnail(imageView: SimpleDraweeView, file: File?, defaultThumbnail: Int) {
    with(imageView) {
        if (file == null) {
            setActualImageResource(defaultThumbnail)
        } else {
            setImageURI(Uri.fromFile(file))
        }

        val radius = resources.getDimensionPixelSize(R.dimen.homepage_node_grid_round_corner_radius)
            .toFloat()
        hierarchy.roundingParams = RoundingParams.fromCornersRadii(radius, radius, 0F, 0F)
    }
}

@BindingAdapter("visibleGone")
fun showHide(view: View, show: Boolean) {
    view.visibility = if (show) View.VISIBLE else View.GONE
}

private var roundingParams: RoundingParams? = null

fun getRoundingParams(context: Context): RoundingParams? {
    roundingParams?.let {
        return it
    }

    roundingParams = RoundingParams.fromCornersRadius(
        Util.dp2px(
            context.resources.getDimension(R.dimen.photo_selected_icon_round_corner_radius),
            context.resources.displayMetrics
        ).toFloat()
    )

    roundingParams?.apply {
        setBorder(
            context.resources.getColor(R.color.accentColor), Util.dp2px(
                context.resources.getDimension(R.dimen.photo_selected_border_width),
                context.resources.displayMetrics
            ).toFloat()
        )
    }

    return roundingParams
}

