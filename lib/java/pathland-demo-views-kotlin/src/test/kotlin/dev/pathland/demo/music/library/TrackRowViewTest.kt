package dev.pathland.demo.music.library

import dev.pathland.core.protocol.Categories
import dev.pathland.core.protocol.Commands
import dev.pathland.core.protocol.Components
import dev.pathland.core.protocol.Properties
import dev.pathland.core.transport.FrameOpcodeSink
import dev.pathland.core.transport.ProtocolFrame
import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.service.MediaPlayerService
import dev.pathland.view.core.Color
import dev.pathland.view.core.Environment
import dev.pathland.view.core.Roles
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.environment
import dev.pathland.view.engine.Emitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackRowViewTest {

    private class RecordingSink : FrameOpcodeSink() {
        val frames = mutableListOf<ProtocolFrame>()

        override fun endFrame() {
            super.endFrame()
            frames.add(frame())
        }
    }

    @Test
    fun selectedBackgroundTracksFocusAndThePlayingIconUsesOpacity() {
        val service = MediaPlayerService()
        service.trackIndex.set(0)
        service.focusedIndex.set(1)
        service.listFocused.set(false)

        val sink = RecordingSink()
        Emitter(sink).mount(
            VStack {
                TrackRowView(service.tracks[0], 0)
                TrackRowView(service.tracks[1], 1)
            }.environment(MediaPlayerService.KEY, service),
            Environment.DEFAULT,
        )

        val initial = sink.frames.last()
        val rows = rowIds(initial)
        assertEquals(2, rows.size)
        assertEquals(0, backgroundColor(listOf(initial), rows[0]), "playing row is not selected")
        assertEquals(
            MusicPlayerView.INACTIVE_ROW_BG.argb,
            backgroundColor(listOf(initial), rows[1]),
            "unfocused selection is gray",
        )
        assertEquals(Color.BLACK.argb, foregroundColor(listOf(initial), rows[1]))

        val icon0 = iconIdForRow(initial, rows[0])!!
        val icon1 = iconIdForRow(initial, rows[1])!!
        assertEquals(2, iconIds(initial).size, "every row has a stable icon child")
        assertEquals(1f, opacity(listOf(initial), icon0))
        assertEquals(0f, opacity(listOf(initial), icon1))
        assertEquals(2, countIconNames(initial, "volume"))

        val beforeFocus = sink.frames.size
        service.listFocused.set(true)
        val focusDelta = sink.frames.drop(beforeFocus)
        assertEquals(MusicPlayerView.ACTIVE_ROW_BG.argb, backgroundColor(focusDelta, rows[1]))
        assertEquals(MusicPlayerView.ACTIVE_ROW_FG.argb, foregroundColor(focusDelta, rows[1]))

        val beforeSelection = sink.frames.size
        service.focusedIndex.set(0)
        val selectionDelta = sink.frames.drop(beforeSelection)
        assertEquals(MusicPlayerView.ACTIVE_ROW_BG.argb, backgroundColor(selectionDelta, rows[0]))
        assertEquals(0, backgroundColor(selectionDelta, rows[1]), "selection moves independently of playback")

        val beforeBlur = sink.frames.size
        service.listFocused.set(false)
        val blurDelta = sink.frames.drop(beforeBlur)
        assertEquals(MusicPlayerView.INACTIVE_ROW_BG.argb, backgroundColor(blurDelta, rows[0]))
        assertEquals(Color.BLACK.argb, foregroundColor(blurDelta, rows[0]))

        val beforeTrack = sink.frames.size
        service.trackIndex.set(1)
        val trackDelta = sink.frames.drop(beforeTrack)
        assertEquals(0, countIcons(trackDelta), "changing tracks does not create another icon")
        assertEquals(0f, opacity(trackDelta, icon0))
        assertEquals(1f, opacity(trackDelta, icon1))
    }

    private fun rowIds(frame: ProtocolFrame): List<Int> =
        frame.opcodes().filter {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xffff) == Properties.ROLE &&
                Float.fromBits(it.c()).toInt() == Roles.LIST_ITEM
        }.map { it.a() }.distinct()

    private fun backgroundColor(frames: List<ProtocolFrame>, rowId: Int): Int? =
        property(frames, rowId, Properties.BACKGROUND_COLOR)?.c()

    private fun foregroundColor(frames: List<ProtocolFrame>, rowId: Int): Int? =
        property(frames, rowId, Properties.COLOR)?.c()

    private fun property(frames: List<ProtocolFrame>, nodeId: Int, property: Int) =
        frames.asSequence()
            .flatMap { it.opcodes().asSequence() }
            .firstOrNull {
                it.category() == Categories.PARAMETER &&
                    it.command() == Commands.Parameter.SET_PROPERTY &&
                    it.a() == nodeId &&
                    (it.b() and 0xffff) == property
            }

    private fun iconIds(frame: ProtocolFrame): List<Int> =
        frame.opcodes().filter {
            it.category() == Categories.TREE &&
                it.command() == Commands.Tree.CREATE_NODE &&
                it.b() == Components.ICON
        }.map { it.a() }

    private fun iconIdForRow(frame: ProtocolFrame, rowId: Int): Int? =
        iconIds(frame).firstOrNull { iconId ->
            frame.opcodes().any {
                it.category() == Categories.TREE &&
                    it.command() == Commands.Tree.INSERT_CHILD &&
                    it.a() == rowId &&
                    it.b() == iconId
            }
        }

    private fun opacity(frames: List<ProtocolFrame>, iconId: Int): Float? =
        property(frames, iconId, Properties.OPACITY)?.let { Float.fromBits(it.c()) }

    private fun countIcons(frames: List<ProtocolFrame>): Int =
        frames.sumOf { iconIds(it).size }

    private fun countIconNames(frame: ProtocolFrame, name: String): Int =
        frame.opcodes().count {
            it.category() == Categories.PARAMETER &&
                it.command() == Commands.Parameter.SET_PROPERTY &&
                (it.b() and 0xffff) == Properties.ICON_NAME &&
                frame.stringAt(it.c()) == name
        }
}
