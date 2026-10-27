import {
  CMD_EDITING_CHANGED,
  CMD_FOCUS_CHANGED,
  CMD_MEDIA_DURATION_CHANGED,
  CMD_MEDIA_ENDED,
  CMD_MEDIA_PLAY_STATE_CHANGED,
  CMD_MEDIA_TIME_UPDATED,
  CMD_MEDIA_VOLUME_CHANGED,
  CMD_NAVIGATE,
  CMD_POINTER_UP,
  CMD_TEXT_CHANGED,
  CMD_VALUE_CHANGED,
  f32FromBits,
  type EventOpcode,
} from "@pathland/protocol";
import type { WritableSignal } from "@pathland/signals";
import type { MediaController } from "@pathland/view";

export interface RenderResult {
  rootId: number;
  textInputs: Map<number, WritableSignal<string>>;
  valueInputs: Map<number, WritableSignal<number>>;
  tapActions: Map<number, () => void>;
  mediaControllers: Map<number, MediaController>;
}

/** Routes raw host events into the mounted tree's bindings. */
export class InputDispatcher {
  constructor(
    private readonly result: RenderResult,
    private readonly route: WritableSignal<string>,
  ) {}

  dispatch(events: readonly EventOpcode[]): void {
    for (const event of events) {
      switch (event.command) {
        case CMD_POINTER_UP:
          this.result.tapActions.get(event.a)?.();
          break;
        case CMD_TEXT_CHANGED:
          if (event.text !== undefined) this.result.textInputs.get(event.a)?.set(event.text);
          break;
        case CMD_VALUE_CHANGED:
          this.result.valueInputs.get(event.a)?.set(f32FromBits(event.b));
          break;
        case CMD_NAVIGATE:
          if (event.text !== undefined) this.route.set(event.text);
          break;
        case CMD_MEDIA_PLAY_STATE_CHANGED:
          this.result.mediaControllers.get(event.a)?.playingReport.set(event.b !== 0);
          break;
        case CMD_MEDIA_TIME_UPDATED:
          this.result.mediaControllers.get(event.a)?.time.set(f32FromBits(event.b));
          break;
        case CMD_MEDIA_DURATION_CHANGED:
          this.result.mediaControllers.get(event.a)?.duration.set(f32FromBits(event.b));
          break;
        case CMD_MEDIA_VOLUME_CHANGED:
          this.result.mediaControllers.get(event.a)?.volumeReport.set(f32FromBits(event.b));
          break;
        case CMD_MEDIA_ENDED:
          this.result.mediaControllers.get(event.a)?.handleEnded();
          break;
        case CMD_FOCUS_CHANGED:
        case CMD_EDITING_CHANGED:
          break;
        default:
          break;
      }
    }
  }
}
