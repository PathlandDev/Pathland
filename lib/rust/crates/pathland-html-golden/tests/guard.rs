//! Guard: the committed SSR golden fixtures must match the current Rust SSR
//! renderer output. Run with `cargo test -p pathland-html-golden`.
//!
//! When the renderer intentionally changes its output, regenerate the fixtures
//! with `cargo run -p pathland-html-golden -- --emit` and commit them together.

use pathland_render_html::HtmlRenderer;

#[test]
fn ssr_fixtures_are_current() {
    let renderer = HtmlRenderer::new();
    match pathland_html_golden::check(&renderer) {
        Ok(()) => {}
        Err(failures) => {
            let mut msg = String::from("SSR golden fixtures are stale:\n");
            for f in &failures {
                msg.push_str(&format!("  - {f}\n"));
            }
            msg.push_str(
                "Regenerate with `cargo run -p pathland-html-golden -- --emit` from lib/rust.",
            );
            panic!("{msg}");
        }
    }
}