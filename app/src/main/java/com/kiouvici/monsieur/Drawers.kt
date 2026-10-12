package com.kiouvici.monsieur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*  서랍(폴더 30개) · 열린 폴더 — 테마 다섯 가지 (샤넬팀 4차 확정, 체크리스트 92~95점)
 *  ① MONSIEUR  뚜껑 띠 아래 편지 카드 5×6
 *  ② LIBRARY   호두나무 탁자 위 책 3더미 × 10권
 *  ③ HOUR      이름 원 이중 링 (바깥 01~18 · 안 19~30, 12시 금색 점에서 시계방향)
 *  ④ TRAIN     출발 안내판 2열 × 15줄 + 금박 액자
 *  ⑤ CAPTION   사진 위 문단 (구분점은 줄 맨 앞에 오지 않음)  */

private val BOOKS = listOf("th/book_ink.webp", "th/book_ivory.webp", "th/book_navy.webp", "th/book_tan.webp")

/** 설계 좌표 → 서랍(화면 전체) 픽셀 */
internal fun MainActivity.boxLay(host: FrameLayout) = Lay(this, host, root.width.coerceAtLeast(1), root.height.coerceAtLeast(1))

/** 테마에 맞춰 서랍 바탕·장식·머리글·여백을 다시 꾸밈 */
internal fun MainActivity.themeBox() {
    if (root.width == 0) return
    val t = look.theme
    deco.removeAllViews(); hdr.removeAllViews()
    val L = boxLay(deco)
    val isM = t == Look.MONSIEUR
    listOf(rimShade, rim, tab).forEach { it.visibility = if (isM) View.VISIBLE else View.GONE }
    var top: Int; var hdrH: Int; var side = dp(16f)
    lidNow = null
    when (t) {
        Look.LIBRARY -> {
            // 원고지: 아이보리 종이 (책·나무 버림 — 성훈님 선택)
            box.background = Dk.libBg(this)
            top = L.y(36f); hdrH = L.y(112f) - top; side = L.x(12f)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(txt("THE LIBRARY · LE MANUSCRIT", Style.jost(this, 500), 11f, if (Dk.lib) Dk.LIB_SUB else 0xFF8A7A66.toInt(), .22f))
            val sn = txt("", Fonts.serifI(this), 24f, if (Dk.lib) Dk.LIB_TX else 0xFF3B3128.toInt()).apply { maxLines = 1 }; libSentence = sn
            col.addView(sn, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6f) })
            hdr.addView(col, FrameLayout.LayoutParams(-1, -2).apply { leftMargin = L.x(12f); topMargin = L.y(8f) })
        }
        Look.HOUR -> {
            // H3: 검정 세라믹 판에 뚫린 창 30개
            box.background = android.graphics.drawable.ColorDrawable(0xFF0E0E0E.toInt())
            top = L.y(50f); hdrH = L.y(132f) - top; side = L.x(12f)
            val hd = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            val tm = txt("", Style.jost(this, 300), 22f, H_IVORY, .12f, Gravity.CENTER).apply { background = shape(stroke = H_GOLD, strokePx = hair(.75f)); fontFeatureSettings = "tnum" }
            hourTime = tm
            hd.addView(tm, LinearLayout.LayoutParams(dp(120f), dp(40f)))
            hourWx = txt("", Style.kr(this, 300), 13f, tint(.7f, H_IVORY), 0f, Gravity.CENTER).also { hd.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) }) }
            hdr.addView(hd, FrameLayout.LayoutParams(-1, -1))
        }
        Look.TRAIN -> {
            // VOYAGE: 남색 니트 + 마리니에르 띠 + 직조 라벨
            box.background = jerseyDrawable(this, .4f)
            deco.addView(View(this).apply { background = stripesDrawable(this@themeBox) }, FrameLayout.LayoutParams(-1, L.z(12f)).apply { topMargin = L.y(40f) })
            top = L.y(60f); hdrH = L.y(96f) - top; side = L.x(22f)
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(txt("LE VESTIAIRE", Style.jost(this, 400), 12f, V_IVORY, .4f).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            val tc = txt("", Style.jost(this, 300), 13f, V_IVORY, .05f, Gravity.END).apply { maxLines = 1 }; trainClock = tc
            r.addView(tc, LinearLayout.LayoutParams(-2, -2))
            hdr.addView(r, FrameLayout.LayoutParams(-1, -2).apply { leftMargin = L.x(6f); rightMargin = L.x(6f) })
        }
        Look.CAPTION -> {
            box.background = android.graphics.drawable.ColorDrawable(Style.INK)
            deco.addView(View(this).apply { background = themeCover.photoDrawable() }, FrameLayout.LayoutParams(-1, -1))
            deco.addView(View(this).apply {
                background = vgrad(Color.argb(64, 10, 10, 10), Color.argb(153, 10, 10, 10), Color.argb(230, 10, 10, 10), Color.argb(240, 10, 10, 10))
            }, FrameLayout.LayoutParams(-1, -1))
            top = L.y(272f); hdrH = L.y(300f) - top; side = L.x(24f)
            hdr.addView(txt("THE CAPTION", Style.jost(this, 400), 10f, Style.ivory(.75f), .4f), FrameLayout.LayoutParams(-2, -2))
        }
        else -> {
            box.background = android.graphics.drawable.LayerDrawable(arrayOf(
                android.graphics.drawable.ColorDrawable(Style.INK), Style.tile(this, Style.GROSGRAIN, 256f)))
            // 뚜껑 띠 (위 150) — 지금 시각 · 날씨
            val lidH = L.y(150f)
            deco.addView(View(this).apply { background = Pic(Style.img(this@themeBox, "lid.webp"), Pic.COVER); alpha = .55f },
                FrameLayout.LayoutParams(-1, lidH))
            val ln = txt("", Style.jost(this, 300), 13f, Style.ivory(.75f), .2f, Gravity.CENTER); lidNow = ln
            deco.addView(ln, FrameLayout.LayoutParams(-1, -2).apply { topMargin = L.y(92f) })
            (rimShade.layoutParams as FrameLayout.LayoutParams).topMargin = lidH + dp(29f)
            (rim.layoutParams as FrameLayout.LayoutParams).topMargin = lidH
            (tab.layoutParams as FrameLayout.LayoutParams).topMargin = lidH + dp(18f)
            (grip.layoutParams as FrameLayout.LayoutParams).topMargin = lidH
            top = lidH + dp(29f) + dp(6f); hdrH = dp(40f)
            val hd = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            hd.addView(txt("THE BOX", Style.jost(this, 400), 11f, Style.ivory(.9f), .45f, Gravity.CENTER))
            hd.addView(View(this).apply { setBackgroundColor(GOLD_LT) }, LinearLayout.LayoutParams(dp(24f), dp(1f)).apply { topMargin = dp(8f) })
            hdr.addView(hd, FrameLayout.LayoutParams(-1, -1))
        }
    }
    if (!isM) (grip.layoutParams as FrameLayout.LayoutParams).topMargin = 0
    (col.layoutParams as FrameLayout.LayoutParams).apply { topMargin = top; leftMargin = side; rightMargin = side; bottomMargin = 0 }
    (hdr.layoutParams as LinearLayout.LayoutParams).height = hdrH
    // 고리 원은 화면 끝까지 쓰도록 여백을 되돌림
    (stage.layoutParams as LinearLayout.LayoutParams).apply { leftMargin = if (t == Look.HOUR) -side else 0; rightMargin = leftMargin }
    folioBase = when (t) {
        Look.LIBRARY -> "31 · RUE CAMBON · LE MATIN"; Look.HOUR -> "GUICHETS · %s"
        Look.TRAIN -> "VOYAGE · %s"; Look.CAPTION -> "PHOTOGRAPHIE · %s"; else -> "31 · RUE CAMBON · %s"
    }
    val lightBox = t == Look.LIBRARY && !Dk.lib
    searchLine.setBackgroundColor(when (t) { Look.LIBRARY -> if (Dk.lib) Color.argb(110, 200, 96, 74) else tint(.25f, M_INK); Look.TRAIN -> tint(.5f, V_IVORY); Look.HOUR -> tint(.4f, H_GOLD); else -> Style.gold(.4f) })
    search.setHintTextColor(if (lightBox) 0xFF8E8579.toInt() else Style.ivory(.6f)); search.setTextColor(if (lightBox) M_INK else Style.IVORY)
    search.hint = if (t == Look.LIBRARY) "찾기 · SEARCH" else "SEARCH · 찾기"
    val footC = if (lightBox) tint(.75f, 0xFF8A7E6E.toInt()) else Style.ivory(.65f)
    folio.setTextColor(footC); settingsBtn.setTextColor(footC)
    // 하단 글자 12sp (MONSIEUR는 그대로 9sp)
    folio.textSize = 12f; settingsBtn.textSize = 12f
    searchGlyphColor(if (lightBox) 0xFF5E4522.toInt() else if (t == Look.LIBRARY) 0xFFC8604A.toInt() else GOLD_LT)
    col.requestLayout(); box.requestLayout()
    updateThemeClock()
}

/** 서랍 머리의 시각·날씨 (매 분) */
internal fun MainActivity.updateThemeClock() {
    val cal = java.util.Calendar.getInstance()
    val hm = "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    val wx = themeCover.weatherLine
    val short = wx.split("·").take(2).joinToString(" ") { it.trim() }
    lidNow?.text = if (short.isNotEmpty()) "$hm · $short" else hm
    trainClock?.text = hm
    hourWx?.text = wx
    hourTime?.text = hm.replace(":", " : ")
}

// ═════════ 폴더 30개 깔기 ═════════
internal fun MainActivity.buildStage() {
    val W = stage.width.takeIf { it > 0 } ?: return
    val H = stage.height.takeIf { it > 0 } ?: return
    stageIn.removeAllViews(); gridDots.clear(); hourCenter = null; stageIn.background = null
    val folders = repo.folders()
    val favs = repo.favorites()
    val badges = BadgeService.active
    val q = search.text?.toString()?.trim() ?: ""
    fun dimIfNot(v: View, id: String) {
        if (q.isNotEmpty() && !repo.nameOf(id).contains(q) && repo.appsIn(id).none { it.label.contains(q, true) }) v.alpha = .25f
    }
    var contentH = H
    val k = look.folderScale
    when (look.theme) {
        Look.LIBRARY -> {
            // 원고지: 한 칸에 한 글자, 세로(왼쪽 1–15) 또는 가로 순서 (설정)
            val names = folders.map { shortName(repo.nameOf(it)) }
            val ms = Manuscript(this).apply {
                this.names = names; this.favs = folders.indices.filter { folders[it] in favs }.toSet()
                vertical = look.libOrder == 0; rows = maxOf(15, (folders.size + 1) / 2)
            }
            ms.measureFor(W, H)
            val needH = ((ms.rows * 44f + 18f) * ms.u).roundToInt()
            stageIn.addView(ms, FrameLayout.LayoutParams(W, maxOf(H, needH)))
            var waiting = 0
            folders.forEachIndexed { i, id ->
                val (r, side) = ms.slot(i)
                val hit = View(this).apply {
                    background = android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_pressed), android.graphics.drawable.ColorDrawable(Color.argb(26, 192, 83, 63)))
                        addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                    }
                }
                stageIn.addView(hit, FrameLayout.LayoutParams((190f * ms.u).roundToInt(), (44f * ms.u).roundToInt()).apply {
                    leftMargin = (ms.ox + side * 190f * ms.u).roundToInt(); topMargin = ms.rowTop(r).roundToInt()
                })
                bindFolder(hit, id) { on -> hit.foreground = if (on) android.graphics.drawable.ColorDrawable(Color.argb(40, 192, 83, 63)) else null }
                val badge = repo.appsIn(id).any { it.pkg in badges }
                if (badge) waiting++
                val len = names[i].take(7).length
                val cx = if (len < 7) ms.cellLeft(side) + (25 * len + 12.5f) * ms.u else if (side == 0) ms.ox + 190f * ms.u else ms.ox + 383f * ms.u
                val sz = (10f * ms.u).roundToInt()
                val d = View(this).apply { background = shape(if (Dk.lib) Dk.LIB_DOT else M_RED, oval = true); visibility = if (badge) View.VISIBLE else View.GONE }
                stageIn.addView(d, FrameLayout.LayoutParams(sz, sz).apply { leftMargin = (cx - sz / 2f).roundToInt(); topMargin = (ms.charTop(r) + 12.5f * ms.u - sz / 2f).roundToInt() })
                gridDots[id] = d
            }
            fun cap(t: String) = t.replaceFirstChar { it.uppercase() }
            libSentence?.text = when (waiting) {
                0 -> "${cap(Words.num(folders.size))} lines, written this morning."
                1 -> "One of them is waiting."
                else -> "${cap(Words.num(waiting))} of them are waiting."
            }
            contentH = maxOf(H, needH)
        }
        Look.HOUR -> {
            // H3: 세라믹 판 + 창 3열 (이름 13sp, 6자 이상 12sp)
            val pad = dp(10f); val gap = dp(6f); val rgap = dp(8f)
            val rows = (folders.size + 2) / 3
            val cw = (W - pad * 2 - gap * 2) / 3
            val rh = ((H - pad * 2 - rgap * (rows - 1).coerceAtLeast(0)) / rows.coerceAtLeast(1).toFloat()).coerceAtMost(dpf(54f) * k).roundToInt().coerceAtLeast(dp(44f))
            stageIn.background = plateDrawable(this)
            folders.forEachIndexed { i, id ->
                val v = windowView(id, shortName(repo.nameOf(id)), id in favs, repo.appsIn(id).any { it.pkg in badges })
                dimIfNot(v, id)
                stageIn.addView(v, FrameLayout.LayoutParams(cw, rh).apply { leftMargin = pad + (i % 3) * (cw + gap); topMargin = pad + (i / 3) * (rh + rgap) })
            }
            contentH = maxOf(H, pad * 2 + rows * rh + (rows - 1).coerceAtLeast(0) * rgap)
        }
        Look.TRAIN -> {
            // VOYAGE: 직조 라벨 2열 × 15줄
            val rows = maxOf(15, (folders.size + 1) / 2)
            val rh = ((H / rows.toFloat()).coerceAtMost(dpf(44f) * k)).roundToInt().coerceAtLeast(dp(36f))
            val cw = (W - dp(12f)) / 2
            folders.forEachIndexed { i, id ->
                val c = i / rows; val r = i % rows
                val v = labelView(id, shortName(repo.nameOf(id)), id in favs, repo.appsIn(id).any { it.pkg in badges })
                dimIfNot(v, id)
                stageIn.addView(v, FrameLayout.LayoutParams(cw, rh - dp(4f)).apply { leftMargin = c * (cw + dp(12f)); topMargin = r * rh })
            }
            contentH = maxOf(H, rows * rh)
        }
        Look.CAPTION -> {
            val flow = FlowWords(this, dpf(44f) * k, dpf(16f), Style.ivory(.35f), dpf(17f))
            folders.forEachIndexed { i, id ->
                val v = wordView(i, id, shortName(repo.nameOf(id)), id in favs, repo.appsIn(id).any { it.pkg in badges })
                dimIfNot(v, id)
                flow.addView(v)
            }
            stageIn.addView(flow, FrameLayout.LayoutParams(-1, -2))
            flow.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            contentH = maxOf(H, flow.measuredHeight)
        }
        else -> {
            // 편지 카드 5열 — 30개가 한 화면 (예전 최소 84dp 때문에 6번째 줄이 잘리던 문제)
            val gap = dp(10f)
            val rows = (folders.size + 4) / 5
            val colW = ((W - gap * 4) / 5f * minOf(1f, k)).roundToInt()
            val rowGap = dp(8f)
            val fitH = ((H - dp(8f) - rowGap * (rows - 1).coerceAtLeast(0)) / rows.coerceAtLeast(1).toFloat())
            // 크게(1.06)여도 30개가 한 화면을 넘지 않게 — 기본이 이미 가득이면 같음
            val cardH = (minOf(fitH, colW * 1.25f * maxOf(1f, k)) * (if (k < 1f) k else 1f)).roundToInt().coerceAtLeast(dp(56f))
            val left0 = (W - (colW * 5 + gap * 4)) / 2
            folders.forEachIndexed { i, id ->
                val inF = repo.appsIn(id)
                val v = folderCell(i, id, inF, colW, cardH, id in favs, inF.any { it.pkg in badges })
                dimIfNot(v, id)
                stageIn.addView(v, FrameLayout.LayoutParams(colW, cardH).apply { leftMargin = left0 + (i % 5) * (colW + gap); topMargin = dp(4f) + (i / 5) * (cardH + rowGap) })
            }
            contentH = maxOf(H, dp(12f) + rows * (cardH + rowGap))
        }
    }
    (stageIn.layoutParams as FrameLayout.LayoutParams).height = contentH
    stageIn.requestLayout()
    val n = folders.size
    val un = repo.unassigned().size
    folio.text = if (un > 0) "$n FOLDERS · UNSORTED $un" else folioBase.format(if (n == 1) "1 FOLDER" else "$n FOLDERS")
}

/** 폴더 보기 하나에 공통 동작: 누르면 열기 · 길게 누르면 메뉴 · 앱을 끌어 놓으면 옮기기 */
internal fun MainActivity.bindFolder(v: View, id: String, onHover: ((Boolean) -> Unit)? = null) {
    v.setOnClickListener { showFolder(id) }
    v.setOnLongClickListener { folderMenu(v, id); true }
    v.setOnDragListener { _, e ->
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> true
            DragEvent.ACTION_DRAG_ENTERED -> { onHover?.invoke(true) ?: v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start(); true }
            DragEvent.ACTION_DRAG_EXITED -> { onHover?.invoke(false) ?: v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); true }
            DragEvent.ACTION_DROP -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                val parts = e.clipData?.getItemAt(0)?.text?.toString()?.split("\n")
                val pkg = parts?.getOrNull(0)
                val src = parts?.getOrNull(1)?.takeIf { it.isNotEmpty() }
                if (pkg != null) { repo.move(pkg, src, id); stage.post { refreshGrid() } }
                true
            }
            else -> true
        }
    }
    v.contentDescription = "${repo.nameOf(id)} 폴더, 앱 ${repo.appsIn(id).size}개"
}

private fun MainActivity.nameSp(base: Float) = base + look.nameDelta

/** ③ HOUR 창 하나: 아이콘 22 + 이름 (자주 여는 폴더는 베이지 골드) */
private fun MainActivity.windowView(id: String, nm: String, fav: Boolean, badge: Boolean): View {
    val v = FrameLayout(this).apply { background = windowDrawable(this@windowView) }
    repo.coverApps(id).firstOrNull()?.let { a ->
        v.addView(ImageView(this).apply { setImageDrawable(iconOf(a)); styleIcon(this) }, FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER_VERTICAL).apply { leftMargin = dp(9f) })
    }
    val tv = txt(nm, Style.kr(this, 400), nameSp(if (nm.length < 6) 13f else 12f), look.nameColorOn(false, if (fav) H_GOLD else tint(.92f, H_IVORY)), 0f, Gravity.CENTER_VERTICAL).apply { maxLines = 1 }
    v.addView(tv, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = dp(38f); rightMargin = dp(6f) })
    val d = View(this).apply { background = shape(Style.ROUGE, oval = true); visibility = if (badge) View.VISIBLE else View.GONE }
    v.addView(d, FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.END or Gravity.TOP).apply { topMargin = dp(6f); rightMargin = dp(6f) })
    gridDots[id] = d
    bindFolder(v, id) { on -> tv.setTextColor(if (on) H_GOLD else look.nameColorOn(false, if (fav) H_GOLD else tint(.92f, H_IVORY))) }
    return v
}

/** ④ VOYAGE 직조 라벨 하나: 아이콘 18 + 이름 14sp 남색 (자주 여는 폴더는 굵게 + 왼쪽 실선) */
private fun MainActivity.labelView(id: String, nm: String, fav: Boolean, badge: Boolean): View {
    val v = FrameLayout(this).apply { background = LabelBg(this@labelView, fav) }
    repo.coverApps(id).firstOrNull()?.let { a ->
        v.addView(ImageView(this).apply { setImageDrawable(iconOf(a)); styleIcon(this) }, FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER_VERTICAL).apply { leftMargin = dp(16f) })
    }
    val tv = txt(nm, Style.kr(this, if (fav) 600 else 500), nameSp(14f), look.nameColorOn(!Dk.voy, if (Dk.voy) Dk.LIB_TX else V_INK), 0f, Gravity.CENTER_VERTICAL).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    v.addView(tv, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = dp(40f); rightMargin = dp(22f) })
    val d = View(this).apply { background = shape(Style.ROUGE, oval = true); visibility = if (badge) View.VISIBLE else View.GONE }
    v.addView(d, FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(14f) })
    gridDots[id] = d
    bindFolder(v, id) { on -> v.alpha = if (on) .8f else 1f }
    return v
}

/** ② 책 한 권 */
private fun MainActivity.bookView(i: Int, stack: Int, id: String, nm: String, fav: Boolean, badge: Boolean, colW: Int, h: Int): View {
    val bi = (i * 3 + stack) % 4
    val dark = bi == 0 || bi == 2
    val tv = txt(nm, Style.kr(this, if (dark) 400 else 600), nameSp(13f),
        look.nameColorOn(!dark, if (dark) Style.ivory(.95f) else if (bi == 3) 0xFF1A0F08.toInt() else Style.ink(.88f)), -.01f, Gravity.CENTER).apply { maxLines = 1 }
    var fs = nameSp(13f)
    val maxW = colW - dp(4f)
    var w = (tv.paint.measureText(nm) + dp(46f)).roundToInt()
    if (w > maxW) { fs = nameSp(12f); tv.textSize = fs; w = (tv.paint.measureText(nm) + dp(46f)).roundToInt() }
    val jitter = intArrayOf(0, 4, -2, 6, 2, -4, 3)[i % 7]
    w = (w.coerceIn(dp(108f).coerceAtMost(maxW), maxW) + dp(jitter.toFloat())).coerceAtMost(maxW)
    val v = FrameLayout(this).apply {
        background = Pic(Style.img(this@bookView, BOOKS[bi]), Pic.FIT)
        rotation = floatArrayOf(-1.2f, .8f, -.4f, 1.5f, -1.5f, .3f, 1.1f, -.8f, .5f, -1f)[(i * 3) % 10]
        elevation = dpf(3f)
        tag = w
    }
    v.addView(tv, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = dp(if (badge) 24f else 16f); rightMargin = dp(24f) })
    tv.gravity = Gravity.CENTER
    v.addView(txt("%02d".format(i + 1), Style.jost(this, 400), 9f, if (dark) GOLD_LT else if (bi == 3) 0xFF3A2412.toInt() else 0xFF7A5A22.toInt(), 0f),
        FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(9f) })
    if (fav) v.addView(View(this).apply { setBackgroundColor(GOLD_LT) }, FrameLayout.LayoutParams(-1, hair(1f), Gravity.BOTTOM).apply { leftMargin = dp(24f); rightMargin = dp(24f); bottomMargin = dp(7f) })
    val d = View(this).apply { background = shape(Style.ROUGE, oval = true); visibility = if (badge) View.VISIBLE else View.GONE }
    v.addView(d, FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(10f) })
    gridDots[id] = d
    bindFolder(v, id)
    return v
}

/** ③ 이름 원 하나 (검정 세라믹 + 금테 그림) */
private fun MainActivity.capsuleView(i: Int, id: String, nm: String, fav: Boolean, badge: Boolean, size: Int): View {
    val v = FrameLayout(this).apply {
        background = Pic(Style.img(this@capsuleView, "th/index_capsule.webp"), Pic.FIT)
        elevation = dpf(3f)
        if (fav) foreground = shape(stroke = GOLD_LT, strokePx = dp(1.5f), oval = true)
    }
    val two = nm.length > 3 && (nm.contains('&') || nm.contains('·'))
    val label = if (two) nm.replace("&", "&\n").replace("·", "·\n") else nm
    val tv = txt(label, Style.kr(this, 500), nameSp(if (size < dp(50f)) 10f else 10.5f), look.nameColorOn(false, if (fav) GOLD_LT else Style.ivory(.92f)), -.05f, Gravity.CENTER).apply {
        setShadowLayer(dpf(2f), 0f, dpf(1f), Color.argb(230, 0, 0, 0)); setLineSpacing(0f, .95f)
    }
    v.addView(tv, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = dp(2f); rightMargin = dp(2f) })
    tv.gravity = Gravity.CENTER
    val d = View(this).apply { background = shape(Style.ROUGE, oval = true); visibility = if (badge) View.VISIBLE else View.GONE }
    v.addView(d, FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.END or Gravity.TOP).apply { topMargin = dp(2f); rightMargin = dp(2f) })
    gridDots[id] = d
    bindFolder(v, id) { on -> v.animate().scaleX(if (on) 1.18f else 1f).scaleY(if (on) 1.18f else 1f).setDuration(120).start() }
    // 누르는 동안 가운데에 미리보기
    v.setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_DOWN) showHourPreview(id); false }
    return v
}

/** ③ 가운데 원: 번호 · 이름 · 앱 4개 · 안내 */
private fun MainActivity.hourCenterView(size: Int): FrameLayout {
    val c = FrameLayout(this).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT; colors = intArrayOf(0xFF141414.toInt(), 0xFF0B0B0B.toInt()); gradientRadius = size / 2f
            setStroke(dp(1f), tint(.35f, 0xFFC9A86A.toInt()))
        }
    }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    col.addView(txt("", Style.jost(this, 300), 10f, GOLD_LT, .3f, Gravity.CENTER).apply { tag = "no" })
    val g = GridLayout(this).apply { columnCount = 2; tag = "g" }
    col.addView(g, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6f) })
    col.addView(txt("누르면 열림 · 길게 메뉴", Style.kr(this, 300), 10f, Style.ivory(.6f), 0f, Gravity.CENTER), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6f) })
    c.addView(col, FrameLayout.LayoutParams(-1, -1))
    return c
}

internal fun MainActivity.showHourPreview(id: String?) {
    val c = hourCenter ?: return
    id ?: return
    val no = c.findViewWithTag<TextView>("no"); val g = c.findViewWithTag<GridLayout>("g")
    no.text = "%02d · %s".format(repo.folders().indexOf(id) + 1, shortName(repo.nameOf(id)))
    g.removeAllViews()
    repo.coverApps(id).take(4).forEachIndexed { k, a ->
        g.addView(ImageView(this).apply { setImageDrawable(iconOf(a)); styleIcon(this) }, GridLayout.LayoutParams().apply {
            width = dp(24f); height = dp(24f); if (k % 2 == 1) leftMargin = dp(6f); if (k >= 2) topMargin = dp(6f)
        })
    }
}

/** ④ 출발 안내판 한 줄: 번호 판 · 이름 플랩 · 앱 수 */
private fun MainActivity.boardRow(i: Int, id: String, nm: String, fav: Boolean, badge: Boolean, count: Int, h: Int): View {
    val r = FrameLayout(this)
    val inner = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val ph = h - dp(4f)
    inner.addView(txt("%02d".format(i + 1), Fonts.mono(this), 10f, if (fav) Style.INK else 0xFFC9A86A.toInt(), 0f, Gravity.CENTER).apply {
        setBackgroundColor(if (fav) 0xFFB8914A.toInt() else 0xFF0D0D0D.toInt())
    }, LinearLayout.LayoutParams(dp(24f), ph))
    val nmv = txt(nm, Style.kr(this, 400), nameSp(14f), look.nameColorOn(false, Style.IVORY), 0f, Gravity.CENTER_VERTICAL).apply {
        background = FlapBg(dpf(1f)); setPadding(dp(8f), 0, dp(4f), 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    inner.addView(nmv, LinearLayout.LayoutParams(0, ph, 1f).apply { leftMargin = dp(4f) })
    inner.addView(txt(if (count == 0) "—" else "%02d".format(count), Fonts.mono(this), 9f, Style.ivory(.65f), 0f, Gravity.END or Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(dp(22f), ph).apply { leftMargin = dp(4f) })
    r.addView(inner, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(4f) })
    val d = View(this).apply { background = shape(Style.ROUGE, oval = true); visibility = if (badge) View.VISIBLE else View.GONE }
    r.addView(d, FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.END or Gravity.TOP).apply { rightMargin = dp(24f); topMargin = dp(3f) })
    gridDots[id] = d
    bindFolder(r, id) { on -> nmv.setTextColor(if (on) GOLD_LT else look.nameColorOn(false, Style.IVORY)) }
    return r
}

/** ⑤ 문단 속 낱말: 작은 아이콘 + 이름 */
private fun MainActivity.wordView(i: Int, id: String, nm: String, fav: Boolean, badge: Boolean): View {
    val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    repo.coverApps(id).firstOrNull()?.let { a ->
        r.addView(ImageView(this).apply { setImageDrawable(iconOf(a)); styleIcon(this) }, LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { rightMargin = dp(5f) })
    }
    r.addView(txt(nm, Style.kr(this, 300), nameSp(17f), look.nameColorOn(false, if (fav) GOLD_LT else Style.ivory(.92f)), 0f), LinearLayout.LayoutParams(-2, -2))
    val d = txt("●", Style.jost(this, 400), 7f, Style.ROUGE, 0f).apply { visibility = if (badge) View.VISIBLE else View.GONE }
    r.addView(d, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(1f); bottomMargin = dp(8f) })
    gridDots[id] = d
    bindFolder(r, id)
    return r
}

/** ⑤ 낱말을 줄 높이에 맞춰 흘려 놓고, 같은 줄의 낱말 사이에만 '·'를 찍음 (줄 맨 앞에는 없음) */
@SuppressLint("ViewConstructor")
class FlowWords(ctx: Context, private val lineH: Float, private val sepW: Float, private val sepColor: Int, private val sepSize: Float) : ViewGroup(ctx) {
    private val seps = ArrayList<Pair<Float, Float>>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    init { setWillNotDraw(false) }
    override fun onMeasure(ws: Int, hs: Int) {
        val w = MeasureSpec.getSize(ws)
        var x = 0f; var line = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i); c.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            val need = (if (x > 0) sepW else 0f) + c.measuredWidth
            if (x > 0 && x + need > w) { line++; x = 0f }
            x += (if (x > 0) sepW else 0f) + c.measuredWidth
        }
        setMeasuredDimension(w, ((line + 1) * lineH).roundToInt())
    }
    override fun onLayout(ch: Boolean, l: Int, t: Int, r: Int, b: Int) {
        seps.clear()
        val w = r - l; var x = 0f; var line = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val need = (if (x > 0) sepW else 0f) + c.measuredWidth
            if (x > 0 && x + need > w) { line++; x = 0f }
            if (x > 0) { seps += Pair(x + sepW / 2, line * lineH + lineH / 2); x += sepW }
            val top = (line * lineH + (lineH - c.measuredHeight) / 2).roundToInt()
            c.layout(x.roundToInt(), top, (x + c.measuredWidth).roundToInt(), top + c.measuredHeight)
            x += c.measuredWidth
        }
        invalidate()
    }
    override fun onDraw(c: Canvas) {
        p.color = sepColor; p.textSize = sepSize
        val fm = p.fontMetrics
        seps.forEach { (x, y) -> c.drawText("·", x, y - (fm.ascent + fm.descent) / 2, p) }
    }
}

// ═════════ 열린 폴더 ═════════
/** 카드 모양을 테마에 맞춤 — MONSIEUR 편지 카드 · LIBRARY 펼친 책 · TRAIN 승차권 (검색·미분류·표지 고르기는 편지 카드) */
internal fun MainActivity.skinCard(t: Int) {
    cardSkin = t
    stubPerf.visibility = if (t == Look.TRAIN) View.VISIBLE else View.GONE
    when (t) {
        Look.LIBRARY -> {
            card.background = BookSpread(this); card.foreground = null
            cardNum.visibility = View.GONE
            cardKicker.typeface = Style.jost(this, 400); cardKicker.textSize = 9f; cardKicker.letterSpacing = .35f; cardKicker.setTextColor(Style.ink(.5f))
            cardTitle.typeface = Style.kr(this, 300); cardTitle.textSize = 30f
            cardSub.visibility = View.VISIBLE; cardSub.typeface = Fonts.serifI(this); cardSub.textSize = 16f; cardSub.setTextColor(Style.ink(.6f))
            closeBtn.setTextColor(Style.INK); closeBtn.background = shape(stroke = Style.INK, strokePx = dp(1f)); closeBtn.typeface = Style.jost(this, 400)
        }
        Look.TRAIN -> {
            card.background = Style.tile(this, Style.PAPER, 256f); card.foreground = null
            cardNum.visibility = View.GONE
            cardKicker.typeface = Fonts.mono(this); cardKicker.textSize = 10f; cardKicker.letterSpacing = .2f; cardKicker.setTextColor(Style.INK)
            cardTitle.typeface = Style.kr(this, 300); cardTitle.textSize = 26f
            cardSub.visibility = View.GONE
            closeBtn.setTextColor(Style.INK); closeBtn.background = shape(stroke = Style.INK, strokePx = dp(1f)); closeBtn.typeface = Fonts.mono(this)
        }
        else -> {
            card.background = Style.tile(this, Style.PAPER, 256f); card.foreground = InsetBox(Style.INK, dpf(1.5f), dpf(8f))
            cardNum.visibility = View.VISIBLE
            cardKicker.typeface = Style.jost(this, 400); cardKicker.textSize = 9f; cardKicker.letterSpacing = .35f; cardKicker.setTextColor(Style.ink(.5f))
            cardTitle.typeface = Style.kr(this, 300); cardTitle.textSize = 24f
            cardSub.visibility = View.GONE
            closeBtn.setTextColor(Style.IVORY); closeBtn.setBackgroundColor(Style.INK); closeBtn.typeface = Style.jost(this, 400)
        }
    }
}

/** 카드가 놓일 높이 (엄지 자리) — 화면 높이 비율 */
internal fun MainActivity.cardTopFor(t: Int): Int {
    val y = when (t) { Look.LIBRARY -> 300f; Look.TRAIN -> 236f; else -> 220f }
    return (root.height * y / 915f).roundToInt()
}

/** LIBRARY 카드 바탕: 펼친 두 쪽 (가운데 접힘 그늘) */
class BookSpread(private val ctx: Context) : android.graphics.drawable.Drawable() {
    private val paper: android.graphics.drawable.Drawable = if (Dk.lib) android.graphics.drawable.ColorDrawable(Dk.LIB_PAGE) else Style.tile(ctx, Style.PAPER, 256f)
    private val p = Paint()
    override fun draw(c: Canvas) {
        val b = bounds
        paper.bounds = b; paper.draw(c)
        val mid = b.exactCenterX(); val gw = ctx.dpf(18f)
        p.shader = android.graphics.LinearGradient(mid - gw, 0f, mid, 0f, Color.TRANSPARENT, Color.argb(if (Dk.lib) 102 else 64, 0, 0, 0), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(mid - gw, b.top.toFloat(), mid, b.bottom.toFloat(), p)
        p.shader = android.graphics.LinearGradient(mid, 0f, mid + gw, 0f, Color.argb(64, 0, 0, 0), Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(mid, b.top.toFloat(), mid + gw, b.bottom.toFloat(), p)
        p.shader = null; p.color = Color.argb(40, 0, 0, 0); c.drawRect(mid - .5f, b.top.toFloat(), mid + .5f, b.bottom.toFloat(), p)
        if (Dk.lib) { p.style = Paint.Style.STROKE; p.strokeWidth = ctx.dpf(.75f); p.color = Color.argb(36, 237, 230, 216); c.drawRect(b.left + .5f, b.top + .5f, b.right - .5f, b.bottom - .5f, p); p.style = Paint.Style.FILL }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** ③ HOUR 열린 폴더 (H3): 세라믹 판 + 창 4열, 넘치면 판 안에서 스크롤, 각진 CLOSE */
internal fun MainActivity.showHourOpen(id: String) {
    hideThemeOpen()
    footVisible(false)
    cardFolder = id
    val list = repo.appsIn(id)
    val layer = FrameLayout(this).apply { setBackgroundColor(Color.argb(150, 0, 0, 0)); elevation = dpf(24f); isClickable = true; setOnClickListener { hideCard() } }
    val L = Lay(this, layer, root.width, root.height)
    val top = maxOf(sbTop + dp(40f), L.y(150f))
    val plate = FrameLayout(this).apply { background = plateDrawable(this@showHourOpen); elevation = dpf(12f); isClickable = true }
    layer.addView(plate, FrameLayout.LayoutParams(-1, root.height - top - navBot - dp(24f)).apply { topMargin = top; leftMargin = L.x(12f); rightMargin = L.x(12f) })
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18f), dp(24f), dp(18f), dp(20f)) }
    plate.addView(col, FrameLayout.LayoutParams(-1, -1))
    col.addView(txt("GUICHET %02d".format(repo.folders().indexOf(id) + 1), Style.jost(this, 400), 10f, H_GOLD, .35f))
    val head = FrameLayout(this)
    head.addView(txt(shortName(repo.nameOf(id)), Style.kr(this, 300), 24f, H_IVORY).apply { maxLines = 1 }, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
    head.addView(txt("${list.size} APPS", Style.jost(this, 400), 10f, tint(.6f, H_IVORY), .2f), FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
    col.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })
    val grid = GridLayout(this).apply { columnCount = 4 }
    val cw = (root.width - L.x(24f) - dp(36f) - dp(18f)) / 4
    list.forEachIndexed { i, a ->
        val cell = appCell(a, a.pkg in BadgeService.active, id, compact = true, onDark = true).apply { background = windowDrawable(this@showHourOpen); setPadding(0, dp(12f), 0, dp(8f)) }
        grid.addView(cell, GridLayout.LayoutParams().apply { width = cw; if (i % 4 > 0) leftMargin = dp(6f); topMargin = dp(8f) })
    }
    val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(grid) }
    col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12f) })
    if (list.isEmpty()) col.addView(txt("비어 있습니다 · 다른 폴더의 앱을 길게 눌러 끌어 오세요", Style.kr(this, 300), 12f, tint(.65f, H_IVORY)), LinearLayout.LayoutParams(-1, -2))
    val close = txt("CLOSE · 닫기", Style.jost(this, 400), 12f, H_GOLD, .25f, Gravity.CENTER).apply { background = shape(stroke = H_GOLD, strokePx = hair(.75f)); setOnClickListener { hideCard() } }
    col.addView(close, LinearLayout.LayoutParams(dp(280f), dp(44f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12f) })
    root.addView(layer, FrameLayout.LayoutParams(-1, -1))
    themeOpen = layer
    if (!reduceMotion) { plate.alpha = 0f; plate.translationY = dpf(24f); plate.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(MainActivity.EASE).start() }
}

/** ④ VOYAGE 열린 폴더: 엽서 카드 — 우표 칸에 번호, 앱 5열, 남색 CLOSE */
internal fun MainActivity.showVoyageOpen(id: String) {
    hideThemeOpen()
    footVisible(false)
    cardFolder = id
    val list = repo.appsIn(id)
    val no = repo.folders().indexOf(id) + 1
    val layer = FrameLayout(this).apply { setBackgroundColor(Color.argb(115, 0, 0, 0)); elevation = dpf(24f); isClickable = true; setOnClickListener { hideCard() } }
    val L = Lay(this, layer, root.width, root.height)
    val cardW = root.width - L.x(40f)
    val top = maxOf(sbTop + dp(40f), L.y(120f))
    val h = minOf(L.z(720f), root.height - top - navBot - dp(20f))
    val card = FrameLayout(this).apply { background = Pic(Style.img(this@showVoyageOpen, "th/postcard.webp"), Pic.FIT); elevation = dpf(14f); isClickable = true }
    layer.addView(card, FrameLayout.LayoutParams(cardW, h).apply { topMargin = top; leftMargin = L.x(20f) })
    val sx = cardW / 372f
    // 우표 칸 (그림 속 점선 칸 자리)
    val stamp = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    stamp.addView(txt("N°", Style.jost(this, 400), 9f, V_INK, .2f, Gravity.CENTER))
    stamp.addView(txt("%02d".format(no), Style.jost(this, 300), 22f, V_INK, 0f, Gravity.CENTER))
    stamp.addView(txt("VOYAGE", Style.jost(this, 400), 8f, V_INK, .2f, Gravity.CENTER))
    card.addView(stamp, FrameLayout.LayoutParams((58 * sx).roundToInt(), (h * .135f).roundToInt()).apply { leftMargin = (290 * sx).roundToInt(); topMargin = (h * .045f).roundToInt() })
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24f), dp(24f), dp(20f), dp(20f)) }
    card.addView(col, FrameLayout.LayoutParams(-1, -1))
    col.addView(txt("CARTE POSTALE · ${list.size} APPS", Style.jost(this, 400), 10f, tint(.7f, V_INK), .3f))
    col.addView(txt(shortName(repo.nameOf(id)), Style.kr(this, 300), 26f, V_INK).apply { maxLines = 1 }, LinearLayout.LayoutParams((240 * sx).roundToInt(), -2).apply { topMargin = dp(6f) })
    val grid = GridLayout(this).apply { columnCount = 5 }
    val cw = (cardW - dp(44f)) / 5
    list.forEach { a -> grid.addView(appCell(a, a.pkg in BadgeService.active, id), GridLayout.LayoutParams().apply { width = cw }) }
    val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(grid) }
    col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = (h * .07f).roundToInt() })
    if (list.isEmpty()) col.addView(txt("비어 있습니다 · 다른 폴더의 앱을 길게 눌러 끌어 오세요", Style.kr(this, 300), 12f, tint(.7f, V_INK)), LinearLayout.LayoutParams(-1, -2))
    val close = txt("CLOSE · 닫기", Style.jost(this, 400), 12f, V_IVORY, .25f, Gravity.CENTER).apply { setBackgroundColor(V_INK); setOnClickListener { hideCard() } }
    col.addView(close, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(12f) })
    root.addView(layer, FrameLayout.LayoutParams(-1, -1))
    themeOpen = layer
    if (!reduceMotion) { card.alpha = 0f; card.translationY = dpf(24f); card.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(MainActivity.EASE).start() }
}

/** ⑤ CAPTION 열린 폴더: 사진 위에 제목과 앱 목록 2열 (한 화면, 넘치면 목록만 스크롤) */
internal fun MainActivity.showCaptionOpen(id: String) {
    hideThemeOpen()
    footVisible(false)
    cardFolder = id
    val layer = FrameLayout(this).apply { elevation = dpf(24f); isClickable = true }
    layer.addView(View(this).apply { background = themeCover.photoDrawable() }, FrameLayout.LayoutParams(-1, -1))
    layer.addView(View(this).apply {
        background = vgrad(Color.argb(90, 10, 10, 10), Color.argb(200, 10, 10, 10), Color.argb(240, 10, 10, 10), Color.argb(245, 10, 10, 10))
    }, FrameLayout.LayoutParams(-1, -1))
    val L = Lay(this, layer, root.width, root.height)
    L.add(txt("THE CAPTION · %02d".format(repo.folders().indexOf(id) + 1), Style.jost(this, 400), 10f, Style.ivory(.75f), .4f), 28f, 226f)
    L.add(txt(repo.nameOf(id), Style.kr(this, 300), 34f, Style.IVORY), 28f, 246f)
    // 닫기: 아래 엄지 자리 (예전 오른쪽 위 작은 글자 — 샤넬팀)
    val close = txt("닫기", Style.kr(this, 400), 14f, Style.IVORY, 0f, Gravity.CENTER).apply { background = UnderLine(Style.ivory(.6f), dpf(1f), dpf(150f)); setOnClickListener { hideCard() } }
    layer.addView(close, FrameLayout.LayoutParams(-1, dp(44f), Gravity.BOTTOM).apply { leftMargin = L.x(26f); rightMargin = L.x(26f); bottomMargin = navBot + dp(20f) })
    val grid = GridLayout(this).apply { columnCount = 2 }
    val list = repo.appsIn(id)
    val cw = (root.width - L.x(56f) - dp(16f)) / 2
    list.forEachIndexed { i, a ->
        grid.addView(appCell(a, a.pkg in BadgeService.active, id, row = true, onDark = true), GridLayout.LayoutParams().apply { width = cw; if (i % 2 == 1) leftMargin = dp(16f) })
    }
    val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(grid) }
    layer.addView(sv, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = L.x(28f); rightMargin = L.x(28f); topMargin = L.y(304f); bottomMargin = navBot + dp(76f) })
    root.addView(layer, FrameLayout.LayoutParams(-1, -1))
    themeOpen = layer
    if (!reduceMotion) { layer.alpha = 0f; layer.animate().alpha(1f).setDuration(220).start() }
}

/** ② LIBRARY 열린 폴더: 펼친 책 두 쪽 — 왼쪽 VOL·제목·앱 수 + 앱 2열×4, 오른쪽 앱 2열×6 (한 번에 20개), 넘기기, 빨간 리본 CLOSE */
internal fun MainActivity.showLibraryOpen(id: String, page: Int = 0) {
    hideThemeOpen()
    footVisible(false)
    cardFolder = id
    val list = repo.appsIn(id)
    val pages = maxOf(1, (list.size + 19) / 20)
    val pg = page.coerceIn(0, pages - 1)
    val chunk = list.drop(pg * 20).take(20)
    val layer = FrameLayout(this).apply { elevation = dpf(24f); isClickable = true }
    layer.addView(View(this).apply { setBackgroundColor(if (Dk.lib) Color.argb(140, 0, 0, 0) else Color.argb(72, 60, 42, 22)); setOnClickListener { hideCard() } }, FrameLayout.LayoutParams(-1, -1))
    val L = Lay(this, layer, root.width, root.height)
    fun ink(a: Float) = Dk.li(a)
    val bookW = root.width - dp(32f)
    val pageW = bookW / 2
    val top = maxOf(sbTop + dp(48f), L.y(230f))
    val h = minOf(L.z(600f), root.height - top - navBot - dp(52f))
    val book = FrameLayout(this).apply { background = BookSpread(this@showLibraryOpen); elevation = dpf(12f); isClickable = true }
    val cellW = (pageW - dp(28f)) / 2
    fun grid(apps: List<AppEntry>) = GridLayout(this).apply { columnCount = 2
        apps.forEach { a -> addView(appCell(a, a.pkg in BadgeService.active, id, onDark = Dk.lib), GridLayout.LayoutParams().apply { width = cellW }) } }
    val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18f), dp(24f), dp(10f), dp(16f)) }
    left.addView(txt("VOL. %02d".format(repo.folders().indexOf(id) + 1), Style.jost(this, 400), 9f, ink(.55f), .35f))
    left.addView(txt(shortName(repo.nameOf(id)), Style.kr(this, 300), 22f, ink(1f)).apply { maxLines = 2 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
    val cnt = android.text.SpannableStringBuilder()
    cnt.append("${list.size}", android.text.style.TypefaceSpan(Style.jost(this, 300)), 0)
    cnt.append(if (list.size == 1) " app" else " apps")
    left.addView(txt(cnt, Fonts.serifI(this), 16f, ink(.65f)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f) })
    left.addView(grid(chunk.take(8)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16f) })
    if (list.isEmpty()) left.addView(txt("비어 있습니다 · 다른 폴더의 앱을 길게 눌러 끌어 오세요", Style.kr(this, 300), 12f, ink(.65f)).apply { setLineSpacing(dpf(3f), 1f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
    left.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
    left.addView(txt(if (pages > 1) "— ${pg + 1} / $pages —" else "", Style.jost(this, 400), 10f, ink(.4f), .2f), LinearLayout.LayoutParams(-1, -2))
    book.addView(left, FrameLayout.LayoutParams(pageW, -1))
    val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14f), dp(24f), dp(14f), dp(16f)) }
    right.addView(grid(chunk.drop(8)), LinearLayout.LayoutParams(-2, -2))
    right.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
    val turn = txt(if (pages > 1) "넘기기 ›" else "", Style.kr(this, 400), 12f, ink(.55f), 0f, Gravity.END or Gravity.CENTER_VERTICAL)
    if (pages > 1) turn.setOnClickListener { showLibraryOpen(id, (pg + 1) % pages) }
    right.addView(turn, LinearLayout.LayoutParams(-1, dp(44f)))
    book.addView(right, FrameLayout.LayoutParams(pageW, -1, Gravity.END))
    layer.addView(book, FrameLayout.LayoutParams(bookW, h).apply { leftMargin = dp(16f); topMargin = top })
    // 빨간 리본 CLOSE (책 아래 가장자리에 걸침)
    val ribbon = txt("CLOSE", Style.jost(this, 400), 11f, Style.IVORY, .2f, Gravity.CENTER).apply {
        background = object : android.graphics.drawable.Drawable() {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(c: Canvas) {
                val b = bounds
                p.shader = android.graphics.LinearGradient(b.left.toFloat(), 0f, b.right.toFloat(), 0f, intArrayOf(0xFF7E1A1E.toInt(), 0xFFA8262B.toInt(), 0xFF7E1A1E.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
                c.drawPath(android.graphics.Path().apply { moveTo(b.left.toFloat(), b.top.toFloat()); lineTo(b.right.toFloat(), b.top.toFloat()); lineTo(b.right.toFloat(), b.bottom.toFloat())
                    lineTo(b.exactCenterX(), b.bottom - b.height() * .18f); lineTo(b.left.toFloat(), b.bottom.toFloat()); close() }, p)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        setPadding(0, 0, 0, dp(6f)); elevation = dpf(14f)
        setOnClickListener { hideCard() }
    }
    layer.addView(ribbon, FrameLayout.LayoutParams(dp(120f), dp(44f), Gravity.CENTER_HORIZONTAL).apply { topMargin = top + h - dp(12f) })
    root.addView(layer, FrameLayout.LayoutParams(-1, -1))
    themeOpen = layer
    if (!reduceMotion) { book.alpha = 0f; book.translationY = dpf(30f); book.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(MainActivity.EASE).start() }
}

internal fun MainActivity.hideThemeOpen(): Boolean {
    val l = themeOpen ?: return false
    root.removeView(l); themeOpen = null
    footVisible(true)
    return true
}

// ═════════ THE COLLECTION (테마 고르기) ═════════
internal fun MainActivity.showCollection() {
    hideSettingsSheet()
    collection?.let { root.removeView(it) }
    val layer = FrameLayout(this).apply { setBackgroundColor(Style.INK); elevation = dpf(40f); isClickable = true }
    var sel = look.theme
    val L = Lay(this, layer, root.width, root.height)
    L.add(txt("THE COLLECTION", Style.jost(this, 400), 11f, Style.ivory(.6f), .45f), 26f, 58f + sbTop / L.d / L.sy)
    val x = txt("닫기", Style.kr(this, 400), 13f, Style.ivory(.75f), 0f, Gravity.CENTER).apply { setOnClickListener { hideCollection() } }
    layer.addView(x, FrameLayout.LayoutParams(dp(64f), dp(44f), Gravity.END).apply { topMargin = sbTop + dp(30f); rightMargin = dp(12f) })
    val pw = L.z(196f); val ph = (pw * 915f / 412f).roundToInt()
    val rail = FrameLayout(this)
    layer.addView(rail, FrameLayout.LayoutParams(-1, ph).apply { topMargin = sbTop + dp(100f) })
    var placeFn: () -> Unit = {}
    val cards = (0 until 5).map { t ->
        FrameLayout(this).apply {
            background = Pic(Style.img(this@showCollection, "th/pv_$t.webp"), Pic.FIT)
            elevation = dpf(10f); clipToOutline = true
        }.also { rail.addView(it, FrameLayout.LayoutParams(pw, ph, Gravity.CENTER_HORIZONTAL)) }
    }
    val name = txt("", Style.jost(this, 400), 12f, Style.IVORY, .35f, Gravity.CENTER)
    layer.addView(name, FrameLayout.LayoutParams(-1, -2).apply { topMargin = sbTop + dp(100f) + ph + dp(22f) })
    val whenT = txt("", Style.kr(this, 300), 12.5f, Style.ivory(.65f), 0f, Gravity.CENTER)
    layer.addView(whenT, FrameLayout.LayoutParams(-1, -2).apply { topMargin = sbTop + dp(100f) + ph + dp(46f) })
    val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    repeat(5) { i -> dots.addView(View(this).apply { setBackgroundColor(Style.IVORY) }, LinearLayout.LayoutParams(dp(16f), dp(1f)).apply { if (i > 0) leftMargin = dp(8f) }) }
    layer.addView(dots, FrameLayout.LayoutParams(-1, dp(9f)).apply { topMargin = sbTop + dp(100f) + ph + dp(80f) })
    val hint = txt("가운데 견본을 누르면 바로 바뀝니다 · 옆으로 밀어 넘기기", Style.kr(this, 300), 11f, Style.ivory(.5f), 0f, Gravity.CENTER)
    layer.addView(hint, FrameLayout.LayoutParams(-1, -2).apply { topMargin = sbTop + dp(100f) + ph + dp(100f) })
    // 바꾼 뒤 4초 동안: '○○로 바꿨습니다 · 되돌리기'
    val undo = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18f), 0, dp(6f), 0)
        background = shape(0xFF1C1C1C.toInt(), tint(.5f, GOLD_LT), hair(.75f)); visibility = View.GONE
    }
    val undoMsg = txt("", Style.kr(this, 300), 13f, Style.IVORY, 0f, Gravity.CENTER_VERTICAL)
    undo.addView(undoMsg, LinearLayout.LayoutParams(0, -1, 1f))
    val undoBtn = txt("되돌리기", Style.kr(this, 400), 13f, GOLD_LT, 0f, Gravity.CENTER).apply { background = UnderLine(GOLD_LT, dpf(1f), dpf(8f)) }
    undo.addView(undoBtn, LinearLayout.LayoutParams(dp(88f), dp(48f)))
    layer.addView(undo, FrameLayout.LayoutParams(-1, dp(48f), Gravity.BOTTOM).apply { leftMargin = dp(26f); rightMargin = dp(26f); bottomMargin = navBot + dp(32f) })
    var prevTheme = look.theme
    val hideUndo = Runnable { undo.visibility = View.GONE }
    fun place() {
        cards.forEachIndexed { t, v ->
            val d = t - sel
            val tx = d * (pw * .78f + dp(12f))
            val s = if (d == 0) 1f else .86f
            v.animate().translationX(tx).scaleX(s).scaleY(s).alpha(if (kotlin.math.abs(d) > 1) 0f else if (d == 0) 1f else .55f).setDuration(if (reduceMotion) 0 else 260).setInterpolator(MainActivity.EASE).start()
            v.translationZ = if (d == 0) dpf(6f) else 0f
            v.foreground = if (t == look.theme) InsetBox(GOLD_LT, dpf(1.5f), 0f) else null
        }
        name.text = Look.NAMES[sel]; whenT.text = Look.WHEN[sel]
        for (i in 0 until 5) dots.getChildAt(i).alpha = if (i == sel) 1f else .3f
    }
    placeFn = { place() }
    collPlace = { place() }
    fun applySel() {
        if (sel == look.theme) return
        prevTheme = look.theme
        applyTheme(sel); place(); collChanged = true
        undoMsg.text = "${Look.NAMES[sel]}로 바꿨습니다"
        undo.visibility = View.VISIBLE
        undo.removeCallbacks(hideUndo); undo.postDelayed(hideUndo, 4000)
    }
    undoBtn.setOnClickListener { val t = prevTheme; undo.visibility = View.GONE; sel = t; applyTheme(t); place() }
    // 옆으로 밀어 넘기기
    var x0 = 0f
    rail.setOnTouchListener { _, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x0 = e.x; true }
            MotionEvent.ACTION_UP -> {
                val dx = e.x - x0
                if (kotlin.math.abs(dx) > dp(40f)) { sel = (sel + if (dx < 0) 1 else -1).coerceIn(0, 4); place() }
                else {
                    val cx = rail.width / 2f
                    when {
                        e.x < cx - pw / 2 -> { sel = (sel - 1).coerceAtLeast(0); place() }
                        e.x > cx + pw / 2 -> { sel = (sel + 1).coerceAtMost(4); place() }
                        else -> applySel()   // 가운데 견본을 누르면 바로 적용
                    }
                }
                true
            }
            else -> true
        }
    }
    root.addView(layer, FrameLayout.LayoutParams(-1, -1))
    collection = layer
    place()
}

internal fun MainActivity.hideCollection(): Boolean {
    val c = collection ?: return false
    root.removeView(c); collection = null; collPlace = null
    // 테마를 바꿨으면 닫을 때 서랍도 닫고 홈(표지)으로 — 새 표지를 바로 보이게
    if (collChanged) { collChanged = false; closeAllNow(); goPage(0, false) }
    return true
}
