import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/text-field';
import { notifier } from 'notifier';
import { JsonRpc } from 'jsonrpc';
import { components, properties } from 'build-time-data';

export class QwcPathlandTree extends LitElement {

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
        .tree {
            flex: 1; overflow: auto;
            padding: 0 12px 12px 12px;
            font-family: var(--lumo-monospace-font, monospace);
            font-size: var(--lumo-font-size-s);
        }
        ul { list-style: none; margin: 0; padding-left: 18px; border-left: 1px dashed var(--lumo-contrast-20pct); }
        li { margin: 2px 0; }
        .node { display: inline-flex; gap: 8px; align-items: baseline; }
        .id { color: var(--lumo-contrast-50pct); }
        .component { color: var(--lumo-primary-text-color, #1565c0); font-weight: 600; }
        .text { color: var(--lumo-contrast-80pct); }
        .props { color: var(--lumo-contrast-50pct); font-size: var(--lumo-font-size-xs); }
        .badge {
            font-size: var(--lumo-font-size-xs);
            padding: 0 5px; border-radius: 999px;
            background: var(--lumo-success-color-10pct, #e8f5e9);
            color: var(--lumo-success-text-color, #2e7d32);
        }
    `;

    static properties = {
        _sessionId: { state: true },
        _root: { state: true },
        _error: { state: true }
    };

    constructor() {
        super();
        this._sessionId = '';
        this._root = null;
        this._error = null;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.sessions()
            .then((response) => {
                const sessions = response.result || [];
                if (sessions.length && !this._sessionId) {
                    this._sessionId = sessions[0].id;
                    this._load();
                }
            })
            .catch(() => {});
    }

    render() {
        return html`
            <div class="toolbar">
                <vaadin-text-field label="Session" .value=${this._sessionId}
                        style="min-width: 320px"
                        @value-changed=${(event) => this._sessionId = event.detail.value}>
                </vaadin-text-field>
                <vaadin-button theme="small" @click=${this._load}>Load</vaadin-button>
                <span class="spacer"></span>
                <span class="muted">${this._root ? this._count(this._root) + ' nodes' : ''}</span>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            <div class="tree">
                ${this._root ? html`<ul>${this._node(this._root)}</ul>` : html`<span class="muted">Load a live session to inspect its retained tree.</span>`}
            </div>
        `;
    }

    _node(node) {
        return html`
            <li>
                <span class="node">
                    <span class="id">#${node.id}</span>
                    <span class="component">${this._component(node.component)}</span>
                    ${node.text ? html`<span class="text">${node.text}</span>` : ''}
                    ${node.reactiveText ? html`<span class="badge">reactive</span>` : ''}
                    ${this._props(node)}
                </span>
                ${node.children && node.children.length
                    ? html`<ul>${node.children.map((child) => this._node(child))}</ul>`
                    : ''}
            </li>
        `;
    }

    _props(node) {
        const entries = Object.entries(node.properties || {});
        if (!entries.length) {
            return '';
        }
        const text = entries
            .map(([id, value]) => `${this._property(id)}=${value}`)
            .join(', ');
        return html`<span class="props">[${text}]</span>`;
    }

    _component(id) {
        return components[id] || `0x${Number(id).toString(16)}`;
    }

    _property(id) {
        return properties[id] || `0x${Number(id).toString(16)}`;
    }

    _count(node) {
        return 1 + (node.children || []).reduce((total, child) => total + this._count(child), 0);
    }

    _load = () => {
        if (!this._sessionId) {
            notifier.showErrorMessage('Enter a session id');
            return;
        }
        return this.jsonRpc.tree({ sessionId: this._sessionId })
            .then((response) => {
                const result = response.result || {};
                this._error = result.error || null;
                this._root = result.root || null;
                if (result.error) {
                    notifier.showErrorMessage('Tree failed: ' + result.error);
                }
            })
            .catch((error) => this._error = (error && (error.message || error.error)) || String(error));
    };
}

customElements.define('qwc-pathland-tree', QwcPathlandTree);
