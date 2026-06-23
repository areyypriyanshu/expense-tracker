package com.expensetracker.services.upisync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.expensetracker.data.local.ExpenseDatabase
import com.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UpiSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val retryCount = AtomicInteger(0)
    private val maxRetries = 3

    init {
        createNotificationChannel()
    }

    override suspend fun doWork(): Result {
        if (!hasSmsPermission()) {
            return Result.success()
        }

        val attemptNumber = retryCount.incrementAndGet()

        return try {
            val database = ExpenseDatabase.getDatabase(applicationContext)
            val transactionRepository = TransactionRepository(database.transactionDao())
            val upiSyncService = UpiSyncService(applicationContext)

            val existingTransactions = transactionRepository.getAllTransactions().first()
            val syncedTransactions = upiSyncService.scanSmsSince(1, existingTransactions)

            if (syncedTransactions.isNotEmpty()) {
                syncedTransactions.forEach { synced ->
                    transactionRepository.insertTransaction(synced.transaction)
                }

                val hashes = syncedTransactions.map { it.messageHash }.toSet()
                upiSyncService.addImportedHashes(hashes)
                upiSyncService.setLastSyncTime()

                showNotification(syncedTransactions.size)
            }

            retryCount.set(0)
            Result.success(workDataOf(KEY_TRANSACTIONS_SYNCED to syncedTransactions.size))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "UPI sync failed")
            if (attemptNumber >= maxRetries) {
                Result.failure(workDataOf(KEY_ERROR to "Sync failed after multiple attempts"))
            } else {
                Result.retry()
            }
        }
    }

    private fun hasSmsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applicationContext.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        } else {
            applicationContext.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UPI Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for auto-synced UPI transactions"
                setShowBadge(false)
            }

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(count: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("UPI Transactions Synced")
            .setContentText("$count new transactions imported automatically")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        try {
            val notificationId = generateNotificationId()
            NotificationManagerCompat.from(applicationContext)
                .notify(notificationId, notification)
        } catch (e: SecurityException) {
            android.util.Log.w(TAG, "Notification permission not granted")
        }
    }

    private fun generateNotificationId(): Int {
        val baseId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        return (BASE_NOTIFICATION_ID + baseId) % Int.MAX_VALUE
    }

    companion object {
        private const val TAG = "UpiSyncWorker"
        const val WORK_NAME = "upi_sync_work"
        const val CHANNEL_ID = "upi_sync_channel"
        const val BASE_NOTIFICATION_ID = 1000
        const val KEY_TRANSACTIONS_SYNCED = "transactions_synced"
        const val KEY_ERROR = "error"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<UpiSyncWorker>(
                6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun requestImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<UpiSyncWorker>()
                .setConstraints(constraints)
                .addTag("${WORK_NAME}_immediate")
                .build()

            WorkManager.getInstance(context).enqueue(syncRequest)
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
