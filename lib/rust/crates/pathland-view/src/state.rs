//! Cross-platform persisted state.
//!
//! A [`State<T>`] field is the `@State`-shaped form of a **persisted** signal:
//! it is auto-loaded from a [`StateStore`] at creation and auto-saved on change
//! (via a runtime effect). The store is untyped and platform-neutral — the
//! in-memory [`InMemoryStateStore`] ships here; a host can implement the trait
//! over Redis, a file, SQLite, `localStorage`, NVS flash, … (the contract is
//! `get`/`set` by string key).
//!
//! Rust has no annotation processor, so `State` fields are wired explicitly:
//!
//! ```
//! use std::rc::Rc;
//! use pathland_engine::Engine;
//! use pathland_view::state::{InMemoryStateStore, PersistentState};
//!
//! let engine = Engine::new();
//! let store = Rc::new(InMemoryStateStore::new());
//! let persisted = PersistentState::new(store, "session", engine.runtime());
//! let count = persisted.state("count", 0u32);   // key = "count:session"
//! count.set(1);
//! assert_eq!(count.get(), Some(1));
//! ```

use alloc::collections::BTreeMap;
use alloc::format;
use alloc::rc::Rc;
use alloc::string::String;
use alloc::vec::Vec;
use core::cell::RefCell;

use pathland_engine::{Runtime, SignalId, SignalValue, SignalValueKind, WritableSignal};

/// A platform-neutral key/value store for persisted state.
///
/// The value model is the engine's [`SignalValue`] (the wire value kinds), so a
/// store needs no schema and serves every signal type.
pub trait StateStore {
    /// Read a value by key.
    fn get(&self, key: &str) -> Option<SignalValue>;
    /// Write a value by key.
    fn set(&self, key: &str, value: SignalValue);
}

/// A simple in-process [`StateStore`] (a `BTreeMap` under a `RefCell`).
#[derive(Default)]
pub struct InMemoryStateStore {
    map: RefCell<BTreeMap<String, SignalValue>>,
}

impl InMemoryStateStore {
    /// An empty store.
    pub fn new() -> Self {
        Self::default()
    }
}

impl StateStore for InMemoryStateStore {
    fn get(&self, key: &str) -> Option<SignalValue> {
        self.map.borrow().get(key).cloned()
    }

    fn set(&self, key: &str, value: SignalValue) {
        self.map.borrow_mut().insert(String::from(key), value);
    }
}

/// A persisted signal field (`@State`-shaped). Read/write it like a signal; a
/// write auto-saves through the owning [`PersistentState`]'s store.
pub struct State<T> {
    signal: WritableSignal<T>,
}

impl<T: SignalValueKind> State<T> {
    /// The current value.
    pub fn get(&self) -> Option<T> {
        self.signal.get()
    }

    /// Replace the value (auto-saves + re-emits bound nodes).
    pub fn set(&self, value: T) -> Vec<SignalId> {
        self.signal.set(value)
    }

    /// Derive the next value and write it.
    pub fn update(&self, update: impl FnOnce(T) -> T) {
        if let Some(value) = self.get() {
            self.signal.set(update(value));
        }
    }

    /// The underlying writable signal (for binding a control / text).
    pub fn signal(&self) -> WritableSignal<T> {
        self.signal.clone()
    }
}

/// A scoped view over a [`StateStore`]: `state(name, initial)` auto-loads the
/// persisted value (or the initial) and auto-saves on change. The store key is
/// `"{name}:{scope}"`, so different sessions/windows never share state.
pub struct PersistentState {
    store: Rc<dyn StateStore>,
    scope: String,
    runtime: Runtime,
}

impl PersistentState {
    /// A scoped store handle over `store`.
    pub fn new(store: Rc<dyn StateStore>, scope: impl Into<String>, runtime: Runtime) -> Self {
        Self {
            store,
            scope: scope.into(),
            runtime,
        }
    }

    /// Connect a `State` field: auto-load (or `initial`) and auto-save on change.
    pub fn state<T: SignalValueKind + 'static>(&self, name: &str, initial: T) -> State<T> {
        let key = format!("{}:{}", name, self.scope);
        let value = self
            .store
            .get(&key)
            .unwrap_or_else(|| initial.into_signal_value());
        let id = self.runtime.create(value);
        let signal = WritableSignal::from_id(id, &self.runtime);

        let store = self.store.clone();
        let runtime = self.runtime.clone();
        self.runtime.effect(move || {
            store.set(&key, runtime.get(id));
        });

        State { signal }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pathland_engine::Engine;

    #[test]
    fn state_round_trips_and_isolates_scopes() {
        let engine = Engine::new();
        let store: Rc<dyn StateStore> = Rc::new(InMemoryStateStore::new());
        let persisted = PersistentState::new(store.clone(), "s1", engine.runtime());
        let count = persisted.state("count", 0u32);
        assert_eq!(count.get(), Some(0));
        count.set(5);
        assert_eq!(store.get("count:s1"), Some(SignalValue::U32(5)));

        // A new PersistentState in the same scope loads the persisted value.
        let reloaded = PersistentState::new(store.clone(), "s1", engine.runtime());
        assert_eq!(reloaded.state("count", 0u32).get(), Some(5));

        // A different scope is isolated.
        let other = PersistentState::new(store, "s2", engine.runtime());
        assert_eq!(other.state("count", 0u32).get(), Some(0));
    }

    #[test]
    fn state_update_derives_and_saves() {
        let engine = Engine::new();
        let store: Rc<dyn StateStore> = Rc::new(InMemoryStateStore::new());
        let persisted = PersistentState::new(store.clone(), "s", engine.runtime());
        let count = persisted.state("count", 1u32);
        count.update(|v| v + 10);
        assert_eq!(count.get(), Some(11));
        assert_eq!(store.get("count:s"), Some(SignalValue::U32(11)));
    }
}
