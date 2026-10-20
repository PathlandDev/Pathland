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

namespace {
// A media report targets a controller and is reported by component type
// (spec/EVENTS.md), so it bypasses the BINDING_ID gate; programmatic sources
// are still suppressed.
void fireMedia(Bridge *b, PathlandQtEvent &ev) {
    if (b->suppress && *b->suppress) {
        return;
    }
    if (b->event_fn) {
        b->event_fn(&ev, b->user);
    }
}
} // namespace

void Bridge::mediaPlayState(quint32 nodeId, bool playing) {
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_MEDIA_PLAY_STATE;
    ev.node_id = nodeId;
    ev.value = playing ? 1.0f : 0.0f;
    fireMedia(this, ev);
}

void Bridge::mediaTime(quint32 nodeId, qreal seconds) {
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_MEDIA_TIME;
    ev.node_id = nodeId;
    ev.value = static_cast<float>(seconds);
    fireMedia(this, ev);
}

void Bridge::mediaEnded(quint32 nodeId) {
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_MEDIA_ENDED;
    ev.node_id = nodeId;
    fireMedia(this, ev);
}

void Bridge::mediaVolume(quint32 nodeId, qreal volume) {
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_MEDIA_VOLUME;
    ev.node_id = nodeId;
    ev.value = static_cast<float>(volume);
    fireMedia(this, ev);
}

void Bridge::mediaDuration(quint32 nodeId, qreal seconds) {
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_MEDIA_DURATION;
    ev.node_id = nodeId;
    ev.value = static_cast<float>(seconds);
    fireMedia(this, ev);
}

void Bridge::fitChanged(quint32 nodeId, int index) {
    // A fit report targets the slot (by component type), not gated by BINDING_ID.
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_FIT_CHANGED;
    ev.node_id = nodeId;
    ev.value = static_cast<float>(index);
    fireMedia(this, ev);
}

namespace {
bool wants(Bridge *b, quint32 id, uint32_t bit) {
    if (!b->listeners) {
        return false;
    }
    auto it = b->listeners->find(id);
    return it != b->listeners->end() && (it->second & bit) != 0;
}
} // namespace

void Bridge::hover(quint32 nodeId, bool entering) {
    // The listener MouseArea (and its enter/exit handlers) is only attached when
    // POINTER_MOVE is requested, so no extra gate here.
    if (suppress && *suppress) {
        return;
    }
    PathlandQtEvent ev{};
    ev.kind = entering ? PLQT_EV_POINTER_ENTER : PLQT_EV_POINTER_LEAVE;
    ev.node_id = nodeId;
    if (event_fn) {
        event_fn(&ev, user);
    }
}

void Bridge::key(quint32 nodeId, quint32 keyCode, quint32 modifiers, bool repeat, bool down) {
    const uint32_t bit = down ? PL_LISTENER_KEY_DOWN : PL_LISTENER_KEY_UP;
    if (!wants(this, nodeId, bit)) {
        return;
    }
    if (suppress && *suppress) {
        return;
    }
    PathlandQtEvent ev{};
    ev.kind = down ? PLQT_EV_KEY_DOWN : PLQT_EV_KEY_UP;
    ev.node_id = nodeId;
    ev.key_code = static_cast<uint16_t>(keyCode & 0xFFFF);
    ev.modifiers = static_cast<uint8_t>(modifiers & 0xFF);
    ev.repeat = repeat ? 1 : 0;
    if (event_fn) {
        event_fn(&ev, user);
    }
}

void Bridge::focusChanged(quint32 nodeId, bool focused) {
    if (!wants(this, nodeId, PL_LISTENER_FOCUS)) {
        return;
    }
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_FOCUS;
    ev.node_id = nodeId;
    ev.value = focused ? 1.0f : 0.0f;
    if (event_fn) {
        event_fn(&ev, user);
    }
}

void Bridge::editingChanged(quint32 nodeId, bool editing) {
    if (!wants(this, nodeId, PL_LISTENER_EDITING)) {
        return;
    }
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_EDITING;
    ev.node_id = nodeId;
    ev.value = editing ? 1.0f : 0.0f;
    if (event_fn) {
        event_fn(&ev, user);
    }
}

void Bridge::submit(quint32 nodeId) {
    if (!wants(this, nodeId, PL_LISTENER_SUBMIT)) {
        return;
    }
    PathlandQtEvent ev{};
    ev.kind = PLQT_EV_SUBMIT;
    ev.node_id = nodeId;
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