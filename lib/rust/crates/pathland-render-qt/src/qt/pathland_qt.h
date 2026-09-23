// Shared C ABI types between the Rust decode core and the C++ Qt widget layer.
//
// A `PathlandQtCommand` is one delta: Rust produces a batch (mirroring the
// opcode engine's "only changes" discipline) and C++ applies it to the Qt Quick
// scene. This is deliberately a flat struct — the renderer's FFI boundary stays
// a batch of small commands, never a serialized tree.
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
    PLQT_RESET_NODE = 8,    /* a=id — reset the widget to renderer defaults      */
    PLQT_SET_SCHEME = 9,    /* a=scheme (0 light, 1 dark)                        */
} PathlandQtCommandKind;

typedef struct PathlandQtCommand {
    uint8_t kind;
    uint32_t a;
    uint32_t b;
    uint32_t c;
    uint32_t str_len;
    const char *str;
} PathlandQtCommand;

/* Wake callback into the Rust host: a Qt event fired; the host drains its own
   event ring (renderer never drains). */
typedef void (*PathlandQtWake)(void *user);
/* Qt timer tick -> Rust pump (drain pending frames), like GTK's idle pump. */
typedef void (*PathlandQtTick)(void *user);

/* Own the Qt application shell (QGuiApplication + QQuickWindow + engine +
   root item + idle tick timer). Blocks until the window closes / quit. */
int pathland_qt_layer_run(const char *title, int width, int height,
                          PathlandQtWake wake, PathlandQtTick tick, void *user);

/* Apply a batch of delta commands to the Qt Quick scene. */
void pathland_qt_layer_apply(const PathlandQtCommand *cmds, uint32_t count);

/* Reset the whole scene (META::RESET). */
void pathland_qt_layer_reset(void);

/* Quit the event loop (host-initiated shutdown). */
void pathland_qt_layer_quit(void);

#ifdef __cplusplus
}
#endif

#endif /* PATHLAND_QT_LAYER_H */