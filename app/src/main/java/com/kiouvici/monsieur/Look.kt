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
        val NAMES = listOf("MONSIEUR", "THE LIBRARY", "THE HOUR", "THE VOYAGE", "THE CAPTION")
        val WHEN = listOf("평일 기본 · 검은 상자와 리본", "주말 아침 · 캉봉 아파트의 서재 · 원고지", "평일 출근 · NOIR / IVOIRE 문자판",
            "여행·주말·가족 · 엽서 한 장에 담은 우리 여행", "기념일·가족 · 직접 찍은 사진")
        const val HA_BASE = "https://kiouvici.synology.me:8123"
        const val MAX_PHOTOS = 6
    }

    private val p = ctx.getSharedPreferences("monsieur", Context.MODE_PRIVATE)
    init { Dk.lib = libDark; Dk.voy = voyDark }

    /** LIBRARY · VOYAGE 바탕: true 블랙(기본) · false 밝게 */
    var libDark: Boolean
        get() = p.getBoolean("libDark", true)
        set(v) { p.edit().putBoolean("libDark", v).apply(); Dk.lib = v }
    var voyDark: Boolean
        get() = p.getBoolean("voyDark", true)
        set(v) { p.edit().putBoolean("voyDark", v).apply(); Dk.voy = v }

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

    /** THE HOUR 문자판 0 NOIR(검정 세라믹) · 1 IVOIRE(아이보리 에나멜) */
    var hourDial: Int
        get() = p.getInt("hourDial", 0).coerceIn(0, 1)
        set(v) { p.edit().putInt("hourDial", v).apply() }
    /** THE LIBRARY 원고지 순서 0 세로(왼쪽 1–15) · 1 가로 */
    var libOrder: Int
        get() = p.getInt("libOrder", 0).coerceIn(0, 1)
        set(v) { p.edit().putInt("libOrder", v).apply() }

    /** 마지막으로 들은 곡 (미디어 화면이 비지 않게 — 삼성 미디어 위젯처럼) */
    var lastTitle: String
        get() = p.getString("lastTitle", "") ?: ""
        set(v) { p.edit().putString("lastTitle", v).apply() }
    var lastArtist: String
        get() = p.getString("lastArtist", "") ?: ""
        set(v) { p.edit().putString("lastArtist", v).apply() }
    var lastAlbum: String
        get() = p.getString("lastAlbum", "") ?: ""
        set(v) { p.edit().putString("lastAlbum", v).apply() }
    var lastPkg: String
        get() = p.getString("lastPkg", "") ?: ""
        set(v) { p.edit().putString("lastPkg", v).apply() }

    /** Spotify 기기 목록에서 이 폰의 이름 (한 번 고르면 기억) */
    var spPhone: String
        get() = p.getString("spPhone", "") ?: ""
        set(v) { p.edit().putString("spPhone", v).apply() }

    /** THE VOYAGE 대표사진 (최대 6장): 앱 안에 복사한 파일 + 장소·날짜 + 보일 범위(확대 s, 중심 cx·cy 0~1) */
    data class Trip(val file: String, val place: String, val date: String, val s: Float, val cx: Float, val cy: Float)
    var trips: List<Trip>
        get() = try {
            val a = JSONArray(p.getString("trips", "[]"))
            (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
                Trip(o.getString("f"), o.optString("pl"), o.optString("dt"), o.optDouble("s", 1.0).toFloat(), o.optDouble("cx", .5).toFloat(), o.optDouble("cy", .5).toFloat()) } }
        } catch (e: Exception) { emptyList() }
        set(v) {
            val a = JSONArray()
            v.take(MAX_PHOTOS).forEach { t -> a.put(org.json.JSONObject().put("f", t.file).put("pl", t.place).put("dt", t.date).put("s", t.s.toDouble()).put("cx", t.cx.toDouble()).put("cy", t.cy.toDouble())) }
            p.edit().putString("trips", a.toString()).apply()
        }
    var tripIdx: Int
        get() = p.getInt("tripIdx", 0)
        set(v) { p.edit().putInt("tripIdx", v).apply() }
}
