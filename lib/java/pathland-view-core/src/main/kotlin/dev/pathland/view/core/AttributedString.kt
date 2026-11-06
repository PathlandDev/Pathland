package dev.pathland.view.core

import dev.pathland.view.core.protocol.Properties
import dev.pathland.view.core.protocol.ValueTypes
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * A scalar text-format attribute on a range.
 *
 * [value] is the standard 4-byte wire value (an `F32` bit pattern for float
 * attributes, a packed `0xAARRGGBB` for colors, `0`/`1` for booleans).
 */
data class TextAttribute(
    val property: Int,
    val valueType: Int = ValueTypes.forProperty(property),
    val value: Int,
) {
    companion object {
        @JvmStatic fun fontSize(points: Float) =
            TextAttribute(Properties.FONT_SIZE, ValueTypes.F32, points.toRawBits())

        @JvmStatic fun fontWeight(weight: Float) =
            TextAttribute(Properties.FONT_WEIGHT, ValueTypes.F32, weight.toRawBits())

        @JvmStatic fun color(argb: Int) =
            TextAttribute(Properties.COLOR, ValueTypes.COLOR, argb)

        @JvmStatic fun italic() =
            TextAttribute(Properties.FONT_STYLE, ValueTypes.F32, 1.0f.toRawBits())

        @JvmStatic fun fontDesign(code: Int) =
            TextAttribute(Properties.FONT_DESIGN, ValueTypes.F32, code.toFloat().toRawBits())

        @JvmStatic fun underline(on: Boolean = true) =
            TextAttribute(Properties.UNDERLINE, ValueTypes.U8, if (on) 1 else 0)

        @JvmStatic fun strikethrough(on: Boolean = true) =
            TextAttribute(Properties.STRIKETHROUGH, ValueTypes.U8, if (on) 1 else 0)
    }
}

/** A range of the text with a set of scalar attributes (UTF-8 byte offsets). */
data class StyleRun(
    val start: Int,
    val end: Int,
    val attributes: List<TextAttribute>,
) {
    fun bold() = copy(attributes = attributes + TextAttribute.fontWeight(700f))
    fun italic() = copy(attributes = attributes + TextAttribute.italic())
    fun monospaced() = copy(attributes = attributes + TextAttribute.fontDesign(3))
    fun strikethrough() = copy(attributes = attributes + TextAttribute.strikethrough())
    fun underline() = copy(attributes = attributes + TextAttribute.underline())

    companion object {
        @JvmStatic fun of(start: Int, end: Int) = StyleRun(start, end, emptyList())
    }
}

/** A range of the text that is a hyperlink (UTF-8 byte offsets). */
data class LinkRun(val start: Int, val end: Int, val url: String)

/**
 * Text plus range-scoped style runs and links — the app-side model for
 * `PARAMETER::SET_ATTRIBUTED_TEXT`.
 *
 * Ranges are **UTF-8 byte offsets** (the wire convention). The [Builder] takes
 * Kotlin character indices and converts them at [Builder.build]; markdown
 * payloads decode directly into byte offsets.
 */
class AttributedString private constructor(
    val text: String,
    val runs: List<StyleRun>,
    val links: List<LinkRun>,
) {
    init {
        val length = text.toByteArray(StandardCharsets.UTF_8).size
        for (run in runs) {
            require(run.start in 0..run.end && run.end <= length) {
                "style run ${run.start}..${run.end} is outside the text ($length bytes)"
            }
        }
        for (link in links) {
            require(link.start in 0..link.end && link.end <= length) {
                "link ${link.start}..${link.end} is outside the text ($length bytes)"
            }
        }
    }

    /** Whether this carries no style runs or links (plain text). */
    val isPlain: Boolean
        get() = runs.isEmpty() && links.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is AttributedString &&
            text == other.text &&
            runs == other.runs &&
            links == other.links

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + runs.hashCode()
        result = 31 * result + links.hashCode()
        return result
    }

    override fun toString(): String =
        "AttributedString(text=$text, runs=$runs, links=$links)"

    /** Encode the `SET_ATTRIBUTED_TEXT` payload (without the arena length wrapper). */
    fun encode(): ByteArray {
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.writeIntLE(textBytes.size)
        out.write(textBytes)
        out.writeIntLE(runs.size)
        for (run in runs) {
            out.writeIntLE(run.start)
            out.writeIntLE(run.end)
            val supported = run.attributes.filter { isSupported(it) }
            out.writeIntLE(supported.size)
            for (attr in supported) {
                out.writeShortLE(attr.property)
                out.write(attr.valueType and 0xFF)
                out.write(0)
                out.writeIntLE(attr.value)
            }
        }
        out.writeIntLE(links.size)
        for (link in links) {
            val url = link.url.toByteArray(StandardCharsets.UTF_8)
            out.writeIntLE(link.start)
            out.writeIntLE(link.end)
            out.writeIntLE(url.size)
            out.write(url)
        }
        return out.toByteArray()
    }

    companion object {
        private val SUPPORTED = setOf(
            Properties.FONT_SIZE,
            Properties.FONT_WEIGHT,
            Properties.COLOR,
            Properties.FONT_STYLE,
            Properties.FONT_DESIGN,
            Properties.UNDERLINE,
            Properties.STRIKETHROUGH,
        )

        private fun isSupported(attribute: TextAttribute): Boolean =
            attribute.property in SUPPORTED

        @JvmStatic fun plain(text: String) = AttributedString(text, emptyList(), emptyList())

        @JvmStatic
        fun of(text: String, runs: List<StyleRun>, links: List<LinkRun>) =
            AttributedString(text, runs, links)

        /** Start a range-tracking builder (inputs are Kotlin character indices). */
        @JvmStatic fun builder(): Builder = Builder()

        /** Decode a native markdown payload (UTF-8 byte offsets already). */
        @JvmStatic
        fun decode(payload: ByteArray): AttributedString {
            var p = 0
            fun need(n: Int) {
                require(p + n <= payload.size) { "attributed text payload truncated" }
            }
            fun i32(): Int {
                need(4)
                val value = (payload[p].toInt() and 0xFF) or
                    ((payload[p + 1].toInt() and 0xFF) shl 8) or
                    ((payload[p + 2].toInt() and 0xFF) shl 16) or
                    ((payload[p + 3].toInt() and 0xFF) shl 24)
                p += 4
                return value
            }
            fun i16(): Int {
                need(2)
                val value = (payload[p].toInt() and 0xFF) or
                    ((payload[p + 1].toInt() and 0xFF) shl 8)
                p += 2
                return value
            }
            fun bytes(n: Int): ByteArray {
                need(n)
                val value = payload.copyOfRange(p, p + n)
                p += n
                return value
            }

            val textLen = i32()
            val text = String(bytes(textLen), StandardCharsets.UTF_8)
            val runCount = i32()
            val runs = ArrayList<StyleRun>(runCount)
            repeat(runCount) {
                val start = i32()
                val end = i32()
                val attrCount = i32()
                val attributes = ArrayList<TextAttribute>(attrCount)
                repeat(attrCount) {
                    val property = i16()
                    val valueType = payload[p++].toInt() and 0xFF
                    p++ // reserved
                    val value = i32()
                    val attribute = TextAttribute(property, valueType, value)
                    if (isSupported(attribute)) {
                        attributes.add(attribute)
                    }
                }
                runs.add(StyleRun(start, end, attributes))
            }
            val linkCount = i32()
            val links = ArrayList<LinkRun>(linkCount)
            repeat(linkCount) {
                val start = i32()
                val end = i32()
                val urlLen = i32()
                val url = String(bytes(urlLen), StandardCharsets.UTF_8)
                links.add(LinkRun(start, end, url))
            }
            return AttributedString(text, runs, links)
        }
    }

    /** Range-tracking builder: append text, then mark character ranges. */
    class Builder internal constructor() {
        private val text = StringBuilder()
        private val runs = ArrayList<StyleRun>()
        private val links = ArrayList<LinkRun>()

        fun append(value: String): Builder {
            text.append(value)
            return this
        }

        fun bold(start: Int, end: Int): Builder = style(start, end, TextAttribute.fontWeight(700f))
        fun italic(start: Int, end: Int): Builder = style(start, end, TextAttribute.italic())
        fun monospaced(start: Int, end: Int): Builder = style(start, end, TextAttribute.fontDesign(3))
        fun underline(start: Int, end: Int): Builder = style(start, end, TextAttribute.underline())
        fun strikethrough(start: Int, end: Int): Builder =
            style(start, end, TextAttribute.strikethrough())

        fun fontSize(start: Int, end: Int, points: Float): Builder =
            style(start, end, TextAttribute.fontSize(points))

        fun color(start: Int, end: Int, argb: Int): Builder =
            style(start, end, TextAttribute.color(argb))

        fun link(start: Int, end: Int, url: String): Builder {
            links.add(LinkRun(byteOffset(start), byteOffset(end), url))
            return this
        }

        private fun style(start: Int, end: Int, attribute: TextAttribute): Builder {
            runs.add(
                StyleRun(byteOffset(start), byteOffset(end), listOf(attribute)),
            )
            return this
        }

        private fun byteOffset(charIndex: Int): Int {
            val value = text.toString()
            require(charIndex in 0..value.length) { "character index $charIndex is out of range" }
            return value.substring(0, charIndex).toByteArray(StandardCharsets.UTF_8).size
        }

        fun build(): AttributedString =
            AttributedString(text.toString(), runs.toList(), links.toList())
    }
}

private fun ByteArrayOutputStream.writeIntLE(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}

private fun ByteArrayOutputStream.writeShortLE(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}
