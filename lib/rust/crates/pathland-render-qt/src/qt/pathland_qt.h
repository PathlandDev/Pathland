// Shared C ABI types between the Rust decode core and the C++ Qt widget layer.
//
// A `PathlandQtCommand` is one delta: Rust produces a batch (mirroring the
// opcode engine's "only changes" discipline) and C++ applies it to the Qt Quick
// scene. This is deliberately a flat struct — the renderer's FFI boundary stays
// a batch of small commands, never a serialized tree.
//
// Inputs flow back the same way: a `PathlandQtEvent` carries one raw input
// (control signal -> C++ -> Rust, which encodes the EVENT opcode and wakes the
// host). The renderer never drains events.
#ifndef PATHLAND_QT_LAYER_H
#define PATHLAND_QT_LAYER_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum PathlandQtCommandKind {
    PLQT_CREATE_NODE = 0,   /* a=id, b=componentType(u16 low)                    */
    PLQT_DELETE_NODE = 1,   /* a=id                                              */
    PLQT_INSERT_CHILD = 2,  /* a=parent, b=child, c=index (u32::MAX = append)    */
    PLQT_REMOVE_CHILD = 3,  /* a=parent, b=child                                 */
    PLQT_MOVE_CHILD = 4,    /* a=parent, b=child, c=newIndex                     */
    PLQT_SET_TEXT = 5,      /* a=id, str=text                                    */
    PLQT_SET_PROPERTY = 6,  /* a=id, b=(valueType<<16)|propertyId, c=value       */
    PLQT_SET_STRING_PROPERTY = 7, /* a=id, b=propertyId, str=value              */
    PLQT_RESET_NODE = 8,    /* a=id — reset renderer-owned style to defaults     */
    PLQT_SET_SCHEME = 9,    /* a=scheme (0 light, 1 dark)                        */
    PLQT_SET_DATE = 10,     /* a=id, b=days since epoch (I32), c=millis of day   */
} PathlandQtCommandKind;

typedef struct PathlandQtCommand {
    uint8_t kind;
    uint32_t a;
    uint32_t b;
    uint32_t c;
    uint32_t str_len;
    const char *str;
} PathlandQtCommand;

/* One raw input reported by the Qt layer (C++ -> Rust). */
/* EVENT_LISTENERS bitmask (spec/EVENTS.md). */
enum PathlandQtListenerBits {
    PL_LISTENER_POINTER_DOWN = 1u << 0,
    PL_LISTENER_POINTER_MOVE = 1u << 1,
    PL_LISTENER_POINTER_UP = 1u << 2,
    PL_LISTENER_KEY_DOWN = 1u << 3,
    PL_LISTENER_KEY_UP = 1u << 4,
    PL_LISTENER_FOCUS = 1u << 5,
    PL_LISTENER_EDITING = 1u << 6,
    PL_LISTENER_SUBMIT = 1u << 7,
    PL_LISTENER_SCROLL = 1u << 8,
    PL_LISTENER_WHEEL = 1u << 9,
};

typedef enum PathlandQtEventKind {
    PLQT_EV_NONE = 0,
    PLQT_EV_POINTER_UP = 1,   /* a control was tapped / pointer released (x,y)   */
    PLQT_EV_VALUE_CHANGED = 2, /* a control's semantic value changed (f32)      */
    PLQT_EV_TEXT_CHANGED = 3, /* a text field's value changed (str)             */
    PLQT_EV_POINTER_DOWN = 4, /* pointer pressed on a listener node (x,y)       */
    PLQT_EV_POINTER_MOVE = 5, /* pointer moved on a listener node (x,y)         */
    PLQT_EV_NAVIGATE_BACK = 6, /* native back request (Escape), global           */
    PLQT_EV_SCHEME_CHANGED = 7, /* platform color scheme changed (value 0/1)     */
    PLQT_EV_MEDIA_PLAY_STATE = 8, /* controller playback state (value 0/1)       */
    PLQT_EV_MEDIA_TIME = 9,       /* controller position, seconds (value)         */
    PLQT_EV_MEDIA_ENDED = 10,     /* controller reached the end (no value)        */
    PLQT_EV_MEDIA_VOLUME = 11,    /* controller volume 0..1 (value)               */
    PLQT_EV_MEDIA_DURATION = 12,  /* controller duration, seconds (value)         */
    PLQT_EV_FIT_CHANGED = 13,     /* SIZE_THAT_FITS derived fit index (value)     */
    PLQT_EV_POINTER_ENTER = 14,   /* pointer entered a listener node (hover)      */
    PLQT_EV_POINTER_LEAVE = 15,   /* pointer left a listener node                 */
    PLQT_EV_KEY_DOWN = 16,        /* key pressed on the focused node              */
    PLQT_EV_KEY_UP = 17,          /* key released on the focused node             */
    PLQT_EV_FOCUS = 18,           /* focus changed (value 0/1)                    */
    PLQT_EV_EDITING = 19,         /* editing changed (value 0/1)                  */
    PLQT_EV_SUBMIT = 20,          /* text input submitted                         */
} PathlandQtEventKind;

typedef struct PathlandQtEvent {
    uint8_t kind;
    uint32_t node_id;
    float x;
    float y;
    float value;
    uint32_t str_len;
    const char *str;
    /* Key payload (KEY_DOWN/KEY_UP): the platform key code, a renderer-defined
       modifier bitmask, and auto-repeat. Zero for every other event. */
    uint16_t key_code;
    uint8_t modifiers;
    uint8_t repeat;
} PathlandQtEvent;

/* Wake callback into the Rust host: an event was written to the ring; the host
   drains its own event ring (renderer never drains). */
typedef void (*PathlandQtWake)(void *user);
/* Qt timer tick -> Rust pump (drain pending frames), like GTK's idle pump. */
typedef void (*PathlandQtTick)(void *user);
/* A raw input fired (C++ -> Rust): Rust encodes the EVENT opcode + writes it,
   then wakes the host. */
typedef void (*PathlandQtEventFn)(const PathlandQtEvent *ev, void *user);

/* Own the Qt application shell (QGuiApplication + QQuickWindow + engine +
   root item + idle tick timer). `pathland_qt_layer_run` blocks until the
   window closes; `pathland_qt_layer_init` only builds the shell so tests can
   drive `apply` headless without an event loop. */
int pathland_qt_layer_init(PathlandQtWake wake, PathlandQtTick tick,
                           PathlandQtEventFn event_fn, void *user);
int pathland_qt_layer_run(const char *title, int width, int height,
                          PathlandQtWake wake, PathlandQtTick tick,
                          PathlandQtEventFn event_fn, void *user);

/* Apply a batch of delta commands to the Qt Quick scene. */
void pathland_qt_layer_apply(const PathlandQtCommand *cmds, uint32_t count);

/* Reset the whole scene (META::RESET). */
void pathland_qt_layer_reset(void);

/* Set the asset root so web-style `/_pathland/...` media/image sources resolve
   against a local directory (mirrors `pathland_gtk_set_asset_root`). */
void pathland_qt_layer_set_asset_root(const char *root);

/* Quit the event loop (host-initiated shutdown). */
void pathland_qt_layer_quit(void);

/* Test hooks: query the live scene so Rust tests can assert on it. */
uint32_t pathland_qt_layer_apply_count(void);
uint32_t pathland_qt_layer_last_text(char *out, uint32_t cap);
uint32_t pathland_qt_layer_root_child_count(void);
uint32_t pathland_qt_layer_widget_child_count(uint32_t id);
uint32_t pathland_qt_layer_scroll_content_has_child(uint32_t id, uint32_t childId);
uint32_t pathland_qt_layer_widget_text(uint32_t id, char *out, uint32_t cap);
uint32_t pathland_qt_layer_widget_prop_text(uint32_t id, const char *prop,
                                            char *out, uint32_t cap);
int32_t pathland_qt_layer_widget_prop_int(uint32_t id, const char *prop);
uint32_t pathland_qt_layer_widget_fill(uint32_t id, uint32_t which);
double pathland_qt_layer_widget_preferred(uint32_t id, uint32_t which);
double pathland_qt_layer_widget_implicit_size(uint32_t id, uint32_t which);
double pathland_qt_layer_widget_size(uint32_t id, uint32_t which);
double pathland_qt_layer_widget_position(uint32_t id, uint32_t which);
uint32_t pathland_qt_layer_top_child(uint32_t parent);
uint32_t pathland_qt_layer_anchors_fill(uint32_t id);
uint32_t pathland_qt_layer_nav_depth(uint32_t slot);
uint32_t pathland_qt_layer_nav_page_fills(uint32_t slot);
uint32_t pathland_qt_layer_video_source(uint32_t id);
uint32_t pathland_qt_layer_media_source(uint32_t id, char *out, uint32_t cap);
double pathland_qt_layer_media_volume(uint32_t id);
uint32_t pathland_qt_layer_media_muted(uint32_t id);
int pathland_qt_layer_media_loops(uint32_t id);
double pathland_qt_layer_media_rate(uint32_t id);
int pathland_qt_layer_fit_index(uint32_t id);
void pathland_qt_layer_report_fit(uint32_t id, double width);
uint32_t pathland_qt_layer_background_color(uint32_t id);
double pathland_qt_layer_background_radius(uint32_t id);
uint32_t pathland_qt_layer_background_border_width(uint32_t id);
uint32_t pathland_qt_layer_text_color(uint32_t id);
int pathland_qt_layer_text_pixel_size(uint32_t id);
uint32_t pathland_qt_layer_font_italic(uint32_t id);
uint32_t pathland_qt_layer_allows_hit_testing(uint32_t id);
double pathland_qt_layer_padding(uint32_t id, const char *edge);
double pathland_qt_layer_offset(uint32_t id, uint32_t which);
uint32_t pathland_qt_layer_shadow_color(uint32_t id);
double pathland_qt_layer_shadow_x(uint32_t id);
int pathland_qt_layer_render_once(void);

#ifdef __cplusplus
}
#endif

#endif /* PATHLAND_QT_LAYER_H */