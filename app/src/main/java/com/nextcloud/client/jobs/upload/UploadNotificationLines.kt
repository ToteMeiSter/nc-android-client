/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.upload

import android.content.Context
import com.owncloud.android.R

class UploadNotificationLines(private val context: Context) {

    fun build(albums: List<UploadAlbum>, uploadedTotal: Int, grandTotal: Int): List<String> {
        val left = (grandTotal - uploadedTotal).coerceAtLeast(0)
        val summary = context.resources.getQuantityString(
            R.plurals.upload_notification_total,
            left,
            uploadedTotal,
            grandTotal,
            left
        )
        return listOf(summary) + albums.map { it.toLine() }
    }

    private fun UploadAlbum.toLine(): String = if (localFolderName == null) {
        context.getString(R.string.upload_notification_folder_line, remoteFolderPath, uploaded, total)
    } else {
        context.getString(R.string.upload_notification_album_line, localFolderName, remoteFolderPath, uploaded, total)
    }
}
