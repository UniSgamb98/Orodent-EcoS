import { blockFontSizes, blockColumns, defaultTableWidths } from './block-options.js';
import { setColumnWidth } from './layout-model.js';

// All user-supplied strings are escaped before becoming property controls.
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const select = (id, label, values, value) => `<label for="${id}">${label}</label><select id="${id}">${values.map(item => { const [key, text] = Array.isArray(item) ? item : [item, item]; return `<option value="${key}" ${key === value ? 'selected' : ''}>${text}</option>`; }).join('')}</select>`;
const check = (id, label, checked) => `<label class="check"><input type="checkbox" id="${id}" ${checked ? 'checked' : ''}>${label}</label>`;
export function blockProperties(attrs, tab) {
  const columns = blockColumns(attrs), widths = attrs.tableWidths || defaultTableWidths(attrs);
  const common = check('block-show-title', 'Mostra titolo', attrs.showTitle) + `<label for="block-title">Titolo della sezione</label><input id="block-title" type="text" maxlength="100" value="${escape(attrs.title)}">`;
  const weighing = attrs.kind === 'weighing' ? select('block-format-order', 'Ordine dei formati', [['production', 'Ordine della produzione'], ['heightAsc', 'Altezza crescente'], ['heightDesc', 'Altezza decrescente'], ['code', 'Codice articolo']], attrs.formatOrder)
    + check('block-hide-zero-formats', 'Nascondi formati con quantità zero', attrs.hideZeroFormats)
    + check('block-diameter', 'Mostra diametro', attrs.showDiameter) + check('block-nominal', 'Mostra altezza nominale', attrs.showNominal)
    + check('block-ppm', 'Mostra ppm', attrs.showPpm) + check('block-coefficient', 'Mostra g/mm', attrs.showCoefficient) : '';
  const mixtures = attrs.kind === 'mixtures' ? select('block-powder-label', 'Identificazione della polvere', [['code', 'Codice'], ['name', 'Nome'], ['codeName', 'Codice e nome']], attrs.powderLabel)
    + '<p class="note">Se il nome non è disponibile viene mostrato il codice.</p>'
    + check('block-hide-zero-powders', 'Nascondi polveri da pesare a zero', attrs.hideZeroPowders)
    + check('block-percent', 'Mostra percentuali in massa', attrs.showPercent) : '';
  const appearance = check('block-compact', 'Spaziatura compatta', attrs.compact)
    + select('block-font-family', 'Carattere del blocco', ['Arial', 'Georgia', 'Verdana', 'Times New Roman'], attrs.fontFamily)
    + select('block-font-size', 'Dimensione del testo', blockFontSizes, attrs.fontSize)
    + (columns.length ? `<label for="block-header-color">Sfondo delle intestazioni</label><input id="block-header-color" type="color" value="${attrs.headerColor || (attrs.kind === 'mixtures' ? '#fff0af' : '#edf5f9')}"><button type="button" class="line" data-block-reset="color">Ripristina colore</button>` : '')
    + (columns.length || attrs.kind === 'totals' ? check('block-highlight-totals', 'Evidenzia i totali', attrs.highlightTotals) : '')
    + (attrs.kind === 'mixtures' ? check('block-emphasize-weights', 'Grammi da pesare in grassetto', attrs.emphasizeWeights) : '')
    + (columns.length ? `<div class="section"><h3>Larghezza delle colonne · %</h3>${columns.map(([key, label], i) => `<label for="block-width-${i}">${label}</label><input id="block-width-${i}" data-block-width="${i}" type="number" min="8" max="${100 - 8 * (columns.length - 1)}" step="0.1" value="${attrs.tableWidths ? widths[i] : ''}" placeholder="Auto">`).join('')}<button type="button" class="line" data-block-reset="widths">Larghezze automatiche</button><p class="note">Modifica un valore per fissare le larghezze. La somma resta 100%; un contenuto troppo largo viene segnalato nella pagina.</p></div>` : '');
  const printing = check('block-page-break', 'Inizia su una nuova pagina', attrs.pageBreakBefore)
    + (columns.length ? check('block-keep-cards', 'Tieni insieme ogni formato o strato', attrs.keepCardsTogether)
    + check('block-repeat-headers', 'Ripeti le intestazioni delle tabelle', attrs.repeatHeaders)
    + '<p class="note">Le impostazioni si applicano alla stampa. Una scheda più alta di una pagina deve comunque proseguire nella pagina successiva.</p>' : '');
  return `<div class="block-property-tabs" role="tablist" aria-label="Proprietà del blocco">${[['content', 'Contenuto'], ['appearance', 'Aspetto'], ['print', 'Stampa']].map(([key, title]) => `<button type="button" id="block-tab-${key}" role="tab" aria-controls="block-panel-${key}" aria-selected="${tab === key}" data-block-tab="${key}">${title}</button>`).join('')}</div>`
    + [['content', common + weighing + mixtures], ['appearance', appearance], ['print', printing]].map(([key, html]) => `<div id="block-panel-${key}" role="tabpanel" aria-labelledby="block-tab-${key}" ${tab === key ? '' : 'hidden'}>${html}</div>`).join('');
}
export function bindBlockProperties(root, editor, attrs, tabChanged, refresh, notify) {
  const update = patch => {
    if (!editor.isEditable) return;
    editor.commands.updateAttributes('tonBlock', patch); refresh(true);
  };
  root.querySelectorAll('[data-block-tab]').forEach(button => {
    button.onclick = () => {
      tabChanged(button.dataset.blockTab);
      root.querySelectorAll('[data-block-tab]').forEach(item => item.setAttribute('aria-selected', String(item === button)));
      root.querySelectorAll('[role=tabpanel]').forEach(panel => { panel.hidden = panel.id !== `block-panel-${button.dataset.blockTab}`; });
    };
  });
  const booleans = { 'show-title': 'showTitle', compact: 'compact', ppm: 'showPpm', coefficient: 'showCoefficient', diameter: 'showDiameter', nominal: 'showNominal', 'hide-zero-formats': 'hideZeroFormats', 'hide-zero-powders': 'hideZeroPowders', percent: 'showPercent', 'highlight-totals': 'highlightTotals', 'emphasize-weights': 'emphasizeWeights', 'page-break': 'pageBreakBefore', 'keep-cards': 'keepCardsTogether', 'repeat-headers': 'repeatHeaders' };
  for (const [id, key] of Object.entries(booleans)) {
    const input = root.querySelector(`#block-${id}`); if (!input) continue;
    input.disabled = !editor.isEditable;
    input.onchange = () => update({ [key]: input.checked, ...(['showPpm', 'showPercent'].includes(key) ? { tableWidths: null } : {}) });
  }
  for (const [id, key] of Object.entries({ title: 'title', 'font-family': 'fontFamily', 'font-size': 'fontSize', 'format-order': 'formatOrder', 'powder-label': 'powderLabel', 'header-color': 'headerColor' })) {
    const input = root.querySelector(`#block-${id}`); if (!input) continue;
    input.disabled = !editor.isEditable;
    input.onchange = () => update({ [key]: key === 'title' ? input.value.trim() || attrs.title : input.value });
  }
  root.querySelectorAll('[data-block-width]').forEach(input => {
    input.disabled = !editor.isEditable;
    input.onchange = () => {
      try { update({ tableWidths: setColumnWidth(attrs.tableWidths || defaultTableWidths(attrs), Number(input.dataset.blockWidth), input.value === '' ? NaN : Number(input.value)) }); }
      catch (error) { notify(error.message); refresh(true); }
    };
  });
  root.querySelectorAll('[data-block-reset]').forEach(button => {
    button.disabled = !editor.isEditable;
    button.onclick = () => update(button.dataset.blockReset === 'widths' ? { tableWidths: null } : { headerColor: '' });
  });
}
