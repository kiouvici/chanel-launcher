package com.kiouvici.monsieur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue

/** N°31 MONSIEUR 디자인 값 (샤넬팀 시안 B) */
object Style {
    const val INK = 0xFF0A0A0A.toInt()
    const val IVORY = 0xFFF6F3EE.toInt()
    const val GOLD = 0xFFB8914A.toInt()
    const val ROUGE = 0xFFB01E30.toInt()
    const val GILT = 0xFF8C6A2F.toInt()     // 짙은 금 (카드 번호)

    // 샤넬팀 B안 실사 그림 (없으면 예전 그림으로 대신)
    const val GROSGRAIN = "grosgrain.webp"   // 검정 실크 그로그랭 바닥
    const val PAPER = "paper.webp"           // 아이보리 코튼 종이
    private val fallback = mapOf(GROSGRAIN to "inner.webp", PAPER to "card.webp")

    fun ivory(alpha: Float) = Color.argb((alpha * 255).toInt(), 0xF6, 0xF3, 0xEE)
    fun ink(alpha: Float) = Color.argb((alpha * 255).toInt(), 0x0A, 0x0A, 0x0A)
    fun gold(alpha: Float) = Color.argb((alpha * 255).toInt(), 0xB8, 0x91, 0x4A)

    private val faces = HashMap<String, Typeface>()

    /** 가변 글꼴에서 두께를 골라 씀 (Jost: 숫자·라틴, NotoSansKR: 한글) */
    fun font(ctx: Context, name: String, weight: Int): Typeface {
        val key = "$name/$weight"
        return faces.getOrPut(key) {
            try {
                Typeface.Builder(ctx.assets, "font/$name.ttf")
                    .setFontVariationSettings("'wght' $weight")
                    .build()
            } catch (e: Exception) {
                Typeface.DEFAULT
            }
        }
    }

    fun jost(ctx: Context, w: Int = 300) = font(ctx, "Jost", w)
    fun kr(ctx: Context, w: Int = 300) = font(ctx, "NotoSansKR", w)

    private val bitmaps = HashMap<String, Bitmap>()
    fun img(ctx: Context, name: String): Bitmap = bitmaps.getOrPut(name) {
        try { ctx.assets.open("img/$name").use { BitmapFactory.decodeStream(it) } }
        catch (e: Exception) { ctx.assets.open("img/" + (fallback[name] ?: name)).use { BitmapFactory.decodeStream(it) } }
    }

    fun has(ctx: Context, name: String) = try { ctx.assets.open("img/$name").close(); true } catch (e: Exception) { false }

    /** 질감 그림을 sizeDp 크기로 줄여 바둑판처럼 깔기 (줄인 그림은 한 번만 만들어 재사용) */
    private val tiles = HashMap<String, Bitmap>()
    fun tile(ctx: Context, name: String, sizeDp: Float): android.graphics.drawable.BitmapDrawable {
        val px = ctx.dp(sizeDp)
        val b = tiles.getOrPut("$name@$px") {
            val src = img(ctx, name)
            val h = (px * src.height.toFloat() / src.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(src, px, h, true)
        }
        return android.graphics.drawable.BitmapDrawable(ctx.resources, b).apply {
            setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        }
    }

    /** 서랍 맨 위 리본 당김 탭: 실사 그림(tab.webp)이 있으면 그것, 없으면 코드로 그린 아이보리 그로그랭 */
    fun tabDrawable(ctx: Context): android.graphics.drawable.Drawable =
        if (has(ctx, "tab.webp")) android.graphics.drawable.BitmapDrawable(ctx.resources, img(ctx, "tab.webp"))
        else object : android.graphics.drawable.Drawable() {
            private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun draw(c: android.graphics.Canvas) {
                val b = bounds
                p.shader = android.graphics.LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
                    intArrayOf(0xFFEFE9DF.toInt(), IVORY, 0xFFE3DCCF.toInt()), floatArrayOf(0f, .4f, 1f), android.graphics.Shader.TileMode.CLAMP)
                c.drawRect(b, p); p.shader = null
                p.color = Color.argb(18, 0, 0, 0)
                val step = ctx.dpf(2.4f); var x = b.left.toFloat()
                while (x < b.right) { c.drawRect(x, b.top.toFloat(), x + ctx.dpf(.6f), b.bottom.toFloat(), p); x += step }
                p.color = Color.argb(26, 0, 0, 0)
                c.drawRect(b.left.toFloat(), b.top + b.height() * .38f, b.right.toFloat(), b.top + b.height() * .38f + ctx.dpf(.6f), p)
                p.color = Color.argb(20, 0, 0, 0)
                c.drawRect(b.left.toFloat(), b.bottom - ctx.dpf(3f), b.right.toFloat(), b.bottom.toFloat(), p)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
}

fun Context.dp(v: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

fun Context.dpf(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
