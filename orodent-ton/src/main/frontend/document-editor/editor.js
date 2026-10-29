import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { TableKit } from '@tiptap/extension-table';

// This is the offline editor foundation, not the TON template or PDF engine.
// Its JSON content will be wrapped by the application's versioned model service.
const host = document.getElementById('editor');
const controls = [...document.querySelectorAll('[data-command]')];
let editor;
let disposed = false;

function activeEditor() {
  if (!editor || disposed) throw new Error('Editor non disponibile.');
  return editor;
}

function refreshControls() {
  if (!editor || disposed) return;
  for (const button of controls) {
    const command = button.dataset.command;
    const formatting = ['bold', 'italic', 'underline'].includes(command);
    button.disabled = !editor.isEditable
      || (command === 'undo' && !editor.can().undo())
      || (command === 'redo' && !editor.can().redo())
      || (command === 'addRowAfter' && !editor.can().addRowAfter())
      || (command === 'addColumnAfter' && !editor.can().addColumnAfter());
    if (formatting) button.setAttribute('aria-pressed', String(editor.isActive(command)));
  }
}

function run(command) {
  const instance = activeEditor();
  if (!instance.isEditable) return false;
  const chain = instance.chain().focus();
  switch (command) {
    case 'bold': return chain.toggleBold().run();
    case 'italic': return chain.toggleItalic().run();
    case 'underline': return chain.toggleUnderline().run();
    case 'undo': return chain.undo().run();
    case 'redo': return chain.redo().run();
    case 'table': return chain.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run();
    case 'addRowAfter': return chain.addRowAfter().run();
    case 'addColumnAfter': return chain.addColumnAfter().run();
    default: throw new Error('Comando non riconosciuto.');
  }
}

try {
  editor = new Editor({
    element: host,
    injectCSS: false,
    extensions: [
      StarterKit.configure({ link: false }),
      TableKit.configure({ table: { resizable: true } }),
    ],
    editorProps: { attributes: { 'aria-label': 'Contenuto del documento', spellcheck: 'false' } },
    content: {
      type: 'doc',
      content: [
        { type: 'heading', attrs: { level: 1 }, content: [{ type: 'text', text: 'Scheda di produzione' }] },
        { type: 'paragraph', content: [{ type: 'text', text: 'Scrivi le istruzioni e inserisci una tabella dalla barra degli strumenti.' }] },
      ],
    },
    onTransaction: refreshControls,
    onSelectionUpdate: refreshControls,
  });

  for (const button of controls) {
    button.onmousedown = event => event.preventDefault();
    button.onclick = () => run(button.dataset.command);
  }

  // Small, string-based bridge for a future JavaFX host. No network/DB bridge.
  window.OrodentDocumentEditor = Object.freeze({
    schemaVersion: 1,
    tiptapVersion: '3.32.1',
    serialize() {
      return JSON.stringify({ schemaVersion: 1, content: activeEditor().getJSON() });
    },
    restore(payload) {
      const envelope = JSON.parse(payload);
      if (envelope.schemaVersion !== 1 || envelope.content?.type !== 'doc') {
        throw new Error('Formato del documento non supportato.');
      }
      const instance = activeEditor();
      const content = instance.schema.nodeFromJSON(envelope.content);
      content.check();
      instance.commands.setContent(content.toJSON(), { emitUpdate: false, errorOnInvalidContent: true });
      refreshControls();
    },
    setEditable(editable) {
      activeEditor().setEditable(Boolean(editable));
      refreshControls();
    },
    run,
    destroy() {
      if (disposed) return;
      disposed = true;
      for (const button of controls) {
        button.onmousedown = null;
        button.onclick = null;
        button.disabled = true;
      }
      editor.destroy();
      document.documentElement.dataset.editorReady = 'false';
    },
  });
  refreshControls();
  document.documentElement.dataset.editorReady = 'true';
} catch (error) {
  document.documentElement.dataset.editorReady = 'error';
  document.getElementById('error').hidden = false;
  throw error;
}
