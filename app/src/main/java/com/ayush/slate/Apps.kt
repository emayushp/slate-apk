package com.ayush.slate

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(val pkg: String, val label: String, val component: ComponentName)

class Visual(val banner: Drawable?, val icon: Drawable?, val color: Int)

object Apps {
    const val AMAZON_HOME = "com.amazon.tv.launcher"

    private val visuals = ConcurrentHashMap<String, Visual>()

    /** Well-known Fire TV packages used to seed Favorites on first run (only if installed). */
    private val seedPackages = listOf(
        "com.netflix.ninja",                 // Netflix
        "com.amazon.firebat",                // Prime Video
        "com.amazon.firetv.youtube",         // YouTube (Fire TV)
        "com.google.android.youtube.tv",     // YouTube (Android TV build)
        "com.disney.disneyplus",             // Disney+
        "com.spotify.tv.android",            // Spotify
        "com.plexapp.android",               // Plex
        "org.xbmc.kodi"                      // Kodi
    )

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val map = LinkedHashMap<String, AppEntry>()
        fun add(category: String) {
            val list = try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            } catch (e: Exception) { emptyList() }
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { ai.packageName }
                // Leanback (TV) entries are added second, so they win over phone launcher entries.
                map[ai.packageName] = AppEntry(ai.packageName, label, ComponentName(ai.packageName, ai.name))
            }
        }
        add(Intent.CATEGORY_LAUNCHER)
        add(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return map.values.sortedBy { it.label.lowercase() }
    }

    fun seedFavorites(apps: List<AppEntry>, searchPkg: String?): MutableList<String> {
        val installed = apps.map { it.pkg }.toSet()
        val out = ArrayList<String>()
        if (searchPkg != null && searchPkg in installed) out.add(searchPkg)
        seedPackages.filter { it in installed }.forEach { out.add(it) }
        if (out.size < 4) {
            apps.filter { !it.pkg.startsWith("com.amazon.") }.forEach { if (out.size < 6) out.add(it.pkg) }
        }
        return out.distinct().take(8).toMutableList()
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun findEveryTV(apps: List<AppEntry>): AppEntry? =
        apps.firstOrNull { norm(it.label).contains("everytv") }
            ?: apps.firstOrNull { norm(it.label).contains("novatv") || norm(it.label) == "notv" }
            ?: apps.firstOrNull { it.pkg.contains("everytv", true) || it.pkg.contains("notv", true) }

    fun launchIntent(ctx: Context, e: AppEntry): Intent {
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(e.pkg)
            ?: pm.getLaunchIntentForPackage(e.pkg)
            ?: Intent(Intent.ACTION_MAIN).setComponent(e.component)
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    /**
     * Hands a search to EveryTV. Prefers a proper ACTION_SEARCH activity; otherwise opens the
     * app's main screen with the query attached as extras (see README for the EveryTV side).
     */
    fun search(ctx: Context, e: AppEntry, query: String): Boolean {
        val pm = ctx.packageManager
        val s = Intent(Intent.ACTION_SEARCH)
            .setPackage(e.pkg)
            .putExtra(SearchManager.QUERY, query)
            .putExtra("query", query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            if (pm.queryIntentActivities(s, 0).isNotEmpty()) {
                ctx.startActivity(s)
            } else {
                val l = launchIntent(ctx, e)
                    .putExtra(SearchManager.QUERY, query)
                    .putExtra("query", query)
                    .putExtra("everytv.search", query)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ctx.startActivity(l)
            }
            true
        } catch (ex: Exception) { false }
    }

    fun cached(pkg: String): Visual? = visuals[pkg]

    fun clearCache(pkg: String? = null) { if (pkg == null) visuals.clear() else visuals.remove(pkg) }

    /** Blocking — call off the main thread. */
    fun visual(ctx: Context, e: AppEntry): Visual {
        visuals[e.pkg]?.let { return it }
        val pm = ctx.packageManager
        val ai = try { pm.getActivityInfo(e.component, 0) } catch (x: Exception) { null }
        var banner: Drawable? = try { ai?.loadBanner(pm) } catch (x: Exception) { null }
        if (banner == null) banner = try { pm.getApplicationBanner(e.pkg) } catch (x: Exception) { null }

        var icon: Drawable? = null
        // Ask for the sharpest icon the app ships, so it stays crisp on a 1080p/4K TV.
        try {
            if (ai != null) {
                val res = pm.getResourcesForApplication(ai.applicationInfo)
                val id = if (ai.iconResource != 0) ai.iconResource else ai.applicationInfo.icon
                if (id != 0) icon = res.getDrawableForDensity(id, 640, null)
            }
        } catch (x: Exception) { }
        if (icon == null) icon = try { pm.getActivityIcon(e.component) } catch (x: Exception) { null }

        val color = dominant(banner ?: icon)
        return Visual(banner, icon, color).also { visuals[e.pkg] = it }
    }

    fun toBitmap(d: Drawable, w: Int, h: Int): Bitmap {
        val copy = d.constantState?.newDrawable()?.mutate() ?: d
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        copy.setBounds(0, 0, w, h)
        copy.draw(c)
        return b
    }

    /** A pleasant, saturated "brand" colour pulled from the icon/banner. */
    fun dominant(d: Drawable?): Int {
        if (d == null) return 0xFF5B6CFF.toInt()
        return try {
            val b = toBitmap(d, 24, 24)
            var r = 0.0; var g = 0.0; var bl = 0.0; var wSum = 0.0
            val hsv = FloatArray(3)
            for (y in 0 until 24) for (x in 0 until 24) {
                val p = b.getPixel(x, y)
                if (Color.alpha(p) < 140) continue
                Color.colorToHSV(p, hsv)
                val w = hsv[1] * hsv[2] + 0.04
                r += Color.red(p) * w; g += Color.green(p) * w; bl += Color.blue(p) * w; wSum += w
            }
            b.recycle()
            if (wSum <= 0) return 0xFF5B6CFF.toInt()
            val avg = Color.rgb((r / wSum).toInt(), (g / wSum).toInt(), (bl / wSum).toInt())
            Color.colorToHSV(avg, hsv)
            hsv[1] = hsv[1].coerceIn(0.35f, 0.85f)
            hsv[2] = 0.78f
            Color.HSVToColor(hsv)
        } catch (x: Exception) { 0xFF5B6CFF.toInt() }
    }
}
