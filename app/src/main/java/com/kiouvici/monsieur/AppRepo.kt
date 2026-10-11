package com.kiouvici.monsieur

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

data class AppEntry(
    val pkg: String,
    val cls: String,
    val label: String,   // 화면에 보이는 이름 (성훈님이 바꾼 이름이 있으면 그것)
    val orig: String,    // 앱 원래 이름 (초기 배정 찾기용)
    val icon: Drawable,
    val bmp: Bitmap,     // 둥근 사각으로 맞춘 아이콘 (한 번 만들어 재사용)
    val cho: String,     // 초성 (ㅋㅋㅇ → 카카오톡)
) {
    val key: String get() = "$pkg/$cls"
}

/**
 * 앱 목록, 폴더(추가·삭제·이름 바꾸기), 한 앱 여러 폴더, 숨기기, 표지 앱, 실행 횟수, 아이콘 모양
 * 폴더는 바뀌지 않는 id로 기억함 — 처음 30개는 원래 이름이 id, 새로 만든 폴더는 'u+시각'
 */
class AppRepo(private val ctx: Context) {

    companion object {
        /** 처음 폴더 30개 (성훈님 홈 화면 폴더 이름 그대로) */
        val DEFAULT_FOLDERS = listOf(
            "건강", "게임", "공유&전송", "굿락", "금융", "기기관리", "날씨", "네비&지도", "대중교통", "동영상",
            "메모&일정", "사진&카메라", "생활편의", "설정", "쇼핑(오프라인)", "쇼핑(온라인)", "아기", "앱&파일", "업무", "여행",
            "외식", "웹툰&북", "음악&라디오", "인증&지갑", "인터넷", "자동차", "전화", "정부", "AI", "SNS",
        )
        const val MAX_FOLDERS = 40
        const val ICON_COLOR = 0
        const val ICON_MONO = 1
        const val ICON_GILT = 2
    }

    private val prefs = ctx.getSharedPreferences("monsieur", Context.MODE_PRIVATE)
    var apps: List<AppEntry> = emptyList()
        private set

    private val folderIds = ArrayList<String>()
    private val names = HashMap<String, String>()                       // id → 바꾼 이름
    private val foldersOf = HashMap<String, LinkedHashSet<String>>()     // pkg → 폴더 id들
    private val opens = HashMap<String, Int>()                           // pkg → 실행 횟수(전체)
    private val fOpens = HashMap<String, HashMap<String, Int>>()         // 폴더 id → pkg → 그 폴더에서 연 횟수
    private val hidden = HashSet<String>()
    private val labels = HashMap<String, String>()                       // pkg → 바꾼 앱 이름
    private val covers = HashMap<String, MutableList<String>>()          // 폴더 id → 직접 고른 표지 앱
    private val presetDone = HashSet<String>()                           // 초기 배정을 이미 마친(또는 직접 손댄) 앱

    var iconMode: Int
        get() = prefs.getInt("iconMode", ICON_COLOR)
        set(v) { prefs.edit().putInt("iconMode", v).apply() }

    /** 원UI 홈 폴더 캡처에서 옮긴 초기 배정(assets/folders.json). 한 번도 배정되지 않은 앱에만 넣음 */
    private val prefixes = HashMap<String, String>()
    private val preset: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        try {
            val o = JSONObject(ctx.assets.open("folders.json").bufferedReader().readText())
            o.keys().forEach { f ->
                if (f in DEFAULT_FOLDERS) {
                    val arr = o.getJSONArray(f)
                    for (i in 0 until arr.length()) {
                        val name = arr.getString(i)
                        // 캡처에서 이름이 잘린 앱('…'로 끝남)은 앞부분으로 찾음
                        if (name.endsWith("…")) prefixes[norm(name.dropLast(1))] = f else m[norm(name)] = f
                    }
                }
            }
        } catch (e: Exception) { }
        m
    }

    /** 이름 비교용: 한글 자모 합치기(NFC) · 소문자 · 공백과 눈에 안 보이는 글자 빼기
     *  (캡처와 같은 이름인데 못 찾던 '복뚝방' 같은 경우 대비) */
    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC).lowercase()
        .replace(Regex("[\\s\\u00A0\\u200B-\\u200D\\u2060\\uFEFF]+"), "")


    // ───────── 저장 ─────────
    private fun load() {
        prefs.getString("folderList", null)?.let { s ->
            val a = JSONArray(s); for (i in 0 until a.length()) folderIds.add(a.getString(i))
        } ?: folderIds.addAll(DEFAULT_FOLDERS)
        prefs.getString("folderNames", null)?.let { s -> val o = JSONObject(s); o.keys().forEach { names[it] = o.getString(it) } }
        prefs.getString("foldersOf", null)?.let { s ->
            val o = JSONObject(s)
            o.keys().forEach { k -> val a = o.getJSONArray(k); foldersOf[k] = LinkedHashSet<String>().apply { for (i in 0 until a.length()) add(a.getString(i)) } }
        }
        prefs.getString("opens", null)?.let { s -> val o = JSONObject(s); o.keys().forEach { opens[it] = o.getInt(it) } }
        prefs.getString("fOpens", null)?.let { s ->
            val o = JSONObject(s)
            o.keys().forEach { f -> val m = o.getJSONObject(f); fOpens[f] = HashMap<String, Int>().apply { m.keys().forEach { put(it, m.getInt(it)) } } }
        }
        prefs.getString("hidden", null)?.let { s -> val a = JSONArray(s); for (i in 0 until a.length()) hidden.add(a.getString(i)) }
        prefs.getString("labels", null)?.let { s -> val o = JSONObject(s); o.keys().forEach { labels[it] = o.getString(it) } }
        prefs.getString("covers", null)?.let { s ->
            val o = JSONObject(s)
            o.keys().forEach { f -> val a = o.getJSONArray(f); covers[f] = MutableList(a.length()) { a.getString(it) } }
        }
        prefs.getString("presetDone", null)?.let { s -> val a = JSONArray(s); for (i in 0 until a.length()) presetDone.add(a.getString(i)) }

        // build-14까지의 저장 방식(앱마다 폴더 하나)을 옮겨 옴
        if (!prefs.contains("foldersOf")) {
            prefs.getString("folders", null)?.let { s ->
                val o = JSONObject(s)
                o.keys().forEach { pkg ->
                    val f = o.getString(pkg)
                    if (f in DEFAULT_FOLDERS) {
                        foldersOf[pkg] = linkedSetOf(f); presetDone.add(pkg)
                        opens[pkg]?.let { n -> fOpens.getOrPut(f) { HashMap() }[pkg] = n }
                    }
                }
            }
            save()
        }
    }

    private fun save() {
        val fo = JSONObject(); foldersOf.forEach { (k, v) -> if (v.isNotEmpty()) fo.put(k, JSONArray(v.toList())) }
        val fop = JSONObject(); fOpens.forEach { (f, m) -> fop.put(f, JSONObject(m as Map<*, *>)) }
        val cv = JSONObject(); covers.forEach { (f, l) -> if (l.isNotEmpty()) cv.put(f, JSONArray(l)) }
        prefs.edit()
            .putString("folderList", JSONArray(folderIds).toString())
            .putString("folderNames", JSONObject(names as Map<*, *>).toString())
            .putString("foldersOf", fo.toString())
            .putString("opens", JSONObject(opens as Map<*, *>).toString())
            .putString("fOpens", fop.toString())
            .putString("hidden", JSONArray(hidden.toList()).toString())
            .putString("labels", JSONObject(labels as Map<*, *>).toString())
            .putString("covers", cv.toString())
            .putString("presetDone", JSONArray(presetDone.toList()).toString())
            .apply()
    }

    // ───────── 앱 목록 ─────────
    /** 앱 목록 + 아이콘 비트맵 만들기 (무거움 — 뒤 스레드에서 불러도 됨) */
    private val bmpCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    fun scan(fresh: Boolean): List<AppEntry> {
        if (fresh) bmpCache.clear()
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(main, PackageManager.MATCH_ALL)
        val lab = HashMap(labels)
        return list.filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                val o = it.loadLabel(pm).toString()
                val k = it.activityInfo.packageName + "/" + it.activityInfo.name
                val ic = it.loadIcon(pm)
                val b = bmpCache.getOrPut(k) { shape(ic) }
                val l = lab[it.activityInfo.packageName] ?: o
                AppEntry(it.activityInfo.packageName, it.activityInfo.name, l, o, ic, b, chosung(l) + " " + chosung(o))
            }
            .sortedBy { it.label }
    }

    /** 메인 스레드에서: 새 목록으로 바꾸고 초기 배정 */
    fun setApps(list: List<AppEntry>) { apps = list; applyPreset() }

    fun reload(fresh: Boolean = false) = setApps(scan(fresh))

    /** 아이콘을 둥근 사각 하나로 맞춤 (샤넬팀 #18 · 성훈님 결정)
     *  - 요즘 방식(AdaptiveIcon): 시스템 모양 그대로
     *  - 옛 방식(흰 정사각 등): 둥근 사각(모서리 23%)으로 자르고 빈 곳은 아이보리 */
    private fun shape(d: Drawable): Bitmap {
        val size = (52 * ctx.resources.displayMetrics.density * 2).toInt()   // 52dp의 2배로 만들어 선명하게
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            d.setBounds(0, 0, size, size); d.draw(c)
        } else {
            val rad = size * .23f
            val path = Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), rad, rad, Path.Direction.CW) }
            c.save(); c.clipPath(path)
            c.drawColor(Style.IVORY)
            d.setBounds(0, 0, size, size); d.draw(c)
            c.restore()
        }
        return bmp
    }

    private val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private fun chosung(s: String) = buildString {
        s.forEach { ch -> if (ch in '\uAC00'..'\uD7A3') append(CHO[(ch.code - 0xAC00) / 588]) else if (!ch.isWhitespace()) append(ch.lowercaseChar()) }
    }

    private fun applyPreset() {
        var changed = false
        apps.forEach { a ->
            if (a.pkg in presetDone || foldersFor(a.pkg).isNotEmpty()) return@forEach
            val key = norm(a.orig)
            val f = preset[key] ?: prefixes.entries.firstOrNull { key.startsWith(it.key) }?.value
            if (f != null && f in folderIds) {
                foldersOf.getOrPut(a.pkg) { LinkedHashSet() }.add(f); presetDone.add(a.pkg); changed = true
            }
        }
        if (changed) save()
    }

    // ───────── 폴더 ─────────
    fun nameOf(id: String) = names[id] ?: id

    /** 가나다순 (한글 이름 먼저, 그다음 영문) — 성훈님 원칙 */
    fun folders(): List<String> = folderIds.sortedWith(compareBy<String>({ if (nameOf(it).firstOrNull()?.let { c -> c in '가'..'힣' } == true) 0 else 1 }, { nameOf(it) }))

    fun folderCount() = folderIds.size
    fun isRenamed(id: String) = id in DEFAULT_FOLDERS && names.containsKey(id)
    fun hasRenamedDefaults() = names.keys.any { it in DEFAULT_FOLDERS }

    /** 새 폴더 기본 이름 ('새 폴더', 겹치면 '새 폴더 2') */
    fun newFolderName(): String {
        val taken = folderIds.map { nameOf(it) }.toSet()
        var n = "새 폴더"; var k = 2
        while (n in taken) { n = "새 폴더 $k"; k++ }
        return n
    }

    /** 저장을 누를 때 만듦 (취소하면 아무것도 안 생김 — 샤넬팀 #2) */
    fun addFolder(name: String): String? {
        if (folderIds.size >= MAX_FOLDERS) return null
        val id = "u" + System.currentTimeMillis()
        folderIds.add(id); names[id] = name.trim().ifEmpty { newFolderName() }; save()
        return id
    }

    /** 지운 기본 폴더 되살리기 (샤넬팀 #19) */
    fun deletedDefaults() = DEFAULT_FOLDERS.filter { it !in folderIds }
    fun restoreDefaults() {
        deletedDefaults().forEach { if (folderIds.size < MAX_FOLDERS) folderIds.add(it) }
        save()
    }

    /** 폴더 카드 안내 문구는 처음 3번만 (샤넬팀 #10) */
    fun takeHint(): Boolean {
        val n = prefs.getInt("hintShown", 0)
        if (n >= 3) return false
        prefs.edit().putInt("hintShown", n + 1).apply(); return true
    }

    fun renameFolder(id: String, name: String) {
        val t = name.trim()
        if (t.isEmpty() || (id in DEFAULT_FOLDERS && t == id)) { if (id in DEFAULT_FOLDERS) names.remove(id) }
        else names[id] = t
        save()
    }

    fun resetName(id: String) { names.remove(id); save() }
    fun resetAllNames() { DEFAULT_FOLDERS.forEach { names.remove(it) }; save() }

    /** 삭제하면 다른 폴더에 없는 앱이 몇 개 UNSORTED로 가는지 */
    fun orphanCount(id: String) = appsIn(id).count { foldersFor(it.pkg).size == 1 }

    fun deleteFolder(id: String) {
        if (folderIds.size <= 1) return
        folderIds.remove(id)
        if (id !in DEFAULT_FOLDERS) names.remove(id)
        foldersOf.values.forEach { it.remove(id) }
        fOpens.remove(id); covers.remove(id)
        save()
    }

    // ───────── 앱 ↔ 폴더 (한 앱 여러 폴더) ─────────
    fun foldersFor(pkg: String): Set<String> = foldersOf[pkg]?.filter { it in folderIds }?.toSet() ?: emptySet()

    fun appsIn(id: String): List<AppEntry> = apps.filter { it.pkg !in hidden && foldersOf[it.pkg]?.contains(id) == true }

    fun unassigned(): List<AppEntry> = apps.filter { it.pkg !in hidden && foldersFor(it.pkg).isEmpty() }

    /** 끌어 놓기 = 항상 이동 (원래 폴더에서 빠지고 놓은 폴더에 들어감) */
    fun move(pkg: String, from: String?, to: String) {
        val s = foldersOf.getOrPut(pkg) { LinkedHashSet() }
        if (from != null && from != to) s.remove(from)
        s.add(to); presetDone.add(pkg); save()
    }

    /** '다른 폴더에도 넣기' 시트에서 고른 폴더들로 */
    fun setFolders(pkg: String, ids: Set<String>) {
        foldersOf[pkg] = LinkedHashSet(ids.filter { it in folderIds }); presetDone.add(pkg); save()
    }

    fun removeFrom(pkg: String, id: String) { foldersOf[pkg]?.remove(id); presetDone.add(pkg); save() }

    // ───────── 숨기기 · 앱 이름 ─────────
    fun isHidden(pkg: String) = pkg in hidden
    fun hide(pkg: String) { hidden.add(pkg); presetDone.add(pkg); save() }
    fun unhide(pkg: String) { hidden.remove(pkg); save() }
    fun hiddenApps(): List<AppEntry> = apps.filter { it.pkg in hidden }.distinctBy { it.pkg }

    /** 빈 칸으로 저장하면 원래 이름으로 */
    fun renameApp(pkg: String, name: String) {
        val t = name.trim()
        val orig = apps.firstOrNull { it.pkg == pkg }?.orig
        if (t.isEmpty() || t == orig) labels.remove(pkg) else labels[pkg] = t
        save(); reload()
    }

    // ───────── 표지 앱 ─────────
    fun customCover(id: String): List<String> = covers[id]?.filter { p -> appsIn(id).any { it.pkg == p } } ?: emptyList()
    fun hasCustomCover(id: String) = customCover(id).isNotEmpty()
    fun setCover(id: String, pkgs: List<String>) { if (pkgs.isEmpty()) covers.remove(id) else covers[id] = pkgs.take(4).toMutableList(); save() }
    fun clearCover(id: String) { covers.remove(id); save() }

    /** 표지 앱 4개: 직접 고른 앱 → 나머지는 그 폴더에서 많이 연 순서 */
    fun coverApps(id: String): List<AppEntry> {
        val list = appsIn(id).distinctBy { it.pkg }
        val picked = customCover(id).mapNotNull { p -> list.firstOrNull { it.pkg == p } }
        val fo = fOpens[id] ?: emptyMap<String, Int>()
        val rest = list.filter { a -> picked.none { it.pkg == a.pkg } }
            .sortedWith(compareByDescending<AppEntry> { fo[it.pkg] ?: 0 }.thenByDescending { opens[it.pkg] ?: 0 })
        return (picked + rest).take(4)
    }

    fun search(q: String): List<AppEntry> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        val q = t.replace(" ", "")
        return apps.filter { it.label.lowercase().contains(t) || it.orig.lowercase().contains(t) || it.pkg.lowercase().contains(t) || it.cho.contains(q) }
    }

    /** 실제로 많이 연 폴더 상위 3개 (금 테두리) — 앱을 연 그 폴더에만 셈 */
    fun favorites(): Set<String> =
        fOpens.filterKeys { it in folderIds }.mapValues { it.value.values.sum() }.filter { it.value > 0 }
            .entries.sortedByDescending { it.value }.take(3).map { it.key }.toSet()

    fun launch(a: AppEntry, folder: String?) {
        opens[a.pkg] = (opens[a.pkg] ?: 0) + 1
        if (folder != null && folder in folderIds) {
            val m = fOpens.getOrPut(folder) { HashMap() }; m[a.pkg] = (m[a.pkg] ?: 0) + 1
        }
        save()
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(a.pkg, a.cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        ctx.startActivity(i)
    }

    // 초기화는 맨 끝에서 — 위의 모든 필드(CHO, bmpCache, preset 등)가 먼저 만들어진 뒤에 실행해야 함
    // (필드보다 init이 앞에 있으면 null이라 앱이 시작하자마자 꺼짐 — build-13 때와 같은 실수 방지)
    init {
        load()
        reload()
    }
}
