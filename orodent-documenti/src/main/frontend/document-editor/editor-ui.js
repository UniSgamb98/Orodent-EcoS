import { editorKey } from './keyboard.js';
import {
  CanvasMultiSelection,
  mountCanvasSelection,
  deleteCanvasSelection,
} from './canvas-selection.js';
import { GapCursor } from '@tiptap/pm/gapcursor';
import { NodeSelection } from '@tiptap/pm/state';
import { labelFields, validateMatrix, measureLabel } from './label-elements.js';
import symbols from './symbols.json';
import { blockTypes, fields, escape, checkOperationalContent } from './laboratory-blocks.js';
import { blockProperties, bindBlockProperties } from './block-properties.js';
import { mountEditorZoom } from './editor-zoom.js';
import { selectedObject, updateObject, selectObjectText } from './canvas-object.js';
import { pagePixels, px, mm, clampGeometry } from './canvas-model.js';

const $ = (id) => document.getElementById(id);
const kindNames = {
  text: 'Riquadro di testo',
  field: 'Dato automatico',
  image: 'Immagine',
  matrix: 'GS1 DataMatrix',
  table: 'Tabella',
  block: 'Blocco automatico',
  group: 'Gruppo',
};
const objectName = (node) =>
  node.attrs.kind === 'field'
    ? (labelFields[node.firstChild?.attrs.key] ?? kindNames.field)
    : kindNames[node.attrs.kind];
export function mountEditorUI(editor, model) {
  const cleanup = [];
  let disposed = false,
    timer,
    propertyKey = '',
    blockTab = 'content',
    printPreview = false,
    replaceImage = false;
  const on = (node, event, callback) => {
    node.addEventListener(event, callback);
    cleanup.push(() => node.removeEventListener(event, callback));
  };
  function notify(message) {
    clearTimeout(timer);
    $('toast').textContent = message;
    $('toast').hidden = false;
    timer = setTimeout(() => {
      $('toast').hidden = true;
    }, 5000);
  }
  function safe(action) {
    try {
      return action();
    } catch (error) {
      notify(error.message);
      return false;
    }
  }
  function uniqueId() {
    let id;
    do {
      id = 'obj-' + model.nextId();
    } while (editor.state.doc.content.content.some((n) => n.attrs.id === id));
    return id;
  }
  function add(content, kind, dimensions = {}, point) {
    if (!editor.isEditable) return;
    model.ensureCanvas();
    const size = pagePixels(model.page()),
      current = selectedObject(editor.state),
      count = editor.state.doc.childCount;
    const inset = Math.min(px(model.page().margin), size.width / 4, size.height / 4),
      offset = (count % 7) * 8;
    const defaults = {
      text: [220, 42],
      field: [120, 18],
      image: [160, 120],
      matrix: [px(20), px(20)],
      table: [340, 140],
      block: [size.width - inset * 2, 200],
      group: [300, 200],
    }[kind];
    const attrs = clampGeometry(
      {
        id: uniqueId(),
        kind,
        x: point?.x ?? inset + offset,
        y: point?.y ?? inset + offset,
        width: dimensions.width ?? defaults[0],
        height: dimensions.height ?? defaults[1],
        page: point?.page ?? current?.node.attrs.page ?? 0,
        z: Math.max(0, ...editor.state.doc.content.content.map((n) => n.attrs.z ?? 0)) + 1,
      },
      size,
    );
    if (['image', 'matrix'].includes(kind)) {
      const ratio = (dimensions.width ?? defaults[0]) / (dimensions.height ?? defaults[1]);
      const factor = Math.min(
        1,
        attrs.width / (dimensions.width ?? defaults[0]),
        attrs.height / (dimensions.height ?? defaults[1]),
      );
      attrs.width = (dimensions.width ?? defaults[0]) * factor;
      attrs.height = attrs.width / ratio;
      attrs.keepRatio = true;
    }
    if (!Array.isArray(content)) content = [content];
    content = content.map((n) =>
      ['labelField', 'tonField'].includes(n.type) ? { type: 'paragraph', content: [n] } : n,
    );
    const node = editor.schema.nodeFromJSON({
      type: 'canvasObject',
      attrs,
      content,
    });
    const pos = editor.state.doc.content.size;
    editor.view.dispatch(editor.state.tr.insert(pos, node));
    // The selection must be built against the inserted document (the old doc can be empty).
    editor.commands.setNodeSelection(pos);
    editor.view.focus();
    refresh(true);
    return pos;
  }
  function selectText(pos) {
    const node = editor.state.doc.nodeAt(pos);
    editor.commands.setTextSelection(pos + 2);
    editor.view.focus();
    return node;
  }
  function insert(kind) {
    if (!editor.isEditable) return;
    if (kind === 'image') {
      replaceImage = false;
      $('image-file').click();
      return;
    }
    if (kind === 'table') {
      $('table-dialog').hidden = false;
      $('table-rows').focus();
      return;
    }
    if (kind.startsWith('symbol:')) {
      const symbol = symbols.find((s) => s.key === kind.slice(7));
      if (!symbol) throw Error('Simbolo non disponibile.');
      return add(
        {
          type: 'image',
          attrs: { src: symbol.src, alt: symbol.title, width: '100%' },
        },
        'image',
        { width: px(8), height: px(8) },
      );
    }
    if (kind === 'gs1') return add({ type: 'gs1Matrix' }, 'matrix');
    if (Object.hasOwn(blockTypes, kind))
      return add(
        {
          type: 'tonBlock',
          attrs: {
            id: 'block-' + model.nextId(),
            kind,
            title: blockTypes[kind],
            compact: true,
          },
        },
        'block',
      );
    if (kind.startsWith('label:') || kind.startsWith('field:')) {
      const label = kind.startsWith('label:'),
        key = kind.slice(6);
      if (!(key in (label ? labelFields : fields))) throw Error('Campo non disponibile.');
      if (label) return add({ type: 'labelValue', attrs: { key } }, 'field', measureLabel(key));
      const content = {
          type: label ? 'labelField' : 'tonField',
          attrs: { key },
        },
        object = selectedObject(editor.state);
      if (object?.node.attrs.kind === 'text' && !(editor.state.selection instanceof NodeSelection))
        return editor.chain().focus().insertContent(content).run();
      return add(content, 'text');
    }
    if (!['text', 'heading'].includes(kind)) throw Error('Elemento non supportato.');
    const pos = add(
      {
        type: kind === 'heading' ? 'heading' : 'paragraph',
        ...(kind === 'heading' ? { attrs: { level: 2 } } : {}),
        content: [],
      },
      'text',
    );
    if (kind === 'heading')
      updateObject(editor, selectedObject(editor.state), {
        fontSize: '18pt',
        height: 48,
      });
    return pos;
  }
  function table() {
    const rows = Number($('table-rows').value),
      cols = Number($('table-cols').value),
      header = $('table-header').checked;
    if (
      !Number.isInteger(rows) ||
      rows < 1 ||
      rows > 20 ||
      !Number.isInteger(cols) ||
      cols < 1 ||
      cols > 8
    )
      return notify('Scegli da 1 a 20 righe e da 1 a 8 colonne.');
    add(
      {
        type: 'table',
        attrs: { compact: true },
        content: Array.from({ length: rows + (header ? 1 : 0) }, (_, i) => ({
          type: 'tableRow',
          content: Array.from({ length: cols }, () => ({
            type: header && i === 0 ? 'tableHeader' : 'tableCell',
            content: [{ type: 'paragraph' }],
          })),
        })),
      },
      'table',
    );
    $('table-dialog').hidden = true;
    refresh(true);
  }
  function palette(kind, title, icon) {
    return `<button class="palette-button" data-insert="${kind}"><span class="symbol">${icon}</span><span>${title}</span></button>`;
  }
  $('basic-palette').innerHTML = [
    ['text', 'Riquadro di testo', 'T'],
    ['heading', 'Titolo', 'H'],
    ['image', 'Immagine dal PC', '▧'],
    ['table', 'Tabella', '▦'],
  ]
    .map((v) => palette(...v))
    .join('');
  $('label-palette').innerHTML =
    palette('gs1', 'GS1 DataMatrix', '▦') +
    Object.entries(labelFields)
      .map(([k, n]) => palette('label:' + k, n, '［］'))
      .join('');
  $('field-palette').innerHTML = Object.entries(fields)
    .map(([k, n]) => palette('field:' + k, n[0], '［］'))
    .join('');
  $('laboratory-palette').innerHTML = Object.entries(blockTypes)
    .map(([k, n]) => palette(k, n, '▤'))
    .join('');
  $('symbol-palette').innerHTML = symbols
    .map(
      (s) =>
        `<button class="palette-button" data-insert="symbol:${s.key}"><img src="${s.src}" alt=""><span>${escape(s.title)}</span></button>`,
    )
    .join('');
  document
    .querySelectorAll('[data-insert]')
    .forEach((b) => on(b, 'click', () => safe(() => insert(b.dataset.insert))));
  function deleteObject() {
    const o = selectedObject(editor.state);
    if (o && editor.isEditable) {
      editor.view.dispatch(editor.state.tr.delete(o.pos, o.pos + o.node.nodeSize));
      refresh(true);
    }
  }
  function duplicate() {
    const o = selectedObject(editor.state);
    if (!o) return;
    const copy = o.node.toJSON();
    copy.attrs = clampGeometry(
      {
        ...copy.attrs,
        id: uniqueId(),
        x: copy.attrs.x + 10,
        y: copy.attrs.y + 10,
        z: Math.max(...editor.state.doc.content.content.map((n) => n.attrs.z)) + 1,
      },
      pagePixels(model.page()),
    );
    const at = editor.state.doc.content.size;
    editor.view.dispatch(editor.state.tr.insert(at, editor.schema.nodeFromJSON(copy)));
    editor.commands.setNodeSelection(at);
    refresh(true);
  }
  function layer(front) {
    const o = selectedObject(editor.state);
    if (!o) return;
    let items = [];
    editor.state.doc.forEach((n, p) => items.push({ node: n, pos: p }));
    items.sort((a, b) => a.node.attrs.z - b.node.attrs.z);
    items = items.filter((x) => x.pos !== o.pos);
    items[front ? 'push' : 'unshift'](o);
    let tr = editor.state.tr;
    items.forEach((x, i) => (tr = tr.setNodeMarkup(x.pos, undefined, { ...x.node.attrs, z: i })));
    tr.setSelection(NodeSelection.create(tr.doc, o.pos));
    editor.view.dispatch(tr);
    refresh(true);
  }
  const hasRange = () =>
    !editor.state.selection.empty && !(editor.state.selection instanceof NodeSelection);
  function format(patch, command) {
    const o = selectedObject(editor.state);
    if (!o || !['text', 'field'].includes(o.node.attrs.kind) || !editor.isEditable) return;
    if (o.node.attrs.kind === 'field') {
      updateObject(editor, o, patch);
      refresh();
      return;
    }
    if (hasRange() && command) {
      command(editor.chain().focus()).run();
      return;
    }
    if (command) {
      selectObjectText(editor, o);
      command(editor.chain().focus()).run();
    }
    const current = { node: editor.state.doc.nodeAt(o.pos), pos: o.pos };
    updateObject(editor, current, patch);
    editor.commands.setNodeSelection(o.pos);
    refresh();
  }
  function run(command) {
    if (command === 'undo' || command === 'redo') return editor.chain().focus()[command]().run();
    if (!editor.isEditable) return false;
    const o = selectedObject(editor.state);
    if (['bold', 'italic', 'underline'].includes(command))
      return format(
        { [command]: !o?.node.attrs[command] },
        hasRange()
          ? (c) =>
              c[
                {
                  bold: 'toggleBold',
                  italic: 'toggleItalic',
                  underline: 'toggleUnderline',
                }[command]
              ]()
          : (c) =>
              c[
                {
                  bold: 'unsetBold',
                  italic: 'unsetItalic',
                  underline: 'unsetUnderline',
                }[command]
              ](),
      );
    if (command.startsWith('align-'))
      return format({ textAlign: command.slice(6) }, (c) => c.setTextAlign(command.slice(6)));
    if (['bulletList', 'orderedList'].includes(command))
      return format({}, (c) =>
        c[command === 'bulletList' ? 'toggleBulletList' : 'toggleOrderedList'](),
      );
    const tableCommands = [
      'addRowAfter',
      'addColumnAfter',
      'deleteRow',
      'deleteColumn',
      'mergeCells',
      'splitCell',
      'toggleHeaderRow',
    ];
    if (tableCommands.includes(command)) return editor.chain().focus()[command]().run();
    return false;
  }
  document.querySelectorAll('[data-command]').forEach((b) => {
    on(b, 'mousedown', (e) => e.preventDefault());
    on(b, 'click', () => safe(() => run(b.dataset.command)));
  });
  const formats = {
    'font-family': (v) => format({ fontFamily: v }, (c) => c.setFontFamily(v)),
    'font-size': (v) => format({ fontSize: v }, (c) => c.setFontSize(v)),
    'text-color': (v) => format({ color: v }, (c) => c.setColor(v)),
    'text-highlight': (v) => format({}, (c) => c.setBackgroundColor(v)),
    'line-height': (v) => format({ lineHeight: Number(v) }, (c) => c.setLineHeight(v)),
    'text-type': (v) =>
      format(
        {
          fontSize: v === 'paragraph' ? '11pt' : v === '1' ? '24pt' : v === '2' ? '18pt' : '14pt',
        },
        (c) => (v === 'paragraph' ? c.setParagraph() : c.setHeading({ level: Number(v) })),
      ),
  };
  Object.entries(formats).forEach(([id, action]) =>
    on($(id), 'change', () => safe(() => action($(id).value))),
  );
  function properties(force = false) {
    const o = selectedObject(editor.state),
      root = $('selection-properties'),
      text = $('text-properties');
    const key =
      (editor.state.selection instanceof CanvasMultiSelection
        ? editor.state.selection.ids.join(',')
        : o
          ? o.pos + JSON.stringify(o.node.attrs) + o.node.firstChild?.type.name
          : 'none') +
      editor.isEditable +
      editor.isActive('table');
    if (!force && (key === propertyKey || $('properties').contains(document.activeElement))) return;
    propertyKey = key;
    text.hidden = !o || !['text', 'field'].includes(o.node.attrs.kind);
    text.classList.toggle('automatic-field', o?.node.attrs.kind === 'field');
    if (editor.state.selection instanceof CanvasMultiSelection) {
      root.innerHTML =
        '<span class="eyebrow">Selezione multipla</span><h2>' +
        editor.state.selection.ids.length +
        ' elementi selezionati</h2>' +
        '<p>Trascina uno degli elementi per spostarli insieme. Frecce: 1 px; Maiusc + frecce: 10 px.</p>' +
        '<div class="object-actions"><button id="delete-selection" class="danger">Elimina selezionati</button><button id="clear-selection">Deseleziona</button></div>';
      $('delete-selection').disabled = !editor.isEditable;
      $('delete-selection').onclick = () => deleteCanvasSelection(editor);
      $('clear-selection').onclick = () => actions.deselect();
      return;
    }
    if (!o) {
      root.innerHTML =
        '<span class="eyebrow">Proprietà</span><h2>Seleziona un oggetto</h2><p>Inserisci un riquadro di testo, un’immagine o un codice. Trascina la maniglia per spostarlo; usa i bordi per ridimensionarlo.</p><p>Frecce: 1 px · Maiusc + frecce: 10 px.</p><div id="object-list"></div>';
      editor.state.doc.forEach((n, pos) => {
        if (n.type.name !== 'canvasObject') return;
        const b = document.createElement('button');
        b.className = 'object-list-item';
        b.textContent = objectName(n) + ' · Pagina ' + (n.attrs.page + 1);
        b.onclick = () => editor.commands.setNodeSelection(pos);
        $('object-list').append(b);
      });
      return;
    }
    const a = o.node.attrs,
      node =
        editor.state.selection instanceof NodeSelection &&
        editor.state.selection.node.type.name !== 'canvasObject'
          ? editor.state.selection.node
          : o.node.firstChild;
    root.innerHTML = `<span class="eyebrow">Oggetto selezionato</span><h2>${escape(objectName(o.node))}</h2><div class="geometry-grid">${[
      ['x', 'X'],
      ['y', 'Y'],
      ['width', 'Larghezza'],
      ['height', 'Altezza'],
    ]
      .map(
        ([key, label]) =>
          `<label>${label} · px<input id="object-${key}" type="number" step="1" min="${key === 'x' || key === 'y' ? 0 : 8}" value="${Math.round(a[key] * 100) / 100}"></label>`,
      )
      .join(
        '',
      )}</div><p class="physical-size">${mm(a.width).toFixed(1)} × ${mm(a.height).toFixed(1)} mm</p>${(model.page().count ?? 1) > 1 ? `<label>Pagina<select id="object-page">${Array.from({ length: model.page().count }, (_, i) => `<option value="${i}" ${i === a.page ? 'selected' : ''}>${i + 1}</option>`).join('')}</select></label>` : ''}<div class="object-actions"><button data-object="front">In primo piano</button><button data-object="back">Sullo sfondo</button><button data-object="fit">${a.kind === 'field' ? 'Adatta al dato' : 'Adatta altezza'}</button><button data-object="duplicate">Duplica</button><button data-object="delete" class="danger">Elimina oggetto</button></div><div id="object-specific"></div><p id="object-overflow" class="matrix-error" hidden>Il contenuto supera il riquadro: aumenta le dimensioni o riduci il testo.</p>`;
    const change = (id, action) => {
      const input = $(id);
      if (input) {
        input.disabled = !editor.isEditable;
        input.onchange = () => {
          safe(() => action(input));
          properties(true);
        };
      }
    };
    for (const k of ['x', 'y', 'width', 'height'])
      change('object-' + k, (input) => {
        const value = Number(input.value);
        if (!Number.isFinite(value)) throw Error('Inserisci un valore numerico.');
        updateObject(editor, selectedObject(editor.state), { [k]: value });
      });
    change('object-page', (input) =>
      updateObject(editor, selectedObject(editor.state), {
        page: Number(input.value),
      }),
    );
    const specific = $('object-specific');
    if (a.kind === 'field')
      specific.innerHTML =
        '<p class="field-name">' +
        escape(labelFields[node.attrs.key]) +
        '</p><p>Valore dal lavoro. Non modificabile come testo.</p>';
    if (a.kind === 'image')
      specific.innerHTML =
        '<label>Descrizione immagine<input id="image-alt" value="' +
        escape(node.attrs.alt ?? '') +
        '"></label><button id="replace-image">Sostituisci immagine dal PC</button>';
    if (a.kind === 'matrix')
      specific.innerHTML =
        '<h3>Dati GS1</h3>' +
        [
          ['gtin', 'GTIN · AI 01'],
          ['lot', 'Lotto · AI 10'],
          ['productionDate', 'Produzione · AI 11'],
          ['expiryDate', 'Scadenza · AI 17'],
        ]
          .map(
            ([k, label]) =>
              `<label>${label}<input id="gs1-${k}" value="${escape(node.attrs[k])}"></label>`,
          )
          .join('') +
        '<p>Usa {{gtin}}, {{lot}}, {{productionDateGs1}}, {{expiryDateGs1}}. Le date sono facoltative. FNC1 e separatori sono automatici.</p>';
    if (a.kind === 'block') {
      specific.innerHTML = blockProperties(node.attrs, blockTab);
      bindBlockProperties(
        specific,
        editor,
        node.attrs,
        (tab) => {
          blockTab = tab;
        },
        properties,
        notify,
      );
    }
    if (a.kind === 'table')
      specific.innerHTML =
        '<p>Fai clic in una cella per modificare la tabella.</p><div class="object-actions">' +
        [
          ['addRowAfter', '＋ Riga'],
          ['addColumnAfter', '＋ Colonna'],
          ['deleteRow', '− Riga'],
          ['deleteColumn', '− Colonna'],
          ['mergeCells', 'Unisci celle'],
          ['splitCell', 'Dividi cella'],
        ]
          .map(([c, l]) => `<button data-table-command="${c}">${l}</button>`)
          .join('') +
        '</div>';
    change('image-alt', (input) => editor.commands.updateAttributes('image', { alt: input.value }));
    if ($('replace-image')) {
      $('replace-image').disabled = !editor.isEditable;
      $('replace-image').onclick = () => {
        replaceImage = true;
        $('image-file').click();
      };
    }
    for (const k of ['gtin', 'lot', 'productionDate', 'expiryDate'])
      change('gs1-' + k, (input) => {
        const attrs = { ...node.attrs, [k]: input.value };
        validateMatrix(attrs);
        editor.commands.updateAttributes('gs1Matrix', attrs);
      });
    root.querySelectorAll('[data-table-command]').forEach((b) => {
      b.disabled = !editor.isEditable || !editor.isActive('table');
      b.onmousedown = (e) => e.preventDefault();
      b.onclick = () => safe(() => run(b.dataset.tableCommand));
    });
    root.querySelectorAll('[data-object]').forEach((b) => {
      b.disabled = !editor.isEditable;
      b.onmousedown = (e) => e.preventDefault();
      b.onclick = () =>
        safe(() => {
          ({
            front: () => layer(true),
            back: () => layer(false),
            duplicate,
            delete: deleteObject,
            fit: () => {
              if (a.kind === 'field') {
                updateObject(editor, selectedObject(editor.state), measureLabel(node.attrs.key, a));
                return;
              }
              const box = document.querySelector(
                `[data-object-id="${a.id}"] .canvas-object-content`,
              );
              const top = box.getBoundingClientRect().top,
                zoom = model.zoomValue();
              const h = Math.max(
                8,
                ...[...box.children].map((n) =>
                  Math.ceil((n.getBoundingClientRect().bottom - top) / zoom),
                ),
              );
              updateObject(editor, selectedObject(editor.state), { height: h });
            },
          })[b.dataset.object]();
        });
    });
  }
  function overflow() {
    const o = selectedObject(editor.state);
    if (!o || !$('object-overflow')) return;
    const content = document.querySelector(
      `[data-object-id="${o.node.attrs.id}"] .canvas-object-content`,
    );
    if (content)
      $('object-overflow').hidden =
        content.scrollHeight <= content.clientHeight + 1 &&
        content.scrollWidth <= content.clientWidth + 1;
  }
  on($('editor'), 'canvas-object-preview', (e) => {
    for (const k of ['x', 'y', 'width', 'height'])
      if ($('object-' + k)) $('object-' + k).value = Math.round(e.detail[k] * 100) / 100;
  });
  on($('editor'), 'dblclick', (event) => {
    if (!model.canvas() || event.target.closest('.canvas-object') || !editor.isEditable) return;
    const rect = $('paper').getBoundingClientRect(),
      scale = Number.parseFloat(getComputedStyle($('paper')).getPropertyValue('--zoom')) || 1,
      size = pagePixels(model.page()),
      step = size.height + 24;
    const y = (event.clientY - rect.top) / scale,
      page = Math.min(size.count - 1, Math.floor(y / step));
    const pos = add(
      { type: 'paragraph' },
      'text',
      {},
      { x: (event.clientX - rect.left) / scale, y: y - page * step, page },
    );
    selectText(pos);
  });
  on($('image-file'), 'change', () => {
    const file = $('image-file').files?.[0];
    $('image-file').value = '';
    if (!file || !editor.isEditable) return;
    if (
      !['image/png', 'image/jpeg', 'image/webp'].includes(file.type) ||
      file.size > 2 * 1024 * 1024
    )
      return notify('Scegli un’immagine PNG, JPEG o WebP fino a 2 MB.');
    const target = replaceImage ? selectedObject(editor.state) : null;
    const reader = new FileReader();
    reader.onerror = () => notify('Impossibile leggere l’immagine.');
    reader.onload = () => {
      if (disposed || !editor.isEditable) return;
      if (model.serialize().length + String(reader.result).length > 8_000_000)
        return notify('Il modello supererebbe 8 MB.');
      const image = new Image();
      image.onload = () =>
        safe(() => {
          if (disposed || !editor.isEditable) return;
          if (target) {
            const actual = editor.state.doc.nodeAt(target.pos);
            if (actual?.attrs.id !== target.node.attrs.id) return;
            editor.commands.setNodeSelection(target.pos);
            editor.commands.updateAttributes('image', {
              src: reader.result,
              alt: file.name,
              width: '100%',
            });
          } else {
            const width = Math.min(220, pagePixels(model.page()).width * 0.7);
            add(
              {
                type: 'image',
                attrs: { src: reader.result, alt: file.name, width: '100%' },
              },
              'image',
              {
                width,
                height: (width * image.naturalHeight) / image.naturalWidth,
              },
            );
          }
        });
      image.onerror = () => notify('L’immagine non è leggibile.');
      image.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
  function closePreview() {
    $('preview-dialog').hidden = true;
    $('preview-content').replaceChildren();
    editor.view.focus();
  }
  function preview() {
    model.preparePrint();
    $('preview-content').innerHTML = model.html();
    $('preview-dialog').hidden = false;
  }
  const actions = {
    'toggle-insert': () => $('insert-panel').classList.toggle('open'),
    'toggle-properties': () => $('properties').classList.toggle('open'),
    preview,
    'close-preview': closePreview,
    'check-model': () => {
      model.validate();
      const missing =
        model.purpose() === 'ETICHETTA' ? [] : checkOperationalContent(editor.state.doc);
      notify(
        missing.length
          ? 'Per l’approvazione mancano: ' + missing.join(', ')
          : 'Formato e contenuto validi. I dati mostrati sono esempi.',
      );
    },
    deselect: () => {
      editor.commands.blur();
      editor.view.dispatch(
        editor.state.tr.setSelection(new GapCursor(editor.state.doc.resolve(0))),
      );
    },
    'close-table': () => {
      $('table-dialog').hidden = true;
    },
    'insert-table': table,
    print: () => {
      model.preparePrint();
      window.print();
    },
  };
  document
    .querySelectorAll('[data-action]')
    .forEach((b) => on(b, 'click', () => safe(() => actions[b.dataset.action]?.())));
  document.querySelectorAll('[data-tab]').forEach((b) =>
    on(b, 'click', () => {
      $('insert-tools').hidden = b.dataset.tab !== 'insert';
      $('page-tools').hidden = b.dataset.tab !== 'page';
      document.querySelectorAll('[data-tab]').forEach((t) => t.classList.toggle('active', t === b));
    }),
  );
  on($('template-title'), 'change', () => model.changeTitle($('template-title').value));
  on($('model-purpose'), 'change', () => safe(() => model.changePurpose($('model-purpose').value)));
  for (const id of [
    'paper-format',
    'paper-width',
    'paper-height',
    'orientation',
    'margins',
    'page-count',
  ])
    on($(id), 'change', () =>
      safe(() => {
        const value = {
          format: $('paper-format').value,
          width: Number($('paper-width').value),
          height: Number($('paper-height').value),
          orientation: $('orientation').value,
          margin: Number($('margins').value),
          count: Number($('page-count').value),
        };
        if (id === 'paper-format' && value.format === 'custom' && value.margin === 15)
          value.margin = 3;
        model.changePage(value);
      }),
    );
  cleanup.push(mountEditorZoom(model));
  cleanup.push(mountCanvasSelection(editor, model));
  const resize = () => {
    const top = document.querySelector('.titlebar').getBoundingClientRect().bottom;
    document.querySelector('.workspace').style.height =
      Math.max(240, innerHeight - top - 32) + 'px';
    $('properties').style.top = top + 'px';
    $('insert-panel').style.top = top + 'px';
  };
  on(window, 'resize', resize);
  const titleObserver = new ResizeObserver(resize);
  titleObserver.observe(document.querySelector('.titlebar'));
  cleanup.push(() => titleObserver.disconnect());
  on(window, 'beforeprint', () => {
    model.preparePrint();
    if ($('preview-dialog').hidden) {
      printPreview = true;
      preview();
    }
  });
  on(window, 'afterprint', () => {
    if (printPreview) {
      printPreview = false;
      closePreview();
    }
  });
  on(document, 'keydown', (e) => {
    if (editorKey(e) === 'Escape') {
      if (!$('preview-dialog').hidden) closePreview();
      else $('table-dialog').hidden = true;
    }
  });
  function refresh(force = false) {
    if (disposed) return;
    const editable = editor.isEditable,
      o = selectedObject(editor.state);
    document.body.classList.toggle('editing', editable);
    document.body.classList.toggle('readonly', !editable);
    $('template-title').readOnly = !editable;
    $('dirty-state').textContent = model.isDirty()
      ? 'Modifiche non salvate'
      : editable
        ? 'Bozza'
        : 'Sola lettura';
    document
      .querySelectorAll('.palette-button,#page-tools input,#page-tools select')
      .forEach((n) => (n.disabled = !editable));
    $('orientation').disabled = !editable || model.page().format === 'custom';
    $('model-purpose').disabled = !editable || !model.purposeEditable();
    $('page-count').disabled = !editable || model.purpose() === 'ETICHETTA';
    for (const c of ['undo', 'redo'])
      document.querySelector(`[data-command="${c}"]`).disabled = !editable || !editor.can()[c]();
    document
      .querySelectorAll('.canvas-object[data-object-id]')
      .forEach((n) =>
        n.classList.toggle('context-selected', n.dataset.objectId === o?.node.attrs.id),
      );
    if (o || editor.state.selection instanceof CanvasMultiSelection)
      $('properties').classList.add('open');
    const a = o?.node.attrs;
    if (a) {
      for (const [id, value] of [
        ['font-family', a.fontFamily],
        ['font-size', a.fontSize],
        ['line-height', String(a.lineHeight)],
        ['text-color', a.color],
        [
          'text-type',
          o.node.firstChild?.type.name === 'heading'
            ? String(o.node.firstChild.attrs.level)
            : 'paragraph',
        ],
      ])
        if (document.activeElement !== $(id)) $(id).value = value;
    }
    $('text-properties')
      .querySelectorAll('button,input,select')
      .forEach((n) => (n.disabled = !editable || !['text', 'field'].includes(a?.kind)));
    for (const c of ['bold', 'italic', 'underline'])
      document
        .querySelector(`[data-command="${c}"]`)
        .setAttribute('aria-pressed', String(Boolean(a?.[c])));
    const s = pagePixels(model.page());
    $('selection-status').textContent =
      `${model.page().format === 'a4' ? 'A4' : model.page().width + ' × ' + model.page().height + ' mm'} · ${s.count} ${s.count === 1 ? 'pagina' : 'pagine'}${o ? ' · ' + objectName(o.node) : editor.state.selection instanceof CanvasMultiSelection ? ' · ' + editor.state.selection.ids.length + ' elementi selezionati' : ''}`;
    properties(force);
    requestAnimationFrame(overflow);
  }
  refresh(true);
  resize();
  return {
    flush() {
      const input = document.activeElement;
      if (
        input?.tagName === 'INPUT' &&
        input.type !== 'file' &&
        input.id !== 'template-title' &&
        (input.closest('#page-tools') || input.closest('#properties'))
      )
        input.dispatchEvent(new Event('change'));
    },
    insert,
    run,
    refresh,
    notify,
    add,
    dispose() {
      disposed = true;
      clearTimeout(timer);
      cleanup.forEach((fn) => fn());
      $('selection-properties').replaceChildren();
      $('preview-content').replaceChildren();
    },
  };
}
