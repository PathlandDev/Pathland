import type { ReadonlySignal, WritableSignal } from "@pathland/signals";
import type { MediaController, ViewProperty } from "@pathland/view";

export interface EmitterNode {
  id: number;
  component: number;
  props: readonly ViewProperty[];
  mods: readonly ViewProperty[];
  children: EmitterNode[];
  textSignal?: ReadonlySignal<string>;
  textBinding?: WritableSignal<string>;
  valueBinding?: WritableSignal<number>;
  text?: string;
  tapAction?: () => void;
  mediaController?: MediaController;
}
