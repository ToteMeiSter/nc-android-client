/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.upload

data class UploadAlbum(val localFolderName: String?, val remoteFolderPath: String, val total: Int, val uploaded: Int)
