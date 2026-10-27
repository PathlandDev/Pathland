export interface Track {
  title: string;
  artist: string;
  album: string;
  duration: number;
  cover: string;
  audio: string;
}

/** The shared Pathland demo library (same tracks/assets as the Java GTK demo). */
export const TRACKS: readonly Track[] = [
  {
    title: "Building on Solid Ground",
    artist: "The Foundation",
    album: "The Open Horizon",
    duration: 178,
    cover: "/_pathland/assets/albumart/cover1.jpg",
    audio: "/_pathland/assets/audio/track1.mp3",
  },
  {
    title: "Pathland Crossing",
    artist: "The Crossings",
    album: "Native Operations",
    duration: 166,
    cover: "/_pathland/assets/albumart/cover2.jpg",
    audio: "/_pathland/assets/audio/track2.mp3",
  },
  {
    title: "Rendered Free",
    artist: "Rena Render",
    album: "Grace From The Source",
    duration: 182,
    cover: "/_pathland/assets/albumart/cover3.jpg",
    audio: "/_pathland/assets/audio/track3.mp3",
  },
  {
    title: "Rendered In Your Arms",
    artist: "Ember Frame",
    album: "Architectures of Grace",
    duration: 160,
    cover: "/_pathland/assets/albumart/cover4.jpg",
    audio: "/_pathland/assets/audio/track4.mp3",
  },
  {
    title: "Sixty Frames Per Second",
    artist: "Rowan Frame",
    album: "The Protocol Sessions",
    duration: 164,
    cover: "/_pathland/assets/albumart/cover5.jpg",
    audio: "/_pathland/assets/audio/track5.mp3",
  },
  {
    title: "The Pathland Dream",
    artist: "Neon Protocol",
    album: "Velvet Horizons",
    duration: 170,
    cover: "/_pathland/assets/albumart/cover6.jpg",
    audio: "/_pathland/assets/audio/track6.mp3",
  },
  {
    title: "Across the Open Land",
    artist: "Open Land",
    album: "The Weight of Open Air",
    duration: 156,
    cover: "/_pathland/assets/albumart/cover7.jpg",
    audio: "/_pathland/assets/audio/track7.mp3",
  },
  {
    title: "Sixteen Bytes",
    artist: "The Byte Ensemble",
    album: "The Ring Buffer Sessions",
    duration: 182,
    cover: "/_pathland/assets/albumart/cover8.jpg",
    audio: "/_pathland/assets/audio/track8.mp3",
  },
];

/** The track at an index (wraps around, safe for negative indexes). */
export function at(index: number): Track {
  const length = TRACKS.length;
  return TRACKS[((Math.trunc(index) % length) + length) % length]!;
}

/** Format seconds as `m:ss`. */
export function fmt(seconds: number): string {
  const total = Math.max(0, Math.round(seconds));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}
