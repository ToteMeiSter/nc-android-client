/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.upload

import android.content.Context
import android.content.res.Resources
import com.owncloud.android.R
import com.owncloud.android.db.OCUpload
import com.owncloud.android.operations.UploadFileOperation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UploadAlbumsTest {

    private val account = "user@cloud"

    private fun autoUpload(local: String, remote: String) = OCUpload(local, remote, account).apply {
        createdBy = UploadFileOperation.CREATED_AS_INSTANT_PICTURE
    }

    private fun manualUpload(local: String, remote: String) = OCUpload(local, remote, account)

    @Test
    fun singleAlbumCountsUploadedAndTotal() {
        val uploads = (1..4).map { autoUpload("/sdcard/DCIM/Camera/$it.jpg", "/Photos/Camera/$it.jpg") }

        val albums = uploads.toAlbums(processedCount = 1)

        assertEquals(listOf(UploadAlbum("Camera", "/Photos/Camera", total = 4, uploaded = 1)), albums)
    }

    @Test
    fun twoAlbumsKeepOwnCountersInOrderOfAppearance() {
        val uploads = listOf(
            autoUpload("/sdcard/DCIM/Camera/1.jpg", "/Photos/Camera/1.jpg"),
            autoUpload("/sdcard/Pictures/Telegram/2.jpg", "/Photos/Telegram/2.jpg"),
            autoUpload("/sdcard/DCIM/Camera/3.jpg", "/Photos/Camera/3.jpg"),
            autoUpload("/sdcard/Pictures/Telegram/4.jpg", "/Photos/Telegram/4.jpg"),
            autoUpload("/sdcard/Pictures/Telegram/5.jpg", "/Photos/Telegram/5.jpg")
        )

        val albums = uploads.toAlbums(processedCount = 3)

        assertEquals(
            listOf(
                UploadAlbum("Camera", "/Photos/Camera", total = 2, uploaded = 2),
                UploadAlbum("Telegram", "/Photos/Telegram", total = 3, uploaded = 1)
            ),
            albums
        )
    }

    @Test
    fun manualUploadUsesTargetFolderOnly() {
        val uploads = listOf(
            manualUpload("/sdcard/Download/a.pdf", "/Docs/a.pdf"),
            manualUpload("/sdcard/Download/b.pdf", "/Docs/b.pdf")
        )

        val albums = uploads.toAlbums(processedCount = 0)

        assertEquals(listOf(UploadAlbum(null, "/Docs", total = 2, uploaded = 0)), albums)
    }

    @Test
    fun fileInServerRootGetsRootFolder() {
        val albums = listOf(manualUpload("/sdcard/Download/a.pdf", "/a.pdf")).toAlbums(processedCount = 0)

        assertEquals("/", albums.single().remoteFolderPath)
    }

    @Test
    fun linesContainSummaryAndOneLinePerAlbum() {
        val resources = mock<Resources>()
        val context = mock<Context> { on { it.resources } doReturn resources }
        whenever(
            resources.getQuantityString(eq(R.plurals.upload_notification_total), any(), any(), any(), any())
        ).thenAnswer { "${it.arguments[2]} of ${it.arguments[3]} uploaded, ${it.arguments[4]} left" }
        whenever(
            context.getString(eq(R.string.upload_notification_album_line), any(), any(), any(), any())
        ).thenAnswer { "${it.arguments[1]} -> ${it.arguments[2]}: ${it.arguments[3]}/${it.arguments[4]}" }
        whenever(
            context.getString(eq(R.string.upload_notification_folder_line), any(), any(), any())
        ).thenAnswer { "${it.arguments[1]}: ${it.arguments[2]}/${it.arguments[3]}" }
        val albums = listOf(
            UploadAlbum("Camera", "/Photos/Camera", total = 2, uploaded = 1),
            UploadAlbum(null, "/Docs", total = 1, uploaded = 0)
        )

        val lines = UploadNotificationLines(context).build(albums, uploadedTotal = 9, grandTotal = 66)

        assertEquals(
            listOf("9 of 66 uploaded, 57 left", "Camera -> /Photos/Camera: 1/2", "/Docs: 0/1"),
            lines
        )
    }
}
