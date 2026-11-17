package dev.pathland.view.dsl

import dev.pathland.view.core.ButtonStyle
import dev.pathland.view.core.Color
import dev.pathland.view.core.EmptyContent
import dev.pathland.view.core.LabelStyle
import dev.pathland.view.core.PathlandDsl
import dev.pathland.view.core.VideoStyle
import dev.pathland.view.core.View

/**
 * The default `ButtonStyle`: renders the label content unchanged.
 *
 * The renderer supplies the native button element and its platform chrome, so
 * this style intentionally adds no visual decoration.
 */
object PlainButtonStyle : ButtonStyle {
    override fun makeBody(config: ButtonStyle.Configuration): View? = config.label()
}

/**
 * A `ButtonStyle` that paints a rounded, bordered, tinted box around the label.
 *
 * Configure padding, background, border color, and radius through
 * [Config]/`borderedButtonStyle`. Label text is forced to white; the renderer
 * still owns focus/pressed states.
 */
class BorderedButtonStyle private constructor(private val config: Config) : ButtonStyle {

    /** Bordered button appearance: padding, background, border color, and radius. */
    @PathlandDsl
    class Config {
        /** Inner padding in logical points (default 10). */
        var padding: Float = 10f
        /** Box background (default blue). */
        var background: Color = Color.rgb(33, 150, 243)
        /** Border color (default darker blue). */
        var borderColor: Color = Color.rgb(25, 118, 210)
        /** Corner radius in logical points (default 6). */
        var radius: Float = 6f

        fun padding(value: Float): Config {
            padding = value
            return this
        }

        fun background(value: Color): Config {
            background = value
            return this
        }

        fun borderColor(value: Color): Config {
            borderColor = value
            return this
        }

        fun radius(value: Float): Config {
            radius = value
            return this
        }
    }

    override fun makeBody(config: ButtonStyle.Configuration): View? {
        val label = config.label() ?: return null
        return label
            .foregroundStyle(Color.WHITE)
            .padding(this.config.padding)
            .background(this.config.background)
            .border(this.config.borderColor, 1f, this.config.radius)
    }

    companion object {
        fun make(configure: Config.() -> Unit): BorderedButtonStyle {
            val config = Config()
            config.configure()
            return BorderedButtonStyle(config)
        }
    }
}

/** The default bordered style value. */
fun borderedButtonStyle(configure: BorderedButtonStyle.Config.() -> Unit = {}): BorderedButtonStyle =
    BorderedButtonStyle.make(configure)

/** The default button style value. */
fun plainButtonStyle(): ButtonStyle = PlainButtonStyle

/**
 * The default `LabelStyle`: icon then title in a small horizontal row.
 *
 * Missing parts are omitted; the result is a grouped subtree so the label keeps
 * a stable component type even when one part is absent.
 */
object DefaultLabelStyle : LabelStyle {
    override fun makeBody(config: LabelStyle.Configuration): View {
        val parts = buildList {
            config.icon()?.let { add(it) }
            config.title()?.let { add(it) }
        }
        return groupView(parts)
    }
}

/** A `LabelStyle` that renders only the title, dropping the icon. */
object TitleOnlyLabelStyle : LabelStyle {
    override fun makeBody(config: LabelStyle.Configuration): View =
        config.title() ?: groupView(emptyList())
}

/** A `LabelStyle` that renders only the icon, dropping the title. */
object IconOnlyLabelStyle : LabelStyle {
    override fun makeBody(config: LabelStyle.Configuration): View =
        config.icon() ?: groupView(emptyList())
}

/**
 * A `VideoStyle` that renders no content, leaving playback controls to the
 * renderer's native chrome. It returns the [EmptyContent] sentinel rather than
 * `null` so the slot still has a stable component type.
 */
object NativeVideoStyle : VideoStyle {
    override fun makeBody(config: VideoStyle.Configuration): View = EmptyContent.INSTANCE
}
