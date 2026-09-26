//! # pathland-html-golden
//!
//! Cross-renderer golden conformance fixtures for the HTML renderers. A small
//! battery of self-contained `PLPL` batches (opcodes + string section) is
//! rendered by the canonical Rust SSR renderer (`pathland-render-html`) into
//! committed fixtures under `lib/typescript/test/fixtures/ssr/`:
//!
//! * `{scenario}.plpl` — the batch bytes the TypeScript DOM client decodes
//!   with `parseBatch` and applies to a fresh DOM (and, for the delta
//!   scenario, to a hydrated DOM).
//! * `{scenario}.html` — the canonical `render_fragment` output both renderers
//!   must reproduce.
//!
//! The Rust side (`tests/guard.rs`) and the TypeScript side
//! (`lib/typescript/test/ssr-conformance.test.ts`) assert the same contract:
//! the SSR output is the single source of truth, so the two renderers cannot
//! drift without a test failing.

use std::io;
use std::path::{Path, PathBuf};

use pathland_core::{
    Opcode, category, component_type, property_id, style, tree, value_type,
};
use pathland_core_transport::encode_frame;
use pathland_render_html::HtmlRenderer;

/// The committed fixture directory, resolved relative to this crate so the
/// emitted files land in the TypeScript test suite.
pub fn fixture_dir() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../../../typescript/test/fixtures/ssr")
}

/// Enum-coded properties are carried as `F32` bits of the enum value (the
/// renderers decode them via `f32::from_bits`). The codes mirror
/// `lib/typescript/src/constants.ts` / `spec/MODIFIERS.md`.
mod enums {
    pub const ALIGN_CENTER: f32 = 1.0;
    pub const SHAPE_ROUNDED_RECTANGLE: f32 = 2.0;
    pub const TOGGLE_STYLE_SWITCH: f32 = 0.0;
}

/// A batch builder: opcodes plus a shared string arena. All string references
/// in the arena are absolute offsets into `strings`, so a base and a delta
/// batch built from one builder share consistent offsets.
struct Builder {
    opcodes: Vec<Opcode>,
    strings: Vec<u8>,
}

impl Builder {
    fn new() -> Self {
        Self {
            opcodes: Vec::new(),
            strings: Vec::new(),
        }
    }

    /// Append a string to the arena, returning its absolute offset.
    fn string(&mut self, s: &str) -> u32 {
        let offset = self.strings.len() as u32;
        self.strings
            .extend_from_slice(&(s.len() as u32).to_le_bytes());
        self.strings.extend_from_slice(s.as_bytes());
        offset
    }

    fn tree(&mut self, command: u8, a: u32, b: u32, c: u32) {
        self.opcodes
            .push(Opcode::new(category::TREE, command, 0, a, b, c));
    }

    fn style(&mut self, command: u8, a: u32, b: u32, c: u32) {
        self.opcodes
            .push(Opcode::new(category::STYLE, command, 0, a, b, c));
    }

    fn create(&mut self, id: u32, component: u16) {
        self.tree(tree::CREATE_NODE, id, component as u32, 0);
    }

    fn insert(&mut self, parent: u32, child: u32) {
        self.tree(tree::INSERT_CHILD, parent, child, pathland_core::APPEND);
    }

    fn set_text(&mut self, id: u32, text: &str) {
        let offset = self.string(text);
        self.style(style::SET_TEXT, id, offset, 0);
    }

    fn set_prop(&mut self, id: u32, vt: u8, prop: u16, value: u32) {
        self.style(
            style::SET_PROPERTY,
            id,
            ((vt as u32) << 16) | prop as u32,
            value,
        );
    }

    fn set_string(&mut self, id: u32, prop: u16, value: &str) {
        let offset = self.string(value);
        self.set_prop(id, value_type::STRING, prop, offset);
    }

    /// A `DESIGN_TOKEN`-typed property reference: `C` = arena offset of the token path.
    fn set_token_ref(&mut self, id: u32, prop: u16, path: &str) {
        let offset = self.string(path);
        self.set_prop(id, value_type::DESIGN_TOKEN, prop, offset);
    }

    /// A `SET_DESIGN_TOKEN` override: `A` = arena offset of the token path.
    fn set_token(&mut self, path: &str, vt: u8, value: u32) {
        let offset = self.string(path);
        self.style(style::SET_DESIGN_TOKEN, offset, vt as u32, value);
    }
}

/// A rendered snapshot scenario.
struct Scenario {
    name: &'static str,
    opcodes: Vec<Opcode>,
    strings: Vec<u8>,
    root: u32,
}

/// All full-snapshot scenarios.
fn scenarios() -> Vec<Scenario> {
    vec![
        counter(),
        form(),
        composite_controls(),
        layout(),
        tokens(),
        semantics(),
        media(),
    ]
}

fn media() -> Scenario {
    let mut b = Builder::new();
    b.create(1, component_type::VSTACK);
    b.create(2, component_type::IMAGE);
    b.create(3, component_type::VIDEO);
    b.create(4, component_type::AUDIO);
    b.insert(1, 2);
    b.insert(1, 3);
    b.insert(1, 4);
    // Image: absolute asset ref + alt (LABEL) + content-mode Fill → cover + aspect ratio.
    b.set_string(2, property_id::IMAGE_SOURCE, "/_pathland/assets/icons/home.svg");
    b.set_string(2, property_id::LABEL, "Home");
    b.set_prop(2, value_type::F32, property_id::CONTENT_MODE, 1f32.to_bits());
    b.set_prop(2, value_type::F32, property_id::ASPECT_RATIO, 1.5f32.to_bits());
    b.set_prop(2, value_type::F32, property_id::WIDTH, 200f32.to_bits());
    // Video: source ref; playback interaction is renderer-native (controls).
    b.set_string(3, property_id::VIDEO_SOURCE, "https://example.com/sample.mp4");
    b.set_prop(3, value_type::F32, property_id::WIDTH, 320f32.to_bits());
    b.set_prop(3, value_type::F32, property_id::HEIGHT, 180f32.to_bits());
    // Audio: source ref.
    b.set_string(4, property_id::AUDIO_SOURCE, "https://example.com/sample.mp3");
    Scenario {
        name: "media",
        opcodes: b.opcodes,
        strings: b.strings,
        root: 1,
    }
}

fn semantics() -> Scenario {
    use pathland_core::role;
    use pathland_core::text_style;
    let mut b = Builder::new();
    // root nav: a generic VStack retagged to <nav>.
    b.create(1, component_type::VSTACK);
    b.create(2, component_type::TEXT);
    b.create(3, component_type::TEXT);
    b.create(4, component_type::HSTACK);
    b.create(5, component_type::TEXT);
    b.create(6, component_type::TEXT);
    b.create(7, component_type::VSTACK);
    b.create(8, component_type::TEXT);
    b.create(9, component_type::VSTACK);
    b.create(10, component_type::TEXT);
    b.create(11, component_type::VSTACK);
    b.create(12, component_type::TEXT);
    b.create(13, component_type::TEXT);
    b.create(14, component_type::TEXT);
    b.create(15, component_type::TEXT);
    b.create(16, component_type::BUTTON);
    b.insert(1, 2);
    b.insert(1, 3);
    b.insert(1, 4);
    b.insert(4, 5);
    b.insert(4, 6);
    b.insert(1, 7);
    b.insert(7, 8);
    b.insert(1, 9);
    b.insert(9, 10);
    b.insert(1, 11);
    b.insert(11, 12);
    b.insert(1, 13);
    b.insert(1, 14);
    b.insert(1, 15);
    b.insert(1, 16);
    b.set_prop(1, value_type::F32, property_id::ROLE, (role::NAVIGATION as f32).to_bits());
    // A heading role → default <h2>.
    b.set_text(2, "Home");
    b.set_prop(2, value_type::F32, property_id::ROLE, (role::HEADER as f32).to_bits());
    // A heading TEXT_STYLE → <hN> (a heading even without a role).
    b.set_text(3, "Title");
    b.set_prop(
        3,
        value_type::F32,
        property_id::TEXT_STYLE,
        (text_style::TITLE2 as f32).to_bits(),
    );
    // A list container (children stay plain — container tag only).
    b.set_prop(4, value_type::F32, property_id::ROLE, (role::LIST as f32).to_bits());
    b.set_text(5, "Item A");
    b.set_text(6, "Item B");
    // A main landmark with a paragraph child.
    b.set_prop(7, value_type::F32, property_id::ROLE, (role::MAIN as f32).to_bits());
    b.set_text(8, "Body");
    b.set_prop(8, value_type::F32, property_id::ROLE, (role::PARAGRAPH as f32).to_bits());
    // Banner + content info landmarks.
    b.set_prop(9, value_type::F32, property_id::ROLE, (role::BANNER as f32).to_bits());
    b.set_text(10, "Site");
    b.set_prop(11, value_type::F32, property_id::ROLE, (role::CONTENT_INFO as f32).to_bits());
    b.set_text(12, "© Pathland");
    // Plain text with no role/style → <span>.
    b.set_text(13, "Plain");
    // A non-heading TEXT_STYLE → still <span> (not a heading).
    b.set_text(14, "Sub");
    b.set_prop(
        14,
        value_type::F32,
        property_id::TEXT_STYLE,
        (text_style::SUBHEADLINE as f32).to_bits(),
    );
    // A heading TEXT_STYLE + heading role → the typography's level wins.
    b.set_text(15, "Head");
    b.set_prop(
        15,
        value_type::F32,
        property_id::TEXT_STYLE,
        (text_style::LARGE_TITLE as f32).to_bits(),
    );
    b.set_prop(15, value_type::F32, property_id::ROLE, (role::HEADER as f32).to_bits());
    // A control keeps its native element — no role needed (intrinsic).
    b.set_text(16, "Go");
    Scenario {
        name: "semantics",
        opcodes: b.opcodes,
        strings: b.strings,
        root: 1,
    }
}

fn counter() -> Scenario {
    let mut b = Builder::new();
    b.create(1, component_type::VSTACK);
    b.create(2, component_type::TEXT);
    b.create(3, component_type::BUTTON);
    b.create(4, component_type::SPACER);
    b.insert(1, 2);
    b.insert(1, 3);
    b.insert(1, 4);
    b.set_prop(1, value_type::F32, property_id::SPACING, 8f32.to_bits());
    b.set_prop(
        1,
        value_type::F32,
        property_id::ALIGNMENT,
        enums::ALIGN_CENTER.to_bits(),
    );
    b.set_text(2, "Hello Pathland");
    b.set_text(3, "Tap");
    b.set_prop(3, value_type::U32, property_id::EVENT_LISTENERS, 4);
    b.set_prop(3, value_type::U32, property_id::ACTION_ID, 7);
    Scenario {
        name: "counter",
        opcodes: b.opcodes,
        strings: b.strings,
        root: 1,
    }
}

fn form() -> Scenario {
    let mut b = Builder::new();
    b.create(1, component_type::VSTACK);
    b.create(2, component_type::TEXT_FIELD);
    b.create(3, component_type::TEXT_EDITOR);
    b.create(4, component_type::TOGGLE);
    b.create(5, component_type::SLIDER);
    b.create(6, component_type::BUTTON);
    b.insert(1, 2);
    b.insert(1, 3);
    b.insert(1, 4);
    b.insert(1, 5);
    b.insert(1, 6);
    b.set_prop(1, value_type::F32, property_id::SPACING, 12f32.to_bits());
    b.set_prop(
        1,
        value_type::F32,
        property_id::PADDING,
        16f32.to_bits(),
    );
    b.set_string(2, property_id::LABEL, "Name:");
    b.set_string(2, property_id::PROMPT, "Enter name");
    b.set_text(2, "Bob");
    b.set_text(3, "First line\nSecond line");
    b.set_text(4, "Enabled");
    b.set_prop(
        4,
        value_type::F32,
        property_id::TOGGLE_STYLE,
        enums::TOGGLE_STYLE_SWITCH.to_bits(),
    );
    b.set_prop(4, value_type::U8, property_id::SELECTED, 1);
    b.set_text(5, "Volume");
    b.set_prop(5, value_type::F32, property_id::MIN_VALUE, 0f32.to_bits());
    b.set_prop(5, value_type::F32, property_id::MAX_VALUE, 10f32.to_bits());
    b.set_prop(5, value_type::F32, property_id::VALUE, 5f32.to_bits());
    b.set_text(6, "Save");
    Scenario {
        name: "form",
        opcodes: b.opcodes,
        strings: b.strings,
        root: 1,
    }
}

fn composite_controls() -> Scenario {
    let mut b = Builder::new();
    b.create(1, component_type::VSTACK);
    b.create(2, component_type::STEPPER);
    b.create(3, component_type::MENU);
    b.create(4, component_type::TEXT);
    b.create(5, component_type::TEXT);
    b.create(6, component_type::PICKER);
    b.create(7, component_type::TEXT);
    b.create(8, component_type::TEXT);
    b.create(9, component_type::TEXT);
    b.create(10, component_type::GAUGE);
    b.create(11, component_type::PROGRESS_VIEW);
    b.create(12, component_type::PROGRESS_VIEW);
    b.create(13, component_type::DIVIDER);
    b.insert(1, 2);
    b.insert(1, 3);
    b.insert(3, 4);
    b.insert(3, 5);
    b.insert(1, 6);
    b.insert(6, 7);
    b.insert(6, 8);
    b.insert(6, 9);
    b.insert(1, 10);
    b.insert(1, 11);
    b.insert(1, 12);
    b.insert(1, 13);
    b.set_prop(1, value_type::F32, property_id::SPACING, 8f32.to_bits());
    // Stepper composite: bounds + value + step.
    b.set_prop(2, value_type::F32, property_id::MIN_VALUE, 0f32.to_bits());
    b.set_prop(2, value_type::F32, property_id::MAX_VALUE, 10f32.to_bits());
    b.set_prop(2, value_type::F32, property_id::VALUE, 4f32.to_bits());
    b.set_prop(2, value_type::F32, property_id::STEP_VALUE, 2f32.to_bits());
    // Menu composite: trigger label + routed child items.
    b.set_text(3, "Actions");
    b.set_text(4, "Open");
    b.set_text(5, "Close");
    // Picker: options as children, selection index 1.
    b.set_prop(6, value_type::U32, property_id::SELECTION, 1);
    b.set_text(7, "A");
    b.set_text(8, "B");
    b.set_text(9, "C");
    // Gauge: value between bounds.
    b.set_prop(10, value_type::F32, property_id::MIN_VALUE, 0f32.to_bits());
    b.set_prop(10, value_type::F32, property_id::MAX_VALUE, 100f32.to_bits());
    b.set_prop(10, value_type::F32, property_id::VALUE, 42f32.to_bits());
    // Progress: determinate + indeterminate.
    b.set_prop(11, value_type::F32, property_id::PROGRESS, 0.5f32.to_bits());
    b.set_prop(11, value_type::F32, property_id::MAX_VALUE, 1f32.to_bits());
    b.set_prop(
        12,
        value_type::U8,
        property_id::IS_INDETERMINATE,
        1,
    );
    Scenario {
        name: "composite_controls",
        opcodes: b.opcodes,
        strings: b.strings,
        root: 1,
    }
}

fn layout() -> Scenario {
    let mut b = Builder::new();
    b.create(1, component_type::VSTACK);
    b.create(2, component_type::ZSTACK);
    b.create(3, component_type::COLOR);
    b.create(4, component_type::TEXT);
    b.create(5, component_type::SHAPE);
    b.create(6, component_type::GRID);
    b.create(7, component_type::TEXT);
    b.create(8, component_type::SCROLLVIEW);
    b.create(9, component_type::TEXT);
    b.create(10, component_type::HSTACK);
    b.create(11, component_type::TEXT);
    b.create(12, component_type::TEXT);
    b.create(13, component_type::SPACER);
    b.insert(1, 2);
    b.insert(2, 3);
    b.insert(2, 4);
    b.insert(2, 5);
    b.insert(2, 6);
    b.insert(6, 7);
    b.insert(2, 8);
    b.insert(8, 9);
    b.insert(1, 10);
    b.insert(10, 11);
    b.insert(10, 12);
    b.insert(10, 13);
    b.set_prop(1, value_type::F32, property_id::SPACING, 8f32.to_bits());
    // Color (layout-greedy fill).
    b.set_prop(3, value_type::COLOR, property_id::COLOR, 0xFFFF_0000);
    // Text overlay.
    b.set_text(4, "Overlay");
    // Rounded-rect shape: size 100x50, blue fill, 8px radius.
    b.set_prop(
        5,
        value_type::F32,
        property_id::SHAPE_KIND,
        enums::SHAPE_ROUNDED_RECTANGLE.to_bits(),
    );
    b.set_prop(5, value_type::F32, property_id::WIDTH, 100f32.to_bits());
    b.set_prop(5, value_type::F32, property_id::HEIGHT, 50f32.to_bits());
    b.set_prop(5, value_type::COLOR, property_id::COLOR, 0xFF00_00FF);
    b.set_prop(
        5,
        value_type::F32,
        property_id::BORDER_RADIUS,
        8f32.to_bits(),
    );
    // Grid with 2 columns (the GRID's WIDTH property is the cell-axis count).
    b.set_prop(6, value_type::F32, property_id::WIDTH, 2f32.to_bits());
    b.set_text(7, "Cell");
    b.set_text(9, "Scroll");
    // HStack with FILL width / HUG height.
    b.set_prop(
        10,
        value_type::F32,
        property_id::WIDTH,
        pathland_core::size::FILL.to_bits(),
    );
    b.set_prop(
        10,
        value_type::F32,
        property_id::HEIGHT,
        pathland_core::size::HUG_CONTENT.to_bits(),
    );
    b.set_text(11, "L");
    b.set_text(12, "R");
    Scenario {
        name: "layout",
        opcodes: b.opcodes,
        strings: b.strings,
        root: 1,
    }
}

fn tokens() -> Scenario {
    let mut b = Builder::new();
    b.create(1, component_type::VSTACK);
    b.create(2, component_type::TEXT);
    b.create(3, component_type::TEXT);
    b.create(4, component_type::TEXT);
    b.create(5, component_type::BUTTON);
    b.insert(1, 2);
    b.insert(1, 3);
    b.insert(1, 4);
    b.insert(1, 5);
    b.set_prop(1, value_type::F32, property_id::SPACING, 8f32.to_bits());
    // Token property references (resolved to var(--pl-...) at render time).
    b.set_text(2, "Primary");
    b.set_token_ref(2, property_id::COLOR, "color.primary");
    b.set_text(3, "Spaced");
    b.set_token_ref(3, property_id::PADDING, "space.2");
    b.set_text(4, "Body");
    b.set_token_ref(4, property_id::FONT_SIZE, "font.body.size");
    b.set_text(5, "Go");
    // A dark-prefixed reference must resolve to the same --pl-var (dark. stripped).
    b.set_token_ref(5, property_id::BACKGROUND_COLOR, "dark.color.primary");
    // Token overrides (base + dark).
    b.set_token(
        "color.primary",
        value_type::COLOR,
        0xFFFF_0000,
    );
    b.set_token(
        "dark.color.primary",
        value_type::COLOR,
        0xFF00_00FF,
    );
    b.set_token("space.2", value_type::F32, 8f32.to_bits());
    b.set_token("font.body.size", value_type::F32, 16f32.to_bits());
    Scenario {
        name: "tokens",
        opcodes: b.opcodes,
        strings: b.strings,
        root: 1,
    }
}

/// The base → delta pair exercising hydration-then-delta application.
fn delta_scenario() -> (Scenario, Scenario) {
    let mut b = Builder::new();
    // Base snapshot.
    b.create(1, component_type::VSTACK);
    b.create(2, component_type::TEXT);
    b.create(3, component_type::TEXT);
    b.insert(1, 2);
    b.insert(1, 3);
    b.set_prop(1, value_type::F32, property_id::SPACING, 8f32.to_bits());
    b.set_text(2, "One");
    b.set_text(3, "Two");
    let base_len = b.opcodes.len();
    // Follow-up delta.
    b.set_text(2, "Changed");
    // A semantic-role change: the hydrated <span> is retagged to <h2>.
    b.set_prop(
        2,
        value_type::F32,
        property_id::ROLE,
        (pathland_core::role::HEADER as f32).to_bits(),
    );
    b.set_prop(1, value_type::F32, property_id::SPACING, 16f32.to_bits());
    b.create(4, component_type::TEXT);
    b.insert(1, 4);
    b.set_text(4, "Three");

    let base = Scenario {
        name: "delta_base",
        opcodes: b.opcodes[..base_len].to_vec(),
        strings: b.strings.clone(),
        root: 1,
    };
    let change = Scenario {
        name: "delta_change",
        opcodes: b.opcodes[base_len..].to_vec(),
        strings: b.strings.clone(),
        root: 1,
    };
    (base, change)
}

/// The emitted fixture set, keyed by file name (without extension).
pub fn emit(renderer: &HtmlRenderer) -> io::Result<Vec<PathBuf>> {
    let dir = fixture_dir();
    std::fs::create_dir_all(&dir)?;
    let mut written = Vec::new();
    for s in scenarios() {
        let html = renderer.render_fragment(&s.opcodes, &s.strings, s.root);
        written.push(write_fixture(&dir, s.name, &encode_frame(&s.opcodes, &s.strings), &html)?);
    }
    let (base, change) = delta_scenario();
    let mut combined = base.opcodes.clone();
    combined.extend_from_slice(&change.opcodes);
    let base_html = renderer.render_fragment(&base.opcodes, &base.strings, base.root);
    let result_html = renderer.render_fragment(&combined, &base.strings, base.root);
    written.push(write_fixture(
        &dir,
        base.name,
        &encode_frame(&base.opcodes, &base.strings),
        &base_html,
    )?);
    written.push(write_fixture(
        &dir,
        change.name,
        &encode_frame(&change.opcodes, &change.strings),
        // A delta batch carries no standalone HTML (it is applied to a hydrated DOM).
        "",
    )?);
    written.push(write_fixture(
        &dir,
        "delta_result",
        &[],
        &result_html,
    )?);
    Ok(written)
}

fn write_fixture(dir: &Path, name: &str, bytes: &[u8], html: &str) -> io::Result<PathBuf> {
    let bytes_path = dir.join(format!("{name}.plpl"));
    std::fs::write(&bytes_path, bytes)?;
    let html_path = dir.join(format!("{name}.html"));
    std::fs::write(&html_path, html)?;
    Ok(bytes_path)
}

/// Verify every committed fixture matches the current rendering. Returns the
/// mismatched fixture paths on failure.
pub fn check(renderer: &HtmlRenderer) -> Result<(), Vec<String>> {
    let dir = fixture_dir();
    let mut failures = Vec::new();

    let mut render = |name: &str, opcodes: &[Opcode], strings: &[u8], root: u32, html: &str| {
        let current = renderer.render_fragment(opcodes, strings, root);
        let path = dir.join(format!("{name}.html"));
        match std::fs::read_to_string(&path) {
            Ok(committed) if committed == current => {}
            Ok(_) => failures.push(format!(
                "{}: SSR output changed — regenerate with `cargo run -p pathland-html-golden -- --emit`",
                path.display()
            )),
            Err(e) => failures.push(format!("{}: {}", path.display(), e)),
        }
        if !html.is_empty() && current != html {
            failures.push(format!(
                "{}: html arg mismatch for {name}",
                path.display()
            ));
        }
    };

    for s in scenarios() {
        render(
            s.name,
            &s.opcodes,
            &s.strings,
            s.root,
            &renderer.render_fragment(&s.opcodes, &s.strings, s.root),
        );
    }
    let (base, change) = delta_scenario();
    let mut combined = base.opcodes.clone();
    combined.extend_from_slice(&change.opcodes);
    render(
        "delta_base",
        &base.opcodes,
        &base.strings,
        base.root,
        &renderer.render_fragment(&base.opcodes, &base.strings, base.root),
    );
    render(
        "delta_result",
        &combined,
        &base.strings,
        base.root,
        &renderer.render_fragment(&combined, &base.strings, base.root),
    );

    if failures.is_empty() {
        Ok(())
    } else {
        Err(failures)
    }
}

/// The set of fixture names the TypeScript conformance test expects.
pub const FIXTURE_NAMES: &[&str] = &[
    "counter",
    "form",
    "composite_controls",
    "layout",
    "tokens",
    "semantics",
    "media",
    "delta_base",
    "delta_change",
    "delta_result",
];