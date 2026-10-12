/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoUploadProgressTrackerTest {

    @Before
    fun setUp() {
        AutoUploadProgressTracker.reset()
    }

    @After
    fun tearDown() {
        AutoUploadProgressTracker.reset()
    }

    @Test
    fun foldersShareOneProgress() {
        AutoUploadProgressTracker.filesFound(FIRST_FOLDER, 3)
        AutoUploadProgressTracker.filesFound(SECOND_FOLDER, 2)
        AutoUploadProgressTracker.fileProcessed(FIRST_FOLDER)
        AutoUploadProgressTracker.fileProcessed(SECOND_FOLDER)

        assertEquals(AutoUploadProgress(done = 2, total = 5, fileName = FILE), AutoUploadProgressTracker.progress(FILE))
    }

    @Test
    fun notificationStaysWhileAnotherFolderIsActive() {
        AutoUploadProgressTracker.filesFound(FIRST_FOLDER, 1)
        AutoUploadProgressTracker.filesFound(SECOND_FOLDER, 1)
        AutoUploadProgressTracker.fileProcessed(FIRST_FOLDER)

        val noneLeft = AutoUploadProgressTracker.folderFinished(FIRST_FOLDER)

        assertFalse(noneLeft)
        assertEquals(AutoUploadProgress(done = 1, total = 2, fileName = FILE), AutoUploadProgressTracker.progress(FILE))
    }

    @Test
    fun progressStartsOverAfterLastFolderFinished() {
        AutoUploadProgressTracker.filesFound(FIRST_FOLDER, 1)
        AutoUploadProgressTracker.filesFound(SECOND_FOLDER, 1)
        AutoUploadProgressTracker.fileProcessed(FIRST_FOLDER)
        AutoUploadProgressTracker.fileProcessed(SECOND_FOLDER)
        AutoUploadProgressTracker.folderFinished(FIRST_FOLDER)

        val noneLeft = AutoUploadProgressTracker.folderFinished(SECOND_FOLDER)

        assertTrue(noneLeft)
        assertEquals(AutoUploadProgress(done = 0, total = 0, fileName = FILE), AutoUploadProgressTracker.progress(FILE))
    }

    @Test
    fun unprocessedFilesOfStoppedFolderLeaveTheTotal() {
        AutoUploadProgressTracker.filesFound(FIRST_FOLDER, 4)
        AutoUploadProgressTracker.filesFound(SECOND_FOLDER, 1)
        AutoUploadProgressTracker.fileProcessed(FIRST_FOLDER)

        AutoUploadProgressTracker.folderFinished(FIRST_FOLDER)

        assertEquals(AutoUploadProgress(done = 1, total = 2, fileName = FILE), AutoUploadProgressTracker.progress(FILE))
    }

    @Test
    fun folderWithoutFilesDoesNotKeepNotificationAlive() {
        assertTrue(AutoUploadProgressTracker.folderFinished(FIRST_FOLDER))
    }

    private companion object {
        const val FIRST_FOLDER = 1L
        const val SECOND_FOLDER = 2L
        const val FILE = "a.jpg"
    }
}
