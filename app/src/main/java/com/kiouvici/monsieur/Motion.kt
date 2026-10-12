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
    fun drawer() = SpringInterp(.45f, .86f, 620)      // 서랍 열고 닫기
    fun open() = SpringInterp(.5f, .78f, 720)         // 폴더 펼치기 (1.5% 넘침)
    fun close() = SpringInterp(.38f, 1f, 420)         // 폴더 닫기 (넘침 없음)
    fun lift() = SpringInterp(.35f, .7f, 420)         // 길게 눌러 들어 올림
}

/** 0 부드럽게 · 1 짧게 · 2 끄기 (안드로이드 '애니메이션 끄기'면 끄기) */
internal val MainActivity.motionMode: Int get() = if (reduceMotion) 2 else look.motion

/** 목록 끝 고무줄: 부드럽게일 때만 안드로이드의 늘어나는 끝 효과를 켬 */
internal fun MainActivity.overMode(): Int = if (motionMode == 0) View.OVER_SCROLL_ALWAYS else View.OVER_SCROLL_NEVER

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
internal fun MainActivity.motionOpen(content: View, scrim: ((Float) -> Unit)?) {
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
    content.post {
        val from = lastTap
        val loc = IntArray(2); content.getLocationInWindow(loc)
        val cw = content.width.coerceAtLeast(1).toFloat(); val ch = content.height.coerceAtLeast(1).toFloat()
        val fx: Float; val fy: Float; val fw: Float; val fh: Float
        if (from != null && from.width() > 0) { fx = from.left.toFloat(); fy = from.top.toFloat(); fw = from.width().toFloat(); fh = from.height().toFloat() }
        else { fw = cw * .9f; fh = ch * .9f; fx = loc[0] + cw * .05f; fy = loc[1] + ch * .05f }
        content.pivotX = 0f; content.pivotY = 0f
        val kids = (content as? ViewGroup)?.let { g -> (0 until g.childCount).map { g.getChildAt(it) } } ?: emptyList()
        kids.forEach { it.alpha = 0f }
        fun frame(p: Float) {
            content.scaleX = fw / cw + (1 - fw / cw) * p; content.scaleY = fh / ch + (1 - fh / ch) * p
            content.translationX = (fx - loc[0]) * (1 - p); content.translationY = (fy - loc[1]) * (1 - p)
            content.alpha = (p / .2f).coerceIn(0f, 1f)
            val k = ((p - .6f) / .3f).coerceIn(0f, 1f); kids.forEach { it.alpha = k }   // 글자는 60% 지나서
            scrim?.invoke(p.coerceIn(0f, 1f)); blurBehind(p.coerceIn(0f, 1f))
        }
        frame(0f)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720; interpolator = Springs.open()
            addUpdateListener { frame(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { kids.forEach { it.alpha = 1f }; content.alpha = 1f }
            })
            start()
        }
    }
}

/** 닫을 때: 누른 폴더 자리로 줄어듦 → end() */
internal fun MainActivity.motionClose(content: View, scrim: ((Float) -> Unit)?, end: () -> Unit) {
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
    val to = lastTap
    val loc = IntArray(2); content.getLocationInWindow(loc)
    val cw = content.width.coerceAtLeast(1).toFloat(); val ch = content.height.coerceAtLeast(1).toFloat()
    val tx: Float; val ty: Float; val tw: Float; val th: Float
    if (to != null && to.width() > 0) { tx = to.left.toFloat(); ty = to.top.toFloat(); tw = to.width().toFloat(); th = to.height().toFloat() }
    else { tw = cw * .9f; th = ch * .9f; tx = loc[0] + cw * .05f; ty = loc[1] + ch * .05f }
    content.pivotX = 0f; content.pivotY = 0f
    val kids = (content as? ViewGroup)?.let { g -> (0 until g.childCount).map { g.getChildAt(it) } } ?: emptyList()
    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 420; interpolator = Springs.close()
        addUpdateListener {
            val q = it.animatedValue as Float; val p = 1 - q
            content.scaleX = tw / cw + (1 - tw / cw) * p; content.scaleY = th / ch + (1 - th / ch) * p
            content.translationX = (tx - loc[0]) * q; content.translationY = (ty - loc[1]) * q
            kids.forEach { k -> k.alpha = (1 - q / .4f).coerceIn(0f, 1f) }
            content.alpha = (p / .25f).coerceIn(0f, 1f)
            scrim?.invoke(p); blurBehind(p)
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) {
                blurBehind(0f); end()
                content.scaleX = 1f; content.scaleY = 1f; content.translationX = 0f; content.translationY = 0f; content.alpha = 1f
                kids.forEach { it.alpha = 1f }
            }
        })
        start()
    }
}

/** 뒤(서랍) 흐림 6px — 안드로이드 12 이상 */
internal fun MainActivity.blurBehind(p: Float) {
    if (android.os.Build.VERSION.SDK_INT < 31 || motionMode != 0) return
    val r = 6f * resources.displayMetrics.density * p
    try { box.setRenderEffect(if (r < .5f) null else android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP)) } catch (_: Exception) {}
}

/** 앱 열기: 부드럽게 = 누른 아이콘 자리에서 커짐 · 짧게 = 흐려졌다 나타남 · 끄기 = 움직임 없음 */
internal fun MainActivity.launchOpts(v: View?): android.os.Bundle? = try {
    when (motionMode) {
        0 -> if (v != null && v.width > 0) android.app.ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height).toBundle() else null
        1 -> android.app.ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle()
        else -> android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
    }
} catch (e: Exception) { null }
