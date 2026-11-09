package dev.pathland.demo.music.model

/**
 * A track in the demo library (fictional albums; the cover art is a local asset
 * and the audio a bundled royalty-free clip, both served by the host under
 * `/_pathland/assets/`).
 */
data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Float,
    val cover: String,
    val thumbnail: String,
    val audio: String,
)
