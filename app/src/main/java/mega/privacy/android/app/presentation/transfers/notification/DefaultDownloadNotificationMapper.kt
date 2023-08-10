package mega.privacy.android.app.presentation.transfers.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import mega.privacy.android.app.R
import mega.privacy.android.app.main.ManagerActivity
import mega.privacy.android.app.presentation.manager.model.TransfersTab
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.Util
import mega.privacy.android.data.mapper.transfer.DownloadNotificationMapper
import mega.privacy.android.domain.entity.transfer.ActiveTransferTotals
import javax.inject.Inject

/**
 * Default implementation of [DownloadNotificationMapper]
 */
class DefaultDownloadNotificationMapper @Inject constructor(
    @ApplicationContext private val context: Context,
) : DownloadNotificationMapper {

    override fun invoke(
        activeTransferTotals: ActiveTransferTotals?,
        paused: Boolean,
    ): Notification {
        //notification channel creation will be done in a use case at app's startup: TRAN-197
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.setShowBadge(false)
            channel.setSound(null, null)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val intent = Intent(context, ManagerActivity::class.java)
        intent.action = Constants.ACTION_SHOW_TRANSFERS
        intent.putExtra(ManagerActivity.TRANSFERS_TAB, TransfersTab.PENDING_TAB)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val content = context.getString(R.string.download_touch_to_show)
        val title =
            if (activeTransferTotals == null || activeTransferTotals.transferredBytes == 0L) {
                context.getString(R.string.download_preparing_files)
            } else {
                val inProgress = activeTransferTotals.totalFinishedTransfers
                val totalTransfers = activeTransferTotals.totalTransfers
                if (paused) {
                    context.getString(
                        R.string.download_service_notification_paused,
                        inProgress,
                        totalTransfers
                    )
                } else {
                    context.getString(
                        R.string.download_service_notification,
                        inProgress,
                        totalTransfers
                    )
                }
            }
        val subText = activeTransferTotals?.let {
            Util.getProgressSize(
                context,
                activeTransferTotals.transferredBytes,
                activeTransferTotals.totalBytes
            )
        }

        val builder = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_ID
        ).apply {
            setSmallIcon(R.drawable.ic_stat_notify)
            setOngoing(true)
            setContentTitle(title)
            setStyle(NotificationCompat.BigTextStyle().bigText(content))
            setContentText(content)
            setOnlyAlertOnce(true)
            setAutoCancel(false)
            setContentIntent(pendingIntent)
            activeTransferTotals?.progressPercent?.let { setProgress(100, it, false) }
            subText?.let { setSubText(subText) }
        }
        return builder.build()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_DOWNLOAD_ID
        private const val NOTIFICATION_CHANNEL_NAME = Constants.NOTIFICATION_CHANNEL_DOWNLOAD_NAME
    }
}