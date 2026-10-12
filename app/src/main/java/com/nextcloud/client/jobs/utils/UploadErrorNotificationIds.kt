/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.utils

import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode

/**
 * IDs for upload error notifications that are not tied to one file.
 *
 * The range lies above the small database-backed IDs (file IDs, upload IDs, the fixed worker IDs) and below
 * the resource IDs used by some legacy notifications, so no fixed ID in the app falls inside it.
 * The ID is derived from a [String] hash, which the JVM specifies, so it is the same across runs and processes.
 */
object UploadErrorNotificationIds {
    const val RANGE_START = 1_500_000_000
    const val RANGE_SIZE = 1_000_000

    fun forGeneralError(accountName: String, code: ResultCode): Int =
        RANGE_START + Math.floorMod("$accountName|${code.name}".hashCode(), RANGE_SIZE)
}
