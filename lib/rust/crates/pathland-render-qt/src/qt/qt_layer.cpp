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
#include <QtGui/QStyleHints>
#include <QtGui/QKeyEvent>
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

#include <algorithm>
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
    PL_COMP_STEPPER = 0x26,
    PL_COMP_DATE_PICKER = 0x27,
    PL_COMP_PICKER = 0x28,
    PL_COMP_MENU = 0x29,
    PL_COMP_COLOR_PICKER = 0x2A,
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
    PL_PROP_EVENT_LISTENERS = 0x2005,
    PL_PROP_PROGRESS = 0x200E,
    PL_PROP_IS_INDETERMINATE = 0x200F,
    PL_PROP_SELECTION = 0x2010,
    PL_PROP_ROUTE = 0x2019,
    PL_PROP_NAV_DEPTH = 0x201A,
    PL_PROP_NAV_CHROME = 0x201B,
    PL_PROP_TRANSITION = 0x1031,
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
    // id -> component type (for control-specific behaviour, e.g. pickers).
    std::unordered_map<uint32_t, uint16_t> components;
    // id -> parent node id (for picker model rebuilds / nav reconcile).
    std::unordered_map<uint32_t, uint32_t> parents;
    // parent -> ordered child ids (the renderer's structural mirror; drives
    // PICKER ComboBox models and the nav slot's destination lookup).
    std::unordered_map<uint32_t, std::vector<uint32_t>> children_of;
    // Navigation slots (VSTACK/HSTACK promoted to a StackView on ROUTE):
    // slot -> StackView, per-slot ROUTE / NAV_DEPTH / custom-chrome flag,
    // and the adapter's page-stack mirror + per-page route tags.
    std::unordered_set<uint32_t> nav_slots;
    std::unordered_map<uint32_t, QQuickItem *> nav_stacks;
    std::unordered_map<uint32_t, QString> nav_route;
    std::unordered_map<uint32_t, uint32_t> nav_depth;
    std::unordered_set<uint32_t> nav_custom_chrome;
    std::unordered_map<uint32_t, std::vector<QQuickItem *>> nav_pages;
    std::unordered_map<uint32_t, std::vector<QString>> nav_page_tags;
    // Nodes that may report interactions (carry BINDING_ID / ACTION_ID).
    std::unordered_set<uint32_t> gated_ids;
    // EVENT_LISTENERS mask per node (raw pointer stream gating).
    std::unordered_map<uint32_t, uint32_t> listener_masks;
    // EVENT_LISTENERS MouseArea overlay per node (id -> MouseArea item).
    std::unordered_map<uint32_t, QQuickItem *> mouse_areas;
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
    case PL_COMP_PROGRESS:
    case PL_COMP_GAUGE:
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "ProgressBar { objectName: \"pl%1\" }")
            .arg(id);
    case PL_COMP_STEPPER:
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "SpinBox { objectName: \"pl%1\" }")
            .arg(id);
    case PL_COMP_PICKER:
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "ComboBox { objectName: \"pl%1\"; "
                   "onActivated: bridge.valueChanged(%2, index) }")
            .arg(id)
            .arg(id);
    case PL_COMP_MENU:
        // Qt 6 removed MenuButton; a Button is the trigger (a popup Menu of the
        // option children is a documented gap, matching the GTK renderer).
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "Button { objectName: \"pl%1\" }")
            .arg(id);
    case PL_COMP_DATE_PICKER:
        // Qt Quick Controls has no native date picker; MVP approximation.
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "TextField { objectName: \"pl%1\"; placeholderText: \"date\" }")
            .arg(id);
    case PL_COMP_COLOR_PICKER:
        // Qt Quick Controls has no native color picker; MVP approximation.
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
                   "Button { objectName: \"pl%1\"; text: \"color\" }")
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

// ---------------------------------------------------------------------------
// Navigation adapter (spec DSL.md §4.5): a VSTACK/HSTACK slot carrying ROUTE
// renders as a StackView; the adapter reconciles its page stack by NAV_DEPTH.
// ---------------------------------------------------------------------------

bool isStackComponent(uint16_t component) {
    return component == PL_COMP_VSTACK || component == PL_COMP_HSTACK ||
           component == PL_COMP_LAZY_VSTACK || component == PL_COMP_LAZY_HSTACK;
}

// The nav container: a plain Item page stack. Qt Quick has no native
// navigation container, and StackView's push/pop are QML-callable-only (not
// invokable from C++ via QMetaObject), so the renderer manages its page cache
// itself: pages are children of this Item, only the top is visible.
QQuickItem *createNavContainer(QQuickItem *parent) {
    return createComponent(g.engine, parent, "import QtQuick 2.15\nItem { clip: true }");
}

// Promote a slot's Column/Row to the nav container (first non-empty ROUTE).
// Children are inserted after props, so the slot has none yet — safe to swap.
void promoteNavSlot(uint32_t slotId) {
    auto it = g.widgets.find(slotId);
    if (it == g.widgets.end()) {
        return;
    }
    QQuickItem *old = it->second;
    QQuickItem *container = createNavContainer(old->parentItem());
    if (!container) {
        return;
    }
    QObject *anchors = container->property("anchors").value<QObject *>();
    if (anchors && old->parentItem()) {
        anchors->setProperty("fill", QVariant::fromValue(old->parentItem()));
    }
    g.widgets[slotId] = container;
    g.nav_slots.insert(slotId);
    g.nav_stacks[slotId] = container;
    old->deleteLater();
}

// PlatformDefault chrome: a page wrapper with a header back button (visible
// except on the root page) over a content placeholder. The back button reports
// a native back request directly; the app pops its own back-stack and re-emits.
QQuickItem *navPageWrapper(bool isRoot) {
    return createComponent(
        g.engine, nullptr,
        QStringLiteral(
            "import QtQuick 2.15\nimport QtQuick.Controls 2.15\n"
            "Item {\n"
            "  property bool showBack: %1\n"
            "  Column { anchors.fill: parent\n"
            "    Row { id: header; height: showBack ? 32 : 0; visible: showBack\n"
            "      Button { text: \"\\u2190\"; onClicked: bridge.navigateBack() } }\n"
            "    Item { id: content; objectName: \"content\";\n"
            "      width: parent.width; height: parent.height - header.height } } }")
            .arg(isRoot ? "false" : "true")
            .toUtf8());
}

// Place a destination subtree root into a container, filling it.
void placeDestination(QQuickItem *dest, QQuickItem *container) {
    if (!dest || !container) {
        return;
    }
    dest->setParentItem(container);
    QObject *anchors = dest->property("anchors").value<QObject *>();
    if (anchors) {
        anchors->setProperty("fill", QVariant::fromValue(container));
    }
}

// Reconcile a nav slot's page stack against the app's current destination
// (single child) + ROUTE + NAV_DEPTH, mirroring the GTK renderer's depth logic:
// pop down to depth, then Refresh (same route) / Push (deeper) / Replace
// (same depth, new route). The back-stack stays app-owned; this stack is the
// renderer's rendered-output cache. Only the top page is visible.
void reconcileNav(uint32_t slotId) {
    auto sit = g.nav_stacks.find(slotId);
    if (sit == g.nav_stacks.end()) {
        return;
    }
    QQuickItem *container = sit->second;
    auto rit = g.nav_route.find(slotId);
    const QString route = rit != g.nav_route.end() ? rit->second : QString();
    const uint32_t depth = std::max(g.nav_depth[slotId], 1u);
    const bool custom = g.nav_custom_chrome.count(slotId) > 0;

    // The destination subtree root: the slot's single child.
    QQuickItem *dest = nullptr;
    for (uint32_t childId : g.children_of[slotId]) {
        dest = findWidget(childId);
        if (dest) {
            break;
        }
    }

    auto &pages = g.nav_pages[slotId];
    auto &tags = g.nav_page_tags[slotId];

    // Pop down to the app's depth (renderer-driven; no NAVIGATE emitted).
    while (pages.size() > depth) {
        QQuickItem *page = pages.back();
        pages.pop_back();
        tags.pop_back();
        if (custom) {
            // The page IS the destination widget (app-owned); just detach it.
            page->setParentItem(nullptr);
            page->setVisible(false);
        } else {
            // Detach any destination the wrapper owns so the app-owned widget
            // survives the wrapper's destruction.
            if (QQuickItem *c = page->findChild<QQuickItem *>("content")) {
                const auto kids = c->childItems();
                for (QQuickItem *kid : kids) {
                    kid->setParentItem(nullptr);
                }
            }
            page->setParentItem(nullptr);
            page->deleteLater();
        }
    }

    auto showTop = [&]() {
        for (size_t i = 0; i < pages.size(); ++i) {
            pages[i]->setVisible(i + 1 == pages.size());
        }
    };

    if (pages.empty()) {
        if (custom && !dest) {
            return; // nothing to show yet (reconcile re-fires on the child)
        }
        QQuickItem *page = custom ? dest : navPageWrapper(/*isRoot=*/true);
        if (custom) {
            placeDestination(dest, container);
        } else if (dest) {
            placeDestination(dest, page->findChild<QQuickItem *>("content"));
            page->setParentItem(container);
        } else {
            page->setParentItem(container);
        }
        page->setVisible(true);
        pages.push_back(page);
        tags.push_back(route);
        return;
    }

    QQuickItem *top = pages.back();
    const QString topTag = tags.back();
    if (topTag == route) {
        // Refresh in place (signal update / re-emit after a user back).
        if (!custom && dest) {
            placeDestination(dest, top->findChild<QQuickItem *>("content"));
        }
    } else if (pages.size() < depth) {
        // Deeper -> push a new page.
        if (custom && !dest) {
            return;
        }
        QQuickItem *page = custom ? dest : navPageWrapper(/*isRoot=*/false);
        if (custom) {
            placeDestination(dest, container);
        } else if (dest) {
            placeDestination(dest, page->findChild<QQuickItem *>("content"));
            page->setParentItem(container);
        } else {
            page->setParentItem(container);
        }
        if (top != page) {
            top->setVisible(false);
        }
        page->setVisible(true);
        pages.push_back(page);
        tags.push_back(route);
    } else {
        // Same depth, new route -> replace the top page's content.
        if (custom) {
            if (dest && dest != top) {
                top->setParentItem(nullptr);
                placeDestination(dest, container);
                dest->setVisible(true);
                pages.back() = dest;
            }
        } else if (dest) {
            placeDestination(dest, top->findChild<QQuickItem *>("content"));
        }
        tags.back() = route;
    }
    showTop();
}

// Remove a node's EVENT_LISTENERS MouseArea overlay (if any).
void detachPointerArea(uint32_t id) {
    auto it = g.mouse_areas.find(id);
    if (it != g.mouse_areas.end()) {
        it->second->deleteLater();
        g.mouse_areas.erase(it);
    }
    g.listener_masks.erase(id);
}

// Attach a MouseArea overlay for a node's EVENT_LISTENERS pointer mask. Only
// the requested handler kinds are wired (each already gated by the mask), so
// the Bridge's pointer path needs no extra gating.
void attachPointerArea(uint32_t id, uint32_t mask) {
    detachPointerArea(id);
    const uint32_t bits = mask & (1 | 2 | 4); // POINTER_DOWN | POINTER_MOVE | POINTER_UP
    if (bits == 0) {
        return;
    }
    QQuickItem *item = findWidget(id);
    if (!item) {
        return;
    }
    QString handlers;
    if (mask & 1) { // POINTER_DOWN -> PLQT_EV_POINTER_DOWN = 4
        handlers += QStringLiteral("onPressed: bridge.pointer(%1, 4, mouse.x, mouse.y)\n").arg(id);
    }
    if (mask & 2) { // POINTER_MOVE -> PLQT_EV_POINTER_MOVE = 5
        handlers += QStringLiteral("onPositionChanged: bridge.pointer(%1, 5, mouse.x, mouse.y)\n").arg(id);
    }
    if (mask & 4) { // POINTER_UP -> PLQT_EV_POINTER_UP = 1
        handlers += QStringLiteral("onReleased: bridge.pointer(%1, 1, mouse.x, mouse.y)\n").arg(id);
    }
    const QString qml =
        QStringLiteral("import QtQuick 2.15\nMouseArea { anchors.fill: parent;\n%1}").arg(handlers);
    QQuickItem *area = createComponent(g.engine, item, qml.toUtf8());
    if (area) {
        g.mouse_areas[id] = area;
        g.listener_masks[id] = mask;
    }
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
    case PL_PROP_PROGRESS:
        item->setProperty("value", static_cast<double>(f32(value)));
        break;
    case PL_PROP_IS_INDETERMINATE:
        item->setProperty("indeterminate", value != 0);
        break;
    case PL_PROP_SELECTION:
        item->setProperty("currentIndex", static_cast<int>(value));
        break;
    case PL_PROP_NAV_DEPTH:
        g.nav_depth[id] = value;
        reconcileNav(id);
        break;
    case PL_PROP_NAV_CHROME:
        if (f32(value) >= 0.5f) {
            g.nav_custom_chrome.insert(id);
        } else {
            g.nav_custom_chrome.erase(id);
        }
        reconcileNav(id);
        break;
    case PL_PROP_EVENT_LISTENERS:
        attachPointerArea(id, value);
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
    case PL_PROP_ROUTE:
        g.nav_route[id] = value;
        // Promote a stack slot to a StackView on the first non-empty ROUTE.
        if (!value.isEmpty() && !g.nav_slots.count(id) && isStackComponent(g.components[id])) {
            promoteNavSlot(id);
        }
        reconcileNav(id);
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
    // Listener overlays are renderer-owned; the full property set that follows
    // RESET re-attaches them from the (re-sent) EVENT_LISTENERS value.
    detachPointerArea(id);
    item->setProperty("spacing", 0.0);
    item->setProperty("padding", 0.0);
    item->setProperty("topPadding", 0.0);
    item->setProperty("rightPadding", 0.0);
    item->setProperty("bottomPadding", 0.0);
    item->setProperty("leftPadding", 0.0);
    item->setProperty("opacity", 1.0);
    item->setProperty("visible", true);
}

// Rebuild a PICKER's ComboBox model from its ordered option children's text.
void rebuildPickerModel(uint32_t parentId) {
    auto cit = g.components.find(parentId);
    if (cit == g.components.end() || cit->second != PL_COMP_PICKER) {
        return;
    }
    QQuickItem *picker = findWidget(parentId);
    if (!picker) {
        return;
    }
    QStringList model;
    for (uint32_t optId : g.children_of[parentId]) {
        QQuickItem *opt = findWidget(optId);
        model << (opt ? opt->property("text").toString() : QString());
    }
    picker->setProperty("model", model);
}

void insertChild(uint32_t parentId, uint32_t childId, uint32_t index) {
    QQuickItem *child = findWidget(childId);
    if (!child) {
        return;
    }
    g.parents[childId] = parentId;
    // Maintain the ordered structural mirror for every parent.
    auto &siblings = g.children_of[parentId];
    siblings.erase(std::remove(siblings.begin(), siblings.end(), childId), siblings.end());
    if (index == UINT32_MAX || index >= siblings.size()) {
        siblings.push_back(childId);
    } else {
        siblings.insert(siblings.begin() + index, childId);
    }

    if (g.components[parentId] == PL_COMP_PICKER) {
        rebuildPickerModel(parentId); // options drive the model, not the visual tree
        return;
    }
    if (g.nav_slots.count(parentId)) {
        reconcileNav(parentId); // destination subtree swap -> refresh/replace the top page
        return; // the destination is placed by the nav adapter, not visually here
    }

    QQuickItem *parent = findWidget(parentId);
    if (!parent) {
        return;
    }
    child->setParentItem(parent);
    if (index == UINT32_MAX) {
        return; // append (already last)
    }
    QList<QQuickItem *> siblingsList = parent->childItems();
    siblingsList.removeAll(child);
    if (static_cast<int>(index) < siblingsList.size()) {
        QQuickItem *after = siblingsList.at(static_cast<int>(index));
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
    g.components.erase(id);
    g.parents.erase(id);
    for (auto &kv : g.children_of) {
        auto &opts = kv.second;
        opts.erase(std::remove(opts.begin(), opts.end(), id), opts.end());
    }
    g.children_of.erase(id);
    g.nav_slots.erase(id);
    g.nav_stacks.erase(id);
    g.nav_route.erase(id);
    g.nav_depth.erase(id);
    g.nav_custom_chrome.erase(id);
    g.nav_pages.erase(id);
    g.nav_page_tags.erase(id);
    g.gated_ids.erase(id);
    detachPointerArea(id);
}

// Native back: Escape is a platform back request (global, never node-keyed),
// matching the GTK renderer's window-level key controller. BackSpace is
// deferred (needs text-focus detection).
class KeyFilter : public QObject {
public:
    using QObject::QObject;
protected:
    bool eventFilter(QObject *obj, QEvent *ev) override {
        if (ev->type() == QEvent::KeyPress) {
            auto *ke = static_cast<QKeyEvent *>(ev);
            if (ke->key() == Qt::Key_Escape) {
                if (g.bridge) {
                    g.bridge->navigateBack();
                }
                return true;
            }
        }
        return QObject::eventFilter(obj, ev);
    }
};

// Report the effective platform color scheme (0 = light, 1 = dark) through the
// Bridge; Rust re-resolves design tokens and re-applies (spec/TOKENS.md).
void reportScheme() {
    if (!g.app || !g.bridge) {
        return;
    }
    const bool dark = g.app->styleHints()->colorScheme() == Qt::ColorScheme::Dark;
    g.bridge->schemeChanged(dark ? 1 : 0);
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

    // Native back: Escape on the window is a global back request (see KeyFilter).
    g.window->installEventFilter(new KeyFilter(g.window));

    // Color-scheme detection (spec/TOKENS.md): the renderer derives the
    // effective scheme from the platform and re-resolves tokens when it
    // changes. Scheme is never carried by the protocol.
    QObject::connect(g.app->styleHints(), &QStyleHints::colorSchemeChanged, g.app,
                     []() { reportScheme(); });
    reportScheme();

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
                g.components[c.a] = component;
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
            const uint32_t parentId = c.a;
            const uint32_t childId = c.b;
            g.parents.erase(childId);
            auto &siblings = g.children_of[parentId];
            siblings.erase(std::remove(siblings.begin(), siblings.end(), childId), siblings.end());
            if (g.components[parentId] == PL_COMP_PICKER) {
                rebuildPickerModel(parentId);
            } else if (g.nav_slots.count(parentId)) {
                reconcileNav(parentId);
            } else {
                QQuickItem *child = findWidget(childId);
                if (child) {
                    child->setParentItem(nullptr); // keep alive (may be reinserted)
                }
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
            // A picker option's label feeds its parent's ComboBox model.
            auto pit = g.parents.find(c.a);
            if (pit != g.parents.end() && g.components[pit->second] == PL_COMP_PICKER) {
                rebuildPickerModel(pit->second);
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
    g.components.clear();
    g.parents.clear();
    g.children_of.clear();
    g.nav_slots.clear();
    g.nav_stacks.clear();
    g.nav_route.clear();
    g.nav_depth.clear();
    g.nav_custom_chrome.clear();
    g.nav_pages.clear();
    g.nav_page_tags.clear();
    g.gated_ids.clear();
    g.listener_masks.clear();
    g.mouse_areas.clear();
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

uint32_t pathland_qt_layer_nav_depth(uint32_t slot) {
    auto it = g.nav_pages.find(slot);
    return it == g.nav_pages.end() ? 0 : static_cast<uint32_t>(it->second.size());
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
    g.components.clear();
    g.parents.clear();
    g.children_of.clear();
    g.nav_slots.clear();
    g.nav_stacks.clear();
    g.nav_route.clear();
    g.nav_depth.clear();
    g.nav_custom_chrome.clear();
    g.nav_pages.clear();
    g.nav_page_tags.clear();
    g.mouse_areas.clear();
    g.listener_masks.clear();
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