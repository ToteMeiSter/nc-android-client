/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import android.app.Notification
import androidx.work.ForegroundInfo
import com.owncloud.android.lib.common.utils.Log_OC
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Shows the shared auto upload notification for one worker. The worker goes to the foreground with the first
 * update, so a run without any upload never shows a notification.
 */
class AutoUploadProgressNotifier(
    private val notificationManager: AutoUploadNotificationManager,
    private val setForeground: suspend (ForegroundInfo) -> Unit,
    private val currentTimeMs: () -> Long = System::currentTimeMillis
) {
    private var foregroundStarted = false
    private var lastUpdateMs = 0L

    suspend fun fileStarted(fileName: String) = show(AutoUploadProgressTracker.progress(fileName, false))

    suspend fun fileFinished(fileName: String) = show(AutoUploadProgressTracker.progress(fileName, true))

    // setForeground of a worker that does not own the foreground service shows the notification of the owner, so
    // the next update has to bring the text of the shared progress back.
    private suspend fun show(progress: AutoUploadProgress) {
        val now = currentTimeMs()
        val throttled = foregroundStarted && now - lastUpdateMs < MIN_UPDATE_INTERVAL_MS
        if (!currentCoroutineContext().isActive || throttled) {
            return
        }

        val notification = notificationManager.progressNotification(progress)
        if (foregroundStarted) {
            notificationManager.showNotification(notification)
            lastUpdateMs = now
        } else {
            startForeground(notification, now)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun startForeground(notification: Notification, now: Long) {
        try {
            setForeground(notificationManager.getForegroundInfo(notification))
            foregroundStarted = true
            lastUpdateMs = now
        } catch (e: Exception) {
            Log_OC.w(AutoUploadWorker.TAG, "⚠️ Could not set foreground service: ${e.message}")
        }
    }

    private companion object {
        const val MIN_UPDATE_INTERVAL_MS = 1_000L
    }
}
