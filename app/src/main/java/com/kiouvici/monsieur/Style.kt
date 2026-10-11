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
        ctx.assets.open("img/$name").use { BitmapFactory.decodeStream(it) }
    }
}

fun Context.dp(v: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

fun Context.dpf(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
