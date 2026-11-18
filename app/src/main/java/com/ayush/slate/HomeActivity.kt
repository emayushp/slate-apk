package com.ayush.slate

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HomeActivity : Activity() {

    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(2)

    private lateinit var root: FrameLayout
    private lateinit var ambient: AmbientView
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var clock: TextView
    private lateinit var ampm: TextView
    private lateinit var date: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var favSection: LinearLayout
    private lateinit var favRow: LinearLayout
    private lateinit var favScroll: HorizontalScrollView
    private lateinit var gridSection: LinearLayout
    private lateinit var grid: LinearLayout
    private lateinit var searchPill: Pill
    private lateinit var toast: TextView

    private var apps: List<AppEntry> = emptyList()
    private val cards = HashMap<String, MutableList<AppCard>>()
    private val overlays = ArrayList<Layer>()
    private val focusBeforeOverlay = ArrayList<View?>()
    private var movingPkg: String? = null
    private var heroPkg: String? = null

    private val accent: () -> Accent = { Look.accents[prefs.accent.mod(Look.accents.size)] }

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateClock()
    }
    private val pkgChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            i.data?.schemeSpecificPart?.let { Apps.clearCache(it) }
            main.removeCallbacks(reload); main.postDelayed(reload, 600)
        }
    }
    private val reload = Runnable { loadApps(keepFocus = true) }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        buildShell()
        setContentView(root)
        loadApps(keepFocus = false)

        val pf = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        register(pkgChanged, pf)
    }

    override fun onStart() {
        super.onStart()
        register(tick, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        updateClock()
        ambient.running = true
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(tick) } catch (e: Exception) { }
        ambient.running = false
    }

    override fun onResume() {
        super.onResume()
        // Back on Slate means the "visit Amazon Home" bypass is over.
        if (prefs.bypass) prefs.bypass = false
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgChanged) } catch (e: Exception) { }
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed while already home: close everything and return to the top.
        while (overlays.isNotEmpty()) closeTop(restoreFocus = false)
        endMove()
        scroll.smoothScrollTo(0, 0)
        focusFirst()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            movingPkg != null -> endMove()
            overlays.isNotEmpty() -> closeTop()
            scroll.scrollY > 0 -> { scroll.smoothScrollTo(0, 0); focusFirst() }
            else -> { /* A home screen has nowhere to go back to. */ }
        }
    }

    private fun register(r: BroadcastReceiver, f: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED) else registerReceiver(r, f)
    }

    // ---------------------------------------------------------------- layout

    private fun buildShell() {
        root = FrameLayout(this)
        ambient = AmbientView(this)
        ambient.motion = prefs.motion
        ambient.setColors(accent().a, accent().b, animate = false)
        root.addView(ambient, FrameLayout.LayoutParams(-1, -1))

        scroll = object : ScrollView(this) {
            // We position the page ourselves so focus moves feel calm, not jumpy.
            override fun computeScrollDeltaToGetChildRectOnScreen(rect: android.graphics.Rect?) = 0
        }
        scroll.isVerticalScrollBarEnabled = false
        scroll.isFillViewport = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; noClip()
            setPadding(0, dp(30), 0, dp(60))
        }
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        content.addView(topBar())
        content.addView(hero())

        favSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; noClip() }
        favScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; noClip(); overScrollMode = View.OVER_SCROLL_NEVER
        }
        favRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; noClip()
            setPadding(dp(56), dp(16), dp(56), dp(10))
        }
        favScroll.addView(favRow)
        favSection.addView(favScroll)
        content.addView(favSection)

        gridSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; noClip() }
        gridSection.addView(sectionTitle("Your apps"))
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; noClip(); setPadding(dp(56), dp(10), dp(56), 0) }
        gridSection.addView(grid)
        content.addView(gridSection)

        content.addView(sectionTitle("System"))
        content.addView(systemRow())
        content.addView(text(this, "Slate · no ads · no internet access · nothing leaves this device", 12f, Look.TEXT_FAINT).apply {
            setPadding(dp(58), dp(28), 0, 0)
        })

        toast = text(this, "", 15f, Color.WHITE, Look.medium).apply {
            background = rounded(0xE6151A28.toInt(), dpf(22), dp(1), 0x26FFFFFF)
            setPadding(dp(22), dp(12), dp(22), dp(12)); alpha = 0f; elevation = dpf(30)
        }
        root.addView(toast, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(36) })

        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now -> onFocusMoved(now) }
    }

    private fun topBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; noClip()
            setPadding(dp(56), 0, dp(56), 0)
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        clock = text(this, "", 58f, Look.TEXT, Look.thin).apply { letterSpacing = -0.02f }
        ampm = text(this, "", 18f, Look.TEXT_DIM, Look.light).apply { setPadding(dp(8), 0, 0, dp(9)) }
        timeRow.addView(clock); timeRow.addView(ampm)
        date = text(this, "", 15f, Look.TEXT_DIM, Look.regular).apply { setPadding(dp(3), dp(6), 0, 0) }
        left.addView(timeRow); left.addView(date)
        bar.addView(left, LinearLayout.LayoutParams(0, -2, 1f))

        searchPill = Pill(this, "Search EveryTV", IconDrawable.Kind.SEARCH, accent, big = true)
        searchPill.setOnClickListener { openSearch() }
        bar.addView(searchPill, LinearLayout.LayoutParams(dp(300), dp(52)))

        val settings = Pill(this, "", IconDrawable.Kind.SLIDERS, accent, big = true)
        settings.setPadding(dp(15), 0, dp(15), 0)
        settings.setOnClickListener { openSettings() }
        bar.addView(settings, LinearLayout.LayoutParams(dp(52), dp(52)).apply { leftMargin = dp(14) })
        return bar
    }

    private fun hero(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(58), dp(40), dp(56), 0)
        }
        heroTitle = text(this, "", 30f, Look.TEXT, Look.medium)
        heroSub = text(this, "", 14f, Look.TEXT_DIM, Look.regular).apply { setPadding(0, dp(8), 0, 0) }
        box.addView(heroTitle); box.addView(heroSub)
        return box
    }

    private fun sectionTitle(s: String) = text(this, s.uppercase(), 12f, Look.TEXT_FAINT, Look.medium).apply {
        letterSpacing = 0.16f; setPadding(dp(58), dp(26), 0, dp(2))
    }

    private fun systemRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; noClip(); setPadding(dp(56), dp(14), dp(56), 0)
        }
        fun add(label: String, kind: IconDrawable.Kind, action: () -> Unit) {
            val p = Pill(this, label, kind, accent)
            p.setOnClickListener { action() }
            row.addView(p, LinearLayout.LayoutParams(-2, dp(46)).apply { rightMargin = dp(14) })
        }
        add("Fire TV Settings", IconDrawable.Kind.GEAR) { openFireSettings() }
        add("Slate Settings", IconDrawable.Kind.SLIDERS) { openSettings() }
        add("Amazon Home", IconDrawable.Kind.HOME) { openAmazonHome() }
        return row
    }

    // ---------------------------------------------------------------- data → views

    private fun searchTarget(): AppEntry? {
        val pkg = prefs.searchPkg
        if (pkg != null) apps.firstOrNull { it.pkg == pkg }?.let { return it }
        return Apps.findEveryTV(apps)
    }

    private fun loadApps(keepFocus: Boolean) {
        val focusedPkg = (currentFocus?.tag as? AppEntry)?.pkg
        val inFav = currentFocus?.let { isInside(it, favRow) } ?: false
        io.execute {
            val list = Apps.load(this)
            main.post {
                if (isFinishing) return@post
                apps = list
                if (!prefs.seeded && list.isNotEmpty()) {
                    prefs.favorites = Apps.seedFavorites(list, searchTarget()?.pkg)
                    prefs.seeded = true
                }
                searchPill.textView.text = "Search ${searchTarget()?.label ?: "EveryTV"}"
                buildRows()
                if (keepFocus && focusedPkg != null) focusPkg(focusedPkg, inFav) else if (!keepFocus) focusFirst()
                loadVisuals()
            }
        }
    }

    private fun visibleApps() = apps.filter { it.pkg !in prefs.hidden }

    private fun favEntries(): List<AppEntry> {
        val byPkg = apps.associateBy { it.pkg }
        return prefs.favorites.mapNotNull { byPkg[it] }
    }

    private fun buildRows() {
        cards.clear()
        val favW = dp(232); val favH = dp(130)
        favRow.removeAllViews()
        val favs = favEntries()
        favSection.visibility = if (favs.isEmpty()) View.GONE else View.VISIBLE
        for (e in favs) {
            val c = makeCard(e, favW, favH)
            favRow.addView(c, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(26) })
        }

        grid.removeAllViews()
        val vis = visibleApps().filter { it.pkg !in prefs.favorites }
        gridSection.visibility = if (vis.isEmpty()) View.GONE else View.VISIBLE
        val usable = resources.displayMetrics.widthPixels - dp(112)
        val cols = 5
        val gap = dp(22)
        val w = (usable - gap * (cols - 1)) / cols
        val h = (w * 9f / 16f).toInt()
        var line: LinearLayout? = null
        vis.forEachIndexed { i, e ->
            if (i % cols == 0) {
                line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; noClip(); setPadding(0, dp(8), 0, dp(4)) }
                grid.addView(line)
            }
            val c = makeCard(e, w, h)
            line!!.addView(c, LinearLayout.LayoutParams(-2, -2).apply { if (i % cols != cols - 1) rightMargin = gap })
        }
    }

    private fun makeCard(e: AppEntry, w: Int, h: Int): AppCard {
        val c = AppCard(this, e, w, h, accent)
        Apps.cached(e.pkg)?.let { c.bind(it) }
        c.frame.setOnClickListener { if (movingPkg == null) c.pulse { launch(e) } }
        c.frame.setOnLongClickListener { openAppMenu(e); true }
        c.moving = e.pkg == movingPkg
        cards.getOrPut(e.pkg) { ArrayList() }.add(c)
        return c
    }

    private fun loadVisuals() {
        val todo = apps.filter { Apps.cached(it.pkg) == null }
        // Favourites first so the top row lights up instantly.
        val favs = prefs.favorites.toSet()
        for (e in todo.sortedBy { if (it.pkg in favs) 0 else 1 }) {
            io.execute {
                val v = Apps.visual(this, e)
                main.post {
                    cards[e.pkg]?.forEach { it.bind(v) }
                    if (heroPkg == e.pkg) ambient.setColors(v.color, accent().b)
                }
            }
        }
    }

    // ---------------------------------------------------------------- focus & motion

    private fun isInside(v: View, parent: View): Boolean {
        var p: Any? = v
        while (p is View) { if (p === parent) return true; p = p.parent }
        return false
    }

    private fun onFocusMoved(now: View?) {
        if (now == null || !isInside(now, content)) return
        // Calm vertical scrolling: top of page for the upper area, otherwise keep focus ~1/3 down.
        var y = 0; var v: View = now
        while (v !== content) { y += v.top; v = v.parent as? View ?: break }
        val screenH = scroll.height
        if (y < screenH * 0.55f) scroll.smoothScrollTo(0, 0)
        else scroll.smoothScrollTo(0, (y - screenH * 0.34f).toInt().coerceAtLeast(0))

        val e = now.tag as? AppEntry
        if (e != null) setHero(e, isInside(now, favRow)) else if (now === searchPill) setHeroSearch()
    }

    private fun setHero(e: AppEntry, fav: Boolean) {
        if (heroPkg == e.pkg && movingPkg == null) return
        heroPkg = e.pkg
        val sub = when {
            movingPkg != null -> "◀ ▶ to move  ·  OK to drop"
            fav -> "Favorite  ·  hold OK or press ☰ for options"
            else -> "Hold OK or press ☰ for options"
        }
        swapHero(e.label, sub)
        val col = Apps.cached(e.pkg)?.color ?: accent().a
        ambient.setColors(col, accent().b)
    }

    private fun setHeroSearch() {
        heroPkg = null
        swapHero("Search", "Find any channel, movie or series in ${searchTarget()?.label ?: "EveryTV"}")
        ambient.setColors(accent().a, accent().b)
    }

    private fun swapHero(title: String, sub: String) {
        if (heroTitle.text == title && heroSub.text == sub) return
        heroTitle.animate().cancel()
        heroTitle.animate().alpha(0f).translationX(-dpf(8)).setDuration(90).withEndAction {
            heroTitle.text = title; heroSub.text = sub
            heroTitle.translationX = dpf(10)
            heroTitle.animate().alpha(1f).translationX(0f).setDuration(200).start()
        }.start()
    }

    private fun focusFirst() {
        root.post {
            val first = (favRow.getChildAt(0) as? AppCard)?.frame
                ?: ((grid.getChildAt(0) as? LinearLayout)?.getChildAt(0) as? AppCard)?.frame
                ?: searchPill
            first.requestFocus()
        }
    }

    private fun focusPkg(pkg: String, preferFav: Boolean) {
        root.post {
            val list = cards[pkg]
            val c = list?.firstOrNull { isInside(it, favRow) == preferFav } ?: list?.firstOrNull()
            if (c != null) c.frame.requestFocus() else focusFirst()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val moving = movingPkg
        if (moving != null && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { shiftFav(moving, -1); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { shiftFav(moving, 1); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> { endMove(); return true }
            }
        }
        if (moving != null && event.action == KeyEvent.ACTION_UP &&
            event.keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)) return true

        if (event.action == KeyEvent.ACTION_DOWN && overlays.isEmpty()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    (currentFocus?.tag as? AppEntry)?.let { openAppMenu(it); return true }
                }
                KeyEvent.KEYCODE_SEARCH -> { openSearch(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------------------------------------------------------------- actions

    private fun launch(e: AppEntry) {
        try { startActivity(Apps.launchIntent(this, e)) } catch (x: Exception) { say("Couldn't open ${e.label}") }
    }

    private fun openFireSettings() {
        try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (x: Exception) { say("Settings unavailable") }
    }

    private fun openAmazonHome() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(Apps.AMAZON_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { prefs.bypass = true; startActivity(i) } catch (x: Exception) { prefs.bypass = false; say("Amazon Home not found") }
    }

    private fun say(msg: String) {
        toast.text = msg
        toast.animate().cancel()
        toast.translationY = dpf(12)
        toast.animate().alpha(1f).translationY(0f).setDuration(180).withEndAction {
            toast.animate().alpha(0f).setStartDelay(1800).setDuration(300).start()
        }.start()
    }

    private fun shiftFav(pkg: String, dir: Int) {
        val f = prefs.favorites
        val i = f.indexOf(pkg); val j = i + dir
        if (i < 0 || j < 0 || j >= f.size) return
        f[i] = f[j]; f[j] = pkg
        prefs.favorites = f
        buildRows()
        focusPkg(pkg, true)
    }

    private fun startMove(pkg: String) {
        movingPkg = pkg
        cards[pkg]?.forEach { it.moving = true }
        heroPkg = null
        focusPkg(pkg, true)
        say("◀ ▶ to move · OK to drop")
    }

    private fun endMove() {
        val pkg = movingPkg ?: return
        movingPkg = null
        cards[pkg]?.forEach { it.moving = false }
        heroPkg = null
        (currentFocus?.tag as? AppEntry)?.let { setHero(it, true) }
    }

    // ---------------------------------------------------------------- overlays

    private fun showLayer(l: Layer) {
        focusBeforeOverlay.add(currentFocus)
        l.onClose = { if (overlays.lastOrNull() === l) closeTop() }
        overlays.add(l)
        root.addView(l, FrameLayout.LayoutParams(-1, -1))
        trapFocus()
        l.post { (l.firstFocus ?: l).requestFocus() }
    }

    /** Keep D-pad focus inside the top-most layer. */
    private fun trapFocus() {
        val top = overlays.lastOrNull()
        scroll.descendantFocusability = if (top == null) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        for (o in overlays) o.descendantFocusability = if (o === top) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun closeTop(restoreFocus: Boolean = true) {
        val l = overlays.removeLastOrNull() ?: return
        (l as? SearchLayer)?.hideKeyboard()
        val prev = focusBeforeOverlay.removeLastOrNull()
        l.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        trapFocus()
        l.animate().alpha(0f).setDuration(140).withEndAction { root.removeView(l) }.start()
        if (restoreFocus) root.post {
            val cur = currentFocus
            val top = overlays.lastOrNull()
            when {
                // Something (e.g. a rebuilt row) already placed focus where it belongs.
                cur != null && cur.isAttachedToWindow && (if (top != null) isInside(cur, top) else isInside(cur, content)) -> {}
                prev != null && prev.isAttachedToWindow && prev.isShown -> prev.requestFocus()
                prev?.tag is AppEntry -> focusPkg((prev.tag as AppEntry).pkg, false)
                top != null -> (top.firstFocus ?: top).requestFocus()
                else -> focusFirst()
            }
        }
    }

    private fun modal(title: String, sub: String?, items: List<MItem>, body: String? = null, width: Int = 480): ListModal {
        val m = ListModal(this, title, sub, items, accent, body, width)
        showLayer(m)
        return m
    }

    private fun openSearch() {
        val target = searchTarget()
        val layer = SearchLayer(this, visibleApps(), target, prefs, accent,
            onSearch = { q ->
                prefs.addRecent(q)
                closeTop(restoreFocus = false)
                if (target == null) { say("Pick a search app in Slate Settings"); searchPill.requestFocus() }
                else if (!Apps.search(this, target, q)) say("Couldn't open ${target.label}")
            },
            onOpenApp = { e -> closeTop(restoreFocus = false); launch(e) })
        showLayer(layer)
    }

    private fun openAppMenu(e: AppEntry) {
        val isFav = e.pkg in prefs.favorites
        val items = ArrayList<MItem>()
        items.add(MItem("Open") { launch(e); false })
        if (isFav) {
            items.add(MItem("Move") { closeTop(restoreFocus = false); startMove(e.pkg); false })
            items.add(MItem("Remove from Favorites") {
                prefs.favorites = prefs.favorites.apply { remove(e.pkg) }
                buildRows(); loadVisuals(); focusPkg(e.pkg, false); say("Removed from Favorites"); false
            })
        } else {
            items.add(MItem("Add to Favorites") {
                prefs.favorites = prefs.favorites.apply { add(e.pkg) }
                buildRows(); loadVisuals(); focusPkg(e.pkg, true); say("Added to Favorites"); false
            })
        }
        items.add(MItem("Hide from Home") {
            prefs.hidden = prefs.hidden + e.pkg
            prefs.favorites = prefs.favorites.apply { remove(e.pkg) }
            buildRows(); focusFirst(); say("${e.label} hidden · unhide in Slate Settings"); false
        })
        items.add(MItem("App info") {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", e.pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (x: Exception) { say("App info unavailable") }
            false
        })
        modal(e.label, e.pkg, items, width = 420)
    }

    private fun homeButtonActive(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains("$packageName/") && prefs.takeover
    }

    private fun openSettings() {
        val items = listOf(
            MItem("Accent", { accent().name }) {
                prefs.accent = (prefs.accent + 1).mod(Look.accents.size)
                applyAccent(); true
            },
            MItem("Ambient motion", { if (prefs.motion) "On" else "Off" }) {
                prefs.motion = !prefs.motion; ambient.motion = prefs.motion; true
            },
            MItem("Search app", { searchTarget()?.label ?: "Not set" }) { pickSearchApp(); false },
            MItem("Hidden apps", { prefs.hidden.size.toString() }) { manageHidden(); false },
            MItem("Home button takeover", { if (homeButtonActive()) "Active" else "Not set up" }) { homeButtonInfo(); false },
            MItem("Privacy", { "No internet access" }) { privacyInfo(); false },
            MItem("Reset layout") {
                prefs.hidden = emptySet()
                prefs.favorites = Apps.seedFavorites(apps, searchTarget()?.pkg)
                buildRows(); loadVisuals(); say("Layout reset"); false
            },
            MItem("Fire TV Settings") { openFireSettings(); false }
        )
        modal("Slate Settings", "Your home screen, your rules", items, width = 500)
    }

    private fun applyAccent() {
        ambient.setColors(accent().a, accent().b)
        val f = currentFocus
        buildRows(); loadVisuals()
        if (f?.tag is AppEntry) focusPkg((f.tag as AppEntry).pkg, isInside(f, favRow))
    }

    private fun pickSearchApp() {
        val items = visibleApps().map { e ->
            MItem(e.label, { if (searchTarget()?.pkg == e.pkg) "✓" else "" }) {
                prefs.searchPkg = e.pkg
                searchPill.textView.text = "Search ${e.label}"
                say("Search goes to ${e.label}"); false
            }
        }
        modal("Search app", "Searches from the home screen open in this app", items)
    }

    private fun manageHidden() {
        val hidden = apps.filter { it.pkg in prefs.hidden }
        if (hidden.isEmpty()) { modal("Hidden apps", null, listOf(MItem("Nothing hidden") { false })); return }
        val items = hidden.map { e ->
            MItem(e.label, { if (e.pkg in prefs.hidden) "Hidden" else "Shown" }) {
                prefs.hidden = if (e.pkg in prefs.hidden) prefs.hidden - e.pkg else prefs.hidden + e.pkg
                buildRows(); loadVisuals(); true
            }
        }
        modal("Hidden apps", "Select to show or hide", items)
    }

    private fun homeButtonInfo() {
        val active = homeButtonActive()
        val seen = prefs.seenClasses
        val body = buildString {
            append(if (active) "Slate opens whenever the Home button is pressed.\n\n"
            else "Fire OS won't let other launchers own the Home button, so Slate uses a tiny accessibility service that brings you back here whenever Amazon's home appears.\n\nTo turn it on, run setup-firetv.bat (or .sh) from the Slate download once. It enables the service over ADB.\n\n")
            append("“Amazon Home” in the System row opens the stock home once without Slate taking over.")
            if (seen.isNotEmpty()) append("\n\nRecently seen: ").append(seen.first().substringAfterLast('.'))
        }
        modal("Home button", if (active) "Active" else "Not set up", listOf(
            MItem("Takeover", { if (prefs.takeover) "On" else "Off" }) { prefs.takeover = !prefs.takeover; true },
            MItem("Accessibility settings") {
                try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (x: Exception) { say("Not available on this Fire OS — use the setup script") }
                false
            },
            MItem("Done") { false }
        ), body = body, width = 560)
    }

    private fun privacyInfo() {
        modal("Privacy", "Slate can't send data anywhere", listOf(MItem("Got it") { false }), body =
            "Slate doesn't request the internet permission, so Android blocks it from making any network connection.\n\n" +
            "Favorites, hidden apps and recent searches are stored only on this Fire TV. There are no ads, analytics or trackers.\n\n" +
            "Fire OS itself still talks to Amazon. The included setup script switches off the ad ID and disables Amazon's metrics and crash reporters where your Fire OS version allows it.",
            width = 560)
    }

    // ---------------------------------------------------------------- clock

    private fun updateClock() {
        val now = Date()
        val is24 = DateFormat.is24HourFormat(this)
        clock.text = SimpleDateFormat(if (is24) "H:mm" else "h:mm", Locale.getDefault()).format(now)
        ampm.text = if (is24) "" else SimpleDateFormat("a", Locale.getDefault()).format(now)
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greet = when (h) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night" }
        date.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now) + "   ·   " + greet
    }
}
