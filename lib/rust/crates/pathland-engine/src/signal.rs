//! Reactive signals (Angular-style).
//!
//! A [`Signal`] is a piece of application state owned by the engine. Retained
//! tree nodes bind their text or a property to a signal via [`SignalId`]; the
//! engine records those dependencies during emission, so when a signal's value
//! changes, only the nodes that read it re-emit their deltas.
//!
//! Signals live here (in `no_std` core) so any host — Rust, the native C ABI,
//! a future WASM/browser guest — shares the same reactive runtime.

use alloc::string::String;
use alloc::vec::Vec;
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

/// A stable, non-generic handle to a signal (an index into the engine's
/// [`SignalStore`]). This is the FFI-safe handle handed to foreign hosts.
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
///
/// The store itself is dynamically typed ([`SignalValue`]); this trait is the
/// ergonomic typing at the `Engine` boundary (`Engine::signal` / `Engine::read`).
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

/// A read-only handle to a signal (a typed [`SignalId`]).
///
/// Handles are `Copy` newtypes; the value lives in the engine's signal store, so
/// read it with [`Engine::read`](crate::Engine::read) and write a
/// [`WritableSignal`] with
/// [`Engine::set_signal`](crate::Engine::set_signal).
#[derive(PartialEq, Eq, Hash)]
pub struct Signal<T> {
    id: SignalId,
    _kind: PhantomData<fn() -> T>,
}

impl<T> Signal<T> {
    /// Wrap a raw id as a typed read-only signal.
    pub const fn from_id(id: SignalId) -> Self {
        Signal {
            id,
            _kind: PhantomData,
        }
    }
    /// The underlying id.
    pub const fn id(self) -> SignalId {
        self.id
    }
}

impl<T> Clone for Signal<T> {
    fn clone(&self) -> Self {
        *self
    }
}
impl<T> Copy for Signal<T> {}
impl<T> core::fmt::Debug for Signal<T> {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "Signal({})", self.id.0)
    }
}

/// A writable handle to a signal (a typed [`SignalId`]).
#[derive(PartialEq, Eq, Hash)]
pub struct WritableSignal<T> {
    id: SignalId,
    _kind: PhantomData<fn() -> T>,
}

impl<T> WritableSignal<T> {
    /// Wrap a raw id as a typed writable signal.
    pub const fn from_id(id: SignalId) -> Self {
        WritableSignal {
            id,
            _kind: PhantomData,
        }
    }
    /// The underlying id.
    pub const fn id(self) -> SignalId {
        self.id
    }
    /// A read-only view of this signal.
    pub const fn as_readonly(self) -> Signal<T> {
        Signal::from_id(self.id)
    }
}

impl<T> Clone for WritableSignal<T> {
    fn clone(&self) -> Self {
        *self
    }
}
impl<T> Copy for WritableSignal<T> {}
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

#[derive(Debug)]
struct SignalCell {
    value: SignalValue,
    /// Reverse dependency index: the node bindings that read this signal.
    deps: Vec<Dep>,
}

/// Owns signal storage plus the reverse dependency index (`signal -> deps`).
///
/// The engine rebuilds the index during each full emit; between emits, the
/// index lets `set` find exactly the nodes that depend on a changed signal.
#[derive(Debug, Default)]
pub struct SignalStore {
    cells: Vec<SignalCell>,
}

impl SignalStore {
    /// Create a new signal with an initial value, returning its handle.
    pub fn create(&mut self, value: SignalValue) -> SignalId {
        let id = SignalId(self.cells.len() as u32);
        self.cells.push(SignalCell {
            value,
            deps: Vec::new(),
        });
        id
    }

    /// Replace a signal's value. Returns `true` if the value actually changed.
    pub fn set(&mut self, id: SignalId, value: SignalValue) -> bool {
        if let Some(cell) = self.cells.get_mut(id.0 as usize) {
            if cell.value != value {
                cell.value = value;
                return true;
            }
        }
        false
    }

    /// Read a signal's current value.
    pub fn get(&self, id: SignalId) -> Option<&SignalValue> {
        self.cells.get(id.0 as usize).map(|c| &c.value)
    }

    /// The dependencies recorded for a signal (empty if none).
    pub fn deps(&self, id: SignalId) -> &[Dep] {
        self.cells
            .get(id.0 as usize)
            .map(|c| c.deps.as_slice())
            .unwrap_or(&[])
    }

    /// Clear all recorded dependencies (called at the start of a full emit).
    pub fn clear_deps(&mut self) {
        for cell in &mut self.cells {
            cell.deps.clear();
        }
    }

    /// Record a dependency for a signal.
    pub fn add_dep(&mut self, id: SignalId, dep: Dep) {
        if let Some(cell) = self.cells.get_mut(id.0 as usize) {
            if !cell.deps.contains(&dep) {
                cell.deps.push(dep);
            }
        }
    }

    /// Number of signals allocated (diagnostics).
    pub fn len(&self) -> usize {
        self.cells.len()
    }

    pub fn is_empty(&self) -> bool {
        self.cells.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn create_set_get_round_trips() {
        let mut store = SignalStore::default();
        let id = store.create(SignalValue::F32(1.0));
        assert_eq!(store.get(id), Some(&SignalValue::F32(1.0)));
        assert!(store.set(id, SignalValue::F32(2.0)));
        assert_eq!(store.get(id), Some(&SignalValue::F32(2.0)));
        assert!(!store.set(id, SignalValue::F32(2.0)), "no-op set");
    }

    #[test]
    fn deps_are_recorded_and_cleared() {
        let mut store = SignalStore::default();
        let id = store.create(SignalValue::Str("hi".into()));
        store.add_dep(id, Dep::Text { node: 3 });
        store.add_dep(id, Dep::Text { node: 3 });
        assert_eq!(store.deps(id), &[Dep::Text { node: 3 }]);
        store.clear_deps();
        assert!(store.deps(id).is_empty());
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
}
