import test from 'node:test';
import assert from 'node:assert/strict';
import { equalWidths, validWidths, setColumnWidth, resizePair, normalizeLayoutJSON } from './layout-model.js';

test('column edits keep 100% and preserve minimum widths, including rounding', () => {
  for (let count = 1; count <= 4; count++) {
    let widths = equalWidths(count);
    assert.ok(validWidths(widths, count));
    for (let i = 0; i < count; i++) for (const wanted of [0, 8, 17.7, 33.3, 60, 99, 100]) {
      widths = setColumnWidth(widths, i, wanted);
      assert.ok(validWidths(widths, count), JSON.stringify(widths));
    }
  }
  assert.deepEqual(setColumnWidth([50, 50], 0, 60), [60, 40]);
  assert.equal(validWidths([60, 50], 2), false);
  assert.equal(validWidths([NaN, 50], 2), false);
  assert.throws(() => setColumnWidth([50, 50], 0, NaN));
});
test('a divider changes only its adjacent pair and stops at the minimum', () => {
  assert.deepEqual(resizePair([25, 35, 40], 0, 5), [30, 30, 40]);
  assert.deepEqual(resizePair([25, 35, 40], 1, -100), [25, 8, 67]);
  assert.deepEqual(resizePair([50, 50], 0, 100), [92, 8]);
});
test('old nested layouts and engineering flags upgrade without changing the source', () => {
  const old = { type: 'doc', content: [{ type: 'tonColumns', content: [
    { type: 'tonColumn', content: [{ type: 'tonBlock', attrs: { kind: 'weighing', engineering: true } }] },
    { type: 'tonColumn', content: [{ type: 'tonColumns', content: [
      { type: 'tonColumn', content: [{ type: 'paragraph' }] }, { type: 'tonColumn', content: [{ type: 'paragraph' }] },
    ] }] },
  ] }] };
  const before = JSON.stringify(old), next = normalizeLayoutJSON(old);
  assert.equal(JSON.stringify(old), before);
  assert.deepEqual(next.content[0].attrs, { widths: [50, 50], gap: 4.5 });
  assert.deepEqual(next.content[0].content[0].content[0].attrs, { kind: 'weighing', showPpm: true, showCoefficient: true });
  assert.deepEqual(next.content[0].content[1].content[0].attrs.widths, [50, 50]);
  assert.deepEqual(normalizeLayoutJSON(next), next);
});
