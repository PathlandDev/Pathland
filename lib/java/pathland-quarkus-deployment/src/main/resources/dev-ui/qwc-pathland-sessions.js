import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/checkbox';
import { notifier } from 'notifier';
import { JsonRpc } from 'jsonrpc';

const REFRESH_MILLIS = 2000;

export class QwcPathlandSessions extends LitElement {

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
            gap: 16px;
            padding: 8px 12px 0 12px;
        }
        .toolbar .spacer {
            flex: 1;
        }
        .muted {
            color: var(--lumo-contrast-60pct);
        }
        .ok {
            color: var(--lumo-success-text-color, #2e7d32);
        }
        .bad {
            color: var(--lumo-error-text-color, #c62828);
        }
        .mono {
            font-family: var(--lumo-monospace-font, monospace);
            font-size: var(--lumo-font-size-s);
        }
        .empty {
            padding: 48px;
            text-align: center;
            color: var(--lumo-contrast-60pct);
        }
        .error {
            margin: 8px 12px;
            padding: 8px 12px;
            border-radius: var(--lumo-border-radius-m);
            background: var(--lumo-error-color-10pct, #fdecea);
            color: var(--lumo-error-text-color, #c62828);
        }
        vaadin-grid {
            flex: 1;
            min-height: 0;
        }
    `;

    static properties = {
        _sessions: { state: true },
        _loading: { state: true },
        _error: { state: true },
        _autoRefresh: { state: true }
    };

    constructor() {
        super();
        this._sessions = [];
        this._loading = false;
        this._error = null;
        this._autoRefresh = true;
        this._timer = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
        this._timer = setInterval(() => {
            if (this._autoRefresh) {
                this._refresh();
            }
        }, REFRESH_MILLIS);
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
                <vaadin-button theme="small" @click=${this._refresh}>
                    Refresh
                </vaadin-button>
                <vaadin-checkbox
                        ?checked=${this._autoRefresh}
                        @change=${(event) => this._setAutoRefresh(event.target.checked)}>
                    Auto-refresh
                </vaadin-checkbox>
                <span class="muted">${this._sessions.length} session(s)</span>
                <span class="spacer"></span>
                <vaadin-button theme="small" @click=${this._resyncAll}>
                    Resync all
                </vaadin-button>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            ${this._sessions.length === 0
                ? html`<div class="empty">${this._loading ? 'Loading…' : 'No live sessions yet.'}</div>`
                : this._renderGrid()}
        `;
    }

    _renderGrid() {
        return html`
            <vaadin-grid .items=${this._sessions} theme="compact">
                <vaadin-grid-column path="mount" header="Mount" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Session" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.shortId}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column path="route" header="Route" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="stateScope" header="State scope" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Connected" auto-width
                        ${columnBodyRenderer((item) => html`
                            <span class=${item.connected ? 'ok' : 'bad'}>${item.connected ? 'yes' : 'no'}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column path="age" header="Age" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="viewport" header="Viewport" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="batches" header="Batches" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="opcodes" header="Opcodes" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="bytes" header="Bytes" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="sequence" header="Seq" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="subscriptions" header="Subs" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="proxies" header="Proxies" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="nodes" header="Nodes" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Actions" auto-width
                        ${columnBodyRenderer((item) => html`
                            <vaadin-button theme="small" @click=${() => this._resync(item.id)}>
                                Resync
                            </vaadin-button>
                            <vaadin-button theme="small error" @click=${() => this._closeSession(item.id)}>
                                Close
                            </vaadin-button>`, [])}>
                </vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _refresh = () => {
        this._loading = true;
        return this.jsonRpc.sessions()
            .then((response) => {
                const sessions = response.result || [];
                this._sessions = sessions.map((session) => this._flatten(session));
                this._error = null;
            })
            .catch((error) => {
                this._error = this._message(error);
            })
            .finally(() => {
                this._loading = false;
            });
    };

    _flatten(session) {
        const stats = session.stats || {};
        return {
            id: session.id,
            shortId: (session.id || '').slice(0, 8),
            mount: session.mount,
            route: session.route,
            stateScope: session.stateScope,
            connected: session.connected,
            age: this._formatAge(session.openedAtMillis),
            viewport: this._formatViewport(session.viewportWidth, session.viewportHeight),
            batches: stats.batchesSent ?? 0,
            opcodes: stats.lastOpcodes ?? 0,
            bytes: stats.lastBytes ?? 0,
            sequence: stats.sequence ?? 0,
            subscriptions: (session.subscriptions || []).length,
            proxies: session.proxyCount ?? 0,
            nodes: session.treeNodes ?? 0
        };
    }

    _setAutoRefresh(enabled) {
        this._autoRefresh = enabled;
        if (enabled) {
            this._refresh();
        }
    }

    _resync = (sessionId) => {
        this.jsonRpc.resync({ sessionId })
            .then((response) => {
                if (response.result && response.result.ok) {
                    notifier.showSuccessMessage('Session resynced');
                } else {
                    notifier.showErrorMessage('Resync failed: ' + ((response.result && response.result.error) || 'unknown error'));
                }
                return this._refresh();
            })
            .catch((error) => notifier.showErrorMessage('Resync failed: ' + this._message(error)));
    };

    _resyncAll = () => {
        this.jsonRpc.resyncAll()
            .then((response) => {
                if (response.result && response.result.ok) {
                    notifier.showSuccessMessage('All sessions resynced');
                } else {
                    notifier.showErrorMessage('Resync failed: ' + ((response.result && response.result.error) || 'unknown error'));
                }
                return this._refresh();
            })
            .catch((error) => notifier.showErrorMessage('Resync failed: ' + this._message(error)));
    };

    _closeSession = (sessionId) => {
        this.jsonRpc.closeSession({ sessionId })
            .then((response) => {
                if (response.result && response.result.ok) {
                    notifier.showSuccessMessage('Session closed');
                } else {
                    notifier.showErrorMessage('Close failed: ' + ((response.result && response.result.error) || 'unknown error'));
                }
                return this._refresh();
            })
            .catch((error) => notifier.showErrorMessage('Close failed: ' + this._message(error)));
    };

    _formatAge(openedAtMillis) {
        if (!openedAtMillis) {
            return '—';
        }
        const seconds = Math.max(0, Math.floor((Date.now() - openedAtMillis) / 1000));
        if (seconds < 60) {
            return seconds + 's';
        }
        const minutes = Math.floor(seconds / 60);
        if (minutes < 60) {
            return minutes + 'm';
        }
        return Math.floor(minutes / 60) + 'h ' + (minutes % 60) + 'm';
    }

    _formatViewport(width, height) {
        const w = width > 0 ? Math.round(width) : '—';
        const h = height > 0 ? Math.round(height) : '—';
        return w + ' × ' + h;
    }

    _message(error) {
        return (error && (error.message || error.error)) || String(error);
    }
}

customElements.define('qwc-pathland-sessions', QwcPathlandSessions);
