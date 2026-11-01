package com.gatenoc.bkashsync

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SmsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName == packageName) return
        if (!AppStorage.isLoggedIn(this)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""

        val fullBody = if (bigText.length >= text.length && bigText.isNotEmpty()) bigText else text
        if (fullBody.isEmpty()) return

        // If notification is from bKash / Google Messages bKash thread
        if (SmsParser.isAllowedSender(title) || fullBody.contains("You have received Tk", ignoreCase = true)) {
            val record = SmsParser.parseIncomingOrManual("bKash", fullBody, "Notification")
            if (record != null) {
                val isNew = AppStorage.insertIfNew(this, record)
                if (isNew) {
                    SmsReceiver.uploadRecordImmediately(applicationContext, record)
                }
            }
        }
    }
}
