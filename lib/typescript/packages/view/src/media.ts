import { COMPONENT_MEDIA_CONTROLLER } from "@pathland/protocol";
import { constant, signal, type ReadonlySignal, type WritableSignal } from "@pathland/signals";
import { element, type ViewElement } from "./element";

/**
 * A media playback controller (the SwiftUI `AVPlayer` analogue). Audio has no
 * view: attach this controller once and drive it from ordinary views bound to
 * its transport signals.
 *
 * Commands (`playing`, `position`, `volume`, `muted`, `loop`, `rate`) are
 * app-written; reports (`playingReport`, `time`, `duration`, `volumeReport`) are
 * fed by the renderer's `MEDIA_*` events. Bind displays to reports and drags to
 * commands so a report never echoes back as a command.
 */
export class MediaController {
  private sourceValue: ReadonlySignal<string> = constant("");

  playing: WritableSignal<boolean> = signal(false);
  position: WritableSignal<number> = signal(0);
  volume: WritableSignal<number> = signal(1);
  muted: WritableSignal<boolean> = signal(false);
  loop: WritableSignal<boolean> = signal(false);
  rate: WritableSignal<number> = signal(1);

  playingReport: WritableSignal<boolean> = signal(false);
  time: WritableSignal<number> = signal(0);
  duration: WritableSignal<number> = signal(0);
  volumeReport: WritableSignal<number> = signal(1);

  private ended: () => void = () => {};

  source(source: string | ReadonlySignal<string>): this {
    this.sourceValue = typeof source === "string" ? constant(source) : source;
    return this;
  }

  get sourceSignal(): ReadonlySignal<string> {
    return this.sourceValue;
  }

  onEnded(handler: () => void): this {
    this.ended = handler;
    return this;
  }

  /** Drive the play command from an app-owned signal. */
  usePlaying(signal: WritableSignal<boolean>): this {
    this.playing = signal;
    return this;
  }

  /** Drive the seek command from an app-owned signal. */
  usePosition(signal: WritableSignal<number>): this {
    this.position = signal;
    return this;
  }

  /** Drive the volume command from an app-owned signal. */
  useVolume(signal: WritableSignal<number>): this {
    this.volume = signal;
    return this;
  }

  /** Feed playback position reports into an app-owned signal. */
  useTime(signal: WritableSignal<number>): this {
    this.time = signal;
    return this;
  }

  /** Feed duration reports into an app-owned signal. */
  useDuration(signal: WritableSignal<number>): this {
    this.duration = signal;
    return this;
  }

  /** Feed play-state reports into an app-owned signal. */
  usePlayingReport(signal: WritableSignal<boolean>): this {
    this.playingReport = signal;
    return this;
  }

  /** Feed volume reports into an app-owned signal. */
  useVolumeReport(signal: WritableSignal<number>): this {
    this.volumeReport = signal;
    return this;
  }

  play(): void {
    this.playing.set(true);
  }

  pause(): void {
    this.playing.set(false);
  }

  toggle(): void {
    this.playing.update((value) => !value);
  }

  seek(seconds: number): void {
    this.position.set(seconds);
  }

  setVolume(value: number): void {
    this.volume.set(Math.max(0, Math.min(1, value)));
  }

  setMuted(value: boolean): void {
    this.muted.set(value);
  }

  setLoop(value: boolean): void {
    this.loop.set(value);
  }

  setRate(value: number): void {
    this.rate.set(value);
  }

  handleEnded(): void {
    this.ended();
  }

  /** A non-visual marker that materializes this controller in the tree once. */
  attach(): ViewElement {
    return element({ component: COMPONENT_MEDIA_CONTROLLER, mediaController: this });
  }
}
