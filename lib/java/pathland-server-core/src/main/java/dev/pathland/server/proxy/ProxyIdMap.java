package dev.pathland.server.proxy;

import dev.pathland.view.Categories;
import dev.pathland.view.Commands;
import dev.pathland.view.emit.Opcode;

/**
 * Node-id namespacing for proxy views (spec/PROXY.md §2).
 *
 * <p>Node ids are per-emitter {@code u32}s that both start at {@code 1}, so a
 * proxying server MUST remap an origin's ids into a disjoint high range before
 * emitting them to its client. The range is
 * {@code [0x4000_0000, 0x8000_0000)} — 1024 blocks of {@code 2^20} ids, one per
 * proxy instance. A proxying app's own emitter never allocates there.
 */
public final class ProxyIdMap {

    /** First id of the reserved proxy range ({@code 0b01} in the top two bits). */
    public static final int BASE = 0x4000_0000;

    /** Ids per proxy block ({@code 2^20}). */
    public static final int BLOCK_SIZE = 1 << 20;

    /** Shift from an id to its block index. */
    public static final int BLOCK_SHIFT = 20;

    /** Maximum number of concurrent proxies ({@code 1024}). */
    public static final int MAX_BLOCKS = (0x8000_0000 - BASE) / BLOCK_SIZE;

    private ProxyIdMap() {}

    /** True when {@code id} lies in the reserved proxy range (top two bits {@code 01}). */
    public static boolean isProxyId(int id) {
        return (id & 0xC000_0000) == BASE;
    }

    /** The first id of {@code block}. */
    public static int base(int block) {
        if (block < 0 || block >= MAX_BLOCKS) {
            throw new IllegalArgumentException("proxy block out of range: " + block);
        }
        return BASE + block * BLOCK_SIZE;
    }

    /** The block index an in-range id belongs to. */
    public static int blockOf(int proxyId) {
        return (proxyId - BASE) >>> BLOCK_SHIFT;
    }

    /** The origin node id behind a proxied id ({@code id - base(block)}). */
    public static int toOriginId(int proxyId) {
        return (proxyId - BASE) & (BLOCK_SIZE - 1);
    }

    /** The proxied id for an origin node id in {@code block}. */
    public static int toProxyId(int block, int originId) {
        if (originId < 0 || originId >= BLOCK_SIZE) {
            throw new IllegalArgumentException(
                    "origin node id exceeds the proxy block capacity (" + originId + ")");
        }
        return base(block) + originId;
    }

    // ── Opcode remapping ────────────────────────────────────────────────────

    /**
     * Remap an origin (guest → host) opcode's node ids into {@code block}'s proxy
     * range. Node-id-bearing fields: {@code CREATE_NODE}/{@code DELETE_NODE} {@code A};
     * {@code INSERT_CHILD}/{@code REMOVE_CHILD}/{@code MOVE_CHILD} {@code A}+{@code B};
     * every {@code PARAMETER} op's {@code A}. {@code META} ops carry no node ids.
     */
    public static Opcode toProxy(Opcode op, int block) {
        if (op.category() == Categories.TREE) {
            return switch (op.command()) {
                case Commands.Tree.CREATE_NODE, Commands.Tree.DELETE_NODE -> new Opcode(
                        op.category(), op.command(), op.flags(),
                        toProxyId(block, op.a()), op.b(), op.c());
                case Commands.Tree.INSERT_CHILD, Commands.Tree.REMOVE_CHILD, Commands.Tree.MOVE_CHILD -> new Opcode(
                        op.category(), op.command(), op.flags(),
                        toProxyId(block, op.a()), toProxyId(block, op.b()), op.c());
                default -> op;
            };
        }
        if (op.category() == Categories.PARAMETER) {
            return new Opcode(op.category(), op.command(), op.flags(),
                    toProxyId(block, op.a()), op.b(), op.c());
        }
        return op;
    }

    /**
     * Remap a client event opcode's target back to the origin's id space
     * (proxy id → {@code id - base(block)}). Only the {@code A} target carries a
     * node id; the caller is responsible for only forwarding events whose target
     * is in this proxy's range.
     */
    public static Opcode toOrigin(Opcode op, int block) {
        if (op.category() == Categories.EVENT) {
            return new Opcode(op.category(), op.command(), op.flags(),
                    toOriginId(op.a()), op.b(), op.c());
        }
        return op;
    }
}
