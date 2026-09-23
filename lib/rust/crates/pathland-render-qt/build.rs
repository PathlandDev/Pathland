//! Build script for `pathland-render-qt`: locate a Qt6 install via
//! `qmake -query`, run moc on the QML-facing bridge, compile the C++ widget
//! layer with `cc`, and hand the linker the Qt frameworks/dylibs.
//!
//! Qt location overrides: `QMAKE` env var (else Homebrew `/opt/homebrew/opt/qtbase/bin/qmake`,
//! else `qmake6`/`qmake` on PATH). Linux (CI) ships qmake at `/usr/bin/qmake6` and
//! moc at `/usr/lib/qt6/libexec/moc`.

use std::env;
use std::path::PathBuf;
use std::process::Command;

fn qmake_bin() -> PathBuf {
    if let Ok(q) = env::var("QMAKE") {
        return PathBuf::from(q);
    }
    for candidate in [
        "/opt/homebrew/opt/qtbase/bin/qmake",
        "/opt/homebrew/opt/qt/bin/qmake",
        "/usr/local/opt/qt/bin/qmake",
    ] {
        if PathBuf::from(candidate).exists() {
            return PathBuf::from(candidate);
        }
    }
    // Fall back to qmake6/qmake on PATH (Linux CI, other installs).
    for name in ["qmake6", "qmake"] {
        if Command::new(name).arg("-query").arg("QT_VERSION").output().is_ok() {
            return PathBuf::from(name);
        }
    }
    panic!(
        "Qt6 not found: set QMAKE=/path/to/qmake (Homebrew: brew install qtbase qtdeclarative, then QMAKE=$(brew --prefix qtbase)/bin/qmake)"
    );
}

fn qmake_query(qmake: &PathBuf, key: &str) -> String {
    let out = Command::new(qmake)
        .arg("-query")
        .arg(key)
        .output()
        .unwrap_or_else(|e| panic!("failed to run {qmake:?} -query {key}: {e}"));
    assert!(out.status.success(), "qmake -query {key} failed");
    String::from_utf8(out.stdout)
        .expect("qmake output not UTF-8")
        .trim()
        .to_string()
}

fn moc_name(header: &str) -> String {
    let base = header
        .rsplit('/')
        .next()
        .unwrap_or(header)
        .strip_suffix(".h")
        .unwrap_or(header);
    format!("moc_{base}")
}

fn find_moc(qmake: &PathBuf) -> PathBuf {
    let qt_bin = qmake.parent().expect("qmake must live in a bin dir");
    let prefix = qt_bin.parent().expect("qmake bin has a prefix");
    let candidates = [
        qt_bin.join("moc"),
        qt_bin.join("moc6"),
        prefix.join("share/qt/libexec/moc"),
        PathBuf::from("/opt/homebrew/share/qt/libexec/moc"),
        PathBuf::from("/usr/local/share/qt/libexec/moc"),
        PathBuf::from("/usr/lib/qt6/libexec/moc"),
        PathBuf::from("/usr/lib/x86_64-linux-gnu/qt6/libexec/moc"),
    ];
    for c in &candidates {
        if c.exists() {
            return c.clone();
        }
    }
    for name in ["moc", "moc6"] {
        if Command::new(name).arg("-V").output().is_ok() {
            return PathBuf::from(name);
        }
    }
    panic!(
        "moc not found (looked next to qmake and in standard Qt libexec paths); set QMAKE to a qtbase qmake"
    );
}

fn main() {
    let qmake = qmake_bin();
    let headers = qmake_query(&qmake, "QT_INSTALL_HEADERS");
    let libs = qmake_query(&qmake, "QT_INSTALL_LIBS");

    // Which Qt frameworks/dylibs do we need? QML controls call into a QObject
    // bridge via context-property invokables, so only Core/Qml/Quick + the
    // moc-generated meta-object code are required (no Controls C++ libs).
    let modules = ["QtCore", "QtGui", "QtQml", "QtQuick"];

    let framework_dir = |m: &str| PathBuf::from(&libs).join(format!("{m}.framework"));
    let frameworks = modules
        .iter()
        .filter(|m| framework_dir(m).exists())
        .cloned()
        .collect::<Vec<_>>();

    // Qt frameworks keep their public headers under a versioned subdir of
    // `<M>.framework/Headers/` (Homebrew layout); add those dirs so
    // `#include <QtQml/QQmlEngine>` resolves.
    let framework_header_dirs = |framework: &str| -> Vec<String> {
        let base = PathBuf::from(&libs).join(format!("{framework}.framework/Headers"));
        let mut dirs = Vec::new();
        if let Ok(rd) = std::fs::read_dir(&base) {
            for e in rd.flatten() {
                if e.file_type().map(|t| t.is_dir()).unwrap_or(false) {
                    dirs.push(e.path().to_string_lossy().into_owned());
                }
            }
        }
        dirs
    };

    let mut build = cc::Build::new();
    build
        .cpp(true)
        .files([
            "src/qt/qt_layer.cpp",
            "src/qt/bridge.cpp",
        ])
        .std("c++17");
    build.include(&headers);
    build.include("src/qt"); // so `#include "bridge.h"` / "pathland_qt.h" resolve
    for f in &frameworks {
        for d in framework_header_dirs(f) {
            build.include(&d);
        }
    }

    // moc the QML-facing bridge so QML can invoke its slot.
    let out_dir = env::var("OUT_DIR").expect("OUT_DIR set by cargo");
    let moc = find_moc(&qmake);
    for h in ["src/qt/bridge.h"] {
        let status = Command::new(&moc)
            .arg(h)
            .arg("-I")
            .arg("src/qt")
            .arg("-o")
            .arg(format!("{out_dir}/{}.cpp", moc_name(h)))
            .status()
            .unwrap_or_else(|e| panic!("failed to run moc on {h}: {e}"));
        assert!(status.success(), "moc on {h} failed");
        build.file(format!("{out_dir}/{}.cpp", moc_name(h)));
    }

    if !frameworks.is_empty() {
        build.flag(&format!("-F{libs}"));
        for f in &frameworks {
            println!("cargo:rustc-link-search=native={libs}");
            println!("cargo:rustc-link-lib=framework={f}");
        }
        // Frameworks are found via -F, not -L.
        println!("cargo:rustc-link-arg=-F{libs}");
    } else {
        // Plain shared lib layout (Linux): -L + -lQt6* + rpath.
        println!("cargo:rustc-link-search=native={libs}");
        for m in &modules {
            println!("cargo:rustc-link-lib=dylib={}", m.replace("Qt", "Qt6"));
        }
        println!("cargo:rustc-link-arg=-Wl,-rpath,{libs}");
    }

    build.compile("pathland_qt_layer");

    println!("cargo:rerun-if-env-changed=QMAKE");
    for f in [
        "src/qt/qt_layer.cpp",
        "src/qt/bridge.cpp",
        "src/qt/bridge.h",
        "src/qt/pathland_qt.h",
    ] {
        println!("cargo:rerun-if-changed={f}");
    }
}