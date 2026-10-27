export interface ReadonlySignal<T> {
  (): T;
  get(): T;
  readonly __pathlandSignal: true;
}

export interface WritableSignal<T> extends ReadonlySignal<T> {
  set(value: T): void;
  update(fn: (current: T) => T): void;
  readonly __pathlandWritable: true;
}

export type MaybeSignal<T> = T | ReadonlySignal<T>;
export type Cleanup = () => void;
export type EffectFn = () => void | Cleanup;
export interface EffectRef {
  destroy(): void;
}

interface ConsumerNode {
  markDirty(): void;
  addProducer(producer: ProducerNode): void;
  removeProducer(producer: ProducerNode): void;
}

interface ProducerNode {
  readonly version: number;
  readonly consumers: Set<ConsumerNode>;
}

let activeConsumer: ConsumerNode | null = null;
const effectQueue: EffectNode[] = [];
let flushing = false;

function track(producer: ProducerNode): void {
  activeConsumer?.addProducer(producer);
}

function flushEffects(): void {
  if (flushing) return;
  flushing = true;
  try {
    while (effectQueue.length > 0) {
      const effect = effectQueue.shift()!;
      if (effect.dirty && !effect.destroyed) effect.run();
    }
  } finally {
    flushing = false;
  }
}

class SignalNode<T> implements ProducerNode {
  version = 0;
  readonly consumers = new Set<ConsumerNode>();

  constructor(
    private value: T,
    private readonly equal: (a: T, b: T) => boolean,
  ) {}

  get current(): T {
    return this.value;
  }

  read(): T {
    track(this);
    return this.value;
  }

  set(next: T): void {
    if (this.equal(this.value, next)) return;
    this.value = next;
    this.version++;
    for (const consumer of [...this.consumers]) consumer.markDirty();
    flushEffects();
  }
}

class ComputedNode<T> implements ProducerNode, ConsumerNode {
  version = 0;
  readonly consumers = new Set<ConsumerNode>();
  readonly producers = new Set<ProducerNode>();
  dirty = true;

  private computing = false;
  private hasRun = false;
  private cached!: T;
  private hasError = false;
  private error: unknown;

  constructor(
    private readonly fn: () => T,
    private readonly equal: (a: T, b: T) => boolean,
  ) {}

  addProducer(producer: ProducerNode): void {
    this.producers.add(producer);
    producer.consumers.add(this);
  }

  removeProducer(producer: ProducerNode): void {
    this.producers.delete(producer);
  }

  private clearProducers(): void {
    for (const producer of this.producers) producer.consumers.delete(this);
    this.producers.clear();
  }

  markDirty(): void {
    if (this.dirty) return;
    this.dirty = true;
    for (const consumer of [...this.consumers]) consumer.markDirty();
  }

  read(): T {
    if (this.computing) throw new Error("circular signal dependency");
    if (this.dirty) this.evaluate();
    if (this.hasError) throw this.error;
    track(this);
    return this.cached;
  }

  private evaluate(): void {
    this.computing = true;
    this.dirty = false;
    this.clearProducers();
    const previous = activeConsumer;
    activeConsumer = this;
    try {
      const next = this.fn();
      if (!this.hasRun || !this.equal(this.cached, next)) {
        this.cached = next;
        this.hasRun = true;
        this.version++;
        for (const consumer of [...this.consumers]) consumer.markDirty();
      }
      this.hasError = false;
      this.error = undefined;
    } catch (error) {
      this.hasError = true;
      this.error = error;
      this.version++;
      for (const consumer of [...this.consumers]) consumer.markDirty();
    } finally {
      activeConsumer = previous;
      this.computing = false;
    }
  }
}

class EffectNode implements ConsumerNode {
  dirty = false;
  destroyed = false;
  readonly producers = new Set<ProducerNode>();
  private cleanup: Cleanup | null = null;

  constructor(private readonly fn: EffectFn) {}

  addProducer(producer: ProducerNode): void {
    this.producers.add(producer);
    producer.consumers.add(this);
  }

  removeProducer(producer: ProducerNode): void {
    this.producers.delete(producer);
  }

  private clearProducers(): void {
    for (const producer of this.producers) producer.consumers.delete(this);
    this.producers.clear();
  }

  markDirty(): void {
    if (this.destroyed) return;
    if (!this.dirty) {
      this.dirty = true;
      effectQueue.push(this);
    }
  }

  run(): void {
    if (this.destroyed) return;
    this.dirty = false;
    if (this.cleanup) {
      this.cleanup();
      this.cleanup = null;
    }
    this.clearProducers();
    const previous = activeConsumer;
    activeConsumer = this;
    try {
      const cleanup = this.fn();
      if (typeof cleanup === "function") this.cleanup = cleanup;
    } finally {
      activeConsumer = previous;
    }
  }

  destroy(): void {
    if (this.destroyed) return;
    this.destroyed = true;
    this.dirty = false;
    if (this.cleanup) {
      this.cleanup();
      this.cleanup = null;
    }
    this.clearProducers();
  }
}

function withSignalApi<T extends object>(fn: T, api: object): T {
  return Object.assign(fn, api, { __pathlandSignal: true });
}

export function signal<T>(initial: T, equal: (a: T, b: T) => boolean = Object.is): WritableSignal<T> {
  const node = new SignalNode(initial, equal);
  const callable = (() => node.read()) as unknown as WritableSignal<T>;
  return withSignalApi(callable, {
    get: () => node.read(),
    set: (value: T) => node.set(value),
    update: (fn: (current: T) => T) => node.set(fn(node.current)),
    __pathlandWritable: true,
  }) as WritableSignal<T>;
}

export function computed<T>(fn: () => T, equal: (a: T, b: T) => boolean = Object.is): ReadonlySignal<T> {
  const node = new ComputedNode(fn, equal);
  const callable = (() => node.read()) as unknown as ReadonlySignal<T>;
  return withSignalApi(callable, { get: () => node.read() }) as ReadonlySignal<T>;
}

export function constant<T>(value: T): ReadonlySignal<T> {
  const callable = (() => value) as unknown as ReadonlySignal<T>;
  return withSignalApi(callable, {
    get: () => value,
    __pathlandConstant: true,
  }) as ReadonlySignal<T>;
}

export function effect(fn: EffectFn): EffectRef {
  const node = new EffectNode(fn);
  node.run();
  return { destroy: () => node.destroy() };
}

export function untracked<T>(fn: () => T): T {
  const previous = activeConsumer;
  activeConsumer = null;
  try {
    return fn();
  } finally {
    activeConsumer = previous;
  }
}

export function isSignal(value: unknown): value is ReadonlySignal<unknown> {
  return typeof value === "function" && (value as { __pathlandSignal?: unknown }).__pathlandSignal === true;
}

export function isConstantSignal(value: unknown): boolean {
  return isSignal(value) && (value as { __pathlandConstant?: unknown }).__pathlandConstant === true;
}
