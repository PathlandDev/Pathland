import {
  BatchWriter,
  CAT_PARAMETER,
  CAT_TREE,
  CMD_CREATE_NODE,
  CMD_DELETE_NODE,
  CMD_INSERT_CHILD,
  CMD_REMOVE_CHILD,
  CMD_SET_PROPERTY,
  CMD_SET_TEXT,
  VAL_F32,
  VAL_LIST,
  VAL_STRING,
  f32bits,
} from "@pathland/protocol";

/** Collects one self-contained PLPL frame (opcodes + frame-local strings). */
export class FrameOpcodeSink {
  private readonly writer = new BatchWriter();
  private frameHandler: ((frame: Uint8Array) => void) | null = null;

  onFrame(handler: (frame: Uint8Array) => void): void {
    this.frameHandler = handler;
  }

  beginFrame(): void {
    this.writer.begin();
  }

  endFrame(): void {
    const frame = this.writer.end(0);
    this.frameHandler?.(frame);
  }

  createNode(id: number, component: number): void {
    this.writer.push(CAT_TREE, CMD_CREATE_NODE, 0, id, component, 0);
  }

  insertChild(parent: number, child: number, index = -1): void {
    this.writer.push(CAT_TREE, CMD_INSERT_CHILD, 0, parent, child, index);
  }

  deleteNode(id: number): void {
    this.writer.push(CAT_TREE, CMD_DELETE_NODE, 0, id, 0, 0);
  }

  removeChild(parent: number, child: number): void {
    this.writer.push(CAT_TREE, CMD_REMOVE_CHILD, 0, parent, child, 0);
  }

  setText(node: number, text: string): void {
    const offset = this.writer.string(text);
    this.writer.push(CAT_PARAMETER, CMD_SET_TEXT, 0, node, offset, 0);
  }

  setProperty(node: number, property: number, valueType: number, value: number | string | readonly number[]): void {
    const b = ((valueType << 16) | (property & 0xffff)) >>> 0;
    if (valueType === VAL_STRING) {
      const offset = this.writer.string(value as string);
      this.writer.push(CAT_PARAMETER, CMD_SET_PROPERTY, 0, node, b, offset);
    } else if (valueType === VAL_LIST) {
      const offset = this.writer.list(value as readonly number[]);
      this.writer.push(CAT_PARAMETER, CMD_SET_PROPERTY, 0, node, b, offset);
    } else {
      const bits = valueType === VAL_F32 ? f32bits(value as number) : (value as number);
      this.writer.push(CAT_PARAMETER, CMD_SET_PROPERTY, 0, node, b, bits);
    }
  }
}
