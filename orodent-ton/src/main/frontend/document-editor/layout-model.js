// Percentages belong to the printed page, never to screen pixels or editor zoom.
export const MIN_COLUMN = 8;
export const MAX_COLUMNS = 4;
export function equalWidths(count) {
  const units = Math.floor(1000 / count);
  return Array.from({ length: count }, (_, i) => (units + (i < 1000 % count ? 1 : 0)) / 10);
}
export function validWidths(widths, count) {
  return Array.isArray(widths) && widths.length === count && count >= 1 && count <= MAX_COLUMNS
    && widths.every(n => typeof n === 'number' && Number.isFinite(n) && n >= (count === 1 ? 100 : MIN_COLUMN) && n <= 100)
    && Math.abs(widths.reduce((a, b) => a + b, 0) - 100) < .01;
}
export const widthsOf = node => node.attrs.widths ?? equalWidths(node.childCount);
export function setColumnWidth(widths, index, value) {
  if (!Number.isFinite(value) || !Number.isInteger(index) || index < 0 || index >= widths.length) throw new Error('Larghezza non valida.');
  if (widths.length === 1) return [100];
  const target = Math.max(80, Math.min(1000 - 80 * (widths.length - 1), Math.round(value * 10)));
  const remaining = 1000 - target - 80 * (widths.length - 1);
  const weights = widths.map((n, i) => i === index ? 0 : Math.max(0, n * 10 - 80));
  const total = weights.reduce((a, b) => a + b, 0);
  const quotas = weights.map((n, i) => i === index ? 0 : remaining * (total ? n / total : 1 / (widths.length - 1)));
  const units = quotas.map((n, i) => i === index ? target : 80 + Math.floor(n));
  let residual = 1000 - units.reduce((a, b) => a + b, 0);
  const order = quotas.map((n, i) => ({ i, rest: n - Math.floor(n) })).filter(n => n.i !== index).sort((a, b) => b.rest - a.rest || a.i - b.i);
  for (let i = 0; residual > 0; i++, residual--) units[order[i % order.length].i]++;
  return units.map(n => n / 10);
}
export function resizePair(widths, index, delta) {
  const result = [...widths], total = Math.round((widths[index] + widths[index + 1]) * 10);
  const first = Math.max(80, Math.min(total - 80, Math.round((widths[index] + delta) * 10)));
  result[index] = first / 10; result[index + 1] = (total - first) / 10;
  return result;
}
export function layoutStyle(widths, gap) {
  return `display:grid;grid-template-columns:${widths.map(n => `minmax(0,${n}fr)`).join(' ')};column-gap:${gap}mm;align-items:start`;
}
export function validateLayout(node) {
  if (!validWidths(widthsOf(node), node.childCount) || !Number.isFinite(node.attrs.gap) || node.attrs.gap < 0 || node.attrs.gap > 12)
    throw new Error('La sezione deve avere da 1 a 4 colonne, larghezze totali del 100% e spazio da 0 a 12 mm.');
}
// Upgrade old layouts in memory. Stored approved revisions are never rewritten.
export function normalizeLayoutJSON(content) {
  let count = 0;
  function visit(node, depth) {
    if (!node || typeof node !== 'object' || depth > 64 || ++count > 100_000) throw new Error('Struttura del modello troppo complessa.');
    const result = { ...node };
    if (node.attrs) result.attrs = { ...node.attrs };
    if (node.type === 'tonBlock') {
      result.attrs ??= {};
      if (result.attrs.showPpm === undefined) result.attrs.showPpm = result.attrs.engineering ?? false;
      if (result.attrs.showCoefficient === undefined) result.attrs.showCoefficient = result.attrs.engineering ?? false;
      delete result.attrs.engineering;
    }
    if (node.type === 'tonColumns') {
      result.attrs ??= {};
      result.attrs.widths ??= equalWidths(node.content?.length || 2);
      result.attrs.gap ??= 4.5;
    }
    if (node.content) {
      if (!Array.isArray(node.content)) throw new Error('Contenuto della sezione non valido.');
      result.content = node.content.map(child => visit(child, depth + 1));
    }
    // A column made only of non-editable blocks gives Chromium no stable DOM
    // selection boundary. Keep a real editable paragraph after its last block.
    if (node.type === 'tonColumn' && result.content?.at(-1)?.type !== 'paragraph') {
      result.content = [...(result.content || []), { type: 'paragraph' }];
    }
    return result;
  }
  return visit(content, 0);
}
