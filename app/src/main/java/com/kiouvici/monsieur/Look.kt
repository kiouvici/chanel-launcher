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
        /** 페이지 종류: 0 표지 · 1 미디어 · 2 HA 홈 · 3 HA 집 */
        val PAGE_ORDERS = listOf(listOf(0, 2, 3, 1), listOf(0, 2, 1, 3), listOf(0, 1, 2, 3))
        val PAGE_ORDER_NAMES = listOf("표지 · HA 홈 · HA 집 · 미디어", "표지 · HA 홈 · 미디어 · HA 집", "표지 · 미디어 · HA 홈 · HA 집")
        val IDLE_SECS = listOf(5, 10, 15, 30, 60, 180)
        fun idleName(s: Int) = if (s >= 60) "${s / 60}분" else "${s}초"
        /** 움직임 속도 — 종류별 (성훈님, build-58). 배율은 걸리는 시간에 곱함: 작을수록 빠름, 0은 끄기 */
        const val SP_HOME = 0; const val SP_DRAWER = 1; const val SP_CARD = 2; const val SP_CELLS = 3; const val SP_PAGE = 4
        val SPD_TYPES = listOf("런처 첫 화면 나타나기", "앱서랍 열고 닫기", "폴더 카드 열고 닫기", "앱·폴더 칸 나타나기", "페이지 넘김")
        val SPD_NAMES = listOf("아주 빠르게", "빠르게", "보통", "느리게", "끄기")
        val SPD_F = floatArrayOf(.4f, .6f, 1f, 1.4f, 0f)
        val spd = FloatArray(5) { 1f }
        /** 기본 시간(ms)에 그 종류의 배율을 곱한 시간 */
        fun ms(base: Long, type: Int): Long = (base * spd[type]).toLong()
        fun off(type: Int) = spd[type] == 0f
    }

    private val p = ctx.getSharedPreferences("monsieur", Context.MODE_PRIVATE)
    init { Dk.libStyle = libStyle; Dk.lib = libStyle != 0; Dk.voy = voyDark; for (t in 0 until 5) spd[t] = SPD_F[speedIdx(t)] }
    fun speedIdx(t: Int) = p.getInt("spd$t", 2).coerceIn(0, 4)
    fun setSpeed(t: Int, i: Int) { p.edit().putInt("spd$t", i).apply(); spd[t] = SPD_F[i] }

    /** LIBRARY · VOYAGE 바탕: true 블랙(기본) · false 밝게 */
    var libDark: Boolean
        get() = libStyle != 0
        set(v) { libStyle = if (v) 1 else 0 }
    /** LIBRARY 바탕 0 밝게 · 1 전부 검정 · 2 아이보리 원고지 · 3 작은 종이 · 4 낮춘 종이 (예전 '밝게' 고른 사람은 0) */
    var libStyle: Int
        get() = p.getInt("libStyle", if (p.getBoolean("libDark", true)) 1 else 0).coerceIn(0, 4)
        set(v) { p.edit().putInt("libStyle", v).apply(); Dk.libStyle = v; Dk.lib = v != 0 }
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

    // ── 보기 · DISPLAY (build-39: 슬라이드·팔레트, 모든 테마 공통 — 성훈님 결정) ──
    /** 앱 아이콘 36~72dp (예전 3단계 값에서 옮겨 옴) */
    var iconDpV: Float
        get() = p.getFloat("iconDpV", floatArrayOf(44f, 52f, 60f)[iconSize.coerceIn(0, 2)]).coerceIn(36f, 72f)
        set(v) { p.edit().putFloat("iconDpV", v.coerceIn(36f, 72f)).apply() }
    /** 폴더 크기 80~130% */
    var folderPct: Int
        get() = p.getInt("folderPct", intArrayOf(90, 100, 106)[folderSize.coerceIn(0, 2)]).coerceIn(80, 130)
        set(v) { p.edit().putInt("folderPct", v.coerceIn(80, 130)).apply() }
    /** 폴더 이름 12~20sp (14가 테마 기본) · 앱 이름 10~16sp (11이 기본) — 테마마다 기본 크기가 달라 '기본 대비 더하기'로 씀 */
    var folderNameSp: Float
        get() = p.getFloat("folderNameSp", 14f + floatArrayOf(-1f, 0f, 1.5f)[nameSize.coerceIn(0, 2)]).coerceIn(12f, 20f)
        set(v) { p.edit().putFloat("folderNameSp", v.coerceIn(12f, 20f)).apply() }
    var appNameSp: Float
        get() = p.getFloat("appNameSp", 11f + floatArrayOf(-1f, 0f, 1.5f)[nameSize.coerceIn(0, 2)]).coerceIn(10f, 16f)
        set(v) { p.edit().putFloat("appNameSp", v.coerceIn(10f, 16f)).apply() }
    /** 앱 간격 0 좁게 · 1 기본 · 2 넓게 */
    var gapMode: Int
        get() = p.getInt("gapMode", 1).coerceIn(0, 2)
        set(v) { p.edit().putInt("gapMode", v).apply() }
    /** 글자 굵기 0 가늘게 · 1 기본 · 2 굵게 (테마 굵기에서 −100 / 0 / +100) */
    var weightMode: Int
        get() = p.getInt("weightMode", 1).coerceIn(0, 2)
        set(v) { p.edit().putInt("weightMode", v).apply() }
    var showAppNames: Boolean
        get() = p.getBoolean("showAppNames", true)
        set(v) { p.edit().putBoolean("showAppNames", v).apply() }
    /** 이름 글꼴 0 테마 · 1 고딕 · 2 명조 */
    var fontMode: Int
        get() = p.getInt("fontMode", 0).coerceIn(0, 2)
        set(v) { p.edit().putInt("fontMode", v).apply() }
    /** 색: 0 = 테마 기본색 */
    var folderColor: Int
        get() = p.getInt("folderColor", legacyColor(false))
        set(v) { p.edit().putInt("folderColor", v).apply() }
    var appColor: Int
        get() = p.getInt("appColor", 0)
        set(v) { p.edit().putInt("appColor", v).apply() }
    var dotColor: Int
        get() = p.getInt("dotColor", 0)
        set(v) { p.edit().putInt("dotColor", v).apply() }
    var recentColors: List<Int>
        get() = try { JSONArray(p.getString("recentColors", "[]")).let { a -> (0 until a.length()).map { a.getInt(it) } } } catch (e: Exception) { emptyList() }
        set(v) { p.edit().putString("recentColors", JSONArray(v.distinct().take(6)).toString()).apply() }
    fun addRecent(c: Int) { if (c != 0) recentColors = listOf(c) + recentColors.filter { it != c } }
    /** 예전 '이름 색'(진하게·금색·옅게)을 고른 적 있으면 그 색으로 시작 */
    private fun legacyColor(light: Boolean): Int = when (nameColor) { 2 -> 0xFFC9A96A.toInt(); else -> 0 }
    /** 페이지 순서 (표지는 늘 처음): 0 표지·HA홈·HA집·미디어(기본) · 1 표지·HA홈·미디어·HA집 · 2 표지·미디어·HA홈·HA집(예전) — 성훈님, build-47 */
    var pageOrder: Int
        get() = p.getInt("pageOrder", 0).coerceIn(0, 2)
        set(v) { p.edit().putInt("pageOrder", v).apply() }
    /** 자동 돌아오기: 손을 뗀 뒤 이만큼 지나면 열린 카드에서 나와 맨 위로 (초) */
    var idleSec: Int
        get() = p.getInt("idleSec", 15).let { if (it in IDLE_SECS) it else 15 }
        set(v) { p.edit().putInt("idleSec", v).apply() }
    /** 움직임 0 부드럽게 · 1 짧게 · 2 끄기 */
    var motion: Int
        get() = p.getInt("motion", 0).coerceIn(0, 2)
        set(v) { p.edit().putInt("motion", v).apply() }

    val folderScale get() = folderPct / 100f
    val iconDp get() = iconDpV
    /** 폴더 이름: 테마 기본 크기에 더하는 값 */
    val nameDelta get() = folderNameSp - 14f
    /** 앱 이름: 기본 크기에 더하는 값 */
    val appNameDelta get() = appNameSp - 11f

    /** 폴더 이름 색 (고른 색이 없으면 테마 색) */
    fun nameColorOn(light: Boolean, base: Int): Int = if (folderColor != 0) folderColor else base
    /** 앱 이름 색 */
    fun appColorOn(base: Int): Int = if (appColor != 0) appColor else base

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
    /** VOYAGE 서랍 라벨 순서: 0 세로(왼쪽 위→아래 먼저) · 1 가로(줄마다 왼쪽→오른쪽) */
    var trainOrder: Int
        get() = p.getInt("trainOrder", 0).coerceIn(0, 1)
        set(v) { p.edit().putInt("trainOrder", v).apply() }
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
