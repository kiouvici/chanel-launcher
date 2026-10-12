package com.kiouvici.monsieur

import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer

/** 진단용: 홈 복귀 후 터치가 언제 도착하는지, 그동안 앱이 바빴는지 기록 (build-36) */
object TouchLog {
    private const val N = 80
    private val buf = ArrayDeque<String>()
    @Volatile var base = 0L
    private var msgStart = 0L
    private var msgName = ""
    private var installed = false
    var touchesSinceBase = 0

    @Synchronized fun add(s: String) {
        val t = SystemClock.uptimeMillis()
        val rel = if (base > 0) "+${t - base}" else "-"
        val clock = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        buf.addLast("$clock  $rel  $s")
        while (buf.size > N) buf.removeFirst()
        android.util.Log.d("MNS", "TL $rel $s")
    }

    /** 기준점: 다른 앱에서 돌아오기 시작한 순간 (onRestart) */
    fun mark(s: String) {
        base = SystemClock.uptimeMillis(); touchesSinceBase = 0
        add("────── $s (기준 0)")
    }

    fun firstFrame(tag: String) {
        val t0 = SystemClock.uptimeMillis()
        Choreographer.getInstance().postFrameCallback { add("첫 화면 그림 ($tag, 기다림 ${SystemClock.uptimeMillis() - t0}ms)") }
    }

    /** 주 스레드에서 50ms 넘게 걸린 일 기록 */
    fun install() {
        if (installed) return; installed = true
        Looper.getMainLooper().setMessageLogging { line ->
            if (line.startsWith(">>>>>")) { msgStart = SystemClock.uptimeMillis(); msgName = line }
            else if (line.startsWith("<<<<<") && msgStart > 0) {
                val d = SystemClock.uptimeMillis() - msgStart
                if (d >= 50) add("바쁨 ${d}ms · " + short(msgName))
                msgStart = 0
            }
        }
    }

    private fun short(l: String): String {
        // ">>>>> Dispatching to Handler (android.view.Choreographer$FrameHandler) {..} android.view.Choreographer$FrameDisplayEventReceiver@..: 0"
        val h = Regex("\\((.*?)\\)").find(l)?.groupValues?.get(1)?.substringAfterLast('.') ?: ""
        val cb = l.substringAfter("} ", "").substringBefore('@').substringBefore(':').substringAfterLast('.')
        return "$h $cb".trim().take(60)
    }

    @Synchronized fun text(): String = if (buf.isEmpty()) "기록 없음" else buf.joinToString("\n")
    @Synchronized fun clear() { buf.clear(); base = 0 }
}
