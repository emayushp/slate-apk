package com.ayush.slate

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Number): Float = v.toFloat() * resources.displayMetrics.density

data class Accent(val name: String, val a: Int, val b: Int)

object Look {
    val accents = listOf(
        Accent("Aurora", 0xFF7C5CFF.toInt(), 0xFF22D3EE.toInt()),
        Accent("Ember", 0xFFFF5F3D.toInt(), 0xFFFFB547.toInt()),
        Accent("Mint", 0xFF19D9A6.toInt(), 0xFF2CA8FF.toInt()),
        Accent("Rose", 0xFFFF4D8D.toInt(), 0xFFA155FF.toInt()),
        Accent("Mono", 0xFFE6E8EE.toInt(), 0xFF7D8499.toInt())
    )
    const val BG_TOP = 0xFF05060B.toInt()
    const val BG_BOTTOM = 0xFF0A0E1A.toInt()
    const val CARD = 0xFF161B2A.toInt()
    const val PANEL = 0xF2121624.toInt()
    const val TEXT = 0xFFF4F6FB.toInt()
    const val TEXT_DIM = 0xFF9AA3B8.toInt()
    const val TEXT_FAINT = 0xFF5D667C.toInt()

    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val thin: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
}

fun rounded(color: Int, radius: Float, strokePx: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    if (strokePx > 0) setStroke(strokePx, strokeColor)
}

fun gradient(c1: Int, c2: Int, radius: Float, o: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR) =
    GradientDrawable(o, intArrayOf(c1, c2)).apply { cornerRadius = radius }

fun withAlpha(c: Int, a: Float) = Color.argb((a * 255).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

fun darken(c: Int, f: Float): Int {
    val hsv = FloatArray(3); Color.colorToHSV(c, hsv); hsv[2] *= f; return Color.HSVToColor(Color.alpha(c), hsv)
}

fun text(ctx: Context, s: CharSequence = "", sizeSp: Float, color: Int = Look.TEXT, tf: Typeface = Look.regular) =
    TextView(ctx).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp); setTextColor(color); typeface = tf
        includeFontPadding = false
    }

fun View.roundClip(radius: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }
    clipToOutline = true
}

fun ViewGroup.noClip() { clipChildren = false; clipToPadding = false }

/** Tiny vector icons drawn in code, so Slate ships with zero image assets for its UI. */
class IconDrawable(private val kind: Kind, color: Int) : Drawable() {
    enum class Kind { SEARCH, SLIDERS, HOME, GEAR, ARROW }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    fun setColor(c: Int) { p.color = c; invalidateSelf() }
    override fun draw(c: Canvas) {
        val b = bounds; val s = b.width().coerceAtMost(b.height()).toFloat()
        val x0 = b.left + (b.width() - s) / 2f; val y0 = b.top + (b.height() - s) / 2f
        p.strokeWidth = s * 0.11f
        when (kind) {
            Kind.SEARCH -> {
                c.drawCircle(x0 + s * 0.42f, y0 + s * 0.42f, s * 0.27f, p)
                c.drawLine(x0 + s * 0.62f, y0 + s * 0.62f, x0 + s * 0.86f, y0 + s * 0.86f, p)
            }
            Kind.SLIDERS -> {
                val ys = floatArrayOf(0.25f, 0.5f, 0.75f); val ks = floatArrayOf(0.66f, 0.34f, 0.58f)
                for (i in 0..2) {
                    c.drawLine(x0 + s * 0.14f, y0 + s * ys[i], x0 + s * 0.86f, y0 + s * ys[i], p)
                    val fill = Paint(p).apply { style = Paint.Style.FILL }
                    c.drawCircle(x0 + s * ks[i], y0 + s * ys[i], s * 0.09f, fill)
                }
            }
            Kind.HOME -> {
                val path = Path().apply {
                    moveTo(x0 + s * 0.16f, y0 + s * 0.48f); lineTo(x0 + s * 0.5f, y0 + s * 0.18f); lineTo(x0 + s * 0.84f, y0 + s * 0.48f)
                    moveTo(x0 + s * 0.26f, y0 + s * 0.42f); lineTo(x0 + s * 0.26f, y0 + s * 0.82f); lineTo(x0 + s * 0.74f, y0 + s * 0.82f); lineTo(x0 + s * 0.74f, y0 + s * 0.42f)
                }
                c.drawPath(path, p)
            }
            Kind.GEAR -> {
                c.drawCircle(x0 + s / 2, y0 + s / 2, s * 0.16f, p)
                for (i in 0 until 8) {
                    val a = Math.PI * i / 4
                    c.drawLine(
                        (x0 + s / 2 + cos(a) * s * 0.28).toFloat(), (y0 + s / 2 + sin(a) * s * 0.28).toFloat(),
                        (x0 + s / 2 + cos(a) * s * 0.4).toFloat(), (y0 + s / 2 + sin(a) * s * 0.4).toFloat(), p
                    )
                }
            }
            Kind.ARROW -> {
                c.drawLine(x0 + s * 0.2f, y0 + s * 0.5f, x0 + s * 0.8f, y0 + s * 0.5f, p)
                c.drawLine(x0 + s * 0.55f, y0 + s * 0.25f, x0 + s * 0.8f, y0 + s * 0.5f, p)
                c.drawLine(x0 + s * 0.55f, y0 + s * 0.75f, x0 + s * 0.8f, y0 + s * 0.5f, p)
            }
        }
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Ambient background: deep navy gradient with two soft glows that drift slowly and
 * take on the colour of whatever app is focused. Throttled to ~20 fps to stay light on a Fire Stick.
 */
class AmbientView(ctx: Context) : View(ctx) {
    private val base = Paint()
    private val glowA = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowB = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignette = Paint()
    private var colorA = Look.accents[0].a
    private var colorB = Look.accents[0].b
    private var anim: ValueAnimator? = null
    var motion = true
        set(v) { field = v; invalidate() }
    var running = true
        set(v) { field = v; if (v) invalidate() }

    fun setColors(a: Int, b: Int, animate: Boolean = true) {
        if (!animate || width == 0) { colorA = a; colorB = b; rebuild(); invalidate(); return }
        val fromA = colorA; val fromB = colorB
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700; interpolator = DecelerateInterpolator()
            val ev = ArgbEvaluator()
            addUpdateListener {
                val f = it.animatedValue as Float
                colorA = ev.evaluate(f, fromA, a) as Int
                colorB = ev.evaluate(f, fromB, b) as Int
                rebuild(); invalidate()
            }
            start()
        }
    }

    private fun rebuild() {
        if (width == 0) return
        val r = width * 0.55f
        glowA.shader = RadialGradient(0f, 0f, r, intArrayOf(withAlpha(colorA, 0.42f), withAlpha(colorA, 0.12f), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        glowB.shader = RadialGradient(0f, 0f, r * 0.85f, intArrayOf(withAlpha(colorB, 0.30f), withAlpha(colorB, 0.08f), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        base.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Look.BG_TOP, Look.BG_BOTTOM, Shader.TileMode.CLAMP)
        vignette.shader = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), 0, 0xB3000000.toInt(), Shader.TileMode.CLAMP)
        rebuild()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRect(0f, 0f, w, h, base)
        val t = if (motion) SystemClock.uptimeMillis() / 1000.0 else 0.0
        c.save(); c.translate((w * (0.18 + 0.06 * sin(t / 9))).toFloat(), (h * (0.12 + 0.07 * cos(t / 11))).toFloat())
        c.drawCircle(0f, 0f, w * 0.55f, glowA); c.restore()
        c.save(); c.translate((w * (0.86 + 0.05 * cos(t / 13))).toFloat(), (h * (0.78 + 0.06 * sin(t / 10))).toFloat())
        c.drawCircle(0f, 0f, w * 0.47f, glowB); c.restore()
        c.drawRect(0f, 0f, w, h, vignette)
        if (motion && running) postInvalidateDelayed(50)
    }
}

/** One app tile: rounded card, focus zoom, glow ring, light sweep and a name that fades in below. */
class AppCard(
    ctx: Context,
    val entry: AppEntry,
    val w: Int,
    val h: Int,
    private val accent: () -> Accent
) : LinearLayout(ctx) {
    val frame = FrameLayout(ctx)
    private val image = ImageView(ctx)
    private val iconView = ImageView(ctx)
    private val innerName = text(ctx, entry.label, 12f, Look.TEXT, Look.medium)
    private val ring = View(ctx)
    private val shine = View(ctx)
    val label = text(ctx, entry.label, 13f, Look.TEXT, Look.medium)
    private val radius = ctx.dpf(14)
    var moving = false
        set(v) { field = v; updateRing() }

    init {
        orientation = VERTICAL
        noClip()
        frame.layoutParams = LayoutParams(w, h)
        frame.background = rounded(Look.CARD, radius)
        frame.roundClip(radius)
        frame.isFocusable = true
        frame.isClickable = true
        frame.tag = entry

        image.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))

        val iconSize = (h * 0.46f).toInt()
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        frame.addView(iconView, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER).apply {
            bottomMargin = (h * 0.08f).toInt()
        })
        innerName.maxLines = 1; innerName.ellipsize = TextUtils.TruncateAt.END
        innerName.alpha = 0.85f
        frame.addView(innerName, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = ctx.dp(7); leftMargin = ctx.dp(8); rightMargin = ctx.dp(8)
        })

        shine.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF))
        shine.rotation = 18f
        val sw = (w * 0.38f).toInt()
        frame.addView(shine, FrameLayout.LayoutParams(sw, (h * 1.6f).toInt(), Gravity.CENTER_VERTICAL))
        shine.translationX = -sw * 1.5f

        ring.alpha = 0f
        frame.addView(ring, FrameLayout.LayoutParams(-1, -1))
        updateRing()

        addView(frame)
        label.maxLines = 1; label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER; label.alpha = 0f
        addView(label, LayoutParams(w, -2).apply { topMargin = (h * 0.1f).toInt() + ctx.dp(4) })

        frame.setOnFocusChangeListener { _, f -> onFocus(f) }
        showPlaceholder()
    }

    private fun updateRing() {
        val a = accent()
        ring.background = if (moving)
            rounded(0, radius, context.dp(3), a.b)
        else
            rounded(0, radius, context.dp(2.5f), 0xF2FFFFFF.toInt())
    }

    private fun showPlaceholder() {
        frame.background = rounded(Look.CARD, radius)
        innerName.visibility = VISIBLE
    }

    fun bind(v: Visual) {
        if (v.banner != null) {
            image.setImageDrawable(v.banner)
            image.visibility = VISIBLE
            iconView.visibility = GONE
            innerName.visibility = GONE
            frame.background = rounded(darken(v.color, 0.35f), radius)
        } else {
            image.visibility = GONE
            iconView.setImageDrawable(v.icon)
            iconView.visibility = VISIBLE
            innerName.visibility = VISIBLE
            frame.background = gradient(darken(v.color, 0.62f), darken(v.color, 0.22f), radius)
        }
    }

    private fun onFocus(f: Boolean) {
        val d = DecelerateInterpolator(2f)
        frame.animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f)
            .translationZ(if (f) context.dpf(16) else 0f).setDuration(220).setInterpolator(d).start()
        ring.animate().alpha(if (f) 1f else 0f).setDuration(180).start()
        label.animate().alpha(if (f) 1f else 0f).translationY(if (f) 0f else -context.dpf(6)).setDuration(220).setInterpolator(d).start()
        if (f) {
            val sw = shine.layoutParams.width.toFloat()
            shine.animate().cancel()
            shine.translationX = -sw * 1.5f
            shine.animate().translationX(w + sw).setStartDelay(140).setDuration(750).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    fun pulse(then: () -> Unit) {
        frame.animate().scaleX(1.02f).scaleY(1.02f).setDuration(90).withEndAction {
            frame.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).withEndAction(then).start()
        }.start()
    }
}

/** A rounded pill button (used in the top bar, system row and modals). */
class Pill(ctx: Context, label: String, icon: IconDrawable.Kind?, private val accent: () -> Accent, big: Boolean = false) : LinearLayout(ctx) {
    private val iconD = icon?.let { IconDrawable(it, Look.TEXT_DIM) }
    val textView = text(ctx, label, if (big) 17f else 15f, Look.TEXT_DIM, Look.medium)
    private val r = ctx.dpf(if (big) 26 else 22)

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val ph = ctx.dp(if (big) 22 else 18)
        setPadding(ph, 0, ph + ctx.dp(4), 0)
        isFocusable = true; isClickable = true
        background = rounded(0x14FFFFFF, r, ctx.dp(1), 0x1AFFFFFF)
        if (iconD != null) {
            val iv = ImageView(ctx); iv.setImageDrawable(iconD)
            val s = ctx.dp(if (big) 22 else 18)
            addView(iv, LayoutParams(s, s).apply { rightMargin = if (label.isEmpty()) 0 else ctx.dp(10) })
        }
        if (label.isNotEmpty()) addView(textView)
        setOnFocusChangeListener { _, f -> focus(f) }
    }

    private fun focus(f: Boolean) {
        val a = accent()
        background = if (f) gradient(a.a, a.b, r, GradientDrawable.Orientation.LEFT_RIGHT)
        else rounded(0x14FFFFFF, r, context.dp(1), 0x1AFFFFFF)
        textView.setTextColor(if (f) Color.WHITE else Look.TEXT_DIM)
        iconD?.setColor(if (f) Color.WHITE else Look.TEXT_DIM)
        animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(180).start()
    }
}
