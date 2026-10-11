package com.kiouvici.monsieur

import android.media.AudioManager
import java.lang.reflect.Method

/**
 * 삼성 폰의 세밀 볼륨(0~150) — 공개 안드로이드 기능은 15칸만 알려 줘서 '17'이 '20'으로 보이던 것.
 * 삼성 숨은 기능을 이름으로 찾아 써 보고, 값이 15칸 볼륨과 맞아떨어질 때만 씀. 아니면 15칸 × 10 으로 돌아감.
 */
object FineVol {
    private var tried = false
    private var getM: Method? = null
    private var setM: Method? = null
    var ok = false; private set
    var note = "확인 전"; private set

    fun init(am: AudioManager) {
        if (tried) return
        tried = true
        try {
            val ms = (AudioManager::class.java.methods + AudioManager::class.java.declaredMethods).filter { it.name.contains("fine", true) }
            val g = ms.firstOrNull { it.name.contains("get", true) && !it.name.contains("max", true) && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) && it.returnType == Int::class.javaPrimitiveType }
            val s = ms.firstOrNull { it.name.contains("set", true) && it.parameterTypes.isNotEmpty() && it.parameterTypes.size in 2..3 && it.parameterTypes.all { p -> p == Int::class.javaPrimitiveType } }
            g?.isAccessible = true; s?.isAccessible = true
            getM = g; setM = s
            // 맞는지 확인: 세밀 값 ≈ 15칸 값 × 10
            val fine = g?.invoke(am, AudioManager.STREAM_MUSIC) as? Int
            val coarse = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            ok = fine != null && s != null && fine in 0..150 && kotlin.math.abs(fine - coarse * 150 / mx.coerceAtLeast(1)) <= 10
            note = if (ok) "세밀 150단계" else if (g == null) "15칸 (세밀 기능 없음)" else "15칸 (세밀 값 확인 실패)"
            android.util.Log.d("MNS", "finevol get=${g?.name} set=${s?.name} fine=$fine coarse=$coarse/$mx ok=$ok")
        } catch (e: Throwable) {
            ok = false; note = "15칸 (세밀 기능 막힘)"
            android.util.Log.d("MNS", "finevol fail $e")
        }
    }

    /** 0~150 */
    fun get(am: AudioManager): Int? = if (!ok) null else try { getM?.invoke(am, AudioManager.STREAM_MUSIC) as? Int } catch (e: Throwable) { ok = false; null }

    fun set(am: AudioManager, v: Int): Boolean {
        if (!ok) return false
        val m = setM ?: return false
        return try {
            if (m.parameterTypes.size == 3) m.invoke(am, AudioManager.STREAM_MUSIC, v, 0) else m.invoke(am, AudioManager.STREAM_MUSIC, v)
            true
        } catch (e: Throwable) { ok = false; note = "15칸 (세밀 바꾸기 막힘)"; false }
    }
}
