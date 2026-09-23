#include "bridge.h"
#include <QtCore/QString>

namespace {
void fire(Bridge *b, const PathlandQtEvent &ev) {
    // Programmatic echo suppression: the renderer set the value itself.
    if (b->suppress && *b->suppress) {
        return;
    }
    // spec/EVENTS.md guards: only gated nodes report interactions.
    if (b->gated && !b->gated->count(ev.node_id)) {
        return;
    }
    if (b->event_fn) {
        b->event_fn(&ev, b->user);
    }
}
} // namespace

Bridge::Bridge(QObject *parent) : QObject(parent) {}

void Bridge::tap(quint32 nodeId, qreal x, qreal y) {
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_POINTER_UP;
    ev.node_id = nodeId;
    ev.x = static_cast<float>(x);
    ev.y = static_cast<float>(y);
    fire(this, ev);
}

void Bridge::valueChanged(quint32 nodeId, qreal value) {
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_VALUE_CHANGED;
    ev.node_id = nodeId;
    ev.value = static_cast<float>(value);
    fire(this, ev);
}

void Bridge::textChanged(quint32 nodeId, const QString &text) {
    const QByteArray bytes = text.toUtf8();
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_TEXT_CHANGED;
    ev.node_id = nodeId;
    ev.str_len = static_cast<uint32_t>(bytes.size());
    ev.str = bytes.constData();
    fire(this, ev);
}

void Bridge::pointer(quint32 nodeId, quint32 kind, qreal x, qreal y) {
    // Raw pointer streams are gated by the EVENT_LISTENERS mask at attachment
    // time (only the permitted handlers are wired), so they bypass the
    // BINDING_ID gate — any element can report raw inputs (spec/EVENTS.md).
    if (suppress && *suppress) {
        return;
    }
    PathlandQtEvent ev{};
    ev.kind = static_cast<uint8_t>(kind);
    ev.node_id = nodeId;
    ev.x = static_cast<float>(x);
    ev.y = static_cast<float>(y);
    if (event_fn) {
        event_fn(&ev, user);
    }
}

void Bridge::navigateBack() {
    // NAVIGATE is global (never node-keyed), so it is never gated.
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_NAVIGATE_BACK;
    ev.node_id = 0;
    if (event_fn) {
        event_fn(&ev, user);
    }
}

void Bridge::schemeChanged(quint32 scheme) {
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_SCHEME_CHANGED;
    ev.node_id = 0;
    ev.value = static_cast<float>(scheme);
    if (event_fn) {
        event_fn(&ev, user);
    }
}