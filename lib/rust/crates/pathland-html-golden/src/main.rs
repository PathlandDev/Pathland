//! CLI for the HTML golden conformance fixtures.
//!
//! `--emit`  (re)generates the committed fixtures under
//!          `lib/typescript/test/fixtures/ssr/` from the canonical Rust SSR
//!          renderer.
//! `--check` verifies the committed fixtures still match the current rendering
//!          (fails with exit code 1 on any drift — used by CI).

use pathland_render_html::HtmlRenderer;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let renderer = HtmlRenderer::new();
    match args.first().map(String::as_str) {
        Some("--emit") => match pathland_html_golden::emit(&renderer) {
            Ok(written) => {
                for p in written {
                    println!("wrote {}", p.display());
                }
            }
            Err(e) => {
                eprintln!("emit failed: {e}");
                std::process::exit(1);
            }
        },
        Some("--check") => match pathland_html_golden::check(&renderer) {
            Ok(()) => println!("golden fixtures are current"),
            Err(failures) => {
                for f in &failures {
                    eprintln!("{f}");
                }
                eprintln!("{} mismatch(es) — run `cargo run -p pathland-html-golden -- --emit`", failures.len());
                std::process::exit(1);
            }
        },
        other => {
            eprintln!("usage: pathland-html-golden --emit | --check (got {other:?})");
            std::process::exit(2);
        }
    }
}