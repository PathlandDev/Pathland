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