package com.kiouvici.monsieur

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/** 미디어 페이지가 앱에 부탁하는 일 */
interface MediaHost {
    val look: Look
    fun pickSheet(kicker: String, items: List<String>, current: String, note: String?, onPick: (String) -> Unit)
    /** 소리 창: cur 0 이 폰 · 1 Marantz, dot = 테마색. 고르면 0 이 폰 · 1 Marantz · 2 블루투스 */
    fun soundSheet(cur: Int, dot: Int, onPick: (Int) -> Unit)
    fun askToken()
    fun photo(): android.graphics.drawable.Drawable
    fun openApp(pkg: String): Boolean
    fun openNotifAccess()
    fun hasApp(pkg: String): Boolean
}

/** 표지 다음 장: 휴대폰에서 재생 중인 음악 + 집 기기 (테마마다 다른 얼굴, 샤넬팀 4차 확정안) */
@SuppressLint("ViewConstructor")
class MediaPage(ctx: Context, private val host: MediaHost) : FrameLayout(ctx) {

    companion object {
        const val SPOTIFY_PKG = "com.spotify.music"
        const val GENIE_PKG = "com.ktmusic.geniemusic"
    }

    private val look get() = host.look
    private var theme = -1
    private var builtW = 0; private var builtH = 0
    private var visibleNow = false

    // ── 휴대폰 재생 (알림 접근 권한으로 읽음 — 알림 점과 같은 권한)
    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val comp = ComponentName(ctx, BadgeService::class.java)
    private var ctl: MediaController? = null
    private var permOk = true
    private val ctlCb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { bind() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { bind() }
        override fun onSessionDestroyed() { pickSession() }
    }
    private val sessL = MediaSessionManager.OnActiveSessionsChangedListener { pickSession() }

    // ── 집 기기 (HA)
    private var home: Map<String, JSONObject>? = null
    private var haOk = false

    // ── 테마가 만들어 둔 화면 조각
    private var art: ArtView? = null
    private var title: TextView? = null
    private var sub: TextView? = null
    private var src: TextView? = null
    private var t0: TextView? = null
    private var t1: TextView? = null
    private var prog: ((Float) -> Unit)? = null
    private var play: Glyph? = null
    private var playText: TextView? = null
    private var out: TextView? = null
    private var sentence: TextView? = null
    private var whenLine: TextView? = null
    private var flaps: List<TextView> = emptyList()
    private var flapNote: TextView? = null
    private var spin: View? = null
    private var spinAnim: ObjectAnimator? = null
    private var flowArt: View? = null
    private var flowAnim: ObjectAnimator? = null
    private val nowViews = ArrayList<View>()      // 재생 없음이면 숨길 것
    private val houseViews = ArrayList<View>()    // 집 모두 꺼짐이면 숨길 것
    private val emptyViews = ArrayList<View>()    // 재생 없음일 때만 보일 것
    private var emptyBox: View? = null
    private var quietBox: View? = null
    private var quietTitle: TextView? = null
    private var quietBtns: LinearLayout? = null
    private var atvName: TextView? = null
    private var atvSub: TextView? = null
    private var atvBtn: View? = null
    private var atvArt: ArtView? = null
    private var mzState: TextView? = null
    private var mzBtn: TextView? = null
    private var mzName: TextView? = null
    private var lightBg = false
    // 볼륨 (재생 중인 기기: 0 PHONE · 1 MARANTZ, 마란츠 상한 50)
    private var vol: VolBar? = null
    private var volLbl: TextView? = null
    private var volVal: TextView? = null
    private var volTarget = 0
    private var volTouch = 0L
    private var volSent = 0L
    private var volPrefix: CharSequence = ""     // CAPTION 'VOLUME · ' · LIBRARY 'Volume · '
    private var mzPend = -1; private var mzPendAt = 0L   // Marantz에 보낸 값: HA가 같은 값을 알려줄 때까지(최대 5초) 유지
    // 소리 옮기기: 0 이 폰 · 1 Marantz, 8초 넘으면 실패
    private var moveTo = -1; private var moveAt = 0L; private var moveFail = false; private var lastMove = -1
    private var outMore: TextView? = null
    private var spSig = ""
    private var progView: LineProg? = null
    private var libHead: TextView? = null
    private var l1: TextView? = null
    private var mzHead: TextView? = null
    private val am by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    init { clipChildren = true }

    val isLight get() = lightBg

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0 && (w != builtW || h != builtH)) post { rebuild(true) }
    }

    /** 테마를 바꿨거나 화면 크기가 바뀌면 다시 그림 */
    fun rebuild(force: Boolean = false) {
        if (width == 0 || height == 0) return
        if (!force && theme == look.theme && builtW == width && builtH == height) return
        theme = look.theme; builtW = width; builtH = height
        stopAnims()
        removeAllViews(); nowViews.clear(); houseViews.clear(); emptyViews.clear()
        art = null; title = null; sub = null; src = null; t0 = null; t1 = null; prog = null; play = null; playText = null
        out = null; sentence = null; whenLine = null; flaps = emptyList(); flapNote = null; spin = null; flowArt = null
        atvName = null; atvSub = null; atvBtn = null; atvArt = null; mzState = null; mzBtn = null; mzName = null; vol = null; volLbl = null; volVal = null; outMore = null; l1 = null; mzHead = null; progView = null; libHead = null; volPrefix = ""
        val L = Lay(context, this, width, height)
        when (theme) {
            Look.LIBRARY -> buildLibrary(L)
            Look.HOUR -> buildHour(L)
            Look.TRAIN -> buildTrain(L)
            Look.CAPTION -> buildCaption(L)
            else -> buildMonsieur(L)
        }
        bind(); bindHome()
    }

    // ───────── 보일 때만 읽고 움직임 ─────────
    fun setShown(on: Boolean) {
        if (on == visibleNow) return
        visibleNow = on
        if (on) {
            try { msm.addOnActiveSessionsChangedListener(sessL, comp); permOk = true } catch (e: SecurityException) { permOk = false }
            pickSession(); pollHa(); removeCallbacks(tick); post(tick)
            try {
                val f = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(volRx, f, Context.RECEIVER_EXPORTED) else context.registerReceiver(volRx, f)
            } catch (_: Exception) {}
        } else {
            try { msm.removeOnActiveSessionsChangedListener(sessL) } catch (_: Exception) {}
            removeCallbacks(tick); removeCallbacks(haLoop); stopAnims()
            try { context.unregisterReceiver(volRx) } catch (_: Exception) {}
        }
    }

    private fun pickSession() {
        val list = try { msm.getActiveSessions(comp).also { permOk = true } } catch (e: SecurityException) { permOk = false; emptyList() }
        val pick = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.isNotBlank() == true }
        if (pick?.sessionToken != ctl?.sessionToken) {
            ctl?.unregisterCallback(ctlCb)
            ctl = pick
            pick?.registerCallback(ctlCb, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        bind()
    }

    private val tick = object : Runnable {
        override fun run() {
            updateProgress(); updateVol()
            if (visibleNow) postDelayed(this, 500)
        }
    }
    private var haTicks = 0
    private val haLoop = Runnable { pollHa() }
    private fun pollHa() {
        removeCallbacks(haLoop)
        if (demo > 0) { home = demoHome(); haOk = true; bindHome(); return }
        Ha.states(look.haToken, listOf(Ha.ATV, Ha.MARANTZ, Ha.SPOTIFY)) { m ->
            home = m; haOk = m != null; bindHome()
            if (visibleNow) postDelayed(haLoop, if (moveTo >= 0 || mzPend >= 0) 1000 else 5000)
        }
        haTicks++
    }

    private fun stopAnims() {
        spinAnim?.cancel(); spinAnim = null
        flowAnim?.cancel(); flowAnim = null
        removeCallbacks(flapLoop)
    }

    // ───────── 값 채우기 ─────────
    /** 화면 점검용 가짜 재생 (adb --ei demo 1 폰 · 2 Marantz) — 사람이 쓸 일은 없음 */
    var demo = 0
        set(v) {
            field = v
            if (v > 0) { home = demoHome(); haOk = true }
            val rv = Style.img(context, "th/riviera_photo.webp")
            if (v == 3) { look.lastTitle = "Heaven Knows"; look.lastArtist = "성시경, Raisa"; look.lastAlbum = "Heaven Knows"; look.lastPkg = SPOTIFY_PKG; lastArt = rv; lastArtLoaded = true }
            if (v == 4) { haArt = rv; haArtPath = "/demo" }
            if (width > 0) { bind(); bindHome() }
        }
    private fun demoHome(): Map<String, JSONObject> = mapOf(
        Ha.ATV to JSONObject().put("state", "off").put("attributes", JSONObject()),
        Ha.MARANTZ to JSONObject().put("state", if (demo == 2 || demo == 4) "playing" else "on").put("attributes", JSONObject().put("volume_level", .23)),
        Ha.SPOTIFY to JSONObject().put("state", if (demo == 3) "idle" else "playing").put("attributes", JSONObject().put("source", if (demo == 2 || demo == 4) "Marantz NR1711" else "Galaxy S26 Ultra")
            .put("media_title", if (demo == 4) "Heaven Knows" else "").put("media_artist", "성시경, Raisa").put("media_album_name", "Heaven Knows")
            .put("entity_picture", "/demo").put("media_duration", 244).put("media_position", 74).put("media_position_updated_at", java.time.OffsetDateTime.now().toString())))
    // ── 무엇을 보여 줄지: 0 없음 · 1 폰 재생 · 2 HA Spotify(다른 기기) · 3 마지막 곡
    private fun ctlTitle() = ctl?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: ""
    private val ctlPlaying get() = ctl?.playbackState?.state == PlaybackState.STATE_PLAYING
    private val spSt get() = home?.get(Ha.SPOTIFY)
    private val haPlaying get() = haOk && spSt?.optString("state") == "playing" && Ha.attr(spSt, "media_title").isNotEmpty()
    private val haPaused get() = haOk && spSt?.optString("state") == "paused" && Ha.attr(spSt, "media_title").isNotEmpty()
    private val mode: Int get() = when {
        demo == 1 || demo == 2 -> 1
        demo == 3 -> 3
        ctlTitle().isNotEmpty() && ctlPlaying -> 1
        haPlaying -> 2
        ctlTitle().isNotEmpty() -> 1
        haPaused -> 2
        look.lastTitle.isNotEmpty() -> 3
        else -> 0
    }
    private fun meta(k: String): String {
        if (demo == 1 || demo == 2) return when (k) {
            MediaMetadata.METADATA_KEY_TITLE -> "Heaven Knows"; MediaMetadata.METADATA_KEY_ARTIST -> "성시경, Raisa"; MediaMetadata.METADATA_KEY_ALBUM -> "Heaven Knows"; else -> "" }
        return when (mode) {
            1 -> ctl?.metadata?.getString(k)?.takeIf { it.isNotBlank() } ?: ""
            2 -> when (k) {
                MediaMetadata.METADATA_KEY_TITLE -> Ha.attr(spSt, "media_title"); MediaMetadata.METADATA_KEY_ARTIST -> Ha.attr(spSt, "media_artist")
                MediaMetadata.METADATA_KEY_ALBUM -> Ha.attr(spSt, "media_album_name"); else -> "" }
            3 -> when (k) {
                MediaMetadata.METADATA_KEY_TITLE -> look.lastTitle; MediaMetadata.METADATA_KEY_ARTIST -> look.lastArtist
                MediaMetadata.METADATA_KEY_ALBUM -> look.lastAlbum; else -> "" }
            else -> ""
        }
    }
    private val hasNow get() = mode != 0
    private val playing get() = when (mode) { 1 -> demo == 1 || ctlPlaying; 2 -> haPlaying; else -> false }
    private fun appName(): String {
        if (demo > 0) return "Spotify"
        val pkg = when (mode) { 1 -> ctl?.packageName; 2 -> SPOTIFY_PKG; 3 -> look.lastPkg.ifEmpty { SPOTIFY_PKG }; else -> null } ?: return ""
        return try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { if (pkg == SPOTIFY_PKG) "Spotify" else pkg }
    }
    private var haArt: Bitmap? = null; private var haArtPath = ""
    private var lastArt: Bitmap? = null; private var lastArtLoaded = false
    private val lastArtFile get() = java.io.File(context.filesDir, "last_art.png")
    private fun artBmp(): Bitmap? = if (demo == 1 || demo == 2) Style.img(context, "th/riviera_photo.webp") else when (mode) {
        1 -> ctl?.metadata?.let { it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) }
        2 -> { loadHaArt(); haArt }
        3 -> { if (!lastArtLoaded) { lastArtLoaded = true; lastArt = try { android.graphics.BitmapFactory.decodeFile(lastArtFile.path) } catch (e: Exception) { null } }; lastArt }
        else -> null
    }
    private fun loadHaArt() {
        val path = Ha.attr(spSt, "entity_picture")
        if (path.isEmpty() || path == haArtPath) return
        haArtPath = path
        Ha.bitmap(look.haToken, path) { b -> if (path == haArtPath) { haArt = b; if (b != null) saveLast(b); bind() } }
    }
    /** 지금 곡을 '마지막 곡'으로 적어 둠 (제목이 바뀔 때만) */
    private var savedKey = ""
    private fun saveLast(artOverride: Bitmap? = null) {
        val m = mode
        if (demo > 0 || (m != 1 && m != 2)) return
        val t = meta(MediaMetadata.METADATA_KEY_TITLE); if (t.isEmpty()) return
        val key = t + "|" + (artOverride != null)
        val newSong = t != look.lastTitle
        if (key == savedKey && !newSong) return
        savedKey = key
        look.lastTitle = t; look.lastArtist = meta(MediaMetadata.METADATA_KEY_ARTIST); look.lastAlbum = meta(MediaMetadata.METADATA_KEY_ALBUM)
        look.lastPkg = if (m == 1) ctl?.packageName ?: SPOTIFY_PKG else SPOTIFY_PKG
        val b = artOverride ?: artBmp() ?: return
        lastArt = b; lastArtLoaded = true
        val f = lastArtFile
        Thread { try { f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Exception) {} }.start()
    }

    /** 지금 소리가 나는 곳 (블루투스 이어폰 이름 · 유선 · 이 폰) */
    private fun outputName(): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = devs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == 26 || it.type == 27 }   // 26·27 = BLE 헤드셋·스피커
        if (bt != null) return bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "블루투스"
        val wired = devs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        if (wired != null) return "유선 이어폰"
        return "이 폰 스피커"
    }

    private fun bind() {
        if (theme < 0) return
        permOk = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        fillEmpty()
        val now = hasNow
        nowViews.forEach { it.visibility = if (now) View.VISIBLE else View.INVISIBLE }
        emptyViews.forEach { it.visibility = if (now) View.GONE else View.VISIBLE }
        emptyBox?.visibility = if (now) View.GONE else View.VISIBLE
        if (!now) { stopAnims(); art?.bmp = null; if (theme == Look.TRAIN) art?.placeholder = 1; updateVol(); return }
        art?.placeholder = 0
        saveLast()
        progView?.idle = mode == 3   // 마지막 곡: 바탕선만 (손잡이·리본·채움 숨김)
        val t = meta(MediaMetadata.METADATA_KEY_TITLE)
        val ar = meta(MediaMetadata.METADATA_KEY_ARTIST).ifEmpty { meta(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) }
        val al = meta(MediaMetadata.METADATA_KEY_ALBUM)
        val app = appName()
        art?.bmp = artBmp()
        val outN = if (volTarget == 1) "Marantz NR1711" else if (mode == 2) Ha.attr(spSt, "source").ifEmpty { "Spotify" } else outputName()
        when (theme) {
            Look.LIBRARY -> {
                val sb = android.text.SpannableStringBuilder((if (mode == 3) "You last listened to" else "You are listening to") + " “$t,” by ${ar.ifEmpty { "someone" }}.")
                Regex("[\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7A3]+([\\s,·]+[\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7A3]+)*").findAll(sb).forEach {
                    sb.setSpan(android.text.style.TypefaceSpan(Fonts.serifKR(context)), it.range.first, it.range.last + 1, 0)
                    sb.setSpan(android.text.style.RelativeSizeSpan(.88f), it.range.first, it.range.last + 1, 0)
                }
                sentence?.text = sb
                title?.text = if (al.isNotEmpty()) "Plate · $al" else "Plate · $app"
                val cal = java.util.Calendar.getInstance()
                val day = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                libHead?.text = if (mode == 3) "LAST PLAYED" else "NOW PLAYING"
                whenLine?.text = if (mode == 3) "Earlier — on this phone" else "%s, %d:%02d — %s".format(day, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), "on " + where(false))
                out?.text = if (moveFail || moveTo >= 0) outLabel(outN, "") else android.text.SpannableStringBuilder("Listening on ").append(outN,
                    android.text.style.TypefaceSpan(Style.kr(context, 300)), 0).append(" — ").append("바꾸기", android.text.style.UnderlineSpan(), 0)
            }
            Look.TRAIN -> {
                title?.text = t
                sub?.text = ar
                out?.text = outLabel(outN, "")
            }
            Look.CAPTION -> {
                title?.text = t
                out?.text = outLabel(outN, "")
                l1?.text = if (mode == 3) "LAST PLAYED —" else "NOW, ON " + where(false).uppercase() + " —"
            }
            Look.HOUR -> {
                title?.text = t
                sub?.text = listOf(ar, al).filter { it.isNotEmpty() }.joinToString(" · ")
                out?.text = outLabel(outN, "›")
            }
            else -> {
                title?.text = t
                sub?.text = listOf(ar, al).filter { it.isNotEmpty() }.joinToString(" · ")
                src?.text = "${app.uppercase()} · " + if (mode == 3) "LAST PLAYED" else where(true).uppercase()
                out?.text = outLabel(outN, "›")
            }
        }
        outMore?.visibility = if (moveFail || moveTo >= 0) View.INVISIBLE else View.VISIBLE
        play?.set(if (playing) 2 else 1)
        playText?.text = if (playing) "멈춤" else "재생"
        updateProgress()
        // 움직임: 재생 중에만 (레코드 회전 · 창밖 풍경)
        if (playing && visibleNow) {
            spin?.let { v -> if (spinAnim == null) spinAnim = ObjectAnimator.ofFloat(v, "rotation", v.rotation, v.rotation + 360f).apply {
                duration = 4000; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator(); start() } }
            flowArt?.let { v -> if (flowAnim == null) flowAnim = ObjectAnimator.ofFloat(v, "translationX", 0f, -v.width * .18f).apply {
                duration = 14000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start() } }
        } else { spinAnim?.cancel(); spinAnim = null; flowAnim?.cancel(); flowAnim = null }

    }

    /** 지금 재생되는 곳 이름: the Marantz · this phone · (HA Spotify 기기 이름) */
    private fun where(short: Boolean): String = when {
        volTarget == 1 -> if (short) "Marantz" else "the Marantz"
        mode == 2 -> Ha.attr(spSt, "source").ifEmpty { "Spotify" }
        else -> if (short) "Phone" else "this phone"
    }

    /** 소리 줄 글자: '소리 · 기기 ›' · 옮기는 중 · 실패 */
    private fun outLabel(outN: String, arrow: String): String = when {
        moveFail -> "소리 · 옮기지 못했습니다 · 다시"
        moveTo == 1 -> "소리 · Marantz로 옮기는 중…"
        moveTo == 0 -> "소리 · 이 폰으로 옮기는 중…"
        else -> "소리 · $outN" + if (arrow.isNotEmpty()) " $arrow" else ""
    }

    private fun updateProgress() {
        if (demo == 1 || demo == 2) { prog?.invoke(.3f); val a = "1:14"; val b = "4:04"
            when (theme) { Look.LIBRARY -> t1?.text = "$a / $b"; Look.CAPTION -> sub?.text = "성시경, Raisa · Heaven Knows · $a / $b"; else -> { t0?.text = a; t1?.text = b } }; return }
        if (mode == 2 || mode == 3) {
            var pos = 0L; var dur = 0L
            if (mode == 2) {
                dur = ((Ha.attr(spSt, "media_duration").toDoubleOrNull() ?: 0.0) * 1000).toLong()
                pos = ((Ha.attr(spSt, "media_position").toDoubleOrNull() ?: 0.0) * 1000).toLong()
                if (haPlaying) {
                    val at = try { java.time.OffsetDateTime.parse(Ha.attr(spSt, "media_position_updated_at")).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
                    if (at > 0) pos += System.currentTimeMillis() - at
                }
                if (dur > 0) pos = pos.coerceIn(0, dur)
            }
            prog?.invoke(if (dur > 0) pos / dur.toFloat() else 0f)
            val a = if (mode == 3) "" else fmtTime(pos); val b = if (mode == 3 || dur <= 0) "" else fmtTime(dur)
            when (theme) {
                Look.LIBRARY -> t1?.text = if (mode == 3) "" else "$a / $b"
                Look.CAPTION -> { val ar = meta(MediaMetadata.METADATA_KEY_ARTIST); val al = meta(MediaMetadata.METADATA_KEY_ALBUM)
                    sub?.text = (listOf(ar, al).filter { it.isNotEmpty() } + (if (mode == 3) "마지막으로 들은 곡" else "$a / $b")).joinToString(" · ") }
                else -> { t0?.text = if (mode == 3 && (theme == Look.HOUR || theme == Look.TRAIN)) "LAST PLAYED" else a; t1?.text = b }
            }
            return
        }
        val st = ctl?.playbackState ?: return
        val dur = ctl?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var pos = st.position
        if (st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0)
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        if (dur > 0) pos = pos.coerceIn(0, dur)
        val f = if (dur > 0) pos / dur.toFloat() else 0f
        prog?.invoke(f)
        when (theme) {
            Look.LIBRARY -> t1?.text = "${fmtTime(pos)} / ${fmtTime(dur)}"
            Look.TRAIN -> { t0?.text = fmtTime(pos); t1?.text = fmtTime(dur) }
            Look.CAPTION -> {
                val ar = meta(MediaMetadata.METADATA_KEY_ARTIST); val al = meta(MediaMetadata.METADATA_KEY_ALBUM)
                sub?.text = (listOf(ar, al).filter { it.isNotEmpty() } + "${fmtTime(pos)} / ${fmtTime(dur)}").joinToString(" · ")
            }
            Look.HOUR -> { t0?.text = fmtTime(pos); t1?.text = fmtTime(dur) }
            else -> { t0?.text = fmtTime(pos); t1?.text = fmtTime(dur) }
        }
    }

    // TRAIN 플랩: 14칸, 넘치면 단어 묶음으로 3초마다 다음 장
    private var flapChunks = listOf("")
    private var flapIdx = 0
    private fun setFlapText(s: String) {
        val words = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val chunks = ArrayList<String>(); var cur = ""
        words.forEach { w0 ->
            val w = w0.take(14)
            if (cur.isEmpty()) cur = w else if (cur.length + 1 + w.length <= 14) cur += " $w" else { chunks += cur; cur = w }
        }
        if (cur.isNotEmpty()) chunks += cur
        if (chunks != flapChunks) { flapChunks = chunks.ifEmpty { listOf("") }; flapIdx = 0; showFlap(false) }
    }
    private fun showFlap(anim: Boolean) {
        val c = flapChunks.getOrElse(flapIdx) { "" }
        val pad = (14 - c.length) / 2
        flaps.forEachIndexed { i, tv ->
            val ch = c.getOrNull(i - pad)?.toString() ?: ""
            if (tv.text != ch) {
                if (anim && !reduce()) tv.animate().scaleY(0f).setDuration(90).withEndAction { tv.text = ch; tv.animate().scaleY(1f).setDuration(110).start() }.start()
                else tv.text = ch
            }
        }
        flapNote?.text = if (flapChunks.size > 1) "${flapIdx + 1} / ${flapChunks.size}" else ""
    }
    private val flapLoop = Runnable {
        if (flapChunks.size > 1) { flapIdx = (flapIdx + 1) % flapChunks.size; showFlap(true) }
        if (visibleNow && theme == Look.TRAIN) postFlap()
    }
    private fun postFlap() { removeCallbacks(flapLoop); postDelayed(flapLoop, 3000) }
    private fun reduce() = android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /** 집 기기 줄 채우기 · 모두 꺼졌으면 '집은 조용합니다' — 기기 이름은 영어 (Apple TV · Marantz · Spotify) */
    private fun bindHome() {
        if (theme < 0) return
        val m = home
        val atv = m?.get(Ha.ATV); val mz = m?.get(Ha.MARANTZ)
        val noToken = demo == 0 && look.haToken.isBlank()
        val quiet = noToken || !haOk || (!Ha.on(atv) && !Ha.on(mz))
        houseViews.forEach { it.visibility = if (quiet) View.INVISIBLE else View.VISIBLE }
        quietBox?.visibility = if (quiet) View.VISIBLE else View.GONE
        val qt = when {
            noToken -> "HA에 연결하면 집 기기가 여기 놓입니다"
            !haOk -> "HA에 연결되지 않았습니다"
            else -> when (theme) { Look.LIBRARY -> "The house is quiet."; else -> "집은 조용합니다" }
        }
        quietTitle?.let { q ->
            q.text = qt
            if (qt.any { it.code > 0x3000 }) { q.typeface = Style.kr(context, 400); q.letterSpacing = 0f; q.textSize = 14f }
            else if (theme == Look.LIBRARY) { q.typeface = Fonts.serif(context); q.textSize = 22f }
        }
        quietBtns?.let { b ->
            (b.getChildAt(0) as? TextView)?.text = if (noToken || !haOk) "HA 연결" else "Marantz에서 듣기"
            b.getChildAt(1)?.visibility = if (noToken || !haOk) View.GONE else View.VISIBLE
        }
        val oldT = volTarget
        updateVol()
        var changed = oldT != volTarget
        if (moveTo >= 0) {
            if (volTarget == moveTo) { moveTo = -1; changed = true }
            else if (SystemClock.uptimeMillis() - moveAt > 8000) { lastMove = moveTo; moveTo = -1; moveFail = true; changed = true }
        }
        val sig = spSt?.let { it.optString("state") + Ha.attr(it, "media_title") + Ha.attr(it, "entity_picture") } ?: ""
        if (sig != spSig) { spSig = sig; changed = true }
        if (changed) bind()
        if (quiet) return
        val atvOn = Ha.on(atv)
        val app = Ha.attr(atv, "app_name").ifEmpty { Ha.attr(atv, "media_title") }
        val st = atv?.optString("state") ?: ""
        val stK = when (st) { "playing" -> "재생 중"; "paused" -> "멈춤"; "idle" -> "대기"; "off", "standby" -> "꺼짐"; else -> st }
        when (theme) {
            Look.LIBRARY, Look.CAPTION -> atvName?.text = "Apple TV" + (if (atvOn && app.isNotEmpty()) " — $app" else "")
            else -> { atvName?.text = "Apple TV"; atvSub?.text = if (atvOn) listOf(stK, app).filter { it.isNotEmpty() }.joinToString(" · ") else "꺼짐" }
        }
        val atvPlaying = st == "playing"
        (atvBtn as? Glyph)?.set(if (!atvOn) 4 else if (atvPlaying) 2 else 1)
        (atvBtn as? TextView)?.text = if (!atvOn) "켜기" else if (atvPlaying) "멈춤" else "재생"
        val mzOn = Ha.on(mz)
        val mzPlay = mz?.optString("state") == "playing" || volTarget == 1
        val mzV = (Ha.attr(mz, "volume_level").toDoubleOrNull()?.times(100))?.let { Math.round(it).toInt() }
        val vtxt = if (mzV != null) " · $mzV" else ""
        val stateK = if (!mzOn) "꺼짐" else if (mzPlay) "재생 중$vtxt" else "켜짐$vtxt"
        when (theme) {
            Look.LIBRARY, Look.CAPTION -> mzName?.text = "Marantz" + if (mzOn) " · $stateK" else ""
            else -> mzState?.text = stateK
        }
        mzHead?.text = "MARANTZ ● " + if (!mzOn) "꺼짐" else if (mzPlay) "재생 중" else "켜짐"
        mzBtn?.text = if (mzOn) "끄기" else "켜기"
    }

    /** 볼륨 대상: 폰에서 재생 중이면 PHONE, Spotify를 Marantz로 보냈거나 Marantz가 재생 중이면 MARANTZ */
    private fun updateVol() {
        val m = home
        val sp = m?.get(Ha.SPOTIFY); val mz = m?.get(Ha.MARANTZ)
        val spOnMz = sp?.optString("state") == "playing" && Ha.attr(sp, "source").contains("marantz", true)
        val remote = ctl?.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
        val phoneLocal = ctlPlaying && !remote
        val mzPlaying = mz?.optString("state") == "playing"
        volTarget = if (Ha.on(mz) && (spOnMz || (mzPlaying && !phoneLocal))) 1 else 0
        val v = vol ?: return
        v.max = if (volTarget == 1) 50 else 150
        setVolLabel(if (volTarget == 1) "MARANTZ" else "PHONE")
        if (SystemClock.uptimeMillis() - volTouch < 700) return
        val value = if (volTarget == 1) {
            val ha = Math.round((Ha.attr(mz, "volume_level").toDoubleOrNull() ?: 0.0) * 100).toInt()
            if (mzPend >= 0 && ha != mzPend && SystemClock.uptimeMillis() - mzPendAt < 5000) mzPend
            else { mzPend = -1; ha }
        } else phoneUnits()
        v.value = value; volVal?.text = "$value"
    }
    /** 폰 볼륨을 삼성 볼륨 창과 같은 0~150 숫자로 */
    private fun phoneUnits(): Int {
        FineVol.init(am)
        FineVol.get(am)?.let { return it }
        val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 150f / mx)
    }
    private fun setVolLabel(name: String) {
        val l = volLbl ?: return
        l.text = if (volPrefix.isEmpty()) name else android.text.SpannableStringBuilder(volPrefix).append(name)
    }
    private fun setVol(x: Int, final: Boolean) {
        volTouch = SystemClock.uptimeMillis()
        volVal?.text = "$x"
        if (volTarget == 0) {
            if (!FineVol.set(am, x)) {
                val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(x * mx / 150f), 0)
            }
        } else {
            val now = SystemClock.uptimeMillis()
            mzPend = x.coerceAtMost(50); mzPendAt = now
            if (!final && now - volSent < 250) return
            volSent = now
            Ha.call(look.haToken, "media_player", "volume_set", JSONObject().put("entity_id", Ha.MARANTZ).put("volume_level", mzPend / 100.0)) { if (final) pollLater() }
        }
    }
    /** 볼륨 줄: 막대(재생 막대와 같은 x0~x1, 가운데 y) · 아래 왼쪽 이름표 · 아래 오른쪽 숫자 (재생 시간과 같은 문법) */
    private fun volRow(L: Lay, x0: Float, x1: Float, y: Float, style: Int, color: Int, dim: Int, labelColor: Int, numColor: Int, labelTop: Float = 5f) {
        val vb = VolBar(context, style, color, dim).apply {
            padPx = L.zf(8f)
            onChange = { setVol(it, false) }; onRelease = { setVol(it, true) }
        }
        L.add(vb, x0 - 8f, y - 22f, x1 - x0 + 16f, 44f); vol = vb
        volLbl = L.t("PHONE", Style.jost(context, 400), 11f, labelColor, .12f).also { L.add(it, x0, y + labelTop) }
        volVal = L.t("0", Style.jost(context, 500), 11f, numColor, 0f, Gravity.END).apply { fontFeatureSettings = "tnum" }.also { L.add(it, x1 - 40f, y + labelTop, 40f) }
        nowViews += listOf(volLbl!!, vb, volVal!!)
    }
    private val volRx = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: android.content.Intent?) { if (volTarget == 0) updateVol() }
    }

    // ───────── 버튼 동작 ─────────
    private fun tap(v: View, go: () -> Unit): View {
        v.isClickable = true
        v.setOnTouchListener { vv, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> vv.animate().scaleX(.94f).scaleY(.94f).alpha(.85f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> vv.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
            }
            false
        }
        v.setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); go() }
        return v
    }
    private fun haSp(svc: String) = Ha.call(look.haToken, "media_player", svc, JSONObject().put("entity_id", Ha.SPOTIFY)) { pollLater(800) }
    private fun doPrev() { when (mode) { 1 -> ctl?.transportControls?.skipToPrevious(); 2 -> haSp("media_previous_track"); 3 -> resumeLast() } }
    private fun doNext() { when (mode) { 1 -> ctl?.transportControls?.skipToNext(); 2 -> haSp("media_next_track"); 3 -> resumeLast() } }
    private fun doPlay() {
        when (mode) {
            1 -> { val c = ctl?.transportControls ?: return; if (playing) c.pause() else c.play() }
            2 -> haSp(if (playing) "media_pause" else "media_play")
            3 -> resumeLast()
        }
    }
    /** 마지막 곡 이어 듣기: 앱을 깨워(삼성 미디어 위젯과 같은 방식 — 최근 재생 요청) 재생, 안 되면 재생 단추 신호 */
    private var browser: android.media.browse.MediaBrowser? = null
    private fun resumeLast() {
        val pkg = look.lastPkg.ifEmpty { SPOTIFY_PKG }
        fun mediaKey() {
            listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP).forEach {
                am.dispatchMediaKeyEvent(android.view.KeyEvent(it, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            postDelayed({ pickSession() }, 1500)
        }
        val svc = try {
            context.packageManager.queryIntentServices(android.content.Intent("android.media.browse.MediaBrowserService").setPackage(pkg), 0).firstOrNull()?.serviceInfo
        } catch (e: Exception) { null }
        if (svc == null) { mediaKey(); return }
        try { browser?.disconnect() } catch (_: Exception) {}
        var b: android.media.browse.MediaBrowser? = null
        b = android.media.browse.MediaBrowser(context, ComponentName(svc.packageName, svc.name), object : android.media.browse.MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                try { MediaController(context, b!!.sessionToken).transportControls.play() } catch (e: Exception) { mediaKey() }
                postDelayed({ pickSession(); try { b?.disconnect() } catch (_: Exception) {} }, 1500)
            }
            override fun onConnectionFailed() { mediaKey() }
        }, android.os.Bundle().apply { putBoolean(android.service.media.MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) })
        browser = b
        try { b.connect() } catch (e: Exception) { mediaKey() }
    }
    /** 소리 줄: 실패 상태면 다시 시도, 아니면 소리 창 */
    private fun doOutput() {
        if (moveFail && lastMove >= 0) { moveFail = false; moveTo(lastMove); return }
        val dot = when (theme) { Look.HOUR -> H_GOLD; Look.TRAIN -> V_INK; Look.LIBRARY -> 0xFFA8262B.toInt(); else -> Style.INK }
        host.soundSheet(volTarget, dot) { pick ->
            when (pick) {
                2 -> systemOutput()
                else -> if (pick != volTarget || moveFail) moveTo(pick)
            }
        }
    }
    private fun systemOutput() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34 && android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher()) return
        } catch (_: Exception) {}
        try {
            context.startActivity(android.content.Intent("android.settings.panel.action.MEDIA_OUTPUT")
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctl?.packageName ?: context.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
    /** 0 이 폰 · 1 Marantz 로 Spotify 옮기기 — 누르는 즉시 '옮기는 중…', 1초마다 확인, 8초 넘으면 실패 */
    private fun moveTo(target: Int) {
        if (look.haToken.isBlank() || !haOk) { host.askToken(); return }
        fun start() { moveTo = target; moveAt = SystemClock.uptimeMillis(); moveFail = false; lastMove = target; bind(); pollLater(1000) }
        if (target == 1) {
            start()
            Ha.call(look.haToken, "script", "turn_on", JSONObject().put("entity_id", Ha.LIVING)) { pollLater(1000) }
            return
        }
        val sp = home?.get(Ha.SPOTIFY)
        val list = Ha.sources(sp)
        val name = phoneSpotifyName(list)
        if (name == null) {
            host.pickSheet("SPOTIFY · 이 폰은 어느 것입니까", list, "", if (list.isEmpty()) "Spotify 기기 목록이 비어 있습니다 · 이 폰에서 Spotify 앱을 한 번 열어 두면 나타납니다" else "한 번 고르면 다음부터는 바로 옮깁니다") { s ->
                look.spPhone = s; moveTo(0)
            }
            return
        }
        start()
        Ha.call(look.haToken, "media_player", "select_source", JSONObject().put("entity_id", Ha.SPOTIFY).put("source", name)) { pollLater(1000) }
    }
    /** Spotify 목록에서 이 폰 찾기: 한 번 고른 이름 → 폰 기기 이름 → 모델명 */
    private fun phoneSpotifyName(list: List<String>): String? {
        look.spPhone.takeIf { it.isNotEmpty() && list.contains(it) }?.let { return it }
        val dn = (android.provider.Settings.Global.getString(context.contentResolver, "device_name") ?: "").trim()
        val model = android.os.Build.MODEL ?: ""
        return list.firstOrNull { dn.isNotEmpty() && it.equals(dn, true) }
            ?: list.firstOrNull { dn.isNotEmpty() && (it.contains(dn, true) || dn.contains(it, true)) && !it.contains("marantz", true) }
            ?: list.firstOrNull { model.isNotEmpty() && it.contains(model, true) }
    }
    private fun doLiving() = moveTo(1)
    private fun doAtv() {
        val on = Ha.on(home?.get(Ha.ATV))
        Ha.call(look.haToken, "media_player", if (on) "media_play_pause" else "turn_on", JSONObject().put("entity_id", Ha.ATV)) { pollLater() }
    }
    private fun doMz() {
        val on = Ha.on(home?.get(Ha.MARANTZ))
        Ha.call(look.haToken, "media_player", if (on) "turn_off" else "turn_on", JSONObject().put("entity_id", Ha.MARANTZ)) { pollLater() }
    }
    private fun doMzOn() = Ha.call(look.haToken, "media_player", "turn_on", JSONObject().put("entity_id", Ha.MARANTZ)) { pollLater() }
    private fun pollLater(ms: Long = 900) { removeCallbacks(haLoop); postDelayed(haLoop, ms) }

    /** 재생 없음: 스포티파이·지니 열기 (설치된 것만) */
    private var emptyRow: LinearLayout? = null
    private var emptyMake: ((String) -> TextView)? = null
    private var emptyMsg: TextView? = null
    private var emptyMsgOk = ""
    private var emptyState = -1
    private fun emptyButtons(row: LinearLayout, make: (String) -> TextView) { emptyRow = row; emptyMake = make; emptyState = -1 }
    /** 재생 없음: 알림 접근이 없으면 '허용' 버튼, 있으면 스포티파이·지니 열기 */
    private fun fillEmpty() {
        val row = emptyRow ?: return; val make = emptyMake ?: return
        val st = if (permOk) 1 else 0
        if (st == emptyState) return
        emptyState = st
        row.removeAllViews()
        if (!permOk) {
            emptyMsg?.text = "알림 접근을 허용하면 이 폰에서 재생 중인 음악이 여기 놓입니다."
            val b = make(if (theme == Look.CAPTION) "알림 접근 허용 →" else "알림 접근 허용").apply { typeface = Style.kr(context, 400); letterSpacing = 0f }
            tap(b) { host.openNotifAccess() }
            row.addView(b, LinearLayout.LayoutParams(-2, -1))
            return
        }
        emptyMsg?.text = emptyMsgOk
        listOf(SPOTIFY_PKG to (if (theme == Look.HOUR || theme == Look.TRAIN) "SPOTIFY" else if (theme == Look.CAPTION) "스포티파이 열기 →" else "Spotify 열기"),
               GENIE_PKG to (if (theme == Look.HOUR || theme == Look.TRAIN) "GENIE" else if (theme == Look.CAPTION) "지니 열기 →" else "genie 열기")).forEach { (pkg, lbl) ->
            if (!host.hasApp(pkg)) return@forEach
            val b = make(lbl)
            tap(b) { host.openApp(pkg) }
            row.addView(b, LinearLayout.LayoutParams(-2, -1).apply { if (row.childCount > 0) leftMargin = context.dp(12f) })
        }
    }
    private fun quietButtons(row: LinearLayout, make: (String) -> TextView) {
        val a = make("Marantz에서 듣기"); tap(a) { if (look.haToken.isBlank() || !haOk) host.askToken() else doLiving() }
        val b = make("Marantz 켜기"); tap(b) { doMzOn() }
        row.addView(a, LinearLayout.LayoutParams(-2, -1))
        row.addView(b, LinearLayout.LayoutParams(-2, -1).apply { leftMargin = context.dp(10f) })
        quietBtns = row
    }
    /** 소리 줄 (VOYAGE·CAPTION): 왼쪽 '소리 · 기기', 오른쪽 '바꾸기', 아래 가는 선 — 줄 전체를 누름 */
    private fun soundLine(L: Lay, x0: Float, x1: Float, y: Float, sp: Float, color: Int, moreColor: Int, more: String, lineColor: Int): View {
        val r = FrameLayout(context)
        r.addView(View(context).apply { setBackgroundColor(lineColor) }, LayoutParams(-1, L.z(.75f).coerceAtLeast(1), Gravity.BOTTOM))
        out = L.t("", Style.kr(context, 300), sp, color, 0f, Gravity.START or Gravity.CENTER_VERTICAL).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        r.addView(out, LayoutParams(-1, -1).apply { rightMargin = L.z(80f) })
        outMore = L.t(more, Style.kr(context, 400), sp - 1.5f, moreColor, 0f, Gravity.END or Gravity.CENTER_VERTICAL)
        r.addView(outMore, LayoutParams(L.z(80f), -1, Gravity.END))
        L.add(tap(r) { doOutput() }, x0, y, x1 - x0, 44f)
        return r
    }
    private fun hRow() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private fun pad(v: TextView, l: Float, r: Float = l) = v.apply { setPadding(context.dp(l), 0, context.dp(r), 0); gravity = Gravity.CENTER }

    // ═════════ ① MONSIEUR — LA POCHETTE ═════════
    private fun buildMonsieur(L: Lay) {
        lightBg = false
        background = android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable(Style.INK), Style.tile(context, Style.GROSGRAIN, 256f)))
        val lid = View(context).apply { background = Pic(Style.img(context, "lid.webp"), Pic.COVER); alpha = .55f }
        addView(lid, LayoutParams(-1, L.y(64f)))
        addView(View(context).apply { background = vgrad(tint(.6f, Color.BLACK), Color.TRANSPARENT) }, LayoutParams(-1, L.z(24f)).apply { topMargin = L.y(64f) })
        // 습자지 (주름을 줄이려 아이보리 55%를 덮음)
        val tissue = FrameLayout(context).apply {
            background = android.graphics.drawable.LayerDrawable(arrayOf(Style.tile(context, "th/tissue.webp", 360f), android.graphics.drawable.ColorDrawable(tint(.70f, 0xFFF3EEE4.toInt()))))
            elevation = L.zf(10f)
        }
        L.addLR(tissue, 16f, 16f, 56f, 424f)
        // 레코드 + 슬리브
        val vinyl = View(context).apply { background = Pic(Style.img(context, "th/vinyl.webp"), Pic.CONTAIN); elevation = L.zf(11f) }
        L.add(vinyl, 150f, 80f, 236f, 236f); spin = vinyl
        val sleeve = FrameLayout(context).apply { setBackgroundColor(0xFF0D0D0D.toInt()); elevation = L.zf(13f) }
        L.add(sleeve, 44f, 80f, 236f, 236f)
        art = ArtView(context).also { sleeve.addView(it, LayoutParams(-1, -1).apply { val m = L.z(13f); setMargins(m, m, m, m) }) }
        // 재생 없음: 빈 종이 슬리브에 N°31 눌러 찍기
        val es = FrameLayout(context).apply { background = Style.tile(context, Style.PAPER, 256f) }
        es.addView(L.t("N°31", Style.jost(context, 300), 28f, 0xFFE3DCCF.toInt(), .2f, Gravity.CENTER).apply {
            setShadowLayer(L.zf(1f), 0f, L.zf(1f), Color.argb(150, 255, 255, 255)) }, LayoutParams(-1, -1))
        sleeve.addView(es, LayoutParams(-1, -1)); emptyViews += es
        val ink = Style.INK
        title = L.t("", Style.jost(context, 400), 24f, ink, .02f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; elevation = L.zf(12f) }
        L.addLR(title!!, 36f, 36f, 322f)
        sub = L.t("", Style.kr(context, 300), 13f, Style.ink(.72f), 0f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; elevation = L.zf(12f) }
        L.addLR(sub!!, 36f, 36f, 356f)
        src = L.t("", Style.jost(context, 400), 9f, Style.GILT, .3f, Gravity.CENTER).apply { elevation = L.zf(12f) }
        L.addLR(src!!, 36f, 36f, 386f)
        // 진행: 검정 선 + 둥근 손잡이
        val pv = LineProg(context, Style.ink(.22f), Style.INK, L.zf(1f), L.zf(2f), knobR = L.zf(5.5f), knobRing = 0xFFF3EEE4.toInt()).apply { elevation = L.zf(12f) }
        L.addLR(pv, 38.5f, 38.5f, 417f, 14f); progView = pv; prog = { pv.p = it }
        t0 = L.t("", Style.jost(context, 400), 11f, Style.ink(.65f), .1f).apply { elevation = L.zf(12f) }
        L.add(t0!!, 44f, 432f)
        t1 = L.t("", Style.jost(context, 400), 11f, Style.ink(.65f), .1f, Gravity.END).apply { elevation = L.zf(12f) }
        L.addLR(t1!!, 44f, 44f, 432f)
        // 버튼: 금 테두리 원 · 리본 단추 · 금 테두리 원
        val prev = FrameLayout(context).apply { background = shape(stroke = tint(.65f, GOLD_LT), strokePx = L.z(1f), oval = true); addView(Glyph(context, 0, GOLD_LT, 1.6f), LayoutParams(-1, -1).apply { val m = L.z(16f); setMargins(m, m, m, m) }) }
        val pl = FrameLayout(context).apply { background = Pic(Style.img(context, "th/btn_ribbon.webp"), Pic.CONTAIN); elevation = L.z(4f).toFloat() }
        play = Glyph(context, 2, Style.INK, 2.2f).also { pl.addView(it, LayoutParams(-1, -1).apply { val m = L.z(26f); setMargins(m, m, m, m) }) }
        val next = FrameLayout(context).apply { background = shape(stroke = tint(.65f, GOLD_LT), strokePx = L.z(1f), oval = true); addView(Glyph(context, 3, GOLD_LT, 1.6f), LayoutParams(-1, -1).apply { val m = L.z(16f); setMargins(m, m, m, m) }) }
        L.add(tap(prev) { doPrev() }, 92f, 514f, 52f, 52f); L.add(tap(pl) { doPlay() }, 170f, 504f, 72f, 72f); L.add(tap(next) { doNext() }, 268f, 514f, 52f, 52f)
        volRow(L, 44f, 368f, 608f, VolBar.LINE_TAB, Style.IVORY, Style.ivory(.3f), Style.ivory(.65f), Style.ivory(.65f))
        out = L.t("", Style.kr(context, 300), 14f, Style.ivory(.85f), 0f, Gravity.CENTER)
        L.addLR(tap(out!!) { doOutput() }, 16f, 16f, 648f, 44f)
        nowViews += listOf(vinyl, title!!, sub!!, src!!, pv, t0!!, t1!!, prev, pl, next, out!!)
        // 집 카드 (종이 + 안쪽 선): Apple TV · Marantz
        val card = FrameLayout(context).apply {
            background = Style.tile(context, Style.PAPER, 256f); foreground = InsetBox(Style.ink(.45f), L.zf(.75f), L.zf(4f)); elevation = L.zf(8f)
        }
        L.addLR(card, 16f, 16f, 700f, 136f)
        val CL = Lay(context, card, L.w - L.x(32f), L.z(136f), 380f, 136f)
        CL.add(CL.t("À LA MAISON", Style.jost(context, 400), 10f, Style.GILT, .3f), 16f, 14f)
        mzHead = CL.t("", Style.jost(context, 400), 10f, Style.ink(.7f), .1f, Gravity.END).also { CL.add(it, 180f, 13f, 184f); houseViews += it }
        fun mrow(top: Float, isAtv: Boolean) {
            val r = FrameLayout(context); CL.addLR(r, 16f, 16f, top, 48f)
            val R1 = Lay(context, r, CL.w - CL.x(32f), CL.z(48f), 348f, 48f)
            r.addView(View(context).apply { setBackgroundColor(Style.ink(.12f)) }, LayoutParams(-1, 1))
            if (isAtv) atvArt = ArtView(context).also { R1.add(it, 0f, 5f, 38f, 38f) }
            else R1.add(View(context).apply { setBackgroundColor(Style.INK) }, 0f, 5f, 38f, 38f)
            val nm = R1.t(if (isAtv) "Apple TV" else "Marantz", Style.jost(context, 400), 13.5f, Style.INK).also { R1.add(it, 50f, 6f) }
            val st = R1.t("", Style.kr(context, 300), 11.5f, Style.ink(.65f)).also { R1.add(it, 50f, 26f) }
            val b = pad(R1.t(if (isAtv) "켜기" else "켜기", Style.kr(context, 400), 12f, Style.IVORY), 0f).apply { setBackgroundColor(Style.INK) }
            R1.add(tap(b) { if (isAtv) doAtv() else doMz() }, 276f, 2f, 72f, 44f)
            if (isAtv) { atvName = nm; atvSub = st; atvBtn = b } else { mzName = nm; mzState = st; mzBtn = b }
            houseViews += r
        }
        mrow(36f, true); mrow(84f, false)
        // 비었을 때 · 조용할 때
        val q = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val qt = CL.t("집은 조용합니다", Style.kr(context, 400), 15f, Style.INK, 0f, Gravity.CENTER); quietTitle = qt
        q.addView(qt, LinearLayout.LayoutParams(-1, -2))
        val qb = hRow(); quietButtons(qb) { pad(CL.t(it, Style.kr(context, 400), 12.5f, Style.INK), 14f).apply { background = shape(stroke = Style.ink(.7f), strokePx = CL.z(1f)) } }
        q.addView(qb, LinearLayout.LayoutParams(-2, CL.z(44f)).apply { topMargin = CL.z(14f) })
        CL.addLR(q, 16f, 16f, 54f); quietBox = q
        val e = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; elevation = L.zf(14f) }
        e.addView(L.t("LA POCHETTE", Style.jost(context, 400), 10f, Style.GILT, .3f, Gravity.CENTER))
        e.addView(L.t("상자가 비어 있습니다", Style.kr(context, 400), 22f, Style.INK, 0f, Gravity.CENTER), LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(10f) })
        e.addView(L.t("음악을 틀면 레코드가 여기 놓입니다.", Style.kr(context, 300), 13f, Style.ink(.7f), 0f, Gravity.CENTER).also { emptyMsg = it; emptyMsgOk = "음악을 틀면 레코드가 여기 놓입니다." }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        val eb = hRow(); emptyButtons(eb) { pad(L.t(it, Style.kr(context, 400), 12.5f, Style.IVORY), 16f).apply { setBackgroundColor(Style.INK) } }
        e.addView(eb, LinearLayout.LayoutParams(-2, L.z(44f)).apply { topMargin = L.z(18f) })
        L.addLR(e, 28f, 28f, 380f); emptyBox = e
    }

    // ═════════ ② THE LIBRARY — LE PROGRAMME ═════════
    private fun buildLibrary(L: Lay) {
        lightBg = true
        background = android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable(0xFFF3EEE4.toInt()), Style.tile(context, Style.PAPER, 300f)))
        libHead = L.t("NOW PLAYING", Style.jost(context, 400), 10f, Style.ink(.55f), .35f).also { L.add(it, 36f, 70f) }
        whenLine = L.t("", Fonts.serifI(context), 17f, Style.ink(.7f)).also { L.add(it, 36f, 92f) }
        // 사진 모서리로 붙인 앨범아트
        val plate = FrameLayout(context).apply { setBackgroundColor(Color.WHITE); elevation = L.zf(3f); clipChildren = false }
        L.add(plate, 86f, 118f, 240f, 240f)
        art = ArtView(context).also { plate.addView(it, LayoutParams(-1, -1).apply { val m = L.z(10f); setMargins(m, m, m, m) }) }
        val corner = Style.img(context, "th/photo_corner.webp")
        listOf(Triple(-1f, -1f, -45f), Triple(1f, -1f, 45f), Triple(-1f, 1f, -135f), Triple(1f, 1f, 135f)).forEach { (sx, sy, rot) ->
            val c = View(context).apply { background = Pic(corner, Pic.CONTAIN); rotation = rot; elevation = L.zf(4f) }
            val cx = if (sx < 0) 86f - 9f else 86f + 240f - 44f + 9f
            val cy = if (sy < 0) 118f - 1f else 118f + 240f - 28f + 1f
            L.add(c, cx, cy, 44f, 28f); nowViews += c
        }
        title = L.t("", Fonts.serifI(context), 14f, Style.ink(.65f), 0f, Gravity.CENTER).also { L.addLR(it, 36f, 36f, 370f) }
        sentence = L.t("", Fonts.serif(context), 25f, Style.INK, 0f, Gravity.CENTER).apply { setLineSpacing(L.zf(4f), 1f); maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END }
        L.addLR(sentence!!, 36f, 36f, 396f)
        val pv = LineProg(context, Style.ink(.3f), Style.INK, L.zf(1f), L.zf(2f), marker = Style.img(context, "th/bookmark.webp"), markerW = L.zf(14f), markerH = L.zf(34f))
        L.addLR(pv, 29f, 29f, 476f, 38f); progView = pv; prog = { pv.p = it }
        t1 = L.t("", Style.jost(context, 400), 11f, Style.ink(.65f), .1f, Gravity.END).also { L.addLR(it, 36f, 36f, 489f) }
        val deb = Style.img(context, "th/deboss.webp")
        fun db(kind: Int, size: Float, icon: Float) = FrameLayout(context).apply {
            background = Pic(deb, Pic.CONTAIN)
            val g = Glyph(context, kind, Style.INK, if (size > 70f) 2.2f else 1.6f); addView(g, LayoutParams(-1, -1).apply { val m = L.z((size - icon) / 2); setMargins(m, m, m, m) })
            if (kind == 2) play = g
        }
        val prev = db(0, 52f, 16f); val pl = db(2, 72f, 20f); val next = db(3, 52f, 16f)
        L.add(tap(prev) { doPrev() }, 92f, 536f, 52f, 52f); L.add(tap(pl) { doPlay() }, 170f, 526f, 72f, 72f); L.add(tap(next) { doNext() }, 268f, 536f, 52f, 52f)
        volPrefix = android.text.SpannableStringBuilder().append("Volume", CustomFont(Fonts.serifI(context), 1.4f), 0).append("  ·  ")
        volRow(L, 36f, 376f, 630f, VolBar.RULER, Style.INK, Style.ink(.3f), Style.ink(.65f), Style.ink(.65f), 9f)
        out = L.t("", Fonts.serif(context), 16f, Style.ink(.75f), 0f, Gravity.CENTER).apply { maxLines = 1 }.also { L.addLR(tap(it) { doOutput() }, 36f, 36f, 670f, 44f) }
        nowViews += listOf(plate, title!!, sentence!!, pv, t1!!, prev, pl, next, out!!, whenLine!!)
        // 집: 점선 줄
        val hs = FrameLayout(context); L.addLR(hs, 36f, 36f, 722f, 120f)
        val H = Lay(context, hs, L.w - L.x(72f), L.z(120f), 340f, 120f)
        H.add(H.t("IN THE HOUSE", Style.jost(context, 400), 10f, Style.ink(.55f), .3f), 0f, 0f)
        fun lrow(top: Float, label: String, btn: String, go: () -> Unit): Pair<TextView, TextView> {
            val r = FrameLayout(context); H.addLR(r, 0f, 0f, top, 46f)
            r.addView(View(context).apply { setBackgroundColor(Style.ink(.15f)) }, LayoutParams(-1, 1))
            val t = H.t(label, Fonts.serif(context), 15f, Style.INK).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            r.addView(t, LayoutParams(H.z(220f), -2, Gravity.CENTER_VERTICAL))
            r.addView(DotLeader(context, Style.ink(.35f), H.zf(1f)), LayoutParams(-1, H.z(4f), Gravity.CENTER_VERTICAL).apply { leftMargin = H.z(226f); rightMargin = H.z(72f) })
            val b = pad(H.t(btn, Style.kr(context, 400), 12f, Style.INK), 10f).apply { background = shape(stroke = Style.ink(.55f), strokePx = H.z(1f)) }
            r.addView(tap(b, go), LayoutParams(H.z(64f), H.z(44f), Gravity.END or Gravity.CENTER_VERTICAL))
            houseViews += r; return t to b
        }
        lrow(24f, "Apple TV", "재생") { doAtv() }.let { atvName = it.first; atvBtn = it.second }
        lrow(70f, "Marantz", "켜기") { doMz() }.let { mzName = it.first; mzState = null; mzBtn = it.second }
        val q = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        quietTitle = H.t("", Fonts.serif(context), 22f, Style.INK, 0f, Gravity.CENTER); q.addView(quietTitle!!, LinearLayout.LayoutParams(-1, -2))
        val qb = hRow(); quietButtons(qb) { pad(H.t(it, Style.kr(context, 400), 12.5f, Style.INK), 14f).apply { background = shape(stroke = Style.ink(.6f), strokePx = H.z(1f)) } }
        q.addView(qb, LinearLayout.LayoutParams(-2, H.z(44f)).apply { topMargin = H.z(14f) })
        H.addLR(q, 0f, 0f, 30f); quietBox = q
        val e = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        e.addView(L.t("Nothing on the programme.", Fonts.serif(context), 32f, Style.INK, 0f, Gravity.CENTER))
        e.addView(L.t("음악을 틀면 이 쪽에 적힙니다.", Style.kr(context, 300), 13f, Style.ink(.7f), 0f, Gravity.CENTER).also { emptyMsg = it; emptyMsgOk = "음악을 틀면 이 쪽에 적힙니다." }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(10f) })
        val eb = hRow(); emptyButtons(eb) { pad(L.t(it, Style.kr(context, 400), 12.5f, Style.INK), 16f).apply { background = shape(stroke = Style.ink(.6f), strokePx = L.z(1f)) } }
        e.addView(eb, LinearLayout.LayoutParams(-2, L.z(44f)).apply { topMargin = L.z(18f) })
        L.addLR(e, 28f, 28f, 220f); emptyBox = e
        // 접힌 귀퉁이
        addView(EarView(context), LayoutParams(L.z(34f), L.z(34f), Gravity.BOTTOM or Gravity.END))
    }

    // ═════════ ③ THE HOUR — H3 (세라믹 판에 뚫린 창) ═════════
    private fun buildHour(L: Lay) {
        lightBg = false
        setBackgroundColor(0xFF0E0E0E.toInt())
        L.add(View(context).apply { background = plateDrawable(context) }, 24f, 52f, 364f, 461f)
        val aw = FrameLayout(context).apply { background = windowDrawable(context) }
        L.add(aw, 80.5f, 76f, 251f, 251f)
        art = ArtView(context).also { aw.addView(it, LayoutParams(-1, -1).apply { val m = L.z(2f); setMargins(m, m, m, m) }) }
        val tw = FrameLayout(context).apply { background = windowDrawable(context) }
        L.add(tw, 48f, 347f, 316f, 42f)
        title = L.t("", Style.jost(context, 300), 20f, H_IVORY, .02f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        tw.addView(title!!, LayoutParams(-1, -1).apply { leftMargin = L.z(10f); rightMargin = L.z(10f) })
        sub = L.t("", Style.kr(context, 300), 13f, tint(.65f, H_IVORY), 0f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }.also { L.addLR(it, 48f, 48f, 393f) }
        val pv = LineProg(context, tint(.2f, H_IVORY), H_GOLD, L.zf(1f), L.zf(2f)).also { L.add(it, 48f, 438f, 316f, 10f) }
        progView = pv; prog = { pv.p = it }
        t0 = L.t("", Style.jost(context, 400), 11f, tint(.6f, H_IVORY), .05f).also { L.add(it, 48f, 455f) }
        t1 = L.t("", Style.jost(context, 400), 11f, tint(.6f, H_IVORY), .05f, Gravity.END).also { L.add(it, 304f, 455f, 60f) }
        fun cb(kind: Int, size: Float) = FrameLayout(context).apply {
            background = shape(0xFF111111.toInt(), tint(.7f, H_GOLD), L.z(.75f).coerceAtLeast(1), oval = true)
            val g = Glyph(context, kind, H_IVORY, if (size > 60f) 2f else 1.6f); addView(g, LayoutParams(-1, -1).apply { val m = L.z(size * .32f); setMargins(m, m, m, m) }); if (kind == 2) play = g
        }
        val prev = cb(0, 52f); val pl = cb(2, 72f); val next = cb(3, 52f)
        L.add(tap(prev) { doPrev() }, 92f, 547f, 52f, 52f); L.add(tap(pl) { doPlay() }, 170f, 537f, 72f, 72f); L.add(tap(next) { doNext() }, 268f, 547f, 52f, 52f)
        volRow(L, 48f, 364f, 641f, VolBar.TICKS, H_GOLD, tint(.25f, H_IVORY), tint(.6f, H_IVORY), H_GOLD)
        out = L.t("", Style.kr(context, 400), 14f, tint(.85f, H_IVORY), 0f, Gravity.CENTER).apply { background = shape(stroke = tint(.6f, H_GOLD), strokePx = L.z(.75f).coerceAtLeast(1)); maxLines = 1 }
        L.add(tap(out!!) { doOutput() }, 24f, 681f, 364f, 44f)
        nowViews += listOf(aw, tw, sub!!, pv, t0!!, t1!!, prev, pl, next, out!!)
        // 집: Apple TV · Marantz
        fun hrow(top: Float, isAtv: Boolean) {
            val r = FrameLayout(context); L.addLR(r, 24f, 24f, top, 48f)
            val R1 = Lay(context, r, L.w - L.x(48f), L.z(48f), 364f, 48f)
            r.addView(View(context).apply { setBackgroundColor(tint(.2f, H_GOLD)) }, LayoutParams(-1, 1))
            val nm = R1.t(if (isAtv) "Apple TV" else "Marantz", Style.jost(context, 400), 15f, H_IVORY, .02f).also { R1.add(it, 0f, 13f) }
            val st = R1.t("", Style.kr(context, 300), 12.5f, tint(.6f, H_IVORY)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }.also { R1.add(it, 116f, 15f, 170f) }
            val b = R1.t("켜기", Style.kr(context, 400), 12f, H_GOLD, 0f, Gravity.CENTER).apply { background = shape(stroke = tint(.6f, H_GOLD), strokePx = R1.z(.75f).coerceAtLeast(1)) }
            R1.add(tap(b) { if (isAtv) doAtv() else doMz() }, 292f, 2f, 72f, 44f)
            if (isAtv) { atvName = nm; atvSub = st; atvBtn = b } else { mzName = nm; mzState = st; mzBtn = b }
            houseViews += r
        }
        hrow(740f, true); hrow(788f, false)
        val q = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        q.addView(L.t("À LA MAISON", Style.jost(context, 400), 10f, H_GOLD, .3f, Gravity.CENTER))
        quietTitle = L.t("", Style.kr(context, 400), 15f, tint(.9f, H_IVORY), 0f, Gravity.CENTER); q.addView(quietTitle!!, LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        val qb = hRow(); quietButtons(qb) { pad(L.t(it, Style.kr(context, 400), 12.5f, H_GOLD), 14f).apply { background = shape(stroke = tint(.6f, H_GOLD), strokePx = L.z(.75f).coerceAtLeast(1)) } }
        q.addView(qb, LinearLayout.LayoutParams(-2, L.z(44f)).apply { topMargin = L.z(14f) })
        L.addLR(q, 28f, 28f, 741f); quietBox = q
        val e = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        e.addView(L.t("GUICHET", Style.jost(context, 400), 10f, H_GOLD, .35f, Gravity.CENTER))
        e.addView(L.t("—", Style.jost(context, 200), 44f, H_IVORY, 0f, Gravity.CENTER), LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        e.addView(L.t("재생을 시작하면 이 창에 앨범이 놓입니다.", Style.kr(context, 300), 13f, tint(.75f, H_IVORY), 0f, Gravity.CENTER).also { emptyMsg = it; emptyMsgOk = "재생을 시작하면 이 창에 앨범이 놓입니다." }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        val eb = hRow(); emptyButtons(eb) { pad(L.t(it, Style.jost(context, 400), 12.5f, H_GOLD, .2f), 18f).apply { background = shape(stroke = tint(.6f, H_GOLD), strokePx = L.z(.75f).coerceAtLeast(1)) } }
        e.addView(eb, LinearLayout.LayoutParams(-2, L.z(44f)).apply { topMargin = L.z(18f) })
        L.addLR(e, 40f, 40f, 180f); emptyBox = e
    }

    // ═════════ ④ THE VOYAGE — 포스터 (남색 니트 + 줄무늬) ═════════
    private fun buildTrain(L: Lay) {
        lightBg = false
        background = jerseyDrawable(context, .4f)
        L.addLR(View(context).apply { background = stripesDrawable(context) }, 0f, 0f, 40f, 12f)
        L.addLR(View(context).apply { background = stripesDrawable(context) }, 0f, 0f, 863f, 12f)
        val poster = View(context).apply { setBackgroundColor(V_IVORY); elevation = L.zf(10f) }
        L.add(poster, 40f, 72f, 332f, 400f)
        val ps = View(context).apply { background = stripesDrawable(context); elevation = L.zf(10.5f) }
        L.add(ps, 56f, 84f, 300f, 12f)
        val av = ArtView(context).apply { elevation = L.zf(10.5f) }; art = av
        L.add(av, 56f, 108f, 300f, 300f)
        title = L.t("", Fonts.serif(context), 20f, V_INK, .02f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; elevation = L.zf(11f) }.also { L.addLR(it, 56f, 56f, 418f) }
        sub = L.t("", Style.kr(context, 300), 13f, tint(.7f, V_INK), 0f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; elevation = L.zf(11f) }.also { L.addLR(it, 56f, 56f, 446f) }
        val pv = LineProg(context, tint(.35f, V_IVORY), V_IVORY, L.zf(1f), L.zf(2f), knobR = L.zf(4f), knobRing = V_NAVY).also { L.addLR(it, 36f, 36f, 502f, 14f) }
        progView = pv; prog = { pv.p = it }
        t0 = L.t("", Style.jost(context, 400), 11f, tint(.7f, V_IVORY), .05f).also { L.add(it, 40f, 515f) }
        t1 = L.t("", Style.jost(context, 400), 11f, tint(.7f, V_IVORY), .05f, Gravity.END).also { L.add(it, 312f, 515f, 60f) }
        fun ib(kind: Int, size: Float) = FrameLayout(context).apply {
            background = shape(V_IVORY, oval = true); elevation = L.zf(3f)
            val g = Glyph(context, kind, V_INK, if (size > 60f) 2.2f else 1.8f, fill = kind != 2); addView(g, LayoutParams(-1, -1).apply { val m = L.z(size * .32f); setMargins(m, m, m, m) }); if (kind == 2) play = g
        }
        val prev = ib(0, 52f); val pl = ib(2, 72f); val next = ib(3, 52f)
        L.add(tap(prev) { doPrev() }, 92f, 548f, 52f, 52f); L.add(tap(pl) { doPlay() }, 170f, 538f, 72f, 72f); L.add(tap(next) { doNext() }, 268f, 548f, 52f, 52f)
        volRow(L, 40f, 372f, 642f, VolBar.LINE_DOT, V_IVORY, tint(.35f, V_IVORY), tint(.7f, V_IVORY), tint(.7f, V_IVORY))
        val sr = soundLine(L, 40f, 372f, 682f, 14f, tint(.85f, V_IVORY), V_IVORY, "바꾸기 ›", tint(.35f, V_IVORY))
        nowViews += listOf(sr, title!!, sub!!, pv, t0!!, t1!!, prev, pl, next)
        fun vrow(top: Float, isAtv: Boolean) {
            val r = FrameLayout(context).apply { setBackgroundColor(0xFFDCD5C6.toInt()) }; L.addLR(r, 40f, 40f, top, 44f)
            val R1 = Lay(context, r, L.w - L.x(80f), L.z(44f), 332f, 44f)
            val nm = R1.t(if (isAtv) "Apple TV" else "Marantz", Fonts.serif(context), 15f, V_INK, .02f).also { R1.add(it, 16f, 12f) }
            val st = R1.t("", Style.kr(context, 300), 12f, tint(.7f, V_INK)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }.also { R1.add(it, 116f, 14f, 136f) }
            val b = R1.t("켜기", Style.kr(context, 400), 12f, V_IVORY, 0f, Gravity.CENTER).apply { setBackgroundColor(V_INK) }
            R1.add(tap(b) { if (isAtv) doAtv() else doMz() }, 260f, 0f, 72f, 44f)
            if (isAtv) { atvName = nm; atvSub = st; atvBtn = b } else { mzName = nm; mzState = st; mzBtn = b }
            houseViews += r
        }
        vrow(738f, true); vrow(790f, false)
        val q = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        quietTitle = L.t("", Style.kr(context, 400), 14f, V_IVORY, 0f, Gravity.CENTER); q.addView(quietTitle!!, LinearLayout.LayoutParams(-1, -2))
        val qb = hRow(); quietButtons(qb) { pad(L.t(it, Style.kr(context, 400), 12.5f, V_INK), 14f).apply { setBackgroundColor(V_IVORY) } }
        q.addView(qb, LinearLayout.LayoutParams(-2, L.z(44f)).apply { topMargin = L.z(14f) })
        L.addLR(q, 28f, 28f, 738f); quietBox = q
        val e = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; elevation = L.zf(12f) }
        e.addView(L.t("THE VOYAGE", Style.jost(context, 500), 11f, tint(.7f, V_IVORY), .45f, Gravity.CENTER))
        e.addView(L.t("Bon voyage.", Fonts.serifI(context), 28f, V_IVORY, 0f, Gravity.CENTER), LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(10f) })
        e.addView(L.t("음악을 틀면 이 포스터에 앨범이 놓입니다.", Style.kr(context, 300), 13f, tint(.8f, V_IVORY), 0f, Gravity.CENTER).also { emptyMsg = it; emptyMsgOk = "음악을 틀면 이 포스터에 앨범이 놓입니다." }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        val eb = hRow(); emptyButtons(eb) { pad(L.t(it, Style.kr(context, 400), 12.5f, V_INK), 16f).apply { setBackgroundColor(V_IVORY) } }
        e.addView(eb, LinearLayout.LayoutParams(-2, L.z(44f)).apply { topMargin = L.z(18f) })
        L.addLR(e, 60f, 60f, 190f); emptyBox = e
    }

    // ═════════ ⑤ THE CAPTION — LA LÉGENDE ═════════
    private fun buildCaption(L: Lay) {
        lightBg = false
        setBackgroundColor(Style.INK)
        // 재생 없음: 표지 사진을 흑백으로 어둡게
        val ep = View(context).apply {
            background = host.photo(); alpha = .55f
            setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) }) })
        }
        addView(ep, LayoutParams(-1, L.w)); emptyViews += ep
        art = ArtView(context).also { addView(it, LayoutParams(-1, L.w)) }
        addView(View(context).apply { background = InsetBox(Style.ivory(.3f), L.zf(1f), 0f) }, LayoutParams(-1, L.w))
        addView(View(context).apply { background = vgrad(Color.TRANSPARENT, Style.INK) }, LayoutParams(-1, L.y(120f)).apply { topMargin = L.w - L.y(112f) })
        val credit = L.t("", Style.jost(context, 400), 9f, Style.ivory(.5f), .3f, Gravity.END).also { L.addLR(it, 32f, 32f, 400f) }
        val l1 = L.t("NOW, ON THIS PHONE —", Style.jost(context, 400), 10f, Style.ivory(.85f), .3f).also { L.add(it, 32f, 424f) }
        this.l1 = l1
        title = L.t("", Style.jost(context, 300), 30f, Style.IVORY).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }.also { L.addLR(it, 32f, 32f, 446f) }
        sub = L.t("", Style.kr(context, 300), 13f, Style.ivory(.75f)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }.also { L.addLR(it, 32f, 32f, 490f) }
        val pv = LineProg(context, Style.ivory(.2f), Style.IVORY, L.zf(1f), L.zf(2f)).also { L.addLR(it, 32f, 32f, 513f, 4f) }
        progView = pv; prog = { pv.p = it; credit.text = "PHOTOGRAPHIE — " + meta(MediaMetadata.METADATA_KEY_ALBUM).ifEmpty { appName() }.uppercase() }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun tb(s: String, go: () -> Unit) = L.t(s, Style.kr(context, 300), 15f, Style.ivory(.75f), .2f, Gravity.CENTER).also { row.addView(tap(it, go), LinearLayout.LayoutParams(0, -1, 1f)) }
        tb("이전") { doPrev() }
        playText = tb("멈춤") { doPlay() }.apply { setTextColor(Style.IVORY); background = UnderLine(Style.IVORY, L.zf(1f), L.zf(14f)) }
        tb("다음") { doNext() }
        L.addLR(row, 32f, 32f, 544f, 56f)
        volPrefix = "VOLUME · "
        volRow(L, 32f, 380f, 630f, VolBar.DOTS, Style.IVORY, Style.ivory(.25f), Style.ivory(.75f), Style.ivory(.75f))
        volLbl!!.letterSpacing = .15f
        val sr = soundLine(L, 32f, 380f, 670f, 14f, Style.ivory(.8f), Style.IVORY, "바꾸기 →", Style.ivory(.25f))
        nowViews += listOf(sr, art!!, credit, l1, title!!, sub!!, pv, row)
        val hs = FrameLayout(context); L.addLR(hs, 32f, 32f, 718f, 124f)
        val H = Lay(context, hs, L.w - L.x(64f), L.z(124f), 348f, 124f)
        H.add(H.t("ELSEWHERE IN THE HOUSE —", Style.jost(context, 400), 10f, Style.ivory(.85f), .3f), 0f, 0f)
        fun crow(top: Float, label: String, act: String, go: () -> Unit): Pair<TextView, TextView> {
            val r = FrameLayout(context); H.addLR(r, 0f, 0f, top, 48f)
            r.addView(View(context).apply { setBackgroundColor(Style.ivory(.15f)) }, LayoutParams(-1, 1))
            val t = H.t(label, Style.kr(context, 300), 14f, Style.ivory(.9f)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            r.addView(t, LayoutParams(H.z(260f), -2, Gravity.CENTER_VERTICAL))
            val b = H.t(act, Style.kr(context, 400), 12f, Style.IVORY, 0f, Gravity.CENTER).apply { background = UnderLine(Style.ivory(.6f), H.zf(1f), H.zf(8f)) }
            r.addView(tap(b, go), LayoutParams(H.z(64f), H.z(44f), Gravity.END or Gravity.CENTER_VERTICAL))
            houseViews += r; return t to b
        }
        crow(22f, "Apple TV", "재생") { doAtv() }.let { atvName = it.first; atvBtn = it.second }
        crow(70f, "Marantz", "켜기 →") { doMz() }.let { mzName = it.first; mzBtn = it.second }
        val q = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        quietTitle = H.t("", Style.kr(context, 400), 15f, Style.ivory(.9f), 0f, Gravity.CENTER); q.addView(quietTitle!!, LinearLayout.LayoutParams(-1, -2))
        val qb = hRow(); quietButtons(qb) { pad(H.t(it, Style.kr(context, 400), 12.5f, Style.IVORY), 4f).apply { background = UnderLine(Style.ivory(.6f), H.zf(1f), H.zf(8f)) } }
        q.addView(qb, LinearLayout.LayoutParams(-2, H.z(44f)).apply { topMargin = H.z(10f) })
        H.addLR(q, 0f, 0f, 26f); quietBox = q
        val e = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        e.addView(L.t("NOW, ON THIS PHONE —", Style.jost(context, 400), 10f, Style.ivory(.85f), .3f))
        e.addView(L.t("Silence.", Style.jost(context, 300), 30f, Style.IVORY), LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(10f) })
        e.addView(L.t("음악을 틀면 이 사진이 앨범이 됩니다.", Style.kr(context, 300), 13f, Style.ivory(.75f)).also { emptyMsg = it; emptyMsgOk = "음악을 틀면 이 사진이 앨범이 됩니다." }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        val eb = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        emptyButtons(eb) { pad(L.t(it, Style.kr(context, 400), 12.5f, Style.IVORY), 2f).apply { background = UnderLine(Style.ivory(.6f), L.zf(1f), L.zf(8f)) } }
        e.addView(eb, LinearLayout.LayoutParams(-2, L.z(44f)).apply { topMargin = L.z(14f) })
        L.addLR(e, 32f, 32f, 430f); emptyBox = e
    }
}

// ───────── 진행 표시들 ─────────
/** 가로 선 진행 (선 + 채움 + 손잡이 또는 책갈피 그림) */
class LineProg(ctx: Context, private val track: Int, private val fill: Int, private val trackPx: Float, private val fillPx: Float,
               private val knobR: Float = 0f, private val knobRing: Int = 0,
               private val marker: Bitmap? = null, private val markerW: Float = 0f, private val markerH: Float = 0f) : View(ctx) {
    var p = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    /** 재생 중이 아님(마지막 곡): 바탕선만 */
    var idle = false
        set(v) { if (field != v) { field = v; invalidate() } }
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(c: Canvas) {
        val pad = maxOf(knobR, markerW / 2)
        val y = if (marker != null) markerH * .15f + 2f else height / 2f
        val x0 = pad; val x1 = width - pad; val x = x0 + (x1 - x0) * p
        pt.color = track; c.drawRect(x0, y - trackPx / 2, x1, y + trackPx / 2, pt)
        if (idle) return
        pt.color = fill; c.drawRect(x0, y - fillPx / 2, x, y + fillPx / 2, pt)
        if (knobR > 0f) { pt.color = knobRing; c.drawCircle(x, y, knobR + knobR * .36f, pt); pt.color = fill; c.drawCircle(x, y, knobR, pt) }
        if (marker != null) c.drawBitmap(marker, null, RectF(x - markerW / 2, y - markerH * .15f, x + markerW / 2, y + markerH * .85f), pt)
    }
}

/** HOUR: 240° 레트로그레이드 진행 호 (8시 → 12시 → 4시) */
class ArcProg(ctx: Context, private val rPx: Float, private val wPx: Float, private val textSp: Float) : View(ctx) {
    var p = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var t1 = ""
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val o = RectF(cx - rPx, cy - rPx, cx + rPx, cy + rPx)
        // 안드로이드 각도: 0° = 3시, 시계방향. 8시 = 150°, 240° 동안
        pt.strokeWidth = wPx; pt.color = tint(.25f, 0xFFC9A86A.toInt()); c.drawArc(o, 150f, 240f, false, pt)
        pt.color = GOLD_LT; c.drawArc(o, 150f, 240f * p, false, pt)
        val a = Math.toRadians((150.0 + 240.0 * p))
        val mx = cx + rPx * cos(a).toFloat(); val my = cy + rPx * sin(a).toFloat()
        pt.style = Paint.Style.FILL; pt.color = GOLD_LT; c.drawCircle(mx, my, wPx * 1.7f, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = wPx * .66f; pt.color = Style.INK; c.drawCircle(mx, my, wPx * 1.7f, pt); pt.strokeWidth = wPx
        tp.textSize = textSp * resources.displayMetrics.scaledDensity; tp.color = tint(.9f, GOLD_LT)
        tp.typeface = Style.jost(context, 400)
        val a0 = Math.toRadians(150.0); val a1 = Math.toRadians(30.0)
        c.drawText("0:00", cx + rPx * cos(a0).toFloat() - wPx * 2, cy + rPx * sin(a0).toFloat() + tp.textSize * 1.6f, tp)
        c.drawText(t1, cx + rPx * cos(a1).toFloat() + wPx * 2, cy + rPx * sin(a1).toFloat() + tp.textSize * 1.6f, tp)
    }
}

/** TRAIN: 노선도 진행 (역 두 곳 + 금 점) */
class RouteProg(ctx: Context, private val linePx: Float, private val dotR: Float) : View(ctx) {
    var p = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val y = height / 2f; val x0 = dotR; val x1 = width - dotR; val x = x0 + (x1 - x0) * p
        pt.style = Paint.Style.FILL
        pt.color = Style.ivory(.25f); c.drawRect(x0, y - linePx / 2, x1, y + linePx / 2, pt)
        pt.color = GOLD_LT; c.drawRect(x0, y - linePx / 2, x, y + linePx / 2, pt)
        listOf(.3f, .7f).forEach { f ->
            val sx = x0 + (x1 - x0) * f
            pt.style = Paint.Style.FILL; pt.color = NAVY; c.drawCircle(sx, y, dotR * .66f, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = linePx * .5f; pt.color = Style.ivory(.5f); c.drawCircle(sx, y, dotR * .66f, pt)
        }
        pt.style = Paint.Style.FILL; pt.color = tint(.25f, GOLD_LT); c.drawCircle(x, y, dotR * 1.5f, pt)
        pt.color = GOLD_LT; c.drawCircle(x, y, dotR, pt)
    }
}

/** 안쪽 선 테두리 */
class InsetBox(private val color: Int, private val wPx: Float, private val insetPx: Float) : android.graphics.drawable.Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    override fun draw(c: Canvas) {
        p.color = color; p.strokeWidth = wPx
        val i = insetPx + wPx / 2
        c.drawRect(bounds.left + i, bounds.top + i, bounds.right - i, bounds.bottom - i, p)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** 글자 아래 밑줄 (inset = 좌우 안쪽 여백) */
class UnderLine(private val color: Int, private val wPx: Float, private val insetPx: Float) : android.graphics.drawable.Drawable() {
    private val p = Paint()
    override fun draw(c: Canvas) {
        p.color = color
        c.drawRect(bounds.left + insetPx, bounds.bottom - wPx - bounds.height() * .12f, bounds.right - insetPx, bounds.bottom - bounds.height() * .12f, p)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** 점선 이음줄 */
class DotLeader(ctx: Context, private val color: Int, private val r: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        p.color = color; var x = r; val y = height / 2f
        while (x < width) { c.drawCircle(x, y, r * .5f, p); x += r * 3 }
    }
}

/** LIBRARY: 접힌 종이 귀퉁이 */
/** 글꼴 바꾸기 + 크기 배수 (LIBRARY 'Volume' 이탤릭) */
class CustomFont(private val tf: android.graphics.Typeface, private val scale: Float) : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(p: android.text.TextPaint) { p.typeface = tf; p.textSize *= scale; p.letterSpacing = 0f }
    override fun updateMeasureState(p: android.text.TextPaint) { p.typeface = tf; p.textSize *= scale; p.letterSpacing = 0f }
}

class EarView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = 0x1F000000; val sh = android.graphics.Path().apply { moveTo(w, 0f); lineTo(0f, h); lineTo(w, h); close() }
        c.save(); c.translate(-3f, -3f); c.drawPath(sh, p); c.restore()
        p.color = 0xFFE3DCCF.toInt(); c.drawPath(sh, p)
    }
}
