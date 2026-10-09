//! Reactive signals (Angular-style).
//!
//! A signal is a piece of application state owned by the shared [`Runtime`].
//! Retained tree nodes bind their text or a property to a signal via
//! [`SignalId`]; the engine records those dependencies during emission, so when
//! a signal's value changes, only the nodes that read it re-emit their deltas.
//!
//! ## Reactive runtime
//!
//! [`Runtime`] owns an `Rc<RefCell<Store>>` shared with the typed handles
//! ([`Signal`]/[`WritableSignal`]) and with the [`Engine`](crate::Engine). It
//! supports **computed** (derived, lazily recomputed, equality-suppressed) and
//! **effect** (runs immediately, then on dependency change) signals, plus
//! `untracked` reads. Because `pathland-engine` is `no_std` (no thread-local),
//! the "current computation" is an explicit stack inside the store — the
//! single-threaded equivalent of the Java DSL's thread-local reactive context.
//!
//! Signals live here (in `no_std` core) so any host — Rust, the native C ABI,
//! a future WASM/browser guest — shares the same reactive runtime.

use alloc::rc::Rc;
use alloc::string::String;
use alloc::vec;
use alloc::vec::Vec;
use core::cell::RefCell;
use core::marker::PhantomData;

/// A signal's value: one of the protocol value kinds.
///
/// Property bindings pack to a `SET_PROPERTY` value (u32); text bindings use
/// [`SignalValue::Str`]. The richer kinds (`Bool`, `Enum`) are ergonomic typing
/// at the host boundary — they still collapse onto the wire's `u32` value.
#[derive(Debug, Clone, PartialEq)]
pub enum SignalValue {
    /// A float property (spacing, font_size, padding, opacity, size hints…).
    F32(f32),
    /// A raw u32 property (color `0xAARRGGBB`, an `EVENT_LISTENERS` mask, …).
    U32(u32),
    /// A boolean property (visible, clips_to_bounds, …).
    Bool(bool),
    /// An enum property wire value (alignment, text alignment, truncation, …).
    Enum(u8),
    /// A string (text content, font family).
    Str(String),
}

impl SignalValue {
    /// Pack this value as a `SET_PROPERTY` `u32` value. `None` for [`Str`],
    /// which is only valid for text bindings.
    pub fn to_property_u32(&self) -> Option<u32> {
        match self {
            SignalValue::F32(f) => Some(f.to_bits()),
            SignalValue::U32(u) => Some(*u),
            SignalValue::Bool(b) => Some(if *b { 1.0f32 } else { 0.0f32 }.to_bits()),
            SignalValue::Enum(e) => Some((*e as f32).to_bits()),
            SignalValue::Str(_) => None,
        }
    }

    /// The text for a text binding. `None` for non-`Str` values.
    pub fn to_text(&self) -> Option<&str> {
        match self {
            SignalValue::Str(s) => Some(s.as_str()),
            _ => None,
        }
    }
}

/// A stable, non-generic handle to a signal (an index into the runtime's store).
/// This is the FFI-safe handle handed to foreign hosts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct SignalId(pub u32);

/// A dependency: a node binding that reads a signal.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Dep {
    /// The node's text is bound to the signal.
    Text { node: u32 },
    /// A node property is bound to the signal.
    Property { node: u32, prop: u16 },
}

/// A value kind that maps to/from a [`SignalValue`] — the typed-signal surface.
pub trait SignalValueKind: Clone + PartialEq + core::fmt::Debug + 'static {
    /// Pack this value as a signal value.
    fn into_signal_value(self) -> SignalValue;
    /// Read this kind back out of a signal value, if the value's kind matches.
    fn from_signal_value(value: &SignalValue) -> Option<Self>;
}

impl SignalValueKind for f32 {
    fn into_signal_value(self) -> SignalValue {
        SignalValue::F32(self)
    }
    fn from_signal_value(value: &SignalValue) -> Option<Self> {
        match value {
            SignalValue::F32(v) => Some(*v),
            _ => None,
        }
    }
}

impl SignalValueKind for u32 {
    fn into_signal_value(self) -> SignalValue {
        SignalValue::U32(self)
    }
    fn from_signal_value(value: &SignalValue) -> Option<Self> {
        match value {
            SignalValue::U32(v) => Some(*v),
            _ => None,
        }
    }
}

impl SignalValueKind for bool {
    fn into_signal_value(self) -> SignalValue {
        SignalValue::Bool(self)
    }
    fn from_signal_value(value: &SignalValue) -> Option<Self> {
        match value {
            SignalValue::Bool(v) => Some(*v),
            _ => None,
        }
    }
}

impl SignalValueKind for u8 {
    fn into_signal_value(self) -> SignalValue {
        SignalValue::Enum(self)
    }
    fn from_signal_value(value: &SignalValue) -> Option<Self> {
        match value {
            SignalValue::Enum(v) => Some(*v),
            _ => None,
        }
    }
}

impl SignalValueKind for String {
    fn into_signal_value(self) -> SignalValue {
        SignalValue::Str(self)
    }
    fn from_signal_value(value: &SignalValue) -> Option<Self> {
        match value {
            SignalValue::Str(v) => Some(v.clone()),
            _ => None,
        }
    }
}

// ---------------------------------------------------------------------------
// Store + runtime
// ---------------------------------------------------------------------------

/// The node evaluating on the computation stack — a computed signal or an effect.
#[derive(Clone, Copy, PartialEq, Eq)]
enum Computation {
    Signal(usize),
    Effect(usize),
}

/// A reader of a signal (a computed or an effect).
#[derive(Clone, Copy, PartialEq, Eq)]
enum Subscriber {
    Signal(usize),
    Effect(usize),
}

struct Cell {
    value: SignalValue,
    /// A computed's evaluator (`None` for a plain value signal).
    compute: Option<Rc<dyn Fn() -> SignalValue>>,
    /// Signals this computed reads (rebuilt on each evaluation).
    sources: Vec<usize>,
    /// Cells (computed/effect) that read this signal.
    subscribers: Vec<Subscriber>,
    /// Node bindings on this signal (engine-owned).
    node_deps: Vec<Dep>,
    dirty: bool,
    initialized: bool,
}

struct EffectCell {
    run: Rc<dyn Fn()>,
    sources: Vec<usize>,
}

#[derive(Default)]
struct Store {
    cells: Vec<Cell>,
    effects: Vec<EffectCell>,
    /// The current computation stack (the no_std equivalent of a reactive
    /// context thread-local).
    stack: Vec<Computation>,
    /// Nonzero while inside `untracked` (reads do not record dependencies).
    untracked: u32,
}

/// The shared reactive runtime. Cheap to clone; all clones observe the same
/// signals (handles, the engine, and computed closures share one store).
#[derive(Clone)]
pub struct Runtime {
    store: Rc<RefCell<Store>>,
}

impl Default for Runtime {
    fn default() -> Self {
        Runtime {
            store: Rc::new(RefCell::new(Store::default())),
        }
    }
}

impl Runtime {
    /// Create a new runtime.
    pub fn new() -> Self {
        Self::default()
    }

    /// Create a plain value signal.
    pub fn create(&self, value: SignalValue) -> SignalId {
        let mut store = self.store.borrow_mut();
        let id = SignalId(store.cells.len() as u32);
        store.cells.push(Cell {
            value,
            compute: None,
            sources: Vec::new(),
            subscribers: Vec::new(),
            node_deps: Vec::new(),
            dirty: false,
            initialized: true,
        });
        id
    }

    /// Create a typed writable signal.
    pub fn signal<T: SignalValueKind>(&self, initial: T) -> WritableSignal<T> {
        WritableSignal {
            id: self.create(initial.into_signal_value()),
            runtime: self.clone(),
            _kind: PhantomData,
        }
    }

    /// Create a **computed** signal (lazily evaluated, memoized, equality
    /// suppressed). The closure reads other signals via their handles.
    pub fn computed<T: SignalValueKind>(&self, compute: impl Fn() -> T + 'static) -> Signal<T> {
        let wrapped: Rc<dyn Fn() -> SignalValue> =
            Rc::new(move || compute().into_signal_value());
        let mut store = self.store.borrow_mut();
        let id = SignalId(store.cells.len() as u32);
        store.cells.push(Cell {
            value: SignalValue::U32(0),
            compute: Some(wrapped),
            sources: Vec::new(),
            subscribers: Vec::new(),
            node_deps: Vec::new(),
            dirty: true,
            initialized: false,
        });
        Signal {
            id,
            runtime: self.clone(),
            _kind: PhantomData,
        }
    }

    /// Register an **effect**: it runs immediately, then again whenever any
    /// signal it reads changes.
    pub fn effect(&self, run: impl Fn() + 'static) {
        let run: Rc<dyn Fn()> = Rc::new(run);
        let idx = {
            let mut store = self.store.borrow_mut();
            let idx = store.effects.len();
            store.effects.push(EffectCell {
                run,
                sources: Vec::new(),
            });
            idx
        };
        self.run_effect(idx);
    }

    /// Read a signal's current value (recomputing a computed if needed).
    pub fn get(&self, id: SignalId) -> SignalValue {
        self.ensure(id.0 as usize);
        let mut store = self.store.borrow_mut();
        let value = store.cells[id.0 as usize].value.clone();
        if store.untracked == 0 {
            if let Some(&top) = store.stack.last() {
                Self::record(&mut store, top, id.0 as usize);
            }
        }
        value
    }

    /// Read a typed signal's current value.
    pub fn read<T: SignalValueKind>(&self, id: SignalId) -> Option<T> {
        T::from_signal_value(&self.get(id))
    }

    /// Record that computation `top` reads signal `s` (idempotent).
    fn record(store: &mut Store, top: Computation, s: usize) {
        match top {
            Computation::Signal(t) if t != s => {
                if !store.cells[t].sources.contains(&s) {
                    store.cells[t].sources.push(s);
                }
                if !store.cells[s].subscribers.contains(&Subscriber::Signal(t)) {
                    store.cells[s].subscribers.push(Subscriber::Signal(t));
                }
            }
            Computation::Effect(e) => {
                if !store.effects[e].sources.contains(&s) {
                    store.effects[e].sources.push(s);
                }
                if !store.cells[s].subscribers.contains(&Subscriber::Effect(e)) {
                    store.cells[s].subscribers.push(Subscriber::Effect(e));
                }
            }
            _ => {}
        }
    }

    /// Ensure a computed cell is up to date (evaluate it if dirty/uninitialized).
    fn ensure(&self, idx: usize) {
        let compute = {
            let store = self.store.borrow();
            match store.cells.get(idx) {
                Some(cell) if cell.compute.is_some() && (cell.dirty || !cell.initialized) => {
                    cell.compute.clone()
                }
                _ => return,
            }
        };
        let Some(compute) = compute else { return };

        // Cycle guard: if this computed is already on the stack, leave it stale.
        {
            let mut store = self.store.borrow_mut();
            if store.stack.contains(&Computation::Signal(idx)) {
                return;
            }
            store.stack.push(Computation::Signal(idx));
        }

        // Evaluate with no borrow held (the closure reads signals via `get`).
        let value = compute();

        {
            let mut store = self.store.borrow_mut();
            let cell = &mut store.cells[idx];
            cell.value = value;
            cell.dirty = false;
            cell.initialized = true;
            store.stack.pop();
        }
    }

    /// Set a signal's value. Marks and recomputes dependent computeds, runs
    /// affected effects, and returns the ids of the signals whose value the
    /// caller must re-emit (the set signal plus any computed that changed).
    pub fn set(&self, id: SignalId, value: SignalValue) -> Vec<SignalId> {
        let idx = id.0 as usize;
        {
            let mut store = self.store.borrow_mut();
            let cell = &mut store.cells[idx];
            if cell.initialized && cell.value == value {
                return Vec::new();
            }
            cell.value = value;
            cell.initialized = true;
        }

        // Propagate: mark computed subscribers dirty (transitively), collect
        // effects to run.
        let mut dirty: Vec<usize> = Vec::new();
        let mut effects: Vec<usize> = Vec::new();
        {
            let mut store = self.store.borrow_mut();
            let subs = store.cells[idx].subscribers.clone();
            let mut queue: Vec<Subscriber> = subs;
            while let Some(sub) = queue.pop() {
                match sub {
                    Subscriber::Signal(s) => {
                        if !dirty.contains(&s) {
                            dirty.push(s);
                            store.cells[s].dirty = true;
                            store.cells[s].sources.clear();
                            queue.extend(store.cells[s].subscribers.clone());
                        }
                    }
                    Subscriber::Effect(e) => {
                        if !effects.contains(&e) {
                            effects.push(e);
                        }
                    }
                }
            }
        }

        // Recompute dirty computeds and note which actually changed.
        let mut changed: Vec<SignalId> = vec![id];
        for d in dirty {
            let before = self.store.borrow().cells[d].value.clone();
            self.ensure(d);
            let after = self.store.borrow().cells[d].value.clone();
            if before != after {
                changed.push(SignalId(d as u32));
            }
        }

        // Synchronous effect flush.
        for e in effects {
            self.run_effect(e);
        }

        changed
    }

    /// Run an effect (with dependency tracking), rebuilding its sources.
    fn run_effect(&self, idx: usize) {
        let run = {
            let mut store = self.store.borrow_mut();
            store.effects[idx].sources.clear();
            store.effects[idx].run.clone()
        };
        {
            let mut store = self.store.borrow_mut();
            if store.stack.contains(&Computation::Effect(idx)) {
                return; // re-entrant effect write: skip
            }
            store.stack.push(Computation::Effect(idx));
        }
        run();
        self.store.borrow_mut().stack.pop();
    }

    /// Run `f` without recording dependencies.
    pub fn untracked<T>(&self, f: impl FnOnce() -> T) -> T {
        self.store.borrow_mut().untracked += 1;
        let value = f();
        self.store.borrow_mut().untracked -= 1;
        value
    }

    /// Read a computed's current value (typed).
    pub fn computed_value<T: SignalValueKind>(&self, signal: Signal<T>) -> Option<T> {
        self.read(signal.id())
    }

    // --- node deps (engine) ---

    /// Clear all node bindings (called at the start of a full emit).
    pub fn clear_node_deps(&self) {
        let mut store = self.store.borrow_mut();
        for cell in &mut store.cells {
            cell.node_deps.clear();
        }
    }

    /// Record a node binding on a signal.
    pub fn add_node_dep(&self, id: SignalId, dep: Dep) {
        let mut store = self.store.borrow_mut();
        if let Some(cell) = store.cells.get_mut(id.0 as usize) {
            if !cell.node_deps.contains(&dep) {
                cell.node_deps.push(dep);
            }
        }
    }

    /// The node bindings recorded on a signal.
    pub fn node_deps(&self, id: SignalId) -> Vec<Dep> {
        self.store
            .borrow()
            .cells
            .get(id.0 as usize)
            .map(|c| c.node_deps.clone())
            .unwrap_or_default()
    }

    /// Number of signals allocated (diagnostics).
    pub fn len(&self) -> usize {
        self.store.borrow().cells.len()
    }

    /// Whether no signals are allocated (diagnostics).
    pub fn is_empty(&self) -> bool {
        self.store.borrow().cells.is_empty()
    }
}

// ---------------------------------------------------------------------------
// Typed handles
// ---------------------------------------------------------------------------

/// A read-only handle to a signal (a typed [`SignalId`] bound to a [`Runtime`]).
pub struct Signal<T> {
    id: SignalId,
    runtime: Runtime,
    _kind: PhantomData<fn() -> T>,
}

impl<T> Signal<T> {
    /// Wrap a raw id as a typed read-only signal on `runtime`.
    pub fn from_id(id: SignalId, runtime: &Runtime) -> Self {
        Signal {
            id,
            runtime: runtime.clone(),
            _kind: PhantomData,
        }
    }
    /// The underlying id.
    pub const fn id(&self) -> SignalId {
        self.id
    }
}

impl<T: SignalValueKind> Signal<T> {
    /// Read the current value.
    pub fn get(&self) -> Option<T> {
        self.runtime.read(self.id)
    }
}

impl<T> Clone for Signal<T> {
    fn clone(&self) -> Self {
        Signal {
            id: self.id,
            runtime: self.runtime.clone(),
            _kind: PhantomData,
        }
    }
}
impl<T> core::fmt::Debug for Signal<T> {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "Signal({})", self.id.0)
    }
}

/// A writable handle to a signal (a typed [`SignalId`] bound to a [`Runtime`]).
pub struct WritableSignal<T> {
    id: SignalId,
    runtime: Runtime,
    _kind: PhantomData<fn() -> T>,
}

impl<T> WritableSignal<T> {
    /// Wrap a raw id as a typed writable signal on `runtime`.
    pub fn from_id(id: SignalId, runtime: &Runtime) -> Self {
        WritableSignal {
            id,
            runtime: runtime.clone(),
            _kind: PhantomData,
        }
    }
    /// The underlying id.
    pub const fn id(&self) -> SignalId {
        self.id
    }
    /// A read-only view of this signal.
    pub fn as_readonly(&self) -> Signal<T> {
        Signal::from_id(self.id, &self.runtime)
    }
}

impl<T: SignalValueKind> WritableSignal<T> {
    /// Read the current value.
    pub fn get(&self) -> Option<T> {
        self.runtime.read(self.id)
    }
}

impl<T> Clone for WritableSignal<T> {
    fn clone(&self) -> Self {
        WritableSignal {
            id: self.id,
            runtime: self.runtime.clone(),
            _kind: PhantomData,
        }
    }
}
impl<T> core::fmt::Debug for WritableSignal<T> {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "WritableSignal({})", self.id.0)
    }
}

/// Anything that identifies a signal — a raw [`SignalId`] or a typed
/// [`Signal`]/[`WritableSignal`] handle. Lets DSL builders accept either.
pub trait IntoSignalId {
    /// The underlying id.
    fn into_signal_id(self) -> SignalId;
}

impl IntoSignalId for SignalId {
    fn into_signal_id(self) -> SignalId {
        self
    }
}
impl<T> IntoSignalId for Signal<T> {
    fn into_signal_id(self) -> SignalId {
        self.id()
    }
}
impl<T> IntoSignalId for WritableSignal<T> {
    fn into_signal_id(self) -> SignalId {
        self.id()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn create_set_get_round_trips() {
        let rt = Runtime::new();
        let id = rt.create(SignalValue::F32(1.0));
        assert_eq!(rt.get(id), SignalValue::F32(1.0));
        assert_eq!(rt.set(id, SignalValue::F32(2.0)), vec![id]);
        assert_eq!(rt.get(id), SignalValue::F32(2.0));
        assert!(rt.set(id, SignalValue::F32(2.0)).is_empty(), "no-op set");
    }

    #[test]
    fn computed_is_lazy_memoized_and_equality_suppressed() {
        let rt = Runtime::new();
        let a = rt.signal(2.0f32);
        let b = rt.signal(3.0f32);
        let (ac, bc) = (a.clone(), b.clone());
        let sum = rt.computed(move || ac.get().unwrap() + bc.get().unwrap());
        assert_eq!(sum.get(), Some(5.0));

        // Changing a source marks the computed dirty and recomputes it.
        let changed = rt.set(a.id(), SignalValue::F32(10.0));
        assert_eq!(changed, vec![a.id(), sum.id()]);
        assert_eq!(sum.get(), Some(13.0));

        // A set that does not change the computed's value reports nothing.
        let changed = rt.set(a.id(), SignalValue::F32(10.0));
        assert!(changed.is_empty());

        // A second computed reads the first (chained computeds).
        let sumc = sum.clone();
        let doubled = rt.computed(move || sumc.get().unwrap() * 2.0);
        assert_eq!(doubled.get(), Some(26.0));
        rt.set(a.id(), SignalValue::F32(0.0));
        assert_eq!(doubled.get(), Some(6.0));
    }

    #[test]
    fn effects_run_immediately_and_on_change() {
        let rt = Runtime::new();
        let s = rt.signal(1.0f32);
        let seen = Rc::new(RefCell::new(Vec::<f32>::new()));
        let observed = seen.clone();
        let watched = s.clone();
        rt.effect(move || observed.borrow_mut().push(watched.get().unwrap()));
        assert_eq!(*seen.borrow(), vec![1.0]);
        rt.set(s.id(), SignalValue::F32(2.0));
        assert_eq!(*seen.borrow(), vec![1.0, 2.0]);
        // Unchanged set does not re-run the effect.
        rt.set(s.id(), SignalValue::F32(2.0));
        assert_eq!(*seen.borrow(), vec![1.0, 2.0]);
    }

    #[test]
    fn untracked_reads_do_not_subscribe() {
        let rt = Runtime::new();
        let s = rt.signal(1.0f32);
        let seen = Rc::new(RefCell::new(0usize));
        let observed = seen.clone();
        let watched = s.clone();
        let rt2 = rt.clone();
        rt.effect(move || {
            // Reading under `untracked` must not subscribe the effect.
            let _ = rt2.untracked(|| watched.get().unwrap());
            *observed.borrow_mut() += 1;
        });
        assert_eq!(*seen.borrow(), 1);
        rt.set(s.id(), SignalValue::F32(2.0));
        // The effect did not depend on `s`, so it did not re-run.
        assert_eq!(*seen.borrow(), 1);
    }

    #[test]
    fn packing_matches_protocol_convention() {
        assert_eq!(SignalValue::F32(4.0).to_property_u32(), Some(4.0f32.to_bits()));
        assert_eq!(SignalValue::U32(0xFF_0000FF).to_property_u32(), Some(0xFF_0000FF));
        assert_eq!(SignalValue::Bool(true).to_property_u32(), Some(1.0f32.to_bits()));
        assert_eq!(SignalValue::Bool(false).to_property_u32(), Some(0.0f32.to_bits()));
        assert_eq!(SignalValue::Enum(2).to_property_u32(), Some(2.0f32.to_bits()));
        assert_eq!(SignalValue::Str("x".into()).to_property_u32(), None);
        assert_eq!(SignalValue::Str("x".into()).to_text(), Some("x"));
        assert_eq!(SignalValue::F32(1.0).to_text(), None);
    }

    #[test]
    fn node_deps_record_and_clear() {
        let rt = Runtime::new();
        let id = rt.create(SignalValue::Str("hi".into()));
        rt.add_node_dep(id, Dep::Text { node: 3 });
        rt.add_node_dep(id, Dep::Text { node: 3 });
        assert_eq!(rt.node_deps(id), vec![Dep::Text { node: 3 }]);
        rt.clear_node_deps();
        assert!(rt.node_deps(id).is_empty());
    }
}
