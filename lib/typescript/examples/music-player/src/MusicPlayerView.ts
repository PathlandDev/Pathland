import { computed, type ReadonlySignal, type WritableSignal } from "@pathland/signals";
import {
  Button,
  Divider,
  HStack,
  Icon,
  Image,
  ScrollView,
  Slider,
  Spacer,
  Text,
  VStack,
  type ViewElement,
} from "@pathland/view";
import {
  background,
  cornerRadius,
  fillHeight,
  fillWidth,
  fontSize,
  fontWeight,
  foregroundStyle,
  lineLimit,
  padding,
  scaledToFill,
  scaledToFit,
  width,
  height,
} from "@pathland/view/modifiers";
import { at, fmt, TRACKS, type Track } from "./track";
import type { MediaController } from "@pathland/view";

const SECONDARY_FG = "#6B7280";
const ACTIVE_ROW_BG = 0xffe8efff;
const ACTIVE_ROW_FG = 0xff1a3a8c;
const NORMAL_FG = 0xff000000;
const CLEAR = 0x00000000;
const SIDEBAR_BG = "#F6F7FA";
const BAR_BG = 0xbfebebed;

export interface PlayerBindings {
  trackIndex: WritableSignal<number>;
  playing: WritableSignal<boolean>;
  position: WritableSignal<number>;
  seekRequest: WritableSignal<number>;
  volume: WritableSignal<number>;
  volumeRequest: WritableSignal<number>;
  player: MediaController;
  select(index: number): void;
  next(): void;
  prev(): void;
}

export function MusicPlayerView(state: PlayerBindings): ViewElement {
  const current = computed(() => at(state.trackIndex()));
  return VStack({ spacing: 0 }, () => [
    state.player.attach(),
    HStack({ spacing: 0 }, () => [
      LibraryView(state, current),
      NowPlayingSidebar(state, current),
    ]).modifiers(fillWidth(), fillHeight()),
    PlayerBar(state, current),
  ]).modifiers(fillWidth(), fillHeight());
}

function LibraryView(
  state: PlayerBindings,
  current: ReadonlySignal<Track>,
): ViewElement {
  const rows = TRACKS.map((track, index) => TrackRow(state, track, index, current));
  rows.push(Spacer());
  return VStack({ spacing: 8 }, () => [
    Text({ text: "Library" }).modifiers(fontSize(26), fontWeight(700)),
    Text({ text: `${TRACKS.length} songs · ${TRACKS.length} albums` }).modifiers(
      foregroundStyle(SECONDARY_FG),
    ),
    Divider().modifiers(padding(6)),
    ScrollView({}, () => [VStack({ spacing: 4 }, () => rows)]).modifiers(fillHeight()),
  ]).modifiers(padding(24), fillWidth(), fillHeight());
}

function TrackRow(
  state: PlayerBindings,
  track: Track,
  index: number,
  current: ReadonlySignal<Track>,
): ViewElement {
  const isCurrent = computed(() => current() === track);
  return Button({
    action: () => state.select(index),
    children: () => [
      HStack({ spacing: 12 }, () => [
        Image({ source: track.cover }).modifiers(width(44), height(44), scaledToFit()),
        VStack({ spacing: 2, alignment: "leading" }, () => [
          Text({ text: track.title }).modifiers(fontWeight(600)),
          Text({ text: `${track.artist} · ${track.album}` }).modifiers(
            fontSize(13),
            foregroundStyle(SECONDARY_FG),
          ),
        ]),
        Spacer(),
        Text({ text: fmt(track.duration) }).modifiers(
          fontSize(13),
          foregroundStyle(SECONDARY_FG),
        ),
      ]).modifiers(padding(6), fillWidth()),
    ],
  }).modifiers(
    background(computed(() => (isCurrent() ? ACTIVE_ROW_BG : CLEAR))),
    foregroundStyle(computed(() => (isCurrent() ? ACTIVE_ROW_FG : NORMAL_FG))),
    cornerRadius(6),
    fillWidth(),
  );
}

function NowPlayingSidebar(
  state: PlayerBindings,
  current: ReadonlySignal<Track>,
): ViewElement {
  return VStack({ spacing: 14, alignment: "leading" }, () => [
    Text({ text: "Now Playing" }).modifiers(fontSize(18), fontWeight(700)),
    Image({ source: computed(() => current().cover) }).modifiers(
      width(220),
      height(220),
      scaledToFill(),
      cornerRadius(8),
    ),
    Text({ text: computed(() => current().title) }).modifiers(fontSize(20), fontWeight(700)),
    Text({ text: computed(() => `${current().artist} · ${current().album}`) }).modifiers(
      foregroundStyle(SECONDARY_FG),
    ),
    Divider().modifiers(padding(6)),
    Text({ text: "Up Next" }).modifiers(fontWeight(600)),
    UpNextRow(state, 1),
    UpNextRow(state, 2),
    Spacer(),
    Text({ text: "TypeScript view DSL · wasm SSR · live deltas" }).modifiers(
      fontSize(11),
      foregroundStyle(SECONDARY_FG),
    ),
  ]).modifiers(
    padding(24),
    width(280),
    fillHeight(),
    background(SIDEBAR_BG),
  );
}

function UpNextRow(state: PlayerBindings, offset: number): ViewElement {
  return Text({
    text: computed(() => {
      const track = at(state.trackIndex() + offset);
      return `${offset === 1 ? "Up next" : "Then"} · ${track.title} — ${track.artist}`;
    }),
  }).modifiers(fontSize(13), foregroundStyle(SECONDARY_FG), lineLimit(1));
}

function PlayerBar(
  state: PlayerBindings,
  current: ReadonlySignal<Track>,
): ViewElement {
  const seek = seekValue(state.player.time, state.player.position);
  const volume = volumeValue(state.player.volumeReport, state.player.volume);
  const transportIcon = computed(() => (state.playing() ? "pause" : "play"));
  const progress = computed(
    () =>
      `${fmt(state.player.time())} / ${fmt(
        state.player.duration() > 0 ? state.player.duration() : current().duration,
      )}`,
  );

  const transport = HStack({ spacing: 14, alignment: "center" }, () => [
    iconButton("skip-back", state.prev),
    iconButton(transportIcon, state.player.toggle.bind(state.player)),
    iconButton("skip-forward", state.next),
  ]);

  const trackInfo = HStack({ spacing: 12, alignment: "center" }, () => [
    Image({ source: computed(() => current().cover) }).modifiers(
      width(36),
      height(36),
      scaledToFit(),
      cornerRadius(4),
    ),
    VStack({ spacing: 2, alignment: "leading" }, () => [
      Text({ text: computed(() => current().title) }).modifiers(
        fontWeight(600),
        lineLimit(1),
        width(160),
      ),
      Text({ text: computed(() => current().artist) }).modifiers(
        fontSize(13),
        foregroundStyle(SECONDARY_FG),
        lineLimit(1),
        width(160),
      ),
    ]),
  ]);

  const volumeControl = HStack({ spacing: 10, alignment: "center" }, () => [
    Icon({ name: "volume" }).modifiers(fontSize(16)),
    Slider({ value: volume, min: 0, max: 1 }).modifiers(width(110)),
  ]);

  return VStack({ spacing: 2 }, () => [
    HStack({ spacing: 18, alignment: "center" }, () => [
      transport,
      Text({ text: "|" }).modifiers(fontSize(18), foregroundStyle(SECONDARY_FG)),
      trackInfo,
      Spacer(),
      Text({ text: progress }).modifiers(fontSize(12), foregroundStyle(SECONDARY_FG)),
      volumeControl,
    ]).modifiers(fillWidth()),
    Slider({ value: seek, min: 0, max: 100 }).modifiers(fillWidth()),
  ]).modifiers(
    padding(12),
    fillWidth(),
    height(76),
    background(BAR_BG),
  );
}

function iconButton(icon: ReadonlySignal<string> | string, action: () => void): ViewElement {
  return Button({
    action,
    children: () => [Icon({ name: icon }).modifiers(fontSize(18))],
  }).modifiers(padding(6));
}

/** The seek slider: displays `time`, writes both `time` (feedback) and `position` (command). */
function seekValue(
  time: WritableSignal<number>,
  position: WritableSignal<number>,
): WritableSignal<number> {
  return writableAdapter(
    () => time(),
    (value) => {
      time.set(value);
      position.set(value);
    },
  );
}

/** The volume slider: displays `volumeReport`, writes both report and command. */
function volumeValue(
  report: WritableSignal<number>,
  volume: WritableSignal<number>,
): WritableSignal<number> {
  return writableAdapter(
    () => report(),
    (value) => {
      report.set(value);
      volume.set(value);
    },
  );
}

function writableAdapter(read: () => number, write: (value: number) => void): WritableSignal<number> {
  const callable = (() => read()) as WritableSignal<number>;
  return Object.assign(callable, {
    get: read,
    set: write,
    update: (fn: (current: number) => number) => write(fn(read())),
    __pathlandSignal: true as const,
    __pathlandWritable: true as const,
  });
}

export { SECONDARY_FG };
