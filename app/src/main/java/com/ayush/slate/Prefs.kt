package com.ayush.slate

import android.content.Context

/** Everything Slate remembers lives here, on the device only. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("slate", Context.MODE_PRIVATE)

    var favorites: MutableList<String>
        get() = sp.getString("fav", "")!!.split('|').filter { it.isNotBlank() }.toMutableList()
        set(v) = sp.edit().putString("fav", v.distinct().joinToString("|")).apply()

    var hidden: Set<String>
        get() = HashSet(sp.getStringSet("hidden", emptySet())!!)
        set(v) = sp.edit().putStringSet("hidden", HashSet(v)).apply()

    var accent: Int
        get() = sp.getInt("accent", 0)
        set(v) = sp.edit().putInt("accent", v).apply()

    var motion: Boolean
        get() = sp.getBoolean("motion", true)
        set(v) = sp.edit().putBoolean("motion", v).apply()

    var showGreeting: Boolean
        get() = sp.getBoolean("greeting", true)
        set(v) = sp.edit().putBoolean("greeting", v).apply()

    var searchPkg: String?
        get() = sp.getString("searchPkg", null)
        set(v) = sp.edit().putString("searchPkg", v).apply()

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = sp.edit().putBoolean("seeded", v).apply()

    var recents: List<String>
        get() = sp.getString("recents", "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("recents", v.take(8).joinToString("\n")).apply()

    fun addRecent(q: String) {
        val list = recents.filterNot { it.equals(q, ignoreCase = true) }.toMutableList()
        list.add(0, q)
        recents = list
    }

    /** Home-button takeover (used by HomeWatcher). */
    var takeover: Boolean
        get() = sp.getBoolean("takeover", true)
        set(v) = sp.edit().putBoolean("takeover", v).apply()

    var bypass: Boolean
        get() = sp.getBoolean("bypass", false)
        set(v) = sp.edit().putBoolean("bypass", v).putLong("bypassAt", System.currentTimeMillis()).apply()

    fun bypassActive(): Boolean =
        bypass && System.currentTimeMillis() - sp.getLong("bypassAt", 0) < 30 * 60_000L

    /** Last few Amazon-launcher window classes seen, for troubleshooting the Home button. */
    var seenClasses: List<String>
        get() = sp.getString("seen", "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("seen", v.take(6).joinToString("\n")).apply()

    fun noteSeen(cls: String) {
        if (cls.isBlank()) return
        val cur = seenClasses
        if (cur.firstOrNull() == cls) return
        seenClasses = listOf(cls) + cur.filterNot { it == cls }
    }
}
