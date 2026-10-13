package dev.pathland.view.state;

/**
 * Pluggable provider of {@link StateStore}s. A platform (Quarkus, Spring Boot, a
 * desktop launcher) contributes a provider so the application layer is written once
 * against {@link StateStore} and the storage mechanism is chosen at startup.
 */
public interface StateStoreProvider {

    /** A store, optionally qualified (e.g. {@code "redis"}). */
    StateStore store(String... qualifiers);

    /** A human-readable platform name (e.g. {@code "redis"}, {@code "in-memory"}). */
    default String platform() {
        return "unknown";
    }
}