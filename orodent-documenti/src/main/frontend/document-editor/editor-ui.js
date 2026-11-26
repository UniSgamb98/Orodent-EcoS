import { editorKey } from './keyboard.js';
import { CanvasMultiSelection, mountCanvasSelection } from './canvas-selection.js';
import { GapCursor } from '@tiptap/pm/gapcursor';
import { NodeSelection } from '@tiptap/pm/state';
import { labelFields, measureLabel } from './label-elements.js';
import symbols from './symbols.json';
import { symbolSize } from './symbol-layout.js';
import { blockTypes, fields, escape, checkOperationalContent } from './laboratory-blocks.js';
import { objectName } from './object-properties-profile.js';
import { mountObjectProperties } from './object-properties.js';
import { mountTextProperties } from './text-properties.js';
import { mountEditorZoom } from './editor-zoom.js';
import { selectedObject, updateObject } from './canvas-object.js';
import { pagePixels, px, clampGeometry } from './canvas-model.js';

const $ = (id) => document.getElementById(id);
export function mountEditorUI(editor, model) {
  const cleanup = [];
  let disposed = false,
    timer,
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
        symbolSize(symbol, px(8)),
      );
    }
    if (kind === 'gs1') return add({ type: 'gs1Matrix', attrs: { tight: true } }, 'matrix');
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
  const textProperties = mountTextProperties(editor, { on, safe, refresh });
  function run(command) {
    if (command === 'undo' || command === 'redo') return editor.chain().focus()[command]().run();
    if (!editor.isEditable) return false;
    if (textProperties.handles(command)) return textProperties.run(command);
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
  const inspector = mountObjectProperties({
    editor,
    model,
    safe,
    notify,
    uniqueId,
    run,
    deselect: () => actions.deselect(),
    requestImageReplacement: () => {
      replaceImage = true;
      $('image-file').click();
    },
  });
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
  cleanup.push(() => model.alignment.dispose());
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
    textProperties.update(o, editable);
    const s = pagePixels(model.page());
    $('selection-status').textContent =
      `${model.page().format === 'a4' ? 'A4' : model.page().width + ' × ' + model.page().height + ' mm'} · ${s.count} ${s.count === 1 ? 'pagina' : 'pagine'}${o ? ' · ' + objectName(o.node) : editor.state.selection instanceof CanvasMultiSelection ? ' · ' + editor.state.selection.ids.length + ' elementi selezionati' : ''}`;
    inspector.refresh(force);
    requestAnimationFrame(inspector.overflow);
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
