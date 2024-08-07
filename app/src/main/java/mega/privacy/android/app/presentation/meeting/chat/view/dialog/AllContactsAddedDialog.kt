package mega.privacy.android.app.presentation.meeting.chat.view.dialog

import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import mega.privacy.android.app.R
import mega.privacy.android.app.presentation.contact.invite.InviteContactActivity
import mega.privacy.android.shared.original.core.ui.controls.dialogs.ConfirmationDialog
import mega.privacy.android.shared.original.core.ui.preview.CombinedThemePreviews
import mega.privacy.android.shared.original.core.ui.theme.OriginalTempTheme

/**
 * All contacts added dialog
 *
 */
@Composable
fun AllContactsAddedDialog(
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    ConfirmationDialog(
        title = stringResource(id = R.string.chat_add_participants_no_contacts_left_to_add_title),
        text = stringResource(id = R.string.chat_add_participants_no_contacts_left_to_add_message),
        confirmButtonText = stringResource(id = R.string.contact_invite),
        onDismiss = onDismiss,
        cancelButtonText = stringResource(id = R.string.button_cancel),
        onConfirm = {
            context.startActivity(Intent(context, InviteContactActivity::class.java))
            onDismiss()
        },
    )
}

@CombinedThemePreviews
@Composable
private fun AllContactsAddedDialogPreview() {
    OriginalTempTheme(isDark = isSystemInDarkTheme()) {
        AllContactsAddedDialog()
    }
}