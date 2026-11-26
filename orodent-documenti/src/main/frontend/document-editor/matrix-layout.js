import { matrixDimensions } from './label-elements.js';

// Only editable old canvas objects upgrade. Passive rendering retains the old
// padding. New bounds coincide with the old visible code, not its white inset.
export function normalizeMatrixObjects(doc) {
  let changed = false;
  const content = (doc.content ?? []).map((object) => {
    if (
      object.type !== 'canvasObject' ||
      object.attrs?.kind !== 'matrix' ||
      object.content?.length !== 1
    )
      return object;
    const matrix = object.content[0];
    if (matrix.type !== 'gs1Matrix' || matrix.attrs?.tight) return object;
    try {
      const a = object.attrs,
        before = matrixDimensions(matrix.attrs),
        after = matrixDimensions({ ...matrix.attrs, tight: true });
      const scale = Math.min(a.width / before.width, a.height / before.height);
      const attrs = {
        ...a,
        x: a.x + (a.width - before.width * scale) / 2 + ((before.width - after.width) * scale) / 2,
        y:
          a.y +
          (a.height - before.height * scale) / 2 +
          ((before.height - after.height) * scale) / 2,
        width: after.width * scale,
        height: after.height * scale,
        keepRatio: true,
      };
      if (attrs.width < 8 || attrs.height < 8) return object;
      changed = true;
      return {
        ...object,
        attrs,
        content: [{ ...matrix, attrs: { ...matrix.attrs, tight: true } }],
      };
    } catch {
      return object;
    } // Incomplete GS1 data remain editable; no invented values.
  });
  return changed ? { ...doc, content } : null;
}
