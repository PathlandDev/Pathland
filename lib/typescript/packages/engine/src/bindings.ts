import type { EffectRef } from "@pathland/signals";

/** Owns a set of binding effects so a mounted tree can be torn down cleanly. */
export class BindingSet {
  private readonly effects: EffectRef[] = [];

  add(effect: EffectRef): void {
    this.effects.push(effect);
  }

  destroy(): void {
    for (const effect of this.effects) effect.destroy();
    this.effects.length = 0;
  }
}
