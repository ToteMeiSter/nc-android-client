/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AutoUploadProgressTrackerTest {

    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()

    @Before
    fun setUp() {
        AutoUploadProgressTracker.reset()
    }

    @After
    fun tearDown() {
        AutoUploadProgressTracker.reset()
    }

    private fun progress(fileFinished: Boolean = false) = AutoUploadProgressTracker.progress(FILE, fileFinished)

    @Test
    fun workersShareOneProgress() {
        AutoUploadProgressTracker.filesFound(first, 3)
        AutoUploadProgressTracker.filesFound(second, 2)
        AutoUploadProgressTracker.fileUploaded(first)
        AutoUploadProgressTracker.fileUploaded(second)

        assertEquals(AutoUploadProgress(done = 2, total = 5, position = 3, fileName = FILE), progress())
    }

    @Test
    fun finishedFileCountsAsDoneInItsOwnUpdate() {
        AutoUploadProgressTracker.filesFound(first, 3)

        assertEquals(AutoUploadProgress(done = 1, total = 3, position = 1, fileName = FILE), progress(true))
    }

    @Test
    fun progressStaysWhileAnotherWorkerIsActive() {
        AutoUploadProgressTracker.filesFound(first, 1)
        AutoUploadProgressTracker.filesFound(second, 1)
        AutoUploadProgressTracker.fileUploaded(first)

        AutoUploadProgressTracker.workerFinished(first)

        assertEquals(AutoUploadProgress(done = 1, total = 2, position = 2, fileName = FILE), progress())
    }

    @Test
    fun progressStartsOverAfterLastWorkerFinished() {
        AutoUploadProgressTracker.filesFound(first, 1)
        AutoUploadProgressTracker.filesFound(second, 1)
        AutoUploadProgressTracker.fileUploaded(first)
        AutoUploadProgressTracker.fileUploaded(second)
        AutoUploadProgressTracker.workerFinished(first)

        AutoUploadProgressTracker.workerFinished(second)

        assertEquals(AutoUploadProgress(done = 0, total = 0, position = 1, fileName = FILE), progress())
    }

    @Test
    fun unprocessedFilesOfStoppedWorkerLeaveTheTotal() {
        AutoUploadProgressTracker.filesFound(first, 4)
        AutoUploadProgressTracker.filesFound(second, 1)
        AutoUploadProgressTracker.fileUploaded(first)

        AutoUploadProgressTracker.workerFinished(first)

        assertEquals(AutoUploadProgress(done = 1, total = 2, position = 2, fileName = FILE), progress())
    }

    @Test
    fun skippedFilesLeaveTheTotalAndDoNotCountAsDone() {
        AutoUploadProgressTracker.filesFound(first, 5)
        AutoUploadProgressTracker.fileSkipped(first)
        AutoUploadProgressTracker.fileSkipped(first)
        AutoUploadProgressTracker.fileUploaded(first)

        assertEquals(AutoUploadProgress(done = 1, total = 3, position = 2, fileName = FILE), progress())
    }

    @Test
    fun replacedRunOfSameFolderDoesNotResetTheRunThatReplacedIt() {
        val replacedRun = UUID.randomUUID()
        val newRun = UUID.randomUUID()
        AutoUploadProgressTracker.filesFound(replacedRun, 2)
        AutoUploadProgressTracker.filesFound(newRun, 3)
        AutoUploadProgressTracker.fileUploaded(newRun)

        AutoUploadProgressTracker.workerFinished(replacedRun)

        assertEquals(AutoUploadProgress(done = 1, total = 3, position = 2, fileName = FILE), progress())
    }

    @Test
    fun workerWithoutFilesChangesNothing() {
        AutoUploadProgressTracker.filesFound(first, 2)

        AutoUploadProgressTracker.workerFinished(second)
        AutoUploadProgressTracker.fileSkipped(second)

        assertEquals(AutoUploadProgress(done = 0, total = 2, position = 1, fileName = FILE), progress())
    }

    private companion object {
        const val FILE = "a.jpg"
    }
}
