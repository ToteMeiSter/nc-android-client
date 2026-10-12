/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

/**
 * Progress of all auto upload folders that run at the same time, so that they share one notification.
 * The counters start from zero again when the last active folder has finished.
 */
object AutoUploadProgressTracker {
    private val lock = Any()
    private val pendingByFolder = mutableMapOf<Long, Int>()
    private var total = 0
    private var done = 0

    fun filesFound(folderId: Long, fileCount: Int) = synchronized(lock) {
        pendingByFolder[folderId] = (pendingByFolder[folderId] ?: 0) + fileCount
        total += fileCount
    }

    fun fileProcessed(folderId: Long) = synchronized(lock) {
        val pending = pendingByFolder[folderId] ?: return@synchronized
        pendingByFolder[folderId] = pending - 1
        done++
    }

    fun progress(fileName: String): AutoUploadProgress = synchronized(lock) {
        AutoUploadProgress(done = done, total = total, fileName = fileName)
    }

    /** Returns true when no other folder is active any more. */
    fun folderFinished(folderId: Long): Boolean = synchronized(lock) {
        val leftover = pendingByFolder.remove(folderId) ?: return@synchronized pendingByFolder.isEmpty()
        total -= leftover
        if (pendingByFolder.isEmpty()) {
            total = 0
            done = 0
        }
        pendingByFolder.isEmpty()
    }

    internal fun reset() = synchronized(lock) {
        pendingByFolder.clear()
        total = 0
        done = 0
    }
}
