/** The transport seam: the session sends self-contained PLPL batches through this. */
export interface PathlandConnection {
  send(bytes: Uint8Array): void;
  isOpen(): boolean;
}
