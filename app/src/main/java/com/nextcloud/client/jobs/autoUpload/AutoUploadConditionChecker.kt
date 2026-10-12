/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import com.nextcloud.client.device.PowerManagementService
import com.nextcloud.client.jobs.upload.FileUploadHelper
import com.nextcloud.client.network.ConnectivityService
import com.owncloud.android.db.OCUpload
import com.owncloud.android.db.UploadResult

class AutoUploadConditionChecker(
    private val connectivityService: ConnectivityService,
    private val powerManagementService: PowerManagementService
) {
    fun areMet(upload: OCUpload, ignorePowerSaveMode: Boolean): Boolean {
        val connectivity = connectivityService.connectivity
        val result = FileUploadHelper.checkUploadConditions(
            upload,
            connectivity,
            powerManagementService.battery,
            powerManagementService,
            connectivity.isConnected && !connectivityService.isInternetWalled(),
            ignorePowerSaveMode
        )
        return result == UploadResult.UPLOADED
    }
}
