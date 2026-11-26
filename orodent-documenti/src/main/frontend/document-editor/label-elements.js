import { Node } from '@tiptap/core';
import bwipjs from 'bwip-js';

export const labelFields = Object.freeze({
  article: 'Codice articolo',
  product: 'Codice prodotto',
  name: 'Nome prodotto',
  line: 'Linea',
  lot: 'Lotto di cottura',
  gtin: 'GTIN',
  diameter: 'Diametro nominale',
  height: 'Altezza nominale',
  temperature: 'Temperatura di cottura',
  production: 'Numero produzione',
  quantity: 'Dischi nel lavoro',
  furnace: 'Forno',
  productionDate: 'Data produzione',
  expiryDate: 'Data scadenza',
});
const sample = {
  article: 'ZR9816MSTA3',
  product: 'ZRMSTA3',
  name: 'Thor A3',
  line: 'Thor',
  lot: '260922',
  gtin: '09506000134352',
  diameter: '98',
  height: '16',
  temperature: '1450',
  production: 'PR-000001',
  quantity: '16',
  furnace: 'F1',
  productionDate: '22/09/2026',
  expiryDate: '30/09/2031',
  productionDateGs1: '260922',
  expiryDateGs1: '310930',
};
let data = sample;
export function setLabelData(value) {
  data = value ?? sample;
}
export function resolveLabel(value) {
  if (typeof value !== 'string' || value.length > 500)
    throw new Error('Valore etichetta non valido.');
  return value.replace(/\{\{([a-zA-Z][a-zA-Z0-9]*)\}\}/g, (_, key) => {
    if (!(key in sample)) throw new Error('Campo etichetta sconosciuto: ' + key);
    if (!String(data[key] ?? '').trim())
      throw new Error('Dato mancante per l’etichetta: ' + (labelFields[key] ?? key));
    return String(data[key]);
  });
}
export function gs1Text(attrs, resolve = resolveLabel) {
  const gtin = resolve(attrs.gtin),
    lot = attrs.lot ? resolve(attrs.lot) : '',
    made = attrs.productionDate ? resolve(attrs.productionDate) : '',
    expires = attrs.expiryDate ? resolve(attrs.expiryDate) : '';
  if (!/^\d{14}$/.test(gtin)) throw new Error('GS1: AI 01 richiede un GTIN di 14 cifre.');
  let sum = 0;
  for (let i = 12, w = 3; i >= 0; i--, w = 4 - w) sum += Number(gtin[i]) * w;
  if ((10 - (sum % 10)) % 10 !== Number(gtin[13]))
    throw new Error('GS1: cifra di controllo GTIN non valida.');
  if (lot && (!/^[\x21-\x7e]{1,20}$/.test(lot) || /[()]/.test(lot)))
    throw new Error('GS1: il lotto deve contenere da 1 a 20 caratteri senza parentesi.');
  for (const [ai, date] of [
    ['11', made],
    ['17', expires],
  ])
    if (date) {
      if (!/^\d{6}$/.test(date)) throw new Error('GS1: AI ' + ai + ' richiede una data AAMMGG.');
      const y = 2000 + Number(date.slice(0, 2)),
        m = Number(date.slice(2, 4)),
        d = Number(date.slice(4));
      if (m < 1 || m > 12 || d > new Date(Date.UTC(y, m, 0)).getUTCDate())
        throw new Error('GS1: data non valida per AI ' + ai + '.');
    }
  return (
    '(01)' +
    gtin +
    (lot ? '(10)' + lot : '') +
    (made ? '(11)' + made : '') +
    (expires ? '(17)' + expires : '')
  );
}
export function matrixOptions(attrs) {
  return {
    bcid: 'gs1datamatrix',
    text: gs1Text(attrs),
    scale: 5,
    padding: attrs.tight ? 0 : 5,
    includetext: false,
  };
}
export function matrixDimensions(attrs) {
  const options = matrixOptions(attrs),
    [raw] = bwipjs.raw(options);
  const svg = bwipjs.toSVG(options),
    box = /viewBox="0 0 (\d+) (\d+)"/.exec(svg);
  return { width: Number(box[1]), height: Number(box[2]), columns: raw.pixx, rows: raw.pixy };
}
export function validateMatrix(attrs) {
  if (attrs.tight !== undefined && typeof attrs.tight !== 'boolean')
    throw new Error('Formato del DataMatrix non valido.');
  for (const key of ['gtin', 'lot', 'productionDate', 'expiryDate'])
    if (typeof attrs[key] !== 'string' || attrs[key].length > 500)
      throw new Error('Campo GS1 non valido.');
  if (!Number.isFinite(attrs.size) || attrs.size < 8 || attrs.size > 80)
    throw new Error('Dimensione GS1: da 8 a 80 mm.');
}
function matrixElement(attrs) {
  validateMatrix(attrs);
  const wrapper = document.createElement('div');
  wrapper.className = 'gs1-matrix';
  wrapper.style.cssText = `width:${attrs.size}mm;max-width:100%;break-inside:avoid;`;
  const canvas = document.createElement('canvas');
  try {
    bwipjs.toCanvas(canvas, matrixOptions(attrs));
    const img = document.createElement('img');
    img.src = canvas.toDataURL('image/png');
    img.style.cssText = 'display:block;width:100%;height:auto;image-rendering:pixelated;';
    if (attrs.tight) {
      wrapper.classList.add('gs1-tight');
      const [raw] = bwipjs.raw(matrixOptions(attrs));
      // Paint a one-module quiet zone outside the selectable code bounds.
      // Shadows do not create scroll overflow or change snap geometry.
      img.style.background = '#fff';
      img.style.boxShadow = `0 0 0 calc(var(--object-width, ${attrs.size}mm) / ${raw.pixx}) #fff`;
    }
    img.alt = gs1Text(attrs);
    wrapper.append(img);
  } catch (error) {
    const warning = document.createElement('span');
    warning.className = 'matrix-error';
    warning.textContent = error.message;
    wrapper.append(warning);
  }
  wrapper.dataset.gs1 = JSON.stringify(attrs);
  return wrapper;
}
export const Gs1Matrix = Node.create({
  name: 'gs1Matrix',
  group: 'block',
  atom: true,
  draggable: true,
  addAttributes() {
    return {
      gtin: { default: '{{gtin}}' },
      lot: { default: '{{lot}}' },
      productionDate: { default: '' },
      expiryDate: { default: '' },
      size: { default: 20 },
      tight: { default: false },
    };
  },
  parseHTML() {
    return [
      {
        tag: 'div[data-gs1]',
        getAttrs: (e) => {
          try {
            return JSON.parse(e.dataset.gs1);
          } catch {
            return false;
          }
        },
      },
    ];
  },
  renderHTML({ node }) {
    return matrixElement(node.attrs);
  },
});
export const LabelField = Node.create({
  name: 'labelField',
  group: 'inline',
  inline: true,
  atom: true,
  addAttributes() {
    return { key: { default: 'article' } };
  },
  parseHTML() {
    return [{ tag: 'span[data-label-field]', getAttrs: (e) => ({ key: e.dataset.labelField }) }];
  },
  renderHTML({ node }) {
    const key = node.attrs.key;
    if (!(key in labelFields)) throw new Error('Campo etichetta non supportato.');
    return [
      'span',
      { class: 'label-field', 'data-label-field': key, title: labelFields[key] },
      String(data[key] ?? ''),
    ];
  },
});
// Standalone automatic values are atomic blocks, never editable text containers.
export const LabelValue = Node.create({
  name: 'labelValue',
  group: 'block',
  atom: true,
  selectable: false,
  addAttributes() {
    return { key: { default: 'article' } };
  },
  parseHTML() {
    return [{ tag: 'div[data-label-value]', getAttrs: (e) => ({ key: e.dataset.labelValue }) }];
  },
  renderHTML({ node }) {
    const key = node.attrs.key;
    if (!(key in labelFields)) throw new Error('Dato automatico non supportato.');
    return ['div', { class: 'label-value', 'data-label-value': key }, String(data[key] ?? '')];
  },
});

export function measureLabel(key, attrs = {}) {
  const probe = document.createElement('span');
  probe.style.cssText = `position:fixed;left:-50000px;top:0;display:inline-block;white-space:pre;max-width:none;padding:0;margin:0;border:0;font-family:${attrs.fontFamily ?? 'Arial'};font-size:${attrs.fontSize ?? '11pt'};line-height:${attrs.lineHeight ?? 1.2};font-weight:${attrs.bold ? 700 : 400};font-style:${attrs.italic ? 'italic' : 'normal'};`;
  probe.textContent = resolveLabel('{{' + key + '}}');
  document.body.append(probe);
  const rect = probe.getBoundingClientRect();
  probe.remove();
  return {
    width: Math.max(8, Math.ceil(rect.width) + 1),
    height: Math.max(8, Math.ceil(rect.height)),
  };
}

// Upgrade only editable objects containing exactly one field, preserving mixed text and history.
export function upgradeLabelObjects(doc) {
  let changed = false;
  const content = (doc.content ?? []).map((object) => {
    const paragraph = object.content?.[0],
      field = paragraph?.content?.[0];
    if (
      object.type !== 'canvasObject' ||
      object.attrs?.kind !== 'text' ||
      object.content.length !== 1 ||
      paragraph.type !== 'paragraph' ||
      paragraph.content?.length !== 1 ||
      field.type !== 'labelField'
    )
      return object;
    changed = true;
    const attrs = { ...object.attrs, kind: 'field' };
    for (const key of ['textAlign', 'lineHeight'])
      if (paragraph.attrs?.[key] != null) attrs[key] = paragraph.attrs[key];
    for (const mark of field.marks ?? []) {
      if (['bold', 'italic', 'underline'].includes(mark.type)) attrs[mark.type] = true;
      if (mark.type === 'textStyle')
        for (const key of ['fontFamily', 'fontSize', 'color', 'lineHeight'])
          if (mark.attrs?.[key] != null) attrs[key] = mark.attrs[key];
    }
    attrs.lineHeight = Number(attrs.lineHeight);
    return { ...object, attrs, content: [{ type: 'labelValue', attrs: { key: field.attrs.key } }] };
  });
  return changed ? { ...doc, content } : null;
}

export function validateLabelNodes(doc) {
  doc.descendants((n) => {
    if (n.type.name === 'gs1Matrix') {
      validateMatrix(n.attrs);
      bwipjs.toSVG(matrixOptions(n.attrs));
    }
    if (['labelField', 'labelValue'].includes(n.type.name)) resolveLabel('{{' + n.attrs.key + '}}');
    if (['tonBlock', 'tonField'].includes(n.type.name))
      throw new Error('Nelle etichette usa i campi della sezione Dati automatici.');
  });
}
