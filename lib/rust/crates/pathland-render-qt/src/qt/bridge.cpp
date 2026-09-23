#include "bridge.h"

Bridge::Bridge(QObject *parent) : QObject(parent) {}

void Bridge::controlEvent(quint32 nodeId) {
    Q_UNUSED(nodeId);
    if (wake) {
        wake(user);
    }
}

void Bridge::valueEvent(quint32 nodeId) {
    Q_UNUSED(nodeId);
    if (wake) {
        wake(user);
    }
}

void Bridge::textEvent(quint32 nodeId, const QString &text) {
    Q_UNUSED(nodeId);
    Q_UNUSED(text);
    if (wake) {
        wake(user);
    }
}