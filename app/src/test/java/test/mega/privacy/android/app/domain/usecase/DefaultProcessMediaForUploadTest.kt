package test.mega.privacy.android.app.domain.usecase

import com.google.common.truth.Truth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mega.privacy.android.app.domain.usecase.DefaultProcessMediaForUpload
import mega.privacy.android.app.domain.usecase.GetCameraUploadSelectionQuery
import mega.privacy.android.app.domain.usecase.ProcessMediaForUpload
import mega.privacy.android.app.domain.usecase.SaveSyncRecordsToDB
import mega.privacy.android.data.wrapper.CameraUploadSyncManagerWrapper
import mega.privacy.android.domain.entity.BackupState
import mega.privacy.android.domain.entity.CameraUploadMedia
import mega.privacy.android.domain.entity.MediaStoreFileType
import mega.privacy.android.domain.entity.SyncTimeStamp
import mega.privacy.android.domain.repository.CameraUploadRepository
import mega.privacy.android.domain.usecase.IsSecondaryFolderEnabled
import mega.privacy.android.domain.usecase.UpdateCameraUploadTimeStamp
import mega.privacy.android.domain.usecase.camerauploads.GetMediaStoreFileTypesUseCase
import mega.privacy.android.domain.usecase.camerauploads.GetPendingUploadListUseCase
import mega.privacy.android.domain.usecase.camerauploads.GetPrimaryFolderPathUseCase
import mega.privacy.android.domain.usecase.camerauploads.GetSecondaryFolderPathUseCase
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.inOrder
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.LinkedList
import java.util.Queue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultProcessMediaForUploadTest {
    private lateinit var underTest: ProcessMediaForUpload

    private val getPrimaryFolderPathUseCase = mock<GetPrimaryFolderPathUseCase>()
    private val getSecondaryFolderPathUseCase = mock<GetSecondaryFolderPathUseCase>()
    private val getMediaStoreFileTypesUseCase = mock<GetMediaStoreFileTypesUseCase>()
    private val isSecondaryFolderEnabled = mock<IsSecondaryFolderEnabled>()
    private val updateTimeStamp = mock<UpdateCameraUploadTimeStamp>()
    private val getPendingUploadListUseCase = mock<GetPendingUploadListUseCase>()
    private val saveSyncRecordsToDB = mock<SaveSyncRecordsToDB>()
    private val cameraUploadSyncManagerWrapper = mock<CameraUploadSyncManagerWrapper>()
    private val cameraUploadRepository = mock<CameraUploadRepository>()
    private val selectionQuery = mock<GetCameraUploadSelectionQuery>()

    @Before
    fun setUp() {
        underTest = DefaultProcessMediaForUpload(
            cameraUploadRepository = cameraUploadRepository,
            getPrimaryFolderPathUseCase = getPrimaryFolderPathUseCase,
            getSecondaryFolderPathUseCase = getSecondaryFolderPathUseCase,
            getMediaStoreFileTypesUseCase = getMediaStoreFileTypesUseCase,
            isSecondaryFolderEnabled = isSecondaryFolderEnabled,
            selectionQuery = selectionQuery,
            updateTimeStamp = updateTimeStamp,
            getPendingUploadListUseCase = getPendingUploadListUseCase,
            saveSyncRecordsToDB = saveSyncRecordsToDB,
            cameraUploadSyncManagerWrapper = cameraUploadSyncManagerWrapper
        )
        runBlocking {
            val queue: Queue<CameraUploadMedia> = LinkedList(
                listOf(
                    mock(),
                    mock()
                )
            )
            SyncTimeStamp.values().forEach {
                whenever(selectionQuery(it)).thenReturn("")
            }
            whenever(getPrimaryFolderPathUseCase()).thenReturn("")
            whenever(getSecondaryFolderPathUseCase()).thenReturn("")
            listOf(
                MediaStoreFileType.IMAGES_INTERNAL,
                MediaStoreFileType.IMAGES_EXTERNAL,
            ).forEach {
                whenever(
                    cameraUploadRepository.getMediaQueue(
                        mediaStoreFileType = eq(it),
                        parentPath = eq(""),
                        isVideo = eq(false),
                        selectionQuery = eq("")
                    )
                ).thenReturn(
                    queue
                )
            }
            listOf(
                MediaStoreFileType.VIDEO_INTERNAL,
                MediaStoreFileType.VIDEO_EXTERNAL
            ).forEach {
                whenever(
                    cameraUploadRepository.getMediaQueue(
                        mediaStoreFileType = eq(it),
                        parentPath = eq(""),
                        isVideo = eq(true),
                        selectionQuery = eq("")
                    )
                ).thenReturn(
                    queue
                )
            }
        }
    }

    @Test
    fun `test that primary photos are prepared when video upload is disabled`() = runTest {
        whenever(getMediaStoreFileTypesUseCase.invoke()).thenReturn(
            listOf(
                MediaStoreFileType.IMAGES_INTERNAL,
                MediaStoreFileType.IMAGES_EXTERNAL,
            )
        )
        whenever(isSecondaryFolderEnabled.invoke()).thenReturn(false)
        val inOrder = inOrder(updateTimeStamp, cameraUploadSyncManagerWrapper)
        underTest(null, null, null)
        inOrder.verify(updateTimeStamp).invoke(null, SyncTimeStamp.PRIMARY_PHOTO)
        inOrder.verify(cameraUploadSyncManagerWrapper)
            .updatePrimaryFolderBackupState(BackupState.ACTIVE)
    }

    @Test
    fun `test that primary and secondary photos are prepared when secondary folder is enabled`() =
        runTest {
            whenever(getMediaStoreFileTypesUseCase.invoke()).thenReturn(
                listOf(
                    MediaStoreFileType.IMAGES_INTERNAL,
                    MediaStoreFileType.IMAGES_EXTERNAL,
                )
            )
            whenever(isSecondaryFolderEnabled.invoke()).thenReturn(true)
            underTest(null, null, null)
            val inOrder = inOrder(updateTimeStamp, cameraUploadSyncManagerWrapper)
            inOrder.verify(updateTimeStamp).invoke(null, SyncTimeStamp.PRIMARY_PHOTO)
            inOrder.verify(updateTimeStamp).invoke(null, SyncTimeStamp.SECONDARY_PHOTO)
            inOrder.verify(cameraUploadSyncManagerWrapper)
                .updateSecondaryFolderBackupState(BackupState.ACTIVE)
        }

    @Test
    fun `test that both primary photos and videos are prepared when video upload is enabled`() =
        runTest {
            whenever(getMediaStoreFileTypesUseCase.invoke()).thenReturn(
                listOf(
                    MediaStoreFileType.IMAGES_INTERNAL,
                    MediaStoreFileType.IMAGES_EXTERNAL,
                    MediaStoreFileType.VIDEO_INTERNAL,
                    MediaStoreFileType.VIDEO_EXTERNAL,
                )
            )
            whenever(isSecondaryFolderEnabled.invoke()).thenReturn(false)
            underTest(null, null, null)
            verify(updateTimeStamp).invoke(null, SyncTimeStamp.PRIMARY_PHOTO)
            verify(updateTimeStamp).invoke(null, SyncTimeStamp.PRIMARY_VIDEO)
        }

    @Test
    fun `test that both primary and secondary photos and videos are prepared when secondary folder upload is enabled`() =
        runTest {
            whenever(getMediaStoreFileTypesUseCase.invoke()).thenReturn(
                listOf(
                    MediaStoreFileType.IMAGES_INTERNAL,
                    MediaStoreFileType.IMAGES_EXTERNAL,
                    MediaStoreFileType.VIDEO_INTERNAL,
                    MediaStoreFileType.VIDEO_EXTERNAL,
                )
            )
            whenever(isSecondaryFolderEnabled.invoke()).thenReturn(true)
            underTest(null, null, null)
            verify(updateTimeStamp).invoke(null, SyncTimeStamp.PRIMARY_PHOTO)
            verify(updateTimeStamp).invoke(null, SyncTimeStamp.PRIMARY_VIDEO)
            verify(updateTimeStamp).invoke(null, SyncTimeStamp.SECONDARY_PHOTO)
            verify(updateTimeStamp).invoke(null, SyncTimeStamp.SECONDARY_VIDEO)
        }

    @Test
    fun `test that cancellation exception is thrown when the operation is cancelled`() =
        runTest {
            val job = launch {
                whenever(getMediaStoreFileTypesUseCase.invoke()).thenReturn(
                    listOf(
                        MediaStoreFileType.IMAGES_INTERNAL,
                        MediaStoreFileType.IMAGES_EXTERNAL,
                        MediaStoreFileType.VIDEO_INTERNAL,
                        MediaStoreFileType.VIDEO_EXTERNAL,
                    )
                )
                getPendingUploadListUseCase.stub {
                    onBlocking { invoke(any(), any(), any()) }.doSuspendableAnswer {
                        delay(2000)
                        return@doSuspendableAnswer emptyList()
                    }
                }
                isSecondaryFolderEnabled.stub {
                    onBlocking { invoke() }.doSuspendableAnswer {
                        delay(3000)
                        return@doSuspendableAnswer true
                    }
                }
                try {
                    underTest(null, null, null)
                } catch (e: Exception) {
                    Truth.assertThat(e).isInstanceOf(CancellationException::class.java)
                }
                verify(saveSyncRecordsToDB, Times(0)).invoke(any(), any(), any(), any())
                verify(saveSyncRecordsToDB, Times(0)).invoke(any(), any(), any(), any())
                verify(cameraUploadSyncManagerWrapper, Times(0)).updatePrimaryFolderBackupState(
                    BackupState.ACTIVE
                )
                verify(cameraUploadSyncManagerWrapper, Times(0)).updateSecondaryFolderBackupState(
                    BackupState.ACTIVE
                )
            }
            runCurrent()
            advanceTimeBy(3000)
            job.cancelAndJoin()
        }
}
