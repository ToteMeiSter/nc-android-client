/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import android.accounts.AccountManager
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import com.nextcloud.client.account.User
import com.nextcloud.client.account.UserAccountManager
import com.nextcloud.client.database.NextcloudDatabase
import com.nextcloud.client.device.BatteryStatus
import com.nextcloud.client.device.PowerManagementService
import com.nextcloud.client.jobs.upload.FileUploadHelper
import com.nextcloud.client.network.Connectivity
import com.nextcloud.client.network.ConnectivityService
import com.owncloud.android.MainApp
import com.owncloud.android.datamodel.MediaFolderType
import com.owncloud.android.datamodel.SyncedFolder
import com.owncloud.android.datamodel.SyncedFolderProvider
import com.owncloud.android.lib.common.OwnCloudAccount
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.OwnCloudClientManager
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.operations.UploadFileOperation
import com.owncloud.android.lib.common.OwnCloudClientManagerFactory
import com.owncloud.android.utils.theme.CapabilityUtils
import io.mockk.MockKAnswerScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Optional
import java.util.UUID

@Suppress("TooManyFunctions")
class AutoUploadWorkerForegroundTest {

    private val foregroundUpdater: ForegroundUpdater = mockk()
    private val systemNotificationManager: NotificationManager = mockk(relaxed = true)
    private val repository: FileSystemRepository = mockk(relaxed = true)
    private val syncedFolder: SyncedFolder = mockk(relaxed = true)
    private val syncedFolderProvider: SyncedFolderProvider = mockk(relaxed = true)
    private val connectivityService: ConnectivityService = mockk(relaxed = true)
    private val powerManagementService: PowerManagementService = mockk(relaxed = true)
    private val uploadHelper: FileUploadHelper = mockk(relaxed = true)
    private val foregroundInfos = mutableListOf<ForegroundInfo>()
    private val userAccountManager: UserAccountManager = mockk {
        every { getUser(any()) } returns Optional.empty<User>()
    }

    private lateinit var worker: AutoUploadWorker

    @Before
    fun setUp() {
        mockkObject(FileUploadHelper.Companion)
        every { FileUploadHelper.instance() } returns uploadHelper
        mockNotificationBuilder()

        val doneFuture: ListenableFuture<Void> = mockk {
            every { isDone } returns true
            every { get() } returns null
        }
        every { foregroundUpdater.setForegroundAsync(any(), any(), capture(foregroundInfos)) } returns doneFuture
        AutoUploadProgressTracker.reset()

        every { syncedFolder.isEnabled } returns true
        every { syncedFolder.type } returns MediaFolderType.IMAGE
        every { syncedFolder.localPath } returns "/storage/emulated/0/DCIM/Camera"
        every { syncedFolder.remotePath } returns "/InstantUpload/Camera"
        every { syncedFolder.getTotalScanInterval(any(), any()) } returns Long.MAX_VALUE
        worker = createWorker(FOLDER_ID)
    }

    private fun createWorker(folderId: Long): AutoUploadWorker {
        val params: WorkerParameters = mockk(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { inputData } returns workDataOf(AutoUploadWorker.SYNCED_FOLDER_ID to folderId)
            every { foregroundUpdater } returns this@AutoUploadWorkerForegroundTest.foregroundUpdater
        }
        val context: Context = mockk(relaxed = true) {
            every { getSystemService(Context.NOTIFICATION_SERVICE) } returns systemNotificationManager
        }
        every { syncedFolderProvider.getSyncedFolderByID(folderId) } returns syncedFolder

        return AutoUploadWorker(
            context = context,
            params = params,
            userAccountManager = userAccountManager,
            uploadsStorageManager = mockk(relaxed = true),
            connectivityService = connectivityService,
            powerManagementService = powerManagementService,
            syncedFolderProvider = syncedFolderProvider,
            repository = repository,
            viewThemeUtils = mockk(relaxed = true),
            localBroadcastManager = mockk(relaxed = true),
            autoUploadHelper = mockk(relaxed = true),
            preferences = mockk(relaxed = true)
        )
    }

    // The android.jar stubs cannot build a real notification, and a constructor mock skips the field setup the
    // builder setters rely on.
    private fun mockNotificationBuilder() {
        mockkConstructor(NotificationCompat.Builder::class)
        every { anyConstructed<NotificationCompat.Builder>().setTicker(any<CharSequence>()) } answers { builder() }
        every { anyConstructed<NotificationCompat.Builder>().setSmallIcon(any<Int>()) } answers { builder() }
        every { anyConstructed<NotificationCompat.Builder>().setSound(any<Uri>()) } answers { builder() }
        every { anyConstructed<NotificationCompat.Builder>().setVibrate(any()) } answers { builder() }
        every { anyConstructed<NotificationCompat.Builder>().setOnlyAlertOnce(any()) } answers { builder() }
        every { anyConstructed<NotificationCompat.Builder>().setOngoing(any()) } answers { builder() }
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk()
    }

    private fun MockKAnswerScope<NotificationCompat.Builder, *>.builder() = self as NotificationCompat.Builder

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun runWithinScanIntervalWithoutPendingFilesDoesNotGoToForeground() = runBlocking<Unit> {
        coEvery { repository.hasPendingFiles(syncedFolder) } returns false

        worker.doWork()

        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    @Test
    fun runAfterScanIntervalWithoutPendingFilesDoesNotGoToForeground() = runBlocking<Unit> {
        every { syncedFolder.getTotalScanInterval(any(), any()) } returns 0L
        coEvery { repository.hasPendingFiles(syncedFolder) } returns false

        worker.doWork()

        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    @Test
    fun runAfterScanIntervalWithUserAndNothingToUploadDoesNotGoToForeground() = runBlocking<Unit> {
        every { syncedFolder.getTotalScanInterval(any(), any()) } returns 0L
        coEvery { repository.hasPendingFiles(syncedFolder) } returns false
        mockUploadEnvironment()

        worker.doWork()

        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    @Test
    fun runWithOnlySkippedFilesDoesNotGoToForeground() = runBlocking<Unit> {
        coEvery { repository.hasPendingFiles(syncedFolder) } returns true
        coEvery { repository.getFilePathsWithIds(syncedFolder, 0) } returns listOf(MISSING_FILE to 1)
        coEvery { repository.getFilePathsWithIds(syncedFolder, 1) } returns emptyList()
        mockUploadEnvironment()

        worker.doWork()

        coVerify { repository.deleteByLocalPathAndId(MISSING_FILE, 1) }
        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    @Test
    fun runRemovesNotificationLeftByEarlierRun() = runBlocking<Unit> {
        coEvery { repository.hasPendingFiles(syncedFolder) } returns false

        worker.doWork()

        verify { systemNotificationManager.cancel(AutoUploadWorker.NOTIFICATION_ID) }
    }

    @Test
    fun differentFoldersUseTheSameNotificationIdWhenUploading() = runBlocking<Unit> {
        val otherWorker = createWorker(OTHER_FOLDER_ID)
        mockUploadEnvironment()
        val file = givenFileToUpload()

        worker.doWork()
        otherWorker.doWork()

        assertEquals(
            listOf(AutoUploadWorker.NOTIFICATION_ID, AutoUploadWorker.NOTIFICATION_ID),
            foregroundInfos.map { it.notificationId }
        )
        file.delete()
    }

    @Test
    fun runWithFileToUploadGoesToForegroundOnce() = runBlocking<Unit> {
        mockUploadEnvironment()
        val files = listOf(givenFileToUpload(), givenFileToUpload())
        coEvery { repository.getFilePathsWithIds(syncedFolder, 0) } returns
            files.mapIndexed { index, file -> file.absolutePath to index + 1 }
        coEvery { repository.getFilePathsWithIds(syncedFolder, 2) } returns emptyList()

        worker.doWork()

        verify(exactly = 1) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
        files.forEach { it.delete() }
    }

    @Test
    fun runWithUnmetUploadConditionsDoesNotGoToForeground() = runBlocking<Unit> {
        mockUploadEnvironment()
        every { syncedFolder.isWifiOnly } returns true
        every { connectivityService.connectivity } returns Connectivity(isConnected = true, isMetered = true)
        val file = givenFileToUpload()

        worker.doWork()

        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
        coVerify(exactly = 0) { repository.markFileAsHandled(file.absolutePath, syncedFolder) }
        file.delete()
    }

    private fun givenFileToUpload(): File {
        val file = File.createTempFile("auto-upload", ".mp4").apply { writeText("content") }
        coEvery { repository.hasPendingFiles(syncedFolder) } returns true
        coEvery { repository.getFilePathsWithIds(syncedFolder, 0) } returns listOf(file.absolutePath to 1)
        coEvery { repository.getFilePathsWithIds(syncedFolder, 1) } returns emptyList()
        return file
    }

    private fun mockUploadEnvironment() {
        val user: User = mockk(relaxed = true) {
            every { accountName } returns ACCOUNT_NAME
        }
        every { userAccountManager.getUser(any()) } returns Optional.of(user)
        every { syncedFolder.type } returns MediaFolderType.VIDEO
        mockkStatic(AccountManager::class)
        every { AccountManager.get(any()) } returns mockk(relaxed = true)
        mockkStatic(OwnCloudClientManagerFactory::class)
        every { OwnCloudClientManagerFactory.getDefaultSingleton() } returns object : OwnCloudClientManager() {
            override fun getClientFor(account: OwnCloudAccount?, context: Context?): OwnCloudClient? = null
        }
        mockkStatic(CapabilityUtils::class)
        every { CapabilityUtils.getCapability(any<User>(), any()) } returns mockk(relaxed = true)
        every { CapabilityUtils.getCapability(any<Context>()) } returns mockk(relaxed = true)
        every { uploadHelper.getUploadByPaths(any(), any(), any()) } returns null
        every { connectivityService.connectivity } returns Connectivity.CONNECTED_WIFI
        every { powerManagementService.battery } returns BatteryStatus()
        mockkStatic(MainApp::class)
        every { MainApp.getAppContext() } returns mockk(relaxed = true)
        mockkObject(NextcloudDatabase.Companion)
        every { NextcloudDatabase.getInstance(any<Context>()) } returns mockk(relaxed = true)
        every { NextcloudDatabase.instance() } returns mockk(relaxed = true)
        mockkConstructor(UploadFileOperation::class)
        every { anyConstructed<UploadFileOperation>().execute(isNull<OwnCloudClient>()) } returns
            RemoteOperationResult<Any>(RemoteOperationResult.ResultCode.OK)
    }

    private companion object {
        const val FOLDER_ID = 7L
        const val ACCOUNT_NAME = "user@cloud.example"
        const val OTHER_FOLDER_ID = 8L
        const val MISSING_FILE = "/does/not/exist.jpg"
    }
}
