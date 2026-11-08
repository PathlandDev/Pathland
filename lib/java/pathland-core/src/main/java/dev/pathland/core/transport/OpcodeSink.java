package dev.pathland.core.transport;

import dev.pathland.core.text.AttributedString;

/**
 * The emission target for opcodes: a self-contained frame sink (SSR/WebSocket) or
 * the zero-copy Rust ring sink.
 */
public interface OpcodeSink {
    void beginFrame();

    void endFrame();

    void createNode(int id, int component);

    void deleteNode(int id);

    void insertChild(int parent, int child, int index);

    void removeChild(int parent, int child);

    void moveChild(int parent, int child, int newIndex);

    void setText(int nodeId, String text);

    void setAttributedText(int nodeId, AttributedString value);

    void setProperty(int nodeId, int property, int valueType, Object value);

    void setDate(int nodeId, int days, int millisOfDay);

    void setDesignToken(String path, int valueType, Object value);

    /** Clear every application design-token override. */
    default void resetTheme() {
        throw new UnsupportedOperationException("resetTheme");
    }

    /** Emit {@code META::SET_COLOR_SCHEME} (0 platform, 1 light, 2 dark). */
    default void setColorScheme(int mode) {
        throw new UnsupportedOperationException("setColorScheme");
    }

    /** Emit {@code META::SET_WINDOW_METADATA}; an empty string clears the field. */
    default void setWindowMetadata(int field, String value) {
        throw new UnsupportedOperationException("setWindowMetadata");
    }

    /** Whether this sink feeds a renderer that gates raw inputs on a {@code BINDING_ID}. */
    default boolean requiresBindingId() {
        return false;
    }
}
