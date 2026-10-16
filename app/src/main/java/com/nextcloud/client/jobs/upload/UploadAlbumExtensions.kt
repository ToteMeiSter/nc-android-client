/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.upload

import com.owncloud.android.db.OCUpload
import com.owncloud.android.operations.UploadFileOperation
import java.io.File

private data class AlbumKey(val localFolderName: String?, val remoteFolderPath: String)

/**
 * Groups [this] batch into albums in order of first appearance. Uploads before [processedCount] count as uploaded.
 */
fun List<OCUpload>.toAlbums(processedCount: Int): List<UploadAlbum> {
    val total = LinkedHashMap<AlbumKey, Int>()
    val uploaded = HashMap<AlbumKey, Int>()

    forEachIndexed { index, upload ->
        val key = upload.albumKey()
        total[key] = (total[key] ?: 0) + 1
        if (index < processedCount) {
            uploaded[key] = (uploaded[key] ?: 0) + 1
        }
    }

    return total.map { (key, count) ->
        UploadAlbum(key.localFolderName, key.remoteFolderPath, count, uploaded[key] ?: 0)
    }
}

private fun OCUpload.albumKey(): AlbumKey {
    val isAutoUpload = createdBy == UploadFileOperation.CREATED_AS_INSTANT_PICTURE ||
        createdBy == UploadFileOperation.CREATED_AS_INSTANT_VIDEO
    val localFolderName = if (isAutoUpload) File(localPath).parentFile?.name else null
    val remoteFolderPath = remotePath.substringBeforeLast('/', "").ifEmpty { "/" }
    return AlbumKey(localFolderName, remoteFolderPath)
}
