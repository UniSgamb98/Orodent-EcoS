import { sizePaper } from './paper-layout.js';
import { validateDocument } from './extensions.js';
import { normalizeLayoutJSON } from './layout-model.js';

export const documentSchemaVersion = 3;
export const defaultPage = Object.freeze({ orientation: 'portrait', margin: 15 });

export function readModel(payload, schema) {
  if (typeof payload !== 'string' || payload.length > 8_000_000) throw new Error('Il modello è troppo grande o non è valido.');
  let envelope;
  try { envelope = JSON.parse(payload); } catch { throw new Error('Il file non contiene un modello valido. Usa un file esportato dall’editor.'); }
  if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) throw new Error('Il file non contiene un modello valido.');
  if (![1, 2, documentSchemaVersion].includes(envelope.schemaVersion) || envelope.content?.type !== 'doc') {
    throw new Error('Formato del documento non supportato.');
  }
  if (envelope.page !== undefined && (!envelope.page || typeof envelope.page !== 'object' || Array.isArray(envelope.page))) throw new Error('Impostazioni della pagina non valide.');
  const page = { orientation: envelope.page?.orientation ?? defaultPage.orientation, margin: envelope.page?.margin ?? defaultPage.margin };
  if (!['portrait', 'landscape'].includes(page.orientation) || ![10, 15, 20].includes(page.margin)) throw new Error('Formato o margini non supportati.');
  const title = envelope.title ?? 'Modello di documento';
  if (typeof title !== 'string' || !title.trim() || title.length > 150) throw new Error('Nome del modello: da 1 a 150 caratteri.');
  let content;
  try { content = schema.nodeFromJSON(normalizeLayoutJSON(envelope.content)); content.check(); } catch { throw new Error('Il contenuto del modello non è supportato da questa versione dell’editor.'); }
  if (content.type.name !== 'doc') throw new Error('Contenuto del documento non valido.');
  validateDocument(content);
  return { title: title.trim(), page, content };
}

export function writeModel(editor, title, page) {
  return JSON.stringify({ schemaVersion: documentSchemaVersion, title, page, content: normalizeLayoutJSON(editor.getJSON()) });
}

export function applyPage(page, zoom) {
  const root = document.documentElement.style;
  root.setProperty('--paper-width', page.orientation === 'portrait' ? '210mm' : '297mm');
  root.setProperty('--paper-height', page.orientation === 'portrait' ? '297mm' : '210mm');
  root.setProperty('--paper-margin', page.margin + 'mm');
  root.setProperty('--zoom', String(zoom));
  sizePaper(zoom);
}
