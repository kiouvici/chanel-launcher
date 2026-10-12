package com.kiouvici.monsieur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/** 보기 · DISPLAY 슬라이드: 1px 선 · 채운 부분 2px · 14dp 검정 손잡이 · 기본값 눈금(아래 6dp) */
class Slide(ctx: Context, val min: Float, val max: Float, val step: Float, var value: Float, private val def: Float) : View(ctx) {
    var onChange: ((Float) -> Unit)? = null
    var onRelease: ((Float) -> Unit)? = null
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pad = 8 * d
    private fun fx(v: Float) = pad + (v - min) / (max - min) * (width - 2 * pad)
    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        p.style = Paint.Style.FILL
        p.color = Color.argb(64, 10, 10, 10); c.drawRect(pad, cy - .5f * d, width - pad, cy + .5f * d, p)
        p.color = Style.INK; c.drawRect(pad, cy - 1f * d, fx(value), cy + 1f * d, p)
        p.color = Color.argb(128, 0x2B, 0x25, 0x20); val dx = fx(def); c.drawRect(dx - .5f * d, cy + 4 * d, dx + .5f * d, cy + 10 * d, p)
        p.color = Style.INK; p.setShadowLayer(3 * d, 0f, d, Color.argb(77, 0, 0, 0)); c.drawCircle(fx(value), cy, 7 * d, p); p.clearShadowLayer()
    }
    private var dragging = false
    private var x0 = 0f; private var y0 = 0f
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x0 = e.x; y0 = e.y; dragging = false; parent?.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    if (kotlin.math.abs(e.y - y0) > 10 * d && kotlin.math.abs(e.y - y0) > kotlin.math.abs(e.x - x0)) { parent?.requestDisallowInterceptTouchEvent(false); return false }
                    if (kotlin.math.abs(e.x - x0) > 4 * d) dragging = true
                }
                if (dragging) set(e.x)
                return true
            }
            MotionEvent.ACTION_UP -> { if (!dragging) set(e.x); dragging = false; onRelease?.invoke(value); return true }
            MotionEvent.ACTION_CANCEL -> { dragging = false; onRelease?.invoke(value) }
        }
        return true
    }
    private fun set(x: Float) {
        val raw = min + ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f) * (max - min)
        val v = ((raw / step).roundToInt() * step).coerceIn(min, max)
        if (v != value) {
            // 기본값 눈금을 지날 때 '틱' (샤넬팀)
            if ((value - def) * (v - def) <= 0f && v != value) hap(Hap.SEL)
            value = v; invalidate(); onChange?.invoke(v)
        }
    }
    fun reset() { value = def; invalidate(); onChange?.invoke(def); onRelease?.invoke(def) }
}

/** 색 고르기 띠: 0 색(차분한 무지개) · 1 밝기(검정→색→흰색) · 2 진하기(투명→색). 값 0~1 */
class ColorBar(ctx: Context, private val kind: Int) : View(ctx) {
    var value = 0f
    var hue = 0f; var tone = .5f
    var onChange: ((Float) -> Unit)? = null
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chk = Paint()
    override fun onDraw(c: Canvas) {
        val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val w = width.toFloat()
        when (kind) {
            0 -> p.shader = LinearGradient(0f, 0f, w, 0f, IntArray(13) { hsl(it * 30f, .7f, .5f) }, null, Shader.TileMode.CLAMP)
            1 -> p.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(Color.BLACK, hsl(hue, .7f, .5f), Color.WHITE), null, Shader.TileMode.CLAMP)
            else -> {
                val s = 5 * d
                var y = 0f; var row = 0
                while (y < height) { var x = if (row % 2 == 0) 0f else s; while (x < w) { chk.color = 0xFFDDDDDD.toInt(); c.drawRect(x, y, x + s, y + s, chk); x += 2 * s }; y += s; row++ }
                val col = hsl(hue, .7f, tone)
                p.shader = LinearGradient(0f, 0f, w, 0f, col and 0x00FFFFFF, col, Shader.TileMode.CLAMP)
            }
        }
        c.drawRect(r, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = Color.argb(51, 0x2B, 0x25, 0x20); c.drawRect(r, p)
        val x = (value * w).coerceIn(3 * d, w - 3 * d)
        p.strokeWidth = 2 * d; p.color = Style.INK; c.drawRect(x - 3 * d, -0f + d, x + 3 * d, height - d, p)
        p.style = Paint.Style.FILL
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        value = (e.x / width).coerceIn(0f, 1f); invalidate(); onChange?.invoke(value)
        return true
    }
    companion object {
        /** HSL → 색 (h 0~360, s·l 0~1) */
        fun hsl(h: Float, s: Float, l: Float): Int {
            val c = (1 - kotlin.math.abs(2 * l - 1)) * s
            val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
            val m = l - c / 2
            val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
                0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
            }
            return Color.rgb(((r + m) * 255).roundToInt().coerceIn(0, 255), ((g + m) * 255).roundToInt().coerceIn(0, 255), ((b + m) * 255).roundToInt().coerceIn(0, 255))
        }
    }
}
