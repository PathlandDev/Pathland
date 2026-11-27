package dev.pathland.demo.music.sidebar

import dev.pathland.core.protocol.Commands
import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.IconName
import dev.pathland.view.core.Roles
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Font
import dev.pathland.view.dsl.Icon
import dev.pathland.view.dsl.Label
import dev.pathland.view.dsl.LazySelection
import dev.pathland.view.dsl.SidebarListStyle
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.accessibilityRole
import dev.pathland.view.dsl.background
import dev.pathland.view.dsl.font
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.listStyle
import dev.pathland.view.dsl.padding
import dev.pathland.view.core.Color as CoreColor
import dev.pathland.view.dsl.List as PathlandList

/** One static navigation section in the left sidebar. */
private data class NavigationItem(val label: String, val icon: IconName)

/**
 * The Apple Music-style left navigation sidebar: a compact, static section list
 * (Library/Playlists/Artists/Albums/Songs) using the DSL `List` with
 * `SidebarListStyle`. Each row is a `Label`; the sidebar list style supplies its
 * icon/text layout and selected-state colors. Selection is local visual feedback
 * only; navigation and filtering are future work.
 */
internal class NavigationSidebar : Component() {

    private val items = listOf(
        NavigationItem("Library", IconName.FOLDER),
        NavigationItem("Playlists", IconName.LIST),
        NavigationItem("Artists", IconName.USERS),
        NavigationItem("Albums", IconName.GRID),
        NavigationItem("Songs", IconName.MUSIC),
    )

    override fun body(): View {
        val service = mediaPlayerService()
        val selection = LazySelection(service.navIndex, service.navFocused)

        return VStack(alignment = HorizontalAlignment.LEADING, spacing = 20f) {
            Text("Music")
                .font(Font.largeTitle())
                .fontSize(22f)
                .fontWeight(FontWeight.BOLD)

            PathlandList(selection = selection) {
                items.forEach { item -> NavigationRow(item) }
            }.listStyle(SidebarListStyle)
        }
            .padding(top = 8f, right = 24f, bottom = 8f, left = 24f)
            .background(CoreColor.token("list.sidebar.background"))
            .foregroundStyle(CoreColor.token("list.sidebar.foreground"))
            .accessibilityRole(Roles.NAVIGATION)
            .frame(
                width = MusicPlayerView.NAV_SIDEBAR_WIDTH,
                height = Commands.Size.FILL,
            )
    }

    private fun NavigationRow(item: NavigationItem): View =
        Label(item.label) { Icon(item.icon) }
}
