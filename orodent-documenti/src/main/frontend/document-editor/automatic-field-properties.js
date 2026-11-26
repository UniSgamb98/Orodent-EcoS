import { dateFieldProperties } from './date-field-properties.js';
import { labelFields } from './label-elements.js';
import { escape } from './laboratory-blocks.js';

// Add a field-specific editor here, keyed by article, lot, diameter, etc.
// Each editor supplies render(context) and bind(context), after the shared sections.
const fieldEditors = { productionDate: dateFieldProperties, expiryDate: dateFieldProperties };
export function automaticFieldProperties(context) {
  const key = context.node.attrs.key;
  return (
    '<p class="field-name">' +
    escape(labelFields[key]) +
    '</p><p>Valore dal lavoro. Non modificabile come testo.</p>' +
    (fieldEditors[key]?.render(context) ?? '')
  );
}
export function bindAutomaticFieldProperties(context) {
  fieldEditors[context.node.attrs.key]?.bind(context);
}
