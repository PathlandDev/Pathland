// The C++ Qt Quick shell for `pathland-render-qt`.
//
// Owns the Qt application objects (QGuiApplication / QQuickWindow / QQmlEngine /
// a root Item / the QML-facing Bridge) and the idle tick timer that pumps the
// ring on the Qt main thread. The shell is split so tests can `init` the scene
// headless (offscreen) and drive `apply` without entering the event loop.
//
// M2 skeleton: `apply` is a stub that records what it received (so Rust tests
// prove the FFI batch marshalling end-to-end); the real component->QML mapping
// lands in M3.

#include "pathland_qt.h"

#include <QtGui/QGuiApplication>
#include <QtQuick/QQuickWindow>
#include <QtQuick/QQuickItem>
#include <QtQml/QQmlEngine>
#include <QtQml/QQmlComponent>
#include <QtQml/QQmlContext>
#include <QtCore/QTimer>
#include <QtCore/QUrl>
#include <QtCore/QByteArray>
#include <QtCore/QString>
#include <QtCore/QDebug>

#include "bridge.h"

namespace {

struct LayerState {
    QGuiApplication *app = nullptr;
    QQmlEngine *engine = nullptr;
    QQuickWindow *window = nullptr;
    QQuickItem *root = nullptr; // window contentItem: parent of every top-level node
    Bridge *bridge = nullptr;
    QTimer *tick_timer = nullptr;
    bool initialized = false;

    // Test hooks: record what `apply` received (kept even after M3 mapping).
    uint32_t apply_count = 0;
    uint32_t last_text_len = 0;
    char last_text[4096] = {0};
};

LayerState g;

bool init_app(int argc, char **argv) {
    g.app = new QGuiApplication(argc, argv);
    return g.app != nullptr;
}

[[maybe_unused]] QQuickItem *createComponent(QQmlEngine *engine, QQuickItem *parent, const char *qml) {
    QQmlComponent comp(engine);
    comp.setData(QByteArray(qml), QUrl());
    if (comp.isError()) {
        qWarning() << "component error:" << comp.errorString();
        return nullptr;
    }
    QObject *obj = comp.create();
    if (!obj) {
        qWarning() << "create failed:" << comp.errorString();
        return nullptr;
    }
    QQuickItem *item = qobject_cast<QQuickItem *>(obj);
    if (item && parent) {
        item->setParentItem(parent);
    }
    return item;
}

} // namespace

extern "C" {

int pathland_qt_layer_init(PathlandQtWake wake, PathlandQtTick tick, void *user) {
    if (g.initialized) {
        return 0;
    }
    static int argc = 1;
    static char arg0[] = "pathland-qt";
    static char *argv[] = {arg0, nullptr};
    if (!init_app(argc, argv)) {
        return -1;
    }

    g.bridge = new Bridge(g.app);
    g.bridge->wake = wake;
    g.bridge->user = user;

    g.engine = new QQmlEngine(g.app);
    g.engine->rootContext()->setContextProperty("bridge", g.bridge);

    g.window = new QQuickWindow();
    g.window->setTitle(QString::fromUtf8("Pathland"));
    g.window->resize(420, 220);
    g.root = g.window->contentItem();

    // Idle tick: pump the ring on the Qt main thread between frames, like the
    // GTK renderer's glib idle pump. A zero-millisecond repeating timer runs
    // when the event loop is free.
    g.tick_timer = new QTimer(g.app);
    g.tick_timer->setInterval(0);
    g.tick_timer->setSingleShot(false);
    QObject::connect(g.tick_timer, &QTimer::timeout, g.app, [tick, user]() {
        if (tick) {
            tick(user);
        }
    });

    g.initialized = true;
    return 0;
}

int pathland_qt_layer_run(const char *title, int width, int height,
                          PathlandQtWake wake, PathlandQtTick tick, void *user) {
    if (pathland_qt_layer_init(wake, tick, user) != 0) {
        return -1;
    }
    if (title) {
        g.window->setTitle(QString::fromUtf8(title));
    }
    if (width > 0 && height > 0) {
        g.window->resize(width, height);
    }
    g.tick_timer->start();
    g.window->show();
    const int rc = g.app->exec();
    return rc;
}

void pathland_qt_layer_apply(const PathlandQtCommand *cmds, uint32_t count) {
    if (!g.initialized || !cmds || count == 0) {
        return;
    }
    // M2: stub — record what crossed the boundary (mapping lands in M3).
    for (uint32_t i = 0; i < count; ++i) {
        const PathlandQtCommand &c = cmds[i];
        if (c.kind == PLQT_SET_TEXT && c.str && c.str_len > 0) {
            const uint32_t n = c.str_len < sizeof(g.last_text) - 1 ? c.str_len
                                                                   : sizeof(g.last_text) - 1;
            memcpy(g.last_text, c.str, n);
            g.last_text[n] = '\0';
            g.last_text_len = n;
        }
        // Other kinds are ignored until the real mapping lands (M3).
    }
    g.apply_count += count;
}

void pathland_qt_layer_reset(void) {
    g.apply_count = 0;
    g.last_text_len = 0;
    g.last_text[0] = '\0';
}

void pathland_qt_layer_quit(void) {
    if (g.app) {
        g.app->quit();
    }
}

// Test hooks ---------------------------------------------------------------

uint32_t pathland_qt_layer_apply_count(void) {
    return g.apply_count;
}

uint32_t pathland_qt_layer_last_text(char *out, uint32_t cap) {
    if (!out || cap == 0) {
        return g.last_text_len;
    }
    const uint32_t n = g.last_text_len < cap ? g.last_text_len : cap - 1;
    memcpy(out, g.last_text, n);
    out[n] = '\0';
    return n;
}

void pathland_qt_layer_shutdown(void) {
    delete g.window;
    g.window = nullptr;
    delete g.engine;
    g.engine = nullptr;
    delete g.app;
    g.app = nullptr;
    g.bridge = nullptr;
    g.tick_timer = nullptr;
    g.root = nullptr;
    g.initialized = false;
}

} // extern "C"