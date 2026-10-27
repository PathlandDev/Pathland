import {
  COMPONENT_MEDIA_CONTROLLER,
  PROP_MEDIA_LOOP,
  PROP_MEDIA_MUTED,
  PROP_MEDIA_POSITION,
  PROP_MEDIA_RATE,
  PROP_MEDIA_SOURCE,
  PROP_MEDIA_VOLUME,
  PROP_PLAYBACK_STATE,
  PROP_BINDING_ID,
  VAL_F32,
  VAL_STRING,
  VAL_U8,
  VAL_U32,
} from "@pathland/protocol";
import {
  computed,
  effect,
  isConstantSignal,
  isSignal,
  type ReadonlySignal,
  type WritableSignal,
} from "@pathland/signals";
import {
  withAppContext,
  type AppContext,
  type MediaController,
  type ViewElement,
  type ViewProperty,
} from "@pathland/view";
import { BindingSet } from "./bindings";
import type { RenderResult } from "./input";
import type { EmitterNode } from "./node";
import type { FrameOpcodeSink } from "./sink";

type ResolvedValue = number | string | readonly number[];
type ResolvedText = { signal: ReadonlySignal<string> | WritableSignal<string>; value: string };

function sameValue(a: ResolvedValue, b: ResolvedValue): boolean {
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((value, index) => Object.is(value, b[index]));
  }
  return Object.is(a, b);
}

export class Emitter {
  private readonly bindings = new BindingSet();
  private tree: EmitterNode | null = null;
  private nextId = 1;
  private rootId = 0;
  private mounted = false;

  constructor(private readonly sink: FrameOpcodeSink) {}

  mount(root: ViewElement, context: AppContext): RenderResult {
    if (this.mounted) throw new Error("Emitter already mounted");
    this.mounted = true;
    const tree = withAppContext(context, () => this.materialize(root));
    this.tree = tree;
    this.assignIds(tree);
    this.sink.beginFrame();
    this.emitNode(tree);
    this.sink.endFrame();
    this.registerBindings(tree);
    this.rootId = tree.id;
    return this.collectResult(tree);
  }

  renderFull(): void {
    if (!this.tree) throw new Error("Emitter not mounted");
    this.sink.beginFrame();
    this.emitNode(this.tree);
    this.sink.endFrame();
  }

  destroy(): void {
    this.bindings.destroy();
    this.tree = null;
  }

  get root(): number {
    return this.rootId;
  }

  private materialize(element: ViewElement): EmitterNode {
    const children = typeof element.children === "function" ? element.children() : element.children;
    const controller = element.mediaController;
    return {
      id: 0,
      component: controller ? COMPONENT_MEDIA_CONTROLLER : element.component,
      props: controller ? [...element.props, ...mediaProperties(controller)] : element.props,
      mods: element.mods,
      textSignal: element.textSignal,
      textBinding: element.textBinding,
      valueBinding: element.valueBinding,
      tapAction: element.tapAction,
      mediaController: controller,
      children: children.map((child: ViewElement) => this.materialize(child)),
    };
  }

  private assignIds(node: EmitterNode): void {
    node.id = this.nextId++;
    for (const child of node.children) this.assignIds(child);
  }

  private emitNode(node: EmitterNode): void {
    this.sink.createNode(node.id, node.component);
    for (const prop of node.props) {
      this.sink.setProperty(node.id, prop.property, prop.valueType, this.propertyValue(prop));
    }
    for (const mod of node.mods) {
      this.sink.setProperty(node.id, mod.property, mod.valueType, this.propertyValue(mod));
    }
    if (node.textBinding || node.valueBinding) {
      this.sink.setProperty(node.id, PROP_BINDING_ID, VAL_U32, node.id);
    }
    const resolved = this.resolveText(node);
    if (resolved) {
      node.text = resolved.value;
      if (resolved.value !== "") this.sink.setText(node.id, resolved.value);
    }
    for (const child of node.children) {
      this.emitNode(child);
      this.sink.insertChild(node.id, child.id);
    }
  }

  private resolveText(node: EmitterNode): ResolvedText | null {
    const signal = node.textBinding ?? node.textSignal;
    if (!signal) return null;
    return { signal, value: signal() };
  }

  private propertyValue(property: ViewProperty): ResolvedValue {
    const value = property.value;
    return isSignal(value) ? (value() as ResolvedValue) : (value as ResolvedValue);
  }

  private registerBindings(node: EmitterNode): void {
    const resolved = this.resolveText(node);
    if (resolved && !isConstantSignal(resolved.signal)) {
      const signal = resolved.signal;
      this.bindings.add(
        effect(() => {
          const value = signal();
          if (value === node.text) return;
          node.text = value;
          this.sink.beginFrame();
          this.sink.setText(node.id, value);
          this.sink.endFrame();
        }),
      );
    }
    for (const property of [...node.props, ...node.mods]) {
      if (!isSignal(property.value) || isConstantSignal(property.value)) continue;
      const signal = property.value as ReadonlySignal<number | string>;
      let previous = this.propertyValue(property);
      this.bindings.add(
        effect(() => {
          const next = signal() as ResolvedValue;
          if (sameValue(previous, next)) return;
          previous = next;
          this.sink.beginFrame();
          this.sink.setProperty(node.id, property.property, property.valueType, next);
          this.sink.endFrame();
        }),
      );
    }
    for (const child of node.children) this.registerBindings(child);
  }

  private collectResult(tree: EmitterNode): RenderResult {
    const textInputs = new Map<number, WritableSignal<string>>();
    const valueInputs = new Map<number, WritableSignal<number>>();
    const tapActions = new Map<number, () => void>();
    const mediaControllers = new Map<number, MediaController>();
    const walk = (node: EmitterNode): void => {
      if (node.textBinding) textInputs.set(node.id, node.textBinding);
      if (node.valueBinding) valueInputs.set(node.id, node.valueBinding);
      if (node.tapAction) tapActions.set(node.id, node.tapAction);
      if (node.mediaController) mediaControllers.set(node.id, node.mediaController);
      for (const child of node.children) walk(child);
    };
    walk(tree);
    return { rootId: tree.id, textInputs, valueInputs, tapActions, mediaControllers };
  }
}

function mediaProperties(controller: MediaController): ViewProperty[] {
  return [
    { property: PROP_MEDIA_SOURCE, valueType: VAL_STRING, value: controller.sourceSignal },
    {
      property: PROP_PLAYBACK_STATE,
      valueType: VAL_U32,
      value: computed(() => (controller.playing() ? 1 : 0)),
    },
    { property: PROP_MEDIA_POSITION, valueType: VAL_F32, value: controller.position },
    { property: PROP_MEDIA_VOLUME, valueType: VAL_F32, value: controller.volume },
    {
      property: PROP_MEDIA_MUTED,
      valueType: VAL_U8,
      value: computed(() => (controller.muted() ? 1 : 0)),
    },
    {
      property: PROP_MEDIA_LOOP,
      valueType: VAL_U8,
      value: computed(() => (controller.loop() ? 1 : 0)),
    },
    { property: PROP_MEDIA_RATE, valueType: VAL_F32, value: controller.rate },
  ];
}
