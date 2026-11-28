package dev.pathland.demo.music.service

import dev.pathland.view.core.ThemeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaPlayerServiceTest {

    @Test
    fun selectTrackStartsFromTheTop() {
        val service = MediaPlayerService()
        service.position.set(30f)
        service.seekRequest.set(30f)
        service.playing.set(false)

        service.selectTrack(2)

        assertEquals(2, service.trackIndex.get())
        assertEquals(MediaPlayerService.TRACKS[2], service.currentTrack.get())
        assertEquals(0f, service.position.get())
        assertEquals(0f, service.seekRequest.get())
        assertTrue(service.playing.get())
    }

    @Test
    fun libraryRepeatsTheBaseTracks() {
        val service = MediaPlayerService()
        assertEquals(MediaPlayerService.LIBRARY_SIZE, service.tracks.size)
        assertTrue(service.tracks[8] === MediaPlayerService.TRACKS[0])
        assertTrue(service.tracks[999] === MediaPlayerService.TRACKS[7])
    }

    @Test
    fun colorSchemeCyclesSystemLightDark() {
        val service = MediaPlayerService()
        assertEquals(ThemeMode.PLATFORM, service.colorScheme.get())

        service.cycleColorScheme()
        assertEquals(ThemeMode.LIGHT, service.colorScheme.get())

        service.cycleColorScheme()
        assertEquals(ThemeMode.DARK, service.colorScheme.get())

        service.cycleColorScheme()
        assertEquals(ThemeMode.PLATFORM, service.colorScheme.get())
    }

    @Test
    fun previousAndNextWrapAround() {
        val service = MediaPlayerService()

        service.trackIndex.set(0)
        service.previous()
        assertEquals(service.tracks.lastIndex, service.trackIndex.get())

        service.next()
        assertEquals(0, service.trackIndex.get())
    }
}
