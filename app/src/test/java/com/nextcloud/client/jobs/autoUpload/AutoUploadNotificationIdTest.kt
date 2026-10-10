/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import com.nextcloud.client.notifications.AppNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUploadNotificationIdTest {

    private val otherNotificationIds = listOf(
        AutoUploadLocalDeletionWorker.NOTIFICATION_ID,
        AppNotificationManager.TRANSFER_NOTIFICATION_ID,
        FILES_EXPORT_NOTIFICATION_ID,
        FOLDER_DOWNLOAD_NOTIFICATION_ID,
        SYNC_CONFLICT_NOTIFICATION_ID
    )

    @Test
    fun differentFoldersGetDifferentIds() {
        val ids = (FIRST_FOLDER_ID..LAST_FOLDER_ID).map { AutoUploadNotificationId.forFolder(it) }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun sameFolderGetsSameId() {
        assertEquals(AutoUploadNotificationId.forFolder(FOLDER_ID), AutoUploadNotificationId.forFolder(FOLDER_ID))
    }

    @Test
    fun idsDoNotCollideWithOtherNotifications() {
        val folderIds = (UNKNOWN_FOLDER_ID..LAST_FOLDER_ID) + Long.MAX_VALUE + Long.MIN_VALUE

        folderIds.forEach { folderId ->
            assertFalse(AutoUploadNotificationId.forFolder(folderId) in otherNotificationIds)
        }
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
        const val FILES_EXPORT_NOTIFICATION_ID = 179
        const val FOLDER_DOWNLOAD_NOTIFICATION_ID = 391
        const val SYNC_CONFLICT_NOTIFICATION_ID = 112
    }
}
