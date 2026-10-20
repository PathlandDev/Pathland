//! The app-side media controller state object (spec/MEDIA.md).
//!
//! A media playback is a **state object**, not a view (the SwiftUI `AVPlayer`
//! analog). The controller owns a resource and its transport as
//! [signals](crate::signal): the *commands* (`playing`, `position`, `volume`,
//! `muted`, `looping`, `rate`) the app/user writes, and the *reports* (`time`,
//! `duration`, `volume_report`) the renderer's `MEDIA_*` events feed.
//!
//! The view DSL materializes **one** non-visual `MEDIA_CONTROLLER` node per
//! controller (via `attach()`), so playback identity is stable across structural
//! swaps — a `SIZE_THAT_FITS` band change never restarts playback, and any number
//! of surfaces/control views referencing the same controller drive the same
//! playback. **Audio has no view**: an audio playback is a controller plus
//! ordinary views (buttons/sliders) bound to its transport signals.

use alloc::rc::Rc;
use alloc::string::String;
use core::cell::{Cell, RefCell};

use crate::signal::{IntoSignalId, Runtime, Signal, SignalValue, WritableSignal};

/// A media playback controller.
///
/// Build it (creating its signals), rebind the transport slots to the app's own
/// signals, then share it (`Rc`) and reference it from surfaces/controls:
///
/// ```
/// use std::rc::Rc;
/// use pathland_engine::{Engine, MediaController};
///
/// let engine = Engine::new();
/// let rt = engine.runtime();
/// let player = Rc::new(
///     MediaController::with_source(&rt, "/assets/track1.mp3")
///         .on_ended(|| { /* next track */ }),
/// );
/// player.play();
/// ```
pub struct MediaController {
    /// The reactive runtime the signals live on.
    runtime: Runtime,
    /// The resource: a static URL/asset, or a reactive binding (current track).
    source: Signal<String>,
    /// Play/pause command (`PLAYBACK_STATE`).
    playing: WritableSignal<bool>,
    /// Seek command (`MEDIA_POSITION`, seconds).
    position: WritableSignal<f32>,
    /// Volume command (`MEDIA_VOLUME`, 0..1).
    volume: WritableSignal<f32>,
    /// Mute command (`MEDIA_MUTED`), independent of `volume`.
    muted: WritableSignal<bool>,
    /// Loop-on-end command (`MEDIA_LOOP`).
    looping: WritableSignal<bool>,
    /// Playback rate command (`MEDIA_RATE`, 1.0 = normal).
    rate: WritableSignal<f32>,
    /// Position report feed (`MEDIA_TIME_UPDATED`).
    time: WritableSignal<f32>,
    /// Duration report feed (`MEDIA_DURATION_CHANGED`) — a seek control's max.
    duration: WritableSignal<f32>,
    /// Volume report feed (`MEDIA_VOLUME_CHANGED`) — bind a display to it.
    volume_report: WritableSignal<f32>,
    /// The end-of-media hook (`MEDIA_ENDED`).
    on_ended: RefCell<Option<Rc<dyn Fn()>>>,
    /// The node id this controller's `MEDIA_CONTROLLER` node is pinned to
    /// (`0` until the first [`assign_ids`](crate::assign_ids) pass).
    node_id: Cell<u32>,
}

impl MediaController {
    /// A controller with no resource (idle until `source` is set).
    pub fn new(runtime: &Runtime) -> Self {
        Self {
            runtime: runtime.clone(),
            source: Signal::from_id(runtime.create(SignalValue::Str(String::new())), runtime),
            playing: runtime.signal(false),
            position: runtime.signal(0.0f32),
            volume: runtime.signal(1.0f32),
            muted: runtime.signal(false),
            looping: runtime.signal(false),
            rate: runtime.signal(1.0f32),
            time: runtime.signal(0.0f32),
            duration: runtime.signal(0.0f32),
            volume_report: runtime.signal(1.0f32),
            on_ended: RefCell::new(None),
            node_id: Cell::new(0),
        }
    }

    /// A controller for a static resource (resource name, file path, or URL).
    pub fn with_source(runtime: &Runtime, source: impl Into<String>) -> Self {
        Self::new(runtime).source(source)
    }

    /// Set a static resource (resource name, file path, or URL).
    pub fn source(mut self, source: impl Into<String>) -> Self {
        let id = self
            .runtime
            .create(SignalValue::Str(source.into()));
        self.source = Signal::from_id(id, &self.runtime);
        self
    }

    /// Bind the resource to a reactive signal (e.g. the current track).
    pub fn bind_source(mut self, source: impl IntoSignalId) -> Self {
        self.source = Signal::from_id(source.into_signal_id(), &self.runtime);
        self
    }

    /// Advance (e.g. to the next track) when the media ends (`MEDIA_ENDED`).
    pub fn on_ended(self, on_ended: impl Fn() + 'static) -> Self {
        *self.on_ended.borrow_mut() = Some(Rc::new(on_ended));
        self
    }

    // ── Rebind a transport slot to an app-owned (e.g. persisted) signal ─────

    /// Use `signal` as the play/pause command.
    pub fn playing(mut self, signal: impl IntoSignalId) -> Self {
        self.playing = WritableSignal::from_id(signal.into_signal_id(), &self.runtime);
        self
    }

    /// Use `signal` as the seek command.
    pub fn position(mut self, signal: impl IntoSignalId) -> Self {
        self.position = WritableSignal::from_id(signal.into_signal_id(), &self.runtime);
        self
    }

    /// Use `signal` as the volume command.
    pub fn volume(mut self, signal: impl IntoSignalId) -> Self {
        self.volume = WritableSignal::from_id(signal.into_signal_id(), &self.runtime);
        self
    }

    /// Use `signal` as the mute command.
    pub fn muted(mut self, signal: impl IntoSignalId) -> Self {
        self.muted = WritableSignal::from_id(signal.into_signal_id(), &self.runtime);
        self
    }

    /// Use `signal` as the loop command.
    pub fn looping(mut self, signal: impl IntoSignalId) -> Self {
        self.looping = WritableSignal::from_id(signal.into_signal_id(), &self.runtime);
        self
    }

    /// Use `signal` as the rate command.
    pub fn rate(mut self, signal: impl IntoSignalId) -> Self {
        self.rate = WritableSignal::from_id(signal.into_signal_id(), &self.runtime);
        self
    }

    /// Use `signal` as the position report feed.
    pub fn time(mut self, signal: impl IntoSignalId) -> Self {
        self.time = WritableSignal::from_id(signal.into_signal_id(), &self.runtime);
        self
    }

    /// Use `signal` as the duration report feed.
    pub fn duration(mut self, signal: impl IntoSignalId) -> Self {
        self.duration = WritableSignal::from_id(signal.into_signal_id(), &self.runtime);
        self
    }

    /// Use `signal` as the volume report feed.
    pub fn volume_report(mut self, signal: impl IntoSignalId) -> Self {
        self.volume_report = WritableSignal::from_id(signal.into_signal_id(), &self.runtime);
        self
    }

    // ── Convenience transport (thin wrappers over the command signals) ──────

    /// Start playback (`PLAYBACK_STATE` = 1).
    pub fn play(&self) {
        self.playing.set(true);
    }

    /// Pause playback (`PLAYBACK_STATE` = 0).
    pub fn pause(&self) {
        self.playing.set(false);
    }

    /// Toggle play/pause (a transport button's action).
    pub fn toggle(&self) {
        self.playing.set(!self.playing.get().unwrap_or(false));
    }

    /// Seek to `seconds` (a change to `MEDIA_POSITION`).
    pub fn seek(&self, seconds: f32) {
        self.position.set(seconds);
    }

    /// Set the volume 0..1 (a change to `MEDIA_VOLUME`).
    pub fn set_volume(&self, value: f32) {
        self.volume.set(value.clamp(0.0, 1.0));
    }

    /// Mute/unmute (independent of [`volume`](Self::volume)).
    pub fn set_muted(&self, value: bool) {
        self.muted.set(value);
    }

    /// Loop the resource on end.
    pub fn set_looping(&self, value: bool) {
        self.looping.set(value);
    }

    /// Set the playback rate (1.0 = normal).
    pub fn set_rate(&self, value: f32) {
        self.rate.set(value);
    }

    // ── Signals (read by the emitter; the app reads the reports) ────────────

    /// The bound resource signal.
    pub fn source_signal(&self) -> Signal<String> {
        self.source.clone()
    }

    /// The play/pause command.
    pub fn playing_signal(&self) -> WritableSignal<bool> {
        self.playing.clone()
    }

    /// The seek command (display progress with [`time_signal`](Self::time_signal)).
    pub fn position_signal(&self) -> WritableSignal<f32> {
        self.position.clone()
    }

    /// The volume command (display the level with
    /// [`volume_report_signal`](Self::volume_report_signal)).
    pub fn volume_signal(&self) -> WritableSignal<f32> {
        self.volume.clone()
    }

    /// The mute command.
    pub fn muted_signal(&self) -> WritableSignal<bool> {
        self.muted.clone()
    }

    /// The loop command.
    pub fn looping_signal(&self) -> WritableSignal<bool> {
        self.looping.clone()
    }

    /// The rate command.
    pub fn rate_signal(&self) -> WritableSignal<f32> {
        self.rate.clone()
    }

    /// The current playback position feed — bind a display to it.
    pub fn time_signal(&self) -> WritableSignal<f32> {
        self.time.clone()
    }

    /// The media duration feed — a seek control's maximum.
    pub fn duration_signal(&self) -> WritableSignal<f32> {
        self.duration.clone()
    }

    /// The volume feed — bind a display to it.
    pub fn volume_report_signal(&self) -> WritableSignal<f32> {
        self.volume_report.clone()
    }

    /// The end-of-media hook.
    pub fn on_ended_handler(&self) -> Option<Rc<dyn Fn()>> {
        self.on_ended.borrow().clone()
    }

    // ── Emitter-internal: the controller's pinned node id ───────────────────

    /// The node id this controller's node is pinned to (`0` = unassigned).
    pub(crate) fn pinned_node_id(&self) -> u32 {
        self.node_id.get()
    }

    /// Pin this controller's node id (called once, by `assign_ids`).
    pub(crate) fn pin_node_id(&self, id: u32) {
        self.node_id.set(id);
    }
}

impl core::fmt::Debug for MediaController {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("MediaController")
            .field("node_id", &self.node_id.get())
            .finish()
    }
}

/// A shared handle to a [`MediaController`], **identity-compared** (two handles
/// compare equal iff they reference the same controller). This is what a
/// retained [`Node`](crate::Node) carries when a view references a controller.
#[derive(Clone)]
pub struct MediaControllerRef(pub Rc<MediaController>);

impl MediaControllerRef {
    /// Wrap a controller handle.
    pub fn new(controller: Rc<MediaController>) -> Self {
        Self(controller)
    }
}

impl PartialEq for MediaControllerRef {
    fn eq(&self, other: &Self) -> bool {
        Rc::ptr_eq(&self.0, &other.0)
    }
}

impl core::fmt::Debug for MediaControllerRef {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("MediaControllerRef").finish()
    }
}
