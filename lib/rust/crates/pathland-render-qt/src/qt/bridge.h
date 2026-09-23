// The QML-facing bridge: QML controls call its invokables (e.g. a Button's
// `onClicked: bridge.tap(id, x, y)`), and it forwards a `PathlandQtEvent` to
// the Rust host. The host owns the transport + wire encoding; it only needs to
// know "a raw input happened" — and this bridge carries the minimal payload
// (kind + node id + value/text) so Rust can encode the EVENT opcode.
//
// Event guards mirror spec/EVENTS.md: an interaction is only reported when the
// node carries BINDING_ID / ACTION_ID (the "gated" set) and never while the
// renderer itself is setting a value (programmatic echo suppression).
#ifndef PATHLAND_QT_BRIDGE_H
#define PATHLAND_QT_BRIDGE_H

#include <QtCore/QObject>
#include <cstdint>
#include <unordered_set>
#include "pathland_qt.h"

class Bridge : public QObject {
    Q_OBJECT
public:
    explicit Bridge(QObject *parent = nullptr);
    // C++ -> Rust event routing (assigned by the shell).
    PathlandQtEventFn event_fn = nullptr;
    void *user = nullptr;
    // Shared renderer state (owned by qt_layer.cpp).
    std::unordered_set<uint32_t> *gated = nullptr;
    bool *suppress = nullptr;

public slots:
    // Called from QML: `onClicked: bridge.tap(nodeId, x, y)` etc.
    void tap(quint32 nodeId, qreal x, qreal y);
    void valueChanged(quint32 nodeId, qreal value);
    void textChanged(quint32 nodeId, const QString &text);
    // Raw pointer streams from an EVENT_LISTENERS MouseArea (`kind` is a
    // PLQT_EV_POINTER_* value); NOT gated by BINDING_ID — the listener mask
    // already filtered which handlers were attached.
    void pointer(quint32 nodeId, quint32 kind, qreal x, qreal y);
    // Global native back request (Escape) — never node-keyed.
    void navigateBack();
    // Platform color-scheme changed (value 0 = light, 1 = dark).
    void schemeChanged(quint32 scheme);
};

#endif // PATHLAND_QT_BRIDGE_H