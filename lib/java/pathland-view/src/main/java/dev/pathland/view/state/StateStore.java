package dev.pathland.view.state;

import java.util.Optional;

/**
 * A cross-platform state store abstraction. The exact same application code runs on a
 * multi-tenant server (Redis), a desktop app (File/SQLite), a browser (LocalStorage),
 * or an embedded ESP32 (NVS Flash) by swapping the {@link StateStore} implementation —
 * component state is decoupled from the storage mechanism.
 *
 * <p>The store is untyped: {@link #load} takes the value's class for a type check and
 * {@link #save} accepts any (serializable) value, so a single store serves every
 * signal type.
 */
public interface StateStore {

    /** Load state for {@code key} (type-checked against {@code clazz}), or empty when absent. */
    <T> Optional<T> load(String key, Class<T> clazz);

    /** Persist {@code state} for {@code key}. */
    void save(String key, Object state);

    /** Remove state for {@code key}. */
    void delete(String key);
}