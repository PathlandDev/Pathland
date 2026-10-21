//! # pathland-render-core
//!
//! The **toolkit-free core shared by every Pathland renderer** (GTK4, Qt, …).
//!
//! It decodes opcode frames into a retained native-element description
//! ([`RenderTree`] / [`HostNode`]) — tree structure (`TREE`) plus constraint
//! properties (`STYLE`) — and owns the **design-token resolution** contract
//! (spec/TOKENS.md): `SET_DESIGN_TOKEN` overrides, Tier-1 default tables,
//! `DESIGN_TOKEN`-typed property resolution, and re-resolution on color-scheme
//! change.
//!
//! This crate touches **no toolkit** (no GTK, no Qt): it is pure protocol +
//! tokens, so every renderer shares one tested decode path and the renderers
//! stay thin widget-mapping layers. The engine never computes layout and never
//! emits rects; this stores only what a renderer needs to create native
//! elements and let them lay themselves out.

pub mod host;

pub use host::{
    concrete_default_tables, describe, render_tree_from_frame, HostNode, RenderTree,
};