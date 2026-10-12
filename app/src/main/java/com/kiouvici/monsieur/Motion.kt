package com.kiouvici.monsieur

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 움직임 (build-39) — 애플식 스프링: 응답 시간(response)과 감쇠(damping)로 정함.
 * 부드럽게 = 스프링(살짝 넘쳤다 쫀득하게 멈춤) · 짧게 = 0.15초 흐려졌다 나타남 · 끄기 = 바로
 */
class SpringInterp(private val response: Float, private val damping: Float, private val durMs: Long) : TimeInterpolator {
    private val w = 2 * PI / response
    override fun getInterpolation(f: Float): Float {
        val t = f * durMs / 1000.0
        if (t <= 0) return 0f
        if (f >= 1f) return 1f
        val z = damping.toDouble()
        return if (z >= 1.0) (1 - exp(-w * t) * (1 + w * t)).toFloat()
        else {
            val wd = w * sqrt(1 - z * z)
            (1 - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))).toFloat()
        }
    }
}

object Springs {
    // 성훈님: "애플처럼 부드럽게 쫀득한 속도로"
    fun drawer() = SpringInterp(.5f, .8f, 760)        // 서랍 열고 닫기 — 끝에서 살짝 넘었다 내려앉음 (성훈님: 티가 나게)
    fun open() = SpringInterp(.62f, .8f, 900)         // 폴더 펼치기 — 더 천천히 부드럽게 (성훈님)
    fun close() = SpringInterp(.5f, 1f, 560)          // 폴더 닫기 — 더 천천히 (넘침 없음)
    fun lift() = SpringInterp(.38f, .68f, 480)        // 길게 눌러 들어 올림
}

/** 0 부드럽게 · 1 짧게 · 2 끄기 (안드로이드 '애니메이션 끄기'면 끄기) */
internal val MainActivity.motionMode: Int get() = if (reduceMotion) 2 else look.motion

/** 목록 끝 고무줄: 부드럽게일 때만 안드로이드의 늘어나는 끝 효과를 켬 */
internal fun MainActivity.overMode(): Int = View.OVER_SCROLL_NEVER   // 고무줄은 RubberScroll이 직접 (안드로이드 늘어남보다 더 크게)

/** 이름 글꼴: 테마 굵기 ± 굵기 설정, 고딕/명조 */
private var serifFace: Typeface? = null
internal fun MainActivity.nameFace(w: Int): Typeface {
    val ww = (w + (look.weightMode - 1) * 100).coerceIn(100, 900)
    return when (look.fontMode) {
        2 -> serifFace ?: try { Typeface.createFromAsset(assets, "font/NotoSerifKR-500.otf") } catch (e: Exception) { Style.kr(this, ww) }.also { serifFace = it }
        else -> Style.kr(this, ww)
    }
}

/** 열 때: 누른 폴더 자리에서 커지며 펼쳐짐. scrim(0~1)은 뒤 어둡게 */
internal fun MainActivity.motionOpen(content: View, flipBack: View? = null, scrim: ((Float) -> Unit)?) {
    when (motionMode) {
        2 -> { scrim?.invoke(1f); return }
        1 -> {
            content.alpha = 0f; scrim?.invoke(0f)
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 150
                addUpdateListener { val p = it.animatedValue as Float; content.alpha = p; scrim?.invoke(p) }
                start()
            }
            return
        }
    }
    content.alpha = 0f; scrim?.invoke(0f)
    content.hap(Hap.LIGHT)
    content.post {
        val from = lastTap
        val loc = IntArray(2); content.getLocationInWindow(loc)
        val cw = content.width.coerceAtLeast(1).toFloat(); val ch = content.height.coerceAtLeast(1).toFloat()
        val fx: Float; val fy: Float; val fw: Float; val fh: Float
        if (from != null && from.width() > 0) { fx = from.left.toFloat(); fy = from.top.toFloat(); fw = from.width().toFloat(); fh = from.height().toFloat() }
        else { fw = cw * .9f; fh = ch * .9f; fx = loc[0] + cw * .05f; fy = loc[1] + ch * .05f }
        content.pivotX = 0f; content.pivotY = 0f
        val kids = (content as? ViewGroup)?.let { g -> (0 until g.childCount).map { g.getChildAt(it) } }?.filter { it !== flipBack } ?: emptyList()
        kids.forEach { it.alpha = 0f }
        // 앱 아이콘: 처음엔 숨겼다가 펼침 55%에서 차례로 떠오름 (24ms 간격, 앞 12개)
        val cells = appCellsIn(content).filter { c -> val r = android.graphics.Rect(); c.getGlobalVisibleRect(r) && r.height() > 0 }
        cells.forEach { it.alpha = 0f }
        var staggered = false
        val flip = flipBack != null
        if (flip) { content.cameraDistance = 12000 * resources.displayMetrics.density; content.pivotX = cw / 2; content.pivotY = ch / 2 }
        fun frame(p: Float) {
            val sx = fw / cw + (1 - fw / cw) * p; val sy = fh / ch + (1 - fh / ch) * p
            content.scaleX = sx; content.scaleY = sy
            if (flip) {
                // 기차표 뒤집기: 라벨 → 엽서, 90°에서 앞뒷면 바꿈 (넘침 없음)
                content.rotationY = 180f * (1 - p).coerceIn(0f, 1f)
                val cxNow = loc[0] + (fx - loc[0]) * (1 - p) + cw * sx / 2; val cyNow = loc[1] + (fy - loc[1]) * (1 - p) + ch * sy / 2
                content.translationX = cxNow - loc[0] - cw / 2; content.translationY = cyNow - loc[1] - ch / 2
                val back = p < .5f
                flipBack!!.visibility = if (back) View.VISIBLE else View.INVISIBLE; flipBack.scaleX = -1f
                kids.forEach { it.alpha = if (back) 0f else 1f }
                content.alpha = 1f
            } else {
                content.translationX = (fx - loc[0]) * (1 - p); content.translationY = (fy - loc[1]) * (1 - p)
                content.alpha = (p / .2f).coerceIn(0f, 1f)
                val k = ((p - .6f) / .3f).coerceIn(0f, 1f); kids.forEach { it.alpha = k }   // 글자는 60% 지나서
            }
            if (!staggered && p >= .55f) { staggered = true; stagger(cells) }
            scrim?.invoke(p.coerceIn(0f, 1f)); if (!flip) blurBehind(p.coerceIn(0f, 1f))
        }
        frame(0f)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (flip) 950 else 900; interpolator = if (flip) SpringInterp(.66f, .88f, 950) else Springs.open()
            addUpdateListener { frame(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { kids.forEach { it.alpha = 1f }; content.alpha = 1f; content.rotationY = 0f; flipBack?.visibility = View.GONE; if (!staggered) stagger(cells) }
            })
            start()
        }
    }
}

/** 닫을 때: 누른 폴더 자리로 줄어듦 → end() */
internal fun MainActivity.motionClose(content: View, scrim: ((Float) -> Unit)?, end: () -> Unit) {
    val flipBack = content.getTag(TAG_FLIP) as? View
    when (motionMode) {
        2 -> { blurBehind(0f); end(); return }
        1 -> {
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 150
                addUpdateListener { val p = it.animatedValue as Float; content.alpha = p; scrim?.invoke(p) }
                addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { blurBehind(0f); end() } })
                start()
            }
            return
        }
    }
    content.hap(Hap.LIGHT)
    val to = lastTap
    val loc = IntArray(2); content.getLocationInWindow(loc)
    val cw = content.width.coerceAtLeast(1).toFloat(); val ch = content.height.coerceAtLeast(1).toFloat()
    val tx: Float; val ty: Float; val tw: Float; val th: Float
    if (to != null && to.width() > 0) { tx = to.left.toFloat(); ty = to.top.toFloat(); tw = to.width().toFloat(); th = to.height().toFloat() }
    else { tw = cw * .9f; th = ch * .9f; tx = loc[0] + cw * .05f; ty = loc[1] + ch * .05f }
    content.pivotX = 0f; content.pivotY = 0f
    val kids = (content as? ViewGroup)?.let { g -> (0 until g.childCount).map { g.getChildAt(it) } }?.filter { it !== flipBack } ?: emptyList()
    val flip = flipBack != null
    if (flip) { content.cameraDistance = 12000 * resources.displayMetrics.density; content.pivotX = cw / 2; content.pivotY = ch / 2 }
    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 560; interpolator = if (flip) SpringInterp(.5f, 1f, 560) else Springs.close()
        addUpdateListener {
            val q = it.animatedValue as Float; val p = 1 - q
            val sx = tw / cw + (1 - tw / cw) * p; val sy = th / ch + (1 - th / ch) * p
            content.scaleX = sx; content.scaleY = sy
            if (flip) {
                content.rotationY = 180f * q
                val cxNow = loc[0] + (tx - loc[0]) * q + cw * sx / 2; val cyNow = loc[1] + (ty - loc[1]) * q + ch * sy / 2
                content.translationX = cxNow - loc[0] - cw / 2; content.translationY = cyNow - loc[1] - ch / 2
                val back = q > .5f
                flipBack!!.visibility = if (back) View.VISIBLE else View.INVISIBLE; flipBack.scaleX = -1f
                kids.forEach { k -> k.alpha = if (back) 0f else 1f }
                content.alpha = 1f
            } else {
                content.translationX = (tx - loc[0]) * q; content.translationY = (ty - loc[1]) * q
                kids.forEach { k -> k.alpha = (1 - q / .4f).coerceIn(0f, 1f) }
                content.alpha = (p / .25f).coerceIn(0f, 1f)
            }
            scrim?.invoke(p); blurBehind(p)
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) {
                blurBehind(0f); end()
                content.scaleX = 1f; content.scaleY = 1f; content.translationX = 0f; content.translationY = 0f; content.alpha = 1f; content.rotationY = 0f
                kids.forEach { it.alpha = 1f }
            }
        })
        start()
    }
}

/** 뒤(서랍) 흐림 6px — 안드로이드 12 이상 */
internal fun MainActivity.blurBehind(p: Float) {
    if (android.os.Build.VERSION.SDK_INT < 31 || motionMode != 0) return
    if (p > 0f && cardFolder == "__search") return   // 검색 중엔 흐림 없이 (키보드와 함께 버벅이던 것)
    val r = 6f * resources.displayMetrics.density * p
    try { box.setRenderEffect(if (r < .5f) null else android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)) } catch (_: Exception) {}
}

/** 앱 열기: 부드럽게 = 누른 아이콘 자리에서 커짐 · 짧게 = 흐려졌다 나타남 · 끄기 = 움직임 없음 */
internal fun MainActivity.launchOpts(v: View?): android.os.Bundle? = try {
    when (motionMode) {
        0 -> {
            // 삼성에서 '아이콘에서 커지기'(makeScaleUp)가 무시되고 옆으로 밀려 열리던 것 → 직접 만든 열기 움직임 (아이콘 위치에 가까운 9칸 중 하나에서 커짐)
            val loc = IntArray(2); v?.getLocationOnScreen(loc)
            val dm = resources.displayMetrics
            val col = if (v == null) 1 else ((loc[0] + v.width / 2f) / dm.widthPixels * 3).toInt().coerceIn(0, 2)
            val row = if (v == null) 1 else ((loc[1] + v.height / 2f) / dm.heightPixels * 3).toInt().coerceIn(0, 2)
            val id = resources.getIdentifier("app_open_$row$col", "anim", packageName)
            TouchLog.add("앱 열기 움직임: 칸 $row$col")
            android.app.ActivityOptions.makeCustomAnimation(this, id, R.anim.home_hold).toBundle()
        }
        1 -> android.app.ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
        else -> android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
    }
} catch (e: Exception) { null }

/**
 * 열린 폴더 화면: 좌우나 아래로 쓸면 닫힘 (성훈님, build-39)
 * 안의 목록이 맨 위가 아닐 때 아래로 쓸면 목록 스크롤이 먼저 · 앱을 길게 눌러 끄는 중에는 가로채지 않음
 */
class SwipeLayer(ctx: android.content.Context, private val onClose: () -> Unit) : android.widget.FrameLayout(ctx) {
    /** 좌우로 쓸었을 때 먼저 물어봄 (dir −1 왼쪽으로 · +1 오른쪽으로). true면 닫지 않음 — LIBRARY 책장 넘기기 */
    var onSide: ((Int) -> Boolean)? = null
    private val d = ctx.resources.displayMetrics.density
    private var x0 = 0f; private var y0 = 0f; private var t0 = 0L; private var done = false
    private var vt: android.view.VelocityTracker? = null
    override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean = check(e)
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: android.view.MotionEvent): Boolean { check(e); return super.onTouchEvent(e) || true }
    private fun check(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { x0 = e.x; y0 = e.y; t0 = e.eventTime; done = false; vt?.recycle(); vt = android.view.VelocityTracker.obtain(); vt?.addMovement(e) }
            android.view.MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (done) return true
                val dx = e.x - x0; val dy = e.y - y0
                val side = kotlin.math.abs(dx) > 48 * d && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f
                val down = dy > 56 * d && dy > kotlin.math.abs(dx) * 1.4f && !scrollableAbove(this, x0, y0)
                // 빠르게 튕긴 것도 (0.25초 안 28dp)
                val quick = e.eventTime - t0 < 250 && ((kotlin.math.abs(dx) > 28 * d && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f) ||
                    (dy > 28 * d && dy > kotlin.math.abs(dx) * 1.4f && !scrollableAbove(this, x0, y0)))
                if (side || down || quick) {
                    done = true
                    val horiz = kotlin.math.abs(dx) > kotlin.math.abs(dy)
                    if (horiz && onSide?.invoke(if (dx < 0) -1 else 1) == true) return true
                    onClose(); return true
                }
            }
        }
        return false
    }
    /** 손가락 아래에 위로 더 스크롤할 수 있는 목록이 있는지 */
    private fun scrollableAbove(v: View, x: Float, y: Float): Boolean {
        if (v !== this && v.canScrollVertically(-1)) return true
        if (v is ViewGroup) for (i in v.childCount - 1 downTo 0) {
            val c = v.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            if (x >= c.left + c.translationX && x < c.right + c.translationX && y >= c.top + c.translationY && y < c.bottom + c.translationY) {
                if (scrollableAbove(c, x - c.left - c.translationX + c.scrollX, y - c.top - c.translationY + c.scrollY)) return true
            }
        }
        return false
    }
}


// ───────── 앱 칸 모음 · 차례로 떠오르기 · 알림 점 톡 · 진동 (build-39 · 샤넬팀) ─────────
internal val TAG_FLIP get() = R.id.flip_back
internal val appCellSet = java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())
/** 화면 순서대로 앱 칸 찾기 */
internal fun appCellsIn(v: View): List<View> {
    val out = ArrayList<View>()
    fun walk(x: View) { if (x in appCellSet) { out += x; return }; if (x is ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i)) }
    walk(v); return out
}
/** 앞에서부터 12개는 45ms 간격, 나머지는 한꺼번에 — 아래 18dp에서 94%→100%, 스프링 .55 · .82 (성훈님: 더 천천히) */
internal fun MainActivity.stagger(cells: List<View>) {
    if (motionMode != 0) { cells.forEach { it.alpha = 1f }; return }
    val dy = 18 * resources.displayMetrics.density
    cells.forEachIndexed { i, c ->
        c.alpha = 0f; c.translationY = dy; c.scaleX = .94f; c.scaleY = .94f
        c.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(minOf(i, 12) * 45L).setDuration(850)
            .setInterpolator(SpringInterp(.55f, .82f, 850)).start()
    }
}
private var popsAt = 0L; private var popsN = 0
/** 알림 점 켜고 끄기: 새로 켜질 땐 0→120→100% 톡 (동시에 최대 3개), 꺼질 땐 160ms에 쏙 */
internal fun MainActivity.setDot(v: View, on: Boolean) {
    val was = v.visibility == View.VISIBLE
    if (on == was) return
    val seen = android.graphics.Rect().let { v.getGlobalVisibleRect(it) } || !on
    if (motionMode != 0 || !seen || !v.isAttachedToWindow) { v.visibility = if (on) View.VISIBLE else View.GONE; v.scaleX = 1f; v.scaleY = 1f; return }
    if (on) {
        val now = android.os.SystemClock.uptimeMillis(); if (now - popsAt > 400) { popsAt = now; popsN = 0 }
        v.visibility = View.VISIBLE
        if (popsN++ >= 3) { v.scaleX = 1f; v.scaleY = 1f; return }
        v.scaleX = 0f; v.scaleY = 0f
        v.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(SpringInterp(.3f, .55f, 520)).start()
    } else {
        v.animate().scaleX(0f).scaleY(0f).setDuration(160).setInterpolator(android.view.animation.AccelerateInterpolator()).withEndAction { v.visibility = View.GONE; v.scaleX = 1f; v.scaleY = 1f }.start()
    }
}
/**
 * 진동 — 애플 규칙 (성훈님: "애플처럼 짧고 약하게, 계속 울려도 안 피곤하게")
 * 선택(selection): 칸·견본·페이지·테마 고르기가 바뀔 때 — 가장 약한 '틱'
 * 가벼운 충격(light): 서랍·폴더·시트가 열리고 닫혀 자리에 닿을 때, 책장 넘김
 * 중간 충격(medium): 길게 눌러 들어 올림·메뉴
 * 성공(success): 테마 적용 — 짧은 두 번
 * 휴대폰의 '터치 진동' 설정을 따름 (USAGE_TOUCH)
 */
object Hap {
    const val SEL = 0; const val LIGHT = 1; const val MEDIUM = 2; const val SUCCESS = 3
    private var last = 0L
    fun play(v: View, kind: Int) {
        val now = android.os.SystemClock.uptimeMillis(); if (now - last < 40) return; last = now
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val vib = (v.context.getSystemService(android.os.VibratorManager::class.java))?.defaultVibrator
                if (vib != null && vib.hasVibrator() && vib.areAllPrimitivesSupported(android.os.VibrationEffect.Composition.PRIMITIVE_TICK, android.os.VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    val c = android.os.VibrationEffect.startComposition()
                    when (kind) {
                        SEL -> c.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_TICK, .35f)
                        LIGHT -> c.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_TICK, .7f)
                        MEDIUM -> c.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_CLICK, .45f)
                        else -> { c.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_CLICK, .35f); c.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_TICK, .6f, 70) }
                    }
                    vib.vibrate(c.compose(), android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_TOUCH))
                    return
                }
            }
        } catch (_: Exception) {}
        v.performHapticFeedback(when (kind) {
            SEL -> android.view.HapticFeedbackConstants.CLOCK_TICK
            LIGHT -> android.view.HapticFeedbackConstants.VIRTUAL_KEY
            MEDIUM -> android.view.HapticFeedbackConstants.LONG_PRESS
            else -> if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.VIRTUAL_KEY
        })
    }
}
internal fun View.hap(kind: Int) = Hap.play(this, kind)

/**
 * 목록 끝 고무줄 (애플식, 성훈님 "더 많이 늘었다 돌아오게"): 끝에서 더 당기면 (1 − 1/(x·0.55/D + 1))·D 만큼 따라 늘어나고
 * 놓으면 스프링(.40 · 1.0)으로 돌아옴. D = 220dp. 부드럽게일 때만.
 */
class RubberScroll(ctx: android.content.Context, private val allowTop: Boolean = true, private val on: () -> Boolean) : android.widget.ScrollView(ctx) {
    // build-42: 누르기·길게 누르기를 절대 가로채지 않게 — 세로로 16dp 넘게 끌고, 그 방향 끝일 때만 고무줄 시작
    // (예전엔 손가락이 조금만 흔들려도 목록이 터치를 가져가 앱이 안 눌리고 목록만 출렁이던 것 — 성훈님 영상)
    private val dd = 220 * ctx.resources.displayMetrics.density
    private val startPx = 16 * ctx.resources.displayMetrics.density
    private var y0 = 0f; private var lastY = 0f; private var raw = 0f; private var rubbering = false
    private var back: ValueAnimator? = null
    private fun rb(x: Float): Float { val a = kotlin.math.abs(x); return Math.signum(x) * (1 - 1 / (a * .55f / dd + 1)) * dd }
    private fun child() = if (childCount > 0) getChildAt(0) else null
    private fun atEdge(dy: Float) = (dy > 0 && allowTop && !canScrollVertically(-1)) || (dy < 0 && !canScrollVertically(1))
    override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { y0 = e.y; lastY = e.y; raw = 0f; rubbering = false }
            android.view.MotionEvent.ACTION_MOVE -> if (on() && !rubbering) {
                val dy = e.y - y0
                if (kotlin.math.abs(dy) > startPx && atEdge(dy)) { rubbering = true; lastY = e.y; raw = 0f; back?.cancel(); return true }
            }
        }
        return super.onInterceptTouchEvent(e)
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (!on()) return super.onTouchEvent(e)
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { y0 = e.y; lastY = e.y }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dy = e.y - lastY
                if (!rubbering && kotlin.math.abs(e.y - y0) > startPx && atEdge(dy)) { rubbering = true; raw = 0f; back?.cancel() }   // 스크롤하다 끝에 닿으면 이어서 늘어남
                lastY = e.y
                if (rubbering) {
                    val was = raw; raw += dy
                    if (was != 0f && Math.signum(raw) != Math.signum(was)) { raw = 0f; rubbering = false; child()?.translationY = 0f; return super.onTouchEvent(e) }
                    child()?.translationY = rb(raw); return true
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> if (rubbering) {
                rubbering = false; val c = child(); val from = c?.translationY ?: 0f
                back = ValueAnimator.ofFloat(from, 0f).apply {
                    duration = 450; interpolator = SpringInterp(.4f, 1f, 450)
                    addUpdateListener { c?.translationY = it.animatedValue as Float }; start()
                }
                raw = 0f
                super.onTouchEvent(android.view.MotionEvent.obtain(e).apply { action = android.view.MotionEvent.ACTION_CANCEL })   // 목록 상태 정리
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
