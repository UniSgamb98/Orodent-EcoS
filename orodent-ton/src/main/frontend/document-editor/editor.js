import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { TableKit } from '@tiptap/extension-table';
import { TextStyleKit } from '@tiptap/extension-text-style';
import TextAlign from '@tiptap/extension-text-align';
import { EditorState } from '@tiptap/pm/state';
import { TonBlock, TonField, TonColumns, TonColumn, TableAppearance, LocalImage } from './extensions.js';
import { initialContent } from './laboratory-blocks.js';
import { defaultPage, documentSchemaVersion, readModel, writeModel, applyPage } from './document-model.js';
import { mountEditorUI } from './editor-ui.js';

// Offline visual editor. Business calculations, DB versioning and PDF emission
// are application responsibilities; the visible laboratory values are fixtures.
let editor, ui, disposed = false, restoring = false, dirty = false;
let title = 'Foglio operativo di pressatura';
let page = { ...defaultPage }, zoom = .8;
const activeEditor = () => {
  if (!editor || disposed) throw new Error('Editor non disponibile.');
  return editor;
};
function applyLayout() {
  applyPage(page, zoom);
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
    title = parsed.title; page = parsed.page; dirty = false;
    applyLayout(); ui.refresh(true);
  } finally { restoring = false; }
}
try {
  editor = new Editor({
    element: document.getElementById('editor'),
    injectCSS: false,
    extensions: [StarterKit.configure({ link: false }), TableKit.configure({ table: { resizable: true } }),
      TextStyleKit, TextAlign.configure({ types: ['heading', 'paragraph'] }),
      LocalImage, TonBlock, TonField, TonColumns, TonColumn, TableAppearance],
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
    serialize: () => writeModel(activeEditor(), title, page),
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
      if (!['portrait', 'landscape'].includes(value.orientation) || ![10, 15, 20].includes(value.margin)) throw new Error('Formato non supportato.');
      if (value.orientation !== page.orientation || value.margin !== page.margin) dirty = true;
      page = { ...value }; applyLayout(); ui.refresh();
    },
    zoom(value) { if ([.5, .65, .8, 1, 1.2].includes(value)) { zoom = value; applyLayout(); } },
    preparePrint() {
      let style = document.getElementById('print-page');
      if (!style) { style = document.createElement('style'); style.id = 'print-page'; document.head.append(style); }
      style.textContent = `@page { size: A4 ${page.orientation}; margin: ${page.margin}mm; }`;
    },
  };
  applyLayout();
  ui = mountEditorUI(editor, model);
  window.OrodentDocumentEditor = Object.freeze({
    schemaVersion: documentSchemaVersion,
    tiptapVersion: '3.32.1',
    serialize: model.serialize,
    restore,
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
