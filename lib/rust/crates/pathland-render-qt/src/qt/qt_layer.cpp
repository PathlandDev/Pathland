// The C++ Qt Quick shell + widget mapping for `pathland-render-qt`.
//
// Owns the Qt application objects (QGuiApplication / QQuickWindow / QQmlEngine /
// a root Item / the QML-facing Bridge) and applies the Rust delta command batch
// to QML items: create/delete/insert/remove widgets, set text + properties, and
// report control inputs back through the Bridge. The shell is split so tests
// can `init` the scene headless (offscreen) and drive `apply` without entering
// the event loop.
//
// Contract: the renderer never drains events — Rust encodes what the Bridge
// reports and wakes the host. Programmatic value sets suppress echoes, and only
// BINDING_ID/ACTION_ID-gated nodes report interactions (spec/EVENTS.md).

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
#include <QtGui/QColor>
#include <QtCore/QDebug>
#include <QtCore/QVariant>

#include <cmath>
#include <cstring>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "bridge.h"

// Component type IDs (spec/PRIMITIVES.md).
enum {
    PL_COMP_TEXT = 0x01,
    PL_COMP_IMAGE = 0x02,
    PL_COMP_COLOR = 0x03,
    PL_COMP_SHAPE = 0x04,
    PL_COMP_DIVIDER = 0x05,
    PL_COMP_SPACER = 0x06,
    PL_COMP_PROGRESS = 0x07,
    PL_COMP_GAUGE = 0x08,
    PL_COMP_VSTACK = 0x10,
    PL_COMP_HSTACK = 0x11,
    PL_COMP_ZSTACK = 0x12,
    PL_COMP_GRID = 0x13,
    PL_COMP_SCROLL = 0x14,
    PL_COMP_LAZY_VGRID = 0x15,
    PL_COMP_LAZY_HGRID = 0x16,
    PL_COMP_LAZY_VSTACK = 0x1B,
    PL_COMP_LAZY_HSTACK = 0x1C,
    PL_COMP_BUTTON = 0x20,
    PL_COMP_TEXT_FIELD = 0x21,
    PL_COMP_TEXT_EDITOR = 0x22,
    PL_COMP_TOGGLE = 0x24,
    PL_COMP_SLIDER = 0x25,
    PL_COMP_COMMENT = 0x7F,
};

// Property IDs (spec/MODIFIERS.md).
enum {
    PL_PROP_SPACING = 0x0001,
    PL_PROP_ALIGNMENT = 0x0002,
    PL_PROP_TEXT_ALIGNMENT = 0x000C,
    PL_PROP_BACKGROUND = 0x1001,
    PL_PROP_IMAGE_SOURCE = 0x1002,
    PL_PROP_BORDER_WIDTH = 0x1003,
    PL_PROP_BORDER_RADIUS = 0x1005,
    PL_PROP_FONT_SIZE = 0x1007,
    PL_PROP_FONT_WEIGHT = 0x1008,
    PL_PROP_FONT_FAMILY = 0x1009,
    PL_PROP_COLOR = 0x100A,
    PL_PROP_WIDTH = 0x100B,
    PL_PROP_HEIGHT = 0x100C,
    PL_PROP_OPACITY = 0x100D,
    PL_PROP_VISIBLE = 0x100E,
    PL_PROP_Z_INDEX = 0x100F,
    PL_PROP_PADDING = 0x1011,
    PL_PROP_PADDING_TOP = 0x1012,
    PL_PROP_PADDING_RIGHT = 0x1013,
    PL_PROP_PADDING_BOTTOM = 0x1014,
    PL_PROP_PADDING_LEFT = 0x1015,
    PL_PROP_VALUE = 0x2006,
    PL_PROP_MIN = 0x2007,
    PL_PROP_MAX = 0x2008,
    PL_PROP_STEP = 0x2009,
    PL_PROP_LABEL = 0x200A,
    PL_PROP_PROMPT = 0x200B,
    PL_PROP_SELECTED = 0x2004,
    PL_PROP_ENABLED = 0x2003,
    PL_PROP_IS_SECURE = 0x200D,
    PL_PROP_ACTION_ID = 0x2016,
    PL_PROP_BINDING_ID = 0x2017,
};

namespace {

float f32(uint32_t bits) {
    float f;
    std::memcpy(&f, &bits, sizeof(f));
    return f;
}

struct LayerState {
    QGuiApplication *app = nullptr;
    QQmlEngine *engine = nullptr;
    QQuickWindow *window = nullptr;
    QQuickItem *root = nullptr; // window contentItem: parent of every top-level node
    Bridge *bridge = nullptr;
    QTimer *tick_timer = nullptr;
    bool initialized = false;

    // The renderer's rendered-output cache: id -> QML item.
    std::unordered_map<uint32_t, QQuickItem *> widgets;
    // Nodes that may report interactions (carry BINDING_ID / ACTION_ID).
    std::unordered_set<uint32_t> gated_ids;
    // While the renderer sets a value, control signals must not echo.
    bool suppress = false;

    // Test hooks: record what `apply` received.
    uint32_t apply_count = 0;
    uint32_t last_text_len = 0;
    char last_text[4096] = {0};
};

LayerState g;

bool init_app(int argc, char **argv) {
    g.app = new QGuiApplication(argc, argv);
    return g.app != nullptr;
}

QQuickItem *createComponent(QQmlEngine *engine, QQuickItem *parent, const char *qml) {
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

// Build the QML snippet for a component type (node id embedded for controls so
// their signal handlers can report it).
QString qmlFor(uint16_t component, uint32_t id) {
    switch (component) {
    case PL_COMP_VSTACK:
    case PL_COMP_LAZY_VSTACK:
        return QStringLiteral("import QtQuick 2.15\nColumn { objectName: \"pl%1\" }").arg(id);
    case PL_COMP_HSTACK:
    case PL_COMP_LAZY_HSTACK:
        return QStringLiteral("import QtQuick 2.15\nRow { objectName: \"pl%1\" }").arg(id);
    case PL_COMP_ZSTACK:
        return QStringLiteral("import QtQuick 2.15\nItem { objectName: \"pl%1\" }").arg(id);
    case PL_COMP_GRID:
    case PL_COMP_LAZY_VGRID:
    case PL_COMP_LAZY_HGRID:
        return QStringLiteral("import QtQuick 2.15\nGrid { objectName: \"pl%1\" }").arg(id);
    case PL_COMP_SCROLL:
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "ScrollView { objectName: \"pl%1\"; clip: true }")
            .arg(id);
    case PL_COMP_TEXT:
        return QStringLiteral("import QtQuick 2.15\nText { objectName: \"pl%1\" }").arg(id);
    case PL_COMP_SPACER:
        return QStringLiteral("import QtQuick 2.15\nItem { objectName: \"pl%1\" }").arg(id);
    case PL_COMP_DIVIDER:
        return QStringLiteral(
                   "import QtQuick 2.15\nRectangle { objectName: \"pl%1\"; width: 1; height: 1; "
                   "color: \"#E5E7EB\" }")
            .arg(id);
    case PL_COMP_COLOR:
    case PL_COMP_SHAPE:
        return QStringLiteral("import QtQuick 2.15\nRectangle { objectName: \"pl%1\" }").arg(id);
    case PL_COMP_IMAGE:
        return QStringLiteral(
                   "import QtQuick 2.15\nImage { objectName: \"pl%1\"; "
                   "fillMode: Image.PreserveAspectFit }")
            .arg(id);
    case PL_COMP_BUTTON:
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "Button { objectName: \"pl%1\"; onClicked: bridge.tap(%2, width / 2, height / 2) }")
            .arg(id)
            .arg(id);
    case PL_COMP_TEXT_FIELD:
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "TextField { objectName: \"pl%1\"; "
                   "onTextChanged: bridge.textChanged(%2, text) }")
            .arg(id)
            .arg(id);
    case PL_COMP_SLIDER:
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "Slider { objectName: \"pl%1\"; "
                   "onValueChanged: bridge.valueChanged(%2, value) }")
            .arg(id)
            .arg(id);
    case PL_COMP_TOGGLE:
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "Switch { objectName: \"pl%1\"; "
                   "onToggled: bridge.valueChanged(%2, checked ? 1 : 0) }")
            .arg(id)
            .arg(id);
    default:
        return QStringLiteral("import QtQuick 2.15\nItem { objectName: \"pl%1\" }").arg(id);
    }
}

QQuickItem *findWidget(uint32_t id) {
    if (id == 0) {
        return g.root;
    }
    auto it = g.widgets.find(id);
    return it == g.widgets.end() ? nullptr : it->second;
}

// Pathland cross-axis alignment (0=Start,1=Center,2=End,else Fill) -> Qt's
// Row/Column `align` (Qt 6.7+, horizontal flags for a Column).
int alignFor(uint32_t raw) {
    const float f = f32(raw);
    const int idx = (std::isfinite(f) && std::floor(f) == f && f >= 0.0f && f <= 3.0f)
                        ? static_cast<int>(f)
                        : static_cast<int>(raw & 0xFF);
    switch (idx) {
    case 0:
        return Qt::AlignLeft; // Start
    case 1:
        return Qt::AlignHCenter; // Center
    case 2:
        return Qt::AlignRight; // End
    default:
        return Qt::AlignLeft; // Fill -> stretch is the QML default; gap
    }
}

int weightFor(float weight) {
    const int w = static_cast<int>(weight + 0.5f);
    switch (w) {
    case 100:
        return int(QFont::Thin);
    case 200:
        return int(QFont::ExtraLight);
    case 300:
        return int(QFont::Light);
    case 500:
        return int(QFont::Medium);
    case 600:
        return int(QFont::DemiBold);
    case 700:
        return int(QFont::Bold);
    case 800:
        return int(QFont::ExtraBold);
    case 900:
        return int(QFont::Black);
    default:
        return int(QFont::Normal);
    }
}

// WIDTH/HEIGHT special values: -1 = FILL (anchors.fill parent), -2 = HUG
// (implicit size). Positive values set an explicit size.
void applySize(QQuickItem *item, const char *dimension, float value) {
    if (value == -1.0f) {
        QObject *anchors = item->property("anchors").value<QObject *>();
        if (anchors && item->parentItem()) {
            anchors->setProperty(dimension, QVariant::fromValue(item->parentItem()));
        }
        return;
    }
    if (value == -2.0f) {
        return; // HUG_CONTENT: implicit size is the default
    }
    item->setProperty(dimension, static_cast<double>(value));
}

void applyProperty(uint32_t id, uint16_t prop, uint8_t value_type, uint32_t value) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return;
    }
    switch (prop) {
    case PL_PROP_SPACING:
        item->setProperty("spacing", static_cast<double>(f32(value)));
        break;
    case PL_PROP_ALIGNMENT:
        item->setProperty("align", alignFor(value));
        break;
    case PL_PROP_PADDING:
        item->setProperty("padding", static_cast<double>(f32(value)));
        break;
    case PL_PROP_PADDING_TOP:
        item->setProperty("topPadding", static_cast<double>(f32(value)));
        break;
    case PL_PROP_PADDING_RIGHT:
        item->setProperty("rightPadding", static_cast<double>(f32(value)));
        break;
    case PL_PROP_PADDING_BOTTOM:
        item->setProperty("bottomPadding", static_cast<double>(f32(value)));
        break;
    case PL_PROP_PADDING_LEFT:
        item->setProperty("leftPadding", static_cast<double>(f32(value)));
        break;
    case PL_PROP_WIDTH:
        applySize(item, "width", f32(value));
        break;
    case PL_PROP_HEIGHT:
        applySize(item, "height", f32(value));
        break;
    case PL_PROP_COLOR:
        item->setProperty("color", QColor::fromRgba(value));
        break;
    case PL_PROP_FONT_SIZE:
        item->setProperty("font.pixelSize", static_cast<double>(f32(value)));
        break;
    case PL_PROP_FONT_WEIGHT:
        item->setProperty("font.weight", weightFor(f32(value)));
        break;
    case PL_PROP_OPACITY:
        item->setProperty("opacity", static_cast<double>(f32(value)));
        break;
    case PL_PROP_VISIBLE:
        item->setProperty("visible", value != 0);
        break;
    case PL_PROP_Z_INDEX:
        item->setProperty("z", static_cast<double>(f32(value)));
        break;
    case PL_PROP_VALUE:
        item->setProperty("value", static_cast<double>(f32(value)));
        break;
    case PL_PROP_MIN:
        item->setProperty("from", static_cast<double>(f32(value)));
        break;
    case PL_PROP_MAX:
        item->setProperty("to", static_cast<double>(f32(value)));
        break;
    case PL_PROP_STEP:
        item->setProperty("stepSize", static_cast<double>(f32(value)));
        break;
    case PL_PROP_SELECTED:
        item->setProperty("checked", value != 0);
        break;
    case PL_PROP_ENABLED:
        item->setProperty("enabled", value != 0);
        break;
    case PL_PROP_IS_SECURE:
        item->setProperty("echoMode", value != 0 ? 2 : 0); // TextField.Password / Normal
        break;
    case PL_PROP_ACTION_ID:
    case PL_PROP_BINDING_ID:
        g.gated_ids.insert(id);
        break;
    default:
        break; // unknown/unsupported props are ignored (Qt setProperty is guarded by the switch)
    }
    Q_UNUSED(value_type);
}

void applyStringProperty(uint32_t id, uint16_t prop, const char *str, uint32_t len) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return;
    }
    const QString value = QString::fromUtf8(str, static_cast<int>(len));
    switch (prop) {
    case PL_PROP_IMAGE_SOURCE:
        item->setProperty("source", value);
        break;
    case PL_PROP_FONT_FAMILY:
        item->setProperty("font.family", value);
        break;
    case PL_PROP_PROMPT:
    case PL_PROP_LABEL:
        item->setProperty("placeholderText", value);
        break;
    default:
        break;
    }
}

// Reset renderer-owned style to defaults (spec: the renderer owns defaults).
// Value props (VALUE/checked/from/to) are app-owned and left untouched, so a
// slider drag / toggle doesn't lose its position on echo updates.
void resetNode(uint32_t id) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return;
    }
    item->setProperty("spacing", 0.0);
    item->setProperty("padding", 0.0);
    item->setProperty("topPadding", 0.0);
    item->setProperty("rightPadding", 0.0);
    item->setProperty("bottomPadding", 0.0);
    item->setProperty("leftPadding", 0.0);
    item->setProperty("opacity", 1.0);
    item->setProperty("visible", true);
}

void insertChild(uint32_t parentId, uint32_t childId, uint32_t index) {
    QQuickItem *parent = findWidget(parentId);
    QQuickItem *child = findWidget(childId);
    if (!parent || !child) {
        return;
    }
    child->setParentItem(parent);
    if (index == UINT32_MAX) {
        return; // append (already last)
    }
    QList<QQuickItem *> siblings = parent->childItems();
    siblings.removeAll(child);
    if (static_cast<int>(index) < siblings.size()) {
        QQuickItem *after = siblings.at(static_cast<int>(index));
        if (after != child) {
            child->stackBefore(after);
        }
    }
}

void deleteNode(uint32_t id) {
    auto it = g.widgets.find(id);
    if (it == g.widgets.end()) {
        return;
    }
    QQuickItem *item = it->second;
    item->setParentItem(nullptr);
    item->deleteLater();
    g.widgets.erase(it);
    g.gated_ids.erase(id);
}

} // namespace

extern "C" {

int pathland_qt_layer_init(PathlandQtWake wake, PathlandQtTick tick,
                           PathlandQtEventFn event_fn, void *user) {
    Q_UNUSED(wake); // reserved: the shell only wakes on run (the host drains the ring)
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
    g.bridge->event_fn = event_fn;
    g.bridge->user = user;
    g.bridge->gated = &g.gated_ids;
    g.bridge->suppress = &g.suppress;

    g.engine = new QQmlEngine(g.app);
    g.engine->rootContext()->setContextProperty("bridge", g.bridge);

    g.window = new QQuickWindow();
    g.window->setTitle(QString::fromUtf8("Pathland"));
    g.window->resize(420, 220);
    g.root = g.window->contentItem();

    // Idle tick: pump the ring on the Qt main thread between frames, like the
    // GTK renderer's glib idle pump.
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
                          PathlandQtWake wake, PathlandQtTick tick,
                          PathlandQtEventFn event_fn, void *user) {
    if (pathland_qt_layer_init(wake, tick, event_fn, user) != 0) {
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
    for (uint32_t i = 0; i < count; ++i) {
        const PathlandQtCommand &c = cmds[i];
        switch (c.kind) {
        case PLQT_CREATE_NODE: {
            const uint16_t component = static_cast<uint16_t>(c.b & 0xFFFF);
            QQuickItem *item = createComponent(g.engine, g.root, qmlFor(component, c.a).toUtf8());
            if (item) {
                g.widgets[c.a] = item;
            }
            break;
        }
        case PLQT_DELETE_NODE:
            deleteNode(c.a);
            break;
        case PLQT_INSERT_CHILD:
            insertChild(c.a, c.b, c.c);
            break;
        case PLQT_REMOVE_CHILD: {
            QQuickItem *child = findWidget(c.b);
            if (child) {
                child->setParentItem(nullptr); // keep alive (may be reinserted)
            }
            break;
        }
        case PLQT_MOVE_CHILD:
            insertChild(c.a, c.b, c.c); // reorder == reinsert at index
            break;
        case PLQT_SET_TEXT: {
            QQuickItem *item = findWidget(c.a);
            if (item && c.str) {
                g.suppress = true;
                item->setProperty("text", QString::fromUtf8(c.str, static_cast<int>(c.str_len)));
                g.suppress = false;
            }
            // Record for the test hook.
            if (c.str && c.str_len > 0) {
                const uint32_t n = c.str_len < sizeof(g.last_text) - 1 ? c.str_len
                                                                       : sizeof(g.last_text) - 1;
                std::memcpy(g.last_text, c.str, n);
                g.last_text[n] = '\0';
                g.last_text_len = n;
            }
            break;
        }
        case PLQT_SET_PROPERTY: {
            const uint16_t prop = static_cast<uint16_t>(c.b & 0xFFFF);
            const uint8_t vt = static_cast<uint8_t>((c.b >> 16) & 0xFF);
            g.suppress = true;
            applyProperty(c.a, prop, vt, c.c);
            g.suppress = false;
            break;
        }
        case PLQT_SET_STRING_PROPERTY:
            g.suppress = true;
            applyStringProperty(c.a, static_cast<uint16_t>(c.b), c.str, c.str_len);
            g.suppress = false;
            break;
        case PLQT_RESET_NODE:
            resetNode(c.a);
            break;
        case PLQT_SET_SCHEME:
            break; // MVP: scheme handling is a follow-up
        }
    }
    g.apply_count += count;
}

void pathland_qt_layer_reset(void) {
    for (auto &kv : g.widgets) {
        kv.second->setParentItem(nullptr);
        kv.second->deleteLater();
    }
    g.widgets.clear();
    g.gated_ids.clear();
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

uint32_t pathland_qt_layer_apply_count(void) { return g.apply_count; }

uint32_t pathland_qt_layer_last_text(char *out, uint32_t cap) {
    if (!out || cap == 0) {
        return g.last_text_len;
    }
    const uint32_t n = g.last_text_len < cap ? g.last_text_len : cap - 1;
    std::memcpy(out, g.last_text, n);
    out[n] = '\0';
    return n;
}

uint32_t pathland_qt_layer_root_child_count(void) {
    return g.root ? static_cast<uint32_t>(g.root->childItems().size()) : 0;
}

uint32_t pathland_qt_layer_widget_child_count(uint32_t id) {
    QQuickItem *item = findWidget(id);
    return item ? static_cast<uint32_t>(item->childItems().size()) : 0;
}

uint32_t pathland_qt_layer_widget_text(uint32_t id, char *out, uint32_t cap) {
    QQuickItem *item = findWidget(id);
    if (!item || !out || cap == 0) {
        return 0;
    }
    const QByteArray bytes = item->property("text").toString().toUtf8();
    const uint32_t n = bytes.size() < static_cast<int>(cap) ? static_cast<uint32_t>(bytes.size())
                                                            : cap - 1;
    std::memcpy(out, bytes.constData(), n);
    out[n] = '\0';
    return n;
}

uint32_t pathland_qt_layer_widget_prop_text(uint32_t id, const char *prop, char *out,
                                            uint32_t cap) {
    QQuickItem *item = findWidget(id);
    if (!item || !out || cap == 0) {
        return 0;
    }
    const QByteArray bytes = item->property(prop).toString().toUtf8();
    const uint32_t n = bytes.size() < static_cast<int>(cap) ? static_cast<uint32_t>(bytes.size())
                                                            : cap - 1;
    std::memcpy(out, bytes.constData(), n);
    out[n] = '\0';
    return n;
}

void pathland_qt_layer_shutdown(void) {
    for (auto &kv : g.widgets) {
        kv.second->deleteLater();
    }
    g.widgets.clear();
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