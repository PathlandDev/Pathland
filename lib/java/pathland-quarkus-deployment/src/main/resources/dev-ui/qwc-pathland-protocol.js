import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/select';
import { protocol } from 'build-time-data';

export class QwcPathlandProtocol extends LitElement {

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
        vaadin-grid {
            flex: 1;
            min-height: 0;
        }
        .mono {
            font-family: var(--lumo-monospace-font, monospace);
            font-size: var(--lumo-font-size-s);
        }
    `;

    static properties = {
        _query: { state: true },
        _kind: { state: true }
    };

    constructor() {
        super();
        this._query = '';
        this._kind = '';
    }

    render() {
        return html`
            <div class="toolbar">
                <vaadin-text-field placeholder="Search name, group or value" clear-button-visible
                        style="min-width: 320px"
                        @value-changed=${(event) => this._query = event.detail.value}>
                </vaadin-text-field>
                <vaadin-select label="Kind" .items=${this._kindItems()} .value=${this._kind}
                        item-label-path="label" item-value-path="value"
                        @value-changed=${(event) => this._kind = event.detail.value}>
                </vaadin-select>
                <span class="spacer"></span>
                <span class="muted">${this._filtered().length} row(s)</span>
            </div>
            <vaadin-grid .items=${this._filtered()} theme="compact">
                <vaadin-grid-column path="kind" header="Kind" auto-width></vaadin-grid-column>
                <vaadin-grid-column path="group" header="Group" auto-width></vaadin-grid-column>
                <vaadin-grid-column header="Name" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.name}</span>`, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column header="Value" auto-width
                        ${columnBodyRenderer((item) => html`<span class="mono">${item.value}</span>`, [])}>
                </vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _kindItems() {
        const kinds = Array.from(new Set((protocol || []).map((row) => row.kind))).sort();
        return [{ label: 'All', value: '' }, ...kinds.map((kind) => ({ label: kind, value: kind }))];
    }

    _filtered() {
        const query = (this._query || '').toLowerCase();
        const kind = this._kind;
        return (protocol || []).filter((row) => {
            if (kind && row.kind !== kind) return false;
            if (!query) return true;
            return [row.kind, row.group, row.name, row.value]
                .some((value) => (value || '').toLowerCase().includes(query));
        });
    }
}

customElements.define('qwc-pathland-protocol', QwcPathlandProtocol);
