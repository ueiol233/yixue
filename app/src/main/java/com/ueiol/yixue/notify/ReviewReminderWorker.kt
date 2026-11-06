package com.ueiol.yixue.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ueiol.yixue.MainActivity
import com.ueiol.yixue.R
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.ui.wrong.WrongBookViewModel
import java.util.concurrent.TimeUnit

/**
 * 复习提醒：每天检查一次「错后满 1 天且未掌握」的错题数，大于 0 则发本地通知，点击进错题本。
 * v1 简化规则（无多轮间隔表）：wrongAt + 1 天即到期，与错题本的「今日待复习」口径一致。
 */
class ReviewReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? YixueApp ?: return Result.success()
        // 用户在「我的」里关掉了提醒
        if (!app.container.settings.reviewReminderEnabled.value) return Result.success()
        val due = app.container.wrongQuestionDao
            .dueCount(System.currentTimeMillis() - WrongBookViewModel.DUE_AFTER_MS)
        if (due <= 0) return Result.success()

        val ctx = applicationContext
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return Result.success()
        ensureChannel(ctx)

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_WRONG, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("错题复习提醒")
            .setContentText("你有 $due 道错题到期待复习，趁热打铁")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 通知权限被撤销，静默跳过
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "study_reminder"
        const val WORK_NAME = "review_reminder"
        const val EXTRA_OPEN_WRONG = "open_wrong"
        private const val NOTIFICATION_ID = 1001

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "学习提醒", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }

        /** 应用启动时调用：注册每日一次的周期检查（已存在则跳过）。 */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
