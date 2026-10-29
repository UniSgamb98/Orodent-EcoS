import test from 'node:test';
import assert from 'node:assert/strict';
import { optionDefaults, validateBlockOptions, blockColumns, defaultTableWidths, validTableWidths, visibleFormats, visiblePowders, powderLabel, numericValue, blockFontSizes } from './block-options.js';
import { setColumnWidth } from './layout-model.js';

test('optional content changes presentation without mutating production fixtures', () => {
  const formats = [{ code: 'B', nominal: '18', count: 2 }, { code: 'A', nominal: '14', count: 1 }, { code: 'C', nominal: '20', count: 0 }];
  const before = JSON.stringify(formats);
  assert.deepEqual(visibleFormats(formats, { hideZeroFormats: true, formatOrder: 'heightAsc' }).map(f => f.code), ['A', 'B']);
  assert.deepEqual(visibleFormats(formats, { formatOrder: 'heightDesc' }).map(f => f.code), ['C', 'B', 'A']);
  assert.equal(JSON.stringify(formats), before);
  const powders = [{ code: 'A', name: 'Bianca', weight: '1.234,5' }, { code: 'B', weight: '0,0' }, { code: 'C', weight: '0,1' }];
  assert.deepEqual(visiblePowders(powders, { hideZeroPowders: true }), [powders[0], powders[2]]);
  assert.equal(numericValue('1.234,5'), 1234.5);
  assert.equal(powderLabel(powders[0], 'codeName'), 'A · Bianca');
  assert.equal(powderLabel(powders[1], 'name'), 'B');
});
test('table widths follow visible columns and stay at 100% after edits', () => {
  for (const kind of ['weighing', 'mixtures']) for (const show of [false, true]) {
    const attrs = { ...optionDefaults, kind, showPpm: show, showPercent: show };
    const columns = blockColumns(attrs), widths = defaultTableWidths(attrs);
    assert.ok(validTableWidths(widths, columns.length));
    for (let i = 0; i < columns.length; i++) for (const value of [3, 30, 99]) {
      const edited = setColumnWidth(widths, i, value);
      assert.ok(validTableWidths(edited, columns.length));
      validateBlockOptions({ ...attrs, tableWidths: edited });
    }
  }
  assert.throws(() => validateBlockOptions({ ...optionDefaults, kind: 'weighing', showPpm: true, tableWidths: [25, 25, 25, 25] }));
});
test('block styles validate imports and include requested small point sizes', () => {
  validateBlockOptions({ ...optionDefaults, kind: 'mixtures' });
  for (const size of ['3pt', '5pt', '7pt']) assert.ok(blockFontSizes.includes(size));
  for (const invalid of [{ headerColor: 'url(https://example.com)' }, { pageBreakBefore: 'false' }, { powderLabel: '<b>' }, { tableWidths: [40, 40, 40] }])
    assert.throws(() => validateBlockOptions({ ...optionDefaults, kind: 'mixtures', ...invalid }));
});
