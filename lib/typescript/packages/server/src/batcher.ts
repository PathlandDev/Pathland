import {
  CAT_PARAMETER,
  CMD_SET_DESIGN_TOKEN,
  CMD_SET_PROPERTY,
  CMD_SET_TEXT,
  VAL_DESIGN_TOKEN,
  VAL_LIST,
  VAL_STRING,
  encodeBatch,
  parseBatch,
  type Opcode,
} from "@pathland/protocol";

const DEFAULT_FLUSH_INTERVAL_MS = 20;
const DEFAULT_MAX_BATCH_BYTES = 16 * 1024;

function rebase(op: Opcode, base: number): Opcode {
  let { a, b, c } = op;
  if (op.category === CAT_PARAMETER) {
    if (op.command === CMD_SET_TEXT) {
      b += base;
    } else if (op.command === CMD_SET_PROPERTY) {
      const valueType = b >>> 16;
      if (valueType === VAL_STRING || valueType === VAL_DESIGN_TOKEN || valueType === VAL_LIST) c += base;
    } else if (op.command === CMD_SET_DESIGN_TOKEN) {
      a += base;
      if ((b & 0xff) === VAL_STRING) c += base;
    }
  }
  return { category: op.category, command: op.command, flags: op.flags, a, b, c };
}

/** Coalesces session delta frames into one sequence-numbered PLPL batch. */
export class DeltaBatcher {
  private opcodes: Opcode[] = [];
  private strings: Uint8Array[] = [];
  private approxBytes = 0;
  private nextSequence = 1;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private closed = false;

  constructor(
    private readonly sender: (bytes: Uint8Array) => void,
    private readonly flushIntervalMs = DEFAULT_FLUSH_INTERVAL_MS,
    private readonly maxBatchBytes = DEFAULT_MAX_BATCH_BYTES,
  ) {}

  append(frame: Uint8Array): void {
    if (this.closed) return;
    const batch = parseBatch(frame);
    if (batch.opcodes.length === 0) return;
    const base = this.strings.reduce((sum, part) => sum + part.length, 0);
    for (const op of batch.opcodes) this.opcodes.push(rebase(op, base));
    this.strings.push(batch.strings);
    this.approxBytes += batch.opcodes.length * 16 + batch.strings.length + 4;
    if (this.approxBytes >= this.maxBatchBytes) {
      this.flush();
    } else if (!this.timer) {
      this.timer = setTimeout(() => {
        this.timer = null;
        this.flush();
      }, this.flushIntervalMs);
      this.timer.unref?.();
    }
  }

  flush(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (this.opcodes.length === 0) return;
    const strings = new Uint8Array(this.strings.reduce((sum, part) => sum + part.length, 0));
    let offset = 0;
    for (const part of this.strings) {
      strings.set(part, offset);
      offset += part.length;
    }
    const opcodes = this.opcodes;
    this.opcodes = [];
    this.strings = [];
    this.approxBytes = 0;
    this.sender(encodeBatch(opcodes, strings, this.nextSequence++));
  }

  close(): void {
    this.closed = true;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.opcodes = [];
    this.strings = [];
  }
}
