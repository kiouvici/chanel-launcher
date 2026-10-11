package com.kiouvici.monsieur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** THE VOYAGE 범위 맞추기: 표지와 같은 크기의 사진 칸 안에서 두 손가락 확대 · 끌기 · 두 번 눌러 2배 */
@SuppressLint("ViewConstructor")
class CropView(ctx: Context) : View(ctx) {
    var bmp: Bitmap? = null; set(v) { field = v; invalidate() }
    var s = 1f; var cx = .5f; var cy = .5f
    var frame = RectF()
    var editable = true
    var onChange: (() -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val d = ctx.resources.displayMetrics.density

    private fun dest(): RectF? {
        val b = bmp ?: return null
        val w = frame.width(); val h = frame.height()
        val k = maxOf(w / b.width, h / b.height) * s
        val dw = b.width * k; val dh = b.height * k
        var l = w / 2 - cx * dw; var t = h / 2 - cy * dh
        l = l.coerceIn(w - dw, 0f); t = t.coerceIn(h - dh, 0f)
        // 칸을 넘지 않게 맞춘 뒤 중심을 다시 계산
        cx = (w / 2 - l) / dw; cy = (h / 2 - t) / dh
        return RectF(frame.left + l, frame.top + t, frame.left + l + dw, frame.top + t + dh)
    }

    override fun onDraw(c: Canvas) {
        val r = dest() ?: return
        c.drawBitmap(bmp!!, null, r, p)
        // 칸 밖은 어둡게
        p.color = Color.argb(140, 0, 0, 0)
        c.drawRect(0f, 0f, width.toFloat(), frame.top, p)
        c.drawRect(0f, frame.bottom, width.toFloat(), height.toFloat(), p)
        c.drawRect(0f, frame.top, frame.left, frame.bottom, p)
        c.drawRect(frame.right, frame.top, width.toFloat(), frame.bottom, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = V_LABEL
        c.drawRect(frame, p); p.style = Paint.Style.FILL
    }

    private val scaleD = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(det: ScaleGestureDetector): Boolean { s = (s * det.scaleFactor).coerceIn(1f, 4f); invalidate(); onChange?.invoke(); return true }
    })
    private val gest = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val b = bmp ?: return false
            val k = maxOf(frame.width() / b.width, frame.height() / b.height) * s
            cx += dx / (b.width * k); cy += dy / (b.height * k); invalidate(); onChange?.invoke(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { s = if (s < 1.5f) 2f else 1f; invalidate(); onChange?.invoke(); return true }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editable) return true
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleD.onTouchEvent(e); if (!scaleD.isInProgress) gest.onTouchEvent(e)
        return true
    }
}

/** 여행 사진 편집 화면 (설정 · 표지 사진 길게 누르기) */
@SuppressLint("SetTextI18n")
internal fun MainActivity.tripEditor() {
    hideSettingsSheet()
    tripLayer?.let { root.removeView(it) }
    val work = look.trips.toMutableList()
    val added = ArrayList<String>(); val removed = ArrayList<String>()
    var idx = if (work.isEmpty()) 0 else look.tripIdx % work.size
    val layer = FrameLayout(this).apply { setBackgroundColor(V_NAVY); elevation = dpf(50f); isClickable = true; setPadding(0, sbTop, 0, maxOf(navBot, 0)) }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    layer.addView(col, FrameLayout.LayoutParams(-1, -1))
    val L = Lay(this, layer, root.width, root.height)
    // 머리
    val head = FrameLayout(this)
    head.addView(txt("범위 맞추기", Style.kr(this, 500), 16f, V_LABEL), FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(16f) })
    val count = txt("", Fonts.mono(this), 12f, tint(.8f, V_LABEL), 0f, Gravity.END)
    head.addView(count, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(16f) })
    col.addView(head, LinearLayout.LayoutParams(-1, dp(48f)))
    // 사진 칸 (표지와 같은 크기)
    val fw = L.z(288f); val fh = L.z(300f)
    val cropH = fh + dp(48f)
    val crop = CropView(this)
    col.addView(crop, LinearLayout.LayoutParams(-1, cropH))
    crop.frame = RectF((root.width - fw) / 2f, dp(24f).toFloat(), (root.width + fw) / 2f, dp(24f) + fh.toFloat())
    // 아래 조작부 (키보드가 뜨면 스크롤)
    val lower = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), 0, dp(16f), dp(12f)) }
    val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(lower) }
    col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
    val hintRow = FrameLayout(this)
    val hint = txt("두 손가락으로 확대 · 끌어서 위치 조정", Style.kr(this, 300), 12f, tint(.6f, V_LABEL), 0f, Gravity.CENTER_VERTICAL)
    hintRow.addView(hint, FrameLayout.LayoutParams(-2, -1, Gravity.START))
    val reset = txt("처음 범위", Style.kr(this, 400), 12f, V_LABEL, 0f, Gravity.CENTER).apply { background = UnderLine(tint(.7f, V_LABEL), dpf(1f), dpf(8f)) }
    hintRow.addView(reset, FrameLayout.LayoutParams(dp(80f), -1, Gravity.END))
    lower.addView(hintRow, LinearLayout.LayoutParams(-1, dp(44f)))
    val thumbs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    lower.addView(thumbs, LinearLayout.LayoutParams(-1, dp(60f)).apply { topMargin = dp(4f) })
    val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    fun ob(t: String) = txt(t, Style.kr(this, 400), 12f, tint(.85f, V_LABEL), 0f, Gravity.CENTER).apply { background = shape(stroke = tint(.4f, V_LABEL), strokePx = hair(.75f)) }
    val bPrev = ob("‹ 앞으로"); val bNext = ob("뒤로 ›"); val bDel = ob("이 사진 빼기")
    listOf(bPrev, bNext, bDel).forEachIndexed { i, b -> btns.addView(b, LinearLayout.LayoutParams(0, dp(44f), 1f).apply { if (i > 0) leftMargin = dp(8f) }) }
    lower.addView(btns, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(10f) })
    fun field(label: String): EditText {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(V_LABEL); setPadding(dp(14f), 0, dp(14f), 0) }
        wrap.addView(txt(label, Style.kr(this, 400), 11f, 0xFF5B6378.toInt()), LinearLayout.LayoutParams(dp(44f), -2))
        val et = EditText(this).apply { typeface = Style.kr(this@tripEditor, 400); textSize = 14f; setTextColor(V_INK); setSingleLine(); background = null; setPadding(0, 0, 0, 0)
            filters = arrayOf<android.text.InputFilter>(android.text.InputFilter.LengthFilter(20)) }
        wrap.addView(et, LinearLayout.LayoutParams(0, -1, 1f))
        lower.addView(wrap, LinearLayout.LayoutParams(-1, dp(52f)).apply { topMargin = dp(8f) })
        return et
    }
    val place = field("장소"); val date = field("날짜")
    date.hint = "사진 날짜 (자동)"; date.setHintTextColor(tint(.45f, V_INK))
    place.hint = "예: 제주, 협재"; place.setHintTextColor(tint(.45f, V_INK))
    val back = txt("기본 사진으로 되돌리기", Style.kr(this, 400), 12f, tint(.7f, V_LABEL), 0f, Gravity.CENTER).apply { background = UnderLine(tint(.5f, V_LABEL), dpf(1f), dpf(80f)) }
    lower.addView(back, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(6f) })
    val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val cancel = txt("취소", Style.kr(this, 400), 14f, V_LABEL, 0f, Gravity.CENTER).apply { background = shape(stroke = V_LABEL, strokePx = dp(1f)) }
    val save = txt("저장", Style.kr(this, 500), 14f, V_INK, 0f, Gravity.CENTER).apply { setBackgroundColor(V_LABEL) }
    bar.addView(cancel, LinearLayout.LayoutParams(0, -1, 1f)); bar.addView(save, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(12f) })
    col.addView(bar, LinearLayout.LayoutParams(-1, dp(52f)).apply { leftMargin = dp(16f); rightMargin = dp(16f); bottomMargin = dp(12f) })

    var filling = false
    fun cur() = work.getOrNull(idx)
    fun store() { val t = cur() ?: return; work[idx] = t.copy(s = crop.s, cx = crop.cx, cy = crop.cy, place = place.text.toString().trim(), date = date.text.toString().trim()) }
    lateinit var refresh: () -> Unit
    refresh = {
        filling = true
        val t = cur()
        crop.bmp = Trips.load(this, t?.file); crop.s = t?.s ?: 1f; crop.cx = t?.cx ?: .5f; crop.cy = t?.cy ?: .5f; crop.editable = t != null; crop.invalidate()
        count.text = if (work.isEmpty()) "기본" else "${idx + 1} / ${work.size}"
        place.setText(t?.place ?: ""); date.setText(t?.date ?: "")
        place.isEnabled = t != null; date.isEnabled = t != null
        hint.text = if (t == null) "사진을 추가해 주세요 (최대 6장)" else "두 손가락으로 확대 · 끌어서 위치 조정"
        listOf(bPrev, bNext, bDel).forEach { it.alpha = if (t == null) .35f else 1f }
        thumbs.removeAllViews()
        work.forEachIndexed { i, tr ->
            val iv = ImageView(this).apply { setImageBitmap(Trips.load(this@tripEditor, tr.file)); scaleType = ImageView.ScaleType.CENTER_CROP
                foreground = if (i == idx) shape(stroke = V_LABEL, strokePx = dp(2f)) else null
                setOnClickListener { store(); idx = i; refresh() } }
            thumbs.addView(iv, LinearLayout.LayoutParams(dp(56f), dp(56f)).apply { if (i > 0) leftMargin = dp(8f) })
        }
        if (work.size < Look.MAX_PHOTOS) thumbs.addView(txt("+", Style.jost(this, 300), 22f, V_LABEL, 0f, Gravity.CENTER).apply {
            background = shape(stroke = tint(.5f, V_LABEL), strokePx = hair(1f)); setOnClickListener { store(); tripPicked = { list -> list.forEach { (f, dt) -> added += f; work += Look.Trip(f, "", dt, 1f, .5f, .5f) }; idx = maxOf(0, work.size - 1); refresh() }; pickTrips() }
        }, LinearLayout.LayoutParams(dp(56f), dp(56f)).apply { if (work.isNotEmpty()) leftMargin = dp(8f) })
        filling = false
    }
    crop.onChange = { }
    reset.setOnClickListener { crop.s = 1f; crop.cx = .5f; crop.cy = .5f; crop.invalidate() }
    bPrev.setOnClickListener { if (idx > 0) { store(); val t = work.removeAt(idx); work.add(idx - 1, t); idx--; refresh() } }
    bNext.setOnClickListener { if (idx < work.size - 1) { store(); val t = work.removeAt(idx); work.add(idx + 1, t); idx++; refresh() } }
    bDel.setOnClickListener { cur()?.let { removed += it.file; work.removeAt(idx); idx = idx.coerceAtMost(maxOf(0, work.size - 1)); refresh() } }
    var armed = false
    back.setOnClickListener {
        if (work.isEmpty()) return@setOnClickListener
        if (!armed) { armed = true; back.text = "한 번 더 누르면 기본 사진으로 되돌립니다"; back.postDelayed({ armed = false; back.text = "기본 사진으로 되돌리기" }, 3000); return@setOnClickListener }
        work.forEach { removed += it.file }; work.clear(); idx = 0; armed = false; back.text = "기본 사진으로 되돌리기"; refresh()
    }
    fun close() { hideKeyboardFor(place); root.removeView(layer); tripLayer = null }
    cancel.setOnClickListener { added.forEach { Trips.forget(it) }; close() }
    save.setOnClickListener {
        store()
        removed.filter { r -> work.none { it.file == r } }.forEach { Trips.forget(it) }
        look.trips = work; look.tripIdx = idx
        themeCover.loadTrip(); close()
    }
    root.addView(layer, FrameLayout.LayoutParams(-1, -1))
    tripLayer = layer
    refresh()
    if (work.isEmpty()) { tripPicked = { list -> list.forEach { (f, dt) -> added += f; work += Look.Trip(f, "", dt, 1f, .5f, .5f) }; idx = 0; refresh() }; pickTrips() }
}
