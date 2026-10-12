package com.kiouvici.monsieur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** 테마 화면 공용 도구 — 목업(412×915 설계 좌표)을 실제 화면 크기에 맞춰 그대로 옮김 */

const val WRAP = -2f
const val FILL = -1f
const val GOLD_LT = 0xFFE2C78E.toInt()
const val NAVY = 0xFF12233D.toInt()

object Fonts {
    private val cache = HashMap<String, Typeface>()
    private fun f(ctx: Context, file: String) = cache.getOrPut(file) {
        try { Typeface.createFromAsset(ctx.assets, "font/$file") } catch (e: Exception) { Typeface.SERIF }
    }
    fun serif(ctx: Context) = f(ctx, "Cormorant-500.ttf")        // Cormorant Garamond 500
    fun serifI(ctx: Context) = f(ctx, "Cormorant-400i.ttf")      // Cormorant Garamond 400 기울임
    fun mono(ctx: Context) = f(ctx, "PlexMono-400.ttf")          // IBM Plex Mono
    fun serifKR(ctx: Context) = f(ctx, "NotoSerifKR-500.otf")    // Noto Serif KR 500 (원고지 · 문장 속 한글)
}

/** 한글 낱말 안에서 줄이 바뀌지 않게 (CSS keep-all): 붙어 있는 한글 글자 사이에 줄바꿈 금지 문자(U+2060)를 넣음 */
private val HANGUL_PAIR = Regex("(?<=[\\uAC00-\\uD7A3])(?=[\\uAC00-\\uD7A3])")
fun keepAll(s: CharSequence?): CharSequence? {
    if (s == null || s.length < 2) return s
    if (!HANGUL_PAIR.containsMatchIn(s)) return s
    if (s !is android.text.Spanned) return HANGUL_PAIR.replace(s, "\u2060")
    val b = android.text.SpannableStringBuilder(s)
    for (i in b.length - 1 downTo 1) {
        val a = b[i - 1]; val c = b[i]
        if (a in '\uAC00'..'\uD7A3' && c in '\uAC00'..'\uD7A3') b.insert(i, "\u2060")
    }
    return b
}
/** 여러 줄 한글이 낱말 단위로 줄바꿈되는 TextView */
class KText(ctx: Context) : TextView(ctx) {
    override fun setText(text: CharSequence?, type: BufferType?) { super.setText(keepAll(text), type) }
}

fun Context.txt(text: CharSequence, tf: Typeface, sp: Float, color: Int, spacing: Float = 0f, g: Int = Gravity.START): TextView =
    KText(this).apply {
        this.text = text; typeface = tf; textSize = sp; setTextColor(color); letterSpacing = spacing; gravity = g
        includeFontPadding = false
    }

fun tint(a: Float, rgb: Int) = Color.argb((a * 255).roundToInt().coerceIn(0, 255), Color.red(rgb), Color.green(rgb), Color.blue(rgb))

/** 설계 좌표(dw×dh dp) → 실제 픽셀. 가로는 화면 폭 비율, 세로는 화면 높이 비율, 크기는 둘 중 작은 비율 */
class Lay(val ctx: Context, val host: FrameLayout, wPx: Int, hPx: Int, dw: Float = 412f, dh: Float = 915f) {
    val d = ctx.resources.displayMetrics.density
    val sx = wPx / (dw * d)
    val sy = hPx / (dh * d)
    val s = minOf(sx, sy)
    val w = wPx; val h = hPx
    fun x(v: Float) = (v * sx * d).roundToInt()
    fun y(v: Float) = (v * sy * d).roundToInt()
    fun z(v: Float) = (v * s * d).roundToInt()
    fun zf(v: Float) = v * s * d
    fun sp(v: Float) = v * s
    private fun dim(v: Float) = when (v) { WRAP -> -2; FILL -> -1; else -> z(v) }

    /** 왼쪽 위 기준 */
    fun add(v: View, left: Float, top: Float, width: Float = WRAP, height: Float = WRAP): View {
        host.addView(v, FrameLayout.LayoutParams(dim(width), dim(height)).apply { leftMargin = x(left); topMargin = y(top) })
        return v
    }
    /** 가운데 기준 (cx = 가로 중심, 없으면 화면 가운데) */
    fun addC(v: View, top: Float, width: Float = WRAP, height: Float = WRAP, cx: Float = 206f): View {
        val wp = dim(width)
        if (wp >= 0) host.addView(v, FrameLayout.LayoutParams(wp, dim(height)).apply { leftMargin = x(cx) - wp / 2; topMargin = y(top) })
        else host.addView(v, FrameLayout.LayoutParams(-1, dim(height)).apply { topMargin = y(top) }).also { (v as? TextView)?.gravity = Gravity.CENTER_HORIZONTAL }
        return v
    }
    /** 좌우 여백 기준 (가로로 꽉) */
    fun addLR(v: View, left: Float, right: Float, top: Float, height: Float = WRAP): View {
        host.addView(v, FrameLayout.LayoutParams(-1, dim(height)).apply { leftMargin = x(left); rightMargin = x(right); topMargin = y(top) })
        return v
    }
    /** 아래 기준 */
    fun addB(v: View, left: Float, right: Float, bottom: Float, height: Float = WRAP): View {
        host.addView(v, FrameLayout.LayoutParams(-1, dim(height), Gravity.BOTTOM).apply { leftMargin = x(left); rightMargin = x(right); bottomMargin = y(bottom) })
        return v
    }
    fun t(text: CharSequence, tf: Typeface, sp: Float, color: Int, spacing: Float = 0f, g: Int = Gravity.START) = ctx.txt(text, tf, sp(sp), color, spacing, g)
}

/** 그림 한 장을 칸에 늘려 채우기 */
class Pic(private val b: Bitmap, private val mode: Int = FIT) : Drawable() {
    companion object { const val FIT = 0; const val COVER = 1; const val CONTAIN = 2 }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun draw(c: Canvas) {
        val r = bounds
        when (mode) {
            FIT -> c.drawBitmap(b, null, r, p)
            COVER, CONTAIN -> {
                val k = if (mode == COVER) maxOf(r.width() / b.width.toFloat(), r.height() / b.height.toFloat())
                        else minOf(r.width() / b.width.toFloat(), r.height() / b.height.toFloat())
                val w = b.width * k; val h = b.height * k
                val l = r.left + (r.width() - w) / 2; val t = r.top + (r.height() - h) / 2
                c.save(); c.clipRect(r); c.drawBitmap(b, null, RectF(l, t, l + w, t + h), p); c.restore()
            }
        }
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** 질감을 거울 바둑판으로 — 이음매가 생기지 않게 (호두나무 탁자) */
fun mirrorTile(ctx: Context, b: Bitmap, sizePx: Int): Drawable {
    val k = sizePx / b.width.toFloat()
    val sb = if (kotlin.math.abs(k - 1f) < .02f) b else Bitmap.createScaledBitmap(b, sizePx, (b.height * k).roundToInt().coerceAtLeast(1), true)
    return android.graphics.drawable.BitmapDrawable(ctx.resources, sb).apply { setTileModeXY(Shader.TileMode.MIRROR, Shader.TileMode.MIRROR) }
}

/** 금박 액자: 그림 가장자리(slice px)를 9칸으로 나눠 테두리만 그림 (가운데는 비움) */
class FrameDrawable(private val b: Bitmap, private val slice: Int, private val edgePx: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun draw(c: Canvas) {
        val r = bounds; val s = slice; val e = edgePx; val W = b.width; val H = b.height
        fun piece(sl: Int, st: Int, sr: Int, sb: Int, dl: Int, dt: Int, dr: Int, db: Int) =
            c.drawBitmap(b, Rect(sl, st, sr, sb), Rect(dl, dt, dr, db), p)
        piece(0, 0, s, s, r.left, r.top, r.left + e, r.top + e)
        piece(W - s, 0, W, s, r.right - e, r.top, r.right, r.top + e)
        piece(0, H - s, s, H, r.left, r.bottom - e, r.left + e, r.bottom)
        piece(W - s, H - s, W, H, r.right - e, r.bottom - e, r.right, r.bottom)
        piece(s, 0, W - s, s, r.left + e, r.top, r.right - e, r.top + e)
        piece(s, H - s, W - s, H, r.left + e, r.bottom - e, r.right - e, r.bottom)
        piece(0, s, s, H - s, r.left, r.top + e, r.left + e, r.bottom - e)
        piece(W - s, s, W, H - s, r.right - e, r.top + e, r.right, r.bottom - e)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** 승차권 절취선 (점선 구멍) */
class Perf(private val hole: Int, private val holeR: Float, private val stepPx: Float) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hole }
    override fun draw(c: Canvas) {
        val r = bounds; var x = r.left + stepPx / 2
        while (x < r.right) { c.drawCircle(x, r.exactCenterY(), holeR, p); x += stepPx }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** 단색·그라데이션 사각형 */
fun vgrad(vararg c: Int) = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, c)
fun shape(fill: Int = Color.TRANSPARENT, stroke: Int = Color.TRANSPARENT, strokePx: Int = 0, radius: Float = 0f, oval: Boolean = false) =
    android.graphics.drawable.GradientDrawable().apply {
        if (oval) shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(fill); if (strokePx > 0) setStroke(strokePx, stroke); cornerRadius = radius
    }

/** 앨범아트 칸: 사진이 없으면 테마 색 대체 그림. 원형·사각 */
class ArtView(ctx: Context, private val circle: Boolean = false, private val ring: Int = 0, private val ringPx: Float = 0f) : View(ctx) {
    var bmp: Bitmap? = null
        set(v) { field = v; invalidate() }
    var placeholder = 0   // 0 짙은 남색 그라데이션 · 1 밤 객실 창 (빈 남색)
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val clip = Path().apply { if (circle) addOval(0f, 0f, w, h, Path.Direction.CW) else addRect(0f, 0f, w, h, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        val b = bmp
        if (b != null) {
            val k = maxOf(w / b.width, h / b.height); val bw = b.width * k; val bh = b.height * k
            // 대체 그림을 그리느라 바뀐 붓 색(투명도 18~25%)이 남아 앨범 그림이 뿌옇게 그려지던 것 — 그림은 전용 붓으로 (build-39)
            c.drawBitmap(b, null, RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), bp)
        } else if (placeholder == 1) {
            p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF0B1830.toInt(), 0xFF1B2C4A.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
        } else {
            p.shader = LinearGradient(0f, 0f, w * .4f, h, intArrayOf(0xFF3A4654.toInt(), 0xFF1C2631.toInt(), 0xFF0E141B.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p); p.shader = null
            p.color = tint(.18f, Style.IVORY)
            val r = minOf(w, h)
            c.drawCircle(w * .5f, h * .5f, r * .08f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = r * .006f; p.color = tint(.25f, Style.IVORY)
            c.drawCircle(w * .5f, h * .5f, r * .2f, p); p.style = Paint.Style.FILL
        }
        c.restore()
        if (ringPx > 0f) { rp.color = ring; rp.strokeWidth = ringPx; c.drawOval(ringPx / 2, ringPx / 2, w - ringPx / 2, h - ringPx / 2, rp) }
    }
}

/** 재생 버튼 기호 (코드로 그림): 0 이전 · 1 재생 · 2 멈춤 · 3 다음 · 4 전원 */
class Glyph(ctx: Context, var kind: Int, private val color: Int, private val strokeDp: Float, private val fill: Boolean = false) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    fun set(k: Int) { kind = k; invalidate() }
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) / 24f
        c.save(); c.translate((width - 24 * s) / 2, (height - 24 * s) / 2); c.scale(s, s)
        p.color = color; p.strokeWidth = strokeDp * resources.displayMetrics.density / s
        p.style = if (fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        val path = Path()
        when (kind) {
            0 -> { c.drawLine(6f, 5f, 6f, 19f, p.apply { style = Paint.Style.STROKE }); path.moveTo(19f, 5f); path.lineTo(9f, 12f); path.lineTo(19f, 19f); path.close() }
            1 -> { path.moveTo(8f, 5f); path.lineTo(19f, 12f); path.lineTo(8f, 19f); path.close() }
            2 -> { p.style = Paint.Style.STROKE; c.drawLine(8f, 5f, 8f, 19f, p); c.drawLine(16f, 5f, 16f, 19f, p) }
            3 -> { c.drawLine(18f, 5f, 18f, 19f, p.apply { style = Paint.Style.STROKE }); path.moveTo(5f, 5f); path.lineTo(15f, 12f); path.lineTo(5f, 19f); path.close() }
            4 -> { p.style = Paint.Style.STROKE; c.drawArc(5f, 5f, 19f, 19f, -60f, 300f, false, p); c.drawLine(12f, 3f, 12f, 11f, p) }
        }
        if (kind == 0 || kind == 1 || kind == 3) { p.style = if (fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE; c.drawPath(path, p) }
        c.restore()
    }
}

fun fmtTime(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
