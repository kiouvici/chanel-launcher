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
    fun sendSheet(sources: List<String>, current: String, onPick: (String) -> Unit)
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
    private var lightBg = false

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
        atvName = null; atvSub = null; atvBtn = null; atvArt = null; mzState = null; mzBtn = null
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
        } else {
            try { msm.removeOnActiveSessionsChangedListener(sessL) } catch (_: Exception) {}
            removeCallbacks(tick); removeCallbacks(haLoop); stopAnims()
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
            updateProgress()
            if (visibleNow) postDelayed(this, 500)
        }
    }
    private var haTicks = 0
    private val haLoop = Runnable { pollHa() }
    private fun pollHa() {
        removeCallbacks(haLoop)
        Ha.states(look.haToken, listOf(Ha.ATV, Ha.MARANTZ, Ha.SPOTIFY)) { m ->
            home = m; haOk = m != null; bindHome()
            if (visibleNow) postDelayed(haLoop, 5000)
        }
        haTicks++
    }

    private fun stopAnims() {
        spinAnim?.cancel(); spinAnim = null
        flowAnim?.cancel(); flowAnim = null
        removeCallbacks(flapLoop)
    }

    // ───────── 값 채우기 ─────────
    private fun meta(k: String) = ctl?.metadata?.getString(k)?.takeIf { it.isNotBlank() } ?: ""
    private val hasNow get() = ctl != null && meta(MediaMetadata.METADATA_KEY_TITLE).isNotEmpty()
    private val playing get() = ctl?.playbackState?.state == PlaybackState.STATE_PLAYING
    private fun appName(): String {
        val pkg = ctl?.packageName ?: return ""
        return try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
    }
    private fun artBmp(): Bitmap? = ctl?.metadata?.let {
        it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
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
        if (!now) { stopAnims(); art?.bmp = null; if (theme == Look.TRAIN) art?.placeholder = 1; return }
        art?.placeholder = 0
        val t = meta(MediaMetadata.METADATA_KEY_TITLE)
        val ar = meta(MediaMetadata.METADATA_KEY_ARTIST).ifEmpty { meta(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) }
        val al = meta(MediaMetadata.METADATA_KEY_ALBUM)
        val app = appName()
        art?.bmp = artBmp()
        val outN = outputName()
        when (theme) {
            Look.LIBRARY -> {
                sentence?.text = "You are listening to “$t,” by ${ar.ifEmpty { "someone" }}."
                title?.text = if (al.isNotEmpty()) "Plate · $al" else "Plate · $app"
                val cal = java.util.Calendar.getInstance()
                val day = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                whenLine?.text = "%s, %d:%02d — on this phone".format(day, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                out?.text = android.text.SpannableStringBuilder("Listening on $outN — ").append("바꾸기",
                    android.text.style.UnderlineSpan(), 0)
            }
            Look.TRAIN -> {
                setFlapText(t.uppercase())
                sub?.text = listOf(ar, al).filter { it.isNotEmpty() }.joinToString(" · ")
                out?.text = "VOITURE · $outN ›"
            }
            Look.CAPTION -> {
                title?.text = t
                out?.text = "출력 · $outN →"
            }
            Look.HOUR -> {
                title?.text = t
                sub?.text = listOf(ar, al).filter { it.isNotEmpty() }.joinToString(" · ")
                src?.text = "${app.uppercase()} · CE TÉLÉPHONE"
                out?.text = "OUT · " + outN.uppercase().take(10) + " ›"
            }
            else -> {
                title?.text = t
                sub?.text = listOf(ar, al).filter { it.isNotEmpty() }.joinToString(" · ")
                src?.text = "${app.uppercase()} · 이 폰"
                out?.text = "출력 · $outN ›"
            }
        }
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
        if (theme == Look.TRAIN && visibleNow) { removeCallbacks(flapLoop); postDelayed(flapLoop, 3000) }
    }

    private fun updateProgress() {
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
            Look.TRAIN -> { t0?.text = "DÉPART ${fmtTime(pos)}"; t1?.text = "ARRIVÉE ${fmtTime(dur)}" }
            Look.CAPTION -> {
                val ar = meta(MediaMetadata.METADATA_KEY_ARTIST); val al = meta(MediaMetadata.METADATA_KEY_ALBUM)
                sub?.text = (listOf(ar, al).filter { it.isNotEmpty() } + "${fmtTime(pos)} / ${fmtTime(dur)}").joinToString(" · ")
            }
            Look.HOUR -> { t0?.text = "0:00"; t1?.text = fmtTime(dur) }
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

    /** 집 기기 줄 채우기 · 모두 꺼졌으면 '집은 조용합니다' */
    private fun bindHome() {
        if (theme < 0) return
        val m = home
        val atv = m?.get(Ha.ATV); val mz = m?.get(Ha.MARANTZ)
        val noToken = look.haToken.isBlank()
        val quiet = noToken || !haOk || (!Ha.on(atv) && !Ha.on(mz))
        houseViews.forEach { it.visibility = if (quiet) View.INVISIBLE else View.VISIBLE }
        quietBox?.visibility = if (quiet) View.VISIBLE else View.GONE
        val qt = when {
            noToken -> "HA에 연결하면 집 기기가 여기 놓입니다"
            !haOk -> "HA에 연결되지 않았습니다"
            else -> when (theme) { Look.LIBRARY -> "The house is quiet."; Look.TRAIN -> "PAS DE CORRESPONDANCE"; else -> "집은 조용합니다" }
        }
        quietTitle?.let { q ->
            q.text = qt
            // 한글 문장은 한글 글꼴로 (영문 세리프·고정폭에 한글이 섞이면 어색함)
            if (qt.any { it.code > 0x3000 }) { q.typeface = Style.kr(context, 400); q.letterSpacing = 0f; q.textSize = 14f }
            else if (theme == Look.LIBRARY) { q.typeface = Fonts.serif(context); q.textSize = 22f }
            else if (theme == Look.TRAIN) { q.typeface = Fonts.mono(context); q.letterSpacing = .1f; q.textSize = 12f }
        }
        quietBtns?.let { b ->
            (b.getChildAt(0) as? TextView)?.text = if (noToken || !haOk) "HA 연결" else "거실에서 듣기"
            b.getChildAt(1)?.visibility = if (noToken || !haOk) View.GONE else View.VISIBLE
        }
        if (quiet) return
        val atvOn = Ha.on(atv)
        val app = Ha.attr(atv, "app_name").ifEmpty { Ha.attr(atv, "media_title") }
        val st = atv?.optString("state") ?: ""
        val stK = when (st) { "playing" -> "재생"; "paused" -> "멈춤"; "idle" -> "대기"; "off", "standby" -> "꺼짐"; else -> st }
        val stF = when (st) { "playing" -> "EN ROUTE"; "paused" -> "EN PAUSE"; "off", "standby" -> "AU DÉPÔT"; else -> "À QUAI" }
        when (theme) {
            Look.TRAIN -> { atvName?.text = "APPLE TV" + (if (app.isNotEmpty()) " · ${app.uppercase()}" else ""); atvSub?.text = stF }
            Look.HOUR -> atvSub?.text = if (atvOn) "$stK · ${app.ifEmpty { "Apple TV" }}" else "꺼짐 · 켜기"
            Look.LIBRARY -> atvName?.text = "거실 · Apple TV" + (if (app.isNotEmpty()) " — $app" else "")
            Look.CAPTION -> atvName?.text = "거실 · Apple TV" + (if (app.isNotEmpty()) " — $app" else "")
            else -> { atvName?.text = "거실 · Apple TV"; atvSub?.text = listOf(app, stK).filter { it.isNotEmpty() }.joinToString(" · ") }
        }
        val atvPlaying = st == "playing"
        (atvBtn as? Glyph)?.set(if (!atvOn) 4 else if (atvPlaying) 2 else 1)
        (atvBtn as? TextView)?.text = if (!atvOn) (if (theme == Look.TRAIN) "⏻" else "켜기") else if (atvPlaying) (if (theme == Look.TRAIN) "❙❙" else "멈춤") else (if (theme == Look.TRAIN) "▶" else "재생")
        val mzOn = Ha.on(mz)
        mzState?.text = when (theme) {
            Look.MONSIEUR -> "MARANTZ ${if (mzOn) "● 켜짐" else "○ 꺼짐"}"
            Look.HOUR -> if (mzOn) "켜짐 · 끄기" else "꺼짐 · 켜기"
            Look.TRAIN -> if (mzOn) "EN SERVICE" else "À QUAI"
            else -> mzState?.text
        }
        mzBtn?.text = if (theme == Look.TRAIN) "⏻" else if (mzOn) "끄기" else "켜기"
        if (theme == Look.HOUR) (mzBtn as? TextView)?.text = if (mzOn) "ON" else "OFF"
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
    private fun doPrev() { ctl?.transportControls?.skipToPrevious() }
    private fun doNext() { ctl?.transportControls?.skipToNext() }
    private fun doPlay() { val c = ctl?.transportControls ?: return; if (playing) c.pause() else c.play() }
    private fun doOutput() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34 && android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher()) return
        } catch (_: Exception) {}
        try {
            context.startActivity(android.content.Intent("android.settings.panel.action.MEDIA_OUTPUT")
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctl?.packageName ?: context.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
    private fun doSend() {
        if (look.haToken.isBlank()) { host.askToken(); return }
        val sp = home?.get(Ha.SPOTIFY)
        val list = Ha.sources(sp)
        host.sendSheet(list, Ha.attr(sp, "source")) { s ->
            if (s == "__living") Ha.call(look.haToken, "script", "turn_on", JSONObject().put("entity_id", Ha.LIVING)) { pollLater() }
            else Ha.call(look.haToken, "media_player", "select_source", JSONObject().put("entity_id", Ha.SPOTIFY).put("source", s)) { pollLater() }
        }
    }
    private fun doLiving() {
        if (look.haToken.isBlank() || !haOk) { host.askToken(); return }
        Ha.call(look.haToken, "script", "turn_on", JSONObject().put("entity_id", Ha.LIVING)) { pollLater() }
    }
    private fun doAtv() {
        val on = Ha.on(home?.get(Ha.ATV))
        Ha.call(look.haToken, "media_player", if (on) "media_play_pause" else "turn_on", JSONObject().put("entity_id", Ha.ATV)) { pollLater() }
    }
    private fun doMz() {
        val on = Ha.on(home?.get(Ha.MARANTZ))
        Ha.call(look.haToken, "media_player", if (on) "turn_off" else "turn_on", JSONObject().put("entity_id", Ha.MARANTZ)) { pollLater() }
    }
    private fun doMzOn() = Ha.call(look.haToken, "media_player", "turn_on", JSONObject().put("entity_id", Ha.MARANTZ)) { pollLater() }
    private fun pollLater() { removeCallbacks(haLoop); postDelayed(haLoop, 900) }

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
            val b = make(if (theme == Look.HOUR || theme == Look.TRAIN) "ALLOW · 알림 접근" else if (theme == Look.CAPTION) "알림 접근 허용 →" else "알림 접근 허용")
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
        val a = make("거실에서 듣기"); tap(a) { if (look.haToken.isBlank() || !haOk) host.askToken() else doLiving() }
        val b = make("마란츠 켜기"); tap(b) { doMzOn() }
        row.addView(a, LinearLayout.LayoutParams(-2, -1))
        row.addView(b, LinearLayout.LayoutParams(-2, -1).apply { leftMargin = context.dp(10f) })
        quietBtns = row
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
            background = android.graphics.drawable.LayerDrawable(arrayOf(Style.tile(context, "th/tissue.webp", 360f), android.graphics.drawable.ColorDrawable(tint(.55f, 0xFFF3EEE4.toInt()))))
            elevation = L.zf(10f)
        }
        L.addLR(tissue, 16f, 16f, 76f, 452f)
        // 레코드 + 슬리브
        val vinyl = View(context).apply { background = Pic(Style.img(context, "th/vinyl.webp"), Pic.CONTAIN); elevation = L.zf(11f) }
        L.add(vinyl, 150f, 104f, 236f, 236f); spin = vinyl
        val sleeve = FrameLayout(context).apply { setBackgroundColor(0xFF0D0D0D.toInt()); elevation = L.zf(13f) }
        L.add(sleeve, 44f, 96f, 252f, 252f)
        art = ArtView(context).also { sleeve.addView(it, LayoutParams(-1, -1).apply { val m = L.z(14f); setMargins(m, m, m, m) }) }
        // 재생 없음: 빈 종이 슬리브에 N°31 눌러 찍기
        val es = FrameLayout(context).apply { background = Style.tile(context, Style.PAPER, 256f) }
        es.addView(L.t("N°31", Style.jost(context, 300), 28f, 0xFFE3DCCF.toInt(), .2f, Gravity.CENTER).apply {
            setShadowLayer(L.zf(1f), 0f, L.zf(1f), Color.argb(150, 255, 255, 255)) }, LayoutParams(-1, -1))
        sleeve.addView(es, LayoutParams(-1, -1)); emptyViews += es
        val ink = Style.INK
        title = L.t("", Style.jost(context, 400), 24f, ink, .02f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; elevation = L.zf(12f) }
        L.addLR(title!!, 36f, 36f, 372f)
        sub = L.t("", Style.kr(context, 300), 13f, Style.ink(.72f), 0f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; elevation = L.zf(12f) }
        L.addLR(sub!!, 36f, 36f, 406f)
        src = L.t("", Style.jost(context, 400), 9f, Style.GILT, .3f, Gravity.CENTER).apply { elevation = L.zf(12f) }
        L.addLR(src!!, 36f, 36f, 432f)
        // 진행: 검정 선 + 둥근 손잡이
        val pv = LineProg(context, Style.ink(.22f), Style.INK, L.zf(1f), L.zf(2f), knobR = L.zf(5.5f), knobRing = 0xFFF3EEE4.toInt()).apply { elevation = L.zf(12f) }
        L.addLR(pv, 44f, 44f, 462f, 14f); prog = { pv.p = it }
        t0 = L.t("", Style.jost(context, 400), 10f, Style.ink(.65f), .1f).apply { elevation = L.zf(12f) }
        L.add(t0!!, 44f, 484f)
        t1 = L.t("", Style.jost(context, 400), 10f, Style.ink(.65f), .1f, Gravity.END).apply { elevation = L.zf(12f) }
        L.addLR(t1!!, 44f, 44f, 484f)
        // 버튼: 금 테두리 원 · 리본 단추 · 금 테두리 원
        val prev = FrameLayout(context).apply { background = shape(stroke = tint(.65f, GOLD_LT), strokePx = L.z(1f), oval = true); addView(Glyph(context, 0, GOLD_LT, 1.6f), LayoutParams(-1, -1).apply { val m = L.z(16f); setMargins(m, m, m, m) }) }
        val pl = FrameLayout(context).apply { background = Pic(Style.img(context, "th/btn_ribbon.webp"), Pic.CONTAIN); elevation = L.z(4f).toFloat() }
        play = Glyph(context, 2, Style.INK, 2.4f).also { pl.addView(it, LayoutParams(-1, -1).apply { val m = L.z(31f); setMargins(m, m, m, m) }) }
        val next = FrameLayout(context).apply { background = shape(stroke = tint(.65f, GOLD_LT), strokePx = L.z(1f), oval = true); addView(Glyph(context, 3, GOLD_LT, 1.6f), LayoutParams(-1, -1).apply { val m = L.z(16f); setMargins(m, m, m, m) }) }
        L.add(tap(prev) { doPrev() }, 70f, 552f, 52f, 52f); L.add(tap(pl) { doPlay() }, 164f, 536f, 84f, 84f); L.add(tap(next) { doNext() }, 290f, 552f, 52f, 52f)
        out = L.t("", Style.kr(context, 300), 12.5f, Style.ivory(.8f), 0f, Gravity.CENTER)
        L.addLR(tap(out!!) { doOutput() }, 16f, 16f, 626f, 40f)
        addView(View(context).apply { setBackgroundColor(Style.ivory(.12f)) }, LayoutParams(-1, 1).apply { leftMargin = L.x(16f); rightMargin = L.x(16f); topMargin = L.y(624f) })
        nowViews += listOf(vinyl, title!!, sub!!, src!!, pv, t0!!, t1!!, prev, pl, next, out!!)
        // 집 카드 (종이 + 안쪽 선)
        val card = FrameLayout(context).apply {
            background = Style.tile(context, Style.PAPER, 256f); foreground = InsetBox(Style.ink(.45f), L.zf(.75f), L.zf(4f)); elevation = L.zf(8f)
        }
        L.addLR(card, 16f, 16f, 676f, 172f)
        val CL = Lay(context, card, L.w - L.x(32f), L.z(172f), 380f, 172f)
        CL.add(CL.t("À LA MAISON", Style.jost(context, 400), 10f, Style.GILT, .3f), 16f, 16f)
        mzState = CL.t("MARANTZ ○ 꺼짐", Style.jost(context, 400), 11f, Style.ink(.7f), .15f, Gravity.CENTER)
        CL.add(tap(mzState!!) { doMz() }, 272f, 4f, 100f, 40f)
        // Apple TV 줄
        val r1 = FrameLayout(context)
        CL.addLR(r1, 16f, 16f, 44f, 62f)
        val R1 = Lay(context, r1, CL.w - CL.x(32f), CL.z(62f), 348f, 62f)
        r1.addView(View(context).apply { setBackgroundColor(Style.ink(.12f)) }, LayoutParams(-1, 1))
        atvArt = ArtView(context).also { R1.add(it, 0f, 9f, 44f, 44f) }
        atvName = R1.t("거실 · Apple TV", Style.kr(context, 400), 13.5f, Style.INK).also { R1.add(it, 56f, 14f) }
        atvSub = R1.t("", Style.kr(context, 300), 11.5f, Style.ink(.65f)).also { R1.add(it, 56f, 35f) }
        val ab = FrameLayout(context).apply { background = shape(Style.INK, oval = true) }
        atvBtn = Glyph(context, 1, Style.IVORY, 2f, fill = true).also { ab.addView(it, LayoutParams(-1, -1).apply { val m = R1.z(13f); setMargins(m, m, m, m) }) }
        R1.add(tap(ab) { doAtv() }, 304f, 9f, 44f, 44f)
        // 스포티파이 줄: 거실에서 · 보내기
        val r2 = FrameLayout(context)
        CL.addLR(r2, 16f, 16f, 106f, 62f)
        val R2 = Lay(context, r2, CL.w - CL.x(32f), CL.z(62f), 348f, 62f)
        r2.addView(View(context).apply { setBackgroundColor(Style.ink(.12f)) }, LayoutParams(-1, 1))
        R2.add(ArtView(context).apply { placeholder = 1 }, 0f, 9f, 44f, 44f)
        R2.add(R2.t("스포티파이", Style.kr(context, 400), 13.5f, Style.INK), 56f, 14f)
        R2.add(R2.t("이 폰에서 재생", Style.kr(context, 300), 11.5f, Style.ink(.65f)), 56f, 35f)
        val g1 = pad(R2.t("거실에서", Style.kr(context, 400), 11.5f, Style.INK), 0f).apply { background = shape(stroke = Style.ink(.7f), strokePx = R2.z(1f), radius = R2.zf(22f)) }
        val g2 = pad(R2.t("보내기 ›", Style.kr(context, 400), 11.5f, Style.INK), 0f).apply { background = shape(stroke = Style.ink(.7f), strokePx = R2.z(1f), radius = R2.zf(22f)) }
        R2.add(tap(g1) { doLiving() }, 172f, 9f, 84f, 44f); R2.add(tap(g2) { doSend() }, 264f, 9f, 84f, 44f)
        houseViews += listOf(r1, r2, mzState!!)
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
        L.add(L.t("NOW PLAYING", Style.jost(context, 400), 10f, Style.ink(.55f), .35f), 36f, 70f)
        whenLine = L.t("", Fonts.serifI(context), 17f, Style.ink(.7f)).also { L.add(it, 36f, 92f) }
        // 사진 모서리로 붙인 앨범아트
        val plate = FrameLayout(context).apply { setBackgroundColor(Color.WHITE); elevation = L.zf(3f); clipChildren = false }
        L.add(plate, 86f, 134f, 240f, 240f)
        art = ArtView(context).also { plate.addView(it, LayoutParams(-1, -1).apply { val m = L.z(10f); setMargins(m, m, m, m) }) }
        val corner = Style.img(context, "th/photo_corner.webp")
        listOf(Triple(-1f, -1f, -45f), Triple(1f, -1f, 45f), Triple(-1f, 1f, -135f), Triple(1f, 1f, 135f)).forEach { (sx, sy, rot) ->
            val c = View(context).apply { background = Pic(corner, Pic.CONTAIN); rotation = rot; elevation = L.zf(4f) }
            val cx = if (sx < 0) 86f - 9f else 86f + 240f - 44f + 9f
            val cy = if (sy < 0) 134f - 1f else 134f + 240f - 28f + 1f
            L.add(c, cx, cy, 44f, 28f); nowViews += c
        }
        title = L.t("", Fonts.serifI(context), 14f, Style.ink(.65f), 0f, Gravity.CENTER).also { L.addLR(it, 36f, 36f, 386f) }
        sentence = L.t("", Fonts.serif(context), 25f, Style.INK, 0f, Gravity.CENTER).apply { setLineSpacing(L.zf(4f), 1f); maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END }
        L.addLR(sentence!!, 36f, 36f, 414f)
        val pv = LineProg(context, Style.ink(.3f), Style.INK, L.zf(1f), L.zf(2f), marker = Style.img(context, "th/bookmark.webp"), markerW = L.zf(14f), markerH = L.zf(34f))
        L.addLR(pv, 48f, 48f, 490f, 38f); prog = { pv.p = it }
        t1 = L.t("", Style.jost(context, 400), 10f, Style.ink(.65f), .15f, Gravity.END).also { L.addLR(it, 48f, 48f, 506f) }
        val deb = Style.img(context, "th/deboss.webp")
        fun db(kind: Int, size: Float, icon: Float) = FrameLayout(context).apply {
            background = Pic(deb, Pic.CONTAIN)
            val g = Glyph(context, kind, Style.INK, if (size > 70f) 2.2f else 1.6f); addView(g, LayoutParams(-1, -1).apply { val m = L.z((size - icon) / 2); setMargins(m, m, m, m) })
            if (kind == 2) play = g
        }
        val prev = db(0, 60f, 18f); val pl = db(2, 84f, 22f); val next = db(3, 60f, 18f)
        L.add(tap(prev) { doPrev() }, 74f, 552f, 60f, 60f); L.add(tap(pl) { doPlay() }, 164f, 540f, 84f, 84f); L.add(tap(next) { doNext() }, 278f, 552f, 60f, 60f)
        out = L.t("", Fonts.serif(context), 16f, Style.ink(.75f), 0f, Gravity.CENTER).also { L.addLR(tap(it) { doOutput() }, 36f, 36f, 632f, 32f) }
        nowViews += listOf(plate, title!!, sentence!!, pv, t1!!, prev, pl, next, out!!, whenLine!!)
        // 집: 점선 줄
        val hs = FrameLayout(context); L.addLR(hs, 36f, 36f, 672f, 200f)
        val H = Lay(context, hs, L.w - L.x(72f), L.z(200f), 340f, 200f)
        H.add(H.t("IN THE HOUSE", Style.jost(context, 400), 10f, Style.ink(.55f), .3f), 0f, 0f)
        fun lrow(top: Float, label: String, btn: String, go: () -> Unit): Pair<TextView, TextView> {
            val r = FrameLayout(context); H.addLR(r, 0f, 0f, top, 50f)
            r.addView(View(context).apply { setBackgroundColor(Style.ink(.15f)) }, LayoutParams(-1, 1))
            val t = H.t(label, Style.kr(context, 400), 13.5f, Style.INK).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            r.addView(t, LayoutParams(H.z(220f), -2, Gravity.CENTER_VERTICAL))
            r.addView(DotLeader(context, Style.ink(.35f), H.zf(1f)), LayoutParams(-1, H.z(4f), Gravity.CENTER_VERTICAL).apply { leftMargin = H.z(226f); rightMargin = H.z(72f) })
            val b = pad(H.t(btn, Style.kr(context, 400), 12f, Style.INK), 10f).apply { background = shape(stroke = Style.ink(.55f), strokePx = H.z(1f)) }
            r.addView(tap(b, go), LayoutParams(H.z(64f), H.z(44f), Gravity.END or Gravity.CENTER_VERTICAL))
            houseViews += r; return t to b
        }
        lrow(18f, "거실 · Apple TV", "재생") { doAtv() }.let { atvName = it.first; atvBtn = it.second }
        lrow(68f, "스포티파이 — 이 폰", "보내기 ›") { doSend() }
        lrow(118f, "거실 · 마란츠 리시버", "켜기") { doMz() }.let { mzState = null; mzBtn = it.second }
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

    // ═════════ ③ THE HOUR — CHRONOGRAPHE ═════════
    private fun buildHour(L: Lay) {
        lightBg = false
        background = android.graphics.drawable.GradientDrawable().apply {
            gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT; colors = intArrayOf(0xFF1A1A1A.toInt(), 0xFF0B0B0B.toInt())
            gradientRadius = L.w * .9f; setGradientCenter(.5f, .3f)
        }
        val dial = FrameLayout(context)
        L.add(dial, 36f, 60f, 340f, 340f)
        dial.addView(View(context).apply { background = Pic(Style.img(context, "th/subdial.webp"), Pic.CONTAIN) },
            LayoutParams(L.z(282f), L.z(282f), Gravity.CENTER))
        val av = ArtView(context, circle = true, ring = tint(.7f, 0xFFC9A86A.toInt()), ringPx = L.zf(1.5f)); art = av
        dial.addView(av, LayoutParams(L.z(180f), L.z(180f), Gravity.CENTER))
        val arc = ArcProg(context, L.zf(150f), L.zf(3f), L.sp(10f)).also { dial.addView(it, LayoutParams(-1, -1)) }
        prog = { arc.p = it; arc.t1 = t1?.text?.toString() ?: "" }
        t1 = TextView(context)   // 끝 시각 글자 (호 끝에 그림)
        title = L.t("", Style.jost(context, 400), 24f, Style.IVORY, .02f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        L.addLR(title!!, 32f, 32f, 418f)
        sub = L.t("", Style.kr(context, 300), 13f, Style.ivory(.75f), 0f, Gravity.CENTER).also { L.addLR(it, 32f, 32f, 452f) }
        src = L.t("", Style.jost(context, 400), 9f, GOLD_LT, .3f, Gravity.CENTER).also { L.addLR(it, 32f, 32f, 478f) }
        val pusher = Style.img(context, "th/pusher.webp")
        fun push(kind: Int) = FrameLayout(context).apply { background = Pic(pusher, Pic.FIT); addView(Glyph(context, kind, GOLD_LT, 1.8f), LayoutParams(L.z(16f), L.z(16f), Gravity.CENTER)) }
        val prev = push(0); val next = push(3)
        val crown = FrameLayout(context).apply { background = Pic(Style.img(context, "th/crown.webp"), Pic.CONTAIN); elevation = L.zf(4f) }
        play = Glyph(context, 2, 0xFF2A1F0C.toInt(), 2.4f).also { crown.addView(it, LayoutParams(L.z(22f), L.z(22f), Gravity.CENTER)) }
        L.add(tap(prev) { doPrev() }, 46f, 553f, 96f, 44f); L.add(tap(crown) { doPlay() }, 166f, 535f, 80f, 80f); L.add(tap(next) { doNext() }, 270f, 553f, 96f, 44f)
        out = L.t("", Style.jost(context, 400), 11f, Style.ivory(.8f), .3f, Gravity.CENTER).apply { background = shape(stroke = tint(.5f, GOLD_LT), strokePx = L.z(1f)) }
        L.add(tap(out!!) { doOutput() }, 74f, 626f, 150f, 44f)
        val send = L.t("보내기 ›", Style.kr(context, 400), 11f, Style.ivory(.8f), .1f, Gravity.CENTER).apply { background = shape(stroke = tint(.5f, GOLD_LT), strokePx = L.z(1f)) }
        L.add(tap(send) { doSend() }, 236f, 626f, 100f, 44f)
        nowViews += listOf(send, art!!, arc, title!!, sub!!, src!!, prev, crown, next, out!!)
        // 집: 보조 다이얼 두 개
        val sub1 = FrameLayout(context).apply { background = Pic(Style.img(context, "th/subdial.webp"), Pic.CONTAIN) }
        L.add(tap(sub1) { doAtv() }, 58f, 690f, 120f, 120f)
        atvArt = ArtView(context, circle = true).also { sub1.addView(it, LayoutParams(L.z(52f), L.z(52f), Gravity.CENTER).apply { bottomMargin = L.z(12f) }) }
        sub1.addView(L.t("APPLE TV", Style.jost(context, 400), 10f, tint(.95f, GOLD_LT), .2f, Gravity.CENTER), LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = L.z(22f) })
        atvSub = L.t("", Style.kr(context, 300), 11f, Style.ivory(.8f), 0f, Gravity.CENTER).also { L.add(it, 18f, 818f, 200f) }
        val sub2 = FrameLayout(context).apply { background = Pic(Style.img(context, "th/subdial.webp"), Pic.CONTAIN) }
        L.add(tap(sub2) { doMz() }, 234f, 690f, 120f, 120f)
        val mb = L.t("OFF", Style.jost(context, 400), 11f, Style.ivory(.8f), .15f, Gravity.CENTER).apply { background = shape(0xFF050505.toInt(), tint(.5f, 0xFFC9A86A.toInt()), L.z(1f)) }
        mzBtn = mb
        sub2.addView(mb, LayoutParams(L.z(44f), L.z(22f), Gravity.CENTER).apply { bottomMargin = L.z(12f) })
        sub2.addView(L.t("MARANTZ", Style.jost(context, 400), 10f, tint(.95f, GOLD_LT), .2f, Gravity.CENTER), LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = L.z(22f) })
        mzState = L.t("", Style.kr(context, 300), 11f, Style.ivory(.8f), 0f, Gravity.CENTER).also { L.add(it, 194f, 818f, 200f) }
        // 보조 다이얼 아래 글자 칸 가운데 맞춤
        (atvSub!!.layoutParams as LayoutParams).leftMargin = L.x(118f) - L.z(100f)
        (mzState!!.layoutParams as LayoutParams).leftMargin = L.x(294f) - L.z(100f)
        houseViews += listOf(sub1, sub2, atvSub!!, mzState!!)
        val q = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        q.addView(L.t("À LA MAISON", Style.jost(context, 400), 10f, GOLD_LT, .3f, Gravity.CENTER))
        quietTitle = L.t("", Style.kr(context, 400), 15f, Style.ivory(.9f), 0f, Gravity.CENTER); q.addView(quietTitle!!, LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        val qb = hRow(); quietButtons(qb) { pad(L.t(it, Style.kr(context, 400), 12.5f, GOLD_LT), 14f).apply { background = shape(stroke = tint(.6f, GOLD_LT), strokePx = L.z(1f)) } }
        q.addView(qb, LinearLayout.LayoutParams(-2, L.z(44f)).apply { topMargin = L.z(14f) })
        L.addLR(q, 28f, 28f, 700f); quietBox = q
        val e = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        e.addView(L.t("CHRONOGRAPHE", Style.jost(context, 400), 10f, GOLD_LT, .3f, Gravity.CENTER))
        e.addView(L.t("0:00", Style.jost(context, 200), 56f, Style.IVORY, 0f, Gravity.CENTER), LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        e.addView(L.t("재생을 시작하면 바늘이 돕니다.", Style.kr(context, 300), 13f, Style.ivory(.75f), 0f, Gravity.CENTER).also { emptyMsg = it; emptyMsgOk = "재생을 시작하면 바늘이 돕니다." }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        val eb = hRow(); emptyButtons(eb) { pad(L.t(it, Style.jost(context, 400), 12.5f, GOLD_LT, .2f), 18f).apply { background = shape(stroke = tint(.6f, GOLD_LT), strokePx = L.z(1f)) } }
        e.addView(eb, LinearLayout.LayoutParams(-2, L.z(44f)).apply { topMargin = L.z(18f) })
        L.addLR(e, 28f, 28f, 420f); emptyBox = e
    }

    // ═════════ ④ TRAIN BLEU — LA VOITURE ═════════
    private fun buildTrain(L: Lay) {
        lightBg = false
        setBackgroundColor(NAVY)
        addView(View(context).apply { background = FrameDrawable(Style.img(context, "th/gilt_frame.webp"), 60, L.z(18f)) },
            LayoutParams(-1, -1).apply { topMargin = L.y(36f); leftMargin = L.x(4f); rightMargin = L.x(4f); bottomMargin = L.y(4f) })
        // 객실 창: 창 안에서 앨범아트가 천천히 흐름
        val win = FrameLayout(context).apply { clipChildren = true }
        L.add(win, 56f, 70f, 300f, 292f)
        val inner = FrameLayout(context).apply { clipChildren = true }
        win.addView(inner, LayoutParams(-1, -1).apply { val m = L.z(30f); setMargins(m, m, m, m) })
        val av = ArtView(context); art = av
        inner.addView(av, LayoutParams(L.z(240f * 1.22f), -1)); flowArt = av
        win.addView(View(context).apply { background = Pic(Style.img(context, "th/window_frame.webp"), Pic.FIT) }, LayoutParams(-1, -1))
        // 플랩 14칸
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val tile = Style.img(context, "th/flap_tile.webp")
        flaps = (0 until 14).map {
            L.t("", Fonts.mono(context), 17f, Style.IVORY, 0f, Gravity.CENTER).apply { background = Pic(tile, Pic.FIT) }.also { tv ->
                row.addView(tv, LinearLayout.LayoutParams(L.z(22f), L.z(32f)).apply { if (it > 0) leftMargin = L.z(2f) })
            }
        }
        L.addLR(row, 0f, 0f, 384f, 32f)
        flapNote = L.t("", Fonts.mono(context), 10f, tint(.65f, GOLD_LT), .15f, Gravity.CENTER).also { L.addLR(it, 0f, 0f, 420f) }
        sub = L.t("", Style.kr(context, 300), 13f, Style.ivory(.8f), 0f, Gravity.CENTER).also { L.addLR(it, 32f, 32f, 440f) }
        t0 = L.t("DÉPART 0:00", Fonts.mono(context), 9f, tint(.9f, GOLD_LT), .1f).also { L.add(it, 32f, 474f) }
        t1 = L.t("ARRIVÉE 0:00", Fonts.mono(context), 9f, tint(.9f, GOLD_LT), .1f, Gravity.END).also { L.addLR(it, 32f, 32f, 474f) }
        val rp = RouteProg(context, L.zf(2f), L.zf(6f)).also { L.addLR(it, 112f, 112f, 471f, 14f) }
        prog = { rp.p = it }
        val brass = Style.img(context, "th/brass_plate.webp"); val brassBig = Style.img(context, "th/brass_plate_big.webp")
        fun bp(kind: Int, big: Boolean) = FrameLayout(context).apply {
            background = Pic(if (big) brassBig else brass, Pic.FIT); elevation = L.zf(4f)
            val g = Glyph(context, kind, 0xFF2A1F0C.toInt(), 2.2f); addView(g, LayoutParams(L.z(18f), L.z(18f), Gravity.CENTER)); if (kind == 2) play = g
        }
        val prev = bp(0, false); val pl = bp(2, true); val next = bp(3, false)
        L.add(tap(prev) { doPrev() }, 60f, 548f, 72f, 52f); L.add(tap(pl) { doPlay() }, 150f, 546f, 112f, 56f); L.add(tap(next) { doNext() }, 280f, 548f, 72f, 52f)
        out = L.t("", Fonts.mono(context), 11f, Style.ivory(.75f), .12f, Gravity.CENTER).also { L.add(tap(it) { doOutput() }, 40f, 624f, 220f, 40f) }
        val send = L.t("보내기 ›", Style.kr(context, 400), 11f, GOLD_LT, .1f, Gravity.CENTER).apply { background = shape(stroke = tint(.6f, GOLD_LT), strokePx = L.z(1f)) }
        L.add(tap(send) { doSend() }, 270f, 624f, 100f, 40f)
        nowViews += listOf(send, row, flapNote!!, sub!!, t0!!, t1!!, rp, prev, pl, next, out!!)
        // 연결편 (CORRESPONDANCES)
        val cs = FrameLayout(context); L.addLR(cs, 32f, 32f, 676f, 170f)
        val C = Lay(context, cs, L.w - L.x(64f), L.z(170f), 348f, 170f)
        C.add(C.t("CORRESPONDANCES", Fonts.mono(context), 10f, GOLD_LT, .3f), 0f, 0f)
        fun crow(top: Float, isAtv: Boolean) {
            val r = FrameLayout(context); C.addLR(r, 0f, 0f, top, 56f)
            r.addView(View(context).apply { setBackgroundColor(Style.ivory(.12f)) }, LayoutParams(-1, 1))
            val RL = Lay(context, r, C.w, C.z(56f), 348f, 56f)
            if (isAtv) atvArt = ArtView(context).also { RL.add(it, 0f, 8f, 40f, 40f) }
            else RL.add(RL.t("○", Style.jost(context, 300), 14f, Style.ivory(.5f), 0f, Gravity.CENTER).apply { setBackgroundColor(0xFF0D0D0D.toInt()) }, 0f, 8f, 40f, 40f)
            val nm = RL.t(if (isAtv) "APPLE TV" else "MARANTZ · 리시버", Style.kr(context, 400), 12f, Style.IVORY).apply {
                setBackgroundColor(0xFF0D0D0D.toInt()); gravity = Gravity.CENTER_VERTICAL; setPadding(RL.z(8f), 0, RL.z(4f), 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            RL.add(nm, 48f, 12f, 180f, 32f)
            val stt = RL.t("", Fonts.mono(context), 9f, tint(.9f, GOLD_LT), .1f, Gravity.END or Gravity.CENTER_VERTICAL)
            RL.add(stt, 230f, 12f, 66f, 32f)
            val b = RL.t("", Style.jost(context, 300), 13f, GOLD_LT, 0f, Gravity.CENTER).apply { background = shape(stroke = tint(.6f, GOLD_LT), strokePx = RL.z(1f)) }
            RL.add(tap(b) { if (isAtv) doAtv() else doMz() }, 304f, 6f, 44f, 44f)
            if (isAtv) { atvName = nm; atvSub = stt; atvBtn = b } else { mzState = stt; mzBtn = b }
            houseViews += r
        }
        crow(22f, true); crow(78f, false)
        val q = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        quietTitle = C.t("", Fonts.mono(context), 12f, Style.ivory(.9f), .1f, Gravity.CENTER); q.addView(quietTitle!!, LinearLayout.LayoutParams(-1, -2))
        val qb = hRow(); quietButtons(qb) { pad(C.t(it, Style.kr(context, 400), 12.5f, GOLD_LT), 14f).apply { background = shape(stroke = tint(.6f, GOLD_LT), strokePx = C.z(1f)) } }
        q.addView(qb, LinearLayout.LayoutParams(-2, C.z(44f)).apply { topMargin = C.z(14f) })
        C.addLR(q, 0f, 0f, 30f); quietBox = q
        val e = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        e.addView(L.t("DÉPARTS", Fonts.mono(context), 10f, GOLD_LT, .3f, Gravity.CENTER))
        e.addView(L.t("AUCUN DÉPART", Fonts.mono(context), 20f, Style.IVORY, .2f, Gravity.CENTER), LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(10f) })
        e.addView(L.t("음악을 틀면 열차가 출발합니다.", Style.kr(context, 300), 13f, Style.ivory(.75f), 0f, Gravity.CENTER).also { emptyMsg = it; emptyMsgOk = "음악을 틀면 열차가 출발합니다." }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = L.z(8f) })
        val eb = hRow(); emptyButtons(eb) { pad(L.t(it, Fonts.mono(context), 12f, 0xFF2A1F0C.toInt(), .15f), 18f).apply { background = Pic(brassBig, Pic.FIT) } }
        e.addView(eb, LinearLayout.LayoutParams(-2, L.z(48f)).apply { topMargin = L.z(18f) })
        L.addLR(e, 28f, 28f, 410f); emptyBox = e
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
        title = L.t("", Style.jost(context, 300), 30f, Style.IVORY).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }.also { L.addLR(it, 32f, 32f, 446f) }
        sub = L.t("", Style.kr(context, 300), 13f, Style.ivory(.75f)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }.also { L.addLR(it, 32f, 32f, 490f) }
        val pv = LineProg(context, Style.ivory(.2f), Style.IVORY, L.zf(1f), L.zf(1f)).also { L.addLR(it, 32f, 32f, 514f, 4f) }
        prog = { pv.p = it; credit.text = "PHOTOGRAPHIE — " + meta(MediaMetadata.METADATA_KEY_ALBUM).ifEmpty { appName() }.uppercase() }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun tb(s: String, go: () -> Unit) = L.t(s, Style.kr(context, 300), 15f, Style.ivory(.75f), .2f, Gravity.CENTER).also { row.addView(tap(it, go), LinearLayout.LayoutParams(0, -1, 1f)) }
        tb("이전") { doPrev() }
        playText = tb("멈춤") { doPlay() }.apply { setTextColor(Style.IVORY); background = UnderLine(Style.IVORY, L.zf(1f), L.zf(14f)) }
        tb("다음") { doNext() }
        L.addLR(row, 32f, 32f, 544f, 56f)
        out = L.t("", Style.kr(context, 300), 12.5f, Style.ivory(.75f)).also { L.add(tap(it) { doOutput() }, 32f, 622f, WRAP, 40f); it.gravity = Gravity.CENTER_VERTICAL }
        val send = L.t("보내기 →", Style.kr(context, 400), 12.5f, Style.IVORY, 0f, Gravity.CENTER).apply { background = UnderLine(Style.ivory(.6f), L.zf(1f), 0f) }
        L.add(tap(send) { doSend() }, 300f, 622f, 80f, 40f)
        nowViews += listOf(send, art!!, credit, l1, title!!, sub!!, pv, row, out!!)
        val hs = FrameLayout(context); L.addLR(hs, 32f, 32f, 686f, 180f)
        val H = Lay(context, hs, L.w - L.x(64f), L.z(180f), 348f, 180f)
        H.add(H.t("ELSEWHERE IN THE HOUSE —", Style.jost(context, 400), 10f, Style.ivory(.85f), .3f), 0f, 0f)
        fun crow(top: Float, label: String, act: String, go: () -> Unit): Pair<TextView, TextView> {
            val r = FrameLayout(context); H.addLR(r, 0f, 0f, top, 52f)
            r.addView(View(context).apply { setBackgroundColor(Style.ivory(.15f)) }, LayoutParams(-1, 1))
            val t = H.t(label, Style.kr(context, 300), 14f, Style.ivory(.9f)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            r.addView(t, LayoutParams(H.z(260f), -2, Gravity.CENTER_VERTICAL))
            val b = H.t(act, Style.kr(context, 400), 12f, Style.IVORY, 0f, Gravity.CENTER).apply { background = UnderLine(Style.ivory(.6f), H.zf(1f), H.zf(8f)) }
            r.addView(tap(b, go), LayoutParams(H.z(64f), H.z(44f), Gravity.END or Gravity.CENTER_VERTICAL))
            houseViews += r; return t to b
        }
        crow(20f, "거실 · Apple TV", "재생") { doAtv() }.let { atvName = it.first; atvBtn = it.second }
        crow(72f, "거실 · 마란츠 리시버", "켜기 →") { doMz() }.let { mzBtn = it.second }
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
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(c: Canvas) {
        val pad = maxOf(knobR, markerW / 2)
        val y = if (marker != null) markerH * .15f + 2f else height / 2f
        val x0 = pad; val x1 = width - pad; val x = x0 + (x1 - x0) * p
        pt.color = track; c.drawRect(x0, y - trackPx / 2, x1, y + trackPx / 2, pt)
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
class EarView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = 0x1F000000; val sh = android.graphics.Path().apply { moveTo(w, 0f); lineTo(0f, h); lineTo(w, h); close() }
        c.save(); c.translate(-3f, -3f); c.drawPath(sh, p); c.restore()
        p.color = 0xFFE3DCCF.toInt(); c.drawPath(sh, p)
    }
}
