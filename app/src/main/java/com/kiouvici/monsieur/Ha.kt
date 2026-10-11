package com.kiouvici.monsieur

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Home Assistant REST — SETTINGS에 한 번 넣은 장기 토큰으로 상태 읽기·서비스 부르기 (뒤 스레드) */
object Ha {
    const val ATV = "media_player.geosil_geosil"           // 거실 Apple TV
    const val MARANTZ = "media_player.marantz_nr1711_ash"  // 마란츠 리시버 (전원만)
    const val SPOTIFY = "media_player.spotify_kiouvici"    // 스포티파이 (보낼 기기 목록 = source_list)
    const val LIVING = "script.eumag_geosillo"             // 거실에서 듣기

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun open(token: String, path: String, method: String): HttpURLConnection =
        (URL(Look.HA_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6000; readTimeout = 8000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }

    /** 여러 엔터티 상태를 한 번에 — 실패하면 null (토큰 없음·연결 안 됨) */
    fun states(token: String, ids: List<String>, cb: (Map<String, JSONObject>?) -> Unit) {
        if (token.isBlank()) { cb(null); return }
        io.execute {
            val out = HashMap<String, JSONObject>()
            var ok = true
            for (id in ids) {
                try {
                    val c = open(token, "/api/states/$id", "GET")
                    when (c.responseCode) {
                        200 -> out[id] = JSONObject(c.inputStream.bufferedReader().readText())
                        401, 403 -> ok = false
                    }
                    c.disconnect()
                } catch (e: Exception) { ok = false }
                if (!ok) break
            }
            main.post { cb(if (ok) out else null) }
        }
    }

    /** 서비스 부르기 (예: media_player / media_play_pause) */
    fun call(token: String, domain: String, service: String, data: JSONObject, cb: ((Boolean) -> Unit)? = null) {
        if (token.isBlank()) { cb?.invoke(false); return }
        io.execute {
            val ok = try {
                val c = open(token, "/api/services/$domain/$service", "POST")
                c.doOutput = true
                c.outputStream.use { it.write(data.toString().toByteArray()) }
                val r = c.responseCode in 200..299
                c.disconnect(); r
            } catch (e: Exception) { false }
            main.post { cb?.invoke(ok) }
        }
    }

    /** HA 그림 받기 (Spotify 앨범 그림 entity_picture — 상대 경로) */
    fun bitmap(token: String, path: String, cb: (android.graphics.Bitmap?) -> Unit) {
        if (token.isBlank() || path.isBlank()) { cb(null); return }
        io.execute {
            val b = try {
                val c = open(token, path, "GET")
                val r = if (c.responseCode == 200) c.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } else null
                c.disconnect(); r
            } catch (e: Exception) { null }
            main.post { cb(b) }
        }
    }

    /** 토큰 확인용 — /api/ 가 200이면 연결됨 */
    fun check(token: String, cb: (Boolean) -> Unit) {
        io.execute {
            val ok = try { val c = open(token, "/api/", "GET"); val r = c.responseCode == 200; c.disconnect(); r } catch (e: Exception) { false }
            main.post { cb(ok) }
        }
    }

    fun on(st: JSONObject?) = st != null && st.optString("state") !in listOf("off", "unavailable", "unknown", "standby", "")
    fun attr(st: JSONObject?, k: String) = st?.optJSONObject("attributes")?.optString(k, "")?.takeIf { it != "null" } ?: ""
    fun sources(st: JSONObject?): List<String> {
        val a: JSONArray = st?.optJSONObject("attributes")?.optJSONArray("source_list") ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }
}
