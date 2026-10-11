package com.kiouvici.monsieur

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** 알림이 있는 앱을 모아 붉은 점으로 보여 주기 위한 서비스 (숫자 없이 점만) */
class BadgeService : NotificationListenerService() {

    companion object {
        @Volatile var active: Set<String> = emptySet()
            private set
        var onChange: (() -> Unit)? = null
    }

    private fun refresh() {
        active = try {
            activeNotifications
                ?.filter { !it.isOngoing && it.notification != null }
                ?.map { it.packageName }
                ?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
        onChange?.invoke()
    }

    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onListenerDisconnected() {
        active = emptySet(); onChange?.invoke()
    }
}
