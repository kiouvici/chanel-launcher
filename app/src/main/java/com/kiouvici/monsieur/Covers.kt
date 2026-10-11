package com.kiouvici.monsieur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** 표지 — MONSIEUR는 원래 표지(뚜껑·리본)를 그대로 쓰고, 나머지 네 테마는 이 층을 덮어 그림 */
@SuppressLint("ViewConstructor")
class ThemeCover(ctx: Context, private val look: Look) : FrameLayout(ctx) {

    private var theme = -1
    private var bw = 0; private var bh = 0
    var weatherLine = ""          // CHANEL 홈의 날씨 문장 (예: 25° · 흐림 · 19시부터 비)
    var weatherState = ""         // HA 날씨 상태 (cloudy, rainy …)
    var weatherTemp: Double? = null
    var sbTop = 0; var navBot = 0
    /** 사진을 눌러 넘기면 알려 줌 (CAPTION) */
    var onPhoto: (() -> Unit)? = null

    private var lDate: TextView? = null; private var lSent: TextView? = null; private var lTime: TextView? = null
    private var hDial: HourDial? = null; private var hWx: TextView? = null; private var hDate: TextView? = null
    private var vPhoto: TripPhoto? = null; private var vPlace: TextView? = null; private var vDate: TextView? = null
    private var vTime: TextView? = null; private var vWx: TextView? = null; private var vDots: android.widget.LinearLayout? = null
    /** VOYAGE 사진을 길게 누르면 (범위 맞추기) */
    var onTrip: (() -> Unit)? = null
    private var cPhoto: View? = null; private var cTime: TextView? = null; private var cWx: TextView? = null; private var cNum: TextView? = null

    val isLight get() = look.theme == Look.LIBRARY

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0) post { rebuild(true) }
    }

    fun rebuild(force: Boolean = false) {
        if (width == 0 || height == 0) return
        if (!force && theme == look.theme && bw == width && bh == height) return
        theme = look.theme; bw = width; bh = height
        removeAllViews(); background = null
        lDate = null; lSent = null; lTime = null; hDial = null; hWx = null; hDate = null
        vPhoto = null; vPlace = null; vDate = null; vTime = null; vWx = null; vDots = null
        cPhoto = null; cTime = null; cWx = null; cNum = null
        visibility = if (theme == Look.MONSIEUR) View.GONE else View.VISIBLE
        val L = Lay(context, this, width, height)
        when (theme) {
            Look.LIBRARY -> library(L)
            Look.HOUR -> hour(L)
            Look.TRAIN -> voyage(L)
            Look.CAPTION -> caption(L)
        }
        tick()
    }

    // ── ② 서재: 시간을 문장으로
    private fun library(L: Lay) {
        background = android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable(0xFFF3EEE4.toInt()), Style.tile(context, Style.PAPER, 300f)))
        lDate = L.t("", Style.jost(context, 400), 10f, Style.ink(.55f), .35f).also { L.add(it, 36f, 165f) }
        lSent = L.t("", Fonts.serif(context), 34f, Style.INK).apply { setLineSpacing(L.zf(10f), 1f) }.also { L.addLR(it, 36f, 36f, 262f) }
        lTime = L.t("", Style.jost(context, 300), 12f, Style.ink(.6f), .2f, Gravity.CENTER).also { L.addLR(it, 0f, 0f, 738f) }
        L.addLR(L.t("AIHERMES · MONSIEUR", Style.jost(context, 400), 9f, Style.ink(.5f), .4f, Gravity.CENTER), 0f, 0f, 814f)
        addView(EarView(context), LayoutParams(L.z(34f), L.z(34f), Gravity.BOTTOM or Gravity.END))
    }

    // ── ③ 시계: 실사 문자판(NOIR · IVOIRE) + 코드 바늘 (샤넬팀 최종)
    private fun hour(L: Lay) {
        setBackgroundColor(Color.BLACK)
        val d = HourDial(context, look.hourDial)
        L.add(d, 44f, 156f, 324f, 324f); hDial = d
        hDate = L.t("", Style.jost(context, 400), 11f, tint(.7f, H_IVORY), .4f, Gravity.CENTER).also { L.addLR(it, 0f, 0f, 516f) }
        L.add(View(context).apply { setBackgroundColor(tint(.4f, H_GOLD)) }, 146f, 548f, 120f, 1f)
        hWx = L.t("", Style.kr(context, 400), 15f, H_IVORY, 0f, Gravity.CENTER).also { L.addLR(it, 0f, 0f, 566f) }
        L.addLR(L.t("AIHERMES · MONSIEUR", Style.jost(context, 400), 10f, tint(.55f, H_IVORY), .4f, Gravity.CENTER), 0f, 0f, 794f)
    }

    // ── ④ 여행: 카드 액자 + 대표사진 (최대 6장, 누르면 다음 · 길게 누르면 범위 맞추기)
    private var spK = 1f
    private fun voyage(L: Lay) {
        spK = L.s
        background = jerseyDrawable(context, .3f)
        L.addLR(View(context).apply { background = stripesDrawable(context) }, 0f, 0f, 40f, 12f)
        L.addLR(View(context).apply { background = stripesDrawable(context) }, 0f, 0f, 863f, 12f)
        val card = View(context).apply { setBackgroundColor(V_IVORY); elevation = L.zf(16f) }
        L.add(card, 46f, 190f, 320f, 520f)
        val ph = TripPhoto(context).apply { elevation = L.zf(16.5f) }
        L.add(ph, 62f, 206f, 288f, 300f); vPhoto = ph
        fun on(v: TextView) = v.apply { elevation = L.zf(17f) }
        L.addLR(on(L.t("THE VOYAGE", Style.jost(context, 500), 11f, tint(.6f, V_INK), .45f, Gravity.CENTER)), 46f, 46f, 518f)
        vPlace = on(L.t("", Fonts.serifKR(context), 17f, V_INK, 0f, Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }).also { L.addLR(it, 76f, 76f, 536f) }
        vDate = on(L.t("", Fonts.serifI(context), 15f, 0xFF5B6378.toInt(), 0f, Gravity.CENTER)).also { L.addLR(it, 46f, 46f, 562f) }
        vTime = on(L.t("", Style.jost(context, 200), 64f, V_INK, .02f, Gravity.CENTER).apply { fontFeatureSettings = "tnum" }).also { L.addLR(it, 46f, 46f, 578f) }
        vWx = on(L.t("", Style.kr(context, 400), 13f, 0xFF4A5468.toInt(), 0f, Gravity.CENTER)).also { L.addLR(it, 46f, 46f, 664f) }
        vDots = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }.also { L.addLR(it, 0f, 0f, 733f, 2f) }
        L.addLR(L.t("AIHERMES · MONSIEUR", Style.jost(context, 400), 10f, tint(.6f, V_IVORY), .4f, Gravity.CENTER), 0f, 0f, 794f)
        ph.setOnClickListener { nextTrip() }
        ph.setOnLongClickListener { onTrip?.invoke(); true }
        loadTrip()
    }

    /** 지금 대표사진과 장소·날짜 (없으면 기본 리비에라 사진) */
    fun loadTrip() {
        val ph = vPhoto ?: return
        val list = look.trips
        val n = list.size
        val t = if (n > 0) list[look.tripIdx % n] else null
        ph.bmp = Trips.load(context, t?.file)
        ph.s = t?.s ?: 1f; ph.cx = t?.cx ?: .5f; ph.cy = t?.cy ?: .5f; ph.invalidate()
        val pl = t?.place ?: "Riviera"
        val kr = pl.any { it.code in 0xAC00..0xD7A3 }
        vPlace?.apply { text = pl; typeface = if (kr) Fonts.serifKR(context) else Fonts.serif(context); textSize = (if (kr) 17f else 19f) * spK }
        vDate?.text = if (t == null) "1924" else t.date
        vDots?.let { d ->
            d.removeAllViews()
            if (n > 1) for (i in 0 until n) d.addView(View(context).apply { setBackgroundColor(V_IVORY); alpha = if (i == look.tripIdx % n) 1f else .35f },
                android.widget.LinearLayout.LayoutParams(context.dp(12f), -1).apply { if (i > 0) leftMargin = context.dp(6f) })
        }
    }
    private fun nextTrip() {
        val n = look.trips.size
        if (n < 2) return
        look.tripIdx = (look.tripIdx + 1) % n
        vPhoto?.animate()?.alpha(0f)?.setDuration(140)?.withEndAction { loadTrip(); vPhoto?.animate()?.alpha(1f)?.setDuration(140)?.start() }?.start()
    }
    /** 화면을 켤 때마다 다음 엽서 */
    fun advanceTrip() { val n = look.trips.size; if (n > 1) { look.tripIdx = (look.tripIdx + 1) % n; loadTrip() } }

    // ── ⑤ 사진: 직접 고른 사진 + 설명 3줄
    private fun caption(L: Lay) {
        setBackgroundColor(Style.INK)
        val ph = View(context)
        addView(ph, LayoutParams(-1, -1)); cPhoto = ph
        loadPhoto()
        addView(View(context).apply { background = vgrad(tint(.25f, Style.INK), Color.TRANSPARENT) }, LayoutParams(-1, L.z(72f)))
        addView(View(context).apply { background = vgrad(Color.TRANSPARENT, tint(.72f, Style.INK)) }, LayoutParams(-1, (height * .42f).toInt(), Gravity.BOTTOM))
        val cap = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        cap.addView(View(context).apply { setBackgroundColor(Style.IVORY) }, LinearLayout.LayoutParams(L.z(24f), L.z(1f)).apply { bottomMargin = L.z(12f) })
        cap.addView(L.t("AIHERMES, MONSIEUR —", Style.jost(context, 400), 10f, Style.ivory(.85f), .3f))
        cTime = L.t("", Style.jost(context, 300), 26f, Style.IVORY).also { cap.addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = L.z(8f) }) }
        cWx = L.t("", Style.kr(context, 300), 13f, Style.ivory(.7f)).also { cap.addView(it, LinearLayout.LayoutParams(-2, -2).apply { topMargin = L.z(8f) }) }
        addView(cap, LayoutParams(-2, -2, Gravity.BOTTOM).apply { leftMargin = L.x(32f); bottomMargin = L.y(88f) + navBot })
        cNum = L.t("", Style.jost(context, 400), 10f, Style.ivory(.55f), .2f).also {
            addView(it, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { rightMargin = L.x(28f); bottomMargin = L.y(92f) + navBot })
        }
        ph.setOnClickListener { nextPhoto() }
        ph.setOnLongClickListener { onPhoto?.invoke(); true }
    }

    private var photoBmp: Bitmap? = null
    /** 고른 사진(최대 6장)을 차례로. 없으면 기본 사진(나선 계단) */
    fun loadPhoto() {
        val ph = cPhoto ?: return
        val list = look.photos
        val n = list.size
        cNum?.text = if (n > 1) "${look.photoIdx % n + 1} / $n" else ""
        val b: Bitmap? = if (n > 0) try {
            val uri = android.net.Uri.parse(list[look.photoIdx % n])
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
            var s = 1; while (opt.outWidth / (s * 2) >= width && opt.outHeight / (s * 2) >= height) s *= 2
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        } catch (e: Exception) { null } else null
        photoBmp = b
        ph.background = Pic(b ?: Style.img(context, "th/stair.webp"), Pic.COVER)
    }
    fun photoDrawable(): android.graphics.drawable.Drawable = Pic(photoBmp ?: Style.img(context, "th/stair.webp"), Pic.COVER)
    private fun nextPhoto() {
        val n = look.photos.size
        if (n < 2) return
        look.photoIdx = (look.photoIdx + 1) % n
        cPhoto?.animate()?.alpha(0f)?.setDuration(180)?.withEndAction { loadPhoto(); cPhoto?.animate()?.alpha(1f)?.setDuration(240)?.start() }?.start()
    }

    /** 매 분: 시각 · 날짜 · 날씨 다시 씀 */
    fun tick() {
        val cal = Calendar.getInstance()
        val hh = cal.get(Calendar.HOUR_OF_DAY); val mm = cal.get(Calendar.MINUTE)
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
        val dayE = arrayOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")[dow]
        val monE = arrayOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")[cal.get(Calendar.MONTH)]
        val day = cal.get(Calendar.DAY_OF_MONTH); val mon = cal.get(Calendar.MONTH) + 1
        val wx = weatherLine
        lDate?.text = "$dayE, $monE $day"
        lSent?.text = Words.sentence(hh, mm, weatherTemp, weatherState, wx)
        lTime?.text = "— %02d : %02d —".format(hh, mm)
        hDial?.set(hh, mm, day, isShown)
        val dayF = arrayOf("DIMANCHE", "LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI")[dow]
        val monF = arrayOf("JAN", "FÉV", "MARS", "AVR", "MAI", "JUIN", "JUIL", "AOÛT", "SEPT", "OCT", "NOV", "DÉC")[mon - 1]
        hDate?.text = "$dayF · $day $monF"
        hWx?.text = wx
        vTime?.text = "%02d:%02d".format(hh, mm)
        vWx?.text = wx
        cTime?.text = "%02d:%02d, %s, %d %s".format(hh, mm, dayE.lowercase().replaceFirstChar { it.uppercase() }, day, monE.lowercase().replaceFirstChar { it.uppercase() })
        cWx?.text = wx
    }
}

/** 시간·날씨를 영어 문장으로 (LIBRARY 표지) */
object Words {
    private val ones = arrayOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty")
    fun num(n: Int): String = when {
        n < 0 -> "minus " + num(-n)
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> tens[n / 10] + "-" + ones[n % 10]
    }
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    private fun h12(h: Int) = num(((h + 11) % 12) + 1)
    fun time(h: Int, m: Int): String = when {
        m == 0 -> "It is ${h12(h)} o'clock."
        m == 15 -> "It is quarter past ${h12(h)}."
        m == 30 -> "It is half past ${h12(h)}."
        m == 45 -> "It is quarter to ${h12(h + 1)}."
        m < 30 -> "It is ${num(m)} past ${h12(h)}."
        else -> "It is ${num(60 - m)} to ${h12(h + 1)}."
    }
    private val cond = mapOf("clear-night" to "a clear night", "cloudy" to "clouds", "fog" to "fog", "hail" to "hail", "lightning" to "thunder",
        "lightning-rainy" to "thunder and rain", "partlycloudy" to "a few clouds", "pouring" to "heavy rain", "rainy" to "rain",
        "snowy" to "snow", "snowy-rainy" to "sleet", "sunny" to "sunshine", "windy" to "wind", "windy-variant" to "wind and clouds")
    fun sentence(h: Int, m: Int, temp: Double?, state: String, line: String): String {
        val sb = StringBuilder(time(h, m))
        val parts = ArrayList<String>()
        if (temp != null) { val t = Math.round(temp).toInt(); parts += cap(num(t)) + if (kotlin.math.abs(t) == 1) " degree" else " degrees" }
        cond[state]?.let { parts += it }
        // 'n시부터 비' → 'rain from seven'
        Regex("(\\d{1,2})시부터\\s*(비|눈|소나기)").find(line)?.let { mt ->
            val w = when (mt.groupValues[2]) { "눈" -> "snow"; "소나기" -> "showers"; else -> "rain" }
            parts += "$w from ${h12(mt.groupValues[1].toInt())}"
        }
        if (parts.isNotEmpty()) {
            sb.append("\n").append(parts[0])
            if (parts.size > 1) sb.append(",\n").append(parts.drop(1).joinToString(", "))
            sb.append(".")
        }
        return sb.toString()
    }
}

/** HOUR 표지 문자판: 그림 + 레트로그레이드 눈금·분침 + 시 창 · 날짜 창 */
class DialFace(ctx: Context, private val k: Float) : View(ctx) {
    var hour = 0; var minute = 0; var date = ""
    private val img = Style.img(ctx, "th/dial.webp")
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val s = w / 340f
        c.drawBitmap(img, null, android.graphics.RectF(0f, 0f, w, w), p)
        val cx = w / 2; val cy = w / 2; val r = 132 * s
        p.color = 0xFFC9A86A.toInt(); p.strokeWidth = 1 * s; p.style = Paint.Style.STROKE
        tp.typeface = Style.jost(context, 300); tp.textSize = 9 * s; tp.color = tint(.85f, 0xFFC9A86A.toInt())
        for (m in 0..60 step 5) {
            val a = Math.toRadians(240.0 + m * 4)
            val len = if (m % 15 == 0) 9 * s else 5 * s
            c.drawLine(cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat(), cx + (r - len) * sin(a).toFloat(), cy - (r - len) * cos(a).toFloat(), p)
            if (m % 15 == 0) c.drawText("$m", cx + (r - 22 * s) * sin(a).toFloat(), cy - (r - 22 * s) * cos(a).toFloat() + 3 * s, tp)
        }
        // 분침 (레트로그레이드 240°)
        val a = Math.toRadians(240.0 + minute * 4)
        p.color = GOLD_LT; p.strokeWidth = 1.6f * s; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(cx, cy, cx + 120 * s * sin(a).toFloat(), cy - 120 * s * cos(a).toFloat(), p)
        p.style = Paint.Style.FILL; p.color = 0xFFC9A86A.toInt(); c.drawCircle(cx, cy, 5 * s, p)
        // 시 창 · 날짜 창
        fun win(l: Float, t: Float, ww: Float, hh: Float, text: String, size: Float, font: Int) {
            p.style = Paint.Style.FILL; p.color = 0xFF050505.toInt(); c.drawRect(l * s, t * s, (l + ww) * s, (t + hh) * s, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1 * s; p.color = tint(if (size > 20) .7f else .5f, 0xFFC9A86A.toInt())
            c.drawRect(l * s, t * s, (l + ww) * s, (t + hh) * s, p)
            tp.typeface = Style.jost(context, font); tp.textSize = size * s; tp.color = if (size > 20) Style.IVORY else Style.ivory(.85f)
            tp.letterSpacing = if (size > 20) 0f else .15f
            val fm = tp.fontMetrics
            c.drawText(text, (l + ww / 2) * s, (t + hh / 2) * s - (fm.ascent + fm.descent) / 2, tp)
            tp.letterSpacing = 0f
        }
        win(142f, 62f, 56f, 40f, "%02d".format(hour), 28f, 300)
        win(138f, 260f, 64f, 22f, date, 10f, 400)
    }
}

/** 승차권 시각 플랩 한 칸 (검정 판 + 가운데 이음 선) */
class FlapBg(private val px: Float) : android.graphics.drawable.Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val b = bounds
        p.color = 0xFF0D0D0D.toInt(); c.drawRect(b, p)
        p.color = Style.ivory(.18f); c.drawRect(b.left.toFloat(), b.exactCenterY() - px / 2, b.right.toFloat(), b.exactCenterY() + px / 2, p)
        p.color = tint(.6f, Color.BLACK); c.drawRect(b.left.toFloat(), b.exactCenterY() + px / 2, b.right.toFloat(), b.exactCenterY() + px * 1.5f, p)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** 점선 가로줄 */
class DashLine(private val color: Int, private val wPx: Float) : android.graphics.drawable.Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    override fun draw(c: Canvas) {
        p.color = color; p.strokeWidth = wPx; p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(wPx * 6, wPx * 6), 0f)
        c.drawLine(bounds.left.toFloat(), bounds.exactCenterY(), bounds.right.toFloat(), bounds.exactCenterY(), p)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
