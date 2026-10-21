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
        if Command::new(name)
            .arg("-query")
            .arg("QT_VERSION")
            .output()
            .is_ok()
        {
            return PathBuf::from(name);
        }
    }
    panic!(
        "Qt6 not found: set QMAKE=/path/to/qmake (Homebrew: brew install qtbase qtdeclarative, then QMAKE=$(brew --prefix qtbase)/bin/qmake)"
    );
}

fn qmake_query_opt(qmake: &PathBuf, key: &str) -> Option<String> {
    let out = Command::new(qmake)
        .arg("-query")
        .arg(key)
        .output()
        .ok()?;
    if !out.status.success() {
        return None;
    }
    let value = String::from_utf8(out.stdout).ok()?.trim().to_string();
    (!value.is_empty()).then_some(value)
}

fn qmake_query(qmake: &PathBuf, key: &str) -> String {
    qmake_query_opt(qmake, key)
        .unwrap_or_else(|| panic!("failed to run {qmake:?} -query {key}"))
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
    let mut candidates: Vec<PathBuf> = Vec::new();
    // A bare PATH name (`qmake6`, the Linux CI fallback) has no parent dir to
    // derive the install layout from — hence the qmake queries below.
    if let Some(qt_bin) = qmake.parent().filter(|p| !p.as_os_str().is_empty()) {
        candidates.push(qt_bin.join("moc"));
        candidates.push(qt_bin.join("moc6"));
        if let Some(prefix) = qt_bin.parent() {
            candidates.push(prefix.join("share/qt/libexec/moc"));
        }
    }
    // qmake knows its own install layout: Debian/Ubuntu put moc in
    // `QT_INSTALL_LIBEXECS` (e.g. /usr/lib/qt6/libexec), not next to qmake.
    for key in ["QT_INSTALL_LIBEXECS", "QT_INSTALL_BINS"] {
        if let Some(dir) = qmake_query_opt(qmake, key) {
            let dir = PathBuf::from(dir);
            candidates.push(dir.join("moc"));
            candidates.push(dir.join("moc6"));
        }
    }
    candidates.extend(
        [
            "/opt/homebrew/share/qt/libexec/moc",
            "/usr/local/share/qt/libexec/moc",
            "/usr/lib/qt6/libexec/moc",
            "/usr/lib/x86_64-linux-gnu/qt6/libexec/moc",
            "/usr/lib/aarch64-linux-gnu/qt6/libexec/moc",
        ]
        .map(PathBuf::from),
    );
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
        "moc not found (looked next to qmake, in qmake's QT_INSTALL_LIBEXECS/BINS, and standard Qt libexec paths); set QMAKE to a qtbase qmake"
    );
}

fn main() {
    let qmake = qmake_bin();
    let headers = qmake_query(&qmake, "QT_INSTALL_HEADERS");
    let libs = qmake_query(&qmake, "QT_INSTALL_LIBS");

    // Which Qt frameworks/dylibs do we need? QML controls call into a QObject
    // bridge via context-property invokables, so only Core/Qml/Quick + the
    // moc-generated meta-object code are required (no Controls C++ libs).
    // QtQuickLayouts provides the Layout attached properties (`Layout.fillWidth`
    // etc.) used to stretch FILL children inside RowLayout/ColumnLayout stacks.
    // QtMultimedia provides the QML `MediaPlayer`/`VideoOutput` types the media
    // kit (`VIDEO` surface + `MEDIA_CONTROLLER`, spec/MEDIA.md) instantiates.
    let modules = [
        "QtCore",
        "QtGui",
        "QtQml",
        "QtQuick",
        "QtQuickLayouts",
        "QtMultimedia",
        "QtMultimediaQuick",
    ];

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
        let mut dirs = vec![base.to_string_lossy().into_owned()]; // flat module headers (<QPointer>)
        if let Ok(rd) = std::fs::read_dir(&base) {
            for e in rd.flatten() {
                if e.file_type().map(|t| t.is_dir()).unwrap_or(false) {
                    dirs.push(e.path().to_string_lossy().into_owned());
                    // Versioned module dirs (`<version>/<Module>`): make
                    // unqualified headers (`<QPointer>`) and `<private/...>`
                    // resolve (Qt Quick Layouts' private header pulls both).
                    if let Ok(sub) = std::fs::read_dir(e.path()) {
                        for s in sub.flatten() {
                            if s.file_type().map(|t| t.is_dir()).unwrap_or(false) {
                                dirs.push(s.path().to_string_lossy().into_owned());
                            }
                        }
                    }
                }
            }
        }
        dirs
    };

    let mut build = cc::Build::new();
    build
        .cpp(true)
        .files(["src/qt/qt_layer.cpp", "src/qt/bridge.cpp"])
        .std("c++17");
    build.include(&headers);
    // Linux/plain-layout Qt installs (Debian/Ubuntu): module headers live in
    // `<headers>/<Module>`, and private headers in the versioned
    // `<headers>/<Module>/<version>/<Module>/private/…` tree. Add both so
    // `<QtQuickLayouts/private/qquicklayout_p.h>` (and its nested
    // `<private/qquickitem_p.h>`, `<QPointer>`) resolve. Harmless on macOS,
    // where the framework header dirs below are used instead.
    for m in &modules {
        let module_dir = PathBuf::from(&headers).join(m);
        if !module_dir.is_dir() {
            continue;
        }
        build.include(&module_dir);
        if let Ok(rd) = std::fs::read_dir(&module_dir) {
            for entry in rd.flatten() {
                if !entry.file_type().map(|t| t.is_dir()).unwrap_or(false) {
                    continue;
                }
                let version_dir = entry.path();
                build.include(&version_dir);
                let versioned_module = version_dir.join(m);
                if versioned_module.is_dir() {
                    build.include(&versioned_module);
                }
            }
        }
    }
    build.include("src/qt"); // so `#include "bridge.h"` / "pathland_qt.h" resolve
    if !frameworks.is_empty() {
        // Framework-style includes (`#include <QtGui/QGuiApplication>`) resolve
        // through the framework search path, not the flat -I dirs above.
        build.flag(&format!("-F{libs}"));
    }
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

    // Compile the C++/moc sources BEFORE emitting the Qt link directives so the
    // static `pathland_qt_layer` archive appears *before* the Qt libraries on the
    // linker command line. Linux links with `--as-needed`: Qt libraries listed
    // first would be dropped (nothing references them yet), leaving the archive's
    // Q_OBJECT/meta-type symbols unresolved.
    build.compile("pathland_qt_layer");

    if !frameworks.is_empty() {
        for f in &frameworks {
            println!("cargo:rustc-link-search=native={libs}");
            println!("cargo:rustc-link-lib=framework={f}");
        }
        // Framework search path must reach every consumer (the demo binary
        // links the Qt frameworks transitively), so use the `framework` kind
        // (emits -F and, unlike rustc-link-arg, is transitive).
        println!("cargo:rustc-link-search=framework={libs}");
    } else {
        // Plain shared lib layout (Linux): -L + -lQt6* + rpath.
        println!("cargo:rustc-link-search=native={libs}");
        for m in &modules {
            println!("cargo:rustc-link-lib=dylib={}", m.replace("Qt", "Qt6"));
        }
        println!("cargo:rustc-link-arg=-Wl,-rpath,{libs}");
    }

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
