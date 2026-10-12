/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.autoUpload

import com.nextcloud.client.database.entity.UploadEntity
import com.nextcloud.client.device.PowerManagementService
import com.nextcloud.client.jobs.upload.FileUploadHelper
import com.nextcloud.client.network.ConnectivityService
import com.owncloud.android.datamodel.UploadsStorageManager
import com.owncloud.android.db.OCUpload
import com.owncloud.android.db.UploadResult

class AutoUploadConditionChecker(
    private val connectivityService: ConnectivityService,
    private val powerManagementService: PowerManagementService,
    private val uploadsStorageManager: UploadsStorageManager
) {
    fun unmetCondition(upload: OCUpload, ignorePowerSaveMode: Boolean): UploadResult? {
        val connectivity = connectivityService.connectivity
        val result = FileUploadHelper.checkUploadConditions(
            upload,
            connectivity,
            powerManagementService.battery,
            powerManagementService,
            connectivity.isConnected && !connectivityService.isInternetWalled(),
            ignorePowerSaveMode
        )
        return result.takeIf { it != UploadResult.UPLOADED }
    }

    // The same final state that UploadsStorageManager.updateDatabaseUploadResult gives a delayed upload.
    fun recordDelayed(entity: UploadEntity, upload: OCUpload, reason: UploadResult) {
        val uploadId = uploadsStorageManager.uploadDao.insertOrReplace(entity)
        uploadsStorageManager.updateUploadStatus(
            uploadId,
            UploadsStorageManager.UploadStatus.UPLOAD_FAILED,
            reason,
            upload.remotePath,
            null
        )
    }
}
