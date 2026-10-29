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

function table(headers, rows, footer = '') {
  return `<table class="lab-table"><thead><tr>${headers.map(h => `<th>${escape(h)}</th>`).join('')}</tr></thead><tbody>${rows}</tbody>${footer ? `<tfoot>${footer}</tfoot>` : ''}</table>`;
}

export function blockElement(attrs, editing = false) {
  const root = document.createElement('section');
  root.className = `ton-block ${attrs.compact ? 'compact' : ''}`;
  root.dataset.tonBlock = attrs.kind;
  root.dataset.blockId = attrs.id;
  root.dataset.title = attrs.title || blockTypes[attrs.kind];
  root.dataset.compact = String(Boolean(attrs.compact));
  root.dataset.engineering = String(Boolean(attrs.engineering));
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
      body = sample.formats.map(f => `<div class="format-card"><div class="format-heading"><b>${f.code}</b><b>${f.count} dischi</b></div><p class="dimensions">Ø ${f.diameter} mm · nominale ${f.nominal} mm<br><strong>Spessore totale: ${f.thickness} mm</strong></p>${table(['Strato', 'g / strato', 'N°', 'Totale g'], f.layers.map(l => `<tr><td>${l.number}${attrs.engineering ? `<small>${l.ppm} ppm</small>` : ''}</td><td>${l.weight}</td><td>${f.count}</td><td>${l.total}</td></tr>`).join(''), `<tr><td>Totale disco</td><td>${f.weight} g</td><td colspan="2">${f.batchWeight} g</td></tr>`)}${attrs.engineering ? `<small>Coefficiente del modello: ${sample.coefficient} g/mm</small>` : ''}</div>`).join('');
      break;
    case 'mixtures':
      body = sample.layers.map(l => `<div class="mixture-card"><div class="format-heading"><b>Strato ${l.number}</b><span>${l.count} strati</span></div>${table(['Polvere', '% massa', 'Da pesare (g)'], l.powders.map(p => `<tr><td>${escape(p.code)}</td><td>${p.percentage}</td><td><b>${p.weight}</b></td></tr>`).join(''), `<tr><td>Da preparare</td><td>100%</td><td>${l.total}</td></tr>`)}</div>`).join('') + '<p class="block-note">Maggiorazione × 1,005 · Pesi a 0,1 g.</p>';
      break;
    case 'totals':
      body = `<div class="production-totals"><div><small>ARTICOLI TOTALI</small><strong>${sample.quantity} dischi</strong></div><div><small>DA DOSARE</small><strong>${sample.needed} g</strong></div><div><small>MISCELA DA PREPARARE · +0,5%</small><strong>${sample.prepared} g</strong></div></div>`;
      break;
    case 'signatures':
      body = '<div class="signature-lines"><div>Preparato da<span>Data e firma</span></div><div>Verificato da<span>Data e firma</span></div></div>';
      break;
    default: throw new Error('Blocco di laboratorio non supportato.');
  }
  root.innerHTML = `${editing ? '<div class="block-handle" data-drag-handle title="Trascina per spostare il blocco">⠿ <span>Blocco automatico</span></div>' : ''}<h3>${label}</h3><div class="block-content">${body}</div>`;
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
  return { type: 'doc', content: [
    { type: 'heading', attrs: { level: 1 }, content: [{ type: 'text', text: 'Foglio operativo di pressatura' }] },
    block('identity'), block('press'),
    { type: 'tonColumns', content: [
      { type: 'tonColumn', content: [block('weighing'), block('totals')] },
      { type: 'tonColumn', content: [block('mixtures')] },
    ] },
    { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Note di lavorazione' }] },
    { type: 'paragraph', content: [{ type: 'text', text: 'Scrivi qui le istruzioni per il laboratorio.' }] },
    block('signatures'), { type: 'paragraph' },
  ] };
}
