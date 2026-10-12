/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import androidx.work.ForegroundInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AutoUploadProgressNotifierTest {

    private val notificationManager: AutoUploadNotificationManager = mockk(relaxed = true)
    private val foregroundInfo: ForegroundInfo = mockk()
    private val workerId = UUID.randomUUID()
    private var foregroundCalls = 0
    private var now = 0L

    private val notifier = AutoUploadProgressNotifier(
        notificationManager,
        setForeground = { foregroundCalls++ },
        currentTimeMs = { now }
    )

    @Before
    fun setUp() {
        AutoUploadProgressTracker.reset()
        AutoUploadProgressTracker.filesFound(workerId, FILE_COUNT)
        every { notificationManager.getForegroundInfo(any()) } returns foregroundInfo
    }

    @After
    fun tearDown() {
        AutoUploadProgressTracker.reset()
    }

    @Test
    fun firstUpdateGoesToForegroundOnce() = runBlocking<Unit> {
        notifier.fileStarted(FILE)
        now += AFTER_INTERVAL_MS
        notifier.fileFinished(FILE)

        assertEquals(1, foregroundCalls)
    }

    @Test
    fun updatesInsideTheIntervalAreDropped() = runBlocking<Unit> {
        notifier.fileStarted(FILE)

        notifier.fileFinished(FILE)

        verify(exactly = 0) { notificationManager.showNotification(any()) }
    }

    @Test
    fun finishedFileUpdatesTheNotificationAfterTheInterval() = runBlocking<Unit> {
        notifier.fileStarted(FILE)
        now += AFTER_INTERVAL_MS

        notifier.fileFinished(FILE)

        verify(exactly = 1) { notificationManager.showNotification(any()) }
    }

    @Test
    fun cancelledWorkerDoesNotPostNotification() = runBlocking<Unit> {
        val job = launch {
            notifier.fileStarted(FILE)
            now += AFTER_INTERVAL_MS
            currentCoroutineContext().cancel()
            notifier.fileFinished(FILE)
        }
        job.join()

        assertEquals(1, foregroundCalls)
        verify(exactly = 0) { notificationManager.showNotification(any()) }
    }

    private companion object {
        const val FILE = "a.jpg"
        const val FILE_COUNT = 3
        const val AFTER_INTERVAL_MS = 1_500L
    }
}
