import { LitElement, html, css } from 'lit';
import '@vaadin/text-field';
import '@vaadin/button';
import '@vaadin/checkbox';
import { notifier } from 'notifier';
import { JsonRpc } from 'jsonrpc';

export class QwcPathlandSsr extends LitElement {

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
        .content {
            flex: 1;
            min-height: 0;
            padding: 0 12px 12px 12px;
        }
        iframe {
            width: 100%;
            height: 100%;
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            background: white;
        }
        pre {
            margin: 0;
            height: 100%;
            overflow: auto;
            padding: 12px;
            border: 1px solid var(--lumo-contrast-20pct);
            border-radius: var(--lumo-border-radius-m);
            font-family: var(--lumo-monospace-font, monospace);
            font-size: var(--lumo-font-size-s);
            white-space: pre-wrap;
            word-break: break-word;
        }
    `;

    static properties = {
        _mount: { state: true },
        _route: { state: true },
        _html: { state: true },
        _error: { state: true },
        _showSource: { state: true },
        _loading: { state: true }
    };

    constructor() {
        super();
        this._mount = '/';
        this._route = '/';
        this._html = '';
        this._error = null;
        this._showSource = false;
        this._loading = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this._render();
    }

    render() {
        return html`
            <div class="toolbar">
                <vaadin-text-field label="Mount" .value=${this._mount}
                        @value-changed=${(event) => this._mount = event.detail.value}>
                </vaadin-text-field>
                <vaadin-text-field label="Route" .value=${this._route}
                        @value-changed=${(event) => this._route = event.detail.value}>
                </vaadin-text-field>
                <vaadin-button theme="primary" ?disabled=${this._loading} @click=${this._render}>
                    Render
                </vaadin-button>
                <vaadin-checkbox ?checked=${this._showSource}
                        @change=${(event) => this._showSource = event.target.checked}>
                    Source
                </vaadin-checkbox>
                <span class="spacer"></span>
                <span class="muted">${this._html ? this._html.length + ' chars' : ''}</span>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            <div class="content">
                ${this._showSource
                    ? html`<pre>${this._html || 'No document rendered yet.'}</pre>`
                    : html`<iframe sandbox="allow-same-origin" .srcdoc=${this._html || ''}></iframe>`}
            </div>
        `;
    }

    _render = () => {
        this._loading = true;
        return this.jsonRpc.ssr({ mount: this._mount, route: this._route })
            .then((response) => {
                const preview = response.result || {};
                this._error = preview.error || null;
                this._html = preview.html || '';
                if (preview.error) {
                    notifier.showErrorMessage('SSR render failed: ' + preview.error);
                }
            })
            .catch((error) => {
                this._error = (error && (error.message || error.error)) || String(error);
            })
            .finally(() => {
                this._loading = false;
            });
    };
}

customElements.define('qwc-pathland-ssr', QwcPathlandSsr);
