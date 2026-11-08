import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/text-field';
import { notifier } from 'notifier';
import { JsonRpc } from 'jsonrpc';

export class QwcPathlandState extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host { display: flex; flex-direction: column; gap: 10px; height: 100%; }
        .toolbar {
            display: flex; align-items: center; gap: 12px;
            padding: 8px 12px 0 12px; flex-wrap: wrap;
        }
        .spacer { flex: 1; }
        .muted { color: var(--lumo-contrast-60pct); }
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
        _entries: { state: true },
        _supported: { state: true },
        _error: { state: true },
        _query: { state: true }
    };

    constructor() {
        super();
        this._entries = [];
        this._supported = true;
        this._error = null;
        this._query = '';
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    render() {
        return html`
            <div class="toolbar">
                <vaadin-text-field placeholder="Filter keys" clear-button-visible
                        style="min-width: 280px"
                        @value-changed=${(event) => { this._query = event.detail.value; this._refresh(); }}>
                </vaadin-text-field>
                <vaadin-button theme="small" @click=${this._refresh}>Refresh</vaadin-button>
                <span class="spacer"></span>
                <span class="muted">${this._entries.length} key(s)</span>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            <vaadin-grid .items=${this._entries} theme="compact">
                <vaadin-grid-column header="Key" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.key}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column path="type" header="Type" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Value" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.value}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column header="" auto-width
                        ${columnBodyRenderer((item) => html`
                            <vaadin-button theme="small error" @click=${() => this._delete(item.key)}>Delete</vaadin-button>`, [])}>
                </vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _refresh = () => {
        return this.jsonRpc.state({ query: this._query })
            .then((response) => {
                const state = response.result || {};
                this._supported = state.supported !== false;
                this._entries = state.entries || [];
                this._error = state.error || null;
            })
            .catch((error) => this._error = this._message(error));
    };

    _delete = (key) => {
        this.jsonRpc.deleteState({ key })
            .then((response) => {
                if (response.result && response.result.ok) {
                    notifier.showSuccessMessage('Deleted ' + key);
                    return this._refresh();
                }
                notifier.showErrorMessage('Delete failed: ' + ((response.result && response.result.error) || 'unknown'));
            })
            .catch((error) => notifier.showErrorMessage('Delete failed: ' + this._message(error)));
    };

    _message(error) {
        return (error && (error.message || error.error)) || String(error);
    }
}

customElements.define('qwc-pathland-state', QwcPathlandState);
