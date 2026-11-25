import { validateObject } from './canvas-model.js';
import { sizePaper } from './paper-layout.js';
import { validateDocument } from './extensions.js';
import { normalizeLayoutJSON } from './layout-model.js';

export const documentSchemaVersion = 6;
import { defaultPage, validatePage, paperSize } from './page-settings.js';
export { defaultPage, validatePage, paperSize } from './page-settings.js';

export function readModel(payload, schema) {
  if (typeof payload !== 'string' || payload.length > 8_000_000)
    throw new Error('Il modello è troppo grande o non è valido.');
  let envelope;
  try {
    envelope = JSON.parse(payload);
  } catch {
    throw new Error('Il modello salvato non contiene una struttura valida.');
  }
  if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope))
    throw new Error('Il file non contiene un modello valido.');
  if (
    ![1, 2, 3, 4, 5, documentSchemaVersion].includes(envelope.schemaVersion) ||
    envelope.content?.type !== 'doc'
  ) {
    throw new Error('Formato del documento non supportato.');
  }
  if (
    envelope.page !== undefined &&
    (!envelope.page || typeof envelope.page !== 'object' || Array.isArray(envelope.page))
  )
    throw new Error('Impostazioni della pagina non valide.');
  const page = validatePage({ ...defaultPage, ...envelope.page });
  const purpose = envelope.purpose ?? 'PRODUZIONE';
  if (!['PRODUZIONE', 'ETICHETTA'].includes(purpose))
    throw new Error('Tipo di modello non supportato.');
  const title = envelope.title ?? 'Modello di documento';
  if (typeof title !== 'string' || !title.trim() || title.length > 150)
    throw new Error('Nome del modello: da 1 a 150 caratteri.');
  let content;
  try {
    content = schema.nodeFromJSON(normalizeLayoutJSON(envelope.content));
    content.check();
  } catch {
    throw new Error('Il contenuto del modello non è supportato da questa versione dell’editor.');
  }
  if (content.type.name !== 'doc') throw new Error('Contenuto del documento non valido.');
  validateDocument(content);
  const canvas = envelope.layout === 'canvas';
  if (envelope.layout !== undefined && !['canvas', 'flow'].includes(envelope.layout))
    throw new Error('Disposizione del documento non valida.');
  if (canvas) validateCanvas(content, page);
  else if (content.content.content.some((n) => n.type.name === 'canvasObject'))
    throw new Error('Disposizione del documento non valida.');
  if (purpose === 'ETICHETTA' && page.count !== 1)
    throw new Error('Un’etichetta deve avere una sola pagina.');
  return { title: title.trim(), purpose, page, content, canvas };
}

export function writeModel(editor, title, page, purpose = 'PRODUZIONE', canvas = true) {
  return JSON.stringify({
    schemaVersion: documentSchemaVersion,
    title,
    purpose,
    page,
    layout: canvas ? 'canvas' : 'flow',
    content: normalizeLayoutJSON(editor.getJSON()),
  });
}

export function applyPage(page, zoom) {
  const root = document.documentElement.style;
  const size = paperSize(page);
  root.setProperty('--paper-width', size.width + 'mm');
  root.setProperty('--paper-height', size.height + 'mm');
  root.setProperty('--paper-margin', page.margin + 'mm');
  root.setProperty('--zoom', String(zoom));
  sizePaper(zoom);
}

export function validateCanvas(doc, page) {
  const ids = new Set();
  doc.forEach((node) => {
    if (node.type.name !== 'canvasObject')
      throw new Error('Tutti gli elementi devono appartenere a un oggetto.');
    validateObject(node.attrs, page);
    if (ids.has(node.attrs.id)) throw new Error('Identificatore oggetto duplicato.');
    ids.add(node.attrs.id);
  });
}
