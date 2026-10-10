/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundUpdater
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import com.nextcloud.client.account.User
import com.nextcloud.client.jobs.upload.FileUploadHelper
import com.owncloud.android.datamodel.MediaFolderType
import com.owncloud.android.datamodel.SyncedFolder
import com.owncloud.android.datamodel.SyncedFolderProvider
import io.mockk.MockKAnswerScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Optional
import java.util.UUID

class AutoUploadWorkerForegroundTest {

    private val foregroundUpdater: ForegroundUpdater = mockk()
    private val repository: FileSystemRepository = mockk(relaxed = true)
    private val syncedFolder: SyncedFolder = mockk(relaxed = true)
    private val syncedFolderProvider: SyncedFolderProvider = mockk(relaxed = true)

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

        val params: WorkerParameters = mockk(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { inputData } returns workDataOf(AutoUploadWorker.SYNCED_FOLDER_ID to FOLDER_ID)
            every { foregroundUpdater } returns this@AutoUploadWorkerForegroundTest.foregroundUpdater
        }
        val context: Context = mockk(relaxed = true) {
            every { getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
        }

        every { syncedFolder.isEnabled } returns true
        every { syncedFolder.type } returns MediaFolderType.IMAGE
        every { syncedFolder.getTotalScanInterval(any(), any()) } returns Long.MAX_VALUE
        every { syncedFolderProvider.getSyncedFolderByID(FOLDER_ID) } returns syncedFolder

        worker = AutoUploadWorker(
            context = context,
            params = params,
            userAccountManager = mockk {
                every { getUser(any()) } returns Optional.empty<User>()
            },
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
    fun runWithNothingToUploadDoesNotGoToForeground() = runBlocking {
        coEvery { repository.hasPendingFiles(syncedFolder) } returns false

        worker.doWork()

        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    @Test
    fun runWithPendingFilesGoesToForeground() = runBlocking {
        coEvery { repository.hasPendingFiles(syncedFolder) } returns true

        worker.doWork()

        verify(exactly = 1) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
    }

    private companion object {
        const val FOLDER_ID = 7L
    }
}
