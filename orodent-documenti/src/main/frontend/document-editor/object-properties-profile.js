import { labelFields } from './label-elements.js';

// Capabilities describe UI sections; they do not change document values or calculations.
const profiles = Object.freeze({
  text: { name: 'Riquadro di testo', text: true, paragraph: true },
  field: { name: 'Dato automatico', text: true, paragraph: false },
  image: { name: 'Immagine' },
  matrix: { name: 'GS1 DataMatrix' },
  table: { name: 'Tabella' },
  block: { name: 'Blocco automatico' },
  group: { name: 'Gruppo' },
});
export function propertyProfile(node) {
  return profiles[node?.attrs.kind] ?? {};
}
export function objectName(node) {
  const profile = propertyProfile(node);
  return node.attrs.kind === 'field'
    ? (labelFields[node.firstChild?.attrs.key] ?? profile.name)
    : profile.name;
}
