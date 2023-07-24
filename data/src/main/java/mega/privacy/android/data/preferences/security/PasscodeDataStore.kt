package mega.privacy.android.data.preferences.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mega.privacy.android.data.cryptography.DecryptData
import mega.privacy.android.data.cryptography.EncryptData
import mega.privacy.android.data.extensions.monitor
import mega.privacy.android.data.gateway.security.PasscodeStoreGateway
import javax.inject.Inject
import javax.inject.Named

internal const val passcodeDatastoreName = "passcodeDataStore"

internal val Context.passcodeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = passcodeDatastoreName,
)

internal class PasscodeDataStore @Inject constructor(
    @Named(passcodeDatastoreName) private val dataStore: DataStore<Preferences>,
    private val encryptData: EncryptData,
    private val decryptData: DecryptData,
) : PasscodeStoreGateway {
    private val failedAttemptsKey = stringPreferencesKey("failedAttemptsKey")
    private val passcodeKey = stringPreferencesKey("passcode")
    private val lockedStateKey = stringPreferencesKey("lockedState")
    private val passcodeEnabledKey = stringPreferencesKey("passcodeEnabled")
    private val passcodeTimeOutKey = stringPreferencesKey("passcodeTimeOutKey")
    private val passcodeLastBackgroundKey = stringPreferencesKey("passcodeLastBackgroundKey")

    override fun monitorFailedAttempts() =
        dataStore.monitor(failedAttemptsKey)
            .map { decryptData(it)?.toIntOrNull() }

    override suspend fun setFailedAttempts(attempts: Int) {
        val encryptedValue = encryptData(attempts.toString()) ?: return
        dataStore.edit {
            it[failedAttemptsKey] = encryptedValue
        }
    }

    override suspend fun setPasscode(passcode: String?) {
        val encryptedValue = encryptData(passcode)
        dataStore.edit {
            if (encryptedValue == null) {
                it.remove(passcodeKey)
            } else {
                it[passcodeKey] = encryptedValue
            }
        }
    }

    override suspend fun getPasscode() = dataStore.monitor(passcodeKey)
        .map { decryptData(it) }
        .first()

    override suspend fun setLockedState(state: Boolean) {
        val encryptedValue = encryptData(state.toString()) ?: return
        dataStore.edit {
            it[lockedStateKey] = encryptedValue
        }
    }

    override fun monitorLockState() =
        dataStore.monitor(lockedStateKey)
            .map { decryptData(it)?.toBooleanStrictOrNull() }

    override suspend fun setPasscodeEnabledState(enabled: Boolean) {
        val encryptedValue = encryptData(enabled.toString()) ?: return
        dataStore.edit {
            it[passcodeEnabledKey] = encryptedValue
        }
    }

    override fun monitorPasscodeEnabledState() =
        dataStore.monitor(passcodeEnabledKey)
            .map { decryptData(it)?.toBooleanStrictOrNull() }

    override suspend fun setPasscodeTimeout(timeOutMilliseconds: Long) {
        val encryptedValue = encryptData(timeOutMilliseconds.toString()) ?: return
        dataStore.edit {
            it[passcodeTimeOutKey] = encryptedValue
        }
    }

    override fun monitorPasscodeTimeOut() = dataStore.monitor(passcodeTimeOutKey)
        .map { decryptData(it)?.toLongOrNull() }

    override suspend fun setLastBackgroundTime(backgroundUTC: Long?) {
        val encryptedValue = encryptData(backgroundUTC?.toString())
        dataStore.edit {
            if (encryptedValue == null) {
                it.remove(passcodeLastBackgroundKey)
            } else {
                it[passcodeLastBackgroundKey] = encryptedValue
            }
        }
    }

    override fun monitorLastBackgroundTime() =
        dataStore.monitor(passcodeLastBackgroundKey)
            .map { decryptData(it)?.toLongOrNull() }
}