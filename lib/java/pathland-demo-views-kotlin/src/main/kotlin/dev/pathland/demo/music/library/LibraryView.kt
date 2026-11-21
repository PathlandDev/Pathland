package dev.pathland.demo.music.library

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.service.MediaPlayerService
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.Color
import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.Keys
import dev.pathland.view.core.Roles
import dev.pathland.view.core.VerticalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.core.subscription.SessionContext
import dev.pathland.view.core.signal.Signal
import dev.pathland.core.protocol.Commands
import dev.pathland.view.dsl.Divider
import dev.pathland.view.dsl.ForEach
import dev.pathland.view.dsl.Font
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.LazyVStack
import dev.pathland.view.dsl.ScrollView
import dev.pathland.view.dsl.Spacer
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.accessibilityRole
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.focusable
import dev.pathland.view.dsl.focused
import dev.pathland.view.dsl.font
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.onFocusChanged
import dev.pathland.view.dsl.onKeyDown
import dev.pathland.view.dsl.padding
import dev.pathland.view.engine.KeyEvent

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
                    val total = source?.total?.get() ?: service.tracks.size
                    val pin: Signal<Int?> = computed { service.focusedIndex.get() }
                    LazyVStack(
                        spacing = 0f,
                        pin = pin,
                        onVisibleRange = { first, count ->
                            // While the list is not focused, keep the roving tab
                            // stop on a visible row so the pin never spans a far
                            // O(gap) window.
                            if (!service.listFocused.get() && count > 0) {
                                val current = service.focusedIndex.get()
                                if (current !in first until (first + count)) {
                                    service.focusedIndex.set(first.coerceIn(0, total - 1))
                                }
                            }
                        },
                    ) {
                        if (source != null) {
                            ForEach(
                                source,
                                itemExtent = LIBRARY_ROW_HEIGHT,
                                placeholder = { index ->
                                    focusableRow(service, index, total) {
                                        TrackRowSkeleton().frame(height = LIBRARY_ROW_HEIGHT)
                                    }
                                },
                            ) { track, index ->
                                focusableRow(service, index, total) {
                                    TrackRowView(track, index).frame(height = LIBRARY_ROW_HEIGHT)
                                }
                            }
                        } else {
                            ForEach(service.tracks) { track, index ->
                                focusableRow(service, index, total) {
                                    TrackRowView(track, index).frame(height = LIBRARY_ROW_HEIGHT)
                                }
                            }
                        }
                    }
                }
            }
        }.padding(24f)
            .frame(width = Commands.Size.FILL, height = Commands.Size.FILL)
    }

    /**
     * The row shell shared by loaded and placeholder rows: a single roving tab
     * stop at [service].focusedIndex, arrow/Enter keyboard navigation, and focus
     * observation for lazy-window pinning (#204).
     */
    // @ref spec/EVENTS.md#keyboard [implements] — roving row focus + key handling
    private fun focusableRow(
        service: MediaPlayerService,
        index: Int,
        total: Int,
        content: () -> View,
    ): View {
        val focused = computed { service.focusedIndex.get() == index }
        val isFocused = focused.get()
        var row: View = content()
            .focusable(isFocused)
            .focused(focused)
            .onKeyDown { key -> handleKey(service, index, total, key) }
        if (isFocused) {
            row = row.onFocusChanged { gained ->
                if (gained) {
                    service.listFocused.set(true)
                    service.focusedIndex.set(index)
                } else if (service.focusedIndex.get() == index) {
                    service.listFocused.set(false)
                }
            }
        }
        return row
    }

    private fun handleKey(service: MediaPlayerService, index: Int, total: Int, key: KeyEvent) {
        when (key.keyCode) {
            Keys.ARROW_UP -> service.focusedIndex.set((index - 1).coerceAtLeast(0))
            Keys.ARROW_DOWN -> service.focusedIndex.set((index + 1).coerceAtMost(total - 1))
            Keys.HOME -> service.focusedIndex.set(0)
            Keys.END -> service.focusedIndex.set((total - 1).coerceAtLeast(0))
            Keys.ENTER -> service.selectTrack(index)
            Keys.SPACE -> service.togglePlay()
        }
    }
}
