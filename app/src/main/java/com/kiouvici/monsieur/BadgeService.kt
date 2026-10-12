package com.kiouvici.monsieur

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** 알림이 있는 앱을 모아 붉은 점으로 보여 주기 위한 서비스 (숫자 없이 점만) */
class BadgeService : NotificationListenerService() {

    companion object {
        @Volatile var active: Set<String> = emptySet()
            private set
        /** 앱마다 가장 최근 알림이 올라온 시각 (배지 대상 알림만) */
        @Volatile var latest: Map<String, Long> = emptyMap()
            private set
        var onChange: (() -> Unit)? = null
    }

    private fun refresh() {
        val lt = HashMap<String, Long>()
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
                ?.onEach { lt[it.packageName] = maxOf(lt[it.packageName] ?: 0L, it.postTime) }
                ?.map { it.packageName }
                ?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
        latest = lt
        onChange?.invoke()
    }

    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()
    override fun onListenerDisconnected() {
        active = emptySet(); latest = emptyMap(); onChange?.invoke()
    }
}

/**
 * 알림 점 = "그 앱을 마지막으로 연 뒤에 새 알림이 왔다" (build-38)
 * 앱을 연 시각: 서랍에서 연 것 + 앱 사용 기록(권한 있을 때 — 알림을 눌러 들어가거나 최근 앱에서 연 것까지)
 */
object Badges {
    private const val P = "badges"
    private var lastQuery = 0L
    lateinit var app: android.content.Context
    fun shown(): Set<String> = if (::app.isInitialized) shown(app) else BadgeService.active
    private fun prefs(c: android.content.Context) = c.getSharedPreferences(P, 0)

    fun usageGranted(c: android.content.Context): Boolean = try {
        val ops = c.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) == android.app.AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    fun seenAt(c: android.content.Context, pkg: String) = prefs(c).getLong(pkg, 0L)
    fun markSeen(c: android.content.Context, pkg: String, t: Long = System.currentTimeMillis()) {
        if (t > seenAt(c, pkg)) prefs(c).edit().putLong(pkg, t).apply()
    }

    /** 사용 기록에서 앱이 앞으로 나온 시각을 읽어 '본 시각'으로 적음 */
    fun syncUsage(c: android.content.Context) {
        if (!usageGranted(c)) return
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L) now - 6 * 3600_000L else lastQuery - 2000
        lastQuery = now
        try {
            val um = c.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val ev = um.queryEvents(from, now)
            val e = android.app.usage.UsageEvents.Event()
            val last = HashMap<String, Long>()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                @Suppress("DEPRECATION")
                if (e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND || e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND)
                    last[e.packageName] = maxOf(last[e.packageName] ?: 0L, e.timeStamp)
            }
            if (last.isNotEmpty()) { val ed = prefs(c).edit(); last.forEach { (k, v) -> if (v > seenAt(c, k)) ed.putLong(k, v) }; ed.apply() }
        } catch (_: Exception) {}
    }

    /** 점을 켤 앱 */
    fun shown(c: android.content.Context): Set<String> =
        BadgeService.latest.filter { (pkg, t) -> t > seenAt(c, pkg) }.keys

    /** 진단: 알림이 있는 앱마다 켜짐/확인함 */
    fun report(c: android.content.Context, label: (String) -> String): String {
        val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
        val lines = BadgeService.latest.entries.sortedByDescending { it.value }.map { (pkg, t) ->
            val s = seenAt(c, pkg)
            "${if (t > s) "● 켜짐" else "○ 확인함"}  ${label(pkg)}  알림 ${f.format(java.util.Date(t))} · 연 시각 ${if (s == 0L) "-" else f.format(java.util.Date(s))}"
        }
        return "앱 사용 기록 권한: ${if (usageGranted(c)) "허용됨" else "없음"}\n" + (if (lines.isEmpty()) "알림 있는 앱 없음" else lines.joinToString("\n"))
    }
}
