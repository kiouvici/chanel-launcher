package com.kiouvici.monsieur

import android.content.Context
import org.json.JSONArray

/** 테마(THE COLLECTION) · 보기 설정(DISPLAY) · HA 연결 · CAPTION 사진 — 휴대폰에 저장 */
class Look(ctx: Context) {
    companion object {
        const val MONSIEUR = 0
        const val LIBRARY = 1
        const val HOUR = 2
        const val TRAIN = 3
        const val CAPTION = 4
        val NAMES = listOf("MONSIEUR", "THE LIBRARY", "THE HOUR", "TRAIN BLEU", "THE CAPTION")
        val WHEN = listOf("평일 기본 · 검은 상자와 리본", "주말 아침 · 캉봉 아파트의 서재", "평일 출근 · Monsieur de Chanel 시계",
            "여행·출장·금요일 밤 · Le Train Bleu", "기념일·가족 · 직접 찍은 사진")
        const val HA_BASE = "https://kiouvici.synology.me:8123"
        const val MAX_PHOTOS = 6
    }

    private val p = ctx.getSharedPreferences("monsieur", Context.MODE_PRIVATE)

    var theme: Int
        get() = p.getInt("theme", MONSIEUR).coerceIn(0, 4)
        set(v) { p.edit().putInt("theme", v).apply() }

    /** 폴더 크기 0 작게 · 1 기본 · 2 크게 */
    var folderSize: Int
        get() = p.getInt("folderSize", 1)
        set(v) { p.edit().putInt("folderSize", v).apply() }
    /** 앱 아이콘 크기 0 작게(44) · 1 기본(52) · 2 크게(60) */
    var iconSize: Int
        get() = p.getInt("iconSize", 1)
        set(v) { p.edit().putInt("iconSize", v).apply() }
    /** 이름 글자 크기 0 작게 · 1 기본 · 2 크게 */
    var nameSize: Int
        get() = p.getInt("nameSize", 1)
        set(v) { p.edit().putInt("nameSize", v).apply() }
    /** 이름 글자 색 0 기본 · 1 진하게 · 2 금색 · 3 옅게 */
    var nameColor: Int
        get() = p.getInt("nameColor", 0)
        set(v) { p.edit().putInt("nameColor", v).apply() }

    val folderScale get() = floatArrayOf(.9f, 1f, 1.06f)[folderSize.coerceIn(0, 2)]   // 크게도 한 화면을 넘지 않게 (서랍에서 맞춤)
    val iconDp get() = floatArrayOf(44f, 52f, 60f)[iconSize.coerceIn(0, 2)]
    val nameDelta get() = floatArrayOf(-1f, 0f, 1.5f)[nameSize.coerceIn(0, 2)]

    /** 바탕이 밝은지(종이·아이보리) 어두운지에 맞춰 이름 색을 고름 */
    fun nameColorOn(light: Boolean, base: Int): Int = when (nameColor) {
        1 -> if (light) Style.INK else Style.IVORY
        2 -> if (light) Style.GILT else 0xFFE2C78E.toInt()
        3 -> if (light) Style.ink(.55f) else Style.ivory(.55f)
        else -> base
    }

    var haToken: String
        get() = p.getString("haToken", "") ?: ""
        set(v) { p.edit().putString("haToken", v.trim()).apply() }

    var photos: List<String>
        get() = try { JSONArray(p.getString("photos", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } } catch (e: Exception) { emptyList() }
        set(v) { p.edit().putString("photos", JSONArray(v.take(MAX_PHOTOS)).toString()).apply() }
    var photoIdx: Int
        get() = p.getInt("photoIdx", 0)
        set(v) { p.edit().putInt("photoIdx", v).apply() }
}
