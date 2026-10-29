import { optionDefaults, blockColumns, visibleFormats, visiblePowders, powderLabel } from './block-options.js';
import { normalizeLayoutJSON } from './layout-model.js';
import sample from './sample-data.json';

// Fixture-only presentation. Actual production calculations belong to TON services.
export const blockTypes = Object.freeze({
  identity: 'Dati della produzione',
  press: 'Parametri della pressa',
  weighing: 'Pesature per formato',
  mixtures: 'Miscele da preparare',
  totals: 'Totali della produzione',
  signatures: 'Spazio firme',
});
export const fields = Object.freeze({
  production: ['Numero produzione', sample.production],
  date: ['Data produzione', sample.date],
  line: ['Linea', sample.line],
  product: ['Prodotto', sample.product],
  quantity: ['Numero dischi', String(sample.quantity)],
});
export const escape = value => String(value ?? '').replace(/[&<>"']/g,
  c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function table(headers, rows, footer = '', widths) {
  return `<table class="lab-table"${widths ? ' style="table-layout:fixed"' : ''}>${widths ? `<colgroup>${widths.map(width => `<col style="width:${width}%">`).join('')}</colgroup>` : ''}<thead><tr>${headers.map(h => `<th>${escape(h)}</th>`).join('')}</tr></thead><tbody>${rows}</tbody>${footer ? `<tfoot>${footer}</tfoot>` : ''}</table>`;
}

export function blockElement(attrs, editing = false) {
  attrs = { ...optionDefaults, ...attrs };
  const root = document.createElement('section');
  root.className = `ton-block ${attrs.compact ? 'compact' : ''} ${attrs.pageBreakBefore ? 'page-break-before' : ''} ${attrs.keepCardsTogether ? 'keep-cards' : ''} ${attrs.repeatHeaders ? 'repeat-headers' : ''} ${attrs.highlightTotals ? 'highlight-totals' : ''} ${attrs.emphasizeWeights ? 'weights-emphasis' : ''}`;
  root.dataset.options = JSON.stringify(Object.fromEntries(Object.keys(optionDefaults).map(key => [key, attrs[key]])));
  if (attrs.headerColor) root.style.setProperty('--header-bg', attrs.headerColor);
  root.dataset.tonBlock = attrs.kind;
  root.dataset.blockId = attrs.id;
  root.dataset.title = attrs.title || blockTypes[attrs.kind];
  root.dataset.compact = String(Boolean(attrs.compact));
  root.dataset.showPpm = String(Boolean(attrs.showPpm));
  root.dataset.showCoefficient = String(Boolean(attrs.showCoefficient));
  root.dataset.fontFamily = attrs.fontFamily;
  root.dataset.fontSize = attrs.fontSize;
  root.dataset.color = attrs.color;
  root.style.fontFamily = attrs.fontFamily;
  root.style.setProperty('--block-font', attrs.fontSize);
  root.style.setProperty('--block-ink', attrs.color);
  root.contentEditable = 'false';
  const label = escape(attrs.title || blockTypes[attrs.kind]);
  let body = '';
  switch (attrs.kind) {
    case 'identity':
      body = `<div class="production-fields">${['production', 'date', 'product'].map(k => `<div><small>${escape(fields[k][0])}</small><b>${escape(fields[k][1])}</b></div>`).join('')}</div>`;
      break;
    case 'press':
      body = `<div class="press-setting"><div><small>PRESSIONE DA IMPOSTARE · ${sample.machine}</small><strong>${sample.pressure} <span>bar</span></strong></div><p>Ricetta ${sample.recipe}<br>Modello ${sample.model}</p></div>`;
      break;
    case 'weighing':
      body = visibleFormats(sample.formats, attrs).map(f => {
        const columns = blockColumns(attrs), headers = columns.map(c => c[1]);
        const rows = f.layers.map(l => {
          const cells = { layer: l.number, ppm: l.ppm, weight: l.weight, count: f.count, total: l.total };
          return `<tr>${columns.map(([key]) => `<td>${escape(cells[key])}</td>`).join('')}</tr>`;
        }).join('');
        const footer = `<tr><td colspan="${attrs.showPpm ? 2 : 1}">Totale disco</td><td>${f.weight} g</td><td colspan="2">${f.batchWeight} g</td></tr>`;
        return `<div class="format-card"><div class="format-heading"><b>${escape(f.code)}</b><b>${f.count} dischi</b></div><p class="dimensions">${attrs.showDiameter ? `<span>Ø ${f.diameter} mm</span>` : ''}${attrs.showNominal ? `<span>nominale ${f.nominal} mm</span>` : ''}${attrs.showCoefficient ? `<span>${sample.coefficient} g/mm</span>` : ''}<strong>Spessore totale: ${f.thickness} mm</strong></p>${table(headers, rows, footer, attrs.tableWidths)}</div>`;
      }).join('');
      break;
    case 'mixtures':
      body = sample.layers.map(l => `<div class="mixture-card"><div class="format-heading"><b>Strato ${l.number}</b><span>${l.count} strati</span></div>${table(blockColumns(attrs).map(c => c[1]), visiblePowders(l.powders, attrs).map(p => `<tr><td>${escape(powderLabel(p, attrs.powderLabel))}</td>${attrs.showPercent ? `<td>${p.percentage}</td>` : ''}<td class="powder-weight">${p.weight}</td></tr>`).join(''), `<tr><td>Da preparare</td>${attrs.showPercent ? '<td>100%</td>' : ''}<td>${l.total}</td></tr>`, attrs.tableWidths)}</div>`).join('') + '<p class="block-note">Maggiorazione × 1,005 · Pesi a 0,1 g.</p>';
      break;
    case 'totals':
      body = `<div class="production-totals"><div><small>ARTICOLI TOTALI</small><strong>${sample.quantity} dischi</strong></div><div><small>DA DOSARE</small><strong>${sample.needed} g</strong></div><div><small>MISCELA DA PREPARARE · +0,5%</small><strong>${sample.prepared} g</strong></div></div>`;
      break;
    case 'signatures':
      body = '<div class="signature-lines"><div>Preparato da<span>Data e firma</span></div><div>Verificato da<span>Data e firma</span></div></div>';
      break;
    default: throw new Error('Blocco di laboratorio non supportato.');
  }
  root.innerHTML = `${editing ? '<div class="block-handle" data-drag-handle title="Trascina per spostare il blocco">⠿ <span>Blocco automatico</span></div>' : ''}${attrs.showTitle ? `<h3>${label}</h3>` : ''}<div class="block-content">${body}</div>`;
  return root;
}

export function checkOperationalContent(doc) {
  const present = new Set();
  doc.descendants(node => { if (node.type.name === 'tonBlock') present.add(node.attrs.kind); });
  return ['identity', 'press', 'weighing', 'mixtures', 'totals']
    .filter(kind => !present.has(kind)).map(kind => blockTypes[kind]);
}

export function initialContent() {
  const block = kind => ({ type: 'tonBlock', attrs: { id: `initial-${kind}`, kind, title: blockTypes[kind], compact: true } });
  return normalizeLayoutJSON({ type: 'doc', content: [
    { type: 'heading', attrs: { level: 1 }, content: [{ type: 'text', text: 'Foglio operativo di pressatura' }] },
    block('identity'), block('press'),
    { type: 'tonColumns', attrs: { widths: [60, 40], gap: 4 }, content: [
      { type: 'tonColumn', content: [block('weighing'), block('totals')] },
      { type: 'tonColumn', content: [block('mixtures')] },
    ] },
    { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Note di lavorazione' }] },
    { type: 'paragraph', content: [{ type: 'text', text: 'Scrivi qui le istruzioni per il laboratorio.' }] },
    block('signatures'), { type: 'paragraph' },
  ] });
}
