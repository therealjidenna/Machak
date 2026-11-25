package com.therealjidenna.machak

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanWorker(private val ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val groups = withContext(Dispatchers.IO) { FileScanner.scan() }
        if (groups.isEmpty()) return Result.success()

        val extra = groups.sumOf { it.size * (it.files.size - 1) }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("machak", "Duplicate scan", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = Notification.Builder(ctx, "machak")
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Machak found ${groups.size} duplicate groups")
            .setContentText("You can free ${Formatter.formatFileSize(ctx, extra)}")
            .setContentIntent(open)
