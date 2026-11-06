package dev.pathland.view.dsl

import dev.pathland.view.core.ButtonStyle
import dev.pathland.view.core.Color
import dev.pathland.view.core.EmptyContent
import dev.pathland.view.core.LabelStyle
import dev.pathland.view.core.VideoStyle
import dev.pathland.view.core.View

/** The default button style: the label alone. */
object PlainButtonStyle : ButtonStyle {
    override fun makeBody(config: ButtonStyle.Configuration): View? = config.label()
}

/** A bordered, tinted button style. */
class BorderedButtonStyle private constructor(private val config: Config) : ButtonStyle {

    class Config {
        var padding: Float = 10f
        var background: Color = Color.rgb(33, 150, 243)
        var borderColor: Color = Color.rgb(25, 118, 210)
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

/** The default label style: icon then title in a row. */
object DefaultLabelStyle : LabelStyle {
    override fun makeBody(config: LabelStyle.Configuration): View {
        val parts = buildList {
            config.icon()?.let { add(it) }
            config.title()?.let { add(it) }
        }
        return groupView(parts)
    }
}

/** A label style showing only the title. */
object TitleOnlyLabelStyle : LabelStyle {
    override fun makeBody(config: LabelStyle.Configuration): View =
        config.title() ?: groupView(emptyList())
}

/** A label style showing only the icon. */
object IconOnlyLabelStyle : LabelStyle {
    override fun makeBody(config: LabelStyle.Configuration): View =
        config.icon() ?: groupView(emptyList())
}

/** The default video style: no content (renderer-native controls). */
object NativeVideoStyle : VideoStyle {
    override fun makeBody(config: VideoStyle.Configuration): View = EmptyContent.INSTANCE
}
