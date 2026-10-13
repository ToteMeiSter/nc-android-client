/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import java.util.UUID

/**
 * Progress of all auto upload runs that work at the same time, so that they share one notification.
 * A run is identified by its worker id because a replaced run of the same folder can overlap with its successor.
 * The counters start from zero again when the last active run has finished.
 */
object AutoUploadProgressTracker {
    private val lock = Any()
    private val pendingByWorker = mutableMapOf<UUID, Int>()
    private var total = 0
    private var done = 0

    fun filesFound(workerId: UUID, fileCount: Int) = synchronized(lock) {
        pendingByWorker[workerId] = (pendingByWorker[workerId] ?: 0) + fileCount
        total += fileCount
    }

    fun fileUploaded(workerId: UUID) = synchronized(lock) {
        if (takePending(workerId)) {
            done++
        }
    }

    fun fileSkipped(workerId: UUID) = synchronized(lock) {
        if (takePending(workerId)) {
            total--
        }
    }

    fun progress(fileName: String, fileFinished: Boolean): AutoUploadProgress = synchronized(lock) {
        val doneIncludingFile = if (fileFinished) done + 1 else done
        AutoUploadProgress(done = doneIncludingFile, total = total, position = done + 1, fileName = fileName)
    }

    fun workerFinished(workerId: UUID) = synchronized(lock) {
        val leftover = pendingByWorker.remove(workerId) ?: return@synchronized
        total -= leftover
        if (pendingByWorker.isEmpty()) {
            total = 0
            done = 0
        }
    }

    internal fun reset() = synchronized(lock) {
        pendingByWorker.clear()
        total = 0
        done = 0
    }

    private fun takePending(workerId: UUID): Boolean {
        val pending = pendingByWorker[workerId] ?: return false
        pendingByWorker[workerId] = pending - 1
        return true
    }
}
