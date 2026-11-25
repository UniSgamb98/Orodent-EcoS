import { sizePaper } from './paper-layout.js';
import { validateDocument } from './extensions.js';
import { normalizeLayoutJSON } from './layout-model.js';

export const documentSchemaVersion = 5;
import { defaultPage, validatePage, paperSize } from './page-settings.js';
export { defaultPage, validatePage, paperSize } from './page-settings.js';

export function readModel(payload, schema) {
  if (typeof payload !== 'string' || payload.length > 8_000_000) throw new Error('Il modello è troppo grande o non è valido.');
  let envelope;
  try { envelope = JSON.parse(payload); } catch { throw new Error('Il file non contiene un modello valido. Usa un file esportato dall’editor.'); }
  if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) throw new Error('Il file non contiene un modello valido.');
  if (![1, 2, 3, 4, documentSchemaVersion].includes(envelope.schemaVersion) || envelope.content?.type !== 'doc') {
    throw new Error('Formato del documento non supportato.');
  }
  if (envelope.page !== undefined && (!envelope.page || typeof envelope.page !== 'object' || Array.isArray(envelope.page))) throw new Error('Impostazioni della pagina non valide.');
  const page = validatePage({ ...defaultPage, ...envelope.page });
  const purpose = envelope.purpose ?? 'PRODUZIONE';
  if (!['PRODUZIONE','ETICHETTA'].includes(purpose)) throw new Error('Tipo di modello non supportato.');
  const title = envelope.title ?? 'Modello di documento';
  if (typeof title !== 'string' || !title.trim() || title.length > 150) throw new Error('Nome del modello: da 1 a 150 caratteri.');
  let content;
  try { content = schema.nodeFromJSON(normalizeLayoutJSON(envelope.content)); content.check(); } catch { throw new Error('Il contenuto del modello non è supportato da questa versione dell’editor.'); }
  if (content.type.name !== 'doc') throw new Error('Contenuto del documento non valido.');
  validateDocument(content);
  return { title: title.trim(), purpose, page, content };
}

export function writeModel(editor, title, page, purpose = 'PRODUZIONE') {
  return JSON.stringify({ schemaVersion: documentSchemaVersion, title, purpose, page, content: normalizeLayoutJSON(editor.getJSON()) });
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
