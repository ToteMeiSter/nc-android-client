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
import androidx.work.ForegroundUpdater
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import com.nextcloud.client.account.User
import com.nextcloud.client.account.UserAccountManager
import com.nextcloud.client.jobs.upload.FileUploadHelper
import com.owncloud.android.datamodel.MediaFolderType
import com.owncloud.android.datamodel.SyncedFolder
import com.owncloud.android.datamodel.SyncedFolderProvider
import com.owncloud.android.lib.common.OwnCloudAccount
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.OwnCloudClientManager
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
import java.util.Optional
import java.util.UUID

class AutoUploadWorkerForegroundTest {

    private val foregroundUpdater: ForegroundUpdater = mockk()
    private val systemNotificationManager: NotificationManager = mockk(relaxed = true)
    private val repository: FileSystemRepository = mockk(relaxed = true)
    private val syncedFolder: SyncedFolder = mockk(relaxed = true)
    private val syncedFolderProvider: SyncedFolderProvider = mockk(relaxed = true)
    private val userAccountManager: UserAccountManager = mockk {
        every { getUser(any()) } returns Optional.empty<User>()
    }

    private lateinit var worker: AutoUploadWorker

    @Before
    fun setUp() {
        mockkObject(FileUploadHelper.Companion)
        every { FileUploadHelper.instance() } returns mockk(relaxed = true)
        mockNotificationBuilder()

        val doneFuture: ListenableFuture<Void> = mockk {
            every { isDone } returns true
            every { get() } returns null
        }
        every { foregroundUpdater.setForegroundAsync(any(), any(), any()) } returns doneFuture

        every { syncedFolder.isEnabled } returns true
        every { syncedFolder.type } returns MediaFolderType.IMAGE
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
            connectivityService = mockk(relaxed = true),
            powerManagementService = mockk(relaxed = true),
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
    fun runWithinScanIntervalWithoutPendingFilesDoesNotGoToForeground() = runBlocking {
        coEvery { repository.hasPendingFiles(syncedFolder) } returns false

        worker.doWork()

        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    @Test
    fun runAfterScanIntervalWithoutPendingFilesDoesNotGoToForeground() = runBlocking {
        every { syncedFolder.getTotalScanInterval(any(), any()) } returns 0L
        coEvery { repository.hasPendingFiles(syncedFolder) } returns false

        worker.doWork()

        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    @Test
    fun runAfterScanIntervalWithUserAndNothingToUploadDoesNotGoToForeground() = runBlocking {
        every { syncedFolder.getTotalScanInterval(any(), any()) } returns 0L
        coEvery { repository.hasPendingFiles(syncedFolder) } returns false
        mockUploadEnvironment()

        worker.doWork()

        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    @Test
    fun runWithOnlySkippedFilesDoesNotGoToForeground() = runBlocking {
        coEvery { repository.hasPendingFiles(syncedFolder) } returns true
        coEvery { repository.getFilePathsWithIds(syncedFolder, 0) } returns listOf(MISSING_FILE to 1)
        coEvery { repository.getFilePathsWithIds(syncedFolder, 1) } returns emptyList()
        mockUploadEnvironment()

        worker.doWork()

        coVerify { repository.markFileAsHandled(MISSING_FILE, syncedFolder) }
        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    @Test
    fun runRemovesNotificationLeftByEarlierRun() = runBlocking {
        coEvery { repository.hasPendingFiles(syncedFolder) } returns false

        worker.doWork()

        verify { systemNotificationManager.cancel(AutoUploadWorker.NOTIFICATION_ID) }
    }

    @Test
    fun differentFoldersUseTheSameNotificationId() = runBlocking {
        val otherWorker = createWorker(OTHER_FOLDER_ID)

        val ids = listOf(worker, otherWorker).map { it.getForegroundInfo().notificationId }

        assertEquals(listOf(AutoUploadWorker.NOTIFICATION_ID, AutoUploadWorker.NOTIFICATION_ID), ids)
    }

    private fun mockUploadEnvironment() {
        val user: User = mockk(relaxed = true)
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
    }

    private companion object {
        const val FOLDER_ID = 7L
        const val OTHER_FOLDER_ID = 8L
        const val MISSING_FILE = "/does/not/exist.jpg"
    }
}
