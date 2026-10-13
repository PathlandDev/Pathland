package dev.pathland.state.redis;

import dev.pathland.view.state.StateStore;
import dev.pathland.view.state.StateStoreProvider;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.Optional;

/**
 * A {@link StateStore} backed by Redis (Lettuce, framework-agnostic). Values are
 * Java-serialized into a byte array and stored under a plain key. The exact same
 * application code runs on a multi-tenant scale-out server (this store), a desktop app
 * (File/SQLite), a browser (LocalStorage), or an embedded ESP32 (NVS Flash) by swapping
 * the store.
 */
public final class RedisStateStore implements StateStore, AutoCloseable {

    private final RedisClient client;
    private final StatefulRedisConnection<String, byte[]> connection;
    private final RedisCommands<String, byte[]> commands;

    public RedisStateStore(RedisURI uri) {
        this(RedisClient.create(uri));
    }

    /** Create against the default Redis URI ({@code redis://localhost:6379}). */
    public RedisStateStore() {
        this(RedisURI.builder().withHost("localhost").withPort(6379)
                .withTimeout(Duration.ofSeconds(2)).build());
    }

    public RedisStateStore(RedisClient client) {
        this.client = client;
        this.connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        this.commands = connection.sync();
    }

    @Override
    public <T> Optional<T> load(String key, Class<T> clazz) {
        byte[] bytes = commands.get(key);
        if (bytes == null) {
            return Optional.empty();
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = in.readObject();
            if (clazz.isInstance(value)) {
                return Optional.of(clazz.cast(value));
            }
            return Optional.empty();
        } catch (IOException | ClassNotFoundException e) {
            throw new StateStoreException("failed to load state key '" + key + "'", e);
        }
    }

    @Override
    public void save(String key, Object state) {
        if (!(state instanceof Serializable)) {
            throw new IllegalArgumentException("state must be Serializable, got " + state.getClass());
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(state);
            oos.flush();
            commands.set(key, out.toByteArray());
        } catch (IOException e) {
            throw new StateStoreException("failed to save state key '" + key + "'", e);
        }
    }

    @Override
    public void delete(String key) {
        commands.del(key);
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }

    /** A provider that produces {@link RedisStateStore}s against the default URI. */
    public static final class Provider implements StateStoreProvider {

        @Override
        public StateStore store(String... qualifiers) {
            return new RedisStateStore();
        }

        @Override
        public String platform() {
            return "redis";
        }
    }

    /** Thrown on storage failures. */
    public static final class StateStoreException extends RuntimeException {
        public StateStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}