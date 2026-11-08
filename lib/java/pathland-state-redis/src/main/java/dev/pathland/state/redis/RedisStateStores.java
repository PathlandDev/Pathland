package dev.pathland.state.redis;

import dev.pathland.view.core.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis-or-fallback {@link StateStore} selection: Redis when reachable, otherwise the
 * supplied fallback (typically an in-memory store). The app opts in by calling this
 * from its own {@code StateStore} bean after adding this module.
 */
public final class RedisStateStores {

    private static final Logger LOG = LoggerFactory.getLogger(RedisStateStores.class);

    private RedisStateStores() {}

    /** A Redis store when reachable, else {@code fallback}. */
    public static StateStore redisOrFallback(StateStore fallback) {
        try {
            StateStore redis = new RedisStateStore.Provider().store();
            redis.load("probe", Object.class); // probe connectivity
            LOG.info("state store: redis");
            return redis;
        } catch (RuntimeException e) {
            LOG.info("state store: in-memory (Redis unavailable: {})", e.getMessage());
            return fallback;
        }
    }
}