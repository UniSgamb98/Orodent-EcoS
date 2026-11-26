import {
  CanvasSelectionExtension,
  CanvasMultiSelection,
  canvasObjects,
  selectCanvasObjects,
  moveCanvasSelection,
  deleteCanvasSelection,
} from './canvas-selection.js';
import { GapCursor } from '@tiptap/pm/gapcursor';
import { normalizeMatrixObjects } from './matrix-layout.js';
import { normalizeSymbolObjects } from './symbol-layout.js';
import { createAlignmentGuides } from './alignment-guides.js';
import { editorKey } from './keyboard.js';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { TableKit } from '@tiptap/extension-table';
import { TextStyleKit } from '@tiptap/extension-text-style';
import TextAlign from '@tiptap/extension-text-align';
import { NodeSelection } from '@tiptap/pm/state';
import { EditorState } from '@tiptap/pm/state';
import {
  TonBlock,
  TonField,
  TonColumns,
  TonColumn,
  TableAppearance,
  LocalImage,
} from './extensions.js';
import { setProductionData } from './laboratory-blocks.js';
import {
  defaultPage,
  documentSchemaVersion,
  readModel,
  writeModel,
  applyPage,
} from './document-model.js';
import {
  LabelField,
  LabelValue,
  upgradeLabelObjects,
  Gs1Matrix,
  setLabelData,
  validateLabelNodes,
} from './label-elements.js';
import { paperSize, validatePage } from './document-model.js';
import {
  CanvasObject,
  CanvasDocument,
  setCanvasPageProvider,
  setCanvasAlignmentProvider,
  selectedObject,
  updateObject,
  selectObjectText,
} from './canvas-object.js';
import { pagePixels, clampGeometry, moveGeometry, px } from './canvas-model.js';
import { validateCanvas } from './document-model.js';
import { mountEditorUI } from './editor-ui.js';

// Offline visual editor. Business calculations, DB versioning and PDF emission
// are application responsibilities; the visible laboratory values are fixtures.
let editor,
  ui,
  disposed = false,
  restoring = false,
  dirty = false;
let title = 'Foglio operativo di pressatura',
  purpose = 'PRODUZIONE';
let page = { ...defaultPage },
  zoom = 0.8,
  canvas = true,
  purposeEditable = true,
  nextId = 1,
  alignment;
setCanvasPageProvider(() => page);
setCanvasAlignmentProvider(() => alignment);
const activeEditor = () => {
  if (!editor || disposed) throw new Error('Editor non disponibile.');
  return editor;
};
function applyLayout() {
  applyPage(page, zoom);
  const size = pagePixels(page),
    height = size.count * size.height + (size.count - 1) * 24;
  document.documentElement.style.setProperty('--document-height', height + 'px');
  document.documentElement.style.setProperty('--page-step', size.height + 24 + 'px');
  document.body.classList.toggle('canvas-layout', canvas);
  document.getElementById('model-purpose').value = purpose;
  document.getElementById('page-count').value = page.count ?? 1;
  document.getElementById('canvas-page-guides').innerHTML = canvas
    ? Array.from(
        { length: size.count },
        (_, i) =>
          `<div class="canvas-page-guide" style="top:${i * (size.height + 24)}px;height:${size.height}px"><span>Pagina ${i + 1}</span></div>`,
      ).join('')
    : '';
  document.getElementById('paper-format').value = page.format;
  document.getElementById('paper-width').value = page.width;
  document.getElementById('paper-height').value = page.height;
  document.getElementById('custom-paper').hidden = page.format !== 'custom';
  document.getElementById('orientation').disabled = page.format === 'custom';
  document.body.classList.toggle('label-layout', purpose === 'ETICHETTA');
  document.getElementById('orientation').value = page.orientation;
  document.getElementById('margins').value = String(page.margin);
  document.getElementById('template-title').value = title;
}
function restore(payload) {
  window.dispatchEvent(new Event('orodent-zoom-change'));
  const instance = activeEditor();
  const parsed = readModel(payload, instance.schema);
  restoring = true;
  try {
    // Opening a model starts a new editing session; undo must not resurrect a
    // document from the previous session.
    instance.view.updateState(
      EditorState.create({
        schema: instance.schema,
        doc: parsed.content,
        plugins: instance.state.plugins,
      }),
    );
    title = parsed.title;
    page = parsed.page;
    purpose = parsed.purpose;
    canvas = parsed.canvas;
    dirty = false;
    applyLayout();
    ui.refresh(true);
  } finally {
    restoring = false;
  }
}
function upgradeEditableObjects() {
  const current = editor.getJSON(),
    fields = upgradeLabelObjects(current);
  const symbols = normalizeSymbolObjects(fields ?? current);
  const matrices = normalizeMatrixObjects(symbols ?? fields ?? current);
  const upgraded = matrices ?? symbols ?? fields;
  if (upgraded) {
    editor.commands.setContent(upgraded);
    dirty = true;
  }
}
function ensureCanvas() {
  if (canvas) {
    upgradeEditableObjects();
    return;
  }
  const size = pagePixels(page),
    paper = document.getElementById('paper'),
    origin = paper.getBoundingClientRect(),
    scale = Number.parseFloat(getComputedStyle(paper).getPropertyValue('--zoom')) || 1;
  const objects = [];
  let maxPage = 0;
  editor.state.doc.forEach((node, pos) => {
    if (node.type.name === 'paragraph' && !node.content.size) return;
    const dom = editor.view.nodeDOM(pos),
      rect = dom?.getBoundingClientRect();
    const y = rect ? Math.max(0, (rect.top - origin.top) / scale) : objects.length * 90;
    let pageNumber = Math.floor(y / size.height),
      localY = y - pageNumber * size.height;
    const height = Math.max(24, Math.min(size.height, rect?.height / scale || 80));
    if (localY + height > size.height) {
      pageNumber++;
      localY = Math.min(px(page.margin), size.height - height);
    }
    if (pageNumber >= 50) throw Error('Il modello supera le 50 pagine.');
    maxPage = Math.max(maxPage, pageNumber);
    const kind =
      {
        image: 'image',
        gs1Matrix: 'matrix',
        tonBlock: 'block',
        table: 'table',
        tonColumns: 'group',
      }[node.type.name] ?? 'text';
    const attrs = clampGeometry(
      {
        id: 'obj-' + nextId++,
        kind,
        x: rect ? Math.max(0, (rect.left - origin.left) / scale) : px(page.margin),
        y: localY,
        width: rect?.width / scale || size.width - px(page.margin) * 2,
        height,
        page: pageNumber,
        z: objects.length,
      },
      size,
    );
    objects.push({ type: 'canvasObject', attrs, content: [node.toJSON()] });
  });
  if (purpose === 'ETICHETTA' && maxPage > 0)
    throw Error(
      'Il modello precedente supera il formato etichetta. Crea un modello con il formato necessario.',
    );
  page = { ...page, count: Math.max(page.count ?? 1, maxPage + 1) };
  canvas = true;
  editor.commands.setContent({ type: 'doc', content: objects });
  upgradeEditableObjects();
  dirty = true;
  applyLayout();
}
function canvasSheets() {
  const source = document.createElement('div');
  source.innerHTML = activeEditor().getHTML();
  const size = paperSize(page),
    sheets = [];
  for (let index = 0; index < (page.count ?? 1); index++) {
    const nodes = [...source.children]
      .filter((n) => Number(n.dataset.canvasPage) === index)
      .map((n) => n.outerHTML)
      .join('');
    sheets.push(
      `<section class="canvas-sheet" data-page="${index}" style="width:${size.width}mm;height:${size.height}mm;--page-step:0px"><div class="canvas-document tiptap">${nodes}</div></section>`,
    );
  }
  return sheets.join('');
}
function renderCanvas(id) {
  validateCanvas(activeEditor().state.doc, page);
  const size = paperSize(page),
    css = document.querySelector('style').textContent;
  return (
    '<!doctype html><html lang="it"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src data:"><style>' +
    css +
    `html,body {display:block!important;margin:0!important;padding:0!important;overflow:auto!important;background:#e3edf3;}
     #${id} {display:block;box-sizing:border-box;width:${size.width}mm;margin:auto;padding:0;}
     .canvas-sheet {margin:0 0 16px;background:#fff;overflow:hidden;}
     .canvas-sheet .label-field,.canvas-sheet .ton-field {background:transparent;border:0;padding:0;}
     @page {size:${size.width}mm ${size.height}mm;margin:0;}
     @media print {html,body {background:white;overflow:visible!important;} body > #${id} {display:block!important;width:auto;margin:0;padding:0;}
       .canvas-sheet {margin:0;break-after:page;page-break-after:always;} .canvas-sheet:last-child {break-after:auto;page-break-after:auto;}}
    </style></head><body class="issued-canvas"><main id="${id}" data-layout="canvas">${canvasSheets()}</main></body></html>`
  );
}
try {
  editor = new Editor({
    element: document.getElementById('editor'),
    injectCSS: false,
    extensions: [
      CanvasDocument,
      CanvasObject,
      CanvasSelectionExtension,
      StarterKit.configure({
        link: false,
        document: false,
        trailingNode: false,
      }),
      TableKit.configure({ table: { resizable: true } }),
      TextStyleKit,
      TextAlign.configure({ types: ['heading', 'paragraph'] }),
      LocalImage,
      LabelField,
      LabelValue,
      Gs1Matrix,
      TonBlock,
      TonField,
      TonColumns,
      TonColumn,
      TableAppearance,
    ],
    editorProps: {
      attributes: {
        'aria-label': 'Contenuto del documento',
        spellcheck: 'false',
      },
      handleTextInput(view, from, to, text) {
        if (!canvas) return false;
        const object = selectedObject(view.state);
        if (!object) return true;
        if (view.state.selection instanceof NodeSelection && object.node.attrs.kind === 'text') {
          editor.commands.setTextSelection(object.pos + object.node.nodeSize - 2);
          editor.commands.insertContent(text);
          return true;
        }
        return object.node.attrs.kind !== 'text' && object.node.attrs.kind !== 'table';
      },
      handlePaste(view, event) {
        if (!canvas) return false;
        const object = selectedObject(view.state);
        if (
          object?.node.attrs.kind === 'field' ||
          view.state.selection instanceof CanvasMultiSelection
        )
          return true;
        if (object) return false;
        const text = event.clipboardData?.getData('text/plain');
        if (text) {
          ui.add({ type: 'paragraph', content: [{ type: 'text', text }] }, 'text');
          return true;
        }
        return true;
      },
      handleKeyDown(view, event) {
        if (!canvas) return false;
        const object = selectedObject(view.state),
          node = view.state.selection instanceof NodeSelection;
        const key = editorKey(event);
        const delta = {
          ArrowLeft: [-1, 0],
          Left: [-1, 0],
          ArrowRight: [1, 0],
          Right: [1, 0],
          ArrowUp: [0, -1],
          Up: [0, -1],
          ArrowDown: [0, 1],
          Down: [0, 1],
        }[key];
        const multi = view.state.selection instanceof CanvasMultiSelection;
        if (multi) {
          if (delta && !event.ctrlKey && !event.metaKey && !event.altKey) {
            event.preventDefault();
            const step = event.shiftKey ? 10 : 1;
            moveCanvasSelection(editor, delta[0] * step, delta[1] * step, page);
            return true;
          }
          if (key === 'Delete' || key === 'Backspace') {
            event.preventDefault();
            return deleteCanvasSelection(editor);
          }
          if (key === 'Escape') {
            editor.view.dispatch(
              view.state.tr.setSelection(new GapCursor(view.state.doc.resolve(0))),
            );
            return true;
          }
          if (key === 'Enter') return true;
        }
        if (
          (event.ctrlKey || event.metaKey) &&
          key.toLowerCase() === 'a' &&
          !(object?.node.attrs.kind === 'text' && !node)
        ) {
          event.preventDefault();
          editor.view.dispatch(
            view.state.tr.setSelection(
              selectCanvasObjects(
                view.state.doc,
                canvasObjects(view.state.doc).map((o) => o.node.attrs.id),
              ),
            ),
          );
          return true;
        }
        if (object && node && delta && !event.ctrlKey && !event.metaKey && !event.altKey) {
          event.preventDefault();
          const step = event.shiftKey ? 10 : 1;
          updateObject(
            editor,
            object,
            moveGeometry(object.node.attrs, delta[0] * step, delta[1] * step, pagePixels(page)),
          );
          return true;
        }
        if (object && key === 'Escape') {
          editor.commands.setNodeSelection(object.pos);
          return true;
        }
        if (
          object?.node.attrs.kind === 'text' &&
          (event.ctrlKey || event.metaKey) &&
          key.toLowerCase() === 'a'
        ) {
          event.preventDefault();
          selectObjectText(editor, object);
          return true;
        }
        if (key === 'Enter' && (!object || node)) {
          event.preventDefault();
          if (object?.node.attrs.kind === 'text') editor.commands.setTextSelection(object.pos + 2);
          return true;
        }
        return false;
      },
      handleDOMEvents: { dragstart: () => canvas },
    },
    content: { type: 'doc', content: [] },
    onTransaction({ transaction }) {
      if (disposed) return;
      if (transaction.docChanged && !restoring) dirty = true;
      ui?.refresh();
    },
    onSelectionUpdate() {
      if (!disposed) ui?.refresh();
    },
  });
  alignment = createAlignmentGuides(
    editor,
    () => page,
    () => zoom,
  );
  const model = {
    alignment,
    title: () => title,
    purpose: () => purpose,
    page: () => ({ ...page }),
    canvas: () => canvas,
    purposeEditable: () => purposeEditable,
    nextId: () => nextId++,
    ensureCanvas,
    html: () =>
      canvas
        ? canvasSheets()
        : `<article class="legacy-sheet">${activeEditor().getHTML()}</article>`,
    validate() {
      validateCanvas(activeEditor().state.doc, page);
    },
    changePurpose(value) {
      if (!purposeEditable || !editor.isEditable) return;
      if (!['PRODUZIONE', 'ETICHETTA'].includes(value)) throw Error('Utilizzo non supportato.');
      if (value !== purpose) {
        if (
          editor.state.doc.content.size &&
          !window.confirm('Cambiare l’utilizzo del modello mantenendo gli oggetti attuali?')
        ) {
          applyLayout();
          return;
        }
        if (value === 'ETICHETTA' && page.count > 1)
          throw Error('Riduci prima il modello a una pagina.');
        purpose = value;
        dirty = true;
      }
      applyLayout();
      ui.refresh(true);
    },
    serialize: () => writeModel(activeEditor(), title, page, purpose, canvas),
    restore,
    isDirty: () => dirty,
    changeTitle(value) {
      if (!editor.isEditable) return;
      const next = String(value).trim().slice(0, 150) || 'Modello di documento';
      if (next !== title) {
        title = next;
        dirty = true;
      }
      applyLayout();
      ui.refresh();
    },
    changePage(value) {
      if (!editor.isEditable) return;
      try {
        value = validatePage(value);
        if (purpose === 'ETICHETTA' && value.count !== 1)
          throw Error('Un’etichetta deve avere una sola pagina.');
        if (canvas) validateCanvas(activeEditor().state.doc, value);
      } catch (error) {
        applyLayout();
        ui.refresh(true);
        throw error;
      }
      if (JSON.stringify(value) !== JSON.stringify(page)) dirty = true;
      page = { ...value };
      applyLayout();
      ui.refresh();
    },
    zoomValue: () => zoom,
    zoom(value, point) {
      if (!Number.isFinite(value) || value < 0.25 || value > 2) return;
      window.dispatchEvent(new Event('orodent-zoom-change'));
      const canvas = document.getElementById('canvas'),
        paper = document.getElementById('paper');
      const rect = canvas.getBoundingClientRect(),
        before = paper.getBoundingClientRect();
      const x =
        point && point.x >= rect.left && point.x <= rect.right
          ? point.x
          : rect.left + rect.width / 2;
      const y =
        point && point.y >= rect.top && point.y <= rect.bottom
          ? point.y
          : rect.top + rect.height / 2;
      const localX = (x - before.left) / zoom,
        localY = (y - before.top) / zoom;
      zoom = value;
      applyLayout();
      const after = paper.getBoundingClientRect();
      canvas.scrollLeft += after.left + localX * zoom - x;
      canvas.scrollTop += after.top + localY * zoom - y;
    },
    preparePrint() {
      let style = document.getElementById('print-page');
      if (!style) {
        style = document.createElement('style');
        style.id = 'print-page';
        document.head.append(style);
      }
      style.textContent = `@page { size: ${paperSize(page).width}mm ${paperSize(page).height}mm; margin: ${canvas ? 0 : page.margin}mm; }`;
    },
  };
  applyLayout();
  ui = mountEditorUI(editor, model);
  window.OrodentDocumentEditor = Object.freeze({
    schemaVersion: documentSchemaVersion,
    tiptapVersion: '3.32.1',
    serialize() {
      // Native Save can run before an HTML input loses focus.
      ui.flush();
      model.changeTitle(document.getElementById('template-title').value);
      const payload = model.serialize();
      readModel(payload, activeEditor().schema);
      return payload;
    },
    newModel(kind = 'PRODUZIONE') {
      setLabelData(null);
      purposeEditable = true;
      restore(
        JSON.stringify({
          schemaVersion: documentSchemaVersion,
          purpose: kind,
          layout: 'canvas',
          title: 'Nuovo modello',
          page: {
            ...defaultPage,
            ...(kind === 'ETICHETTA'
              ? { format: 'custom', width: 100, height: 65, margin: 3 }
              : {}),
          },
          content: { type: 'doc', content: [] },
        }),
      );
    },
    renderLabel(template, payload) {
      setLabelData(JSON.parse(payload));
      restore(template);
      if (purpose !== 'ETICHETTA') throw new Error('Il modello selezionato non è un’etichetta.');
      validateLabelNodes(activeEditor().state.doc);
      activeEditor().setEditable(false);
      if (canvas) return renderCanvas('label-output');
      const content = activeEditor().getHTML(),
        size = paperSize(page);
      const css = document.querySelector('style').textContent;
      return (
        '<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src data:"><style>' +
        css +
        `html,body {display:block!important;width:${size.width}mm!important;height:${size.height}mm!important;margin:0!important;padding:0!important;background:white!important;overflow:hidden!important;}
         #label-output {font:11pt/1.5 Arial,sans-serif;box-sizing:border-box;width:${size.width}mm;height:${size.height}mm;padding:${page.margin}mm;color:#000;background:#fff;overflow:hidden;}
         #label-output .tiptap {outline:none;min-height:0;}
         .label-field {background:transparent!important; border:0!important; padding:0!important;}
         @page {size:${size.width}mm ${size.height}mm;margin:0;}
         @media print {body.label-layout > #label-output {display:block!important;}}` +
        '</style></head><body class="label-layout"><main id="label-output"><div class="tiptap">' +
        content +
        '</div></main></body></html>'
      );
    },
    setEmbedded() {
      document.body.classList.add('embedded');
      document.querySelector('.example-label').textContent = 'Dati di esempio';
      window.dispatchEvent(new Event('resize'));
    },
    restore,
    renderProduction(template, payload) {
      setProductionData(payload);
      restore(template);
      activeEditor().setEditable(false);
      if (canvas) return renderCanvas('production-output');
      const content = activeEditor().getHTML();
      const css = document.querySelector('style').textContent;
      const pageWidth = paperSize(page).width + 'mm';
      const extra = `body.issued-document { display:block; margin:0; padding:18px; overflow:auto; background:#e3edf3; }
        #production-output { display:block; box-sizing:border-box; width:${pageWidth}; max-width:none; margin:auto; padding:${page.margin}mm; background:white; color:#243e50; }
        #production-output .ton-field { background:transparent; border:0; }
        @page { size:${paperSize(page).width}mm ${paperSize(page).height}mm; margin:${page.margin}mm; }
        @media print { body.issued-document { padding:0; background:white; overflow:visible; }
          body.issued-document > #production-output { display:block!important; width:auto; margin:0; padding:0; box-shadow:none; }
          #production-output .tiptap { display:block!important; } }`;
      return (
        '<!doctype html><html lang="it"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src data:"><style>' +
        css +
        extra +
        '</style></head><body class="issued-document"><main id="production-output"><div class="tiptap">' +
        content +
        '</div></main></body></html>'
      );
    },
    isDirty() {
      activeEditor();
      return dirty;
    },
    markSaved() {
      activeEditor();
      dirty = false;
      ui.refresh();
    },
    setEditable(editable) {
      activeEditor().setEditable(Boolean(editable));
      if (editable) ensureCanvas();
      ui.refresh(true);
    },
    setPurposeEditable(value) {
      purposeEditable = Boolean(value);
      ui.refresh();
    },
    run(command) {
      activeEditor();
      return ui.run(command);
    },
    insert(kind) {
      activeEditor();
      return ui.insert(kind);
    },
    destroy() {
      if (disposed) return;
      disposed = true;
      ui.dispose();
      editor.destroy();
      document.documentElement.dataset.editorReady = 'false';
    },
  });
  document.documentElement.dataset.editorReady = 'true';
} catch (error) {
  document.documentElement.dataset.editorReady = 'error';
  document.getElementById('error').hidden = false;
  throw error;
}
