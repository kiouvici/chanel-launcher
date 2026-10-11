package com.kiouvici.monsieur

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import org.json.JSONObject

data class AppEntry(
    val pkg: String,
    val cls: String,
    val label: String,
    val icon: Drawable,
) {
    val key: String get() = "$pkg/$cls"
}

/** 앱 목록, 폴더 배정, 실행 횟수 */
class AppRepo(private val ctx: Context) {

    companion object {
        /** 폴더 30개, 가나다순 (성훈님 홈 화면 폴더 이름 그대로) */
        val FOLDERS = listOf(
            "건강", "게임", "공유&전송", "굿락", "금융", "기기관리", "날씨", "네비&지도", "대중교통", "동영상",
            "메모&일정", "사진&카메라", "생활편의", "설정", "쇼핑(오프라인)", "쇼핑(온라인)", "아기", "앱&파일", "업무", "여행",
            "외식", "웹툰&북", "음악&라디오", "인증&지갑", "인터넷", "자동차", "전화", "정부", "AI", "SNS",
        )
    }

    private val prefs = ctx.getSharedPreferences("monsieur", Context.MODE_PRIVATE)
    var apps: List<AppEntry> = emptyList()
        private set

    private val folderOf = HashMap<String, String>()
    private val opens = HashMap<String, Int>()

    /** 원UI 홈 폴더 캡처에서 옮긴 초기 배정(assets/folders.json).
     *  아직 폴더가 없는 앱만 이름으로 찾아 넣음 — 직접 옮긴 앱은 건드리지 않음 */
    private val prefixes = HashMap<String, String>()
    private val preset: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        try {
            val o = JSONObject(ctx.assets.open("folders.json").bufferedReader().readText())
            o.keys().forEach { f ->
                if (f in FOLDERS) {
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

    private fun norm(s: String) = s.lowercase().replace(Regex("\\s+"), "")

    init {
        loadMaps()
        reload()
    }

    private fun loadMaps() {
        folderOf.clear(); opens.clear()
        prefs.getString("folders", null)?.let { s ->
            val o = JSONObject(s)
            o.keys().forEach { k -> folderOf[k] = o.getString(k) }
        }
        prefs.getString("opens", null)?.let { s ->
            val o = JSONObject(s)
            o.keys().forEach { k -> opens[k] = o.getInt(k) }
        }
    }

    private fun saveMaps() {
        prefs.edit()
            .putString("folders", JSONObject(folderOf as Map<*, *>).toString())
            .putString("opens", JSONObject(opens as Map<*, *>).toString())
            .apply()
    }

    fun reload() {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(main, PackageManager.MATCH_ALL)
        apps = list.filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                AppEntry(
                    it.activityInfo.packageName,
                    it.activityInfo.name,
                    it.loadLabel(pm).toString(),
                    it.loadIcon(pm),
                )
            }
            .sortedBy { it.label }
        applyPreset()
    }

    private fun applyPreset() {
        var changed = false
        apps.forEach { a ->
            if (folderOf[a.pkg] !in FOLDERS) {
                val key = norm(a.label)
                val f = preset[key] ?: prefixes.entries.firstOrNull { key.startsWith(it.key) }?.value
                f?.let { folderOf[a.pkg] = it; changed = true }
            }
        }
        if (changed) saveMaps()
    }

    /** 앱 묶음 키: 패키지 기준으로 폴더를 기억 (같은 앱의 여러 진입점도 함께 이동) */
    fun folderOf(a: AppEntry): String? = folderOf[a.pkg]

    fun appsIn(folder: String): List<AppEntry> = apps.filter { folderOf[it.pkg] == folder }

    fun unassigned(): List<AppEntry> = apps.filter { folderOf[it.pkg] !in FOLDERS }

    fun move(pkg: String, folder: String) {
        folderOf[pkg] = folder; saveMaps()
    }

    fun search(q: String): List<AppEntry> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return emptyList()
        return apps.filter { it.label.lowercase().contains(t) || it.pkg.lowercase().contains(t) }
    }

    /** 실제로 많이 연 폴더 상위 3개 (금 테두리) */
    fun favorites(): Set<String> {
        val per = HashMap<String, Int>()
        opens.forEach { (pkg, n) -> folderOf[pkg]?.let { per[it] = (per[it] ?: 0) + n } }
        return per.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(3).map { it.key }.toSet()
    }

    fun launch(a: AppEntry) {
        opens[a.pkg] = (opens[a.pkg] ?: 0) + 1; saveMaps()
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(a.pkg, a.cls))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        ctx.startActivity(i)
    }

    /** 폴더 배정을 한꺼번에 넣기 (캡처로 받은 초기 구성용) */
    fun importAssignments(map: Map<String, String>) {
        folderOf.putAll(map); saveMaps()
    }
}
