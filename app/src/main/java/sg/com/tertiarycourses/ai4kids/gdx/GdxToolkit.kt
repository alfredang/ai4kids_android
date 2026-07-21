package sg.com.tertiarycourses.ai4kids.gdx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A tiny ShapeDrawer-style helper — reimplemented here (NOT the library) so the
 * app keeps its "no third-party SDKs" stance. It draws **anti-aliased** filled
 * circles and round-capped lines through an existing [Batch], so shapes batch
 * together with text/sprites instead of flushing a separate ShapeRenderer.
 *
 * The smooth edge is the same trick ShapeDrawer uses: render the shape from a
 * texture whose alpha feathers out at the rim. We generate that texture once.
 */
class SmoothDraw : Disposable {

    /** Soft white disc: alpha = 1 inside, feathering to 0 across the last ~1.5px. */
    private val disc: Texture
    /** Soft upward triangle (apex at top), feathered edges, for pictograms. */
    private val tri: Texture
    /** 1×1 white texel for line/rect bodies. */
    private val pixel: Texture

    init {
        val size = 128
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None
        val c = size / 2f
        val r = c - 1f
        val edge = 1.5f
        for (y in 0 until size) for (x in 0 until size) {
            val dx = x + 0.5f - c
            val dy = y + 0.5f - c
            val d = sqrt(dx * dx + dy * dy)
            val a = ((r - d) / edge + 1f).coerceIn(0f, 1f) // 1 inside, 0 past the rim
            pm.setColor(1f, 1f, 1f, a)
            pm.drawPixel(x, y)
        }
        disc = Texture(pm).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        pm.dispose()

        tri = buildTriangle(96)

        val pp = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pp.setColor(Color.WHITE); pp.fill()
        pixel = Texture(pp)
        pp.dispose()
    }

    /** An anti-aliased isoceles triangle, apex at the top, via edge SDF coverage. */
    private fun buildTriangle(n: Int): Texture {
        val pm = Pixmap(n, n, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None
        val ax = n / 2f; val ay = 0.5f                 // apex (top)
        val bx = 0.5f; val by = n - 0.5f               // bottom-left
        val cx2 = n - 0.5f; val cy2 = n - 0.5f         // bottom-right
        val edges = arrayOf(floatArrayOf(ax, ay, bx, by), floatArrayOf(bx, by, cx2, cy2), floatArrayOf(cx2, cy2, ax, ay))
        val cenx = (ax + bx + cx2) / 3f; val ceny = (ay + by + cy2) / 3f
        for (y in 0 until n) for (x in 0 until n) {
            var sd = Float.MAX_VALUE
            for (e in edges) {
                val ex = e[2] - e[0]; val ey = e[3] - e[1]
                val len = sqrt(ex * ex + ey * ey)
                val sgn = if (ex * (ceny - e[1]) - ey * (cenx - e[0]) >= 0f) 1f else -1f
                val dist = (ex * (y + 0.5f - e[1]) - ey * (x + 0.5f - e[0])) / len * sgn
                sd = min(sd, dist)
            }
            pm.setColor(1f, 1f, 1f, (sd / 1.2f + 0.5f).coerceIn(0f, 1f))
            pm.drawPixel(x, y)
        }
        return Texture(pm).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }.also { pm.dispose() }
    }

    /** An anti-aliased filled circle, centred at ([cx],[cy]). */
    fun circle(batch: Batch, cx: Float, cy: Float, radius: Float, color: Color) {
        val prev = batch.packedColor
        batch.setColor(color)
        val d = radius * 2f
        batch.draw(disc, cx - radius, cy - radius, d, d)
        batch.packedColor = prev
    }

    /** A round-capped line of the given [thickness]. */
    fun line(batch: Batch, x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Color) {
        val prev = batch.packedColor
        batch.setColor(color)
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        val ang = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees
        batch.draw(
            pixel, x1, y1 - thickness / 2f, 0f, thickness / 2f, len, thickness,
            1f, 1f, ang, 0, 0, 1, 1, false, false,
        )
        batch.packedColor = prev
        circle(batch, x1, y1, thickness / 2f, color)
        circle(batch, x2, y2, thickness / 2f, color)
    }

    /** A crisp filled rectangle (axis-aligned edges need no anti-aliasing). */
    fun rect(batch: Batch, x: Float, y: Float, w: Float, h: Float, color: Color) {
        val prev = batch.packedColor
        batch.setColor(color)
        batch.draw(pixel, x, y, w, h)
        batch.packedColor = prev
    }

    /** An anti-aliased triangle filling ([x],[y],[w],[h]); apex up, or down if
     *  [pointDown]. Optionally rotated [rotationDeg] degrees (counter-clockwise)
     *  about the pivot ([originX],[originY]) measured from ([x],[y]) — e.g. an
     *  origin of (w/2, 0) pivots about the base centre, to aim a spike outward. */
    fun triangle(
        batch: Batch, x: Float, y: Float, w: Float, h: Float, color: Color,
        pointDown: Boolean = false, rotationDeg: Float = 0f, originX: Float = 0f, originY: Float = 0f,
    ) {
        val prev = batch.packedColor
        batch.setColor(color)
        batch.draw(tri, x, y, originX, originY, w, h, 1f, 1f, rotationDeg, 0, 0, tri.width, tri.height, false, pointDown)
        batch.packedColor = prev
    }

    override fun dispose() {
        disc.dispose()
        tri.dispose()
        pixel.dispose()
    }
}

/**
 * A tiny TextraTypist-style rich-text renderer — reimplemented here (NOT the
 * library). It supports the pieces this app actually needs, asset-free:
 *  - **typewriter** reveal (a la `TypingLabel`): pass [reveal] = chars to show.
 *  - inline **colour markup**: `"[#FFCC00]gold[] plain"` — `[#rrggbb]` opens a
 *    colour, `[]` resets to the base colour.
 *  - inline **icon tokens**: `":g7:"` emits an icon of "kind" 7 via the caller's
 *    [icon] lambda (our shape-pictogram stand-in for an emoji atlas).
 *
 * One centred line is drawn with a fixed box (sized to the *full* text) so a
 * typewriter reveals left-to-right without the line jittering as it grows.
 */
object RichText {

    private sealed interface Tok
    private class Span(val text: String, val color: Color) : Tok
    private class Icon(val kind: Int) : Tok

    /** Printable length (markup stripped; each icon counts as one character). */
    fun length(markup: String): Int = parse(markup, Color.WHITE).sumOf { if (it is Span) it.text.length else 1 }

    /**
     * Draw [markup] centred at [cx], vertically centred on [cy]. Reveals only the
     * first [reveal] printable characters (for a typewriter). [iconSize] is the
     * icon radius; [icon] draws one: `(kind, centreX, centreY, size)`.
     */
    fun draw(
        batch: Batch,
        font: BitmapFont,
        layout: GlyphLayout,
        markup: String,
        cx: Float,
        cy: Float,
        scale: Float,
        base: Color,
        reveal: Int = Int.MAX_VALUE,
        iconSize: Float = 0f,
        icon: ((kind: Int, x: Float, y: Float, size: Float) -> Unit)? = null,
    ) {
        font.data.setScale(scale)
        val toks = parse(markup, base)
        val iconW = iconSize * 2f + 6f

        var full = 0f
        for (t in toks) full += when (t) {
            is Span -> { layout.setText(font, t.text); layout.width }
            is Icon -> iconW
        }

        var x = cx - full / 2f
        var left = reveal
        for (t in toks) {
            if (left <= 0) break
            when (t) {
                is Span -> {
                    val show = if (t.text.length <= left) t.text else t.text.substring(0, left)
                    left -= show.length
                    font.color = t.color
                    layout.setText(font, show)
                    font.draw(batch, layout, x, cy + layout.height / 2f)
                    layout.setText(font, t.text)
                    x += layout.width
                }
                is Icon -> {
                    left -= 1
                    icon?.invoke(t.kind, x + iconW / 2f, cy, iconSize)
                    x += iconW
                }
            }
        }
    }

    private fun parse(markup: String, base: Color): List<Tok> {
        val out = ArrayList<Tok>()
        val sb = StringBuilder()
        var color = base
        var i = 0
        fun flush() { if (sb.isNotEmpty()) { out.add(Span(sb.toString(), color)); sb.setLength(0) } }
        while (i < markup.length) {
            val ch = markup[i]
            if (ch == '[') {
                val end = markup.indexOf(']', i)
                if (end >= 0) {
                    flush()
                    val tag = markup.substring(i + 1, end)
                    color = if (tag.isEmpty()) base else parseColor(tag, base)
                    i = end + 1
                    continue
                }
            } else if (ch == ':' && i + 3 < markup.length && markup[i + 1] == 'g' && markup[i + 3] == ':') {
                val k = markup[i + 2] - '0'
                if (k in 0..9) { flush(); out.add(Icon(k)); i += 4; continue }
            }
            sb.append(ch)
            i++
        }
        flush()
        return out
    }

    private fun parseColor(tag: String, base: Color): Color = when {
        tag.startsWith("#") -> runCatching { Color.valueOf(tag.substring(1)) }.getOrDefault(base)
        else -> runCatching { Color.valueOf(tag) }.getOrDefault(base)
    }
}
