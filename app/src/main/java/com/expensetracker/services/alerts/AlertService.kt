package com.expensetracker.services.alerts

import android.Manifest
import android.app.NotificationChannel
import java.util.concurrent.atomic.AtomicInteger
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.expensetracker.domain.engine.BudgetStatus

class AlertService(private val context: Context) {

    companion object {
        private const val TAG = "AlertService"
        const val BUDGET_CHANNEL_ID = "budget_alerts"
        const val RECURRING_CHANNEL_ID = "recurring_alerts"
        private const val BUDGET_NOTIFICATION_BASE = 2000
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val budgetChannel = NotificationChannel(
                BUDGET_CHANNEL_ID,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Budget limit notifications" }

            val recurringChannel = NotificationChannel(
                RECURRING_CHANNEL_ID,
                "Recurring Expenses",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Recurring expense reminders" }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(budgetChannel)
            manager.createNotificationChannel(recurringChannel)
        }
    }

    fun checkBudgetAndAlert(budgetStatuses: List<BudgetStatus>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notificationCounter = AtomicInteger(BUDGET_NOTIFICATION_BASE)
        budgetStatuses.filter { it.shouldAlert }.forEach { status ->
            val title = if (status.isOverBudget) "Budget Exceeded: ${status.budget.category}" else "Budget Alert: ${status.budget.category}"
            val message = "You've used ${(status.percentageUsed * 100).toInt()}% of your ${status.budget.category} budget"
            
            showNotification(BUDGET_CHANNEL_ID, title, message, notificationCounter.getAndIncrement())
        }
    }

    private fun showNotification(channelId: String, title: String, message: String, notificationId: Int) {
        val safeNotificationId = if (notificationId > 0 && notificationId < 10000) notificationId else (notificationId.hashCode() and 0x7FFFFFFF) % 10000
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(safeNotificationId, notification)
        } catch (e: SecurityException) {
            android.util.Log.w(TAG, "Notification permission not granted")
        }
    }
}
