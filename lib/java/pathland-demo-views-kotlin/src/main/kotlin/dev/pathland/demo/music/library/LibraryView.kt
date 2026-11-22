package dev.pathland.demo.music.library

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.Color
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.Keys
import dev.pathland.view.core.Roles
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.core.subscription.SessionContext
import dev.pathland.core.protocol.Commands
import dev.pathland.view.dsl.Divider
import dev.pathland.view.dsl.ForEach
import dev.pathland.view.dsl.Font
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.LazySelection
import dev.pathland.view.dsl.LazyVStack
import dev.pathland.view.dsl.ScrollView
import dev.pathland.view.dsl.Spacer
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.accessibilityRole
import dev.pathland.view.dsl.font
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.padding

/** The fixed main-axis height of a library row (the lazy window's extent). */
internal const val LIBRARY_ROW_HEIGHT = 64f

/** A subtle placeholder tone for a not-yet-loaded row. */
private val SKELETON = Color.argb(0x26, 0x4B, 0x55, 0x63)

/** A placeholder row shown while a page is loading. */
private fun TrackRowSkeleton(): View = HStack(
    alignment = VerticalAlignment.CENTER,
    spacing = 12f,
) {
    SKELETON.frame(width = 44f, height = 44f)
    VStack(alignment = HorizontalAlignment.LEADING, spacing = 6f) {
        SKELETON.frame(width = 160f, height = 10f)
        SKELETON.frame(width = 110f, height = 8f)
    }
    Spacer()
    SKELETON.frame(width = 34f, height = 8f)
}.padding(6f).frame(height = LIBRARY_ROW_HEIGHT)

/**
 * The library pane: the "Library" header and the full track list. Each row is a
 * [TrackRowView]; selecting one starts that track through the shared player
 * service. The list is a windowed `LazyVStack` over the repeated library.
 */
internal class LibraryView : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        // A live/SSR session uses the paged source; an emitter-only unit-test
        // mount (no session context) keeps the in-memory list.
        val source = SessionContext.current()?.let { service.librarySource }

        return VStack {
            Text("Library")
                .font(Font.largeTitle())
                .fontSize(26f)
                .fontWeight(FontWeight.BOLD)
                .accessibilityRole(Roles.HEADER)

            Text("${service.tracks.size} songs · ${service.tracks.size} albums")
                .foregroundStyle(MusicPlayerView.SECONDARY_FG)

            Divider().padding(top = 0f, right = 0f, bottom = 10f, left = 0f)

            ScrollView {
                if (source != null && source.loading.get() && source.total.get() == 0) {
                    Text("Loading library…")
                        .foregroundStyle(MusicPlayerView.SECONDARY_FG)
                } else {
                    LazyVStack(
                        spacing = 0f,
                        selection = LazySelection(
                            selectedIndex = service.focusedIndex,
                            focusState = service.listFocused,
                            onActivate = service::selectTrack,
                            onTap = service::selectTrack,
                            onKeyDown = { _, key ->
                                if (key.keyCode == Keys.SPACE) {
                                    service.togglePlay()
                                    true
                                } else {
                                    false
                                }
                            },
                        ),
                    ) {
                        if (source != null) {
                            ForEach(
                                source,
                                itemExtent = LIBRARY_ROW_HEIGHT,
                                placeholder = {
                                    TrackRowSkeleton().frame(height = LIBRARY_ROW_HEIGHT)
                                },
                            ) { track, index ->
                                TrackRowView(track, index).frame(height = LIBRARY_ROW_HEIGHT)
                            }
                        } else {
                            ForEach(service.tracks) { track, index ->
                                TrackRowView(track, index).frame(height = LIBRARY_ROW_HEIGHT)
                            }
                        }
                    }
                }
            }
        }.padding(24f)
            .frame(width = Commands.Size.FILL, height = Commands.Size.FILL)
    }
}
