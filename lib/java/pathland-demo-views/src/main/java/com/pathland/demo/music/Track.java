package com.pathland.demo.music;

/**
 * A track in the demo library (fictional albums; the cover art is a local SVG
 * asset and the audio a bundled royalty-free clip, both served by the host under
 * {@code /_pathland/assets/}).
 *
 * @param title    the track title
 * @param artist   the artist name
 * @param album    the album name
 * @param duration the length in seconds
 * @param cover    the album-art asset path
 * @param audio    the audio asset path (the source the player plays)
 */
public record Track(String title, String artist, String album, float duration, String cover, String audio) {}