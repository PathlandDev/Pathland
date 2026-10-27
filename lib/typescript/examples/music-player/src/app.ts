import { computed } from "@pathland/signals";
import { MediaController } from "@pathland/view";
import type { AppContext, ViewElement } from "@pathland/view";
import type { PathlandApp } from "@pathland/server";
import { State } from "@pathland/state";
import { MusicPlayerView, type PlayerBindings } from "./MusicPlayerView";
import { at, TRACKS } from "./track";

/** The music player demo app: app-owned transport + library state. */
export class MusicPlayerApp implements PathlandApp {
  trackIndex = new State<number>(0, "player.track");
  playing = new State<boolean>(false, "player.playing");
  position = new State<number>(0, "player.position");
  volume = new State<number>(0.7, "player.volume");
  seekRequest = new State<number>(0, "player.seekRequest");
  volumeRequest = new State<number>(0.7, "player.volumeRequest");

  readonly player = new MediaController();

  newRoot(context: AppContext): ViewElement {
    context.state.connect(this);
    this.player
      .source(computed(() => at(this.trackIndex.signal()).audio))
      .usePlaying(this.playing.signal)
      .usePosition(this.seekRequest.signal)
      .useVolume(this.volumeRequest.signal)
      .useTime(this.position.signal)
      .useVolumeReport(this.volume.signal)
      .onEnded(() => this.next());

    const bindings: PlayerBindings = {
      trackIndex: this.trackIndex.signal,
      playing: this.playing.signal,
      position: this.position.signal,
      seekRequest: this.seekRequest.signal,
      volume: this.volume.signal,
      volumeRequest: this.volumeRequest.signal,
      player: this.player,
      select: (index) => this.select(index),
      next: () => this.next(),
      prev: () => this.prev(),
    };
    return MusicPlayerView(bindings);
  }

  select(index: number): void {
    this.trackIndex.set(index);
    this.position.set(0);
    this.seekRequest.set(0);
    this.playing.set(true);
  }

  next(): void {
    this.select((this.trackIndex.get() + 1) % TRACKS.length);
  }

  prev(): void {
    this.select((this.trackIndex.get() - 1 + TRACKS.length) % TRACKS.length);
  }
}
