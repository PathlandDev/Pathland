import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/button';
import { notifier } from 'notifier';
import { JsonRpc } from 'jsonrpc';

const REFRESH_MILLIS = 2000;

export class QwcPathlandProxies extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host { display: flex; flex-direction: column; gap: 10px; height: 100%; }
        .toolbar {
            display: flex; align-items: center; gap: 12px;
            padding: 8px 12px 0 12px;
        }
        .spacer { flex: 1; }
        .muted { color: var(--lumo-contrast-60pct); }
        .ok { color: var(--lumo-success-text-color, #2e7d32); }
        .bad { color: var(--lumo-error-text-color, #c62828); }
        .error {
            margin: 8px 12px; padding: 8px 12px;
            border-radius: var(--lumo-border-radius-m);
            background: var(--lumo-error-color-10pct, #fdecea);
            color: var(--lumo-error-text-color, #c62828);
        }
        vaadin-grid { flex: 1; min-height: 0; }
        .mono { font-family: var(--lumo-monospace-font, monospace); font-size: var(--lumo-font-size-s); }
    `;

    static properties = {
        _rows: { state: true },
        _error: { state: true }
    };

    constructor() {
        super();
        this._rows = [];
        this._error = null;
        this._timer = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
        this._timer = setInterval(() => this._refresh(), REFRESH_MILLIS);
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
                <vaadin-button theme="small" @click=${this._refresh}>Refresh</vaadin-button>
                <span class="spacer"></span>
                <span class="muted">${this._rows.length} origin connection(s)</span>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            <vaadin-grid .items=${this._rows} theme="compact">
                <vaadin-grid-column path="mount" header="Mount" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Session" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.shortId}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column header="Endpoint" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.endpoint}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column path="route" header="Route" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="block" header="Block" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Ready" auto-width
                        ${columnBodyRenderer((item) => html`
                            <span class=${item.ready ? 'ok' : 'bad'}>${item.ready ? 'yes' : 'no'}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column header="Snapshot" auto-width
                        ${columnBodyRenderer((item) => html`
                            <span class=${item.snapshotReceived ? 'ok' : 'bad'}>${item.snapshotReceived ? 'yes' : 'no'}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column path="attempts" header="Attempts" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="originRootId" header="Origin root" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="" auto-width
                        ${columnBodyRenderer((item) => html`
                            <vaadin-button theme="small" @click=${() => this._resync(item.sessionId)}>Resync</vaadin-button>`, [])}>
                </vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _refresh = () => {
        return this.jsonRpc.sessions()
            .then((response) => {
                const rows = [];
                for (const session of response.result || []) {
                    for (const proxy of session.proxies || []) {
                        rows.push({
                            ...proxy,
                            mount: session.mount,
                            sessionId: session.id,
                            shortId: (session.id || '').slice(0, 8)
                        });
                    }
                }
                this._rows = rows;
                this._error = null;
            })
            .catch((error) => this._error = this._message(error));
    };

    _resync = (sessionId) => {
        this.jsonRpc.resync({ sessionId })
            .then((response) => {
                if (!response.result || !response.result.ok) {
                    notifier.showErrorMessage('Resync failed: ' + ((response.result && response.result.error) || 'unknown'));
                } else {
                    notifier.showSuccessMessage('Origin snapshots requested');
                }
                return this._refresh();
            })
            .catch((error) => notifier.showErrorMessage('Resync failed: ' + this._message(error)));
    };

    _message(error) {
        return (error && (error.message || error.error)) || String(error);
    }
}

customElements.define('qwc-pathland-proxies', QwcPathlandProxies);
