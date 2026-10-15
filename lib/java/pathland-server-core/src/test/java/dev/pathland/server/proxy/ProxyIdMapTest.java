package dev.pathland.server.proxy;

import dev.pathland.view.Categories;
import dev.pathland.view.Commands;
import dev.pathland.view.emit.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proxy node-id namespacing (spec/PROXY.md §2): the disjoint high range, the
 * block math, and the opcode remap in both directions.
 */
class ProxyIdMapTest {

    @Test
    void recognisesTheReservedProxyRange() {
        assertTrue(ProxyIdMap.isProxyId(ProxyIdMap.BASE));
        assertTrue(ProxyIdMap.isProxyId(ProxyIdMap.BASE + 1));
        assertTrue(ProxyIdMap.isProxyId(0x7FFF_FFFF));
        assertFalse(ProxyIdMap.isProxyId(0));
        assertFalse(ProxyIdMap.isProxyId(1));
        assertFalse(ProxyIdMap.isProxyId(ProxyIdMap.BASE - 1));
        assertFalse(ProxyIdMap.isProxyId(0x8000_0000)); // one past the range
        assertFalse(ProxyIdMap.isProxyId(-1));
    }

    @Test
    void blockMathRoundTrips() {
        assertEquals(ProxyIdMap.BASE, ProxyIdMap.base(0));
        assertEquals(ProxyIdMap.BASE + ProxyIdMap.BLOCK_SIZE, ProxyIdMap.base(1));
        for (int block : new int[] {0, 1, 7, 1023}) {
            int originId = 42;
            int proxyId = ProxyIdMap.toProxyId(block, originId);
            assertEquals(block, ProxyIdMap.blockOf(proxyId));
            assertEquals(originId, ProxyIdMap.toOriginId(proxyId));
        }
    }

    @Test
    void rejectsOriginIdsBeyondABlock() {
        assertThrows(IllegalArgumentException.class, () -> ProxyIdMap.toProxyId(0, ProxyIdMap.BLOCK_SIZE));
        assertThrows(IllegalArgumentException.class, () -> ProxyIdMap.base(ProxyIdMap.MAX_BLOCKS));
    }

    @Test
    void remapsTreeAndParameterNodeIdsButNotMeta() {
        int block = 3;
        int base = ProxyIdMap.base(block);

        assertEquals(base + 5, ProxyIdMap.toProxy(
                new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, 5, 0x10, 0), block).a());
        assertEquals(base + 7, ProxyIdMap.toProxy(
                new Opcode(Categories.TREE, Commands.Tree.DELETE_NODE, 0, 7, 0, 0), block).a());

        Opcode insert = ProxyIdMap.toProxy(
                new Opcode(Categories.TREE, Commands.Tree.INSERT_CHILD, 0, 1, 2, -1), block);
        assertEquals(base + 1, insert.a(), "parent remapped");
        assertEquals(base + 2, insert.b(), "child remapped");
        assertEquals(-1, insert.c(), "the APPEND index is not a node id");

        assertEquals(base + 9, ProxyIdMap.toProxy(
                new Opcode(Categories.PARAMETER, Commands.Parameter.SET_TEXT, 0, 9, 0, 0), block).a());

        Opcode meta = new Opcode(Categories.META, Commands.Meta.RESET, 0, 0, 0, 0);
        assertSame(meta, ProxyIdMap.toProxy(meta, block), "META carries no node ids");
    }

    @Test
    void remapsEventTargetsBackToOrigin() {
        int block = 2;
        int proxyId = ProxyIdMap.toProxyId(block, 11);
        Opcode egress = ProxyIdMap.toOrigin(
                new Opcode(Categories.EVENT, Commands.Event.POINTER_UP, 0, proxyId, 0, 0), block);
        assertEquals(11, egress.a(), "event target unmapped to the origin id");

        // A non-EVENT opcode passes through untouched on the egress path.
        Opcode tree = new Opcode(Categories.TREE, Commands.Tree.CREATE_NODE, 0, proxyId, 0x10, 0);
        assertSame(tree, ProxyIdMap.toOrigin(tree, block));
    }
}
