// The QML-facing bridge: QML controls call its invokables (e.g. a Button's
// `onClicked: bridge.controlEvent(7)`), and it forwards to the Rust wake
// callback. This is the C++ widget layer's only surface back to the host —
// the host owns the transport + wire encoding and only needs to know "an
// event happened". See pathland_qt.h for the command/wake types.
#ifndef PATHLAND_QT_BRIDGE_H
#define PATHLAND_QT_BRIDGE_H

#include <QtCore/QObject>
#include "pathland_qt.h"

class Bridge : public QObject {
    Q_OBJECT
public:
    explicit Bridge(QObject *parent = nullptr);
    // Assigned by the shell; invoked when any QML control reports an input.
    PathlandQtWake wake = nullptr;
    void *user = nullptr;

public slots:
    // Called from QML: `onClicked: bridge.controlEvent(nodeId)` etc.
    void controlEvent(quint32 nodeId);
    // Programmatic value/state change echoes from a control (QML -> host).
    void valueEvent(quint32 nodeId);
    void textEvent(quint32 nodeId, const QString &text);
};

#endif // PATHLAND_QT_BRIDGE_H