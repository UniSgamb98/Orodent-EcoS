import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { TableKit } from '@tiptap/extension-table';
import { TextStyleKit } from '@tiptap/extension-text-style';
import TextAlign from '@tiptap/extension-text-align';
import { EditorState } from '@tiptap/pm/state';
import { TonBlock, TonField, TonColumns, TonColumn, TableAppearance, LocalImage } from './extensions.js';
import { initialContent, setProductionData } from './laboratory-blocks.js';
import { defaultPage, documentSchemaVersion, readModel, writeModel, applyPage } from './document-model.js';
import { LabelField, Gs1Matrix, setLabelData, initialLabel, validateLabelNodes } from './label-elements.js';
import { paperSize, validatePage } from './document-model.js';
import { mountEditorUI } from './editor-ui.js';

// Offline visual editor. Business calculations, DB versioning and PDF emission
// are application responsibilities; the visible laboratory values are fixtures.
let editor, ui, disposed = false, restoring = false, dirty = false;
let title = 'Foglio operativo di pressatura', purpose = 'PRODUZIONE';
let page = { ...defaultPage }, zoom = .8;
const activeEditor = () => {
  if (!editor || disposed) throw new Error('Editor non disponibile.');
  return editor;
};
function applyLayout() {
  applyPage(page, zoom);
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
  const instance = activeEditor();
  const parsed = readModel(payload, instance.schema);
  restoring = true;
  try {
    // Opening a model starts a new editing session; undo must not resurrect a
    // document from the previous session.
    instance.view.updateState(EditorState.create({
      schema: instance.schema, doc: parsed.content, plugins: instance.state.plugins,
    }));
    title = parsed.title; page = parsed.page; purpose = parsed.purpose; dirty = false;
    applyLayout(); ui.refresh(true);
  } finally { restoring = false; }
}
try {
  editor = new Editor({
    element: document.getElementById('editor'),
    injectCSS: false,
    extensions: [StarterKit.configure({ link: false }), TableKit.configure({ table: { resizable: true } }),
      TextStyleKit, TextAlign.configure({ types: ['heading', 'paragraph'] }),
      LocalImage, LabelField, Gs1Matrix, TonBlock, TonField, TonColumns, TonColumn, TableAppearance],
    editorProps: { attributes: { 'aria-label': 'Contenuto del documento', spellcheck: 'false' } },
    content: initialContent(),
    onTransaction({ transaction }) {
      if (disposed) return;
      if (transaction.docChanged && !restoring) dirty = true;
      ui?.refresh();
    },
    onSelectionUpdate() { if (!disposed) ui?.refresh(); },
  });
  const model = {
    title: () => title,
    purpose: () => purpose,
    page: () => ({...page}),
    serialize: () => writeModel(activeEditor(), title, page, purpose),
    restore,
    isDirty: () => dirty,
    changeTitle(value) {
      if (!editor.isEditable) return;
      const next = String(value).trim().slice(0, 150) || 'Modello di documento';
      if (next !== title) { title = next; dirty = true; }
      applyLayout(); ui.refresh();
    },
    changePage(value) {
      if (!editor.isEditable) return;
      validatePage(value);
      if (JSON.stringify(value) !== JSON.stringify(page)) dirty = true;
      page = { ...value }; applyLayout(); ui.refresh();
    },
    zoomValue: () => zoom,
    zoom(value, point) {
      if (!Number.isFinite(value) || value < .25 || value > 2) return;
      window.dispatchEvent(new Event('orodent-zoom-change'));
      const canvas = document.getElementById('canvas'), paper = document.getElementById('paper');
      const rect = canvas.getBoundingClientRect(), before = paper.getBoundingClientRect();
      const x = point && point.x >= rect.left && point.x <= rect.right ? point.x : rect.left + rect.width / 2;
      const y = point && point.y >= rect.top && point.y <= rect.bottom ? point.y : rect.top + rect.height / 2;
      const localX = (x - before.left) / zoom, localY = (y - before.top) / zoom;
      zoom = value; applyLayout();
      const after = paper.getBoundingClientRect();
      canvas.scrollLeft += after.left + localX * zoom - x;
      canvas.scrollTop += after.top + localY * zoom - y;
    },
    preparePrint() {
      let style = document.getElementById('print-page');
      if (!style) { style = document.createElement('style'); style.id = 'print-page'; document.head.append(style); }
      style.textContent = `@page { size: ${paperSize(page).width}mm ${paperSize(page).height}mm; margin: ${page.margin}mm; }`;
    },
  };
  applyLayout();
  ui = mountEditorUI(editor, model);
  window.OrodentDocumentEditor = Object.freeze({
    schemaVersion: documentSchemaVersion,
    tiptapVersion: '3.32.1',
    serialize() {
      // Native Save can run before an HTML input loses focus.
      model.changeTitle(document.getElementById('template-title').value);
      const payload = model.serialize(); readModel(payload, activeEditor().schema); return payload;
    },
    newModel(kind = 'PRODUZIONE') {
      const label = kind === 'ETICHETTA';
      setLabelData(null);
      restore(JSON.stringify({ schemaVersion: documentSchemaVersion, purpose: kind,
        title: label ? 'Etichetta dischi' : 'Foglio operativo di pressatura',
        page: label ? {...defaultPage,format:'custom',width:100,height:65,margin:3} : defaultPage,
        content: label ? initialLabel() : initialContent() }));
    },
    renderLabel(template, payload) {
      setLabelData(JSON.parse(payload));
      restore(template);
      if (purpose !== 'ETICHETTA') throw new Error('Il modello selezionato non è un’etichetta.');
      validateLabelNodes(activeEditor().state.doc);
      activeEditor().setEditable(false);
      const content = activeEditor().getHTML(), size = paperSize(page);
      const css = document.querySelector('style').textContent;
      return '<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src data:"><style>' + css +
        `html,body {display:block!important;width:${size.width}mm!important;height:${size.height}mm!important;margin:0!important;padding:0!important;background:white!important;overflow:hidden!important;}
         #label-output {font:11pt/1.5 Arial,sans-serif;box-sizing:border-box;width:${size.width}mm;height:${size.height}mm;padding:${page.margin}mm;color:#000;background:#fff;overflow:hidden;}
         #label-output .tiptap {outline:none;min-height:0;}
         .label-field {background:transparent!important; border:0!important; padding:0!important;}
         @page {size:${size.width}mm ${size.height}mm;margin:0;}
         @media print {body.label-layout > #label-output {display:block!important;}}` +
        '</style></head><body class="label-layout"><main id="label-output"><div class="tiptap">'+content+'</div></main></body></html>';
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
      return '<!doctype html><html lang="it"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src data:"><style>'
        + css + extra + '</style></head><body class="issued-document"><main id="production-output"><div class="tiptap">' + content + '</div></main></body></html>';
    },
    isDirty() { activeEditor(); return dirty; },
    markSaved() { activeEditor(); dirty = false; ui.refresh(); },
    setEditable(editable) { activeEditor().setEditable(Boolean(editable)); ui.refresh(true); },
    run(command) { activeEditor(); return ui.run(command); },
    insert(kind) { activeEditor(); return ui.insert(kind); },
    destroy() {
      if (disposed) return;
      disposed = true; ui.dispose(); editor.destroy();
      document.documentElement.dataset.editorReady = 'false';
    },
  });
  document.documentElement.dataset.editorReady = 'true';
} catch (error) {
  document.documentElement.dataset.editorReady = 'error';
  document.getElementById('error').hidden = false;
  throw error;
}
