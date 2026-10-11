package com.kiouvici.monsieur

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.net.http.SslError
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class MainActivity : ComponentActivity() {

    companion object {
        const val HOME_URL = "https://kiouvici.synology.me:8123/dashboard-chanel/0"
        val EASE = PathInterpolator(.32f, .72f, 0f, 1f)
    }

    private lateinit var repo: AppRepo
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var lidLayer: FrameLayout
    private lateinit var progress: View
    private lateinit var failBox: LinearLayout
    private lateinit var dim: View
    private lateinit var tab: FrameLayout
    private lateinit var box: FrameLayout
    private lateinit var grid: GridLayout
    private lateinit var countText: TextView
    private lateinit var folio: TextView
    private lateinit var search: EditText
    private lateinit var card: FrameLayout
    private lateinit var cardKicker: TextView
    private lateinit var cardTitle: TextView
    private lateinit var cardHint: TextView
    private lateinit var cardApps: GridLayout

    private var openP = 0f          // 0 닫힘 ~ 1 열림
    private var boxH = 0
    private var reduceMotion = false
    private var pageLoaded = false
    private var cardFolder: String? = null

    // ───────── 시작 ─────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepo(this)
        reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        root = FrameLayout(this).apply { setBackgroundColor(Style.INK) }
        setContentView(root)

        buildWeb()
        buildLid()
        dim = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0f; visibility = View.GONE; setOnClickListener { closeAll() } }
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))
        buildTab()
        buildBox()
        buildCard()

        root.post {
            boxH = root.height - dp(24f)
            (box.layoutParams as FrameLayout.LayoutParams).apply { height = boxH; topMargin = dp(24f) }
            box.requestLayout()
            setOpen(0f)
        }

        BadgeService.onChange = { runOnUiThread { refreshGrid(); cardFolder?.let { refreshCardApps() } } }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    card.visibility == View.VISIBLE -> hideCard()
                    openP > 0f -> animateOpen(0f)
                    web.canGoBack() -> web.goBack()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 홈 버튼: 열린 것만 닫는다 (서랍은 끌어올리기로만 연다 — 성훈님 결정)
        if (Intent.ACTION_MAIN == intent.action) closeAll()
    }

    override fun onResume() {
        super.onResume()
        repo.reload()
        refreshGrid()
        if (!pageLoaded) web.reload()
    }

    // ───────── HA 화면 ─────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWeb() {
        web = WebView(this).apply {
            setBackgroundColor(Style.INK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, p: Int) { setProgress(p / 100f) }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageLoaded = true
                    hideLid()
                }
                override fun onReceivedError(view: WebView?, req: WebResourceRequest?, err: WebResourceError?) {
                    if (req?.isForMainFrame == true) { pageLoaded = false; showFail() }
                }
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.cancel(); pageLoaded = false; showFail()
                }
            }
            loadUrl(HOME_URL)
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
    }

    // ───────── ① 첫 화면 (닫힌 상자 뚜껑) ─────────
    private fun buildLid() {
        lidLayer = FrameLayout(this).apply { setBackgroundColor(Style.INK); isClickable = true }
        val lid = ImageView(this).apply {
            setImageBitmap(Style.img(this@MainActivity, "lid.webp"))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        lidLayer.addView(lid, FrameLayout.LayoutParams(-1, dp(560f)).apply { topMargin = dp(64f) })
        val wm = label("CHANEL", Style.jost(this, 300), 22f, Style.IVORY, .5f)
        lidLayer.addView(wm, FrameLayout.LayoutParams(-1, -2).apply { topMargin = dp(64f + 150f) })
        val ms = label("MONSIEUR", Style.jost(this, 400), 10f, Style.ivory(.6f), .3f)
        lidLayer.addView(ms, FrameLayout.LayoutParams(-1, -2).apply { topMargin = dp(64f + 190f) })

        val track = FrameLayout(this).apply { setBackgroundColor(Style.gold(.2f)) }
        progress = View(this).apply { setBackgroundColor(Style.GOLD); pivotX = 0f; scaleX = 0f }
        track.addView(progress, FrameLayout.LayoutParams(-1, -1))
        lidLayer.addView(track, FrameLayout.LayoutParams(dp(220f), dp(1f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(64f + 584f)
        })

        failBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE
        }
        failBox.addView(label("MAISON · 연결되지 않음", Style.kr(this, 300), 12f, Style.ivory(.7f), .05f))
        val retry = label("다시 연결", Style.kr(this, 400), 13f, Style.IVORY, .04f).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setStroke(dp(1f), Style.ivory(.8f)) }
            setOnClickListener { failBox.visibility = View.GONE; setProgress(0f); web.loadUrl(HOME_URL) }
        }
        failBox.addView(retry, LinearLayout.LayoutParams(dp(180f), dp(44f)).apply { topMargin = dp(14f) })
        lidLayer.addView(failBox, FrameLayout.LayoutParams(-1, -2).apply { topMargin = dp(64f + 600f) })
        root.addView(lidLayer, FrameLayout.LayoutParams(-1, -1))
    }

    private fun setProgress(p: Float) { progress.scaleX = p.coerceIn(0f, 1f) }
    private fun hideLid() {
        if (lidLayer.visibility != View.VISIBLE) return
        lidLayer.animate().alpha(0f).setDuration(if (reduceMotion) 120 else 420).setInterpolator(EASE)
            .withEndAction { lidLayer.visibility = View.GONE }.start()
    }
    private fun showFail() {
        lidLayer.animate().cancel(); lidLayer.alpha = 1f; lidLayer.visibility = View.VISIBLE
        failBox.visibility = View.VISIBLE
    }

    // ───────── 리본 손잡이 ─────────
    @SuppressLint("ClickableViewAccessibility")
    private fun buildTab() {
        tab = FrameLayout(this)
        val rib = ImageView(this).apply {
            setImageBitmap(Style.img(this@MainActivity, "loop.webp"))
            scaleType = ImageView.ScaleType.FIT_CENTER
            elevation = dpf(4f)
        }
        tab.addView(rib, FrameLayout.LayoutParams(dp(26f), dp(58f), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM))
        tab.contentDescription = "앱 상자 열기"
        root.addView(tab, FrameLayout.LayoutParams(dp(120f), dp(72f), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
            bottomMargin = dp(40f)
        })
        tab.setOnTouchListener(DragOpen(fromOpen = false))
    }

    /** 손가락을 1:1로 따라 상자를 여닫는 끌기 */
    private inner class DragOpen(private val fromOpen: Boolean) : View.OnTouchListener {
        private var y0 = 0f
        private var p0 = 0f
        private var moved = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { y0 = e.rawY; p0 = openP; moved = false; return true }
                MotionEvent.ACTION_MOVE -> {
                    val d = (y0 - e.rawY) / boxH.coerceAtLeast(1)
                    if (kotlin.math.abs(y0 - e.rawY) > dp(4f)) moved = true
                    setOpen((p0 + d).coerceIn(0f, 1f))
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!fromOpen && !moved) { animateOpen(1f); return true }
                    animateOpen(if (fromOpen) (if (openP < .65f) 0f else 1f) else (if (openP > .35f) 1f else 0f))
                    return true
                }
            }
            return false
        }
    }

    private fun setOpen(p: Float) {
        openP = p
        box.translationY = (1f - p) * boxH
        box.visibility = if (p > 0f) View.VISIBLE else View.INVISIBLE
        dim.visibility = if (p > 0f) View.VISIBLE else View.GONE
        dim.alpha = p * .4f
        tab.alpha = if (p > .02f) 0f else 1f
        if (p == 0f) hideKeyboard()
    }

    private var anim: ValueAnimator? = null
    private fun animateOpen(target: Float) {
        anim?.cancel()
        if (reduceMotion) { setOpen(target); return }
        anim = ValueAnimator.ofFloat(openP, target).apply {
            duration = 420; interpolator = EASE
            addUpdateListener { setOpen(it.animatedValue as Float) }
            start()
        }
    }

    private fun closeAll() {
        hideCard()
        search.setText("")
        animateOpen(0f)
    }

    // ───────── ② 상자 안 (서랍) ─────────
    @SuppressLint("ClickableViewAccessibility")
    private fun buildBox() {
        box = FrameLayout(this).apply {
            val inner = Style.img(this@MainActivity, "inner.webp")
            val scaled = Bitmap.createScaledBitmap(inner, dp(256f), dp(256f), true)
            background = BitmapDrawable(resources, scaled).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
            isClickable = true
            visibility = View.INVISIBLE
            elevation = dpf(8f)
        }
        root.addView(box, FrameLayout.LayoutParams(-1, -1))

        // 열린 상자 가장자리 + 위에 걸린 리본 (여기를 끌어 내리면 닫힘)
        val edge = ImageView(this).apply {
            setImageBitmap(Style.img(this@MainActivity, "edge.webp")); scaleType = ImageView.ScaleType.FIT_XY
        }
        box.addView(edge, FrameLayout.LayoutParams(-1, dp(50f)))
        val rib = ImageView(this).apply {
            setImageBitmap(Style.img(this@MainActivity, "loop.webp")); scaleType = ImageView.ScaleType.FIT_CENTER
        }
        box.addView(rib, FrameLayout.LayoutParams(dp(21f), dp(46f), Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(2f) })
        val grip = View(this)
        box.addView(grip, FrameLayout.LayoutParams(-1, dp(56f)))
        grip.setOnTouchListener(DragOpen(fromOpen = true))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(col, FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = dp(56f); leftMargin = dp(16f); rightMargin = dp(16f); bottomMargin = dp(20f)
        })

        val hd = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        hd.addView(label("LA BOÎTE", Style.jost(this, 400), 10f, Style.ivory(.6f), .3f, Gravity.START),
            LinearLayout.LayoutParams(0, -2, 1f))
        countText = label("30 CARTES", Style.jost(this, 200), 12f, Style.ivory(.6f), .14f, Gravity.END)
        hd.addView(countText, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(hd, LinearLayout.LayoutParams(-1, dp(30f)))

        grid = GridLayout(this).apply { columnCount = 5; useDefaultMargins = false }
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(grid) }
        col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

        search = EditText(this).apply {
            hint = "찾기 · RECHERCHE"
            setHintTextColor(Style.ivory(.42f)); setTextColor(Style.IVORY)
            typeface = Style.kr(this@MainActivity, 300); textSize = 14f
            setSingleLine(); background = null
            setPadding(dp(26f), 0, dp(4f), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onSearch(s?.toString() ?: "") }
            })
        }
        val sWrap = FrameLayout(this)
        sWrap.addView(search, FrameLayout.LayoutParams(-1, -1))
        sWrap.addView(View(this).apply { setBackgroundColor(Style.ivory(.3f)) }, FrameLayout.LayoutParams(-1, dp(1f), Gravity.BOTTOM))
        sWrap.addView(SearchGlyph(), FrameLayout.LayoutParams(dp(16f), dp(16f), Gravity.CENTER_VERTICAL).apply { leftMargin = dp(2f) })
        col.addView(sWrap, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(10f) })

        folio = label("31 · RUE CAMBON · Nº 10", Style.jost(this, 300), 9f, Style.ivory(.4f), .25f)
        folio.setOnClickListener { if (repo.unassigned().isNotEmpty()) showUnassigned() }
        col.addView(folio, LinearLayout.LayoutParams(-1, dp(22f)).apply { topMargin = dp(8f) })
    }

    private inner class SearchGlyph : View(this@MainActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dpf(1f); color = Style.ivory(.6f) }
        override fun onDraw(c: Canvas) {
            val r = width * .32f
            c.drawCircle(width * .42f, height * .42f, r, p)
            c.drawLine(width * .65f, height * .65f, width * .92f, height * .92f, p)
        }
    }

    /** 폴더 표지 30장을 다시 그림 */
    private fun refreshGrid() {
        if (!::grid.isInitialized) return
        grid.removeAllViews()
        val w = (resources.displayMetrics.widthPixels - dp(32f))
        val gap = dp(10f)
        val cover = ((w - gap * 4) / 5)
        val favs = repo.favorites()
        val badges = BadgeService.active
        val q = search.text?.toString()?.trim() ?: ""
        AppRepo.FOLDERS.forEachIndexed { i, name ->
            val inF = repo.appsIn(name)
            val v = folderCell(name, inF, cover, name in favs, inF.any { it.pkg in badges })
            if (q.isNotEmpty() && !name.contains(q) && inF.none { it.label.contains(q, true) }) v.alpha = .25f
            grid.addView(v, GridLayout.LayoutParams().apply {
                width = cover; height = cover + dp(38f)
                leftMargin = if (i % 5 == 0) 0 else gap
            })
        }
        countText.text = "30 CARTES"
        val un = repo.unassigned().size
        folio.text = if (un > 0) "31 · RUE CAMBON · 미분류 $un" else "31 · RUE CAMBON · Nº 10"
    }

    private fun folderCell(name: String, list: List<AppEntry>, size: Int, fav: Boolean, badge: Boolean): View {
        val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val cv = FrameLayout(this).apply {
            background = BitmapDrawable(resources, Style.img(this@MainActivity, "cover.webp"))
            foreground = GradientDrawable().apply { setStroke(dp(1f), if (fav) Style.GOLD else Style.ink(.08f)) }
            elevation = dpf(6f)
        }
        val ic = dp(24f); val pad = (size - ic * 2 - dp(6f)) / 2
        list.take(4).forEachIndexed { k, a ->
            val iv = ImageView(this).apply { setImageDrawable(a.icon) }
            cv.addView(iv, FrameLayout.LayoutParams(ic, ic).apply {
                leftMargin = pad + (k % 2) * (ic + dp(6f)); topMargin = pad + (k / 2) * (ic + dp(6f))
            })
        }
        if (badge) cv.addView(dot(), FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.END or Gravity.TOP))
        cell.addView(cv, LinearLayout.LayoutParams(size, size))
        val m = Regex("^(.+)\\((.+)\\)$").find(name)
        val t = label(m?.groupValues?.get(1) ?: name, Style.kr(this, 300), 11f, Style.ivory(.72f), 0f)
        cell.addView(t, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6f) })
        if (m != null) cell.addView(label(m.groupValues[2], Style.kr(this, 300), 9.5f, Style.ivory(.5f), 0f))
        cell.setOnClickListener { showFolder(name) }
        // 앱을 끌어 이 폴더에 놓기
        cell.setOnDragListener { _, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    cv.foreground = GradientDrawable().apply { setStroke(dp(1f), Style.GOLD, dpf(3f), dpf(2f)) }; true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    cv.foreground = GradientDrawable().apply { setStroke(dp(1f), if (fav) Style.GOLD else Style.ink(.08f)) }; true
                }
                DragEvent.ACTION_DROP -> {
                    val pkg = e.clipData?.getItemAt(0)?.text?.toString()
                    if (pkg != null) repo.move(pkg, name)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { cell.post { refreshGrid() }; true }
                else -> true
            }
        }
        cell.contentDescription = "$name 폴더, 앱 ${list.size}개"
        return cell
    }

    private fun dot() = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Style.ROUGE) }
        translationX = dpf(3f); translationY = -dpf(3f)
    }

    // ───────── ③ 카드 (폴더 열림) ─────────
    private fun buildCard() {
        card = FrameLayout(this).apply {
            background = BitmapDrawable(resources, Style.img(this@MainActivity, "card.webp"))
            foreground = InsetLine()
            elevation = dpf(24f)
            visibility = View.GONE
            isClickable = true
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(col, FrameLayout.LayoutParams(-1, -2))
        cardKicker = label("CARTE Nº 05", Style.jost(this, 400), 10f, Style.ink(.55f), .3f, Gravity.START)
        col.addView(cardKicker, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(30f); leftMargin = dp(26f) })
        cardTitle = label("금융", Style.kr(this, 300), 22f, Style.INK, 0f, Gravity.START)
        col.addView(cardTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f); leftMargin = dp(26f) })
        cardHint = label("길게 눌러 다른 카드로 옮기기", Style.kr(this, 300), 11f, Style.ink(.5f), 0f, Gravity.START)
        col.addView(cardHint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f); leftMargin = dp(26f) })
        cardApps = GridLayout(this).apply { columnCount = 4 }
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(cardApps) }
        col.addView(sv, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(14f) })
        val close = label("폴더 닫기", Style.kr(this, 400), 13f, Style.INK, .04f).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setStroke(dp(1f), Style.INK) }
            setOnClickListener { hideCard() }
        }
        col.addView(close, LinearLayout.LayoutParams(-1, dp(44f)).apply {
            leftMargin = dp(26f); rightMargin = dp(26f); topMargin = dp(12f); bottomMargin = dp(26f)
        })
        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            leftMargin = dp(16f); rightMargin = dp(16f); topMargin = dp(110f)
        })
    }

    /** 샤넬 편지지처럼 가장자리 6dp 안쪽의 검은 1dp 선 */
    private inner class InsetLine : android.graphics.drawable.Drawable() {
        private val p = Paint().apply { style = Paint.Style.STROKE; strokeWidth = dpf(1f); color = Style.INK }
        override fun draw(c: Canvas) {
            val i = dpf(6f)
            c.drawRect(bounds.left + i, bounds.top + i, bounds.right - i, bounds.bottom - i, p)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun showFolder(name: String) {
        cardFolder = name
        cardKicker.text = "CARTE Nº %02d".format(AppRepo.FOLDERS.indexOf(name) + 1)
        cardTitle.text = name
        cardHint.text = "길게 눌러 다른 카드로 옮기기"
        refreshCardApps()
        revealCard()
    }

    private fun showUnassigned() {
        cardFolder = "__unassigned"
        cardKicker.text = "NON CLASSÉ"
        cardTitle.text = "미분류"
        cardHint.text = "길게 눌러 넣을 카드 위에 놓으세요"
        refreshCardApps()
        revealCard()
    }

    private var searchList: List<AppEntry> = emptyList()
    private fun onSearch(q: String) {
        refreshGrid()
        if (q.isBlank()) { if (cardFolder == "__search") hideCard(); return }
        searchList = repo.search(q)
        cardFolder = "__search"
        cardKicker.text = "RECHERCHE"
        cardTitle.text = "‘$q’"
        cardHint.text = if (searchList.isEmpty()) "찾는 앱이 없습니다" else "앱 ${searchList.size}개"
        refreshCardApps()
        if (card.visibility != View.VISIBLE) revealCard()
    }

    private fun refreshCardApps() {
        val f = cardFolder ?: return
        val list = when (f) {
            "__unassigned" -> repo.unassigned()
            "__search" -> searchList.take(24)
            else -> repo.appsIn(f)
        }
        cardApps.removeAllViews()
        val badges = BadgeService.active
        list.forEach { a -> cardApps.addView(appCell(a, a.pkg in badges), GridLayout.LayoutParams().apply { width = dp(78f) }) }
    }

    private fun appCell(a: AppEntry, badge: Boolean): View {
        val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(4f), 0, dp(12f)) }
        val fr = FrameLayout(this)
        val iv = ImageView(this).apply { setImageDrawable(a.icon) }
        fr.addView(iv, FrameLayout.LayoutParams(dp(52f), dp(52f)))
        if (badge) fr.addView(dot(), FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.END or Gravity.TOP))
        cell.addView(fr, LinearLayout.LayoutParams(dp(52f), dp(52f)))
        val t = label(a.label, Style.kr(this, 300), 11f, Style.ink(.8f), 0f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        cell.addView(t, LinearLayout.LayoutParams(dp(72f), -2).apply { topMargin = dp(6f) })
        cell.setOnClickListener { repo.launch(a); closeAllLater() }
        cell.setOnLongClickListener {
            // 1.06배로 들어 손가락을 따라감 (흔들림 없음) → 카드를 내려놓고 폴더 위에 놓기
            val shadow = object : View.DragShadowBuilder(iv) {
                override fun onProvideShadowMetrics(size: Point, touch: Point) {
                    val s = (iv.width * 1.06f).toInt(); size.set(s, s); touch.set(s / 2, s / 2)
                }
                override fun onDrawShadow(c: Canvas) { c.scale(1.06f, 1.06f); iv.draw(c) }
            }
            iv.startDragAndDrop(ClipData.newPlainText("pkg", a.pkg), shadow, null, 0)
            hideCard(); hideKeyboard()
            true
        }
        return cell
    }

    private fun closeAllLater() { root.postDelayed({ closeAll() }, 350) }

    private fun revealCard() {
        card.visibility = View.VISIBLE
        if (reduceMotion) { card.alpha = 1f; return }
        card.alpha = 0f; card.scaleX = .94f; card.scaleY = .94f; card.translationY = dpf(30f)
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(280).setInterpolator(EASE).start()
    }

    private fun hideCard() {
        if (!::card.isInitialized) return
        cardFolder = null
        card.visibility = View.GONE
    }

    private fun hideKeyboard() {
        if (!::search.isInitialized) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }

    // ───────── 공통 ─────────
    private fun label(text: String, tf: android.graphics.Typeface, sp: Float, color: Int, spacing: Float, g: Int = Gravity.CENTER_HORIZONTAL) =
        TextView(this).apply {
            this.text = text; typeface = tf; textSize = sp; setTextColor(color); letterSpacing = spacing; gravity = g
            includeFontPadding = false
        }

    override fun onDestroy() {
        BadgeService.onChange = null
        super.onDestroy()
    }
}
