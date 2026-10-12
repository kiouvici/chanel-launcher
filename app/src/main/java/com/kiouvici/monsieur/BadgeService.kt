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
            // 삼성 홈 화면처럼: 앱이 '배지 표시'를 허락한 알림만 (알림 채널 설정), 실행 중 표시·전송·서비스 알림은 빼기
            val rk = currentRanking
            val r = Ranking()
            activeNotifications
                ?.filter { sbn ->
                    val n = sbn.notification ?: return@filter false
                    if (sbn.isOngoing) return@filter false
                    if (n.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0) return@filter false
                    if (n.category in setOf(android.app.Notification.CATEGORY_SERVICE, android.app.Notification.CATEGORY_TRANSPORT,
                            android.app.Notification.CATEGORY_PROGRESS, android.app.Notification.CATEGORY_SYSTEM)) return@filter false
                    val ok = rk?.getRanking(sbn.key, r) == true
                    !ok || (r.canShowBadge() && r.importance >= android.app.NotificationManager.IMPORTANCE_LOW)
                }
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
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()
    override fun onListenerDisconnected() {
        active = emptySet(); onChange?.invoke()
    }
}
