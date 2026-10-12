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

class MainActivity : ComponentActivity(), MediaHost {

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
        /** HA 로그인 화면에서 '로그인 유지'를 자동으로 켜 둠 → 한 번 로그인하면 계속 유지 */
        const val KEEP_LOGIN_JS = """
(function(){
 function all(r,sel,out){out=out||[];if(!r)return out;r.querySelectorAll(sel).forEach(function(e){out.push(e)});
  r.querySelectorAll('*').forEach(function(e){if(e.shadowRoot)all(e.shadowRoot,sel,out)});return out;}
 var n=0,t=setInterval(function(){
  if(++n>60){clearInterval(t);return;}
  if(location.pathname.indexOf('/auth/')<0){clearInterval(t);return;}
  all(document,'ha-checkbox,mwc-checkbox,input[type=checkbox]').forEach(function(c){
   if(!c.checked&&!c.__mns){c.__mns=1;c.click();}
  });
 },500);
})();
"""
        val EASE = PathInterpolator(.32f, .72f, 0f, 1f)
    }

    internal lateinit var repo: AppRepo
    override lateinit var look: Look
    internal lateinit var root: FrameLayout
    internal lateinit var media: MediaPage
    internal lateinit var themeCover: ThemeCover
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
    private lateinit var band: FrameLayout
    internal var sbTop = 0           // 상태바 높이
    internal var navBot = 0          // 아래 시스템 제스처 영역 높이
    private var imeBot = 0          // 키보드 높이 (이름 바꾸기 시트를 키보드 위로)
    private var loginLayer: FrameLayout? = null
    private var lastLogin = 0L
    internal lateinit var box: FrameLayout
    internal lateinit var folio: TextView
    internal lateinit var settingsBtn: TextView
    internal lateinit var search: EditText
    internal lateinit var searchLine: View
    internal lateinit var card: FrameLayout
    internal lateinit var cardKicker: TextView
    internal lateinit var cardTitle: TextView
    internal lateinit var cardSub: TextView
    private lateinit var cardHint: TextView
    private lateinit var cardApps: GridLayout
    private lateinit var cardScroll: ScrollView
    private lateinit var cardScrim: View
    internal lateinit var stubPerf: View
    internal var cardSkin = Look.MONSIEUR
    // 테마 서랍 (Drawers.kt)
    internal lateinit var deco: FrameLayout
    internal lateinit var hdr: FrameLayout
    internal lateinit var col: LinearLayout
    internal lateinit var stage: ScrollView
    internal lateinit var stageIn: FrameLayout
    internal lateinit var rimShade: View
    internal lateinit var rim: View
    internal lateinit var tab: ImageView
    internal lateinit var grip: View
    internal var lidNow: TextView? = null
    internal var libSentence: TextView? = null
    internal var hourTime: TextView? = null
    private var searchGlyph: SearchGlyph? = null
    internal fun searchGlyphColor(c: Int) { searchGlyph?.color = c; searchGlyph?.invalidate() }
    internal var hourWx: TextView? = null
    internal var trainClock: TextView? = null
    internal var hourCenter: FrameLayout? = null
    internal var folioBase = "31 · RUE CAMBON · %s"
    internal var themeOpen: View? = null
    internal var collection: View? = null
    internal var collPlace: (() -> Unit)? = null
    internal var collChanged = false

    private var openP = 0f          // 0 닫힘 ~ 1 열림
    private var boxH = 0
    internal var reduceMotion = false
    private var pageLoaded = false
    internal var cardFolder: String? = null
    private var cur = 0               // 0 표지 · 1 미디어 · 2 HA 홈 · 3 HA 집
    private val N = 4

    // ───────── 시작 ─────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepo(this)
        look = Look(this)
        reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        root = FrameLayout(this).apply { setBackgroundColor(Style.INK) }
        setContentView(root)
        // 표지를 상태바 뒤까지 꽉 채우기 (샤넬팀): 화면 끝까지 그리고, 여백은 직접 계산
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Style.INK
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, ins ->
            val sb = ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val ime = ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val barsChanged = sb.top != sbTop || sb.bottom != navBot
            sbTop = sb.top; navBot = sb.bottom; imeBot = ime.bottom
            sheet?.let { placeSheet(it) }
            if (::card.isInitialized && card.visibility == View.VISIBLE && cardFolder == "__search") root.post { fitCard() }
            loginLayer?.setPadding(0, sbTop, 0, maxOf(navBot, ime.bottom))
            tripLayer?.setPadding(0, sbTop, 0, maxOf(navBot, ime.bottom))
            if (::box.isInitialized) box.setPadding(0, 0, 0, maxOf(navBot, ime.bottom))
            // 키보드만 오르내릴 때는 전체 재배치를 하지 않음
            if (barsChanged) root.post { applyInsets() }
            ins
        }

        buildPages()
        dim = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0f; visibility = View.GONE; setOnClickListener { closeAll() } }
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))
        buildBand()
        buildBox()
        buildCard()

        androidx.core.view.ViewCompat.requestApplyInsets(root)
        root.post { applyInsets(); setOpen(0f) }
        root.postDelayed({ shotIntent(intent) }, 1200)

        // 화면이 꺼지면 표지로 돌려 둠 → 켤 때 항상 표지부터. 날짜는 매 분 확인
        registerReceiver(sysRx, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })

        BadgeService.onChange = { runOnUiThread { if (openP > 0f) refreshBadges() else badgeDirty = true } }

        // 앱 설치·삭제·바뀜 때만 앱 목록을 다시 읽음 (뒤 스레드) — 예전엔 홈에 올 때마다 300개를 다시 읽었음
        registerReceiver(pkgRx, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    tripLayer != null -> { tripLayer?.let { root.removeView(it) }; tripLayer = null }
                    menuLayer != null -> hideMenu()
                    sheetBg != null -> hideSettingsSheet()
                    collection != null -> hideCollection()
                    pickFolder != null -> { pickFolder = null; cardFolder?.let { showFolder(it) } }
                    themeOpen != null -> hideCard()
                    card.visibility == View.VISIBLE -> hideCard()
                    openP > 0f -> animateOpen(0f)
                    cur == 2 && haBack(web, HOME_URL) -> {}
                    cur == 3 && haBack(webHouse, HOUSE_URL) -> {}
                    cur != 0 -> goPage(0)
                }
            }
        })
    }

    private val sysRx = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> { closeAllNow(); goPage(0, false); if (look.theme == Look.TRAIN) themeCover.advanceTrip() }
                else -> { updateDate(); refreshWeather(); themeCover.tick(); updateThemeClock() }
            }
        }
    }

    /** HA 화면 뒤로 가기: 하위 화면이면 한 단계 뒤로, 홈·집 맨 위면 표지로.
     *  뒤 기록이 로그인 화면이면 그리로 가지 않고 그 화면의 맨 위로 (뒤로 가기 → 로그인 화면이 뜨던 문제) */
    private fun haBack(w: WebView, topUrl: String): Boolean {
        val path = android.net.Uri.parse(w.url ?: "").path ?: ""
        if (Regex("/dashboard-chanel/(home|house|0|1)/?$").containsMatchIn(path)) return false
        val h = w.copyBackForwardList()
        val prev = if (h.currentIndex > 0) h.getItemAtIndex(h.currentIndex - 1)?.url ?: "" else ""
        when {
            prev.isEmpty() || prev.contains("/auth/") -> w.loadUrl(topUrl)
            else -> w.goBack()
        }
        return true
    }

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val pkgRx = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) { reloadAppsInBackground() }
    }
    private fun reloadAppsInBackground() {
        worker.execute {
            val list = try { repo.scan(true) } catch (e: Exception) { null }
            if (list != null) runOnUiThread { repo.setApps(list); refreshAll() }
        }
    }

    private var badgeDirty = false
    internal val gridDots = HashMap<String, View>()
    private val cardDots = HashMap<String, MutableList<View>>()
    /** 알림 점만 다시 켜고 끔 (서랍 전체를 다시 그리지 않음) */
    private fun refreshBadges() {
        badgeDirty = false
        val b = BadgeService.active
        gridDots.forEach { (id, v) -> v.visibility = if (repo.appsIn(id).any { it.pkg in b }) View.VISIBLE else View.GONE }
        cardDots.forEach { (pkg, vs) -> vs.forEach { it.visibility = if (pkg in b) View.VISIBLE else View.GONE } }
    }

    internal fun closeAllNow() { hideMenu(); hideSettingsSheet(); hideCollection(); hideCard(); if (::search.isInitialized) search.setText(""); anim?.cancel(); setOpen(0f) }

    /** 화면 점검용 (adb am start --ei theme 0~4 --ei page 0~3 --ez drawer true --ei folder n) — 사람이 누를 일은 없음 */
    private fun shotIntent(i: Intent?) {
        i ?: return
        val t = i.getIntExtra("theme", -1)
        if (t in 0..4 && t != look.theme) applyTheme(t)
        val pg = i.getIntExtra("page", -1)
        if (pg in 0..3) { closeAllNow(); goPage(pg, false) }
        if (i.hasExtra("demo")) media.demo = i.getIntExtra("demo", 0)
        when (i.getStringExtra("sheet")) {
            "dial" -> root.postDelayed({ setOpen(1f); choiceSheet(0) }, 400)
            "sound" -> root.postDelayed({ soundSheet(i.getIntExtra("demo", 1) - 1, H_GOLD) {} }, 400)
            "settings" -> root.postDelayed({ setOpen(1f); showSettingsSheet() }, 400)
            "display" -> root.postDelayed({ setOpen(1f); displaySheet() }, 400)
            "collection" -> root.postDelayed({ showCollection() }, 400)
        }
        if (i.getBooleanExtra("drawer", false)) root.postDelayed({
            setOpen(1f)
            val f = i.getIntExtra("folder", -1)
            if (f >= 0) root.postDelayed({ repo.folders().getOrNull(f)?.let { showFolder(it) } }, 500)
        }, 400)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra("theme") || intent.hasExtra("page") || intent.hasExtra("drawer") || intent.hasExtra("sheet") || intent.hasExtra("demo")) { root.postDelayed({ shotIntent(intent) }, 300); return }
        // 홈 버튼: 열린 것만 닫는다 (서랍은 끌어올리기로만 연다 — 성훈님 결정)
        if (Intent.ACTION_MAIN == intent.action) {
            val wasClosed = openP == 0f && card.visibility != View.VISIBLE
            closeAll()
            if (wasClosed) goPage(0) // 홈 버튼 = 표지
        }
    }

    override fun onResume() {
        super.onResume()
        refreshGrid()
        media.setShown(cur == 1)
        updateDate()
        root.postDelayed({ refreshWeather() }, 1500)
        if (!pageLoaded) web.reload()
    }

    /** 상태바·제스처 영역에 맞춰 자리 잡기 */
    private fun applyInsets() {
        if (root.height == 0 || !::box.isInitialized || !::band.isInitialized) return
        // 상태바·제스처 영역 높이를 직접 다시 읽음 (실기기에서 0으로 읽히던 문제 — 샤넬팀 검수)
        androidx.core.view.ViewCompat.getRootWindowInsets(root)?.let {
            val sb = it.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            if (sb.top > 0) sbTop = sb.top
            if (sb.bottom > 0) navBot = sb.bottom
        }
        if (sbTop == 0) {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) sbTop = resources.getDimensionPixelSize(id)
        }
        if (navBot == 0) {
            val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0) navBot = resources.getDimensionPixelSize(id)
        }
        listOf(web, webHouse).forEach { w ->
            (w.layoutParams as FrameLayout.LayoutParams).apply { topMargin = sbTop; bottomMargin = navBot + dp(24f) }
            w.requestLayout()
        }
        (band.layoutParams as FrameLayout.LayoutParams).bottomMargin = navBot; band.requestLayout()
        (indicator.layoutParams as FrameLayout.LayoutParams).bottomMargin = navBot + dp(8f); indicator.requestLayout()
        // 서랍은 화면 전체 (테마마다 위쪽을 직접 꾸밈 — MONSIEUR는 뚜껑 띠)
        boxH = root.height
        (box.layoutParams as FrameLayout.LayoutParams).apply { height = boxH; topMargin = 0 }
        box.requestLayout()
        themeCover.sbTop = sbTop; themeCover.navBot = navBot
        layoutCover()
        themeBox()
        setOpen(openP)
        refreshGrid()
    }

    override fun onPause() {
        super.onPause()
        media.setShown(false)
        CookieManager.getInstance().flush()
    }

    // ───────── HA 로그인 (처음 한 번) ─────────
    /** HA 화면이 로그인 페이지로 가면, 전체 화면 로그인 창을 띄움. 로그인 유지는 자동으로 켬 */
    @SuppressLint("SetJavaScriptEnabled")
    private fun showLogin() {
        if (loginLayer != null) return
        val layer = FrameLayout(this).apply { setBackgroundColor(Style.INK); elevation = dpf(40f); isClickable = true }
        val lw = WebView(this).apply {
            setBackgroundColor(Style.INK)
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.databaseEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, u: String?) {
                    val url = u ?: ""
                    if (url.contains("/auth/")) view?.evaluateJavascript(KEEP_LOGIN_JS, null)
                    else if (url.contains("/dashboard-chanel")) waitTokens(view, 0)
                }
            }
            loadUrl(HOME_URL)
        }
        layer.addView(lw, FrameLayout.LayoutParams(-1, -1))
        layer.setPadding(0, sbTop, 0, navBot)
        root.addView(layer, FrameLayout.LayoutParams(-1, -1))
        loginLayer = layer
    }

    /** 로그인 직후 HA가 토큰을 저장할 때까지 기다렸다가(최대 30초) 창을 닫음.
     *  예전에는 0.6초 만에 닫아서 토큰이 저장되기 전에 창이 사라져 로그인이 무한 반복됐음 */
    private fun waitTokens(view: WebView?, n: Int) {
        view ?: return
        view.evaluateJavascript("(function(){try{return localStorage.getItem('hassTokens')?'1':'0'}catch(e){return '0'}})()") { r ->
            if (r?.contains("1") == true) {
                CookieManager.getInstance().flush()
                lastLogin = System.currentTimeMillis()
                hideLogin(); web.loadUrl(HOME_URL); webHouse.loadUrl(HOUSE_URL)
            } else if (n < 60) root.postDelayed({ waitTokens(view, n + 1) }, 500)
            // 30초가 지나도 저장이 안 되면 창을 그대로 둠 (반복 방지)
        }
    }

    private fun hideLogin() {
        val l = loginLayer ?: return
        root.removeView(l)
        (l.getChildAt(0) as? WebView)?.destroy()
        loginLayer = null
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
                if ((u ?: "").contains("/auth/")) {
                    // 방금 로그인했으면 다시 띄우지 않고 한 번 더 불러오기만 (토큰 반영 지연 대비)
                    if (System.currentTimeMillis() - lastLogin < 15000) { view?.let { v -> v.postDelayed({ v.loadUrl(if (isHome) HOME_URL else HOUSE_URL) }, 1500) }; return }
                    showLogin(); return
                }
                view?.evaluateJavascript(HIDE_HEADER_JS, null)
                // 로그인 전 거쳐 간 로그인 화면을 기록에서 지움 → 뒤로 가기로 로그인 화면이 다시 뜨지 않게
                view?.let { v ->
                    val h = v.copyBackForwardList()
                    if ((0 until h.size).any { (h.getItemAtIndex(it)?.url ?: "").contains("/auth/") }) v.clearHistory()
                }
                if (isHome) { pageLoaded = true; hideProgress(); root.postDelayed({ refreshWeather() }, 4000) }
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
        media = MediaPage(this, this)
        web = makeWeb(HOME_URL, true)
        webHouse = makeWeb(HOUSE_URL, false)
        pages = listOf(cover, media, web, webHouse)
        pages.forEach { pager.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        indicator = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        repeat(N) { i ->
            indicator.addView(View(this).apply { setBackgroundColor(Style.IVORY) },
                LinearLayout.LayoutParams(dp(16f), dp(1f)).apply { if (i > 0) leftMargin = dp(8f) })
        }
        root.addView(indicator, FrameLayout.LayoutParams(-2, dp(9f), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM))
        root.post { layoutPages(0f) }
    }

    /** 표지: 상태바 뒤까지 꽉 찬 상자 뚜껑 + CHANEL · MONSIEUR + 날짜 (+ 뒤에서 HA를 준비할 때만 금선) */
    private lateinit var wordmark: TextView
    private lateinit var frame: View
    private lateinit var clock: TextView
    private lateinit var weather: TextView
    private lateinit var monsieur: TextView
    private fun buildCover(): FrameLayout {
        val c = FrameLayout(this).apply { setBackgroundColor(Style.INK) }
        val lid = ImageView(this).apply {
            setImageBitmap(Style.img(this@MainActivity, "lid.webp")); scaleType = ImageView.ScaleType.CENTER_CROP
        }
        c.addView(lid, FrameLayout.LayoutParams(-1, -1))
        // 상태바 아이콘이 보이도록 위쪽 그늘 (#0A0A0A 40% → 0%)
        c.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Style.ink(.4f), Style.ink(0f)))
        }, FrameLayout.LayoutParams(-1, dp(72f)))
        wordmark = label("AIHERMES", Style.jost(this, 300), 22f, Style.IVORY, .5f)
        c.addView(wordmark, FrameLayout.LayoutParams(-1, -2))
        monsieur = label("MONSIEUR", Style.jost(this, 400), 10f, Style.ivory(.6f), .3f)
        c.addView(monsieur, FrameLayout.LayoutParams(-1, -2))
        dateText = label("", Style.jost(this, 300), 11f, Style.ivory(.6f), .3f)
        c.addView(dateText, FrameLayout.LayoutParams(-1, -2))
        // 시계: 휴대폰 시각, Jost 200 64sp, 숫자 폭 고정 (샤넬팀 6:1)
        clock = label("", Style.jost(this, 200), 64f, Style.ivory(.9f), .02f).apply { fontFeatureSettings = "tnum" }
        c.addView(clock, FrameLayout.LayoutParams(-1, -2))
        // 날씨 한 줄: CHANEL 홈에 실제로 보이는 문장을 그대로 (HA가 끊기면 숨김)
        weather = label("", Style.kr(this, 300), 13f, Style.ivory(.6f), .1f).apply { visibility = View.INVISIBLE }
        c.addView(weather, FrameLayout.LayoutParams(-1, -2))
        // 테두리: 사진 속 테두리는 잘라 내고 코드로 (1dp 아이보리 40%, 각진 모서리)
        frame = View(this).apply { background = GradientDrawable().apply { setStroke(dp(1f), Style.ivory(.4f)) } }
        c.addView(frame, FrameLayout.LayoutParams(-1, -1))
        updateDate()
        // MONSIEUR 외 네 테마의 표지는 이 층이 덮어 그림 (HA 준비 금선·다시 연결은 그 위)
        themeCover = ThemeCover(this, look).apply { onPhoto = { pickPhotos() }; onTrip = { tripEditor() } }
        c.addView(themeCover, FrameLayout.LayoutParams(-1, -1))

        progressTrack = FrameLayout(this).apply { setBackgroundColor(Style.gold(.2f)) }
        progress = View(this).apply { setBackgroundColor(Style.GOLD); pivotX = 0f; scaleX = 0f }
        progressTrack.addView(progress, FrameLayout.LayoutParams(-1, -1))
        c.addView(progressTrack, FrameLayout.LayoutParams(-1, dp(1f)).apply { leftMargin = dp(16f); rightMargin = dp(16f) })

        failBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        failBox.addView(label("HOME · 연결되지 않음", Style.kr(this, 300), 12f, Style.ivory(.7f), .05f))
        val retry = label("다시 연결", Style.kr(this, 400), 13f, Style.IVORY, .04f).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setStroke(dp(1f), Style.ivory(.8f)) }
            setOnClickListener {
                failBox.visibility = View.GONE; progressTrack.alpha = 1f; progressTrack.visibility = View.VISIBLE
                setProgress(0f); web.loadUrl(HOME_URL); webHouse.loadUrl(HOUSE_URL)
            }
        }
        failBox.addView(retry, LinearLayout.LayoutParams(dp(180f), dp(44f)).apply { topMargin = dp(10f) })
        c.addView(failBox, FrameLayout.LayoutParams(-1, -2))
        return c
    }

    /** 화면 높이에 맞춰 자리 잡기 — 사진 속 리본은 화면 높이 61.8%~70.6% (테두리를 잘라 낸 사진 기준) */
    private fun layoutCover() {
        val h = root.height.takeIf { it > 0 } ?: return
        val ribbonTop = (h * .618f).toInt()
        val ribbonBottom = (h * .706f).toInt()
        (frame.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = dp(16f); rightMargin = dp(16f); topMargin = sbTop + dp(12f); bottomMargin = navBot + dp(28f)
        }
        (wordmark.layoutParams as FrameLayout.LayoutParams).topMargin = (h * .28f).toInt()
        wordmark.post {
            // 글자 상자가 아니라 실제 글자 윗선·밑선 기준으로 간격을 맞춤
            val wTop = (h * .28f).toInt()
            val wBase = wTop + wordmark.baseline
            (monsieur.layoutParams as FrameLayout.LayoutParams).topMargin = wBase + dp(16f) - capGap(monsieur, "M")
            monsieur.requestLayout()
            (dateText.layoutParams as FrameLayout.LayoutParams).topMargin = ribbonTop - dp(24f) - dateText.baseline
            dateText.requestLayout()
            val cTop = ribbonBottom + dp(32f) - capGap(clock, "0")
            (clock.layoutParams as FrameLayout.LayoutParams).topMargin = cTop
            clock.requestLayout()
            (weather.layoutParams as FrameLayout.LayoutParams).topMargin = cTop + clock.baseline + dp(12f) - capGap(weather, "2")
            weather.requestLayout()
        }
        // HA 준비 금선·다시 연결은 테두리 안쪽 맨 아래
        val inBottom = h - navBot - dp(28f)
        (progressTrack.layoutParams as FrameLayout.LayoutParams).apply { topMargin = inBottom - dp(16f); leftMargin = dp(32f); rightMargin = dp(32f) }
        // CAPTION은 아래에 사진 설명이 있어 '다시 연결'을 위로
        (failBox.layoutParams as FrameLayout.LayoutParams).topMargin = if (look.theme == Look.CAPTION) sbTop + dp(64f) else inBottom - dp(96f)
        listOf(frame, wordmark, clock, progressTrack, failBox).forEach { it.requestLayout() }
    }

    /** 글자 상자 맨 위에서 실제 글자 윗선까지의 거리 */
    private fun capGap(tv: TextView, sample: String): Int {
        val r = android.graphics.Rect(); tv.paint.getTextBounds(sample, 0, sample.length, r)
        return (tv.baseline + r.top).coerceAtLeast(0)
    }

    /** CHANEL 홈 화면(뒤에 열어 둔 웹뷰)에 실제로 보이는 날씨 문장을 읽어 옴. 연결이 끊겼으면 숨김 */
    private fun refreshWeather() {
        if (!::web.isInitialized || !::weather.isInitialized) return
        val js = """(function(){try{
 var ha=document.querySelector('home-assistant'); if(!ha||!ha.hass||!ha.hass.connected) return '';
 var hit='';
 (function walk(r){ if(hit) return; r.querySelectorAll('*').forEach(function(e){ if(e.shadowRoot) walk(e.shadowRoot); });
   var tw=document.createTreeWalker(r,NodeFilter.SHOW_TEXT),n;
   while(!hit&&(n=tw.nextNode())){ var t=(n.textContent||'').trim(); if(/^-?\d+(\.\d+)?°\s*·/.test(t)&&t.length<60&&t.indexOf('›')<0) hit=t; } })(document);
 var st=ha.hass.states['weather.naver_weather_jeceonsi_jangragdong_nalssi_jeceonsi_jangragdong'];
 var o={line:hit,state:'',temp:null};
 if(st){ o.state=st.state||''; if(st.attributes&&st.attributes.temperature!=null) o.temp=st.attributes.temperature;
   // 화면에 아직 안 그려졌으면: 대시보드와 같은 네이버 날씨 값으로 기온·상태만
   if(!hit&&o.temp!=null){ var c=ha.hass.formatEntityState?ha.hass.formatEntityState(st):st.state; o.line=Math.round(o.temp)+'° · '+c; } }
 return o.line?JSON.stringify(o):''; }catch(e){ return ''; }})()"""
        web.evaluateJavascript(js) { r ->
            val raw = try { org.json.JSONArray("[" + (r ?: "\"\"") + "]").optString(0, "") } catch (e: Exception) { "" }
            val o = try { if (raw.startsWith("{")) org.json.JSONObject(raw) else null } catch (e: Exception) { null }
            val t = o?.optString("line", "") ?: ""
            if (t.isBlank() || t == "null") { weather.visibility = View.INVISIBLE; return@evaluateJavascript }
            weather.text = styledWeather(t)
            weather.visibility = View.VISIBLE
            // 테마 표지·서랍에도 같은 날씨 (LIBRARY는 영어 문장으로)
            themeCover.weatherLine = t
            themeCover.weatherState = o?.optString("state", "") ?: ""
            themeCover.weatherTemp = o?.takeIf { !it.isNull("temp") }?.optDouble("temp")?.takeIf { !it.isNaN() }
            themeCover.tick(); updateThemeClock()
        }
    }

    /** 숫자는 Jost 300, 글자는 Noto Sans KR 300 */
    private fun styledWeather(t: String): CharSequence {
        val sp = android.text.SpannableString(t)
        Regex("[0-9°.-]+").findAll(t).forEach {
            sp.setSpan(android.text.style.TypefaceSpan(Style.jost(this, 300)), it.range.first, it.range.last + 1, 0)
        }
        return sp
    }

    private fun updateDate() {
        if (!::dateText.isInitialized || !::clock.isInitialized) return
        val cal = java.util.Calendar.getInstance()
        val days = arrayOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
        clock.text = "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        // 월 · 일 · 요일 (영어 전환 — 샤넬팀 6:1)
        // '요일 · 일 월' — '10 · 11'이 날짜인지 시각인지 헷갈리던 것 (샤넬팀 build-29 검수)
        val months = arrayOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")
        dateText.text = "%s · %d %s".format(days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1],
            cal.get(java.util.Calendar.DAY_OF_MONTH), months[cal.get(java.util.Calendar.MONTH)])
    }

    private fun setProgress(p: Float) { if (::progress.isInitialized) progress.scaleX = p.coerceIn(0f, 1f) }
    private fun hideProgress() {
        if (!::progressTrack.isInitialized || progressTrack.visibility != View.VISIBLE) return
        progressTrack.animate().alpha(0f).setDuration(300).withEndAction { progressTrack.visibility = View.GONE }.start()
    }
    private fun showFail() {
        progressTrack.visibility = View.GONE
        // 연결 안 됨 상자는 MONSIEUR 표지에만 (다른 테마는 사진·종이 위에 떠 보여서 — 샤넬팀)
        failBox.visibility = if (look.theme == Look.MONSIEUR) View.VISIBLE else View.GONE
        goPage(0, false)
    }

    /** 지금 화면(cur)과 끌고 있는 만큼(dx)으로 세 장의 위치를 정함. 끝에서 처음으로 돌아감 */
    private fun layoutPages(dx: Float) {
        val w = pager.width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        pages.forEachIndexed { i, v ->
            val rel = when (i) {
                cur -> 0
                (cur + 1) % N -> 1
                (cur + N - 1) % N -> -1
                else -> 2
            }
            v.translationX = rel * w + dx
            // HA 화면은 늘 그려 둠(화면 밖으로 밀어 둘 뿐) — 숨기면 웹뷰가 그리기를 멈춰 날씨 문장을 못 읽음
            val near = rel == 0 || (rel == 1 && dx < 0) || (rel == -1 && dx > 0)
            v.visibility = if (v is WebView || near) View.VISIBLE else View.INVISIBLE
        }
        // 페이지 점: LIBRARY 종이 위(표지·미디어)에서는 먹색 — 아이보리 점이 종이에 묻히던 것
        val inkDots = look.theme == Look.LIBRARY && cur <= 1
        for (i in 0 until indicator.childCount) indicator.getChildAt(i).apply {
            setBackgroundColor(if (inkDots) (if (i == cur) 0xFF2B2520.toInt() else 0xFF8A7E6E.toInt()) else Style.IVORY)
            alpha = if (inkDots || i == cur) 1f else .3f
            (layoutParams as LinearLayout.LayoutParams).let { lp -> val h = if (inkDots) dp(1.5f) else dp(1f); if (lp.height != h) { lp.height = h; layoutParams = lp } }
        }
        // 아래 리본 한 줄은 HA 화면에서만 (HA 화면이 보이는 만큼)
        if (::band.isInitialized) {
            fun shown(v: View) = if (v.visibility == View.VISIBLE) (1f - kotlin.math.abs(v.translationX) / w).coerceIn(0f, 1f) else 0f
            band.alpha = maxOf(shown(web), shown(webHouse))
            band.visibility = if (band.alpha > .01f) View.VISIBLE else View.INVISIBLE
        }
        if (dx == 0f) { media.setShown(cur == 1); updateBars() }
    }

    /** 밝은 바탕(LIBRARY 표지·미디어)에서는 상태바 글자를 검정으로 */
    private var barsLight: Boolean? = null
    internal fun updateBars() {
        val light = if (openP > .5f) look.theme == Look.LIBRARY
            else themeOpen == null && ((cur == 0 && themeCover.isLight) || (cur == 1 && media.isLight))
        if (light == barsLight) return
        barsLight = light
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = light
        android.util.Log.d("MNS", "bars light=$light cur=$cur theme=${look.theme}")
    }

    private var pageAnim: ValueAnimator? = null
    internal fun goPage(target: Int, animate: Boolean = true, fromDx: Float = 0f) {
        pageAnim?.cancel()
        val w = pager.width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        val n = ((target % N) + N) % N
        if (!animate || n == cur && fromDx == 0f) { cur = n; layoutPages(0f); return }
        if (n != cur && n != (cur + 1) % N && n != (cur + N - 1) % N) { cur = n; layoutPages(0f); return }   // 두 장 건너뛰기는 바로
        val dir = when (n) { (cur + 1) % N -> -1f; cur -> 0f; else -> 1f }
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
        private var vertical = false; private var p0 = 0f; private var shadeDone = false
        private var tDown = 0L; private var yDown = 0f
        private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        private var vt: android.view.VelocityTracker? = null
        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x0 = e.x; y0 = e.y; tDown = e.eventTime; yDown = e.y; dragging = false; vertical = false; shadeDone = false; p0 = openP; pageAnim?.cancel()
                    vt?.recycle(); vt = android.view.VelocityTracker.obtain(); vt?.addMovement(e) }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e)
                    val dx = e.x - x0; val dy = e.y - y0
                    if (kotlin.math.abs(dx) > slop * 2 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f) { dragging = true; x0 = e.x - dx; return true }
                    // 표지 위 사진·버튼을 누른 채 위로 쓸어도 서랍이 열림 — 더 예민하게(12dp, 세로가 가로보다 크면) (10번에 2번 안 되던 것)
                    if ((cur == 0 || cur == 1) && -dy > dpf(12f) && kotlin.math.abs(dy) > kotlin.math.abs(dx)) { vertical = true; y0 = e.y - dy; return true }
                    // 아래로 쓸면 아이폰처럼: 왼쪽 절반 알림 · 오른쪽 절반 빠른 설정. HA 화면은 위쪽 100dp에서 시작할 때만 (HA 안 스크롤과 안 겹치게)
                    if (!shadeDone && dy > dpf(28f) && dy > kotlin.math.abs(dx) * 1.5f && (cur == 0 || cur == 1 || y0 < dpf(100f))) { shadeDone = true; expandShade(x0 > width / 2f); return true }
                }
            }
            return false
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            vt?.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.x; y0 = e.y; tDown = e.eventTime; yDown = e.y; dragging = false; vertical = false; shadeDone = false; p0 = openP; pageAnim?.cancel()
                    vt?.recycle(); vt = android.view.VelocityTracker.obtain(); vt?.addMovement(e)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.x - x0; val dy = e.y - y0
                    // 표지에서는 위로 쓸어 올리면 어디서든 앱 상자가 열림
                    if (!dragging && !vertical && (cur == 0 || cur == 1) && -dy > slop && kotlin.math.abs(dy) > kotlin.math.abs(dx)) vertical = true
                    if (!dragging && !vertical && !shadeDone && dy > dpf(28f) && dy > kotlin.math.abs(dx) * 1.5f && (cur == 0 || cur == 1 || y0 < dpf(100f))) { shadeDone = true; expandShade(x0 > width / 2f); return true }
                    if (shadeDone) return true
                    if (vertical) { setOpen((p0 + (-dy) / boxH.coerceAtLeast(1)).coerceIn(0f, 1f)); return true }
                    if (!dragging && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) dragging = true
                    if (dragging) layoutPages(dx)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (vertical) {
                        vertical = false
                        vt?.computeCurrentVelocity(1000); val vy = vt?.yVelocity ?: 0f
                        // 빠르게 짧게 튕긴 것도 열림: 속도 추적이 0으로 나올 때가 있어(10번에 2번) 누른 시점부터의 거리·시간으로도 판단
                        val up = yDown - e.y; val dt = (e.eventTime - tDown).coerceAtLeast(1)
                        val flick = up > dpf(32f) && dt < 350
                        animateOpen(if (openP > .15f || vy < -dpf(500f) || flick) 1f else 0f); return true
                    }
                    if (!dragging) { layoutPages(0f); return true }
                    val dx = e.x - x0
                    vt?.computeCurrentVelocity(1000); val v = vt?.xVelocity ?: 0f
                    val far = kotlin.math.abs(dx) > width * .25f || (kotlin.math.abs(v) > 1200 && kotlin.math.abs(dx) > slop)
                    dragging = false
                    if (far) goPage(if (dx < 0) cur + 1 else cur + N - 1, true, dx) else goBack(dx)
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

    // ───────── HA 화면 아래 리본 한 줄 (샤넬팀 b안) ─────────
    @SuppressLint("ClickableViewAccessibility")
    private fun buildBand() {
        band = FrameLayout(this).apply { setBackgroundColor(Style.INK); contentDescription = "앱 상자 열기" }
        band.addView(View(this).apply { setBackgroundColor(Style.ivory(.5f)) }, FrameLayout.LayoutParams(-1, dp(1.5f), Gravity.TOP))
        root.addView(band, FrameLayout.LayoutParams(-1, dp(24f), Gravity.BOTTOM))
        band.setOnTouchListener(DragOpen(fromOpen = false))
        indicator.bringToFront()
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
                    animateOpen(if (fromOpen) (if (openP < .85f) 0f else 1f) else (if (openP > .35f) 1f else 0f))
                    return true
                }
            }
            return false
        }
    }

    private fun setOpen(p: Float) {
        val wasOpen = openP > 0f
        openP = p
        box.translationY = (1f - p) * boxH
        box.visibility = if (p > 0f) View.VISIBLE else View.INVISIBLE
        dim.visibility = if (p > 0f) View.VISIBLE else View.GONE
        dim.alpha = p * .6f   // 서랍 위로 보이는 표지를 60% 어둡게 (샤넬팀)
        if (::indicator.isInitialized) indicator.alpha = 1f - (p * 3f).coerceAtMost(1f)
        // 서랍이 조금이라도 열리면 표지 테두리를 숨김 (서랍 위로 비치던 문제 — 샤넬팀 7:0)
        if (::frame.isInitialized) frame.visibility = if (p > 0f) View.INVISIBLE else View.VISIBLE
        // 상자를 '닫을 때만' 키보드를 내림. (예전에는 닫혀 있을 때마다 내려서,
        //  로그인 창에서 키보드가 뜨면 화면 재배치 → 곧바로 키보드가 내려가는 문제가 있었음)
        if (p == 0f && wasOpen) hideKeyboard()
        if (p > 0f && !wasOpen && badgeDirty) refreshBadges()
        updateBars()
    }

    private var anim: ValueAnimator? = null
    private fun animateOpen(target: Float) {
        anim?.cancel()
        if (reduceMotion) { setOpen(target); return }
        anim = ValueAnimator.ofFloat(openP, target).apply {
            duration = if (target == 0f) 360 else 420; interpolator = EASE
            addUpdateListener { setOpen(it.animatedValue as Float) }
            start()
        }
    }

    private fun closeAll() {
        hideMenu()
        hideCollection()
        hideSettingsSheet()
        hideCard()
        search.setText("")
        animateOpen(0f)
    }

    /** 상자 안 어디서든 아래로 크게 끌면 닫힘 (목록이 맨 위일 때) — 30% 또는 빠른 튕김 */
    private inner class BoxLayout : FrameLayout(this@MainActivity) {
        private var y0 = 0f; private var x0 = 0f; private var drag = false
        private var tDown = 0L; private var yDown = 0f
        private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        private var vt: android.view.VelocityTracker? = null
        /** 아래로 끌어 닫기 시작할지: 위쪽 300dp에서 시작했거나 목록이 맨 위일 때, 세로로 slop(약 8dp) 넘게 */
        private fun startDrag(e: MotionEvent): Boolean {
            val dy = e.rawY - y0
            val atTop = !::stage.isInitialized || stage.scrollY == 0 || (y0 - box.top - box.translationY) < dpf(300f)
            if (atTop && card.visibility != View.VISIBLE && themeOpen == null && dy > slop && dy > kotlin.math.abs(e.rawX - x0)) {
                drag = true; y0 = e.rawY - dy; hideKeyboard(); return true
            }
            return false
        }
        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { y0 = e.rawY; x0 = e.rawX; yDown = e.rawY; tDown = e.eventTime; drag = false
                    vt?.recycle(); vt = android.view.VelocityTracker.obtain(); vt?.addMovement(e) }
                MotionEvent.ACTION_MOVE -> { vt?.addMovement(e); if (startDrag(e)) return true }
            }
            return false
        }
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            // 빈 자리(맨 위 여백 등)를 잡고 끌 때는 가로채기가 불리지 않아 반응이 없던 것 — 여기서도 시작
            if (!drag) {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) { y0 = e.rawY; x0 = e.rawX; yDown = e.rawY; tDown = e.eventTime; vt?.recycle(); vt = android.view.VelocityTracker.obtain(); vt?.addMovement(e) }
                if (e.actionMasked == MotionEvent.ACTION_MOVE) { vt?.addMovement(e); if (startDrag(e)) return true }
                super.onTouchEvent(e); return true
            }
            vt?.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> setOpen((1f - (e.rawY - y0) / boxH.coerceAtLeast(1)).coerceIn(0f, 1f))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    drag = false
                    vt?.computeCurrentVelocity(1000); val vy = vt?.yVelocity ?: 0f
                    // 15%만 끌어 내리거나 살짝 튕기면(600dp/s) 닫힘 — 예전 30%·1000dp/s는 너무 많이 끌어야 했음
                    val down = e.rawY - yDown; val dt = (e.eventTime - tDown).coerceAtLeast(1)
                    animateOpen(if (openP < .85f || vy > dpf(600f) || (down > dpf(32f) && dt < 350)) 0f else 1f)
                }
            }
            return true
        }
    }

    // ───────── ② 상자 안 (서랍) — 테마마다 꾸밈은 Drawers.kt ─────────
    @SuppressLint("ClickableViewAccessibility")
    private fun buildBox() {
        box = BoxLayout().apply {
            background = android.graphics.drawable.LayerDrawable(arrayOf(
                android.graphics.drawable.ColorDrawable(Style.INK), Style.tile(this@MainActivity, Style.GROSGRAIN, 256f)))
            isClickable = true
            visibility = View.INVISIBLE
            elevation = dpf(8f)
        }
        root.addView(box, FrameLayout.LayoutParams(-1, -1))
        deco = FrameLayout(this)
        box.addView(deco, FrameLayout.LayoutParams(-1, -1))

        // MONSIEUR: 평평한 검정 가장자리 28dp + 아이보리 테두리선 + 가운데 리본 당김 탭
        rimShade = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(140, 0, 0, 0), Color.TRANSPARENT))
        }
        box.addView(rimShade, FrameLayout.LayoutParams(-1, dp(20f)).apply { topMargin = dp(29f) })
        rim = View(this).apply { background = RimDrawable(); elevation = dpf(2f) }
        box.addView(rim, FrameLayout.LayoutParams(-1, dp(29f)))
        tab = ImageView(this).apply {
            setImageDrawable(Style.tabDrawable(this@MainActivity)); scaleType = ImageView.ScaleType.FIT_XY
            elevation = dpf(4f); outlineSpotShadowColor = Color.argb(128, 0, 0, 0)
        }
        val real = Style.has(this, "tab.webp")
        box.addView(tab, FrameLayout.LayoutParams(dp(if (real) 60f else 44f), dp(if (real) 20f else 24f), Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(if (real) 20f else 18f) })
        grip = View(this).apply { contentDescription = "서랍 닫기"; elevation = dpf(5f) }
        box.addView(grip, FrameLayout.LayoutParams(-1, dp(56f)))
        val drag = DragOpen(fromOpen = true)
        grip.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> tab.animate().translationY(dpf(2f)).translationZ(dpf(2f)).setDuration(120).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tab.animate().translationY(0f).translationZ(0f).setDuration(120).start()
            }
            drag.onTouch(v, e)
        }

        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(col, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(40f); leftMargin = dp(16f); rightMargin = dp(16f) })
        hdr = FrameLayout(this)
        col.addView(hdr, LinearLayout.LayoutParams(-1, dp(40f)))
        stageIn = FrameLayout(this)
        stage = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; clipChildren = false; addView(stageIn, FrameLayout.LayoutParams(-1, -2)) }
        col.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        stage.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, oRight, ob -> if (r - l != oRight - ol || b - t != ob - ot) stage.post { buildStage() } }

        // 검색: 금 밑줄, 금 돋보기
        search = EditText(this).apply {
            hint = "SEARCH · 찾기"
            setHintTextColor(Style.ivory(.6f)); setTextColor(Style.IVORY)
            typeface = Style.jost(this@MainActivity, 300); textSize = 13f; letterSpacing = .08f
            setSingleLine(); background = null
            setPadding(dp(28f), 0, dp(4f), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onSearch(s?.toString() ?: "") }
            })
        }
        val sWrap = FrameLayout(this)
        sWrap.addView(search, FrameLayout.LayoutParams(-1, -1))
        searchLine = View(this).apply { setBackgroundColor(Style.gold(.4f)) }
        sWrap.addView(searchLine, FrameLayout.LayoutParams(-1, hair(.75f), Gravity.BOTTOM))
        sWrap.addView(SearchGlyph().also { searchGlyph = it }, FrameLayout.LayoutParams(dp(16f), dp(16f), Gravity.CENTER_VERTICAL).apply { leftMargin = dp(2f) })
        col.addView(sWrap, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(8f) })

        // 폴리오(왼쪽) + SETTINGS(오른쪽), 위에 아이보리 8% 선
        val footWrap = FrameLayout(this)
        footWrap.addView(View(this).apply { setBackgroundColor(Style.ivory(.08f)) }, FrameLayout.LayoutParams(-1, hair(.5f), Gravity.TOP))
        val foot = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        folio = label("31 · RUE CAMBON · 30 FOLDERS", Style.jost(this, 300), 9f, Style.ivory(.6f), .25f, Gravity.START or Gravity.CENTER_VERTICAL)
        folio.setOnClickListener { if (repo.unassigned().isNotEmpty()) showUnassigned() }
        folio.maxLines = 1; folio.ellipsize = android.text.TextUtils.TruncateAt.END
        foot.addView(folio, LinearLayout.LayoutParams(0, -1, 1f))
        settingsBtn = label("SETTINGS", Style.jost(this, 300), 9f, Style.ivory(.6f), .25f, Gravity.END or Gravity.CENTER_VERTICAL)
        settingsBtn.contentDescription = "설정"
        settingsBtn.setOnClickListener { showSettingsSheet() }
        foot.addView(settingsBtn, LinearLayout.LayoutParams(dp(104f), -1))
        footWrap.addView(foot, FrameLayout.LayoutParams(-1, -1))
        col.addView(footWrap, LinearLayout.LayoutParams(-1, dp(44f)).apply { bottomMargin = dp(4f) })
    }

    /** 0.5dp 같은 가는 선 — 최소 1px */
    internal fun hair(v: Float) = dpf(v).toInt().coerceAtLeast(1)

    /** 맨 위 가장자리: #0A0A0A + 위 아이보리 12% 0.5dp + 아래 아이보리 70% 1dp 테두리선 */
    private inner class RimDrawable : android.graphics.drawable.Drawable() {
        private val p = Paint()
        override fun draw(c: Canvas) {
            val b = bounds
            p.color = Style.INK; c.drawRect(b, p)
            p.color = Style.ivory(.12f); c.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + dpf(.5f), p)
            p.color = Style.ivory(.7f); c.drawRect(b.left.toFloat(), b.bottom - dpf(1f), b.right.toFloat(), b.bottom.toFloat(), p)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
    }

    internal inner class SearchGlyph : View(this@MainActivity) {
        var color = Style.gold(.7f)
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dpf(1f) }
        override fun onDraw(c: Canvas) {
            p.color = color
            val r = width * .32f
            c.drawCircle(width * .42f, height * .42f, r, p)
            c.drawLine(width * .65f, height * .65f, width * .92f, height * .92f, p)
        }
    }

    /** 서랍을 다시 그림 (테마별 배치는 Drawers.kt의 buildStage) */
    internal fun refreshGrid() {
        if (!::stage.isInitialized) return
        buildStage()
    }

    /** '쇼핑(온라인)' → '쇼핑·온라인' 한 줄 */
    internal fun shortName(name: String) = Regex("^(.+)\\((.+)\\)$").replace(name, "$1·$2")

    /** 카드 테두리: 3dp 안쪽 0.75dp 검은 선 (편지지 bordure noire) + 자주 여는 폴더는 바깥 1.5dp 금박 */
    internal inner class CardLine(private val fav: Boolean, private val dashed: Boolean = false) : android.graphics.drawable.Drawable() {
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dpf(.75f); color = Style.INK }
        private val gilt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dpf(1.5f) }
        override fun draw(c: Canvas) {
            val b = bounds; val i = dpf(3f)
            c.drawRect(b.left + i, b.top + i, b.right - i, b.bottom - i, ink)
            if (fav || dashed) {
                val h = dpf(.75f)
                gilt.shader = android.graphics.LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                    intArrayOf(0xFF8A6A2E.toInt(), 0xFFE2C78E.toInt(), 0xFFB8914A.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                gilt.pathEffect = if (dashed) android.graphics.DashPathEffect(floatArrayOf(dpf(3f), dpf(2f)), 0f) else null
                c.drawRect(b.left + h, b.top + h, b.right - h, b.bottom - h, gilt)
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** ① MONSIEUR 편지 카드 (68×78 기준, 크기는 화면에 맞춤) */
    internal fun folderCell(index: Int, id: String, list: List<AppEntry>, w: Int, h: Int, fav: Boolean, badge: Boolean): View {
        val cv = FrameLayout(this).apply {
            background = Style.tile(this@MainActivity, Style.PAPER, 220f)
            foreground = CardLine(fav)
            elevation = dpf(3f)
            outlineSpotShadowColor = Color.argb(153, 0, 0, 0); outlineAmbientShadowColor = Color.argb(90, 0, 0, 0)
        }
        val k = (w / dpf(68f)).coerceIn(.8f, 1.3f)
        // 번호 01~30 (잉크 70% — 샤넬팀 4차)
        cv.addView(label("%02d".format(index + 1), Style.jost(this, 400), 8f, Style.ink(.7f), .15f, Gravity.START),
            FrameLayout.LayoutParams(-2, -2).apply { leftMargin = dp(7f); topMargin = dp(6f) })
        // 미니 아이콘 2×2
        val ic = (dpf(20f) * k).toInt(); val g = (dpf(4f) * k).toInt()
        val left = (w - ic * 2 - g) / 2
        val iconTop = if (h < dp(88f)) dp(16f) else dp(20f)
        repo.coverApps(id).forEachIndexed { i, a ->
            val iv = ImageView(this).apply { setImageDrawable(iconOf(a)); styleIcon(this) }
            cv.addView(iv, FrameLayout.LayoutParams(ic, ic).apply {
                leftMargin = left + (i % 2) * (ic + g); topMargin = iconTop + (i / 2) * (ic + g)
            })
        }
        val gd = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Style.ROUGE) }
            visibility = if (badge) View.VISIBLE else View.GONE
        }
        cv.addView(gd, FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.END or Gravity.TOP).apply { topMargin = dp(6f); rightMargin = dp(6f) })
        gridDots[id] = gd
        if (h >= dp(88f)) cv.addView(View(this).apply { setBackgroundColor(Style.ink(.12f)) },
            FrameLayout.LayoutParams(dp(28f), hair(.5f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(26f) })
        val nm = shortName(repo.nameOf(id))
        val t = label(nm, Style.kr(this, 400), (if (nm.length <= 6) 11f else 10f) + look.nameDelta, look.nameColorOn(true, Style.ink(.9f)), -.02f).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            if (paint.measureText(nm) > w - dp(6f)) textSize = 9f + look.nameDelta
        }
        cv.addView(t, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(3f); rightMargin = dp(3f); bottomMargin = dp(6f) })
        bindFolder(cv, id) { on -> cv.foreground = CardLine(fav, dashed = on) }
        return cv
    }

    private fun dot() = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Style.ROUGE) }
        translationX = dpf(3f); translationY = -dpf(3f)
    }

    // ───────── 아이콘 모양: COLOR / MONO / GILT (샤넬팀 6:1) ─────────
    private val monoFilter by lazy {
        val c = 1.1f; val b = .92f
        val m = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        val k = c * b; val off = (1f - c) * 128f * b
        m.postConcat(android.graphics.ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, off, 0f, k, 0f, 0f, off, 0f, 0f, k, 0f, off, 0f, 0f, 0f, 1f, 0f)))
        android.graphics.ColorMatrixColorFilter(m)
    }
    /** 흑백으로 바꾼 뒤 어두운 곳 #4A3818 ~ 밝은 곳 #E2C78E 금빛 두 색 */
    private val giltFilter by lazy {
        fun row(lo: Int, hi: Int): FloatArray { val k = (hi - lo) / 255f; return floatArrayOf(.299f * k, .587f * k, .114f * k, 0f, lo.toFloat()) }
        val r = row(0x4A, 0xE2); val g = row(0x38, 0xC7); val b = row(0x18, 0x8E)
        android.graphics.ColorMatrixColorFilter(floatArrayOf(*r, *g, *b, 0f, 0f, 0f, 1f, 0f))
    }
    /** 앱마다 아이콘을 따로 복사해 씀 — 같은 아이콘을 여러 칸이 나눠 쓰면 한 칸에 입힌 MONO·GILT 색이
     *  다른 칸에도 남아, COLOR로 돌아와도 폴더 표지 색이 안 바뀌던 문제 */
    internal fun iconOf(a: AppEntry): android.graphics.drawable.Drawable =
        BitmapDrawable(resources, a.bmp).apply { isFilterBitmap = true }

    internal fun styleIcon(iv: ImageView) {
        when (repo.iconMode) {
            AppRepo.ICON_MONO -> { iv.colorFilter = monoFilter; iv.imageAlpha = 217 }
            AppRepo.ICON_GILT -> { iv.colorFilter = giltFilter; iv.imageAlpha = 255 }
            else -> { iv.clearColorFilter(); iv.imageAlpha = 255 }
        }
    }

    // ───────── ③ 카드 (폴더 열림) ─────────
    internal lateinit var closeBtn: TextView
    private lateinit var cardEmpty: TextView
    private lateinit var pickBar: LinearLayout
    private var pickFolder: String? = null          // 표지 앱 고르기 중인 폴더
    private val pickSel = ArrayList<String>()

    internal lateinit var cardNum: TextView

    /** 열린 폴더 = 꺼내 든 편지 카드 (샤넬팀 B안): 8dp 안쪽 1.5dp 검은 테두리, 큰 금 번호, CLOSE 검정 채움 */
    private fun buildCard() {
        card = FrameLayout(this).apply {
            background = Style.tile(this@MainActivity, Style.PAPER, 256f)
            foreground = InsetLine(8f, 1.5f)
            elevation = dpf(24f)
            visibility = View.GONE
            isClickable = true
        }
        val cc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(28f), dp(28f), dp(28f), dp(28f)) }
        card.addView(cc, FrameLayout.LayoutParams(-1, -2))
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = true }
        cardNum = label("04", Style.jost(this, 200), 44f, Style.GILT, 0f, Gravity.START)
        head.addView(cardNum, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6f) })
        cardKicker = label("FOLDER", Style.jost(this, 400), 9f, Style.ink(.5f), .35f, Gravity.START)
        head.addView(cardKicker, LinearLayout.LayoutParams(-2, -2))
        cc.addView(head, LinearLayout.LayoutParams(-1, -2))
        cardTitle = label("금융", Style.kr(this, 300), 24f, Style.INK, 0f, Gravity.START)
        cc.addView(cardTitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })
        cardSub = label("", Fonts.serifI(this), 16f, Style.ink(.6f), 0f, Gravity.START).apply { visibility = View.GONE }
        cc.addView(cardSub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f) })
        cc.addView(View(this).apply { setBackgroundColor(Style.ink(.15f)) }, LinearLayout.LayoutParams(-1, hair(.5f)).apply { topMargin = dp(14f) })
        cardHint = label("", Style.kr(this, 300), 11f, Style.ink(.65f), 0f, Gravity.START)
        cc.addView(cardHint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
        cardApps = GridLayout(this).apply { columnCount = 4 }
        cardScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(cardApps) }
        cc.addView(cardScroll, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(18f) })
        cardEmpty = label("비어 있습니다 · 다른 폴더의 앱을 길게 눌러 끌어 오세요", Style.kr(this, 300), 12f, Style.ink(.65f), 0f, Gravity.START).apply { visibility = View.GONE }
        cc.addView(cardEmpty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f); bottomMargin = dp(8f) })
        closeBtn = label("CLOSE · 닫기", Style.jost(this, 400), 12f, Style.IVORY, .2f).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Style.INK)
            setOnClickListener { hideCard() }
        }
        // TRAIN 승차권: 닫기 위 절취선
        stubPerf = View(this).apply { background = Perf(NAVY, dpf(3f), dpf(14f)); visibility = View.GONE }
        cc.addView(stubPerf, LinearLayout.LayoutParams(-1, dp(10f)).apply { topMargin = dp(20f); leftMargin = -dp(28f); rightMargin = -dp(28f) })
        cc.addView(closeBtn, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(20f) })
        // 표지 앱 고르기: 취소 · 저장(검정 채움 + 금 안쪽 선)
        pickBar = buttonPair("취소", "저장", true, { pickFolder = null; cardFolder?.let { showFolder(it) } }, goldInner = true) {
            val f = pickFolder ?: return@buttonPair
            repo.setCover(f, pickSel.toList()); pickFolder = null; refreshGrid(); showFolder(f)
        }.apply { visibility = View.GONE }
        cc.addView(pickBar, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(20f) })
        // 카드 바깥(상자)을 누르면 카드가 닫힘 — 뒤는 #000 45%
        cardScrim = View(this).apply { setBackgroundColor(Color.argb(115, 0, 0, 0)); elevation = dpf(23f); visibility = View.GONE; setOnClickListener { hideCard() } }
        root.addView(cardScrim, FrameLayout.LayoutParams(-1, -1))
        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            leftMargin = dp(16f); rightMargin = dp(16f); topMargin = dp(110f)
        })
    }

    /** 사각 버튼 두 개: 왼쪽 1dp 검정 테두리, 오른쪽 검정 채움(또는 금 테두리). goldInner면 검정 채움 안쪽에 금 1dp 선 */
    private fun buttonPair(left: String, right: String, rightFilled: Boolean, onLeft: () -> Unit, goldInner: Boolean = false, onRight: () -> Unit): LinearLayout {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val l = label(left, Style.kr(this, 400), 13f, Style.INK, .04f).apply {
            gravity = Gravity.CENTER; background = GradientDrawable().apply { setStroke(dp(1f), Style.INK) }
            setOnClickListener { onLeft() }
        }
        val r = label(right, Style.kr(this, 400), 13f, if (rightFilled) Style.IVORY else Style.INK, .04f).apply {
            gravity = Gravity.CENTER
            background = if (rightFilled && goldInner) android.graphics.drawable.LayerDrawable(arrayOf(
                    GradientDrawable().apply { setColor(Style.INK) },
                    GradientDrawable().apply { setStroke(dp(1f), Style.GOLD) })).apply { setLayerInset(1, dp(3f), dp(3f), dp(3f), dp(3f)) }
                else GradientDrawable().apply { if (rightFilled) setColor(Style.INK) else setStroke(dp(1f), Style.GOLD) }
            setOnClickListener { onRight() }
        }
        bar.addView(l, LinearLayout.LayoutParams(0, -1, 1f))
        bar.addView(r, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(10f) })
        return bar
    }

    /** 샤넬 편지지처럼 가장자리 안쪽의 검은 선 (inset·두께 dp) */
    private inner class InsetLine(private val insetDp: Float = 6f, widthDp: Float = 1f) : android.graphics.drawable.Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dpf(widthDp); color = Style.INK }
        override fun draw(c: Canvas) {
            val i = dpf(insetDp)
            c.drawRect(bounds.left + i, bounds.top + i, bounds.right - i, bounds.bottom - i, p)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** 메뉴 줄을 누르고 있을 때: 검정 4% 바탕 + 왼쪽 금 2dp */
    private fun pressedBg(): android.graphics.drawable.StateListDrawable {
        val on = android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.ColorDrawable(Style.ink(.04f)), android.graphics.drawable.ColorDrawable(Style.GOLD))).apply {
            setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL); setLayerWidth(1, dp(2f)); setLayerInsetTop(1, dp(8f)); setLayerInsetBottom(1, dp(8f))
        }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), on)
            addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
    }

    internal fun showFolder(id: String) {
        // HOUR · CAPTION은 카드 대신 화면 전체로 엶
        when (look.theme) {
            Look.HOUR -> { hideCardOnly(); showHourOpen(id); return }
            Look.LIBRARY -> { hideCardOnly(); showLibraryOpen(id); return }
            Look.CAPTION -> { hideCardOnly(); showCaptionOpen(id); return }
            Look.TRAIN -> { hideCardOnly(); showVoyageOpen(id); return }
        }
        hideThemeOpen()
        val wasOpen = card.visibility == View.VISIBLE && cardFolder == id
        cardFolder = id
        skinCard(look.theme)
        val no = repo.folders().indexOf(id) + 1
        val n = repo.appsIn(id).size
        cardNum.text = "%02d".format(no)
        cardKicker.text = when (look.theme) { Look.LIBRARY -> "VOL. %02d".format(no); Look.TRAIN -> "PLATFORM %02d · $n APPS".format(no); else -> "FOLDER" }
        cardSub.text = "$n apps"
        cardTitle.text = shortName(repo.nameOf(id))
        cardHint.text = "길게 눌러 메뉴 · 끌어서 다른 폴더로 옮기기"
        cardHint.visibility = if (repo.takeHint()) View.VISIBLE else View.GONE
        setPickUi(false)
        refreshCardApps()
        if (wasOpen) fitCard() else revealCard()
    }

    /** 표지 앱 고르기 모드 */
    private fun startPick(id: String) {
        hideThemeOpen(); skinCard(Look.MONSIEUR)
        pickFolder = id
        pickSel.clear(); pickSel.addAll(repo.customCover(id))
        cardFolder = id
        cardTitle.text = repo.nameOf(id)
        cardHint.text = "표지에 놓을 앱을 순서대로 누르세요 · 최대 4개"
        cardHint.visibility = View.VISIBLE
        setPickUi(true)
        updatePickKicker()
        refreshCardApps()
        if (card.visibility == View.VISIBLE) fitCard() else revealCard()
    }

    private fun setPickUi(on: Boolean) {
        if (!on) pickFolder = null
        closeBtn.visibility = if (on) View.GONE else View.VISIBLE
        pickBar.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun updatePickKicker() { cardKicker.text = "COVER · ${pickSel.size}/4" }

    internal fun showUnassigned() {
        hideThemeOpen(); skinCard(Look.MONSIEUR)
        cardFolder = "__unassigned"
        setPickUi(false)
        cardKicker.setTextColor(Style.ink(.5f))
        cardNum.visibility = View.GONE
        cardKicker.text = "UNSORTED"
        cardTitle.text = "미분류"
        cardHint.text = "길게 눌러 끌어서 넣을 폴더 위에 놓으세요"
        cardHint.visibility = View.VISIBLE
        refreshCardApps()
        revealCard()
    }

    private var searchList: List<AppEntry> = emptyList()
    private fun onSearch(q: String) {
        refreshGrid()
        if (q.isBlank()) { if (cardFolder == "__search") hideCard(); return }
        searchList = repo.search(q)
        hideThemeOpen(); skinCard(Look.MONSIEUR)
        cardFolder = "__search"
        setPickUi(false)
        cardKicker.setTextColor(Style.ink(.5f))
        cardNum.visibility = View.GONE
        cardKicker.text = "SEARCH"
        cardTitle.text = "‘$q’"
        cardHint.text = if (searchList.isEmpty()) "찾는 앱이 없습니다" else "앱 ${searchList.size}개"
        cardHint.visibility = View.VISIBLE
        refreshCardApps()
        if (card.visibility != View.VISIBLE) revealCard() else fitCard()
    }

    private fun refreshCardApps() {
        val f = cardFolder ?: return
        val list = when (f) {
            "__unassigned" -> repo.unassigned()
            "__search" -> searchList
            else -> repo.appsIn(f)
        }
        cardApps.removeAllViews()
        cardDots.clear()
        cardEmpty.visibility = if (list.isEmpty() && !f.startsWith("__")) View.VISIBLE else View.GONE
        val badges = BadgeService.active
        val src = if (f.startsWith("__")) null else f
        val cw = dp(look.iconDp + 26f)
        // LIBRARY 펼친 책: 2열 + 가운데 접힘 + 2열
        val book = cardSkin == Look.LIBRARY
        val cols = if (look.iconDp > 56f && !book) 3 else 4
        cardApps.columnCount = cols
        list.forEachIndexed { i, a -> cardApps.addView(appCell(a, a.pkg in badges, src), GridLayout.LayoutParams().apply {
            width = if (book) dp(look.iconDp + 18f) else cw
            if (book && i % 4 == 2) leftMargin = dp(36f)
        }) }
    }

    /** 앱 한 칸: 누르면 실행 · 길게 누른 채 8dp 넘게 끌면 옮기기 · 길게 누르고 그냥 떼면 메뉴 (샤넬팀 7:0) */
    @SuppressLint("ClickableViewAccessibility")
    internal fun appCell(a: AppEntry, badge: Boolean, src: String?, compact: Boolean = false, onDark: Boolean = false, row: Boolean = false): View {
        // 아이콘 크기는 SETTINGS · DISPLAY (작게 44 · 기본 52 · 크게 60)
        val icDp = when { row -> 40f; compact -> look.iconDp * 44f / 52f; else -> look.iconDp }
        val ic = dp(icDp)
        val cell = LinearLayout(this).apply {
            if (row) { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(52f); background = UnderLine(Style.ivory(.1f), dpf(.5f), 0f) }
            else { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(2f), 0, if (compact) 0 else dp(16f)) }
        }
        val fr = FrameLayout(this)
        val iv = ImageView(this).apply { setImageDrawable(iconOf(a)); styleIcon(this) }
        fr.addView(iv, FrameLayout.LayoutParams(ic, ic))
        val cd = dot().apply { visibility = if (badge) View.VISIBLE else View.GONE }
        fr.addView(cd, FrameLayout.LayoutParams(dp(6f), dp(6f), Gravity.END or Gravity.TOP))
        cardDots.getOrPut(a.pkg) { ArrayList() }.add(cd)
        // 표지 앱 고르기: 오른쪽 위 18dp 칸 (고른 앱은 금 바탕에 순서 숫자)
        val pf = pickFolder
        if (pf != null && src == pf) {
            val n = pickSel.indexOf(a.pkg)
            val boxv = label(if (n >= 0) "${n + 1}" else "", Style.jost(this, 400), 10f, Style.IVORY, 0f).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { if (n >= 0) setColor(Style.GOLD) else { setColor(Style.IVORY); setStroke(dp(1f), Style.ink(.4f)) } }
                elevation = dpf(2f)
            }
            fr.addView(boxv, FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.END or Gravity.TOP).apply { topMargin = dp(2f); rightMargin = dp(2f) })
        }
        cell.addView(fr, LinearLayout.LayoutParams(ic, ic))
        val nameSp = (if (row) 13f else if (compact) 10f else 11f) + look.nameDelta
        val nameCol = look.nameColorOn(!onDark, if (onDark) Style.ivory(.82f) else Style.ink(.8f))
        val t = label(a.label, Style.kr(this, 300), nameSp, nameCol, 0f, if (row) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            if (onDark && compact) setShadowLayer(dpf(2f), 0f, dpf(1f), Color.argb(200, 0, 0, 0))
        }
        if (row) cell.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12f) })
        else cell.addView(t, LinearLayout.LayoutParams(if (compact) dp(64f) else maxOf(dp(72f), ic + dp(18f)), -2).apply { topMargin = dp(if (compact) 4f else 6f) })
        if (repo.isHidden(a.pkg)) cell.alpha = .5f   // 숨긴 앱은 검색에서 흐리게

        if (pf != null && src == pf) {
            cell.setOnClickListener {
                val i = pickSel.indexOf(a.pkg)
                when {
                    i >= 0 -> pickSel.removeAt(i)
                    pickSel.size >= 4 -> {
                        cardKicker.setTextColor(Style.GOLD)
                        cardKicker.postDelayed({ cardKicker.setTextColor(Style.ink(.5f)) }, 200)
                        return@setOnClickListener
                    }
                    else -> pickSel.add(a.pkg)
                }
                updatePickKicker(); refreshCardApps()
            }
            return cell
        }

        val longTime = android.view.ViewConfiguration.getLongPressTimeout().toLong()
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        var x0 = 0f; var y0 = 0f; var longed = false; var dragged = false; var moved = false
        val onLong = Runnable {
            longed = true
            cell.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            cell.parent?.requestDisallowInterceptTouchEvent(true)
            iv.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
        }
        cell.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.rawX; y0 = e.rawY; longed = false; dragged = false; moved = false
                    cell.postDelayed(onLong, longTime); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val d = kotlin.math.hypot(e.rawX - x0, e.rawY - y0)
                    if (!longed && d > slop) { moved = true; cell.removeCallbacks(onLong) }
                    if (longed && !dragged && d > dp(8f)) {
                        dragged = true
                        iv.scaleX = 1f; iv.scaleY = 1f
                        // 1.06배로 들어 손가락을 따라감 (흔들림 없음) → 카드를 내려놓고 폴더 위에 놓기
                        val shadow = object : View.DragShadowBuilder(iv) {
                            override fun onProvideShadowMetrics(size: Point, touch: Point) {
                                val s = (iv.width * 1.06f).toInt(); size.set(s, s); touch.set(s / 2, s / 2)
                            }
                            override fun onDrawShadow(c: Canvas) { c.scale(1.06f, 1.06f); iv.draw(c) }
                        }
                        iv.startDragAndDrop(ClipData.newPlainText("pkg", a.pkg + "\n" + (src ?: "")), shadow, null, 0)
                        hideCard(); hideKeyboard()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    cell.removeCallbacks(onLong)
                    iv.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    when {
                        longed && !dragged -> appMenu(cell, a, src)
                        !longed && !moved -> { repo.launch(a, src); closeAllLater() }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cell.removeCallbacks(onLong); iv.scaleX = 1f; iv.scaleY = 1f; true
                }
                else -> true
            }
        }
        return cell
    }

    private fun closeAllLater() { root.postDelayed({ closeAll() }, 350) }

    /** 앱이 많아 카드가 화면보다 길면, 카드를 화면 안에 맞추고 앱 목록만 스크롤 — '폴더 닫기'가 늘 보이게 */
    private fun fitCard() {
        val minTop = sbTop + dp(64f)
        // 엄지 자리: 테마별 높이에 놓고, 길면 위로 올림 (샤넬팀 4차)
        val search = cardFolder == "__search"
        val bottomGap = if (search) maxOf(navBot, imeBot) + dp(120f) else navBot + dp(24f)
        (card.layoutParams as FrameLayout.LayoutParams).height = FrameLayout.LayoutParams.WRAP_CONTENT
        (cardScroll.layoutParams as LinearLayout.LayoutParams).apply { height = LinearLayout.LayoutParams.WRAP_CONTENT; weight = 0f }
        (card.getChildAt(0).layoutParams as FrameLayout.LayoutParams).height = FrameLayout.LayoutParams.WRAP_CONTENT
        card.measure(View.MeasureSpec.makeMeasureSpec(root.width - dp(32f), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val want = if (search || cardFolder?.startsWith("__") == true) minTop else maxOf(minTop, cardTopFor(cardSkin))
        val top = if (want + card.measuredHeight > root.height - bottomGap) maxOf(minTop, root.height - bottomGap - card.measuredHeight) else want
        val avail = root.height - top - bottomGap
        (card.layoutParams as FrameLayout.LayoutParams).topMargin = top
        if (card.measuredHeight > avail) {
            (card.layoutParams as FrameLayout.LayoutParams).height = avail
            (card.getChildAt(0).layoutParams as FrameLayout.LayoutParams).height = FrameLayout.LayoutParams.MATCH_PARENT
            (cardScroll.layoutParams as LinearLayout.LayoutParams).apply { height = 0; weight = 1f }
        }
        card.requestLayout()
    }

    private fun revealCard() {
        fitCard()
        cardScroll.scrollTo(0, 0)
        cardScrim.visibility = View.VISIBLE
        card.visibility = View.VISIBLE
        if (reduceMotion) { card.alpha = 1f; return }
        card.alpha = 0f; card.scaleX = .94f; card.scaleY = .94f; card.translationY = dpf(30f)
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(280).setInterpolator(EASE).start()
    }

    /** 카드만 닫음 (테마 화면은 그대로) */
    private fun hideCardOnly() { if (::card.isInitialized) { card.visibility = View.GONE; cardScrim.visibility = View.GONE } }

    internal fun hideCard() {
        if (!::card.isInitialized) return
        hideThemeOpen()
        val wasSearch = cardFolder == "__search"
        cardFolder = null
        if (wasSearch && ::search.isInitialized && !search.text.isNullOrEmpty()) search.setText("")
        pickFolder = null
        card.visibility = View.GONE
        if (::cardScrim.isInitialized) cardScrim.visibility = View.GONE
    }

    /** 앱·폴더를 바꾼 뒤 서랍과 열린 카드를 다시 그림 */
    private fun refreshAll() {
        refreshGrid()
        val f = cardFolder ?: return
        when {
            f == "__search" -> { searchList = repo.search(search.text?.toString() ?: ""); refreshCardApps(); fitCard() }
            f == "__unassigned" -> { refreshCardApps(); fitCard() }
            repo.folders().contains(f) -> if (themeOpen != null) showFolder(f) else { cardTitle.text = repo.nameOf(f); refreshCardApps(); fitCard() }
            else -> hideCard()
        }
    }

    /** 열린 폴더가 떠 있는 동안 서랍의 검색줄·하단 문구를 숨김 (리본·닫기와 겹치지 않게) */
    internal fun footVisible(on: Boolean) {
        listOf(search.parent as? View, folio.parent?.parent as? View).forEach { it?.visibility = if (on) View.VISIBLE else View.INVISIBLE }
    }

    /** 알림창(false) · 빠른 설정(true) 내리기 — 런처들이 쓰는 상태바 기능 (EXPAND_STATUS_BAR) */
    @SuppressLint("WrongConstant")
    internal fun expandShade(settings: Boolean) {
        try {
            val sb = getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod(if (settings) "expandSettingsPanel" else "expandNotificationsPanel").invoke(sb)
        } catch (e: Throwable) { android.util.Log.d("MNS", "shade fail $e") }
    }

    internal fun hideKeyboardFor(v: View) { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0) }

    // ───────── THE VOYAGE 여행 사진 (최대 6장 · 앱 안에 복사) ─────────
    internal var tripLayer: FrameLayout? = null
    internal var tripPicked: ((List<Pair<String, String>>) -> Unit)? = null
    private val tripPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        val room = Look.MAX_PHOTOS - look.trips.size.coerceAtMost(Look.MAX_PHOTOS)
        val pick = uris.take(maxOf(1, room))
        worker.execute {
            val got = pick.mapNotNull { Trips.importPhoto(this, it) }
            runOnUiThread { tripPicked?.invoke(got) }
        }
    }
    internal fun pickTrips() { try { tripPicker.launch(arrayOf("image/*")) } catch (_: Exception) {} }

    private fun hideKeyboard() {
        if (!::search.isInitialized) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }

    // ───────── 길게 누르기 메뉴 카드 (아이보리 240dp, 한 줄 48dp, 모서리 0) ─────────
    private var menuLayer: FrameLayout? = null

    private fun showMenu(anchor: View, items: List<Pair<String, () -> Unit>>) {
        hideMenu()
        val layer = FrameLayout(this).apply { elevation = dpf(36f); isClickable = true; setOnClickListener { hideMenu() } }
        val m = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Style.tile(this@MainActivity, Style.PAPER, 256f); foreground = InsetLine(4f, .75f)
            elevation = dpf(12f); isClickable = true
        }
        items.forEachIndexed { i, (text, go) ->
            if (i > 0) m.addView(View(this).apply { setBackgroundColor(Style.ink(.1f)) }, LinearLayout.LayoutParams(-1, hair(.5f)).apply { leftMargin = dp(4f); rightMargin = dp(4f) })
            val t = label(text, Style.kr(this, 300), 14f, Style.INK, 0f, Gravity.START or Gravity.CENTER_VERTICAL).apply {
                setPadding(dp(22f), 0, dp(20f), 0); background = pressedBg()
                setOnClickListener { hideMenu(); go() }
            }
            m.addView(t, LinearLayout.LayoutParams(-1, dp(56f)))
        }
        val mw = dp(240f)
        val mh = items.size * dp(56f) + (items.size - 1) * hair(.5f)
        val loc = IntArray(2); anchor.getLocationInWindow(loc)
        val rl = IntArray(2); root.getLocationInWindow(rl)
        val ax = loc[0] - rl[0]; val ay = loc[1] - rl[1]
        val x = (ax + anchor.width / 2 - mw / 2).coerceIn(dp(16f), root.width - dp(16f) - mw)
        val above = ay - dp(8f) - mh
        val y = if (above > sbTop + dp(8f)) above else (ay + anchor.height + dp(8f)).coerceAtMost(root.height - navBot - mh - dp(8f))
        layer.addView(m, FrameLayout.LayoutParams(mw, mh).apply { leftMargin = x; topMargin = y })
        root.addView(layer, FrameLayout.LayoutParams(-1, -1))
        menuLayer = layer
        if (!reduceMotion) { m.alpha = 0f; m.scaleX = .96f; m.scaleY = .96f; m.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(EASE).start() }
    }

    private fun hideMenu(): Boolean {
        val l = menuLayer ?: return false
        root.removeView(l); menuLayer = null; return true
    }

    private fun item(text: String, go: () -> Unit): Pair<String, () -> Unit> = Pair(text, go)

    /** 앱 메뉴: 이름 바꾸기 · 다른 폴더에도 넣기 · 이 폴더에서 빼기 · 숨기기 · 앱 정보 */
    private fun appMenu(anchor: View, a: AppEntry, src: String?) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += item("이름 바꾸기") { renameSheet("RENAME", a.label, a.orig) { repo.renameApp(a.pkg, it); refreshAll() } }
        items += item("다른 폴더에도 넣기") { folderPickSheet(a) }
        if (src != null) items += item("이 폴더에서 빼기") { repo.removeFrom(a.pkg, src); refreshAll() }
        if (repo.isHidden(a.pkg)) items += item("다시 보이기") { repo.unhide(a.pkg); refreshAll() }
        else items += item("숨기기") { repo.hide(a.pkg); refreshAll() }
        items += item("앱 정보") {
            openSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + a.pkg)))
        }
        showMenu(anchor, items)
    }

    /** 폴더 메뉴: 이름 바꾸기 · 표지 앱 고르기 · 표지 원래대로 · 원래 이름으로 · 폴더 삭제 */
    internal fun folderMenu(anchor: View, id: String) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += item("폴더 이름 바꾸기") { renameSheet("RENAME", repo.nameOf(id), null) { repo.renameFolder(id, it); refreshAll() } }
        items += item("표지 앱 고르기") { startPick(id) }
        if (repo.hasCustomCover(id)) items += item("표지 원래대로") { repo.clearCover(id); refreshAll() }
        if (repo.isRenamed(id)) items += item("원래 이름으로") { repo.resetName(id); refreshAll() }
        if (repo.folderCount() > 1) items += item("폴더 삭제") { deleteSheet(id) }
        showMenu(anchor, items)
    }

    // ───────── 바텀시트 (SETTINGS · 이름 바꾸기 · 폴더 고르기 · 숨긴 앱 · 삭제 확인) ─────────
    private var sheetBg: View? = null
    private var sheet: LinearLayout? = null
    private var sheetWrap: View? = null
    /** 시트가 화면보다 길면 안에서 스크롤 (상태바 아래 24dp까지) */
    private inner class MaxScroll : android.widget.ScrollView(this@MainActivity) {
        override fun onMeasure(wSpec: Int, hSpec: Int) {
            val mx = (root.height - sbTop - dp(24f)).coerceAtLeast(dp(200f))
            super.onMeasure(wSpec, View.MeasureSpec.makeMeasureSpec(mx, View.MeasureSpec.AT_MOST))
        }
    }

    private fun openSheet(build: (LinearLayout) -> Unit) {
        hideSettingsSheet()
        val bg = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0f; elevation = dpf(30f); setOnClickListener { hideSettingsSheet() } }
        val sh = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Style.tile(this@MainActivity, Style.PAPER, 256f); foreground = InsetLine(4f, .75f)
            elevation = dpf(32f); isClickable = true
        }
        build(sh)
        val sw = MaxScroll().apply { elevation = dpf(32f); isFillViewport = false; overScrollMode = View.OVER_SCROLL_NEVER; isVerticalScrollBarEnabled = false }
        sh.elevation = 0f
        sw.addView(sh, FrameLayout.LayoutParams(-1, -2))
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))
        root.addView(sw, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        sheetBg = bg; sheet = sh; sheetWrap = sw
        placeSheet(sh)
        bg.animate().alpha(.4f).setDuration(if (reduceMotion) 120 else 280).start()
        if (!reduceMotion) { sw.translationY = dpf(200f); sw.animate().translationY(0f).setDuration(280).setInterpolator(EASE).start() }
    }

    /** 키보드가 있으면 키보드 바로 위 + 아래 여백 24dp, 없으면 제스처 영역 + 24dp (빈칸 두 번 계산하던 문제 — 샤넬팀 #7) */
    private fun placeSheet(sh: LinearLayout) {
        val ime = imeBot > navBot
        sh.setPadding(dp(24f), dp(24f), dp(24f), dp(if (ime) 24f else 16f) + if (ime) 0 else navBot)
        (sheetWrap?.layoutParams as? FrameLayout.LayoutParams)?.let { it.bottomMargin = if (ime) imeBot else 0; sheetWrap?.requestLayout() }
    }

    /** 시트 머리글: 카드 키커와 같은 Jost 400 10sp, 자간 .3em, 검정 55% (샤넬팀 #9) */
    private fun sheetKicker(sh: LinearLayout, text: String) {
        sh.addView(label(text, Style.jost(this, 400), 10f, Style.ink(.55f), .3f, Gravity.START), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14f) })
    }

    private fun showSettingsSheet() {
        hideKeyboard()
        openSheet { sh ->
            sheetKicker(sh, "SETTINGS")
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun line() = list.addView(View(this).apply { setBackgroundColor(Style.ink(.1f)) }, LinearLayout.LayoutParams(-1, hair(.5f)))
            fun row(text: String, right: String?, enabled: Boolean, go: () -> Unit) {
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = pressedBg() }
                r.addView(label(text, Style.kr(this, 300), 14f, Style.INK, 0f, Gravity.START or Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(0, -2, 1f))
                if (right != null) {
                    val latin = right.all { it.code < 128 }
                    r.addView(label(right, if (latin) Style.jost(this, 300) else Style.kr(this, 300), 13f, Style.ink(.55f), if (latin) .1f else 0f, Gravity.END))
                }
                if (enabled) r.setOnClickListener { hideSettingsSheet(); go() } else r.alpha = .4f
                list.addView(r, LinearLayout.LayoutParams(-1, dp(56f)))
            }
            // 1. 아이콘 COLOR / MONO / GILT — 시트를 닫지 않고 바로 바뀜
            list.addView(segRow("아이콘", listOf("COLOR", "MONO", "GILT"), { repo.iconMode }) { repo.iconMode = it; refreshAll() }, LinearLayout.LayoutParams(-1, dp(56f))); line()
            row("테마 · THE COLLECTION", Look.NAMES[look.theme], true) { showCollection() }; line()
            row("보기 · DISPLAY", null, true) { displaySheet() }; line()
            // 테마 전용 메뉴는 그 테마를 쓰는 동안에만 (성훈님 결정)
            when (look.theme) {
                Look.CAPTION -> { row("표지 사진 · THE CAPTION", if (look.photos.isEmpty()) "기본" else "${look.photos.size}장", true) { pickPhotos() }; line() }
                Look.TRAIN -> { row("여행 사진 · THE VOYAGE", if (look.trips.isEmpty()) "기본" else "${look.trips.size}장", true) { tripEditor() }; line() }
                Look.HOUR -> { row("시계 문자판 · THE HOUR", if (look.hourDial == 0) "NOIR" else "IVOIRE", true) { choiceSheet(0) }; line() }
                Look.LIBRARY -> { row("원고지 순서 · THE LIBRARY", if (look.libOrder == 0) "세로" else "가로", true) { choiceSheet(1) }; line() }
            }
            row("HA 연결 · 미디어", if (look.haToken.isBlank()) "연결 안 됨" else "토큰 있음", true) { tokenSheet() }; line()
            FineVol.init(getSystemService(AUDIO_SERVICE) as android.media.AudioManager)
            row("폰 볼륨 · PHONE", FineVol.note, false) {}; line()
            row("폴더 추가", null, repo.folderCount() < AppRepo.MAX_FOLDERS) {
                // 저장을 눌러야 폴더가 생김 (취소하면 아무것도 안 생김)
                renameSheet("NEW FOLDER", repo.newFolderName(), null, selectAll = true) { repo.addFolder(it); refreshAll() }
            }; line()
            val hc = repo.hiddenApps().size
            row("숨긴 앱", "$hc", hc > 0) { hiddenSheet() }; line()
            row("폴더 이름 모두 원래대로", null, repo.hasRenamedDefaults()) { repo.resetAllNames(); refreshAll() }; line()
            val dd = repo.deletedDefaults().size
            if (dd > 0) { row("기본 폴더 되살리기", "$dd", repo.folderCount() < AppRepo.MAX_FOLDERS) { repo.restoreDefaults(); refreshAll() }; line() }
            row("기본 홈 앱 바꾸기", null, true) { openSafely(Intent(Settings.ACTION_HOME_SETTINGS)) }; line()
            row("알림 점 · 미디어 허용", null, true) { openSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
            list.measure(View.MeasureSpec.makeMeasureSpec(root.width - dp(48f), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            sh.addView(sv, LinearLayout.LayoutParams(-1, minOf(list.measuredHeight, (root.height * .68f).toInt())))
        }
    }

    /** 이름 + 가로 칸 고르기 (시트를 닫지 않고 바로 바뀜) */
    private fun segRow(title: String, opts: List<String>, get: () -> Int, set: (Int) -> Unit): LinearLayout {
        val ir = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ir.addView(label(title, Style.kr(this, 300), 14f, Style.INK, 0f, Gravity.START or Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(0, -2, 1f))
        val segs = ArrayList<TextView>()
        fun paint() = segs.forEachIndexed { i, s ->
            val on = get() == i
            s.setTextColor(if (on) Style.IVORY else Style.INK)
            s.background = GradientDrawable().apply { if (on) setColor(Style.INK) else setStroke(dp(1f), Style.ink(.3f)) }
        }
        val latin = opts.all { it.all { c -> c.code < 128 } }
        val w = if (opts.size > 3) 52f else 60f
        opts.forEachIndexed { i, n ->
            val s = label(n, if (latin) Style.jost(this, 400) else Style.kr(this, 400), if (latin) 10f else 11.5f, Style.INK, if (latin) .2f else 0f).apply {
                gravity = Gravity.CENTER
                setOnClickListener { set(i); paint() }
            }
            segs += s
            ir.addView(s, LinearLayout.LayoutParams(dp(w), dp(32f)).apply { if (i > 0) leftMargin = dp(6f) })
        }
        paint()
        return ir
    }

    /** 보기 · DISPLAY: 폴더 크기 · 앱 아이콘 크기 · 이름 크기 · 이름 색 */
    private fun displaySheet() {
        openSheet { sh ->
            sheetKicker(sh, "DISPLAY")
            fun line() = sh.addView(View(this).apply { setBackgroundColor(Style.ink(.1f)) }, LinearLayout.LayoutParams(-1, hair(.5f)))
            val three = listOf("작게", "기본", "크게")
            sh.addView(segRow("폴더 크기", three, { look.folderSize }) { look.folderSize = it; refreshAll() }, LinearLayout.LayoutParams(-1, dp(56f))); line()
            sh.addView(segRow("앱 아이콘", three, { look.iconSize }) { look.iconSize = it; refreshAll() }, LinearLayout.LayoutParams(-1, dp(56f))); line()
            sh.addView(segRow("이름 크기", three, { look.nameSize }) { look.nameSize = it; refreshAll() }, LinearLayout.LayoutParams(-1, dp(56f))); line()
            sh.addView(segRow("이름 색", listOf("기본", "진하게", "금색", "옅게"), { look.nameColor }) { look.nameColor = it; refreshAll() }, LinearLayout.LayoutParams(-1, dp(56f)))
            sh.addView(label("'크게'는 폴더 30개가 한 화면에 들어가는 가장 큰 크기입니다.", Style.kr(this, 300), 12f, Style.ink(.5f), 0f, Gravity.START).apply { setLineSpacing(dpf(3f), 1f) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) })
        }
    }

    /** 두 가지 중 고르기 시트 (0 HOUR 문자판 · 1 LIBRARY 원고지 순서) — 누르면 바로 바뀜 */
    private fun choiceSheet(kind: Int) {
        openSheet { sh ->
            sheetKicker(sh, if (kind == 0) "THE HOUR · 문자판" else "THE LIBRARY · 원고지 순서")
            sh.addView(label("누르면 바로 바뀝니다", Style.kr(this, 300), 13f, Style.ink(.6f), 0f, Gravity.START), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14f) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val tiles = ArrayList<View>(); val marks = ArrayList<View>()
            val cur = { if (kind == 0) look.hourDial else look.libOrder }
            val acc = if (kind == 0) H_GOLD else 0xFFC0533F.toInt()
            // 고른 쪽: 굵은 테(2.5dp) + '● 사용 중' · 안 고른 쪽: 50% 흐리게 (선택이 잘 안 보이던 것 — 성훈님)
            fun paint() {
                tiles.forEachIndexed { i, t ->
                    val on = i == cur()
                    t.foreground = GradientDrawable().apply { if (on) setStroke(dp(2.5f), acc) else setStroke(hair(.75f), Style.ink(.2f)) }
                    // 칸 바탕은 그대로, 그림과 글자만 흐리게 (회색 막을 덮지 않음)
                    t.alpha = 1f
                    for (k in 0 until (t as ViewGroup).childCount) t.getChildAt(k).let { c -> if (c !== marks[i]) c.alpha = if (on) 1f else .5f }
                    marks[i].visibility = if (on) View.VISIBLE else View.INVISIBLE
                }
            }
            for (i in 0..1) {
                val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(if (kind == 0) Color.BLACK else 0xFFFBF8F1.toInt()); setPadding(dp(10f), dp(12f), dp(10f), dp(10f)) }
                if (kind == 0) {
                    // 실제 표지와 같은 문자판 (바늘 · 날짜창 포함, 지금 시각)
                    val cal = java.util.Calendar.getInstance()
                    t.addView(HourDial(this, i).apply { set(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), cal.get(java.util.Calendar.DAY_OF_MONTH), false) }, LinearLayout.LayoutParams(dp(112f), dp(112f)))
                    t.addView(label(if (i == 0) "NOIR" else "IVOIRE", Style.jost(this, 400), 11f, H_IVORY, .3f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
                    t.addView(label(if (i == 0) "검정 세라믹 · 금빛 바늘" else "아이보리 에나멜 · 검정 바늘", Style.kr(this, 300), 11f, tint(.6f, H_IVORY), 0f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2f) })
                } else {
                    t.addView(label(if (i == 0) "세로 순서" else "가로 순서", Style.kr(this, 400), 15f, M_INK, 0f), LinearLayout.LayoutParams(-1, -2))
                    t.addView(label(if (i == 0) "왼쪽 1–15 · 오른쪽 16–30" else "왼쪽 → 오른쪽 · 줄마다 두 개", Style.kr(this, 300), 11.5f, 0xFF8A7E6E.toInt(), 0f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })
                }
                val mk = label("●  사용 중", Style.kr(this, 500), 12f, acc, 0f)
                t.addView(mk, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) }); marks += mk
                t.setOnClickListener {
                    if (kind == 0) { look.hourDial = i; themeCover.rebuild(true) } else { look.libOrder = i; refreshGrid() }
                    paint()
                }
                tiles += t
                row.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(12f) })
            }
            paint()
            sh.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** HA 연결: 장기 액세스 토큰을 한 번 넣어 둠 (HA 프로필 → 보안 → 장기 액세스 토큰) */
    private fun tokenSheet() {
        openSheet { sh ->
            sheetKicker(sh, "HOME ASSISTANT")
            sh.addView(label("HA 프로필 → 보안 → 장기 액세스 토큰에서 만든 토큰을 붙여 넣어 주십시오. 미디어 페이지의 Apple TV · Marantz · 소리 옮기기와 Marantz 볼륨에 씁니다.",
                Style.kr(this, 300), 13f, Style.ink(.75f), 0f, Gravity.START).apply { setLineSpacing(dpf(4f), 1f) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16f) })
            val et = EditText(this).apply {
                setText(look.haToken); hint = "토큰"; setHintTextColor(Style.ink(.35f))
                typeface = Fonts.mono(this@MainActivity); textSize = 12f; setTextColor(Style.INK)
                setSingleLine(); background = null; setPadding(0, 0, 0, dp(6f))
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            val wrap = FrameLayout(this)
            wrap.addView(et, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            wrap.addView(View(this).apply { setBackgroundColor(Style.INK) }, FrameLayout.LayoutParams(-1, dp(1f), Gravity.BOTTOM))
            sh.addView(wrap, LinearLayout.LayoutParams(-1, dp(44f)))
            val msg = label("", Style.kr(this, 300), 12f, Style.ink(.6f), 0f, Gravity.START)
            sh.addView(msg, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f) })
            sh.addView(buttonPair("취소", "연결 확인", true, { hideIme(et); hideSettingsSheet() }) {
                val tk = et.text.toString().trim()
                if (tk.isEmpty()) { look.haToken = ""; msg.text = "토큰을 지웠습니다"; media.rebuild(true); return@buttonPair }
                msg.text = "확인 중…"
                Ha.check(tk) { ok ->
                    if (ok) { look.haToken = tk; msg.text = "연결됨 · 저장했습니다"; hideIme(et); root.postDelayed({ hideSettingsSheet() }, 700); media.rebuild(true) }
                    else msg.text = "연결되지 않았습니다 · 토큰을 확인해 주십시오"
                }
            }, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(20f) })
        }
    }

    // ───────── THE CAPTION 사진 고르기 (최대 6장) ─────────
    private val photoPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        val keep = uris.take(Look.MAX_PHOTOS)
        keep.forEach { u -> try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
        // 예전 사진 권한은 놓아 줌
        look.photos.forEach { old -> if (keep.none { it.toString() == old }) try { contentResolver.releasePersistableUriPermission(android.net.Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
        look.photos = keep.map { it.toString() }; look.photoIdx = 0
        themeCover.loadPhoto()
        if (look.theme == Look.CAPTION) themeBox()
    }
    internal fun pickPhotos() { try { photoPicker.launch(arrayOf("image/*")) } catch (_: Exception) {} }

    /** 테마 바꾸기: 표지 · 미디어 · 서랍을 모두 다시 그림 */
    internal fun applyTheme(t: Int) {
        look.theme = t
        hideCard()
        themeCover.rebuild(true)
        layoutCover()
        media.rebuild(true)
        themeBox()
        refreshGrid()
        barsLight = null; updateBars()
    }

    // ───────── MediaHost ─────────
    override fun pickSheet(kicker: String, items: List<String>, current: String, note: String?, onPick: (String) -> Unit) {
        openSheet { sh ->
            sheetKicker(sh, kicker)
            items.forEach { s ->
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = pressedBg() }
                r.addView(label(s, Style.kr(this, 400), 14.5f, Style.INK, 0f, Gravity.START or Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(0, -2, 1f))
                if (s == current) r.addView(View(this).apply { background = shape(Style.GOLD, oval = true) }, LinearLayout.LayoutParams(dp(8f), dp(8f)).apply { leftMargin = dp(10f) })
                r.setOnClickListener { hideSettingsSheet(); onPick(s) }
                sh.addView(View(this).apply { setBackgroundColor(Style.ink(.12f)) }, LinearLayout.LayoutParams(-1, hair(.5f)))
                sh.addView(r, LinearLayout.LayoutParams(-1, dp(56f)))
            }
            if (note != null) sh.addView(label(note, Style.kr(this, 300), 12f, Style.ink(.55f), 0f, Gravity.START), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })
        }
    }
    /** 소리 창: 이 폰 · Marantz · 블루투스 — 지금 듣는 곳에 '듣는 중' + 테마색 점 */
    override fun soundSheet(cur: Int, dot: Int, onPick: (Int) -> Unit) {
        openSheet { sh ->
            sh.addView(label("LE SON · 소리", Style.jost(this, 400), 11f, Style.ink(.55f), .2f, Gravity.START), LinearLayout.LayoutParams(-1, -2))
            sh.addView(label("어디서 들을까요?", Style.kr(this, 400), 18f, Style.INK, 0f, Gravity.START), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f); bottomMargin = dp(16f) })
            listOf(Triple(0, "이 폰에서 듣기", "Spotify · 이 폰 스피커"), Triple(1, "Marantz에서 듣기", "Marantz NR1711 · 거실"), Triple(2, "블루투스 · 이어폰", "삼성 출력 창 열기 ›")).forEach { (k, t, sub) ->
                sh.addView(View(this).apply { setBackgroundColor(Style.ink(.12f)) }, LinearLayout.LayoutParams(-1, hair(.75f)))
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = pressedBg() }
                val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                col.addView(label(t, Style.kr(this, 400), 14.5f, Style.INK, 0f, Gravity.START))
                col.addView(label(sub, Style.kr(this, 300), 12f, Style.ink(.6f), 0f, Gravity.START), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5f) })
                r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                if (k == cur) {
                    r.addView(label("듣는 중", Style.kr(this, 400), 12f, Style.ink(.7f), 0f, Gravity.END))
                    r.addView(View(this).apply { background = shape(dot, oval = true) }, LinearLayout.LayoutParams(dp(8f), dp(8f)).apply { leftMargin = dp(8f) })
                }
                r.setOnClickListener { hideSettingsSheet(); onPick(k) }
                sh.addView(r, LinearLayout.LayoutParams(-1, dp(62f)))
            }
            sh.addView(View(this).apply { setBackgroundColor(Style.ink(.12f)) }, LinearLayout.LayoutParams(-1, hair(.75f)))
            sh.addView(label("바꾸면 Spotify가 그 기기로 옮겨 갑니다.\n2~5초 걸릴 수 있습니다.", Style.kr(this, 300), 12f, Style.ink(.55f), 0f, Gravity.START).apply { setLineSpacing(dpf(4f), 1f) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14f) })
        }
    }
    override fun askToken() = tokenSheet()
    override fun photo() = themeCover.photoDrawable()
    override fun openNotifAccess() = openSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    override fun hasApp(pkg: String) = packageManager.getLaunchIntentForPackage(pkg) != null
    override fun openApp(pkg: String): Boolean {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        openSafely(i); return true
    }

    /** 이름 바꾸기 시트 (200dp, 밑줄 입력칸 16sp, 취소 · 저장). 빈 칸으로 저장하면 원래 이름 */
    private fun renameSheet(title: String, current: String, original: String?, selectAll: Boolean = false, onSave: (String) -> Unit) {
        openSheet { sh ->
            sheetKicker(sh, title)
            val et = EditText(this).apply {
                setText(current)
                if (selectAll) selectAll() else setSelection(current.length)   // 새 폴더는 전체 선택 → 바로 덮어쓰기
                typeface = Style.kr(this@MainActivity, 300); textSize = 16f; setTextColor(Style.INK)
                if (original != null) { hint = original; setHintTextColor(Style.ink(.35f)) }
                setSingleLine(); background = null; setPadding(0, 0, 0, dp(6f))
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            }
            val wrap = FrameLayout(this)
            wrap.addView(et, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            wrap.addView(View(this).apply { setBackgroundColor(Style.INK) }, FrameLayout.LayoutParams(-1, dp(1f), Gravity.BOTTOM))
            sh.addView(wrap, LinearLayout.LayoutParams(-1, dp(44f)))
            val save = { val v = et.text.toString(); hideIme(et); hideSettingsSheet(); onSave(v) }
            et.setOnEditorActionListener { _, _, _ -> save(); true }
            sh.addView(buttonPair("취소", "저장", true, { hideIme(et); hideSettingsSheet() }) { save() },
                LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(28f) })
            et.post {
                et.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun hideIme(v: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    /** 다른 폴더에도 넣기: 폴더 이름 칩 5열 (들어 있는 폴더는 검정), 여러 개 골라 완료 */
    private fun folderPickSheet(a: AppEntry) {
        val sel = LinkedHashSet(repo.foldersFor(a.pkg))
        openSheet { sh ->
            sheetKicker(sh, "ADD TO FOLDERS")
            sh.addView(label(a.label, Style.kr(this, 300), 16f, Style.INK, 0f, Gravity.START), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16f) })
            val ids = repo.folders()
            val inner = root.width - dp(48f)
            val gap = dp(6f)
            val cw = (inner - gap * 4) / 5
            val g = GridLayout(this).apply { columnCount = 5 }
            ids.forEachIndexed { i, id ->
                val chip = label(shortName(repo.nameOf(id)), Style.kr(this, 300), 11f, Style.INK, 0f).apply {
                    gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(2f), 0, dp(2f), 0)
                }
                fun paint() {
                    val on = id in sel
                    chip.setTextColor(if (on) Style.IVORY else Style.INK)
                    chip.background = GradientDrawable().apply { if (on) setColor(Style.INK) else setStroke(dp(1f), Style.ink(.25f)) }
                }
                paint()
                chip.setOnClickListener { if (!sel.remove(id)) sel.add(id); paint() }
                g.addView(chip, GridLayout.LayoutParams().apply {
                    width = cw; height = dp(36f)
                    leftMargin = if (i % 5 == 0) 0 else gap; topMargin = if (i < 5) 0 else gap
                })
            }
            val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(g) }
            val rows = (ids.size + 4) / 5
            val gh = rows * dp(36f) + (rows - 1) * gap
            sh.addView(sv, LinearLayout.LayoutParams(-1, minOf(gh, (root.height * .5f).toInt())))
            val done = label("완료", Style.kr(this, 400), 13f, Style.IVORY, .04f).apply {
                gravity = Gravity.CENTER; setBackgroundColor(Style.INK)
                setOnClickListener { repo.setFolders(a.pkg, sel); hideSettingsSheet(); refreshAll() }
            }
            sh.addView(done, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(20f) })
        }
    }

    /** 숨긴 앱 목록 — 다시 보이기 */
    private fun hiddenSheet() {
        openSheet { sh ->
            sheetKicker(sh, "HIDDEN APPS")
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun fill() {
                list.removeAllViews()
                repo.hiddenApps().forEachIndexed { i, a ->
                    if (i > 0) list.addView(View(this).apply { setBackgroundColor(Style.ink(.1f)) }, LinearLayout.LayoutParams(-1, dp(1f)))
                    val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    r.addView(ImageView(this).apply { setImageDrawable(iconOf(a)); styleIcon(this) }, LinearLayout.LayoutParams(dp(32f), dp(32f)))
                    r.addView(label(a.label, Style.kr(this, 300), 14f, Style.INK, 0f, Gravity.START).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14f) })
                    r.addView(label("다시 보이기", Style.kr(this, 400), 12f, Style.INK, .04f).apply {
                        gravity = Gravity.CENTER; background = GradientDrawable().apply { setStroke(dp(1f), Style.INK) }
                        setOnClickListener { repo.unhide(a.pkg); refreshAll(); if (repo.hiddenApps().isEmpty()) hideSettingsSheet() else fill() }
                    }, LinearLayout.LayoutParams(dp(92f), dp(32f)))
                    list.addView(r, LinearLayout.LayoutParams(-1, dp(56f)))
                }
            }
            fill()
            val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
            val n = repo.hiddenApps().size
            sh.addView(sv, LinearLayout.LayoutParams(-1, minOf(n * dp(57f), (root.height * .55f).toInt())))
        }
    }

    /** 폴더 삭제 확인 (붉은색 없이 검정 채움 버튼) */
    private fun deleteSheet(id: String) {
        val m = repo.orphanCount(id)
        openSheet { sh ->
            sheetKicker(sh, "DELETE FOLDER")
            sh.addView(label("‘${repo.nameOf(id)}’ 폴더를 삭제합니다. 다른 폴더에 없는 앱 ${m}개는 UNSORTED로 갑니다.",
                Style.kr(this, 300), 14f, Style.INK, 0f, Gravity.START).apply { setLineSpacing(dpf(4f), 1f) }, LinearLayout.LayoutParams(-1, -2))
            sh.addView(buttonPair("취소", "삭제", true, { hideSettingsSheet() }) {
                hideSettingsSheet()
                if (cardFolder == id) hideCard()
                repo.deleteFolder(id); refreshAll()
            }, LinearLayout.LayoutParams(-1, dp(44f)).apply { topMargin = dp(28f) })
        }
    }

    internal fun hideSettingsSheet(): Boolean {
        val bg = sheetBg ?: return false
        root.removeView(bg); sheetWrap?.let { root.removeView(it) }
        sheetBg = null; sheet = null; sheetWrap = null
        return true
    }

    private fun openSafely(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ───────── 공통 ─────────
    private fun label(text: CharSequence, tf: android.graphics.Typeface, sp: Float, color: Int, spacing: Float, g: Int = Gravity.CENTER_HORIZONTAL) =
        KText(this).apply {
            this.text = text; typeface = tf; textSize = sp; setTextColor(color); letterSpacing = spacing; gravity = g
            includeFontPadding = false
        }

    override fun onDestroy() {
        BadgeService.onChange = null
        try { unregisterReceiver(sysRx) } catch (_: Exception) {}
        try { unregisterReceiver(pkgRx) } catch (_: Exception) {}
        worker.shutdown()
        super.onDestroy()
    }
}
