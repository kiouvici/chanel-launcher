package com.kiouvici.monsieur

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import java.io.File
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*  build-28 (샤넬팀 최종 통과본) 공용 조각
 *  · HOUR 실사 문자판 NOIR / IVOIRE + 바늘
 *  · VOYAGE 줄무늬 띠 · 직조 라벨 · 대표사진 칸
 *  · LIBRARY 원고지
 *  · HOUR 세라믹 판 · 창
 *  · 미디어 볼륨 막대 (PHONE / MARANTZ, 마란츠 상한 50)  */

const val H_GOLD = 0xFFC9B58C.toInt()      // 베이지 골드 (선에만)
const val H_IVORY = 0xFFEDE8DF.toInt()
const val V_NAVY = 0xFF1A2740.toInt()      // VOYAGE 바탕
const val V_INK = 0xFF1B2A4A.toInt()       // VOYAGE 글자 남색
const val V_IVORY = 0xFFF1ECE2.toInt()
const val V_LABEL = 0xFFEFE8DA.toInt()
const val M_INK = 0xFF2B2520.toInt()       // 원고지 먹색
const val M_RED = 0xFFA3222B.toInt()       // 알림 빨강

private fun drawableOf(fn: (Canvas, android.graphics.Rect) -> Unit): Drawable = object : Drawable() {
    override fun draw(c: Canvas) = fn(c, bounds)
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

// ═════════ HOUR 문자판 (지름 324 설계) ═════════
class HourDial(ctx: Context, var kind: Int) : View(ctx) {
    var hour = 10; var minute = 8; var day = 1
    private var shownMin = -1f
    private var anim: ValueAnimator? = null
    private val bmpA = Style.img(ctx, "th/hour_dial_a.webp")
    private val bmpB = Style.img(ctx, "th/hour_dial_b.webp")
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sh = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }   // 바늘 그림자 흐림

    /** 분이 바뀌면 바늘이 180ms 동안 '톡' 넘어감 */
    fun set(h: Int, m: Int, d: Int, animate: Boolean) {
        val from = if (shownMin < 0) m.toFloat() else shownMin
        hour = h; minute = m; day = d
        anim?.cancel()
        val to = if (m == 0 && from > 50f) 60f else m.toFloat()
        if (!animate || from == to) { shownMin = m.toFloat(); invalidate(); return }
        anim = ValueAnimator.ofFloat(from, to).apply {
            duration = 180; interpolator = android.view.animation.OvershootInterpolator(1.4f)
            addUpdateListener { shownMin = (it.animatedValue as Float) % 60f; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val s = w / 324f; val cx = w / 2; val cy = w / 2
        val clip = Path().apply { addCircle(cx, cy, w / 2, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        val b = if (kind == 0) bmpA else bmpB
        val k = if (kind == 0) 1.03f else 1.01f
        val d = w * k
        c.drawBitmap(b, null, RectF(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2), p)
        if (kind == 0) {
            p.shader = LinearGradient(0f, 0f, w * .55f, w * .55f, Color.argb(15, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, w, p); p.shader = null
        } else {
            // 왼쪽 위 반사 줄이기 + 테두리 안쪽 그늘
            p.shader = RadialGradient(w * .28f, w * .22f, w * .4f, Color.argb(26, 60, 50, 40), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, w, p); p.shader = null
            // 6시 눈금을 날짜창이 대신함
            p.shader = LinearGradient(0f, 256 * s, 0f, 306 * s, Color.rgb(216, 207, 190), Color.rgb(203, 191, 174), Shader.TileMode.CLAMP)
            c.drawRect(154 * s, 256 * s, 170 * s, 306 * s, p); p.shader = null
        }
        c.restore()
        // 테두리
        p.style = Paint.Style.STROKE
        if (kind == 1) {
            p.color = Color.argb(128, 0, 0, 0); p.strokeWidth = 2 * s; c.drawCircle(cx, cy, w / 2 - s, p)
        }
        p.color = tint(if (kind == 0) .25f else .3f, H_GOLD); p.strokeWidth = s; c.drawCircle(cx, cy, w / 2 - s / 2, p)
        p.style = Paint.Style.FILL
        // 날짜
        tp.typeface = Style.jost(context, 500); tp.color = 0xFF1A1A1A.toInt()
        if (kind == 0) {
            tp.textSize = 9 * s
            c.drawText("$day", 164 * s, 274.5f * s - (tp.ascent() + tp.descent()) / 2, tp)
        } else {
            val r = RectF(150 * s, 276 * s, 174 * s, 296 * s)
            p.color = 0xFFFBF8F2.toInt(); c.drawRoundRect(r, 2 * s, 2 * s, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = .75f * s; p.color = 0xFF1A1A1A.toInt(); c.drawRoundRect(r, 2 * s, 2 * s, p); p.style = Paint.Style.FILL
            tp.textSize = 10 * s
            c.drawText("$day", r.centerX(), r.centerY() - (tp.ascent() + tp.descent()) / 2, tp)
        }
        // 바늘
        val m = if (shownMin < 0) minute.toFloat() else shownMin
        val hAng = ((hour % 12) + m / 60f) * 30f; val mAng = m * 6f
        if (kind == 0) {
            hand(c, cx, cy, hAng, 82 * s, 9 * s, 3 * s, 5 * s, 4 * s, true)
            hand(c, cx, cy, mAng, 128 * s, 6.5f * s, 5 * s, 8 * s, 5 * s, true)
        } else {
            hand(c, cx, cy, hAng, 92 * s, 10 * s, 2 * s, 4 * s, 3 * s, false)
            hand(c, cx, cy, mAng, 140 * s, 7 * s, 2 * s, 4 * s, 3 * s, false)
        }
        p.shader = RadialGradient(cx, cy, 7 * s, H_IVORY, 0xFF8C7B58.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, 7 * s, p); p.shader = null
    }

    /** 도핀 바늘: 금빛 두 면(NOIR) · 검정 + 금 가장자리(IVOIRE) */
    private fun hand(c: Canvas, cx: Float, cy: Float, ang: Float, len: Float, wid: Float, sx: Float, sy: Float, blur: Float, gold: Boolean) {
        val a = Math.toRadians(ang.toDouble())
        val ux = sin(a).toFloat(); val uy = -cos(a).toFloat(); val px = -uy; val py = ux
        val tailX = cx - ux * len * .12f; val tailY = cy - uy * len * .12f
        val tipX = cx + ux * len; val tipY = cy + uy * len
        val bx = cx + ux * len * .18f; val by = cy + uy * len * .18f
        val lX = bx + px * wid / 2; val lY = by + py * wid / 2; val rX = bx - px * wid / 2; val rY = by - py * wid / 2
        val full = Path().apply { moveTo(tailX, tailY); lineTo(lX, lY); lineTo(tipX, tipY); lineTo(rX, rY); close() }
        sh.color = Color.argb(if (gold) 140 else 90, 0, 0, 0); sh.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        c.save(); c.translate(sx, sy); c.drawPath(full, sh); c.restore()
        if (gold) {
            p.color = 0xFFE2D3AE.toInt(); c.drawPath(Path().apply { moveTo(tailX, tailY); lineTo(lX, lY); lineTo(tipX, tipY); close() }, p)
            p.color = 0xFFA8946A.toInt(); c.drawPath(Path().apply { moveTo(tailX, tailY); lineTo(rX, rY); lineTo(tipX, tipY); close() }, p)
        } else {
            p.color = 0xFF121212.toInt(); c.drawPath(full, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = .75f * width / 324f; p.color = H_GOLD; c.drawPath(full, p); p.style = Paint.Style.FILL
        }
    }
}

// ═════════ VOYAGE ═════════
/** 마리니에르 띠: 남색 12dp 위에 아이보리 2dp 줄 셋 */
fun stripesDrawable(ctx: Context): Drawable = drawableOf { c, b ->
    val p = Paint()
    val u = b.height() / 12f
    if (Dk.voy) {
        // 블랙: 아이보리 두 줄 사이 남색 한 줄 (바탕은 니트가 비침)
        listOf(V_IVORY, Dk.VOY_NAVY2, V_IVORY).forEachIndexed { k, col -> p.color = col; c.drawRect(b.left.toFloat(), b.top + k * 4 * u, b.right.toFloat(), b.top + k * 4 * u + 2 * u, p) }
        return@drawableOf
    }
    p.color = V_INK; c.drawRect(b, p)
    p.color = V_IVORY
    for (k in 0 until 3) c.drawRect(b.left.toFloat(), b.top + k * 4 * u, b.right.toFloat(), b.top + k * 4 * u + 2 * u, p)
}

/** 남색 바탕 + 니트 무늬 (alpha) — 블랙이면 검은 니트(차가운 검정) */
fun jerseyDrawable(ctx: Context, alpha: Float): Drawable =
    if (Dk.voy) android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable(Dk.VOY_BG), Pic(Style.img(ctx, "th/voy_knit.webp"), Pic.COVER)))
    else android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable(V_NAVY),
        Style.tile(ctx, "th/jersey_navy2.webp", 256f).apply { setAlpha((alpha * 255).roundToInt()) }))

/** LIBRARY·VOYAGE 바탕 · 블랙(기본) / 밝게 — 샤넬팀: HA 보드(검정)와 한 벌 */
object Dk {
    @JvmStatic var lib = true
    @JvmStatic var voy = true
    const val LIB_BG = 0xFF15130F.toInt()      // 먹지 (따뜻한 검정)
    const val LIB_PAGE = 0xFF1E1B17.toInt()    // 열린 책장
    const val LIB_TX = 0xFFEDE6D8.toInt()      // 본문 글자
    const val LIB_SUB = 0xFF8C8478.toInt()     // 보조 글자
    const val LIB_CORNER = 0xFFE3DCCF.toInt()  // 사진 모서리 (검정 위)
    const val LIB_DOT = 0xFFD23A40.toInt()     // 알림 점
    const val VOY_BG = 0xFF111318.toInt()      // 검은 니트 (차가운 검정)
    const val VOY_NAVY = 0xFF1B2A4A.toInt()    // 라벨 · 집 상자
    const val VOY_NAVY2 = 0xFF2E4570.toInt()   // 줄무늬 가운데 (검정 니트 위에서 보이게)
    /** LIBRARY 글자색: 블랙이면 아이보리, 밝게면 먹 */
    fun li(a: Float = 1f) = tint(a, if (lib) LIB_TX else Style.INK)
    /** LIBRARY 바탕 */
    fun libBg(ctx: Context, tileDp: Float = 300f): Drawable =
        if (lib) android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable(LIB_BG), Pic(Style.img(ctx, "th/lib_black.webp"), Pic.COVER)))
        else android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable(0xFFF3EEE4.toInt()), Style.tile(ctx, Style.PAPER, tileDp)))
}

/** 직조 라벨: 평평한 아이보리 + 짧은 변 양쪽 박음질(점선). 자주 여는 폴더는 왼쪽이 남색 2dp 실선 */
class LabelBg(private val ctx: Context, private val fav: Boolean) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val b = bounds
        p.style = Paint.Style.FILL; p.pathEffect = null
        p.color = Color.argb(64, 0, 0, 0); c.drawRect(b.left.toFloat(), b.top + 1f, b.right.toFloat(), b.bottom.toFloat(), p)
        p.color = V_LABEL; c.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom - ctx.dpf(1f), p)
        val inset = ctx.dpf(6f); val y0 = b.top + ctx.dpf(5f); val y1 = b.bottom - ctx.dpf(6f)
        p.style = Paint.Style.STROKE
        listOf(b.left + inset, b.right - inset).forEachIndexed { i, x ->
            val ink = V_INK   // 라벨은 블랙에서도 아이보리 + 남색 (성훈님 선택)
            if (i == 0 && fav) { p.pathEffect = null; p.strokeWidth = ctx.dpf(2f); p.color = ink }
            else { p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(ctx.dpf(2f), ctx.dpf(2f)), 0f); p.strokeWidth = ctx.dpf(.5f); p.color = tint(.45f, ink) }
            c.drawLine(x, y0, x, y1, p)
        }
        p.pathEffect = null
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** 대표사진 칸: 고른 범위(확대 s, 중심 cx·cy)대로 칸을 채움 */
class TripPhoto(ctx: Context) : View(ctx) {
    var bmp: Bitmap? = null; set(v) { field = v; invalidate() }
    var s = 1f; var cx = .5f; var cy = .5f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    fun dest(w: Float, h: Float, b: Bitmap, s: Float, cx: Float, cy: Float): RectF {
        val k = maxOf(w / b.width, h / b.height) * s.coerceAtLeast(1f)
        val dw = b.width * k; val dh = b.height * k
        var l = w / 2 - cx * dw; var t = h / 2 - cy * dh
        l = l.coerceIn(w - dw, 0f); t = t.coerceIn(h - dh, 0f)
        return RectF(l, t, l + dw, t + dh)
    }
    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        c.save(); c.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        c.drawBitmap(b, null, dest(width.toFloat(), height.toFloat(), b, s, cx, cy), p)
        c.restore()
    }
}

object Trips {
    fun dir(ctx: Context) = File(ctx.filesDir, "voyage").apply { mkdirs() }
    /** 고른 사진을 앱 안에 긴 쪽 1600px로 복사 (갤러리에서 지워도 표지가 유지) — 실패하면 null */
    fun importPhoto(ctx: Context, uri: android.net.Uri): Pair<String, String>? = try {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
        var smp = 1; while (maxOf(opt.outWidth, opt.outHeight) / (smp * 2) >= 1600) smp *= 2
        var b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = smp }) }!!
        // 사진 방향 · 촬영일
        var date = ""
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            val ex = android.media.ExifInterface(ins)
            val deg = when (ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 }
            if (deg != 0) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, android.graphics.Matrix().apply { postRotate(deg.toFloat()) }, true)
            val dt = ex.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL) ?: ex.getAttribute(android.media.ExifInterface.TAG_DATETIME)
            if (dt != null && dt.length >= 10) { val y = dt.substring(0, 4); val mo = dt.substring(5, 7).toIntOrNull(); val d = dt.substring(8, 10).toIntOrNull()
                if (mo != null && d != null) date = "$y. $mo. $d" }
        }
        val big = maxOf(b.width, b.height)
        if (big > 1600) { val k = 1600f / big; b = Bitmap.createScaledBitmap(b, (b.width * k).roundToInt(), (b.height * k).roundToInt(), true) }
        val f = File(dir(ctx), "t" + System.currentTimeMillis() + ".jpg")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        f.absolutePath to date
    } catch (e: Exception) { null }

    private val cache = HashMap<String, Bitmap>()
    fun load(ctx: Context, path: String?): Bitmap = if (path == null) Style.img(ctx, "th/riviera_photo.webp") else
        cache.getOrPut(path) { try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null } ?: Style.img(ctx, "th/riviera_photo.webp") }
    fun forget(path: String) { cache.remove(path); try { File(path).delete() } catch (_: Exception) {} }
}

// ═════════ HOUR 세라믹 판 · 창 ═════════
fun plateDrawable(ctx: Context): Drawable = drawableOf { c, b ->
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = RectF(b); val rad = ctx.dpf(28f)
    p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, 0xFF171717.toInt(), 0xFF0F0F0F.toInt(), Shader.TileMode.CLAMP)
    c.drawRoundRect(r, rad, rad, p); p.shader = null
    p.style = Paint.Style.STROKE; p.strokeWidth = ctx.dpf(1f); p.color = Color.argb(15, 255, 255, 255)
    c.drawRoundRect(RectF(r.left + .5f, r.top + .5f, r.right - .5f, r.bottom - .5f), rad, rad, p)
}
/** 판에 뚫린 창: 검정 + 위쪽 안쪽 그늘 + 아래 가는 빛 */
fun windowDrawable(ctx: Context): Drawable = drawableOf { c, b ->
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = RectF(b); val rad = ctx.dpf(3f)
    p.color = 0xFF050505.toInt(); c.drawRoundRect(r, rad, rad, p)
    c.save(); c.clipRect(r)
    p.shader = LinearGradient(0f, r.top, 0f, r.top + ctx.dpf(5f), Color.argb(230, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
    c.drawRect(r.left, r.top, r.right, r.top + ctx.dpf(5f), p); p.shader = null
    p.color = Color.argb(13, 255, 255, 255); c.drawRect(r.left + rad, r.bottom - ctx.dpf(1f), r.right - rad, r.bottom, p)
    c.restore()
}

// ═════════ LIBRARY 원고지 ═════════
/** 폴더 이름을 한 칸에 한 글자씩 쓰는 원고지 (누르기·알림 점은 위에 얹은 칸이 맡음) */
class Manuscript(ctx: Context) : View(ctx) {
    var names: List<String> = emptyList()
    var favs: Set<Int> = emptySet()
    var vertical = true
    var rows = 15
    /** 계산된 칸 크기 · 왼쪽 위 */
    var u = 1f; var ox = 0f; var oy = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun measureFor(w: Int, h: Int) {
        // 블랙(시안 D): A 크기 그대로 + 종이 색 낮추기 + 테두리·그림자 더하기
        val extra = if (Dk.lib) 30f else 18f
        u = minOf(w / 396f, h / (rows * 44f + extra))
        ox = (w - 380 * u) / 2f; oy = (if (Dk.lib) 11 else 5) * u
    }
    /** 폴더 i의 줄·쪽(0 왼쪽 1 오른쪽) */
    fun slot(i: Int): Pair<Int, Int> = if (vertical) (i % rows) to (i / rows) else (i / 2) to (i % 2)
    fun cellLeft(side: Int) = ox + 2.5f * u + (if (side == 0) 0f else 200f * u)
    fun rowTop(r: Int) = oy + (4 + 44 * r) * u           // 위 띠 시작
    fun charTop(r: Int) = rowTop(r) + 19 * u              // 글자 칸 시작

    override fun onDraw(c: Canvas) {
        if (u <= 0f) return
        val a = if (false) .60f else .50f   // 먹지 위는 조금 진하게 (#C8604A 60%)
        val rr = if (false) 200 else 192; val gg = if (false) 96 else 83; val bb = if (false) 74 else 63
        val col = Color.argb((a * 255).roundToInt(), rr, gg, bb)
        val dimc = Color.argb(((if (false) .35f else a * .6f) * 255).roundToInt(), rr, gg, bb)
        val L = ox; val R = ox + 380 * u; val T = oy; val B = oy + (rows * 44 + 8) * u
        // 블랙 바탕: 먹지 위에 아이보리 원고지 종이 한 장 (그림자 · 가는 가장자리) — 샤넬팀 6:1
        if (Dk.lib) {
            val pr = RectF(L - 7 * u, T - 8 * u, R + 7 * u, B + 8 * u)
            p.style = Paint.Style.FILL; p.shader = null
            p.color = 0xFFF3EEE3.toInt(); p.setShadowLayer(26 * u, 0f, 10 * u, Color.argb(200, 0, 0, 0)); c.drawRect(pr, p); p.clearShadowLayer()
            p.shader = BitmapShader(Style.img(context, Style.PAPER), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                setLocalMatrix(android.graphics.Matrix().apply { val k = 300f * resources.displayMetrics.density / Style.img(context, Style.PAPER).width; setScale(k, k) }) }
            c.drawRect(pr, p); p.shader = null
            // 종이 색 낮추기: 먹 14%를 덮어 회색빛 아이보리 (눈부심 줄임)
            p.color = Color.argb(36, 30, 26, 20); c.drawRect(pr, p)
            // 테두리: 바깥 먹선 1.5dp + 안쪽 아이보리 하이라이트 (올려놓은 종이 한 장)
            val dd = resources.displayMetrics.density
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * dd; p.color = Color.argb(140, 0, 0, 0); c.drawRect(pr, p)
            p.strokeWidth = dd; p.color = Color.argb(70, 255, 250, 240); c.drawRect(pr.left + 2 * dd, pr.top + 2 * dd, pr.right - 2 * dd, pr.bottom - 2 * dd, p)
        }
        // 겹선 테두리
        p.style = Paint.Style.STROKE; p.color = Color.argb((a * 1.27f * 255).roundToInt().coerceAtMost(255), rr, gg, bb)
        p.strokeWidth = 1.5f * u; c.drawRect(L + .5f * u, T, R - .5f * u, B, p)
        p.strokeWidth = .5f * u; c.drawRect(L - 2 * u, T - 2.5f * u, R + 2 * u, B + 2.5f * u, p)
        // 가운데 접힌 자국
        p.style = Paint.Style.FILL
        val mid = (L + R) / 2
        p.shader = LinearGradient(mid - 6 * u, 0f, mid + 6 * u, 0f, intArrayOf(Color.TRANSPARENT, if (false) Color.argb(90, 0, 0, 0) else Color.argb(15, 94, 78, 58), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(mid - 6 * u, T, mid + 6 * u, B, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = .75f * u
        for (r in 0 until rows) {
            val y0 = charTop(r); val y1 = y0 + 25 * u
            val lens = IntArray(2) { side -> names.indices.firstOrNull { slot(it) == (r to side) }?.let { names[it].length.coerceAtMost(7) } ?: 0 }
            for (side in 0..1) {
                val x0 = cellLeft(side)
                p.color = col; c.drawLine(x0, y0, x0 + 175 * u, y0, p); c.drawLine(x0, y1, x0 + 175 * u, y1, p)
                for (k in 0..7) { p.color = if (k <= lens[side]) col else dimc; c.drawLine(x0 + 25 * k * u, y0, x0 + 25 * k * u, y1, p) }
            }
        }
        // 글자 · 권점
        p.style = Paint.Style.FILL
        names.forEachIndexed { i, nm ->
            val (r, side) = slot(i)
            if (r >= rows) return@forEachIndexed
            val x0 = cellLeft(side); val y0 = charTop(r)
            nm.take(7).forEachIndexed { j, ch ->
                val latin = ch.code < 128 && ch.isLetter()
                tp.typeface = if (latin) Fonts.serif(context) else Fonts.serifKR(context)
                tp.textSize = (if (latin) 17f else 16f) * u * resources.displayMetrics.density / resources.displayMetrics.density
                tp.color = if (false) Dk.LIB_TX else M_INK
                val cxx = x0 + (25 * j + 12.5f) * u
                c.drawText(ch.toString(), cxx, y0 + 12.5f * u - (tp.ascent() + tp.descent()) / 2, tp)
                if (i in favs) { p.color = if (false) Dk.LIB_TX else M_INK; c.drawCircle(cxx, y0 - 5 * u, 1.75f * u, p) }
            }
        }
    }
}

// ═════════ 미디어 볼륨 막대 ═════════
/** PHONE(0~150, 삼성 볼륨 창과 같은 숫자) / MARANTZ(0~50) 볼륨 — 막대 오른쪽 끝 = max. 옆으로 끌 때만 움직임 */
class VolBar(ctx: Context, private val style: Int, private val color: Int, private val dimColor: Int) : View(ctx) {
    companion object { const val TICKS = 0; const val LINE_TAB = 1; const val LINE_DOT = 2; const val RULER = 3; const val DOTS = 4 }
    /** 단계 수 (폰 150 · Marantz 50) — 막대 오른쪽 끝 = max */
    var max = 100; set(v) { field = v.coerceAtLeast(1); invalidate() }
    var value = 0; set(v) { field = v.coerceIn(0, max); invalidate() }
    var onChange: ((Int) -> Unit)? = null
    var onRelease: ((Int) -> Unit)? = null
    /** 막대 양끝 안쪽 여백(px) — 뷰를 막대보다 좌우 이만큼 넓게 놓아 손잡이가 잘리지 않게 */
    var padPx = 8 * ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tab = if (style == LINE_TAB) Style.img(ctx, "tab.webp") else null
    private val d = ctx.resources.displayMetrics.density
    private fun x0() = padPx
    private fun x1() = width - padPx
    private fun frac() = value / max.toFloat()
    private fun xAt(f: Float) = x0() + (x1() - x0()) * f
    /** 손잡이 자리 — DOTS는 켜진 마지막 점 */
    private fun knobX(): Float = if (style == DOTS) {
        val on = (frac() * 10f).roundToInt(); val step = (x1() - x0() - 6 * d) / 9f
        x0() + 3 * d + (on - 1).coerceAtLeast(0) * step
    } else xAt(frac())

    override fun onDraw(c: Canvas) {
        val y = height / 2f
        val f = frac()
        p.style = Paint.Style.FILL; p.alpha = 255
        when (style) {
            TICKS -> {
                p.strokeWidth = .75f * d
                for (i in 0..60) {
                    val t = i / 60f; val x = xAt(t); val L = (if (i % 5 == 0) 8 else 4) * d
                    p.color = if (t <= f + 1e-4f) color else dimColor
                    c.drawLine(x, y - L / 2, x, y + L / 2, p)
                }
                val mx = xAt(f)
                p.color = color
                c.drawPath(Path().apply { moveTo(mx - 5 * d, y - 14 * d); lineTo(mx + 5 * d, y - 14 * d); lineTo(mx, y - 7 * d); close() }, p)
            }
            LINE_TAB, LINE_DOT -> {
                p.color = dimColor; c.drawRect(x0(), y - .5f * d, x1(), y + .5f * d, p)
                p.color = color; c.drawRect(x0(), y - d, xAt(f), y + d, p)
                val mx = xAt(f)
                if (style == LINE_TAB) tab?.let { c.drawBitmap(it, null, RectF(mx - 14 * d, y - 6 * d, mx + 14 * d, y + 6 * d), p) }
                else { p.color = color; c.drawCircle(mx, y, 7 * d, p) }
            }
            RULER -> {
                p.color = color; c.drawRect(x0(), y - .5f * d, x1(), y + .5f * d, p)
                p.strokeWidth = .75f * d
                for (k in 0..10) { val x = xAt(k / 10f); c.drawLine(x, y, x, y + (if (k % 5 == 0) 8 else 4) * d, p) }
                c.drawCircle(xAt(f), y, 6 * d, p)
            }
            DOTS -> {
                val on = (f * 10f).roundToInt()
                val step = (x1() - x0() - 6 * d) / 9f
                for (k in 0 until 10) {
                    p.color = if (k < on) color else dimColor
                    c.drawCircle(x0() + 3 * d + k * step, y, 3 * d, p)
                }
            }
        }
    }

    // 톡 눌러서 뛰지 않음: 손잡이를 잡고 옆으로 끌 때만 값이 바뀜. 처음 8dp로 방향을 정하고, 위로(가로의 1.7배 넘게) 밀면 서랍에 넘김
    private var downX = 0f; private var downY = 0f; private var startV = 0; private var mode = 0   // 0 미정 · 1 볼륨 · 2 넘김
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 손잡이(지금 값 자리) 근처를 잡았을 때만 볼륨 — 나머지 자리에서 옆으로 밀면 화면 넘기기 (볼륨이 먹히던 것)
                if (kotlin.math.abs(e.x - knobX()) > 28 * d) return false
                downX = e.x; downY = e.y; startV = value; mode = 0; return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                if (mode == 0 && kotlin.math.hypot(dx, dy) > 8 * d) {
                    mode = if (-dy > kotlin.math.abs(dx) * 1.7f || kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.7f) 2 else 1
                    if (mode == 1) { parent?.requestDisallowInterceptTouchEvent(true); downX = e.x; startV = value }
                }
                if (mode == 1) {
                    val nv = (startV + (e.x - downX) / (x1() - x0()) * max).roundToInt().coerceIn(0, max)
                    if (nv != value) { value = nv; onChange?.invoke(nv) }
                    return true
                }
                return mode != 2
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (mode == 1) onRelease?.invoke(value); mode = 0; return true }
        }
        return true
    }
}
