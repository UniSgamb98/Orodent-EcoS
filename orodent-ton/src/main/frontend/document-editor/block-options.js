// Presentation only: none of these settings changes production calculations.
export const blockFontSizes = ['3pt', '5pt', '7pt', '9pt', '10pt', '11pt', '12pt', '14pt', '18pt', '24pt'];
export const optionDefaults = Object.freeze({
  showTitle: true, headerColor: '', highlightTotals: true,
  pageBreakBefore: false, keepCardsTogether: true, repeatHeaders: true,
  hideZeroFormats: false, formatOrder: 'production', showDiameter: true, showNominal: true,
  hideZeroPowders: false, showPercent: true, powderLabel: 'code', emphasizeWeights: true,
  tableWidths: null,
});
export const weighingColumns = showPpm => [
  ['layer', 'Strato'], ...(showPpm ? [['ppm', 'ppm']] : []),
  ['weight', 'g / strato'], ['count', 'N°'], ['total', 'Totale g'],
];
export const mixtureColumns = showPercent => [['powder', 'Polvere'], ...(showPercent ? [['percent', '% massa']] : []), ['amount', 'Da pesare (g)']];
export const blockColumns = attrs => attrs.kind === 'weighing' ? weighingColumns(attrs.showPpm) : attrs.kind === 'mixtures' ? mixtureColumns(attrs.showPercent) : [];
export function defaultTableWidths(attrs) {
  return attrs.kind === 'weighing' ? (attrs.showPpm ? [12, 16, 25, 12, 35] : [15, 30, 15, 40]) : (attrs.showPercent ? [50, 20, 30] : [65, 35]);
}
export function validTableWidths(widths, count) {
  return Array.isArray(widths) && widths.length === count && widths.every(n => typeof n === 'number' && Number.isFinite(n) && n >= 8 && n <= 100)
    && Math.abs(widths.reduce((a, b) => a + b, 0) - 100) < .01;
}
export function validateBlockOptions(attrs) {
  for (const [key, value] of Object.entries(optionDefaults)) {
    if (typeof value === 'boolean' && typeof attrs[key] !== 'boolean') throw new Error('Opzioni del blocco non valide.');
  }
  if (typeof attrs.headerColor !== 'string' || (attrs.headerColor !== '' && !/^#[a-f\d]{6}$/i.test(attrs.headerColor))) throw new Error('Colore delle intestazioni non valido.');
  if (!['production', 'heightAsc', 'heightDesc', 'code'].includes(attrs.formatOrder) || !['code', 'name', 'codeName'].includes(attrs.powderLabel)) throw new Error('Opzioni del contenuto non valide.');
  if (attrs.tableWidths !== null && !validTableWidths(attrs.tableWidths, blockColumns(attrs).length)) throw new Error('Le larghezze delle colonne devono totalizzare il 100%, con minimo 8%.');
}
// Source values use Italian decimal notation. Use this only for filtering and sorting,
// never to recompute masses or round the already calculated production values.
export const numericValue = value => typeof value === 'number' ? value : Number(String(value).replace(/\./g, '').replace(',', '.').replace('%', '').trim());
export function visibleFormats(formats, attrs) {
  const result = formats.filter(f => !attrs.hideZeroFormats || numericValue(f.count) !== 0);
  if (attrs.formatOrder === 'heightAsc' || attrs.formatOrder === 'heightDesc') result.sort((a, b) => (numericValue(a.nominal) - numericValue(b.nominal)) * (attrs.formatOrder === 'heightDesc' ? -1 : 1) || a.code.localeCompare(b.code));
  if (attrs.formatOrder === 'code') result.sort((a, b) => a.code.localeCompare(b.code));
  return result;
}
export const visiblePowders = (powders, attrs) => powders.filter(p => !attrs.hideZeroPowders || numericValue(p.weight) !== 0);
export function powderLabel(powder, mode) {
  if (!powder.name || powder.name === powder.code || mode === 'code') return powder.code;
  return mode === 'name' ? powder.name : `${powder.code} · ${powder.name}`;
}
