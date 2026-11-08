import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/button';
import { notifier } from 'notifier';
import { JsonRpc } from 'jsonrpc';

const REFRESH_MILLIS = 2000;

export class QwcPathlandTheme extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host { display: flex; flex-direction: column; gap: 10px; height: 100%; }
        .toolbar {
            display: flex; align-items: center; gap: 12px;
            padding: 8px 12px 0 12px;
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
        .actions { display: flex; gap: 4px; flex-wrap: wrap; }
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
                <span class="muted">${this._rows.length} session(s)</span>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            <vaadin-grid .items=${this._rows} theme="compact">
                <vaadin-grid-column header="Mount" path="mount" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Session" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.shortId}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column path="route" header="Route" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="colorScheme" header="Scheme" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Overrides" auto-width
                        ${columnBodyRenderer((item) => html`<span>${item.themeOverrides ? 'yes' : 'no'}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column header="Actions" auto-width
                        ${columnBodyRenderer((item) => html`
                            <div class="actions">
                                <vaadin-button theme="small" @click=${() => this._scheme(item.id, 'platform')}>Platform</vaadin-button>
                                <vaadin-button theme="small" @click=${() => this._scheme(item.id, 'light')}>Light</vaadin-button>
                                <vaadin-button theme="small" @click=${() => this._scheme(item.id, 'dark')}>Dark</vaadin-button>
                                <vaadin-button theme="small error" @click=${() => this._reset(item.id)}>Reset theme</vaadin-button>
                            </div>`, [])}>
                </vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _refresh = () => {
        return this.jsonRpc.sessions()
            .then((response) => {
                this._rows = (response.result || []).map((session) => ({
                    ...session,
                    shortId: (session.id || '').slice(0, 8)
                }));
                this._error = null;
            })
            .catch((error) => this._error = this._message(error));
    };

    _scheme = (sessionId, mode) => {
        this.jsonRpc.setColorScheme({ sessionId, mode })
            .then((response) => {
                if (!response.result || !response.result.ok) {
                    notifier.showErrorMessage('Color scheme failed: ' + ((response.result && response.result.error) || 'unknown'));
                }
                return this._refresh();
            })
            .catch((error) => notifier.showErrorMessage('Color scheme failed: ' + this._message(error)));
    };

    _reset = (sessionId) => {
        this.jsonRpc.resetTheme({ sessionId })
            .then((response) => {
                if (!response.result || !response.result.ok) {
                    notifier.showErrorMessage('Reset failed: ' + ((response.result && response.result.error) || 'unknown'));
                } else {
                    notifier.showSuccessMessage('Theme overrides cleared');
                }
                return this._refresh();
            })
            .catch((error) => notifier.showErrorMessage('Reset failed: ' + this._message(error)));
    };

    _message(error) {
        return (error && (error.message || error.error)) || String(error);
    }
}

customElements.define('qwc-pathland-theme', QwcPathlandTheme);
