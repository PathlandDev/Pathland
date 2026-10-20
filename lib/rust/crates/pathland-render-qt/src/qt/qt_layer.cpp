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
#include <QtCore/QCoreApplication>
#include <QtCore/QDate>
#include <QtCore/QUrl>
#include <QtCore/QByteArray>
#include <QtCore/QString>
#include <QtGui/QColor>
#include <QtCore/QDebug>
#include <QtCore/QVariant>
// The Layout attached properties (`Layout.fillWidth`/`fillHeight`/`preferredWidth`)
// used to stretch FILL children inside RowLayout/ColumnLayout stacks. Private
// header (Qt Quick Layouts ships it); the build.rs adds the framework include
// dirs. Matches the Qt version the crate builds against.
#include <QtQuickLayouts/private/qquicklayout_p.h>

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
    PL_COMP_VIDEO = 0x0A,
    PL_COMP_ICON = 0x0B,
    PL_COMP_VSTACK = 0x10,
    PL_COMP_HSTACK = 0x11,
    PL_COMP_ZSTACK = 0x12,
    PL_COMP_GRID = 0x13,
    PL_COMP_SCROLL = 0x14,
    PL_COMP_LAZY_VGRID = 0x15,
    PL_COMP_LAZY_HGRID = 0x16,
    PL_COMP_SIZE_THAT_FITS = 0x17,
    PL_COMP_LAZY_VSTACK = 0x1B,
    PL_COMP_LAZY_HSTACK = 0x1C,
    PL_COMP_GRID_ROW = 0x1D,
    PL_COMP_PROXY_VIEW = 0x1E,
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
    PL_COMP_MEDIA_CONTROLLER = 0x30,
    PL_COMP_COMMENT = 0x7F,
};

// Property IDs (spec/MODIFIERS.md).
enum {
    PL_PROP_SPACING = 0x0001,
    PL_PROP_ALIGNMENT = 0x0002,
    PL_PROP_CONTENT_MARGINS = 0x0005,
    PL_PROP_TEXT_ALIGNMENT = 0x000C,
    PL_PROP_LINE_LIMIT = 0x000B,
    PL_PROP_TRUNCATION = 0x000D,
    PL_PROP_OFFSET_X = 0x000E,
    PL_PROP_OFFSET_Y = 0x000F,
    PL_PROP_POSITION_X = 0x0010,
    PL_PROP_POSITION_Y = 0x0011,
    PL_PROP_MIN_WIDTH = 0x0012,
    PL_PROP_IDEAL_WIDTH = 0x0013,
    PL_PROP_MAX_WIDTH = 0x0014,
    PL_PROP_MIN_HEIGHT = 0x0015,
    PL_PROP_IDEAL_HEIGHT = 0x0016,
    PL_PROP_MAX_HEIGHT = 0x0017,
    PL_PROP_FIXED_SIZE_HORIZONTAL = 0x0018,
    PL_PROP_FIXED_SIZE_VERTICAL = 0x0019,
    PL_PROP_LAYOUT_PRIORITY = 0x001A,
    PL_PROP_ASPECT_RATIO = 0x001B,
    PL_PROP_CONTENT_MODE = 0x001C,
    PL_PROP_MINIMUM_SCALE_FACTOR = 0x001D,
    PL_PROP_GRID_COLUMNS = 0x001E,
    PL_PROP_GRID_ROWS = 0x001F,
    PL_PROP_BACKGROUND = 0x1001,
    PL_PROP_IMAGE_SOURCE = 0x1002,
    PL_PROP_BORDER_WIDTH = 0x1003,
    PL_PROP_BORDER_COLOR = 0x1004,
    PL_PROP_BORDER_RADIUS = 0x1005,
    PL_PROP_CLIPS_TO_BOUNDS = 0x1010,
    PL_PROP_SHADOW_COLOR = 0x1021,
    PL_PROP_SHADOW_RADIUS = 0x1022,
    PL_PROP_SHADOW_X = 0x1023,
    PL_PROP_SHADOW_Y = 0x1024,
    PL_PROP_ICON_NAME = 0x1038,
    PL_PROP_FIT_QUERY = 0x1039,
    PL_PROP_TEXT_STYLE = 0x1032,
    PL_PROP_MEDIA_SOURCE = 0x103B,
    PL_PROP_MEDIA_CONTROLLER_ID = 0x103A,
    PL_PROP_PLAYBACK_STATE = 0x1035,
    PL_PROP_MEDIA_POSITION = 0x1036,
    PL_PROP_MEDIA_VOLUME = 0x1037,
    PL_PROP_MEDIA_MUTED = 0x103C,
    PL_PROP_MEDIA_LOOP = 0x103D,
    PL_PROP_MEDIA_RATE = 0x103E,
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

// Pathland TEXT_ALIGNMENT enum -> Qt::Alignment (Leading=0, Center=1,
// Trailing=2, Fill/Justify=3).
int alignmentFor(float code) {
    switch (static_cast<int>(code + 0.5f)) {
    case 1:
        return int(Qt::AlignHCenter);
    case 2:
        return int(Qt::AlignRight);
    case 3:
        return int(Qt::AlignJustify);
    default:
        return int(Qt::AlignLeft);
    }
}

// Canonical icon name -> glyph (spec/ICONS.md). The vocabulary is the protocol
// contract; the concrete glyph is renderer-owned. Unknown names fall back to a
// neutral dot so the node still renders.
// Resolve an asset source: `/_pathland/<rest>` -> `<root>/<rest>` when an
// asset root is configured, else the source unchanged. Non-`/_pathland/`
// sources (absolute paths, file:// URLs) pass through.
QUrl resolveAsset(const QString &source, const QString &root) {
    const QString prefix = QStringLiteral("/_pathland/");
    if (root.isEmpty() || !source.startsWith(prefix)) {
        return QUrl(source);
    }
    QString r = root;
    while (r.endsWith('/')) {
        r.chop(1);
    }
    if (r.isEmpty()) {
        return QUrl(source);
    }
    return QUrl::fromLocalFile(r + QStringLiteral("/") + source.mid(prefix.size()));
}

QString iconGlyph(const QString &name) {
    static const std::unordered_map<QString, QString> glyphs = {
        {QStringLiteral("home"), QStringLiteral("\u2302")},
        {QStringLiteral("search"), QStringLiteral("\u2315")},
        {QStringLiteral("menu"), QStringLiteral("\u2630")},
        {QStringLiteral("close"), QStringLiteral("\u2715")},
        {QStringLiteral("chevron-left"), QStringLiteral("\u2039")},
        {QStringLiteral("chevron-right"), QStringLiteral("\u203A")},
        {QStringLiteral("chevron-up"), QStringLiteral("\u2303")},
        {QStringLiteral("chevron-down"), QStringLiteral("\u2304")},
        {QStringLiteral("arrow-left"), QStringLiteral("\u2190")},
        {QStringLiteral("arrow-right"), QStringLiteral("\u2192")},
        {QStringLiteral("add"), QStringLiteral("+")},
        {QStringLiteral("remove"), QStringLiteral("\u2212")},
        {QStringLiteral("check"), QStringLiteral("\u2713")},
        {QStringLiteral("edit"), QStringLiteral("\u270E")},
        {QStringLiteral("delete"), QStringLiteral("\u2717")},
        {QStringLiteral("save"), QStringLiteral("\U0001F4BE")},
        {QStringLiteral("share"), QStringLiteral("\u21AA")},
        {QStringLiteral("download"), QStringLiteral("\u21E9")},
        {QStringLiteral("upload"), QStringLiteral("\u21E7")},
        {QStringLiteral("refresh"), QStringLiteral("\u21BB")},
        {QStringLiteral("lock"), QStringLiteral("\U0001F512")},
        {QStringLiteral("logout"), QStringLiteral("\u21B5")},
        {QStringLiteral("play"), QStringLiteral("\u25B6")},
        {QStringLiteral("pause"), QStringLiteral("\u2016")},
        {QStringLiteral("stop"), QStringLiteral("\u25A0")},
        {QStringLiteral("skip-back"), QStringLiteral("\u23EE")},
        {QStringLiteral("skip-forward"), QStringLiteral("\u23ED")},
        {QStringLiteral("volume"), QStringLiteral("\U0001F50A")},
        {QStringLiteral("volume-mute"), QStringLiteral("\U0001F507")},
        {QStringLiteral("shuffle"), QStringLiteral("\U0001F500")},
        {QStringLiteral("repeat"), QStringLiteral("\U0001F501")},
        {QStringLiteral("music"), QStringLiteral("\u266B")},
        {QStringLiteral("info"), QStringLiteral("\u2139")},
        {QStringLiteral("warning"), QStringLiteral("\u26A0")},
        {QStringLiteral("error"), QStringLiteral("\u26D4")},
        {QStringLiteral("success"), QStringLiteral("\u2714")},
        {QStringLiteral("heart"), QStringLiteral("\u2665")},
        {QStringLiteral("star"), QStringLiteral("\u2605")},
        {QStringLiteral("bell"), QStringLiteral("\U0001F514")},
        {QStringLiteral("cloud"), QStringLiteral("\u2601")},
        {QStringLiteral("user"), QStringLiteral("\U0001F464")},
        {QStringLiteral("users"), QStringLiteral("\U0001F465")},
        {QStringLiteral("settings"), QStringLiteral("\u2699")},
        {QStringLiteral("grid"), QStringLiteral("\u25A6")},
        {QStringLiteral("list"), QStringLiteral("\u2637")},
        {QStringLiteral("filter"), QStringLiteral("\u2630")},
        {QStringLiteral("folder"), QStringLiteral("\U0001F4C1")},
        {QStringLiteral("file"), QStringLiteral("\U0001F4C4")},
        {QStringLiteral("image"), QStringLiteral("\U0001F5BC")},
    };
    auto it = glyphs.find(name);
    return it == glyphs.end() ? QStringLiteral("\u2022") : it->second;
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
    // Stack cross-axis alignment (raw Pathland alignment) per stack node id,
    // applied to each child via its Layout attached properties on insert.
    std::unordered_map<uint32_t, uint32_t> stack_align;
    // EVENT_LISTENERS mask per node (raw pointer stream gating).
    std::unordered_map<uint32_t, uint32_t> listener_masks;
    // MEDIA_CONTROLLER: the last app-declared playback state (0 pause, 1 play),
    // re-applied when MEDIA_SOURCE lands after the transport props.
    std::unordered_map<uint32_t, uint32_t> media_play_state;
    // Local asset root for web-style `/_pathland/...` source paths (empty = use
    // sources as-is), set by a foreign host via pathland_qt_layer_set_asset_root.
    QString asset_root;
    // SIZE_THAT_FITS: ascending candidate thresholds (`FIT_QUERY`) and the last
    // reported fit index (so FIT_CHANGED is reported only when it changes).
    std::unordered_map<uint32_t, std::vector<double>> fit_queries;
    std::unordered_map<uint32_t, int> fit_index;
    // GRID_ROW -> its parent Grid (rows are flattened; cells attach to the grid).
    std::unordered_map<uint32_t, uint32_t> grid_of;
    // Full EVENT_LISTENERS mask per node (gates focus/editing/submit reports).
    // Controls promoted to composite-override mode (children replace native chrome).
    std::unordered_set<uint32_t> composite;
    std::unordered_map<uint32_t, uint32_t> node_listeners;
    // CSS-like foreground context: a container's COLOR inherited by descendant
    // Text/Icon, and which nodes set their own color.
    std::unordered_map<uint32_t, uint32_t> fg_colors;
    std::unordered_set<uint32_t> fg_own;
    // `.shadow(...)` — a best-effort offset rectangle behind the node.
    std::unordered_map<uint32_t, QColor> shadow_color;
    std::unordered_map<uint32_t, double> shadow_radius;
    std::unordered_map<uint32_t, double> shadow_dx;
    std::unordered_map<uint32_t, double> shadow_dy;
    // The focused text-input node (keys are reported against it) + its component.
    uint32_t focused_id = 0;
    uint16_t focused_component = 0;
    // EVENT_LISTENERS MouseArea overlay per node (id -> MouseArea item).
    std::unordered_map<uint32_t, QQuickItem *> mouse_areas;
    // Deferred FILL dimensions (bit0=width, bit1=height) for widgets whose
    // props arrived before they gained their final parent. Applied on insert
    // (skipped when the parent is a positioner — Row/Column reject anchors).
    std::unordered_map<QQuickItem *, uint8_t> pending_fill;
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
    case PL_COMP_PROXY_VIEW:
    case PL_COMP_VSTACK:
    case PL_COMP_LAZY_VSTACK:
        // A stack renders as a wrapper Item holding an inner ColumnLayout (the
        // node's widget is the wrapper; children attach to the layout, whose
        // `Layout` attached properties give FILL children a real stretch —
        // plain Row/Column reject anchors on their children). Padding is
        // carried by `plPad` (drives the layout's anchors.margins + the
        // wrapper's implicit size so the wrapper hugs its content).
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Layouts 1.15\n"
                   "Item {\n"
                   "  objectName: \"pl%1\"\n"
                   "  property real plPad: 0\n"
                   "  implicitWidth: lay ? lay.implicitWidth + 2 * plPad : 0\n"
                   "  implicitHeight: lay ? lay.implicitHeight + 2 * plPad : 0\n"
                   "  Rectangle {\n"
                   "    objectName: \"pl%1bg\"\n"
                   "    anchors.fill: parent\n"
                   "    color: \"transparent\"\n"
                   "    z: -1\n"
                   "  }\n"
                   "  ColumnLayout {\n"
                   "    id: lay\n"
                   "    objectName: \"pl%1lay\"\n"
                   "    anchors.fill: parent\n"
                   "    anchors.margins: plPad\n"
                   "    spacing: 0\n"
                   "  }\n"
                   "}")
            .arg(id);
    case PL_COMP_HSTACK:
    case PL_COMP_LAZY_HSTACK:
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Layouts 1.15\n"
                   "Item {\n"
                   "  objectName: \"pl%1\"\n"
                   "  property real plPad: 0\n"
                   "  implicitWidth: lay ? lay.implicitWidth + 2 * plPad : 0\n"
                   "  implicitHeight: lay ? lay.implicitHeight + 2 * plPad : 0\n"
                   "  Rectangle {\n"
                   "    objectName: \"pl%1bg\"\n"
                   "    anchors.fill: parent\n"
                   "    color: \"transparent\"\n"
                   "    z: -1\n"
                   "  }\n"
                   "  RowLayout {\n"
                   "    id: lay\n"
                   "    objectName: \"pl%1lay\"\n"
                   "    anchors.fill: parent\n"
                   "    anchors.margins: plPad\n"
                   "    spacing: 0\n"
                   "  }\n"
                   "}")
            .arg(id);
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
        // Word-wrap within the allocated width (HTML/SwiftUI behavior); with no
        // constraint the implicit width is the unwrapped line, so this is inert.
        return QStringLiteral(
                   "import QtQuick 2.15\nText { objectName: \"pl%1\"; "
                   "wrapMode: Text.WordWrap }")
            .arg(id);
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
    case PL_COMP_ICON:
        // A semantic icon: a Text item whose glyph is resolved from ICON_NAME
        // by the renderer (spec/ICONS.md — the glyph mapping is renderer-owned).
        return QStringLiteral("import QtQuick 2.15\nText { objectName: \"pl%1\" }").arg(id);
    case PL_COMP_SIZE_THAT_FITS:
        // A fit slot: stretches to its parent and shows the one selected child.
        return QStringLiteral(
                   "import QtQuick 2.15\nItem { objectName: \"pl%1\"; clip: true }")
            .arg(id);
    case PL_COMP_GRID_ROW:
        // Explicit row grouping inside a Grid (spec/PRIMITIVES.md): a RowLayout
        // keeps its cells together; cross-row column alignment is a follow-up.
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtQuick.Layouts 1.15\n"
                   "RowLayout { objectName: \"pl%1\" }")
            .arg(id);
    case PL_COMP_VIDEO:
        // Video surface: renders the referenced MEDIA_CONTROLLER's output
        // (spec/MEDIA.md). The controller owns the playback; this is just the
        // VideoOutput that displays it.
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtMultimedia\n"
                   "Item { objectName: \"pl%1\"; "
                   "VideoOutput { objectName: \"pl%1out\"; anchors.fill: parent } }")
            .arg(id);
    case PL_COMP_MEDIA_CONTROLLER:
        // Non-visual: owns the media resource + transport (spec/MEDIA.md). The
        // item is never attached to the visual tree (layout-transparent); it
        // hosts the QML MediaPlayer that VIDEO surfaces bind to.
        return QStringLiteral(
                   "import QtQuick 2.15\nimport QtMultimedia\n"
                   "Item {\n"
                   "  objectName: \"pl%1\"\n"
                   "  visible: false\n"
                   "  width: 0\n"
                   "  height: 0\n"
                   "  MediaPlayer {\n"
                   "    objectName: \"pl%1player\"\n"
                   "    onPlaybackStateChanged: bridge.mediaPlayState(%1, playbackState === MediaPlayer.PlayingState)\n"
                   "    onPositionChanged: bridge.mediaTime(%1, position / 1000.0)\n"
                   "    onDurationChanged: bridge.mediaDuration(%1, duration / 1000.0)\n"
                   "    onMediaStatusChanged: { if (mediaStatus === MediaPlayer.EndOfMedia) bridge.mediaEnded(%1) }\n"
                   "    audioOutput: AudioOutput {\n"
                   "      objectName: \"pl%1audio\"\n"
                   "      onVolumeChanged: bridge.mediaVolume(%1, volume)\n"
                   "    }\n"
                   "  }\n"
                   "}")
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
                   "onTextChanged: bridge.textChanged(%2, text); "
                   "onEditingFinished: bridge.submit(%2) }")
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

// The QML MediaPlayer owned by a MEDIA_CONTROLLER node (spec/MEDIA.md).
// Direct visual child with the given objectName. (A lazily created item's QML
// `objectName` is reliable, but its QObject parent isn't always the visual
// parent, so search childItems() rather than findChild().)
QQuickItem *childItemNamed(QQuickItem *parent, const QString &name) {
    if (!parent) {
        return nullptr;
    }
    for (QQuickItem *c : parent->childItems()) {
        if (c->objectName() == name) {
            return c;
        }
    }
    return nullptr;
}

// The background/border rectangle for a node: the injected `pl<id>bg` (stacks)
// or a lazily created child Rectangle for plain containers. Null when the node
// is a positioner/control/leaf (those own their visuals or can't host a child).
QQuickItem *ensureBackground(uint32_t id, QQuickItem *item) {
    if (!item) {
        return nullptr;
    }
    const QString name = QStringLiteral("pl%1bg").arg(id);
    if (QQuickItem *existing = childItemNamed(item, name)) {
        return existing;
    }
    const char *cls = item->metaObject()->className();
    if (cls && (strstr(cls, "QQuickGrid") || strstr(cls, "QQuickScrollView") ||
                strstr(cls, "QQuickControl") || strstr(cls, "QQuickText") ||
                strstr(cls, "QQuickImage"))) {
        return nullptr; // positioner/control/leaf: no generic background slot
    }
    const QString qml =
        QStringLiteral(
            "import QtQuick 2.15\nRectangle { objectName: \"pl%1bg\"; "
            "color: \"transparent\"; z: -1 }")
            .arg(id);
    QQuickItem *bg = createComponent(g.engine, item, qml.toUtf8());
    if (bg) {
        QObject *anchors = bg->property("anchors").value<QObject *>();
        if (anchors) {
            anchors->setProperty("fill", QVariant::fromValue(item));
        }
    }
    return bg;
}

// TEXT_STYLE enum (pathland_core::text_style) -> a base pixel size + weight.
// Raw FONT_SIZE/FONT_WEIGHT modifiers are applied after it and override it.
bool textStyleFont(uint32_t code, int *px, QFont::Weight *weight) {
    switch (code) {
    case 0: *px = 34; *weight = QFont::Bold; return true;      // LargeTitle
    case 1: *px = 28; *weight = QFont::Bold; return true;      // Title
    case 2: *px = 22; *weight = QFont::DemiBold; return true;  // Title2
    case 3: *px = 20; *weight = QFont::DemiBold; return true;  // Title3
    case 4: *px = 17; *weight = QFont::DemiBold; return true;  // Headline
    case 5: *px = 15; *weight = QFont::Normal; return true;    // Subheadline
    case 6: *px = 14; *weight = QFont::Normal; return true;    // Body
    case 7: *px = 16; *weight = QFont::Normal; return true;    // Callout
    case 8: *px = 13; *weight = QFont::Normal; return true;    // Footnote
    case 9: *px = 12; *weight = QFont::Normal; return true;    // Caption
    case 10: *px = 11; *weight = QFont::Normal; return true;   // Caption2
    default: return false;
    }
}

// The shadow layer for a node (lazily created). Null for nodes that cannot host
// one (same restrictions as the background layer).
QQuickItem *ensureShadow(uint32_t id, QQuickItem *item) {
    if (!item) {
        return nullptr;
    }
    const QString name = QStringLiteral("pl%1shadow").arg(id);
    if (QQuickItem *existing = childItemNamed(item, name)) {
        return existing;
    }
    const char *cls = item->metaObject()->className();
    if (cls && (strstr(cls, "QQuickGrid") || strstr(cls, "QQuickScrollView") ||
                strstr(cls, "QQuickControl") || strstr(cls, "QQuickText") ||
                strstr(cls, "QQuickImage"))) {
        return nullptr;
    }
    const QString qml =
        QStringLiteral(
            "import QtQuick 2.15\nRectangle { objectName: \"pl%1shadow\"; z: -2; "
            "color: \"transparent\"; width: parent ? parent.width : 0; "
            "height: parent ? parent.height : 0 }")
            .arg(id);
    return createComponent(g.engine, item, qml.toUtf8());
}

void applyShadow(uint32_t id, QQuickItem *item) {
    auto cit = g.shadow_color.find(id);
    if (cit == g.shadow_color.end() || cit->second.alpha() == 0) {
        return;
    }
    QQuickItem *sh = ensureShadow(id, item);
    if (!sh) {
        return;
    }
    sh->setProperty("color", cit->second);
    sh->setProperty("radius", g.shadow_radius.count(id) ? g.shadow_radius[id] : 0.0);
    sh->setProperty("x", g.shadow_dx.count(id) ? g.shadow_dx[id] : 0.0);
    sh->setProperty("y", g.shadow_dy.count(id) ? g.shadow_dy[id] : 0.0);
}

// Whether an item renders text (TEXT/ICON both map to a QML Text item).
bool isTextual(QQuickItem *item) {
    const char *cls = item ? item->metaObject()->className() : nullptr;
    return cls && strstr(cls, "QQuickText") != nullptr;
}

// The nearest inherited foreground color for a node (walking its ancestors).
bool inheritedForeground(uint32_t id, uint32_t *out) {
    auto pit = g.parents.find(id);
    while (pit != g.parents.end()) {
        const uint32_t p = pit->second;
        auto it = g.fg_colors.find(p);
        if (it != g.fg_colors.end()) {
            *out = it->second;
            return true;
        }
        pit = g.parents.find(p);
    }
    return false;
}

// Apply an inherited foreground to a subtree's text items that don't own a color.
void applyForegroundDescendants(uint32_t id, bool have, uint32_t color) {
    auto it = g.children_of.find(id);
    if (it == g.children_of.end()) {
        return;
    }
    for (uint32_t child : it->second) {
        if (isTextual(findWidget(child)) && !g.fg_own.count(child)) {
            QQuickItem *t = findWidget(child);
            if (t) {
                t->setProperty("color",
                               have ? QColor::fromRgba(color) : QColor(Qt::black));
            }
        }
        applyForegroundDescendants(child, have, color);
    }
}

QObject *mediaPlayerFor(uint32_t controllerId) {
    QQuickItem *item = findWidget(controllerId);
    if (!item) {
        return nullptr;
    }
    return item->findChild<QObject *>(QStringLiteral("pl%1player").arg(controllerId));
}

// The QML AudioOutput owned by a MEDIA_CONTROLLER (Qt 6 routes volume/mute
// through AudioOutput, not the MediaPlayer itself).
QObject *mediaAudioFor(uint32_t controllerId) {
    QQuickItem *item = findWidget(controllerId);
    if (!item) {
        return nullptr;
    }
    return item->findChild<QObject *>(QStringLiteral("pl%1audio").arg(controllerId));
}

// The fit index for an allocated width: the largest candidate whose minWidth is
// <= width (thresholds ascending), clamped to the table (spec/PRIMITIVES.md).
int deriveFitIndex(const std::vector<double> &thresholds, double width) {
    if (thresholds.empty()) {
        return 0;
    }
    int index = 0;
    for (size_t i = 0; i < thresholds.size(); ++i) {
        if (thresholds[i] <= width) {
            index = static_cast<int>(i);
        } else {
            break;
        }
    }
    return index;
}

// Measure a fit slot and report FIT_CHANGED when the derived index changes.
void reportFit(uint32_t id, double width) {
    auto it = g.fit_queries.find(id);
    if (it == g.fit_queries.end() || !g.bridge) {
        return;
    }
    const int index = deriveFitIndex(it->second, width);
    auto prev = g.fit_index.find(id);
    if (prev != g.fit_index.end() && prev->second == index) {
        return;
    }
    g.fit_index[id] = index;
    g.bridge->fitChanged(id, index);
}

// ---------------------------------------------------------------------------
// Navigation adapter (spec DSL.md §4.5): a VSTACK/HSTACK slot carrying ROUTE
// renders as a StackView; the adapter reconciles its page stack by NAV_DEPTH.
// ---------------------------------------------------------------------------

bool isStackComponent(uint16_t component) {
    return component == PL_COMP_VSTACK || component == PL_COMP_HSTACK ||
           component == PL_COMP_LAZY_VSTACK || component == PL_COMP_LAZY_HSTACK ||
           component == PL_COMP_PROXY_VIEW;
}

// Layout-transparent nodes occupy no visual slot (spec/MEDIA.md): a
// MEDIA_CONTROLLER owns playback but renders no element.
bool isNonVisual(uint16_t component) {
    return component == PL_COMP_MEDIA_CONTROLLER;
}

// A vertical/horizontal layout host: a stack, a proxy view, or a composite
// control (which uses the same ColumnLayout wrapper).
bool isVerticalContainer(uint32_t id) {
    if (g.composite.count(id)) {
        return true;
    }
    const uint16_t c = g.components[id];
    return c == PL_COMP_VSTACK || c == PL_COMP_LAZY_VSTACK || c == PL_COMP_PROXY_VIEW;
}
bool isHorizontalContainer(uint32_t id) {
    const uint16_t c = g.components[id];
    return c == PL_COMP_HSTACK || c == PL_COMP_LAZY_HSTACK;
}
bool isContainer(uint32_t id) {
    return isVerticalContainer(id) || isHorizontalContainer(id);
}

// A component that consumes BackSpace (so it must not trigger native back).
bool isTextInput(uint16_t component) {
    return component == PL_COMP_TEXT_FIELD || component == PL_COMP_TEXT_EDITOR ||
           component == PL_COMP_DATE_PICKER;
}

// Renderer-defined modifier bitmask reported with key events.
uint32_t modifierMask(Qt::KeyboardModifiers m) {
    uint32_t out = 0;
    if (m & Qt::ShiftModifier) out |= 1;
    if (m & Qt::ControlModifier) out |= 2;
    if (m & Qt::AltModifier) out |= 4;
    if (m & Qt::MetaModifier) out |= 8;
    return out;
}

// Whether a parent item is a Qt Quick positioner (Row/Column/Grid). Positioners
// manage child placement themselves, so `anchors.fill` is rejected on their
// children — FILL there falls back to the positioner's default sizing.
bool isPositioner(QQuickItem *parent) {
    if (!parent) {
        return false;
    }
    const char *cls = parent->metaObject()->className();
    return cls && (strstr(cls, "QQuickRow") || strstr(cls, "QQuickColumn") ||
                   strstr(cls, "QQuickGrid"));
}

// Whether an item is a Qt Quick Layout (RowLayout/ColumnLayout/GridLayout).
bool isLayout(QQuickItem *item) {
    return item && qobject_cast<QQuickLayout *>(item) != nullptr;
}

// The layout that owns a stack node's children: a stack renders as a wrapper
// Item holding an inner ColumnLayout/RowLayout. Returns the inner layout, or
// `item` itself when it already is a layout.
QQuickItem *layoutOf(QQuickItem *item) {
    if (!item) {
        return nullptr;
    }
    if (isLayout(item)) {
        return item;
    }
    const auto kids = item->childItems();
    for (QQuickItem *k : kids) {
        if (isLayout(k)) {
            return k;
        }
    }
    return nullptr;
}

// The `Layout` attached object for an item (Layout.fillWidth/fillHeight/
// preferredWidth/…). Creates it on demand, as QML's `Layout.foo` would.
QQuickLayoutAttached *layoutAttached(QQuickItem *item) {
    if (!item) {
        return nullptr;
    }
    return qobject_cast<QQuickLayoutAttached *>(
        qmlAttachedPropertiesObject<QQuickLayout>(item, /*create=*/true));
}

// Pathland cross-axis alignment (0=Start,1=Center,2=End,3=Fill) applied to a
// stack's children via the Layout attached properties: Fill stretches the child
// on the stack's cross axis; Start/Center/End set the Layout alignment.
void applyStackAlignment(uint32_t parentId, QQuickItem *child) {
    if (!child) {
        return;
    }
    auto it = g.stack_align.find(parentId);
    if (it == g.stack_align.end()) {
        return;
    }
    const uint16_t comp = g.components[parentId];
    const bool vertical = comp == PL_COMP_VSTACK || comp == PL_COMP_LAZY_VSTACK;
    const float raw = f32(it->second);
    const int idx = (std::isfinite(raw) && std::floor(raw) == raw && raw >= 0.0f && raw <= 3.0f)
                        ? static_cast<int>(raw)
                        : static_cast<int>(it->second & 0xFF);
    QQuickLayoutAttached *a = layoutAttached(child);
    if (!a) {
        return;
    }
    if (idx == 3) { // Fill -> stretch on the cross axis
        if (vertical) {
            a->setFillWidth(true);
        } else {
            a->setFillHeight(true);
        }
        return;
    }
    Qt::Alignment align;
    if (vertical) { // Column: cross axis is horizontal
        align = idx == 0 ? Qt::AlignLeft : (idx == 1 ? Qt::AlignHCenter : Qt::AlignRight);
    } else { // Row: cross axis is vertical
        align = idx == 0 ? Qt::AlignTop : (idx == 1 ? Qt::AlignVCenter : Qt::AlignBottom);
    }
    a->setAlignment(align);
}

// Apply a stack's ALIGNMENT to every current child (after a change).
void reapplyStackAlignment(uint32_t parentId) {
    for (uint32_t childId : g.children_of[parentId]) {
        applyStackAlignment(parentId, findWidget(childId));
    }
}

// The nav container: a plain Item page stack. Qt Quick has no native
// navigation container, and StackView's push/pop are QML-callable-only (not
// invokable from C++ via QMetaObject), so the renderer manages its page cache
// itself: pages are children of this Item, only the top is visible.
QQuickItem *createNavContainer(QQuickItem *parent) {
    return createComponent(g.engine, parent, "import QtQuick 2.15\nItem { clip: true }");
}

// Promote a slot's stack wrapper to the nav container (first non-empty ROUTE).
// The diff inserts the destination before ROUTE arrives, so the wrapper's inner
// layout may already own the destination — detach it (and the layout) before the
// swap so the app-owned destination survives the wrapper's deletion.
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
    // Fill the slot's parent when it is a plain container (a positioner like
    // Row/Column rejects anchors on its children and sizes them itself).
    if (old->parentItem() && !isPositioner(old->parentItem())) {
        QObject *anchors = container->property("anchors").value<QObject *>();
        if (anchors) {
            anchors->setProperty("fill", QVariant::fromValue(old->parentItem()));
        }
    }
    for (QQuickItem *kid : old->childItems()) {
        const auto grandkids = kid->childItems();
        for (QQuickItem *gk : grandkids) {
            gk->setParentItem(nullptr);
        }
        g.pending_fill.erase(kid);
        kid->setParentItem(nullptr);
        kid->deleteLater();
    }
    g.pending_fill.erase(old);
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
    g.pending_fill.erase(dest);
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
            placeDestination(page, container); // page fills the container
        } else {
            placeDestination(page, container);
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
            placeDestination(page, container); // page fills the container
        } else {
            placeDestination(page, container);
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
        // Hover enter/leave ride the same pointer-move listener (spec/EVENTS.md).
        handlers += QStringLiteral("onEntered: bridge.hover(%1, true)\n").arg(id);
        handlers += QStringLiteral("onExited: bridge.hover(%1, false)\n").arg(id);
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

// A semantic control with children renders in Composite Override Mode: the
// native chrome is suppressed and the app's child tree is laid out in its place
// (spec/PRIMITIVES.md §2). The ButtonStyle body is ordinary child content, so
// this is what makes styled buttons look like the web/GTK demos. The shell is
// the same Item+ColumnLayout wrapper a VStack uses (so it hugs its content and
// Layout stretch works); raw pointer listeners re-attach to it.
void promoteComposite(uint32_t id) {
    if (g.composite.count(id)) {
        return;
    }
    QQuickItem *old = findWidget(id);
    if (!old) {
        return;
    }
    QQuickItem *parent = old->parentItem();
    const QSizeF size = old->size();
    const QPointF pos(old->x(), old->y());
    const bool visible = old->isVisible();
    QQuickItem *item =
        createComponent(g.engine, parent, qmlFor(PL_COMP_VSTACK, id).toUtf8());
    if (!item) {
        return;
    }
    // The old native control's children are its internal chrome (background /
    // contentItem / indicator), not app content: discard them with the control.
    item->setWidth(size.width());
    item->setHeight(size.height());
    item->setX(pos.x());
    item->setY(pos.y());
    item->setVisible(visible);
    // Preserve root fill / any deferred FILL the old widget carried.
    if (parent == g.root) {
        QObject *anchors = item->property("anchors").value<QObject *>();
        if (anchors) {
            anchors->setProperty("fill", QVariant::fromValue(g.root));
        }
    }
    auto pf = g.pending_fill.find(old);
    if (pf != g.pending_fill.end()) {
        g.pending_fill[item] = pf->second;
        g.pending_fill.erase(pf);
    }
    old->setParentItem(nullptr);
    old->deleteLater();
    g.widgets[id] = item;
    g.composite.insert(id);
    // The old widget's listener overlay went with it; re-attach from the mask.
    auto it = g.node_listeners.find(id);
    if (it != g.node_listeners.end()) {
        attachPointerArea(id, it->second);
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

// WIDTH/HEIGHT special values: -1 = FILL (stretch), -2 = HUG (implicit size).
// Positive values set a fixed size.
// Apply a pending FILL once a widget gains its final parent. In a Qt Quick
// Layout (RowLayout/ColumnLayout) FILL stretches via the Layout attached
// properties; elsewhere it is anchors.fill. Skipped for plain positioners
// (Grid rejects anchors on its children).
void flushFill(QQuickItem *item, QQuickItem *container) {
    auto it = g.pending_fill.find(item);
    if (it == g.pending_fill.end()) {
        return;
    }
    if (container) {
        if (isLayout(container)) {
            QQuickLayoutAttached *a = layoutAttached(item);
            if (a) {
                if (it->second & 1u) {
                    a->setFillWidth(true);
                }
                if (it->second & 2u) {
                    a->setFillHeight(true);
                }
            }
        } else if (!isPositioner(container)) {
            QObject *anchors = item->property("anchors").value<QObject *>();
            if (anchors) {
                if (it->second & 1u) {
                    anchors->setProperty("width", QVariant::fromValue(container));
                }
                if (it->second & 2u) {
                    anchors->setProperty("height", QVariant::fromValue(container));
                }
            }
        }
    }
    g.pending_fill.erase(it);
}

void applySize(QQuickItem *item, const char *dimension, float value) {
    if (value == -1.0f) {
        // FILL: stretch. In a Layout this is `Layout.fillWidth/fillHeight` and
        // works as soon as the child joins it. Elsewhere anchors.fill the final
        // parent once known; defer when the current parent is the create-time
        // placeholder (contentItem) or a plain positioner.
        QQuickItem *parent = item->parentItem();
        if (isLayout(parent)) {
            QQuickLayoutAttached *a = layoutAttached(item);
            if (a) {
                if (*dimension == 'w') {
                    a->setFillWidth(true);
                } else {
                    a->setFillHeight(true);
                }
            }
            return;
        }
        if (parent && parent != g.root && !isPositioner(parent)) {
            QObject *anchors = item->property("anchors").value<QObject *>();
            if (anchors) {
                anchors->setProperty(dimension, QVariant::fromValue(parent));
            }
        } else {
            g.pending_fill[item] |= (*dimension == 'w') ? 1u : 2u;
        }
        return;
    }
    if (value == -2.0f) {
        // HUG_CONTENT: implicit size. In a Layout, clear any fixed preferred size.
        QQuickItem *parent = item->parentItem();
        if (isLayout(parent)) {
            QQuickLayoutAttached *a = layoutAttached(item);
            if (a) {
                if (*dimension == 'w') {
                    a->setPreferredWidth(-1);
                } else {
                    a->setPreferredHeight(-1);
                }
            }
        }
        return;
    }
    // Fixed size: preferred size in a Layout, explicit width/height elsewhere.
    QQuickItem *parent = item->parentItem();
    if (isLayout(parent)) {
        QQuickLayoutAttached *a = layoutAttached(item);
        if (a) {
            if (*dimension == 'w') {
                a->setPreferredWidth(value);
            } else {
                a->setPreferredHeight(value);
            }
        }
    } else {
        item->setProperty(dimension, static_cast<double>(value));
    }
}

// Apply a stack's padding to its inner layout (anchors.margins / edge margins)
// and, for the uniform case, the wrapper's `plPad` (which drives the wrapper's
// implicit size so it hugs its padded content). No-op for non-stack widgets.
void applyPadding(QQuickItem *item, const char *edge, float value) {
    QQuickItem *lay = layoutOf(item);
    if (!lay) {
        return;
    }
    QObject *anchors = lay->property("anchors").value<QObject *>();
    if (!anchors) {
        return;
    }
    if (!edge) {
        item->setProperty("plPad", static_cast<double>(value));
        anchors->setProperty("margins", static_cast<double>(value));
    } else {
        anchors->setProperty(edge, static_cast<double>(value));
    }
}

// Apply a font property via the `font` QFont (QObject::setProperty does not
// resolve dotted paths like "font.pixelSize").
void applyFont(QQuickItem *item, uint16_t prop, uint32_t value) {
    QVariant fv = item->property("font");
    QFont font = fv.value<QFont>();
    switch (prop) {
    case PL_PROP_FONT_SIZE:
        font.setPixelSize(static_cast<int>(f32(value)));
        break;
    case PL_PROP_FONT_WEIGHT:
        font.setWeight(static_cast<QFont::Weight>(weightFor(f32(value))));
        break;
    default:
        return;
    }
    item->setProperty("font", QVariant::fromValue(font));
}

void applyProperty(uint32_t id, uint16_t prop, uint8_t value_type, uint32_t value) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return;
    }
    switch (prop) {
    case PL_PROP_SPACING: {
        QQuickItem *lay = layoutOf(item);
        if (lay) {
            lay->setProperty("spacing", static_cast<double>(f32(value)));
        }
        break;
    }
    case PL_PROP_ALIGNMENT:
        g.stack_align[id] = value;
        reapplyStackAlignment(id);
        break;
    case PL_PROP_CONTENT_MARGINS:
    case PL_PROP_PADDING:
        applyPadding(item, nullptr, f32(value));
        break;
    case PL_PROP_PADDING_TOP:
        applyPadding(item, "topMargin", f32(value));
        break;
    case PL_PROP_PADDING_RIGHT:
        applyPadding(item, "rightMargin", f32(value));
        break;
    case PL_PROP_PADDING_BOTTOM:
        applyPadding(item, "bottomMargin", f32(value));
        break;
    case PL_PROP_PADDING_LEFT:
        applyPadding(item, "leftMargin", f32(value));
        break;
    case PL_PROP_WIDTH:
        applySize(item, "width", f32(value));
        break;
    case PL_PROP_HEIGHT:
        applySize(item, "height", f32(value));
        break;
    case PL_PROP_COLOR:
        if (isTextual(item)) {
            item->setProperty("color", QColor::fromRgba(value));
            g.fg_own.insert(id);
        } else {
            // CSS `color` inheritance: store on the container and push down.
            g.fg_colors[id] = value;
            applyForegroundDescendants(id, true, value);
        }
        break;
    case PL_PROP_FONT_SIZE:
        applyFont(item, prop, value);
        break;
    case PL_PROP_FONT_WEIGHT:
        applyFont(item, prop, value);
        break;
    case PL_PROP_TEXT_STYLE: {
        // ENUM values ride as F32 bit patterns (spec/OPCODE.md).
        const uint32_t code = static_cast<uint32_t>(f32(value) + 0.5f);
        int px = 0;
        QFont::Weight weight = QFont::Normal;
        if (isTextual(item) && textStyleFont(code, &px, &weight)) {
            QFont font = item->property("font").value<QFont>();
            font.setPixelSize(px);
            font.setWeight(weight);
            item->setProperty("font", QVariant::fromValue(font));
        }
        break;
    }
    case PL_PROP_SHADOW_COLOR:
        g.shadow_color[id] = QColor::fromRgba(value);
        applyShadow(id, item);
        break;
    case PL_PROP_SHADOW_RADIUS:
        g.shadow_radius[id] = static_cast<double>(f32(value));
        applyShadow(id, item);
        break;
    case PL_PROP_SHADOW_X:
        g.shadow_dx[id] = static_cast<double>(f32(value));
        applyShadow(id, item);
        break;
    case PL_PROP_SHADOW_Y:
        g.shadow_dy[id] = static_cast<double>(f32(value));
        applyShadow(id, item);
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
        g.node_listeners[id] = value;
        attachPointerArea(id, value);
        break;
    case PL_PROP_ACTION_ID:
    case PL_PROP_BINDING_ID:
        g.gated_ids.insert(id);
        break;
    case PL_PROP_CLIPS_TO_BOUNDS:
        item->setProperty("clip", value != 0);
        break;
    case PL_PROP_BACKGROUND: {
        // A Rectangle-backed node is its own fill; a container gets a background
        // Rectangle behind its content (CSS background).
        QQuickItem *target =
            item->property("radius").isValid() ? item : ensureBackground(id, item);
        if (target) {
            target->setProperty("color", QColor::fromRgba(value));
        }
        break;
    }
    case PL_PROP_BORDER_WIDTH:
    case PL_PROP_BORDER_COLOR:
    case PL_PROP_BORDER_RADIUS: {
        QQuickItem *target =
            item->property("radius").isValid() ? item : ensureBackground(id, item);
        if (!target) {
            break;
        }
        if (prop == PL_PROP_BORDER_RADIUS) {
            target->setProperty("radius", static_cast<double>(f32(value)));
        } else {
            QObject *border = target->property("border").value<QObject *>();
            if (border) {
                if (prop == PL_PROP_BORDER_WIDTH) {
                    border->setProperty("width", static_cast<int>(f32(value)));
                } else {
                    border->setProperty("color", QColor::fromRgba(value));
                }
            }
        }
        break;
    }
    case PL_PROP_TEXT_ALIGNMENT:
        item->setProperty("horizontalAlignment", alignmentFor(f32(value)));
        break;
    case PL_PROP_LINE_LIMIT:
        item->setProperty("maximumLineCount", static_cast<int>(value));
        break;
    case PL_PROP_TRUNCATION:
        item->setProperty("elide", value != 0 ? 2 /* ElideRight */ : 0 /* ElideNone */);
        break;
    case PL_PROP_CONTENT_MODE:
        if (item->property("fillMode").isValid()) {
            item->setProperty("fillMode",
                              f32(value) >= 0.5f ? 2 /* PreserveAspectCrop */
                                                 : 1 /* PreserveAspectFit */);
        }
        break;
    case PL_PROP_OFFSET_X:
    case PL_PROP_POSITION_X:
        item->setProperty("x", static_cast<double>(f32(value)));
        break;
    case PL_PROP_OFFSET_Y:
    case PL_PROP_POSITION_Y:
        item->setProperty("y", static_cast<double>(f32(value)));
        break;
    case PL_PROP_MIN_WIDTH:
    case PL_PROP_IDEAL_WIDTH:
    case PL_PROP_MAX_WIDTH:
    case PL_PROP_MIN_HEIGHT:
    case PL_PROP_IDEAL_HEIGHT:
    case PL_PROP_MAX_HEIGHT: {
        QQuickLayoutAttached *a = layoutAttached(item);
        if (a) {
            const double v = static_cast<double>(f32(value));
            switch (prop) {
            case PL_PROP_MIN_WIDTH: a->setMinimumWidth(v); break;
            case PL_PROP_IDEAL_WIDTH: a->setPreferredWidth(v); break;
            case PL_PROP_MAX_WIDTH: a->setMaximumWidth(v); break;
            case PL_PROP_MIN_HEIGHT: a->setMinimumHeight(v); break;
            case PL_PROP_IDEAL_HEIGHT: a->setPreferredHeight(v); break;
            case PL_PROP_MAX_HEIGHT: a->setMaximumHeight(v); break;
            }
        }
        break;
    }
    case PL_PROP_GRID_COLUMNS:
        if (item->property("columns").isValid()) {
            item->setProperty("columns", static_cast<int>(f32(value)));
        }
        break;
    // ── Media transport (spec/MEDIA.md) ────────────────────────────────
    case PL_PROP_PLAYBACK_STATE: {
        g.media_play_state[id] = value;
        if (QObject *player = mediaPlayerFor(id)) {
            QMetaObject::invokeMethod(player, value != 0 ? "play" : "pause");
        }
        break;
    }
    case PL_PROP_MEDIA_POSITION:
        if (QObject *player = mediaPlayerFor(id)) {
            QMetaObject::invokeMethod(
                player, "setPosition",
                Q_ARG(qint64, static_cast<qint64>(f32(value) * 1000.0f)));
        }
        break;
    case PL_PROP_MEDIA_VOLUME:
        if (QObject *audio = mediaAudioFor(id)) {
            audio->setProperty("volume", static_cast<double>(f32(value)));
        }
        break;
    case PL_PROP_MEDIA_MUTED:
        if (QObject *audio = mediaAudioFor(id)) {
            audio->setProperty("muted", value != 0);
        }
        break;
    case PL_PROP_MEDIA_LOOP:
        if (QObject *player = mediaPlayerFor(id)) {
            player->setProperty("loops", value != 0 ? -1 : 1); // -1 = Infinite
        }
        break;
    case PL_PROP_MEDIA_RATE:
        if (QObject *player = mediaPlayerFor(id)) {
            player->setProperty("playbackRate", static_cast<double>(f32(value)));
        }
        break;
    case PL_PROP_MEDIA_CONTROLLER_ID: {
        QQuickItem *out = childItemNamed(item, QStringLiteral("pl%1out").arg(id));
        QObject *player = mediaPlayerFor(value);
        if (out && player) {
            out->setProperty("source", QVariant::fromValue(player));
        }
        break;
    }
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
    case PL_PROP_FIT_QUERY: {
        std::vector<double> thresholds;
        for (const QString &part : value.split(',', Qt::SkipEmptyParts)) {
            bool ok = false;
            const double v = part.trimmed().toDouble(&ok);
            if (ok) {
                thresholds.push_back(v);
            }
        }
        g.fit_queries[id] = thresholds;
        g.fit_index.erase(id);
        reportFit(id, item->width());
        break;
    }
    case PL_PROP_ICON_NAME:
        item->setProperty("text", iconGlyph(value));
        break;
    case PL_PROP_MEDIA_SOURCE:
        if (QObject *player = mediaPlayerFor(id)) {
            player->setProperty("source", resolveAsset(value, g.asset_root));
            // Re-apply the app's declared transport state: a play() that landed
            // before the source was known would otherwise stay silent.
            auto st = g.media_play_state.find(id);
            if (st != g.media_play_state.end()) {
                QMetaObject::invokeMethod(player, st->second != 0 ? "play" : "pause");
            }
        }
        break;
    case PL_PROP_IMAGE_SOURCE:
        item->setProperty("source", resolveAsset(value, g.asset_root));
        break;
    case PL_PROP_FONT_FAMILY: {
        QVariant fv = item->property("font");
        QFont font = fv.value<QFont>();
        font.setFamily(value);
        item->setProperty("font", QVariant::fromValue(font));
        break;
    }
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
    g.fg_colors.erase(id);
    g.fg_own.erase(id);
    if (isTextual(item)) {
        uint32_t inherited = 0;
        item->setProperty("color", inheritedForeground(id, &inherited)
                                       ? QColor::fromRgba(inherited)
                                       : QColor(Qt::black));
    } else {
        uint32_t inherited = 0;
        const bool have = inheritedForeground(id, &inherited);
        applyForegroundDescendants(id, have, inherited);
    }
    g.stack_align.erase(id);
    item->setProperty("opacity", 1.0);
    item->setProperty("visible", true);
    if (QQuickItem *lay = layoutOf(item)) {
        lay->setProperty("spacing", 0.0);
        QObject *anchors = lay->property("anchors").value<QObject *>();
        if (anchors) {
            anchors->setProperty("margins", 0.0);
            anchors->setProperty("topMargin", 0.0);
            anchors->setProperty("rightMargin", 0.0);
            anchors->setProperty("bottomMargin", 0.0);
            anchors->setProperty("leftMargin", 0.0);
        }
        const auto kids = lay->childItems();
        for (QQuickItem *k : kids) {
            if (QQuickLayoutAttached *a = layoutAttached(k)) {
                a->setFillWidth(false);
                a->setFillHeight(false);
                a->setPreferredWidth(-1);
                a->setPreferredHeight(-1);
            }
        }
    }
    // Reset the renderer-owned background/border layer.
    QQuickItem *bg = childItemNamed(item, QStringLiteral("pl%1bg").arg(id));
    if (!bg && item->property("radius").isValid()) {
        bg = item;
    }
    if (bg) {
        bg->setProperty("color", QColor(Qt::transparent));
        bg->setProperty("radius", 0.0);
        if (QObject *border = bg->property("border").value<QObject *>()) {
            border->setProperty("width", 0);
        }
    }
    g.shadow_color.erase(id);
    g.shadow_radius.erase(id);
    g.shadow_dx.erase(id);
    g.shadow_dy.erase(id);
    if (QQuickItem *sh = childItemNamed(item, QStringLiteral("pl%1shadow").arg(id))) {
        sh->setProperty("color", QColor(Qt::transparent));
    }
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

    if (isNonVisual(g.components[childId])) {
        // Layout-transparent: it exists (owns playback), but no visual slot.
        // CREATE_NODE parented it to the content item as a placeholder; detach.
        if (QQuickItem *nv = findWidget(childId)) {
            nv->setParentItem(nullptr);
        }
        return;
    }
    if (g.components[parentId] == PL_COMP_PICKER) {
        rebuildPickerModel(parentId); // options drive the model, not the visual tree
        return;
    }
    if (g.nav_slots.count(parentId)) {
        reconcileNav(parentId); // destination subtree swap -> refresh/replace the top page
        return; // the destination is placed by the nav adapter, not visually here
    }

    // GRID_ROW (spec/PRIMITIVES.md): a row's cells are flattened into the parent
    // Grid so columns align across rows. The row itself renders nothing.
    if (g.components[childId] == PL_COMP_GRID_ROW &&
        g.components[parentId] == PL_COMP_GRID) {
        g.grid_of[childId] = parentId;
        if (QQuickItem *row = findWidget(childId)) {
            QQuickItem *grid = findWidget(parentId);
            for (QQuickItem *cell : row->childItems()) {
                cell->setParentItem(grid); // cells already present join the grid
            }
            row->setParentItem(nullptr);
        }
        return;
    }

    // A GRID_ROW's cell attaches to the row's grid (flattened), at its global
    // row-major cell index.
    uint32_t visualParentId = parentId;
    uint32_t visualIndex = index;
    if (g.components[parentId] == PL_COMP_GRID_ROW) {
        auto git = g.grid_of.find(parentId);
        if (git != g.grid_of.end()) {
            visualParentId = git->second;
            int n = 0;
            for (uint32_t c : g.children_of[visualParentId]) {
                if (c == parentId) {
                    break;
                }
                n += (g.components[c] == PL_COMP_GRID_ROW)
                         ? static_cast<int>(g.children_of[c].size())
                         : 1;
            }
            const auto &rowKids = g.children_of[parentId];
            auto lit = std::find(rowKids.begin(), rowKids.end(), childId);
            const int local = (lit == rowKids.end()) ? 0 : static_cast<int>(lit - rowKids.begin());
            visualIndex = static_cast<uint32_t>(n + local);
        }
    }

    if (g.components[visualParentId] == PL_COMP_BUTTON && !isNonVisual(g.components[childId])) {
        promoteComposite(visualParentId); // children => composite override mode
    }

    QQuickItem *parent = findWidget(visualParentId);
    if (!parent) {
        return;
    }
    // Stacks attach children to their inner layout (so `Layout` stretch works);
    // a ScrollView attaches to its contentItem (so content sizes/scrolls it).
    QQuickItem *attach = parent;
    if (isStackComponent(g.components[visualParentId]) || g.composite.count(visualParentId)) {
        attach = layoutOf(parent);
        if (!attach) {
            attach = parent;
        }
    } else if (g.components[visualParentId] == PL_COMP_SCROLL) {
        QVariant ci = parent->property("contentItem");
        if (ci.isValid()) {
            if (QQuickItem *c = ci.value<QQuickItem *>()) {
                attach = c;
            }
        }
    }

    child->setParentItem(attach);
    // Window content (pseudo-parent 0): roots fill the window (GTK's window
    // child expands).
    if (parentId == 0) {
        QObject *anchors = child->property("anchors").value<QObject *>();
        if (anchors) {
            anchors->setProperty("fill", QVariant::fromValue(g.root));
        }
    }
    applyStackAlignment(parentId, child);
    // Default stack alignment is stretch (CSS `align-items:stretch`): with no
    // explicit ALIGNMENT, a child fills the container's cross axis.
    if (isContainer(visualParentId) && isLayout(attach) &&
        !g.stack_align.count(visualParentId)) {
        if (QQuickLayoutAttached *a = layoutAttached(child)) {
            if (isVerticalContainer(visualParentId)) {
                a->setFillWidth(true);
            } else {
                a->setFillHeight(true);
            }
        }
    }
    // A Spacer expands along the container's main axis.
    if (g.components[childId] == PL_COMP_SPACER && isContainer(visualParentId) &&
        isLayout(attach)) {
        if (QQuickLayoutAttached *a = layoutAttached(child)) {
            if (isVerticalContainer(visualParentId)) {
                a->setFillHeight(true);
            } else {
                a->setFillWidth(true);
            }
        }
    }
    // A ScrollView fills its parent by default (GTK: halign/valign = Fill), so a
    // scroll content area stretches instead of collapsing to its content. A
    // SIZE_THAT_FITS slot is likewise greedy on both axes (spec/LAYOUT.md).
    if (g.components[childId] == PL_COMP_SCROLL ||
        g.components[childId] == PL_COMP_SIZE_THAT_FITS) {
        if (isLayout(attach)) {
            QQuickLayoutAttached *a = layoutAttached(child);
            if (a) {
                a->setFillWidth(true);
                a->setFillHeight(true);
            }
        } else if (!isPositioner(attach)) {
            QObject *anchors = child->property("anchors").value<QObject *>();
            if (anchors) {
                anchors->setProperty("fill", QVariant::fromValue(attach));
            }
        }
    }
    // Inherit a container's foreground color (CSS `color` inheritance).
    if (isTextual(child) && !g.fg_own.count(childId)) {
        uint32_t color = 0;
        if (inheritedForeground(childId, &color)) {
            child->setProperty("color", QColor::fromRgba(color));
        }
    }
    // A fit slot's one selected child occupies the whole slot.
    if (g.components[parentId] == PL_COMP_SIZE_THAT_FITS) {
        QObject *anchors = child->property("anchors").value<QObject *>();
        if (anchors) {
            anchors->setProperty("fill", QVariant::fromValue(attach));
        }
    }
    flushFill(child, attach);
    if (visualIndex == UINT32_MAX) {
        return; // append (already last)
    }
    QList<QQuickItem *> siblingsList = attach->childItems();
    siblingsList.removeAll(child);
    if (static_cast<int>(visualIndex) < siblingsList.size()) {
        QQuickItem *after = siblingsList.at(static_cast<int>(visualIndex));
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
    g.pending_fill.erase(item);
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
    g.stack_align.erase(id);
    g.fit_queries.erase(id);
    g.fit_index.erase(id);
    g.grid_of.erase(id);
    g.fg_colors.erase(id);
    g.fg_own.erase(id);
    g.shadow_color.erase(id);
    g.shadow_radius.erase(id);
    g.shadow_dx.erase(id);
    g.shadow_dy.erase(id);
    g.composite.erase(id);
    g.node_listeners.erase(id);
    if (g.focused_id == id) {
        g.focused_id = 0;
        g.focused_component = 0;
    }
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
        if (ev->type() != QEvent::KeyPress && ev->type() != QEvent::KeyRelease) {
            return QObject::eventFilter(obj, ev);
        }
        auto *ke = static_cast<QKeyEvent *>(ev);
        const bool down = ev->type() == QEvent::KeyPress;
        // Escape is always native back (global, never node-keyed).
        if (down && ke->key() == Qt::Key_Escape) {
            if (g.bridge) {
                g.bridge->navigateBack();
            }
            return true;
        }
        // BackSpace is native back unless a text input owns the key.
        if (down && ke->key() == Qt::Key_Backspace && !isTextInput(g.focused_component)) {
            if (g.bridge) {
                g.bridge->navigateBack();
            }
            return true;
        }
        // Report KEY_DOWN/KEY_UP for the focused node (gated by its listener bits
        // inside the Bridge); the event still reaches the focused control.
        if (g.focused_id != 0 && g.bridge) {
            g.bridge->key(g.focused_id, ke->key(), modifierMask(ke->modifiers()),
                          ke->isAutoRepeat(), down);
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
    g.bridge->listeners = &g.node_listeners;
    g.bridge->suppress = &g.suppress;

    g.engine = new QQmlEngine(g.app);
    // Qt adds the executable's directory to QML's implicit import path. A
    // `cargo test` binary lives in `target/debug/deps`, which can hold tens of
    // thousands of files; resolving a QML plugin then scans that directory and
    // can take minutes (the headless-test "hang"). We ship no QML there, so
    // drop it and keep only the genuine Qt import paths.
    {
        QStringList imports = g.engine->importPathList();
        imports.removeAll(QCoreApplication::applicationDirPath());
        g.engine->setImportPathList(imports);
    }
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
                if (component == PL_COMP_SIZE_THAT_FITS) {
                    const uint32_t id = c.a;
                    QObject::connect(item, &QQuickItem::widthChanged, item,
                                     [id, item]() { reportFit(id, item->width()); });
                }
                if (isTextInput(component)) {
                    const uint32_t id = c.a;
                    QObject::connect(item, &QQuickItem::activeFocusChanged, item, [id, item]() {
                        const bool focused = item->hasActiveFocus();
                        if (focused) {
                            g.focused_id = id;
                            g.focused_component = g.components[id];
                        } else if (g.focused_id == id) {
                            g.focused_id = 0;
                            g.focused_component = 0;
                        }
                        if (g.bridge) {
                            g.bridge->focusChanged(id, focused);
                            g.bridge->editingChanged(id, focused);
                        }
                    });
                }
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
        case PLQT_SET_DATE: {
            // A DATE_PICKER value: days since the Unix epoch + millis of day.
            // The renderer owns the formatting (QML approximation of a picker).
            if (QQuickItem *item = findWidget(c.a)) {
                const qint64 days = static_cast<int32_t>(c.b);
                const QDate date = QDate(1970, 1, 1).addDays(days);
                QString text = date.toString(QStringLiteral("yyyy-MM-dd"));
                const int millis = static_cast<int>(c.c);
                if (millis > 0) {
                    const int hours = millis / 3'600'000;
                    const int minutes = (millis / 60'000) % 60;
                    text += QStringLiteral(" %1:%2")
                                .arg(hours, 2, 10, QChar('0'))
                                .arg(minutes, 2, 10, QChar('0'));
                }
                g.suppress = true;
                item->setProperty("text", text);
                g.suppress = false;
            }
            break;
        }
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
    g.stack_align.clear();
    g.listener_masks.clear();
    g.mouse_areas.clear();
    g.pending_fill.clear();
    g.media_play_state.clear();
    g.fit_queries.clear();
    g.fit_index.clear();
    g.grid_of.clear();
    g.fg_colors.clear();
    g.fg_own.clear();
    g.shadow_color.clear();
    g.shadow_radius.clear();
    g.shadow_dx.clear();
    g.shadow_dy.clear();
    g.composite.clear();
    g.node_listeners.clear();
    g.focused_id = 0;
    g.focused_component = 0;
    g.apply_count = 0;
    g.last_text_len = 0;
    g.last_text[0] = '\0';
}

void pathland_qt_layer_set_asset_root(const char *root) {
    g.asset_root = root ? QString::fromUtf8(root) : QString();
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
    if (!item) {
        return 0;
    }
    // A stack's children live in its inner layout (the wrapper holds the layout).
    QQuickItem *lay = layoutOf(item);
    return static_cast<uint32_t>((lay ? lay : item)->childItems().size());
}

uint32_t pathland_qt_layer_nav_depth(uint32_t slot) {
    auto it = g.nav_pages.find(slot);
    return it == g.nav_pages.end() ? 0 : static_cast<uint32_t>(it->second.size());
}

// Whether the top page is sized to fill its nav container (the content-area fix).
uint32_t pathland_qt_layer_nav_page_fills(uint32_t slot) {
    auto it = g.nav_pages.find(slot);
    if (it == g.nav_pages.end() || it->second.empty()) {
        return 0;
    }
    QQuickItem *page = it->second.back();
    QObject *anchors = page->property("anchors").value<QObject *>();
    if (!anchors) {
        return 0;
    }
    return anchors->property("fill").value<QQuickItem *>() != nullptr ? 1 : 0;
}

// Test hook: whether a widget's Layout attached fill flag is explicitly set
// (which==0 -> fillWidth, else fillHeight). Proves FILL stretch wiring.
uint32_t pathland_qt_layer_widget_fill(uint32_t id, uint32_t which) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return 0;
    }
    QQuickLayoutAttached *a = layoutAttached(item);
    if (!a) {
        return 0;
    }
    return which == 0 ? (a->isFillWidthSet() && a->fillWidth() ? 1u : 0u)
                      : (a->isFillHeightSet() && a->fillHeight() ? 1u : 0u);
}

// Test hook: a widget's Layout attached preferred size (which==0 width, 1 height).
double pathland_qt_layer_widget_preferred(uint32_t id, uint32_t which) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return -1;
    }
    QQuickLayoutAttached *a = layoutAttached(item);
    if (!a) {
        return -1;
    }
    return which == 0 ? a->preferredWidth() : a->preferredHeight();
}

// Test hook: a widget's current width (0) or height (1). Offscreen (no window
// render) sizes are the anchors/layout result, proving fill geometry.
double pathland_qt_layer_widget_size(uint32_t id, uint32_t which) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return -1;
    }
    return which == 0 ? item->width() : item->height();
}

// Test hook: whether a widget's anchors.fill is bound to the window contentItem
// (roots are anchored to fill the window — the "sliver" fix).
uint32_t pathland_qt_layer_anchors_fill(uint32_t id) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return 0;
    }
    QObject *anchors = item->property("anchors").value<QObject *>();
    if (!anchors) {
        return 0;
    }
    QVariant fill = anchors->property("fill");
    QQuickItem *target = fill.value<QQuickItem *>();
    return target == g.root ? 1 : 0;
}
void pathland_qt_layer_report_fit(uint32_t id, double width) { reportFit(id, width); }

namespace {
QQuickItem *backgroundOf(uint32_t id) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return nullptr;
    }
    if (QQuickItem *bg = childItemNamed(item, QStringLiteral("pl%1bg").arg(id))) {
        return bg;
    }
    return item->property("radius").isValid() ? item : nullptr;
}
} // namespace

uint32_t pathland_qt_layer_background_color(uint32_t id) {
    QQuickItem *bg = backgroundOf(id);
    if (!bg) {
        return 0;
    }
    return bg->property("color").value<QColor>().rgba();
}

uint32_t pathland_qt_layer_text_color(uint32_t id) {
    QQuickItem *item = findWidget(id);
    return item ? item->property("color").value<QColor>().rgba() : 0;
}

int pathland_qt_layer_text_pixel_size(uint32_t id) {
    QQuickItem *item = findWidget(id);
    return item ? item->property("font").value<QFont>().pixelSize() : -1;
}

uint32_t pathland_qt_layer_shadow_color(uint32_t id) {
    QQuickItem *item = findWidget(id);
    QQuickItem *sh = childItemNamed(item, QStringLiteral("pl%1shadow").arg(id));
    return sh ? sh->property("color").value<QColor>().rgba() : 0;
}

double pathland_qt_layer_shadow_x(uint32_t id) {
    QQuickItem *item = findWidget(id);
    QQuickItem *sh = childItemNamed(item, QStringLiteral("pl%1shadow").arg(id));
    return sh ? sh->property("x").toDouble() : 0.0;
}

double pathland_qt_layer_background_radius(uint32_t id) {
    QQuickItem *bg = backgroundOf(id);
    return bg ? bg->property("radius").toDouble() : -1.0;
}

uint32_t pathland_qt_layer_background_border_width(uint32_t id) {
    QQuickItem *bg = backgroundOf(id);
    if (!bg) {
        return 0;
    }
    QObject *border = bg->property("border").value<QObject *>();
    return border ? static_cast<uint32_t>(border->property("width").toInt()) : 0;
}

int pathland_qt_layer_fit_index(uint32_t id) {
    auto it = g.fit_index.find(id);
    return it == g.fit_index.end() ? -1 : it->second;
}

uint32_t pathland_qt_layer_video_source(uint32_t id) {
    QQuickItem *item = findWidget(id);
    if (!item) {
        return 0;
    }
    QQuickItem *out = childItemNamed(item, QStringLiteral("pl%1out").arg(id));
    if (!out) {
        return 0;
    }
    return out->property("source").value<QObject *>() ? 1 : 0;
}


// Test hook: force one synchronous render pass (grabWindow) so anchors/layout
// geometry is computed without entering the event loop. Offscreen + software
// backend; the window must be shown once to get a real size.
int pathland_qt_layer_render_once(void) {
    if (!g.window) {
        return 0;
    }
    if (!g.window->isVisible()) {
        g.window->show();
        // Let the window expose and run a layout pass before grabbing.
        QCoreApplication::processEvents();
        QCoreApplication::processEvents();
    }
    QImage img = g.window->grabWindow();
    return img.isNull() ? 0 : 1;
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
    // A stack's spacing/padding live on its inner layout (the wrapper holds it).
    QQuickItem *lay = layoutOf(item);
    QQuickItem *target = lay ? lay : item;
    const QByteArray bytes = target->property(prop).toString().toUtf8();
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
    g.stack_align.clear();
    g.pending_fill.clear();
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