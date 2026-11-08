import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/select';
import { notifier } from 'notifier';
import { JsonRpc } from 'jsonrpc';

const REFRESH_MILLIS = 2000;

export class QwcPathlandTopics extends LitElement {

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
        vaadin-grid { flex: 1; min-height: 160px; }
        .publish {
            display: flex; align-items: end; gap: 10px; flex-wrap: wrap;
            padding: 0 12px 12px 12px;
            border-top: 1px solid var(--lumo-contrast-10pct);
        }
        .publish h4 { margin: 10px 0 0 0; color: var(--lumo-contrast-70pct); width: 100%; }
        .mono { font-family: var(--lumo-monospace-font, monospace); font-size: var(--lumo-font-size-s); }
    `;

    static properties = {
        _mount: { state: true },
        _rows: { state: true },
        _error: { state: true },
        _topic: { state: true },
        _subject: { state: true },
        _subjectType: { state: true },
        _valueType: { state: true },
        _value: { state: true }
    };

    constructor() {
        super();
        this._mount = '/';
        this._rows = [];
        this._error = null;
        this._topic = '';
        this._subject = '';
        this._subjectType = 'string';
        this._valueType = 'string';
        this._value = '';
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
                <vaadin-text-field label="Mount" .value=${this._mount}
                        @value-changed=${(event) => this._mount = event.detail.value}>
                </vaadin-text-field>
                <vaadin-button theme="small" @click=${this._refresh}>Refresh</vaadin-button>
                <span class="spacer"></span>
                <span class="muted">${this._rows.length} subscription(s)</span>
            </div>
            ${this._error ? html`<div class="error">${this._error}</div>` : ''}
            <vaadin-grid .items=${this._rows} theme="compact">
                <vaadin-grid-column header="Topic" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.topic}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column header="Subject" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.subject}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column path="subscribers" header="Subscribers" auto-width></vaadin-grid-column>
            </vaadin-grid>
            <div class="publish">
                <h4>Publish a value</h4>
                <vaadin-text-field label="Topic" .value=${this._topic}
                        @value-changed=${(event) => this._topic = event.detail.value}>
                </vaadin-text-field>
                <vaadin-text-field label="Subject" .value=${this._subject}
                        @value-changed=${(event) => this._subject = event.detail.value}>
                </vaadin-text-field>
                <vaadin-select label="Subject type" .items=${this._types()} .value=${this._subjectType}
                        item-label-path="label" item-value-path="value"
                        @value-changed=${(event) => this._subjectType = event.detail.value}>
                </vaadin-select>
                <vaadin-text-field label="Value" .value=${this._value}
                        @value-changed=${(event) => this._value = event.detail.value}>
                </vaadin-text-field>
                <vaadin-select label="Value type" .items=${this._types()} .value=${this._valueType}
                        item-label-path="label" item-value-path="value"
                        @value-changed=${(event) => this._valueType = event.detail.value}>
                </vaadin-select>
                <vaadin-button theme="primary" @click=${this._publish}>Publish</vaadin-button>
            </div>
        `;
    }

    _types() {
        return [
            { label: 'string', value: 'string' },
            { label: 'number', value: 'number' },
            { label: 'boolean', value: 'boolean' },
            { label: 'null', value: 'null' }
        ];
    }

    _refresh = () => {
        return this.jsonRpc.topics({ mount: this._mount })
            .then((response) => {
                this._rows = response.result || [];
                this._error = null;
            })
            .catch((error) => this._error = this._message(error));
    };

    _publish = () => {
        this.jsonRpc.publishTopic({
            mount: this._mount,
            topic: this._topic,
            subject: this._subject,
            subjectType: this._subjectType,
            valueType: this._valueType,
            value: this._value
        }).then((response) => {
            if (response.result && response.result.ok) {
                notifier.showSuccessMessage('Published');
                return this._refresh();
            }
            notifier.showErrorMessage('Publish failed: ' + ((response.result && response.result.error) || 'unknown'));
        }).catch((error) => notifier.showErrorMessage('Publish failed: ' + this._message(error)));
    };

    _message(error) {
        return (error && (error.message || error.error)) || String(error);
    }
}

customElements.define('qwc-pathland-topics', QwcPathlandTopics);
