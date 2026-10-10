/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import com.nextcloud.client.jobs.upload.FileUploadWorker
import com.nextcloud.client.notifications.AppNotificationManager
import com.owncloud.android.operations.UploadFileOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUploadNotificationIdTest {

    @Test
    fun differentFoldersGetDifferentIds() {
        val ids = (FIRST_FOLDER_ID..LAST_FOLDER_ID).map { AutoUploadNotificationId.forFolder(it) }

        assertEquals(LAST_FOLDER_ID.toInt(), ids.toSet().size)
    }

    @Test
    fun sameFolderGetsSameId() {
        assertEquals(AutoUploadNotificationId.forFolder(FOLDER_ID), AutoUploadNotificationId.forFolder(FOLDER_ID))
    }

    @Test
    fun idsStayInReservedRange() {
        val folderIds = listOf(UNKNOWN_FOLDER_ID, FOLDER_ID, Long.MAX_VALUE, Long.MIN_VALUE)

        folderIds.forEach { folderId ->
            val id = AutoUploadNotificationId.forFolder(folderId)
            assertTrue(id >= AutoUploadNotificationId.BASE)
            assertTrue(id < AutoUploadNotificationId.BASE + AutoUploadNotificationId.RANGE)
        }
    }

    @Test
    fun rangeIsAboveOtherNotificationIds() {
        val otherNotificationIds = listOf(
            AppNotificationManager.TRANSFER_NOTIFICATION_ID,
            FileUploadWorker.NOTIFICATION_ERROR_ID,
            UploadFileOperation.MISSING_FILE_PERMISSION_NOTIFICATION_ID,
            AutoUploadLocalDeletionWorker.NOTIFICATION_ID
        )

        assertTrue(otherNotificationIds.all { it < AutoUploadNotificationId.BASE })
    }

    @Test
    fun unknownFolderDoesNotShareIdWithFirstFolder() {
        assertNotEquals(
            AutoUploadNotificationId.forFolder(UNKNOWN_FOLDER_ID),
            AutoUploadNotificationId.forFolder(FIRST_FOLDER_ID)
        )
    }

    private companion object {
        const val UNKNOWN_FOLDER_ID = -1L
        const val FIRST_FOLDER_ID = 1L
        const val LAST_FOLDER_ID = 500L
        const val FOLDER_ID = 42L
    }
}
