/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Alper Ozturk <alper.ozturk@nextcloud.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import android.app.Notification
import android.content.Context
import com.nextcloud.client.jobs.notification.WorkerNotificationManager
import com.owncloud.android.R
import com.owncloud.android.ui.notifications.NotificationUtils
import com.owncloud.android.utils.theme.ViewThemeUtils

class AutoUploadNotificationManager(private val appContext: Context, viewThemeUtils: ViewThemeUtils, id: Int) :
    WorkerNotificationManager(
        id,
        appContext,
        viewThemeUtils,
        tickerId = R.string.foreground_service_upload,
        channelId = NotificationUtils.NOTIFICATION_CHANNEL_UPLOAD
    ) {

    fun progressNotification(progress: AutoUploadProgress): Notification {
        val title = if (progress.total > 1) {
            appContext.getString(
                R.string.upload_notification_manager_start_text,
                progress.done + 1,
                progress.total,
                progress.fileName
            )
        } else {
            progress.fileName
        }

        return notificationBuilder
            .setContentTitle(title)
            .setProgress(progress.total, progress.done, false)
            .setSmallIcon(R.drawable.uploads)
            .setOngoing(true)
            .setSound(null)
            .setVibrate(null)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }
}
