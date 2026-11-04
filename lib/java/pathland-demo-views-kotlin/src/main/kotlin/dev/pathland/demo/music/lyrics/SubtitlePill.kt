package dev.pathland.demo.music.lyrics

import dev.pathland.demo.music.MusicPlayerView
import dev.pathland.demo.music.model.Lyrics
import dev.pathland.demo.music.service.mediaPlayerService
import dev.pathland.view.core.Color
import dev.pathland.view.core.FontStyle
import dev.pathland.view.core.HorizontalAlignment
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.allowsHitTesting
import dev.pathland.view.dsl.background
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.cornerRadius
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontStyle
import dev.pathland.view.dsl.foregroundStyle
import dev.pathland.view.dsl.offset
import dev.pathland.view.dsl.padding

/**
 * The karaoke lyric pill just above the floating player bar: two italic lines on
 * a translucent black pill; invisible when there is no lyric block.
 */
internal class SubtitlePill : Component() {

    override fun body(): View {
        val service = mediaPlayerService()
        val block = computed {
            if (service.playing.get()) {
                Lyrics.blockFor(service.currentTrack.get().title, service.position.get())
            } else {
                emptyList()
            }
        }
        val current = computed {
            if (service.playing.get()) {
                Lyrics.currentIndex(service.currentTrack.get().title, service.position.get())
            } else {
                -1
            }
        }
        val previous = computed {
            if (service.playing.get()) {
                Lyrics.previousLine(service.currentTrack.get().title, service.position.get())
            } else {
                ""
            }
        }

        return VStack(alignment = HorizontalAlignment.CENTER, spacing = 2f) {
            Text(computed {
                val p = previous.get()
                if (p.isEmpty()) lineAt(block.get(), current.get()) else p
            }).fontSize(13f)
                .fontStyle(FontStyle.ITALIC)
                .foregroundStyle(Color.WHITE)

            Text(computed {
                val p = previous.get()
                if (p.isEmpty()) "" else lineAt(block.get(), current.get())
            }).fontSize(13f)
                .fontStyle(FontStyle.ITALIC)
                .foregroundStyle(Color.argb(170, 255, 255, 255))
        }.padding(top = 6f, right = 12f, bottom = 6f, left = 12f)
            .cornerRadius(8f)
            .offset(x = 0f, y = -(MusicPlayerView.BAR_HEIGHT + 24f))
            .allowsHitTesting(false)
            .background(computed {
                if (block.get().isEmpty() || current.get() < 0) Color.CLEAR else Color.argb(128, 0, 0, 0)
            })
    }

    /** The row text for a block index (empty beyond the block). */
    private fun lineAt(block: List<String>, index: Int): String =
        if (index in block.indices) block[index] else ""
}
