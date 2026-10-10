/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

object AutoUploadNotificationId {
    // Above AppNotificationManager.TRANSFER_NOTIFICATION_ID.
    internal const val BASE = 2_000_000
    internal const val RANGE = 1_000_000

    // WorkManager cancels the notification with the finished worker's id, so folders must not share one.
    fun forFolder(syncedFolderId: Long): Int = BASE + Math.floorMod(syncedFolderId, RANGE.toLong()).toInt()
}
