/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.nextcloud.client.jobs.utils

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import com.nextcloud.client.account.User
import com.nextcloud.client.jobs.autoUpload.AutoUploadWorker
import com.nextcloud.client.jobs.notification.WorkerNotificationManager
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import com.owncloud.android.operations.UploadFileOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class UploadErrorNotificationManagerTest {
    private val resources: Resources = mock()
    private val context: Context = mock()
    private val builder: NotificationCompat.Builder = mock(defaultAnswer = Answers.RETURNS_SELF)
    private val progressBuilder: NotificationCompat.Builder = mock(defaultAnswer = Answers.RETURNS_SELF)
    private val notification: Notification = mock()
    private val user: User = mock()
    private val operation: UploadFileOperation = mock()
    private val notificationManager: WorkerNotificationManager = mock()
    private val pendingIntent: PendingIntent = mock()
    private lateinit var pendingIntentStatic: MockedStatic<PendingIntent>

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        pendingIntentStatic = Mockito.mockStatic(PendingIntent::class.java) { pendingIntent }
        whenever(context.getString(any())).thenReturn(TEXT)
        whenever(resources.getString(any())).thenReturn(TEXT)
        whenever(context.resources).thenReturn(resources)
        whenever(user.accountName).thenReturn(ACCOUNT)
        whenever(operation.user).thenReturn(user)
        whenever(operation.ocUploadId).thenReturn(UPLOAD_ID)
        whenever(builder.build()).thenReturn(notification)
        whenever(progressBuilder.build()).thenReturn(notification)
        whenever(notificationManager.createNotificationBuilder()).thenReturn(builder)
        whenever(notificationManager.notificationBuilder).thenReturn(progressBuilder)
        whenever(notificationManager.getId()).thenReturn(AutoUploadWorker.NOTIFICATION_ID)
    }

    @After
    fun tearDown() {
        pendingIntentStatic.close()
        Dispatchers.resetMain()
    }

    private suspend fun handle(code: ResultCode, keep: Boolean = true) {
        val result = RemoteOperationResult<Any?>(code)
        UploadErrorNotificationManager.handleResult(
            context,
            notificationManager,
            operation,
            result,
            keepGeneralErrorsAfterRun = keep
        )
    }

    @Test
    fun generalErrorIsShownUnderIdDifferentFromForegroundId() = runTest {
        handle(ResultCode.QUOTA_EXCEEDED)
        testScheduler.advanceUntilIdle()

        val id = argumentCaptor<Int>()
        verify(notificationManager).showNotification(id.capture(), eq(notification))
        assertNotEquals(AutoUploadWorker.NOTIFICATION_ID, id.firstValue)
        verify(notificationManager, never()).showNotification(any<Notification>())
    }

    @Test
    fun generalErrorDoesNotTouchProgressBuilder() = runTest {
        handle(ResultCode.WRONG_CONNECTION)
        testScheduler.advanceUntilIdle()

        verify(progressBuilder, never()).setOngoing(any())
        verify(progressBuilder, never()).clearActions()
    }

    @Test
    fun credentialsErrorIsNotOngoing() = runTest {
        handle(ResultCode.UNAUTHORIZED)
        testScheduler.advanceUntilIdle()

        verify(builder).setOngoing(false)
        verify(builder, never()).setOngoing(true)
        verify(notificationManager).showNotification(any(), eq(notification))
    }

    @Test
    fun repeatedErrorForSameAccountReusesSameId() = runTest {
        handle(ResultCode.UNAUTHORIZED)
        handle(ResultCode.UNAUTHORIZED)
        testScheduler.advanceUntilIdle()

        val id = argumentCaptor<Int>()
        verify(notificationManager, times(2)).showNotification(id.capture(), eq(notification))
        assertEquals(id.firstValue, id.secondValue)
    }

    @Test
    fun differentErrorTypesGetDifferentIds() {
        val unauthorized = UploadErrorNotificationIds.forGeneralError(ACCOUNT, ResultCode.UNAUTHORIZED)
        val quota = UploadErrorNotificationIds.forGeneralError(ACCOUNT, ResultCode.QUOTA_EXCEEDED)
        assertNotEquals(unauthorized, quota)
    }

    @Test
    fun idStaysInsideReservedRange() {
        ResultCode.values().forEach { code ->
            val id = UploadErrorNotificationIds.forGeneralError(ACCOUNT, code)
            assertTrue(id >= UploadErrorNotificationIds.RANGE_START)
            assertTrue(id < UploadErrorNotificationIds.RANGE_START + UploadErrorNotificationIds.RANGE_SIZE)
        }
    }

    @Test
    fun withoutKeepFlagGeneralErrorStillUsesWorkerId() = runTest {
        handle(ResultCode.QUOTA_EXCEEDED, keep = false)
        testScheduler.advanceUntilIdle()

        verify(notificationManager).showNotification(any<Notification>())
        verify(notificationManager, never()).createNotificationBuilder()
    }

    private companion object {
        const val TEXT = "text"
        const val ACCOUNT = "user@cloud.example"
        const val UPLOAD_ID = 42L
    }
}
