package com.ayush.slate

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MItem(
    val label: String,
    val value: (() -> String)? = null,
    val focusable: Boolean = true,
    val action: (() -> Boolean)? = null   // return true to keep the modal open
)

/** Dimmed full-screen layer. Everything modal sits on one of these. */
open class Layer(ctx: Context) : FrameLayout(ctx) {
    var onClose: (() -> Unit)? = null
    var firstFocus: View? = null
    init {
        setBackgroundColor(0xB8000000.toInt())
        isClickable = true
        noClip()
    }
    fun close() { onClose?.invoke() }
}

/** A centred, glassy list panel — used for app options, settings and pickers. */
class ListModal(
    ctx: Context,
    title: String,
    subtitle: String?,
    private val items: List<MItem>,
    private val accent: () -> Accent,
    body: String? = null,
    widthDp: Int = 480
) : Layer(ctx) {
    private val rows = ArrayList<Pair<MItem, TextView?>>()

    init {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Look.PANEL, ctx.dpf(22), ctx.dp(1), 0x1FFFFFFF)
            setPadding(ctx.dp(14), ctx.dp(22), ctx.dp(14), ctx.dp(14))
            elevation = ctx.dpf(24)
            noClip()
        }
        val a = accent()
        val bar = View(ctx).apply { background = gradient(a.a, a.b, ctx.dpf(2), GradientDrawable.Orientation.LEFT_RIGHT) }
        card.addView(bar, LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(4)).apply { leftMargin = ctx.dp(12); bottomMargin = ctx.dp(14) })
        card.addView(text(ctx, title, 22f, Look.TEXT, Look.medium).apply { setPadding(ctx.dp(12), 0, ctx.dp(12), 0) })
        if (subtitle != null) card.addView(text(ctx, subtitle, 13f, Look.TEXT_DIM).apply {
            setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), 0)
        })
        if (body != null) card.addView(text(ctx, body, 14f, Look.TEXT_DIM).apply {
            setLineSpacing(ctx.dpf(4), 1f)
            setPadding(ctx.dp(12), ctx.dp(14), ctx.dp(12), ctx.dp(4))
        })

        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; noClip(); setPadding(0, ctx.dp(12), 0, 0) }
        for (it in items) list.addView(row(it))
        val maxRows = 7
        if (items.size > maxRows) {
            val sv = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; isFillViewport = true; noClip() }
            sv.addView(list)
            card.addView(sv, LinearLayout.LayoutParams(-1, ctx.dp(54) * maxRows + ctx.dp(12)))
        } else card.addView(list)

        addView(card, LayoutParams(ctx.dp(widthDp), -2, Gravity.CENTER))
        card.alpha = 0f; card.scaleX = 0.94f; card.scaleY = 0.94f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun row(item: MItem): View {
        val ctx = context
        val r = ctx.dpf(14)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(14), 0, ctx.dp(14), 0)
            isFocusable = item.focusable; isClickable = item.focusable
        }
        val label = text(ctx, item.label, 16f, if (item.focusable) Look.TEXT else Look.TEXT_FAINT, Look.regular)
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        var valueView: TextView? = null
        if (item.value != null) {
            valueView = text(ctx, item.value.invoke(), 14f, Look.TEXT_DIM, Look.medium)
            row.addView(valueView)
        }
        rows.add(item to valueView)
        row.setOnFocusChangeListener { _, f ->
            val a = accent()
            row.background = if (f) gradient(withAlpha(a.a, 0.9f), withAlpha(a.b, 0.75f), r, GradientDrawable.Orientation.LEFT_RIGHT) else null
            label.setTextColor(if (f) Color.WHITE else Look.TEXT)
            valueView?.setTextColor(if (f) Color.WHITE else Look.TEXT_DIM)
            row.animate().scaleX(if (f) 1.02f else 1f).scaleY(if (f) 1.02f else 1f).setDuration(150).start()
        }
        row.setOnClickListener {
            val keep = item.action?.invoke() ?: false
            if (keep) refresh() else close()
        }
        if (firstFocus == null && item.focusable) firstFocus = row
        row.layoutParams = LinearLayout.LayoutParams(-1, ctx.dp(52)).apply { bottomMargin = ctx.dp(2) }
        return row
    }

    fun refresh() { for ((item, tv) in rows) tv?.text = item.value?.invoke() ?: "" }
}

/** Full-screen search: type once, results open in EveryTV. Also finds installed apps. */
class SearchLayer(
    ctx: Context,
    private val apps: List<AppEntry>,
    private val target: AppEntry?,
    private val prefs: Prefs,
    private val accent: () -> Accent,
    private val onSearch: (String) -> Unit,
    private val onOpenApp: (AppEntry) -> Unit
) : Layer(ctx) {
    val input = EditText(ctx)
    private val results = LinearLayout(ctx)

    init {
        setBackgroundColor(0xEB05060B.toInt())
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; noClip() }

        val a = accent()
        val targetName = target?.label ?: "EveryTV"
        col.addView(text(ctx, "Search", 34f, Look.TEXT, Look.light))
        col.addView(text(ctx, "Channels, movies and series open straight in $targetName", 14f, Look.TEXT_DIM).apply {
            setPadding(0, ctx.dp(8), 0, ctx.dp(22))
        })

        val r = ctx.dpf(30)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(22), 0, ctx.dp(22), 0)
            background = rounded(0x17FFFFFF, r, ctx.dp(1), 0x26FFFFFF)
        }
        val icon = IconDrawable(IconDrawable.Kind.SEARCH, Look.TEXT_DIM)
        box.addView(ImageView(ctx).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(ctx.dp(24), ctx.dp(24)).apply { rightMargin = ctx.dp(14) })
        input.apply {
            hint = "Search $targetName"
            setHintTextColor(Look.TEXT_FAINT); setTextColor(Look.TEXT)
            textSize = 22f; typeface = Look.regular
            background = null; isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, id, ev ->
                val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
                if (id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_GO || enter) {
                    go(text.toString()); true
                } else false
            }
            setOnFocusChangeListener { _, f ->
                box.background = if (f) rounded(0x24FFFFFF, r, ctx.dp(2), a.b) else rounded(0x17FFFFFF, r, ctx.dp(1), 0x26FFFFFF)
                icon.setColor(if (f) Color.WHITE else Look.TEXT_DIM)
                if (f) showKeyboard()
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { render(s?.toString().orEmpty()) }
            })
        }
        box.addView(input, LinearLayout.LayoutParams(0, -1, 1f))
        col.addView(box, LinearLayout.LayoutParams(-1, ctx.dp(60)))

        results.orientation = LinearLayout.VERTICAL; results.noClip()
        results.setPadding(0, ctx.dp(22), 0, 0)
        col.addView(results, LinearLayout.LayoutParams(-1, -2))

        addView(col, LayoutParams(ctx.dp(760), -2, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = ctx.dp(56) })
        firstFocus = input
        render("")
        alpha = 0f; animate().alpha(1f).setDuration(180).start()
    }

    private fun showKeyboard() {
        postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 120)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun go(q: String) {
        val t = q.trim(); if (t.isEmpty()) return
        hideKeyboard(); onSearch(t)
    }

    private fun header(s: String) = text(context, s.uppercase(), 12f, Look.TEXT_FAINT, Look.medium).apply {
        letterSpacing = 0.14f; setPadding(context.dp(4), context.dp(18), 0, context.dp(12))
    }

    private fun render(q: String) {
        val ctx = context
        results.removeAllViews()
        val t = q.trim()
        if (t.isEmpty()) {
            val rec = prefs.recents
            if (rec.isEmpty()) {
                results.addView(text(ctx, "Tip: press OK to open the keyboard, or use its mic to search by voice.", 14f, Look.TEXT_FAINT).apply {
                    setPadding(ctx.dp(4), ctx.dp(8), 0, 0)
                })
                return
            }
            results.addView(header("Recent"))
            val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; noClip() }
            val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; noClip(); setPadding(ctx.dp(4), ctx.dp(6), ctx.dp(4), ctx.dp(6)) }
            for (r in rec) {
                val chip = Pill(ctx, r, null, accent)
                chip.setOnClickListener { input.setText(r); go(r) }
                chips.addView(chip, LinearLayout.LayoutParams(-2, ctx.dp(44)).apply { rightMargin = ctx.dp(12) })
            }
            hs.addView(chips); results.addView(hs)
            return
        }

        // Primary action: search EveryTV.
        val a = accent()
        val r = ctx.dpf(18)
        val action = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(22), 0, ctx.dp(22), 0)
            isFocusable = true; isClickable = true
            background = rounded(0x12FFFFFF, r)
        }
        val arrow = IconDrawable(IconDrawable.Kind.ARROW, Look.TEXT_DIM)
        val label = text(ctx, if (target != null) "Search ${target.label} for “$t”" else "EveryTV isn't installed — choose a search app in Settings", 18f, Look.TEXT, Look.medium)
        action.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        action.addView(ImageView(ctx).apply { setImageDrawable(arrow) }, LinearLayout.LayoutParams(ctx.dp(22), ctx.dp(22)))
        action.setOnFocusChangeListener { _, f ->
            action.background = if (f) gradient(a.a, a.b, r, GradientDrawable.Orientation.LEFT_RIGHT) else rounded(0x12FFFFFF, r)
            arrow.setColor(if (f) Color.WHITE else Look.TEXT_DIM)
            action.animate().scaleX(if (f) 1.02f else 1f).scaleY(if (f) 1.02f else 1f).setDuration(150).start()
        }
        action.setOnClickListener { go(t) }
        results.addView(action, LinearLayout.LayoutParams(-1, ctx.dp(66)))

        val lower = t.lowercase()
        val matches = apps.filter { it.label.lowercase().contains(lower) }
            .sortedBy { if (it.label.lowercase().startsWith(lower)) 0 else 1 }.take(8)
        if (matches.isNotEmpty()) {
            results.addView(header("Apps"))
            val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; noClip() }
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; noClip(); setPadding(ctx.dp(4), ctx.dp(10), ctx.dp(20), ctx.dp(10)) }
            for (m in matches) {
                val card = AppCard(ctx, m, ctx.dp(150), ctx.dp(84), accent)
                Apps.cached(m.pkg)?.let { card.bind(it) }
                card.frame.setOnClickListener { hideKeyboard(); onOpenApp(m) }
                row.addView(card, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = ctx.dp(18) })
            }
            hs.addView(row); results.addView(hs)
        }
    }
}
