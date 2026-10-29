//! Window/surface presentation metadata (`META::SET_WINDOW_METADATA`,
//! spec/OPCODE.md §Window metadata).
//!
//! These are the *protocol* fields — presentation metadata for the host
//! window/surface that owns an opcode stream. Each renderer maps them onto its
//! own chrome (browser `<title>`/meta description/`<html lang>`, GTK window
//! title, Qt window title, …). The web mapping is never the definition.

use alloc::string::String;

use crate::constants::window_field;

/// Decoded window/surface metadata.
///
/// A field is `None` when unset (the renderer applies its default). An empty
/// wire string clears the field, so [`Self::set`] stores `None` for `""`.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct WindowMeta {
    /// Window/surface title.
    pub title: Option<String>,
    /// Human-readable description of the current content.
    pub description: Option<String>,
    /// Content locale (BCP 47).
    pub locale: Option<String>,
}

impl WindowMeta {
    /// Create empty metadata (every field unset).
    #[must_use]
    pub fn new() -> Self {
        Self::default()
    }

    /// Set one field from a decoded string. An empty value clears the field.
    pub fn set(&mut self, field: u16, value: &str) {
        let slot = self.slot_mut(field);
        if let Some(slot) = slot {
            *slot = if value.is_empty() {
                None
            } else {
                Some(String::from(value))
            };
        }
    }

    /// Clear one field.
    pub fn clear(&mut self, field: u16) {
        if let Some(slot) = self.slot_mut(field) {
            *slot = None;
        }
    }

    /// The value of `field`, if set.
    #[must_use]
    pub fn get(&self, field: u16) -> Option<&str> {
        self.slot(field).and_then(Option::as_deref)
    }

    /// Whether every field is unset.
    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.title.is_none() && self.description.is_none() && self.locale.is_none()
    }

    fn slot(&self, field: u16) -> Option<&Option<String>> {
        match field {
            window_field::TITLE => Some(&self.title),
            window_field::DESCRIPTION => Some(&self.description),
            window_field::LOCALE => Some(&self.locale),
            _ => None,
        }
    }

    fn slot_mut(&mut self, field: u16) -> Option<&mut Option<String>> {
        match field {
            window_field::TITLE => Some(&mut self.title),
            window_field::DESCRIPTION => Some(&mut self.description),
            window_field::LOCALE => Some(&mut self.locale),
            _ => None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn set_get_clear_and_empty_clears() {
        let mut meta = WindowMeta::new();
        assert!(meta.is_empty());
        meta.set(window_field::TITLE, "Playlist");
        meta.set(window_field::DESCRIPTION, "8 songs");
        meta.set(window_field::LOCALE, "en");
        assert_eq!(meta.title.as_deref(), Some("Playlist"));
        assert_eq!(meta.get(window_field::DESCRIPTION), Some("8 songs"));
        assert_eq!(meta.locale.as_deref(), Some("en"));

        meta.set(window_field::TITLE, "");
        assert_eq!(meta.title, None);
        meta.clear(window_field::LOCALE);
        assert_eq!(meta.locale, None);
        assert!(!meta.is_empty());
    }

    #[test]
    fn unknown_field_is_ignored() {
        let mut meta = WindowMeta::new();
        meta.set(0x00FF, "ignored");
        assert!(meta.is_empty());
        assert_eq!(meta.get(0x00FF), None);
    }
}
