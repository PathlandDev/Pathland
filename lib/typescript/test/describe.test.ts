import { describe, expect, it } from "vitest";
import { describeBatch, describeBatchDetail } from "../src/describe";
import { parseBatch } from "../src/plpl";
import { encodeEnvironment, encodeNavigate, encodePointerUp, encodeResync } from "../src/events";
import {
  CAT_PARAMETER,
  CAT_TREE,
  CMD_CREATE_NODE,
  CMD_INSERT_CHILD,
  CMD_SET_PROPERTY,
  CMD_SET_TEXT,
  COMPONENT_TEXT,
  COMPONENT_VSTACK,
  PROP_ROUTE,
  VAL_STRING,
} from "../src/constants";
import { buildBatch, stringEntry } from "./plpl.test";

describe("describeBatch", () => {
  it("describes events (emitted) meaningfully", () => {
    const up = describeBatch(parseBatch(encodePointerUp(4, 100, 100)));
    expect(up).toContain("EVENT POINTER_UP(target=4, x=100, y=100)");

    const nav = describeBatch(parseBatch(encodeNavigate("https://example.com/users/7")));
    expect(nav).toContain('EVENT NAVIGATE(target=0, url="https://example.com/users/7")');

    const env = describeBatchDetail(parseBatch(encodeEnvironment(800, 600, "/users/42")));
    expect(env).toContain("META ENVIRONMENT(VIEWPORT_WIDTH=800)");
    expect(env).toContain('META ENVIRONMENT(ROUTE="/users/42")');

    const resync = describeBatch(parseBatch(encodeResync()));
    expect(resync).toContain("META RESYNC");
  });

  it("describes received opcodes (frames) meaningfully", () => {
    const batch = parseBatch(
      buildBatch(
        [
          [CAT_TREE, CMD_CREATE_NODE, 0, 1, COMPONENT_VSTACK],
          [CAT_TREE, CMD_CREATE_NODE, 0, 2, COMPONENT_TEXT],
          [CAT_TREE, CMD_INSERT_CHILD, 0, 1, 2, -1],
          [CAT_PARAMETER, CMD_SET_TEXT, 0, 2, 0],
        ],
        stringEntry("Users"),
      ),
    );
    const detail = describeBatchDetail(batch);
    expect(detail).toContain("TREE CREATE_NODE(id=1, VSTACK)");
    expect(detail).toContain("TREE CREATE_NODE(id=2, TEXT)");
    expect(detail).toContain("TREE INSERT_CHILD(parent=1, child=2, index=4294967295)");
    expect(detail).toContain('STYLE SET_TEXT("Users", node=2)');

    // The compact summary leads with the first opcode + count.
    expect(describeBatch(batch)).toContain("TREE CREATE_NODE(id=1, VSTACK)");
    expect(describeBatch(batch)).toContain("(4 ops)");
  });

  it("describes a ROUTE property with its value", () => {
    const batch = parseBatch(
      buildBatch([[CAT_PARAMETER, CMD_SET_PROPERTY, 0, 1, (VAL_STRING << 16) | PROP_ROUTE, 0]], stringEntry("/users")),
    );
    expect(describeBatchDetail(batch)).toContain('STYLE SET_PROPERTY(ROUTE="/users", node=1)');
  });
});