import type { ReadonlySignal, WritableSignal } from "@pathland/signals";
import type { MediaController } from "./media";

/** A static value or a reactive signal of a value. */
export type PropertyValue = number | string | readonly number[] | ReadonlySignal<number> | ReadonlySignal<string>;

export interface ViewProperty {
  readonly property: number;
  readonly valueType: number;
  readonly value: PropertyValue;
}

export type ViewModifier = ViewProperty;
export type ViewChildren = ViewElement[] | (() => ViewElement[]);

export interface ViewElement {
  readonly component: number;
  readonly props: readonly ViewProperty[];
  readonly mods: readonly ViewModifier[];
  readonly children: ViewChildren;
  readonly textSignal?: ReadonlySignal<string>;
  readonly textBinding?: WritableSignal<string>;
  readonly valueBinding?: WritableSignal<number>;
  readonly tapAction?: () => void;
  readonly mediaController?: MediaController;
  modifiers(...mods: ViewModifier[]): ViewElement;
}

export interface ElementOptions {
  component: number;
  props?: readonly ViewProperty[];
  children?: ViewChildren;
  textSignal?: ReadonlySignal<string>;
  textBinding?: WritableSignal<string>;
  valueBinding?: WritableSignal<number>;
  tapAction?: () => void;
  mediaController?: MediaController;
}

/** Create a retained view element. Modifiers are immutable chainable values. */
export function element(options: ElementOptions): ViewElement {
  const view: ViewElement = {
    component: options.component,
    props: options.props ?? [],
    mods: [],
    children: options.children ?? [],
    textSignal: options.textSignal,
    textBinding: options.textBinding,
    valueBinding: options.valueBinding,
    tapAction: options.tapAction,
    mediaController: options.mediaController,
    modifiers(...mods: ViewModifier[]): ViewElement {
      return { ...this, mods: [...this.mods, ...mods] };
    },
  };
  return view;
}
