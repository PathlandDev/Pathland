//! Window/surface presentation metadata (`META::SET_WINDOW_METADATA`).
//!
//! The app binds a field to a signal; [`Engine`](crate::Engine) emits only the
//! changed field on a signal write (and the initial values at mount). A route
//! can therefore drive the window title from a computed over its router signal
//! without any renderer-specific API.

use crate::signal::{IntoSignalId, SignalId};

/// A builder for the engine's window-metadata signal bindings.
///
/// Unset fields are left to the renderer default. The Java DSL has the
/// equivalent root `windowMetadata(...)` modifier.
#[derive(Debug, Clone, Copy, Default)]
pub struct WindowMetadata {
    pub(crate) title: Option<SignalId>,
    pub(crate) description: Option<SignalId>,
    pub(crate) locale: Option<SignalId>,
}

impl WindowMetadata {
    /// Empty metadata (every field unset).
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Bind the window/surface title.
    #[must_use]
    pub fn title(mut self, signal: impl IntoSignalId) -> Self {
        self.title = Some(signal.into_signal_id());
        self
    }

    /// Bind the content description.
    #[must_use]
    pub fn description(mut self, signal: impl IntoSignalId) -> Self {
        self.description = Some(signal.into_signal_id());
        self
    }

    /// Bind the content locale (BCP 47).
    #[must_use]
    pub fn locale(mut self, signal: impl IntoSignalId) -> Self {
        self.locale = Some(signal.into_signal_id());
        self
    }

    pub(crate) fn signal(&self, field: u16) -> Option<SignalId> {
        match field {
            pathland_core::window_field::TITLE => self.title,
            pathland_core::window_field::DESCRIPTION => self.description,
            pathland_core::window_field::LOCALE => self.locale,
            _ => None,
        }
    }

    pub(crate) fn set_signal(&mut self, field: u16, signal: SignalId) {
        match field {
            pathland_core::window_field::TITLE => self.title = Some(signal),
            pathland_core::window_field::DESCRIPTION => self.description = Some(signal),
            pathland_core::window_field::LOCALE => self.locale = Some(signal),
            _ => {}
        }
    }
}
