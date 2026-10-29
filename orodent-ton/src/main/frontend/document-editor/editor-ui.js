import { mountEditorZoom } from './editor-zoom.js';
import { sectionPath, sectionBreadcrumb, columnProperties, bindColumnControls } from './column-controls.js';
import { mountEditorDrag } from './editor-drag.js';
import { NodeSelection } from '@tiptap/pm/state';
import { blockTypes, fields, escape, checkOperationalContent } from './laboratory-blocks.js';

const $ = id => document.getElementById(id);

export function mountEditorUI(editor, model) {
  const cleanup = [];
  const urls = new Set();
  let disposed = false, toastTimer, nextId = 1, propertyKey = '', preview = false, printPreview = false;
  const notify = message => {
    clearTimeout(toastTimer); $('toast').textContent = message; $('toast').hidden = false;
    toastTimer = setTimeout(() => { $('toast').hidden = true; }, 4500);
  };
  const on = (node, event, handler) => {
    node.addEventListener(event, handler); cleanup.push(() => node.removeEventListener(event, handler));
  };
  function uniqueId() {
    let id, found;
    do { id = `block-${nextId++}`; found = false; editor.state.doc.descendants(n => { if (n.attrs.id === id) found = true; }); } while (found);
    return id;
  }
  const makeBlock = kind => ({ type: 'tonBlock', attrs: { id: uniqueId(), kind, title: blockTypes[kind], compact: true, showPpm: false, showCoefficient: false } });
  function insert(kind, position) {
    if (!editor.isEditable) return;
    if (typeof kind !== 'string' || (!Object.prototype.hasOwnProperty.call(blockTypes, kind)
        && !['table', 'image', 'columns', 'heading', 'text'].includes(kind)
        && !(kind.startsWith('field:') && Object.prototype.hasOwnProperty.call(fields, kind.slice(6))))) {
      throw new Error('Elemento da inserire non supportato.');
    }
    if (kind === 'table') return openTableDialog();
    if (kind === 'image') return $('image-file').click();
    let content;
    if (Object.prototype.hasOwnProperty.call(blockTypes, kind)) content = makeBlock(kind);
    else if (kind.startsWith('field:')) content = { type: 'tonField', attrs: { key: kind.slice(6) } };
    else if (kind === 'columns') content = { type: 'tonColumns', attrs: { widths: [50, 50], gap: 4 }, content: [0, 1].map(() => ({ type: 'tonColumn', content: [{ type: 'paragraph' }] })) };
    else if (kind === 'heading') content = { type: 'heading', attrs: { level: 2 }, content: [{ type: 'text', text: 'Nuovo titolo' }] };
    else content = { type: 'paragraph', content: [{ type: 'text', text: 'Scrivi il tuo testo…' }] };
    const chain = editor.chain().focus();
    if (position === undefined && kind === 'columns' && editor.state.selection instanceof NodeSelection) chain.insertContentAt(editor.state.selection.node.type.name === 'tonColumn' ? editor.state.selection.to - 1 : editor.state.selection.to, content).run();
    else if (position === undefined) chain.insertContent(content).run(); else chain.insertContentAt(position, content).run();
    if (content.type === 'tonBlock') {
      let pos; editor.state.doc.descendants((node, offset) => { if (node.attrs.id === content.attrs.id) pos = offset; });
      if (pos !== undefined) editor.commands.setNodeSelection(pos);
    }
    $('insert-panel').classList.remove('open');
    refresh(true);
  }

  function openTableDialog() {
    $('table-dialog').hidden = false; $('table-rows').focus();
  }
  function closeTableDialog() { $('table-dialog').hidden = true; editor.commands.focus(); }
  function insertTable() {
    const rows = Number($('table-rows').value), cols = Number($('table-cols').value);
    if (!Number.isInteger(rows) || rows < 1 || rows > 20 || !Number.isInteger(cols) || cols < 1 || cols > 8) return notify('Scegli da 1 a 20 righe e da 1 a 8 colonne.');
    const withHeaderRow = $('table-header').checked;
    editor.chain().focus().insertTable({ rows: rows + (withHeaderRow ? 1 : 0), cols, withHeaderRow }).run();
    closeTableDialog(); refresh(true);
  }

  function palette(kind, title, symbol, automatic = false) {
    return `<button class="palette-button ${automatic ? 'automatic' : ''}" data-insert="${kind}" draggable="false"><span class="symbol" aria-hidden="true">${symbol}</span><span>${title}${automatic ? '<small>Compilato dalla produzione</small>' : ''}</span></button>`;
  }
  $('basic-palette').innerHTML = [
    ['heading', 'Titolo', 'H'], ['text', 'Testo libero', 'T'], ['table', 'Tabella libera', '▦'],
    ['image', 'Immagine dal PC', '▧'], ['columns', 'Sezione a colonne', '▥'],
  ].map(v => palette(...v)).join('');
  $('laboratory-palette').innerHTML = Object.entries(blockTypes).map(([kind, title]) => palette(kind, title, '▤', true)).join('');
  $('field-palette').innerHTML = Object.entries(fields).map(([key, value]) => palette('field:' + key, value[0], '［］')).join('');
  for (const button of document.querySelectorAll('[data-insert]')) {
    on(button, 'click', () => insert(button.dataset.insert));
  }
  cleanup.push(mountEditorDrag(editor, insert));

  function run(command) {
    if (!commandAllowed(command)) return false;
    const c = editor.chain().focus();
    switch (command) {
      case 'bold': return c.toggleBold().run();
      case 'italic': return c.toggleItalic().run();
      case 'underline': return c.toggleUnderline().run();
      case 'undo': return c.undo().run();
      case 'redo': return c.redo().run();
      case 'table': return c.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run();
      case 'addRowAfter': return c.addRowAfter().run();
      case 'addColumnAfter': return c.addColumnAfter().run();
      case 'deleteRow': return c.deleteRow().run();
      case 'deleteColumn': return c.deleteColumn().run();
      case 'deleteTable': return c.deleteTable().run();
      case 'mergeCells': return c.mergeCells().run();
      case 'splitCell': return c.splitCell().run();
      case 'toggleHeaderRow': return c.toggleHeaderRow().run();
      case 'bulletList': return c.toggleBulletList().run();
      case 'orderedList': return c.toggleOrderedList().run();
      case 'align-left': return c.setTextAlign('left').run();
      case 'align-center': return c.setTextAlign('center').run();
      case 'align-right': return c.setTextAlign('right').run();
      default: throw new Error('Comando non riconosciuto.');
    }
  }

  function selectedNode() { return editor.state.selection instanceof NodeSelection ? editor.state.selection.node : null; }
  function updateProperties(force = false) {
    const node = selectedNode();
    const path = sectionPath(editor.state), section = path.at(-1);
    const type = ['tonColumns', 'tonColumn'].includes(node?.type.name) || (section && !node && !editor.isActive('table')) ? 'columns' : node?.type.name === 'tonBlock' ? 'block' : node?.type.name === 'image' ? 'image' : editor.isActive('table') ? 'table' : 'text';
    const attrs = type === 'table' ? editor.getAttributes('table') : node?.attrs;
    const key = type + JSON.stringify(attrs) + editor.isEditable + path.map(s => s.pos + ':' + s.column + ':' + JSON.stringify(s.node.attrs)).join('|');
    if (!force && (key === propertyKey || $('properties').contains(document.activeElement))) return;
    propertyKey = key;
    const root = $('properties');
    let html = '<span class="eyebrow">Proprietà della selezione</span>';
    if (path.length) html += `<nav class="layout-breadcrumb" aria-label="Contenitori della selezione">${sectionBreadcrumb(editor.state)}</nav>`;
    if (type === 'columns') {
      html += columnProperties(section);
    } else if (type === 'block') {
      html += `<h2>${escape(blockTypes[node.attrs.kind])}</h2><p>Blocco automatico · Dati di esempio</p><div class="section"><label for="block-title">Titolo della sezione</label><input id="block-title" type="text" maxlength="100" value="${escape(node.attrs.title)}"><label class="check"><input type="checkbox" id="block-compact" ${node.attrs.compact ? 'checked' : ''}>Spaziatura compatta</label><label for="block-font-family">Carattere del blocco</label><select id="block-font-family">${['Arial','Georgia','Verdana','Times New Roman'].map(f=>`<option ${node.attrs.fontFamily===f?'selected':''}>${f}</option>`).join('')}</select><label for="block-font-size">Dimensione del testo</label><select id="block-font-size">${['9pt','10pt','11pt','12pt','14pt','18pt','24pt'].map(f=>`<option ${node.attrs.fontSize===f?'selected':''}>${f}</option>`).join('')}</select>${node.attrs.kind === 'weighing' ? `<label class="check"><input type="checkbox" id="block-ppm" ${node.attrs.showPpm ? 'checked' : ''}>Mostra ppm</label><label class="check"><input type="checkbox" id="block-coefficient" ${node.attrs.showCoefficient ? 'checked' : ''}>Mostra g/mm</label>` : ''}</div><div class="section"><span class="eyebrow">Dati sempre presenti</span><ul class="required-list">${({ identity: ['Produzione e data', 'Prodotto'], press: ['Macchina e pressione in bar'], weighing: ['Grammi per singolo strato', 'Numero di pesate', 'Peso complessivo', 'Spessore totale in mm'], mixtures: ['Numero degli strati', 'Polveri e percentuali', 'Grammi e totale miscela'], totals: ['Numero dei dischi', 'Fabbisogno da dosare', 'Quantità da preparare'], signatures: ['Spazi per data e firma'] }[node.attrs.kind]).map(t => `<li>${t}</li>`).join('')}</ul></div><div class="row"><button data-property="duplicate" class="line">Duplica blocco</button><button data-property="delete" class="line danger">Elimina</button></div><div class="note">Trascina la maniglia del blocco per spostarlo. I numeri si modificano nei dati della produzione.</div>`;
    } else if (type === 'table') {
      html += '<h2>Tabella libera</h2><p>Seleziona celle e usa questi comandi per modificarne la struttura.</p><div class="section"><div class="row"><button data-command="addRowAfter" class="line">＋ Riga</button><button data-command="addColumnAfter" class="line">＋ Colonna</button></div><div class="row"><button data-command="deleteRow" class="line">Elimina riga</button><button data-command="deleteColumn" class="line">Elimina colonna</button></div><div class="row"><button data-command="mergeCells" class="line">Unisci celle</button><button data-command="splitCell" class="line">Dividi cella</button></div><div class="row"><button data-command="toggleHeaderRow" class="line">Intestazione</button></div></div><label class="check"><input id="table-striped" type="checkbox" ' + (attrs.striped ? 'checked' : '') + '>Righe alternate</label><label class="check"><input id="table-compact" type="checkbox" ' + (attrs.compact ? 'checked' : '') + '>Spaziatura compatta</label><div class="row"><button data-command="deleteTable" class="line danger">Elimina tabella</button></div><div class="note">Per ridimensionare una colonna trascina il bordo fra due celle.</div>';
    } else if (type === 'image') {
      html += '<h2>Immagine locale</h2><p>L’immagine è conservata nel modello e non richiede Internet.</p><label for="image-width">Larghezza nella pagina</label><select id="image-width">' + ['25%', '50%', '75%', '100%'].map(w => `<option ${node.attrs.width === w ? 'selected' : ''}>${w}</option>`).join('') + '</select><div class="row"><button data-property="delete" class="line danger">Elimina immagine</button></div>';
    } else {
      html += '<h2>Testo e impaginazione</h2><p>Scrivi direttamente sulla pagina. Seleziona una porzione di testo per cambiarne carattere, dimensione e colore.</p><div class="section"><span class="eyebrow">Tabelle e immagini</span><p>Fai clic in una tabella o su un’immagine per visualizzare le proprietà.</p></div><div class="section"><span class="eyebrow">Blocchi del laboratorio</span><p>Seleziona il bordo di un blocco automatico per cambiarne titolo e spaziatura.</p></div><div class="note">I blocchi usano dati di esempio. L’esportazione conserva la struttura del modello, non una produzione emessa.</div>';
    }
    root.innerHTML = html;
    root.querySelectorAll('[data-layout-select]').forEach(button => { button.onclick = () => { editor.commands.setNodeSelection(Number(button.dataset.layoutSelect)); updateProperties(true); }; });
    if (type === 'columns') bindColumnControls(root, editor, section, notify, updateProperties);
    root.querySelectorAll('[data-command]').forEach(bindCommand);
    const change = (id, action) => {
      const input = $(id); if (!input) return;
      input.disabled = !editor.isEditable;
      input.onchange = () => { action(input); updateProperties(true); };
    };
    change('block-title', input => editor.commands.updateAttributes('tonBlock', { title: input.value.trim() || blockTypes[node.attrs.kind] }));
    change('block-font-family', input => editor.commands.updateAttributes('tonBlock', { fontFamily: input.value }));
    change('block-font-size', input => editor.commands.updateAttributes('tonBlock', { fontSize: input.value }));
    change('block-compact', input => editor.commands.updateAttributes('tonBlock', { compact: input.checked }));
    change('block-ppm', input => editor.commands.updateAttributes('tonBlock', { showPpm: input.checked }));
    change('block-coefficient', input => editor.commands.updateAttributes('tonBlock', { showCoefficient: input.checked }));
    change('table-striped', input => editor.commands.updateAttributes('table', { striped: input.checked }));
    change('table-compact', input => editor.commands.updateAttributes('table', { compact: input.checked }));
    change('image-width', input => editor.commands.updateAttributes('image', { width: input.value }));
    root.querySelectorAll('[data-property]').forEach(button => {
      button.disabled = !editor.isEditable;
      button.onclick = () => {
        if (!editor.isEditable || !(editor.state.selection instanceof NodeSelection)) return;
        if (button.dataset.property === 'delete') editor.chain().focus().deleteSelection().run();
        else { const selected = editor.state.selection.node.toJSON(); selected.attrs.id = uniqueId(); editor.chain().focus().insertContentAt(editor.state.selection.to, selected).run(); }
        updateProperties(true);
      };
    });
  }

  on($('editor'), 'orodent-layout-preview', event => {
    const section = sectionPath(editor.state).at(-1);
    if (section?.pos === event.detail.pos) event.detail.widths.forEach((width, i) => { const input = $(`layout-width-${i}`); if (input) input.value = width; });
  });
  on($('editor'), 'pointerdown', event => {
    if (event.target.closest('.layout-divider,.layout-select,.layout-column-select')) $('properties').classList.add('open');
  });

  function commandAllowed(command) {
    const node = selectedNode();
    if (['tonBlock', 'image', 'tonColumns', 'tonColumn'].includes(node?.type.name) && ['bold', 'italic', 'underline', 'bulletList', 'orderedList', 'align-left', 'align-center', 'align-right'].includes(command)) return false;
    const native = ['undo', 'redo', 'addRowAfter', 'addColumnAfter', 'deleteRow', 'deleteColumn', 'deleteTable', 'mergeCells', 'splitCell', 'toggleHeaderRow'];
    return editor.isEditable && (!native.includes(command) || editor.can()[command]());
  }
  function bindCommand(button) {
    button.disabled = !commandAllowed(button.dataset.command);
    button.onmousedown = event => event.preventDefault();
    button.onclick = () => { run(button.dataset.command); refresh(); };
  }
  document.querySelectorAll('.toolbar [data-command]').forEach(bindCommand);
  const selects = {
    'text-type': value => value === 'paragraph' ? editor.chain().focus().setParagraph().run() : editor.chain().focus().setHeading({ level: Number(value) }).run(),
    'font-family': value => editor.chain().focus().setFontFamily(value).run(),
    'font-size': value => editor.chain().focus().setFontSize(value).run(),
    'line-height': value => editor.chain().focus().setLineHeight(value).run(),
    'text-color': value => editor.chain().focus().setColor(value).run(),
    'text-highlight': value => editor.chain().focus().setBackgroundColor(value).run(),
  };
  Object.entries(selects).forEach(([id, action]) => on($(id), 'change', event => {
    if (!editor.isEditable) return;
    const node = selectedNode();
    const attribute = { 'font-family': 'fontFamily', 'font-size': 'fontSize', 'text-color': 'color' }[id];
    if (node?.type.name === 'tonBlock') {
      if (attribute) editor.commands.updateAttributes('tonBlock', { [attribute]: event.target.value });
    } else action(event.target.value);
  }));

  function previewDocument() {
    const missing = checkOperationalContent(editor.state.doc);
    $('preview-content').innerHTML = `<div class="paper-brand"><strong>ORODENT</strong><span>LABORATORIO · PRODUZIONE</span></div>${editor.getHTML()}<div class="paper-footer">Dati di esempio · Nessun documento emesso</div>`;
    $('preview-dialog').hidden = false; preview = true;
    if (missing.length) notify('Scheda operativa incompleta: mancano ' + missing.join(', ') + '.');
    $('preview-dialog').querySelector('[data-action="close-preview"]').focus();
  }
  function closePreview() { $('preview-dialog').hidden = true; $('preview-content').replaceChildren(); preview = false; editor.commands.focus(); }
  function exportModel() {
    const payload = model.serialize();
    if (payload.length > 8_000_000) return notify('Il modello supera 8 MB. Riduci immagini o contenuto prima di esportarlo.');
    const url = URL.createObjectURL(new Blob([payload], { type: 'application/json' })); urls.add(url);
    const link = document.createElement('a'); link.href = url; link.download = (model.title().replace(/[^a-z0-9_-]/gi, '-') || 'modello') + '.json';
    document.body.append(link); link.click(); link.remove();
    notify('Modello esportato come file JSON locale.');
    setTimeout(() => { URL.revokeObjectURL(url); urls.delete(url); }, 1000);
  }
  function readFile(input, callback) {
    const file = input.files?.[0]; if (!file) return;
    const reader = new FileReader();
    reader.onload = () => { if (!disposed && editor.isEditable) callback(file, reader.result); };
    reader.onerror = () => notify('Impossibile leggere il file selezionato.');
    if (input.id === 'image-file') {
      if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 2 * 1024 * 1024) { input.value = ''; return notify('Scegli un’immagine PNG, JPEG o WebP fino a 2 MB.'); }
      reader.readAsDataURL(file);
    } else {
      if (file.size > 8_000_000) { input.value = ''; return notify('Il modello supera 8 MB.'); }
      reader.readAsText(file);
    }
    input.value = '';
  }
  on($('image-file'), 'change', () => readFile($('image-file'), (file, src) => {
    if (model.serialize().length + src.length + 1000 > 8_000_000) return notify('L’immagine porterebbe il modello oltre 8 MB. Scegli un’immagine più piccola.');
    editor.chain().focus().setImage({ src, alt: file.name, width: '50%' }).run();
  }));
  on($('model-file'), 'change', () => readFile($('model-file'), (file, text) => {
    if (model.isDirty() && !window.confirm('Scartare le modifiche locali e aprire il modello selezionato?')) return;
    try { model.restore(text); notify('Modello aperto.'); } catch (error) { notify(error.message); }
  }));
  const actions = {
    'toggle-insert': () => $('insert-panel').classList.toggle('open'),
    'toggle-properties': () => $('properties').classList.toggle('open'),
    'open-model': () => $('model-file').click(), 'export-model': exportModel,
    'check-model': () => { const missing = checkOperationalContent(editor.state.doc); notify(missing.length ? 'Mancano: ' + missing.join(', ') + '.' : 'La struttura contiene tutti i blocchi operativi richiesti. I dati mostrati sono esempi.'); },
    preview: previewDocument, 'close-preview': closePreview,
    'close-table': closeTableDialog, 'insert-table': insertTable,
    deselect: () => editor.commands.setTextSelection(editor.state.doc.content.size),
    print: () => { model.preparePrint(); window.print(); },
  };
  document.querySelectorAll('[data-action]').forEach(button => on(button, 'click', () => actions[button.dataset.action]?.()));
  document.querySelectorAll('[data-tab]').forEach(button => on(button, 'click', () => {
    $('insert-tools').hidden = button.dataset.tab !== 'insert'; $('page-tools').hidden = button.dataset.tab !== 'page';
    document.querySelectorAll('[data-tab]').forEach(tab => tab.classList.toggle('active', tab === button));
  }));
  on($('template-title'), 'change', () => model.changeTitle($('template-title').value));
  for (const id of ['orientation', 'margins']) on($(id), 'change', () => { if (editor.isEditable) model.changePage({ orientation: $('orientation').value, margin: Number($('margins').value) }); });
  cleanup.push(mountEditorZoom(model));
  const resize = () => {
    const top = document.querySelector('.toolbar').getBoundingClientRect().bottom;
    document.querySelector('.workspace').style.height = Math.max(280, innerHeight - top - 32) + 'px';
    $('properties').style.top = top + 'px'; $('insert-panel').style.top = top + 'px';
  };
  on(window, 'resize', resize);
  on(window, 'beforeprint', () => {
    model.preparePrint();
    if (!preview) { printPreview = true; previewDocument(); }
  });
  on(window, 'afterprint', () => {
    if (printPreview) { printPreview = false; closePreview(); }
  });
  on(document, 'keydown', event => {
    const dialog = preview ? $('preview-dialog') : !$('table-dialog').hidden ? $('table-dialog') : null;
    if (dialog && event.key === 'Tab') {
      const nodes = [...dialog.querySelectorAll('button,input,select,[tabindex]')].filter(n => !n.disabled && n.getClientRects().length);
      const first = nodes[0], last = nodes[nodes.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      return;
    }
    if (event.key !== 'Escape') return;
    if (preview) closePreview(); else if (!$('table-dialog').hidden) closeTableDialog();
    else { $('properties').classList.remove('open'); $('insert-panel').classList.remove('open'); }
  });

  function refresh(force = false) {
    if (disposed) return;
    const editable = editor.isEditable;
    document.body.classList.toggle('editing', editable);
    document.body.classList.toggle('readonly', !editable);
    $('template-title').readOnly = !editable;
    $('dirty-state').textContent = model.isDirty() ? 'Modifiche non salvate' : document.body.classList.contains('embedded') ? (editable ? 'Bozza' : 'Sola lettura') : 'Bozza locale';
    const widget = selectedNode()?.type.name === 'tonBlock' ? selectedNode() : null;
    const image = ['image', 'tonColumns', 'tonColumn'].includes(selectedNode()?.type.name);
    const marks = widget?.attrs || editor.getAttributes('textStyle');
    for (const [id, value] of [['font-family', marks.fontFamily || 'Arial'], ['font-size', marks.fontSize || '11pt'], ['line-height', marks.lineHeight || '1.5']]) {
      if (document.activeElement !== $(id)) $(id).value = value;
    }
    const heading = editor.getAttributes('heading').level;
    if (document.activeElement !== $('text-type')) $('text-type').value = editor.isActive('heading') ? String(heading) : 'paragraph';
    document.querySelectorAll('[data-command]').forEach(button => {
      const command = button.dataset.command;
      button.disabled = !commandAllowed(command);
      if (['bold', 'italic', 'underline', 'bulletList', 'orderedList'].includes(command)) button.setAttribute('aria-pressed', String(editor.isActive(command)));
    });
    document.querySelectorAll('.toolbar select,.toolbar input,#orientation,#margins,[data-action="open-model"],.palette-button').forEach(n => n.disabled = !editable);
    for (const id of ['text-type', 'text-highlight', 'line-height']) $(id).disabled = !editable || Boolean(widget) || image;
    if (image) for (const id of ['font-family', 'font-size', 'text-color']) $(id).disabled = true;
    if (/^#[a-f\d]{6}$/i.test(marks.color || '')) $('text-color').value = marks.color;
    const selected = selectedNode();
    $('selection-status').textContent = selected?.type.name === 'tonBlock' ? blockTypes[selected.attrs.kind] : editor.isActive('table') ? 'Tabella libera' : 'Documento in flusso · A4';
    updateProperties(force);
  }
  refresh(true); resize();
  return {
    run, refresh, insert, notify,
    dispose() {
      disposed = true; clearTimeout(toastTimer); cleanup.forEach(fn => fn());
      document.querySelectorAll('[data-command],[data-property]').forEach(button => { button.onmousedown = null; button.onclick = null; button.disabled = true; });
      $('properties').replaceChildren(); $('preview-content').replaceChildren(); $('preview-dialog').hidden = true; $('table-dialog').hidden = true;
      for (const url of urls) URL.revokeObjectURL(url); urls.clear();
    },
  };
}
