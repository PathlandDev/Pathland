import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/checkbox';
import '@vaadin/text-field';
import '@vaadin/select';
import { notifier } from 'notifier';
import { JsonRpc } from 'jsonrpc';

const POLL_MILLIS = 700;
const MAX_ROWS = 1000;

const CATEGORIES = {
    0x01: 'TREE',
    0x02: 'PARAMETER',
    0x03: 'EVENT',
    0x04: 'META'
};

export class QwcPathlandWire extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 10px;
            height: 100%;
        }
        .toolbar {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 8px 12px 0 12px;
            flex-wrap: wrap;
        }
        .spacer { flex: 1; }
        .muted { color: var(--lumo-contrast-60pct); }
        .error {
            margin: 8px 12px;
            padding: 8px 12px;
            border-radius: var(--lumo-border-radius-m);
            background: var(--lumo-error-color-10pct, #fdecea);
            color: var(--lumo-error-text-color, #c62828);
        }
        vaadin-grid {
            flex: 1;
            min-height: 220px;
        }
        .details {
            border-top: 1px solid var(--lumo-contrast-10pct);
            padding: 8px 12px 12px 12px;
            max-height: 35vh;
            overflow: auto;
        }
        .details h4 {
            margin: 0 0 6px 0;
            color: var(--lumo-contrast-70pct);
        }
        pre {
            margin: 0;
            font-family: var(--lumo-monospace-font, monospace);
            font-size: var(--lumo-font-size-s);
            white-space: pre-wrap;
            word-break: break-word;
        }
        .mono {
            font-family: var(--lumo-monospace-font, monospace);
            font-size: var(--lumo-font-size-s);
        }
        .kind {
            font-family: var(--lumo-monospace-font, monospace);
            font-size: var(--lumo-font-size-xs);
            padding: 1px 6px;
            border-radius: 999px;
            background: var(--lumo-contrast-10pct);
        }
        .kind-BATCH { background: var(--lumo-primary-color-10pct, #e3f2fd); }
        .kind-EVENTS { background: var(--lumo-success-color-10pct, #e8f5e9); }
        .kind-SSR { background: var(--lumo-warning-color-10pct, #fff8e1); }
    `;

    static properties = {
        _entries: { state: true },
        _lastId: { state: true },
        _paused: { state: true },
        _selected: { state: true },
        _sessionFilter: { state: true },
        _kindFilter: { state: true },
        _error: { state: true }
    };

    constructor() {
        super();
        this._entries = [];
        this._lastId = 0;
        this._paused = false;
        this._selected = null;
        this._sessionFilter = '';
        this._kindFilter = '';
        this._error = null;
        this._timer = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._poll();
        this._timer = setInterval(() => {
            if (!this._paused) {
                this._poll();
            }
        }, POLL_MILLIS);
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._timer) {
            clearInterval(this._timer);
            this._timer = null;
        }
    }

    render() {
        return html`
            <div class="toolbar">
                <vaadin-button theme="small" @click=${this._poll}>Refresh</vaadin-button>
                <vaadin-checkbox ?checked=${this._paused}
                        @change=${(event) => this._paused = event.target.checked}>
                    Pause
                </vaadin-checkbox>
                <vaadin-button theme="small" @click=${this._clear}>Clear</vaadin-button>
                <vaadin-text-field placeholder="Session" clear-button-visible
                        @value-changed=${(event) => this._sessionFilter = event.detail.value}>
                </vaadin-text-field>
                <vaadin-select label="Kind" .items=${this._kindItems()} .value=${this._kindFilter}
                        item-label-path="label" item-value-path="value"
                        @value-changed=${(event) => this._kindFilter = event.detail.value}>
                </vaadin-select>
                <span class="muted">${this._entries.length} retained</span>
                <span class="spacer"></span>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            <vaadin-grid .items=${this._filtered()} theme="compact">
                <vaadin-grid-column header="Time" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${this._time(item.timestampMillis)}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column header="Kind" auto-width
                        ${columnBodyRenderer((item) => html`<span class="kind kind-${item.kind}">${item.kind}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column header="Session" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${this._short(item.sessionId)}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column path="mount" header="Mount" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Detail" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${this._detail(item)}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column header="" auto-width
                        ${columnBodyRenderer((item) => html`
                            <vaadin-button theme="small" @click=${() => this._selected = item}>Inspect</vaadin-button>`, [])}>
                </vaadin-grid-column>
            </vaadin-grid>
            ${this._selected ? this._renderDetails() : ''}
        `;
    }

    _renderDetails() {
        const item = this._selected;
        return html`
            <div class="details">
                <h4>${item.kind} #${item.id}${item.error ? html` — <span style="color: var(--lumo-error-text-color)">${item.error}</span>` : ''}</h4>
                ${item.kind === 'BATCH' ? html`
                    <pre>${(item.decoded || []).map((op) => this._opcode(op)).join('\n') || 'no opcodes'}</pre>
                ` : item.kind === 'EVENTS' ? html`
                    <pre>${JSON.stringify(item.events || [], null, 2)}</pre>
                ` : item.kind === 'SSR' ? html`
                    <pre>route ${item.route}\n${this._nanos(item.nanos)} · ${item.bytes} bytes</pre>
                ` : html`
                    <pre>${JSON.stringify(item, null, 2)}</pre>
                `}
            </div>
        `;
    }

    _kindItems() {
        return [
            { label: 'All', value: '' },
            { label: 'BATCH', value: 'BATCH' },
            { label: 'EVENTS', value: 'EVENTS' },
            { label: 'SSR', value: 'SSR' },
            { label: 'SESSION_OPEN', value: 'SESSION_OPEN' },
            { label: 'SESSION_CLOSE', value: 'SESSION_CLOSE' }
        ];
    }

    _filtered() {
        const session = (this._sessionFilter || '').toLowerCase();
        const kind = this._kindFilter;
        return this._entries.filter((entry) => {
            if (kind && entry.kind !== kind) return false;
            if (session) {
                const haystack = ((entry.sessionId || '') + ' ' + (entry.mount || '')).toLowerCase();
                if (!haystack.includes(session)) return false;
            }
            return true;
        });
    }

    _poll = () => {
        return this.jsonRpc.trace({ sinceId: this._lastId, limit: 200 })
            .then((response) => {
                const snapshot = response.result;
                if (!snapshot) return;
                const incoming = snapshot.entries || [];
                if (incoming.length) {
                    this._entries = [...incoming.slice().reverse(), ...this._entries].slice(0, MAX_ROWS);
                }
                if (snapshot.nextId != null) {
                    this._lastId = snapshot.nextId;
                }
                this._error = null;
            })
            .catch((error) => {
                this._error = this._message(error);
            });
    };

    _clear = () => {
        this.jsonRpc.clearTrace()
            .then((response) => {
                this._entries = [];
                this._selected = null;
                if (Array.isArray(response.result)) {
                    // no-op; a scalar result is the high-water id
                } else if (response.result != null) {
                    this._lastId = response.result;
                }
                notifier.showSuccessMessage('Trace cleared');
            })
            .catch((error) => notifier.showErrorMessage('Clear failed: ' + this._message(error)));
    };

    _opcode(op) {
        const category = CATEGORIES[op.category] || `0x${op.category.toString(16)}`;
        const command = `0x${op.command.toString(16).padStart(2, '0')}`;
        const text = op.text == null ? '' : ` "${op.text}"`;
        return `${category.padEnd(10)} cmd=${command} flags=${op.flags} a=${op.a} b=${op.b} c=${op.c}${text}`;
    }

    _detail(item) {
        if (item.kind === 'BATCH') {
            return `seq ${item.sequence} · ${item.frames}f · ${item.opcodes} ops · ${item.bytes}B`;
        }
        if (item.kind === 'EVENTS') {
            return `${(item.events || []).length} event(s)`;
        }
        if (item.kind === 'SSR') {
            return `${item.route} · ${this._nanos(item.nanos)} · ${item.bytes}B`;
        }
        return '—';
    }

    _short(id) {
        return id ? id.slice(0, 8) : '—';
    }

    _time(millis) {
        if (!millis) return '—';
        const date = new Date(millis);
        return date.toLocaleTimeString([], { hour12: false }) + '.' + String(date.getMilliseconds()).padStart(3, '0');
    }

    _nanos(nanos) {
        if (nanos == null) return '—';
        if (nanos < 1000) return nanos + 'ns';
        if (nanos < 1000000) return (nanos / 1000).toFixed(1) + 'µs';
        return (nanos / 1000000).toFixed(1) + 'ms';
    }

    _message(error) {
        return (error && (error.message || error.error)) || String(error);
    }
}

customElements.define('qwc-pathland-wire', QwcPathlandWire);
