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
            box.background = android.graphics.drawable.LayerDrawable(arrayOf(
                android.graphics.drawable.ColorDrawable(0xFF2A1A10.toInt()),
                mirrorTile(this, Style.img(this, "th/table_wood.webp"), (root.width * .9f).roundToInt()),
                vgrad(Color.argb(140, 0, 0, 0), Color.argb(13, 0, 0, 0), Color.argb(13, 0, 0, 0), Color.argb(90, 0, 0, 0))))
            top = L.y(150f); hdrH = L.y(240f) - top; side = L.x(14f)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(txt("THE LIBRARY", Style.jost(this, 400), 12f, Style.ivory(.92f), .45f))
            col.addView(txt("Thirty volumes on the table.", Fonts.serifI(this), 19f, GOLD_LT), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8f) })
            hdr.addView(col, FrameLayout.LayoutParams(-1, -2).apply { leftMargin = L.x(6f) })
        }
        Look.HOUR -> {
            box.background = android.graphics.drawable.GradientDrawable().apply {
                gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT; colors = intArrayOf(0xFF181818.toInt(), 0xFF0B0B0B.toInt())
                gradientRadius = root.width * .9f; setGradientCenter(.5f, .62f)
            }
            val d = DialFace(this, 1f).also { dial -> dialFace = dial }
            L.add(d, 86f, 60f, 240f, 240f)
            hourWx = L.t("", Style.kr(this, 300), 13f, Style.ivory(.75f), 0f, Gravity.CENTER).also { L.addLR(it, 0f, 0f, 318f) }
            top = L.y(366f); hdrH = 0; side = L.x(24f)
        }
        Look.TRAIN -> {
            box.background = android.graphics.drawable.ColorDrawable(NAVY)
            deco.addView(View(this).apply { background = FrameDrawable(Style.img(this@themeBox, "th/gilt_frame.webp"), 60, L.z(18f)) },
                FrameLayout.LayoutParams(-1, -1).apply { topMargin = L.y(36f); leftMargin = L.x(4f); rightMargin = L.x(4f); bottomMargin = L.y(4f) })
            top = L.y(62f); hdrH = L.y(104f) - top; side = L.x(30f)
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(txt("DEPARTURES", Fonts.mono(this), 11f, GOLD_LT, .22f).apply { maxLines = 1 }, LinearLayout.LayoutParams(-2, -2))
            r.addView(txt("LE TRAIN BLEU · 1924", Fonts.serif(this), 13f, tint(.85f, GOLD_LT), .1f, Gravity.CENTER).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            val tc = txt("", Fonts.mono(this), 11f, GOLD_LT, .22f, Gravity.END).apply { maxLines = 1 }; trainClock = tc
            r.addView(tc, LinearLayout.LayoutParams(-2, -2))
            hdr.addView(r, FrameLayout.LayoutParams(-1, -2))
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
        Look.LIBRARY -> "31 · RUE CAMBON · LA TABLE"; Look.HOUR -> "MONSIEUR HOUR · %s"
        Look.TRAIN -> "GARE DE LYON · %s"; Look.CAPTION -> "PHOTOGRAPHIE · %s"; else -> "31 · RUE CAMBON · %s"
    }
    searchLine.setBackgroundColor(if (t == Look.TRAIN || t == Look.LIBRARY) tint(.5f, GOLD_LT) else Style.gold(.4f))
    search.setHintTextColor(Style.ivory(.6f))
    folio.setTextColor(Style.ivory(.6f)); settingsBtn.setTextColor(Style.ivory(.6f))
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
    trainClock?.text = hm.replace(":", ":")
    hourWx?.text = wx
    dialFace?.let { it.hour = cal.get(java.util.Calendar.HOUR_OF_DAY); it.minute = cal.get(java.util.Calendar.MINUTE)
        it.date = "${cal.get(java.util.Calendar.DAY_OF_MONTH)} " + arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]; it.invalidate() }
}

// ═════════ 폴더 30개 깔기 ═════════
internal fun MainActivity.buildStage() {
    val W = stage.width.takeIf { it > 0 } ?: return
    val H = stage.height.takeIf { it > 0 } ?: return
    stageIn.removeAllViews(); gridDots.clear(); hourCenter = null
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
            // 3더미 × 10권 (넘치면 더미가 길어짐)
            val per = maxOf(10, (folders.size + 2) / 3)
            val gap = dp(2f)
            val bh = (((H - gap * (per - 1)) / per.toFloat()).coerceAtMost(dpf(47f) * k)).roundToInt().coerceAtLeast(dp(34f))
            val colW = W / 3
            folders.forEachIndexed { i, id ->
                val s = i / per; val r = i % per
                val nm = shortName(repo.nameOf(id))
                val v = bookView(i, s, id, nm, id in favs, repo.appsIn(id).any { it.pkg in badges }, colW, bh)
                dimIfNot(v, id)
                stageIn.addView(v, FrameLayout.LayoutParams(v.tag as Int, bh).apply {
                    leftMargin = s * colW + (colW - (v.tag as Int)) / 2; topMargin = r * (bh + gap)
                })
            }
            contentH = maxOf(H, per * (bh + gap))
        }
        Look.HOUR -> {
            // 이중 링: 바깥 18 · 안 12 (넘치면 바깥 링을 늘림)
            val cx = W / 2f; val cy = H / 2f
            val outer = minOf(18, folders.size); val inner = folders.size - outer
            val so = dpf(52f) * k; val si = dpf(48f) * k
            val rO = minOf(W / 2f - so / 2 - dp(6f), H / 2f - so / 2 - dp(4f), dpf(164f) * k)
            val rI = rO - (so + si) / 2 - dp(6f)
            // 시작 표시 (12시 바깥 금색 점)
            stageIn.addView(View(this).apply { background = shape(GOLD_LT, oval = true) },
                FrameLayout.LayoutParams(dp(5f), dp(5f)).apply { leftMargin = (cx - dp(2.5f)).roundToInt(); topMargin = (cy - rO - so / 2 - dp(9f)).roundToInt().coerceAtLeast(0) })
            // 가운데: 미리보기
            val cs = (2 * (rI - si / 2) - dp(8f)).roundToInt().coerceAtMost(dp(150f))
            val center = hourCenterView(cs)
            stageIn.addView(center, FrameLayout.LayoutParams(cs, cs).apply { leftMargin = (cx - cs / 2).roundToInt(); topMargin = (cy - cs / 2).roundToInt() })
            hourCenter = center
            folders.forEachIndexed { i, id ->
                val ring = i < outer
                val n = if (ring) outer else inner
                val a = Math.toRadians(360.0 * (if (ring) i else i - outer) / n)
                val r = if (ring) rO else rI; val sz = if (ring) so else si
                val x = cx + r * sin(a).toFloat(); val y = cy - r * cos(a).toFloat()
                val v = capsuleView(i, id, shortName(repo.nameOf(id)), id in favs, repo.appsIn(id).any { it.pkg in badges }, sz.roundToInt())
                dimIfNot(v, id)
                stageIn.addView(v, FrameLayout.LayoutParams(sz.roundToInt(), sz.roundToInt()).apply { leftMargin = (x - sz / 2).roundToInt(); topMargin = (y - sz / 2).roundToInt() })
            }
            showHourPreview(favs.firstOrNull { it in folders } ?: folders.firstOrNull())
        }
        Look.TRAIN -> {
            val rows = maxOf(15, (folders.size + 1) / 2)
            val rh = ((H / rows.toFloat()).coerceAtMost(dpf(44f) * k)).roundToInt().coerceAtLeast(dp(32f))
            val cw = (W - dp(10f)) / 2
            folders.forEachIndexed { i, id ->
                val c = i / rows; val r = i % rows
                val v = boardRow(i, id, shortName(repo.nameOf(id)), id in favs, repo.appsIn(id).any { it.pkg in badges }, repo.appsIn(id).size, rh)
                dimIfNot(v, id)
                stageIn.addView(v, FrameLayout.LayoutParams(cw, rh).apply { leftMargin = c * (cw + dp(10f)); topMargin = r * rh })
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
            val cardH = (minOf(fitH * maxOf(1f, k), colW * 1.25f) * (if (k < 1f) k else 1f)).roundToInt().coerceAtLeast(dp(56f))
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
    folio.text = folioBase.format(if (n == 1) "1 FOLDER" else "$n FOLDERS").let { if (look.theme == Look.TRAIN) it.replace("FOLDERS", "QUAIS").replace("FOLDER", "QUAI") else it } +
        (if (un > 0) " · UNSORTED $un" else "")
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
    v.addView(txt("%02d".format(i + 1), Style.jost(this, 400), 9f, if (dark) GOLD_LT else 0xFF7A5A22.toInt(), 0f),
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
    inner.addView(txt("%02d".format(count), Fonts.mono(this), 9f, Style.ivory(.65f), 0f, Gravity.END or Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(dp(22f), ph).apply { leftMargin = dp(4f) })
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
    private val paper = Style.tile(ctx, Style.PAPER, 256f)
    private val p = Paint()
    override fun draw(c: Canvas) {
        val b = bounds
        paper.bounds = b; paper.draw(c)
        val mid = b.exactCenterX(); val gw = ctx.dpf(18f)
        p.shader = android.graphics.LinearGradient(mid - gw, 0f, mid, 0f, Color.TRANSPARENT, Color.argb(64, 0, 0, 0), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(mid - gw, b.top.toFloat(), mid, b.bottom.toFloat(), p)
        p.shader = android.graphics.LinearGradient(mid, 0f, mid + gw, 0f, Color.argb(64, 0, 0, 0), Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(mid, b.top.toFloat(), mid + gw, b.bottom.toFloat(), p)
        p.shader = null; p.color = Color.argb(40, 0, 0, 0); c.drawRect(mid - .5f, b.top.toFloat(), mid + .5f, b.bottom.toFloat(), p)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** ③ HOUR 열린 폴더: 앱을 동심원(안 6 · 바깥 12)에, 18개가 넘으면 가운데 '다음 장' */
internal fun MainActivity.showHourOpen(id: String, page: Int = 0) {
    hideThemeOpen()
    cardFolder = id
    val list = repo.appsIn(id)
    val pages = maxOf(1, (list.size + 17) / 18)
    val pg = page.coerceIn(0, pages - 1)
    val layer = FrameLayout(this).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT; colors = intArrayOf(0xFF1B1B1B.toInt(), 0xFF0B0B0B.toInt())
            gradientRadius = root.width * .8f; setGradientCenter(.5f, .58f)
        }
        elevation = dpf(24f); isClickable = true
    }
    val L = Lay(this, layer, root.width, root.height)
    val cx = root.width / 2f; val cy = L.y(560f).toFloat()
    val ring = FrameLayout(this).apply {
        background = shape(0xFF141414.toInt(), tint(.35f, 0xFFC9A86A.toInt()), dp(1f), oval = true)
    }
    val rr = L.z(170f)
    layer.addView(ring, FrameLayout.LayoutParams(rr * 2, rr * 2).apply { leftMargin = (cx - rr).roundToInt(); topMargin = (cy - rr).roundToInt() })
    val r2 = L.z(110f)
    layer.addView(View(this).apply { background = shape(stroke = tint(.35f, 0xFFC9A86A.toInt()), strokePx = dp(1f), oval = true) },
        FrameLayout.LayoutParams(r2 * 2, r2 * 2).apply { leftMargin = (cx - r2).roundToInt(); topMargin = (cy - r2).roundToInt() })
    val icon = dp(look.iconDp * 44f / 52f)
    list.drop(pg * 18).take(18).forEachIndexed { k, a ->
        val (r, ang) = if (k < 6) L.zf(84f) to k * 60.0 else L.zf(158f) to (k - 6) * 30.0 + 15
        val x = cx + r * sin(Math.toRadians(ang)).toFloat(); val y = cy - r * cos(Math.toRadians(ang)).toFloat()
        val cell = appCell(a, a.pkg in BadgeService.active, id, compact = true, onDark = true)
        val cw = dp(64f)
        layer.addView(cell, FrameLayout.LayoutParams(cw, -2).apply { leftMargin = (x - cw / 2).roundToInt(); topMargin = (y - icon / 2f).roundToInt() })
    }
    val ct = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    ct.addView(txt("FOLDER %02d".format(repo.folders().indexOf(id) + 1), Style.jost(this, 400), 8f, 0xFFC9A86A.toInt(), .3f, Gravity.CENTER))
    ct.addView(txt(repo.nameOf(id), Style.kr(this, 300), 18f, Style.IVORY, 0f, Gravity.CENTER).apply { maxLines = 1 }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2f) })
    ct.addView(txt(if (pages > 1) "${list.size} · 다음 장 ›" else "${list.size}", Style.kr(this, 300), 9f, Style.ivory(.6f), 0f, Gravity.CENTER), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2f) })
    if (pages > 1) ct.setOnClickListener { showHourOpen(id, (pg + 1) % pages) }
    val cs = L.z(110f)
    layer.addView(ct, FrameLayout.LayoutParams(cs, cs).apply { leftMargin = (cx - cs / 2).roundToInt(); topMargin = (cy - cs / 2).roundToInt() })
    val close = txt("CLOSE · 닫기", Style.jost(this, 400), 12f, GOLD_LT, .2f, Gravity.CENTER).apply {
        background = shape(stroke = tint(.6f, 0xFFC9A86A.toInt()), strokePx = dp(1f)); setOnClickListener { hideCard() }
    }
    layer.addView(close, FrameLayout.LayoutParams(-1, dp(44f), Gravity.BOTTOM).apply { leftMargin = L.x(26f); rightMargin = L.x(26f); bottomMargin = navBot + dp(28f) })
    root.addView(layer, FrameLayout.LayoutParams(-1, -1))
    themeOpen = layer
    if (!reduceMotion) { layer.alpha = 0f; layer.animate().alpha(1f).setDuration(220).start() }
}

/** ⑤ CAPTION 열린 폴더: 사진 위에 제목과 앱 목록 2열 (한 화면, 넘치면 목록만 스크롤) */
internal fun MainActivity.showCaptionOpen(id: String) {
    hideThemeOpen()
    cardFolder = id
    val layer = FrameLayout(this).apply { elevation = dpf(24f); isClickable = true }
    layer.addView(View(this).apply { background = themeCover.photoDrawable() }, FrameLayout.LayoutParams(-1, -1))
    layer.addView(View(this).apply {
        background = vgrad(Color.argb(90, 10, 10, 10), Color.argb(200, 10, 10, 10), Color.argb(240, 10, 10, 10), Color.argb(245, 10, 10, 10))
    }, FrameLayout.LayoutParams(-1, -1))
    val L = Lay(this, layer, root.width, root.height)
    L.add(txt("THE CAPTION · %02d".format(repo.folders().indexOf(id) + 1), Style.jost(this, 400), 10f, Style.ivory(.75f), .4f), 28f, 226f)
    L.add(txt(repo.nameOf(id), Style.kr(this, 300), 34f, Style.IVORY), 28f, 246f)
    val close = txt("닫기 ×", Style.kr(this, 400), 13f, Style.IVORY, 0f, Gravity.CENTER).apply { background = UnderLine(Style.ivory(.6f), dpf(1f), dpf(6f)); setOnClickListener { hideCard() } }
    L.add(close, 316f, 252f, 72f, 44f)
    val grid = GridLayout(this).apply { columnCount = 2 }
    val list = repo.appsIn(id)
    val cw = (root.width - L.x(56f) - dp(16f)) / 2
    list.forEachIndexed { i, a ->
        grid.addView(appCell(a, a.pkg in BadgeService.active, id, row = true, onDark = true), GridLayout.LayoutParams().apply { width = cw; if (i % 2 == 1) leftMargin = dp(16f) })
    }
    val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(grid) }
    layer.addView(sv, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = L.x(28f); rightMargin = L.x(28f); topMargin = L.y(304f); bottomMargin = navBot + dp(16f) })
    root.addView(layer, FrameLayout.LayoutParams(-1, -1))
    themeOpen = layer
    if (!reduceMotion) { layer.alpha = 0f; layer.animate().alpha(1f).setDuration(220).start() }
}

internal fun MainActivity.hideThemeOpen(): Boolean {
    val l = themeOpen ?: return false
    root.removeView(l); themeOpen = null
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
            setOnClickListener { if (t != sel) { sel = t; placeFn() } }
        }.also { rail.addView(it, FrameLayout.LayoutParams(pw, ph, Gravity.CENTER_HORIZONTAL)) }
    }
    val name = txt("", Style.jost(this, 400), 12f, Style.IVORY, .35f, Gravity.CENTER)
    layer.addView(name, FrameLayout.LayoutParams(-1, -2).apply { topMargin = sbTop + dp(100f) + ph + dp(22f) })
    val whenT = txt("", Style.kr(this, 300), 12.5f, Style.ivory(.65f), 0f, Gravity.CENTER)
    layer.addView(whenT, FrameLayout.LayoutParams(-1, -2).apply { topMargin = sbTop + dp(100f) + ph + dp(46f) })
    val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    repeat(5) { i -> dots.addView(View(this).apply { setBackgroundColor(Style.IVORY) }, LinearLayout.LayoutParams(dp(16f), dp(1f)).apply { if (i > 0) leftMargin = dp(8f) }) }
    layer.addView(dots, FrameLayout.LayoutParams(-1, dp(9f)).apply { topMargin = sbTop + dp(100f) + ph + dp(80f) })
    val use = txt("", Style.kr(this, 400), 13f, Style.INK, .04f, Gravity.CENTER).apply { setBackgroundColor(Style.IVORY) }
    layer.addView(use, FrameLayout.LayoutParams(-1, dp(48f), Gravity.BOTTOM).apply { leftMargin = dp(26f); rightMargin = dp(26f); bottomMargin = navBot + dp(32f) })
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
        use.text = if (sel == look.theme) "사용 중 · IN USE" else "이 테마로 · USE"
    }
    placeFn = { place() }
    collPlace = { place() }
    use.setOnClickListener { if (sel != look.theme) { applyTheme(sel); place() } else hideCollection() }
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
                    if (e.x < cx - pw / 2) { sel = (sel - 1).coerceAtLeast(0); place() } else if (e.x > cx + pw / 2) { sel = (sel + 1).coerceAtMost(4); place() }
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
    return true
}
