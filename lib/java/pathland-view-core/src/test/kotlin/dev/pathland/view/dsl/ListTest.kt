package dev.pathland.view.dsl

import dev.pathland.core.protocol.Categories
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Components
import dev.pathland.core.protocol.Properties
import dev.pathland.core.protocol.ValueTypes
import dev.pathland.core.transport.FrameOpcodeSink
import dev.pathland.core.transport.ProtocolFrame
import dev.pathland.view.core.Environment
import dev.pathland.view.core.IconName
import dev.pathland.view.core.ListStyle
import dev.pathland.view.core.signal.Signals
import dev.pathland.view.engine.Emitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListTest {

    private fun mount(root: dev.pathland.view.core.View): ProtocolFrame {
        val sink = FrameOpcodeSink()
        Emitter(sink).mount(root, Environment.DEFAULT)
        return sink.frame()
    }

    private fun created(frame: ProtocolFrame): List<Int> =
        frame.opcodes()
            .filter { it.category() == Categories.TREE && it.command() == Commands.Tree.CREATE_NODE }
            .map { it.b() and 0xffff }

    private fun setProperties(frame: ProtocolFrame) =
        frame.opcodes().filter {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY
        }

    private fun tokenPaths(frame: ProtocolFrame): List<String> =
        setProperties(frame)
            .filter { (it.b() ushr 16) == ValueTypes.DESIGN_TOKEN }
            .map { frame.stringAt(it.c()) }

    private fun propertyIds(frame: ProtocolFrame): List<Int> =
        setProperties(frame).map { it.b() and 0xffff }

    @Test
    fun staticListIsAScrollViewOverRowsWithSemanticRoles() {
        val frame = mount(
            List {
                Text("One")
                Text("Two")
            },
        )

        val components = created(frame)
        assertTrue(components.contains(Components.SCROLLVIEW))
        assertTrue(components.contains(Components.VSTACK))
        assertTrue(components.contains(Components.DIVIDER))
        assertEquals(3, propertyIds(frame).count { it == Properties.ROLE })
        assertTrue(propertyIds(frame).contains(Properties.PADDING_TOP))
    }

    @Test
    fun defaultListUsesTheListStyleColorTokens() {
        val frame = mount(
            List {
                Text("One")
                Text("Two")
            },
        )
        val tokens = tokenPaths(frame)
        assertTrue(tokens.contains("list.background"), "viewport background: $tokens")
        assertTrue(tokens.contains("list.separator"), "separator: $tokens")
    }

    @Test
    fun insetGroupedListUsesGroupAndSelectionTokens() {
        val selection = LazySelection(
            dev.pathland.view.core.signal.Signals.signal(0),
            dev.pathland.view.core.signal.Signals.signal(false),
        )
        val frame = mount(
            List(selection = selection) {
                Text("One")
                Text("Two")
            }.listStyle(InsetGroupedListStyle),
        )
        val tokens = tokenPaths(frame)
        assertTrue(tokens.contains("list.group.background"), "group background: $tokens")
        assertTrue(tokens.contains("list.group.inset"), "group inset: $tokens")
        assertTrue(tokens.contains("list.group.radius"), "group radius: $tokens")
        assertTrue(tokens.contains("list.row.selected.background"), "selection: $tokens")
    }

    @Test
    fun sidebarListStylesItsLabelsWithTheSidebarTokens() {
        val selection = LazySelection(Signals.signal(0), Signals.signal(false))
        val frame = mount(
            List(selection = selection) {
                Label("One") { Icon(IconName.FOLDER) }
                Label("Two") { Icon(IconName.MUSIC) }
            }.listStyle(SidebarListStyle),
        )
        val tokens = tokenPaths(frame)
        assertTrue(tokens.contains("list.sidebar.background"), "sidebar background: $tokens")
        assertTrue(tokens.contains("list.sidebar.foreground"), "sidebar foreground: $tokens")
        assertTrue(tokens.contains("list.sidebar.selected.foreground"), "selected text: $tokens")
        assertTrue(tokens.contains("list.sidebar.icon.foreground"), "icon: $tokens")
        assertTrue(tokens.contains("list.sidebar.selected.icon.foreground"), "selected icon: $tokens")
        assertTrue(tokens.contains("space.3"), "label gap: $tokens")
        assertTrue(tokens.contains("space.4"), "icon size: $tokens")
        assertTrue(tokens.contains("control.font.size"), "label font size: $tokens")
    }

    @Test
    fun lazyListAddsStyleChromeToTheDeclaredItemExtent() {
        val frame = mount(
            List((0 until 4).toList(), itemExtent = 44f) { _, index ->
                Text("Row $index")
            },
        )

        val components = created(frame)
        assertTrue(components.contains(Components.LAZY_VSTACK))
        assertEquals(4, components.count { it == Components.TEXT }, "one text per row")
        assertEquals(3, components.count { it == Components.DIVIDER }, "one separator between rows")
        val itemExtent = setProperties(frame).single {
            (it.b() and 0xffff) == Properties.ITEM_EXTENT
        }
        assertEquals(44f + PlainListStyle.rowExtentOverhead, Float.fromBits(itemExtent.c()))
    }

    @Test
    fun listStyleIsEnvironmentScopedAndNearestWins() {
        val frame = mount(
            VStack {
                List {
                    Text("Inner")
                }.listStyle(SidebarListStyle)
            }.listStyle(InsetGroupedListStyle),
        )
        val tokens = tokenPaths(frame)
        assertTrue(tokens.contains("list.sidebar.background"), "inner style: $tokens")
        assertTrue(tokens.contains("list.sidebar.foreground"), "inner style: $tokens")
    }

    @Test
    fun customListStyleCanDecorateRows() {
        val style = object : ListStyle {
            override val rowExtentOverhead: Float = 2f

            override fun makeRow(content: dev.pathland.view.core.View, config: ListStyle.RowConfiguration): dev.pathland.view.core.View =
                content.padding(2f).background(Color.token("list.row.background"))
        }

        val frame = mount(
            List {
                Text("One")
            }.listStyle(style),
        )
        assertTrue(tokenPaths(frame).contains("list.row.background"))
    }
}
