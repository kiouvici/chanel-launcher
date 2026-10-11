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
        const val HOME_URL = "https://kiouvici.synology.me:8123/dashboard-chanel/home"
        const val HOUSE_URL = "https://kiouvici.synology.me:8123/dashboard-chanel/house"

        /** HA 위쪽 메뉴줄 숨기기 — 홈·집 화면에서만 (하위 화면은 뒤로 가기 화살표가 필요해 그대로 둠) */
        const val HIDE_HEADER_JS = """
(function(){
 if(window.__mnsHide) return; window.__mnsHide=1;
 function q(r,s){return r&&r.shadowRoot&&r.shadowRoot.querySelector(s);}
 function run(){
  try{
   var root=q(q(q(document.querySelector('home-assistant'),'home-assistant-main'),'ha-panel-lovelace'),'hui-root');
   if(!root) return; var sr=root.shadowRoot;
   var p=location.pathname, top=/\/dashboard-chanel\/(home|house|0|1)\/?$/.test(p);
   var st=sr.getElementById('mnsHide');
   if(top&&!st){st=document.createElement('style');st.id='mnsHide';
     st.textContent='.header{display:none!important}#view{padding-top:0!important;min-height:100vh!important}';sr.appendChild(st);}
   if(!top&&st) st.remove();
  }catch(e){}
 }
 run(); setInterval(run,400);
})();
"""
        val EASE = PathInterpolator(.32f, .72f, 0f, 1f)
    }

    private lateinit var repo: AppRepo
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var webHouse: WebView
    private lateinit var pager: FrameLayout
    private lateinit var cover: FrameLayout
    private lateinit var pages: List<View>
    private lateinit var indicator: LinearLayout
    private lateinit var dateText: TextView
    private lateinit var progressTrack: FrameLayout
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
    private var cur = 0               // 0 표지 · 1 HA 홈 · 2 HA 집

    // ───────── 시작 ─────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepo(this)
        reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        root = FrameLayout(this).apply { setBackgroundColor(Style.INK) }
        setContentView(root)

        buildPages()
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

        // 화면이 꺼지면 표지로 돌려 둠 → 켤 때 항상 표지부터. 날짜는 매 분 확인
        registerReceiver(sysRx, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })

        BadgeService.onChange = { runOnUiThread { refreshGrid(); cardFolder?.let { refreshCardApps() } } }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    card.visibility == View.VISIBLE -> hideCard()
                    openP > 0f -> animateOpen(0f)
                    cur == 1 && web.canGoBack() -> web.goBack()
                    cur == 2 && webHouse.canGoBack() -> webHouse.goBack()
                    cur != 0 -> goPage(0)
                }
            }
        })
    }

    private val sysRx = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> { closeAllNow(); goPage(0, false) }
                else -> updateDate()
            }
        }
    }

    private fun closeAllNow() { hideCard(); if (::search.isInitialized) search.setText(""); anim?.cancel(); setOpen(0f) }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 홈 버튼: 열린 것만 닫는다 (서랍은 끌어올리기로만 연다 — 성훈님 결정)
        if (Intent.ACTION_MAIN == intent.action) {
            val wasClosed = openP == 0f && card.visibility != View.VISIBLE
            closeAll()
            if (wasClosed) goPage(0) // 홈 버튼 = 표지
        }
    }

    override fun onResume() {
        super.onResume()
        repo.reload()
        refreshGrid()
        updateDate()
        if (!pageLoaded) web.reload()
    }

    // ───────── 세 화면: 표지 · HA 홈 · HA 집 (옆으로 밀어 돌아감) ─────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWeb(url: String, isHome: Boolean): WebView = WebView(this).apply {
        setBackgroundColor(Style.INK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, p: Int) { if (isHome) setProgress(p / 100f) }
        }
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, u: String?) {
                view?.evaluateJavascript(HIDE_HEADER_JS, null)
                if (isHome) { pageLoaded = true; hideProgress() }
            }
            override fun onReceivedError(view: WebView?, req: WebResourceRequest?, err: WebResourceError?) {
                if (req?.isForMainFrame == true && isHome) { pageLoaded = false; showFail() }
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel(); if (isHome) { pageLoaded = false; showFail() }
            }
        }
        loadUrl(url)
    }

    private fun buildPages() {
        pager = Pager()
        root.addView(pager, FrameLayout.LayoutParams(-1, -1))
        cover = buildCover()
        web = makeWeb(HOME_URL, true)
        webHouse = makeWeb(HOUSE_URL, false)
        pages = listOf(cover, web, webHouse)
        pages.forEach { pager.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        indicator = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        repeat(3) { i ->
            indicator.addView(View(this).apply { setBackgroundColor(Style.IVORY) },
                LinearLayout.LayoutParams(dp(16f), dp(1f)).apply { if (i > 0) leftMargin = dp(8f) })
        }
        root.addView(indicator, FrameLayout.LayoutParams(-2, dp(9f), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
            bottomMargin = dp(124f)
        })
        root.post { layoutPages(0f) }
    }

    /** 표지: 닫힌 상자 뚜껑 + CHANEL · MONSIEUR + 날짜 한 줄 (+ 뒤에서 HA를 준비할 때만 금선) */
    private fun buildCover(): FrameLayout {
        val c = FrameLayout(this).apply { setBackgroundColor(Style.INK) }
        val w = resources.displayMetrics.widthPixels
        val lidH = (w * 1.5f).toInt()
        val top = dp(64f)
        val lid = ImageView(this).apply {
            setImageBitmap(Style.img(this@MainActivity, "lid.webp")); scaleType = ImageView.ScaleType.CENTER_CROP
        }
        c.addView(lid, FrameLayout.LayoutParams(-1, lidH).apply { topMargin = top })
        c.addView(label("CHANEL", Style.jost(this, 300), 22f, Style.IVORY, .5f),
            FrameLayout.LayoutParams(-1, -2).apply { topMargin = top + dp(150f) })
        c.addView(label("MONSIEUR", Style.jost(this, 400), 10f, Style.ivory(.6f), .3f),
            FrameLayout.LayoutParams(-1, -2).apply { topMargin = top + dp(190f) })
        dateText = label("", Style.jost(this, 300), 11f, Style.ivory(.6f), .3f)
        c.addView(dateText, FrameLayout.LayoutParams(-1, -2).apply { topMargin = top + lidH - dp(64f) })
        updateDate()

        progressTrack = FrameLayout(this).apply { setBackgroundColor(Style.gold(.2f)) }
        progress = View(this).apply { setBackgroundColor(Style.GOLD); pivotX = 0f; scaleX = 0f }
        progressTrack.addView(progress, FrameLayout.LayoutParams(-1, -1))
        c.addView(progressTrack, FrameLayout.LayoutParams(-1, dp(1f)).apply {
            topMargin = top + lidH + dp(24f); leftMargin = dp(16f); rightMargin = dp(16f)
        })

        failBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        failBox.addView(label("MAISON · 연결되지 않음", Style.kr(this, 300), 12f, Style.ivory(.7f), .05f))
        val retry = label("다시 연결", Style.kr(this, 400), 13f, Style.IVORY, .04f).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setStroke(dp(1f), Style.ivory(.8f)) }
            setOnClickListener {
                failBox.visibility = View.GONE; progressTrack.alpha = 1f; progressTrack.visibility = View.VISIBLE
                setProgress(0f); web.loadUrl(HOME_URL); webHouse.loadUrl(HOUSE_URL)
            }
        }
        failBox.addView(retry, LinearLayout.LayoutParams(dp(180f), dp(44f)).apply { topMargin = dp(10f) })
        c.addView(failBox, FrameLayout.LayoutParams(-1, -2).apply { topMargin = top + lidH + dp(12f) })
        return c
    }

    private fun updateDate() {
        if (!::dateText.isInitialized) return
        val cal = java.util.Calendar.getInstance()
        val days = arrayOf("DIMANCHE", "LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI")
        dateText.text = "%d · %d · %s".format(
            cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH) + 1,
            days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1])
    }

    private fun setProgress(p: Float) { if (::progress.isInitialized) progress.scaleX = p.coerceIn(0f, 1f) }
    private fun hideProgress() {
        if (!::progressTrack.isInitialized || progressTrack.visibility != View.VISIBLE) return
        progressTrack.animate().alpha(0f).setDuration(300).withEndAction { progressTrack.visibility = View.GONE }.start()
    }
    private fun showFail() {
        progressTrack.visibility = View.GONE
        failBox.visibility = View.VISIBLE
        goPage(0, false)
    }

    /** 지금 화면(cur)과 끌고 있는 만큼(dx)으로 세 장의 위치를 정함. 끝에서 처음으로 돌아감 */
    private fun layoutPages(dx: Float) {
        val w = pager.width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        pages.forEachIndexed { i, v ->
            val rel = when (i) {
                cur -> 0
                (cur + 1) % 3 -> 1
                else -> -1
            }
            v.translationX = rel * w + dx
            v.visibility = if (rel == 0 || (rel == 1 && dx < 0) || (rel == -1 && dx > 0)) View.VISIBLE else View.INVISIBLE
        }
        for (i in 0 until indicator.childCount) indicator.getChildAt(i).alpha = if (i == cur) 1f else .3f
    }

    private var pageAnim: ValueAnimator? = null
    private fun goPage(target: Int, animate: Boolean = true, fromDx: Float = 0f) {
        pageAnim?.cancel()
        val w = pager.width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        val n = ((target % 3) + 3) % 3
        if (!animate || n == cur && fromDx == 0f) { cur = n; layoutPages(0f); return }
        val dir = when (n) { (cur + 1) % 3 -> -1f; cur -> 0f; else -> 1f }
        if (reduceMotion) {
            cur = n; layoutPages(0f)
            pages[cur].alpha = 0f; pages[cur].animate().alpha(1f).setDuration(120).start(); return
        }
        pageAnim = ValueAnimator.ofFloat(fromDx, dir * w).apply {
            duration = 360; interpolator = EASE
            addUpdateListener { layoutPages(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { cur = n; layoutPages(0f) }
            })
            start()
        }
    }

    /** 가로로 민 것만 가로채서 화면을 넘김 (세로 스크롤은 HA에 그대로) */
    private inner class Pager : FrameLayout(this@MainActivity) {
        private var x0 = 0f; private var y0 = 0f; private var dragging = false
        private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        private var vt: android.view.VelocityTracker? = null
        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x0 = e.x; y0 = e.y; dragging = false; pageAnim?.cancel()
                    vt?.recycle(); vt = android.view.VelocityTracker.obtain(); vt?.addMovement(e) }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    val dx = e.x - x0; val dy = e.y - y0
                    if (kotlin.math.abs(dx) > slop * 2 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f) { dragging = true; x0 = e.x - dx; return true }
                }
            }
            return false
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            vt?.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.x; y0 = e.y; dragging = false; pageAnim?.cancel()
                    vt?.recycle(); vt = android.view.VelocityTracker.obtain(); vt?.addMovement(e)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.x - x0
                    if (!dragging && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(e.y - y0)) dragging = true
                    if (dragging) layoutPages(dx)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) { layoutPages(0f); return true }
                    val dx = e.x - x0
                    vt?.computeCurrentVelocity(1000); val v = vt?.xVelocity ?: 0f
                    val far = kotlin.math.abs(dx) > width * .25f || (kotlin.math.abs(v) > 1200 && kotlin.math.abs(dx) > slop)
                    dragging = false
                    if (far) goPage(if (dx < 0) cur + 1 else cur + 2, true, dx) else goBack(dx)
                    return true
                }
            }
            return true
        }
        private fun goBack(dx: Float) {
            if (reduceMotion || dx == 0f) { layoutPages(0f); return }
            pageAnim = ValueAnimator.ofFloat(dx, 0f).apply {
                duration = 240; interpolator = EASE
                addUpdateListener { layoutPages(it.animatedValue as Float) }; start()
            }
        }
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
        if (::indicator.isInitialized) indicator.alpha = 1f - (p * 3f).coerceAtMost(1f)
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
        // 화면이 꺼지면 표지로 돌려 둠 → 켤 때 항상 표지부터. 날짜는 매 분 확인
        registerReceiver(sysRx, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })

        BadgeService.onChange = null
        try { unregisterReceiver(sysRx) } catch (_: Exception) {}
        super.onDestroy()
    }
}
