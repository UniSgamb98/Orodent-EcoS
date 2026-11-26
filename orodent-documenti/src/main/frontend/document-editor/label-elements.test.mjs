import test from 'node:test';
import assert from 'node:assert/strict';
import bwipjs from 'bwip-js';
import zxing from '@zxing/library';
import Decoder from '@zxing/library/cjs/core/datamatrix/decoder/Decoder.js';
import {
  gs1Text,
  setLabelData,
  matrixOptions,
  upgradeLabelObjects,
  LabelValue,
  resolveLabel,
} from './label-elements.js';
import { validatePage, paperSize } from './page-settings.js';

const fields = {
  gtin: '{{gtin}}',
  lot: '{{lot}}',
  productionDate: '{{productionDateGs1}}',
  expiryDate: '{{expiryDateGs1}}',
  size: 20,
};
const data = {
  gtin: '09506000134352',
  lot: '260922',
  productionDateGs1: '260922',
  expiryDateGs1: '310930',
};
test('an independent DataMatrix decoder sees FNC1 and the separator after variable AI 10', () => {
  setLabelData(data);
  const [raw] = bwipjs.raw(matrixOptions(fields));
  const bits = new zxing.BitMatrix(raw.pixx, raw.pixy);
  for (let y = 0; y < raw.pixy; y++)
    for (let x = 0; x < raw.pixx; x++) if (raw.pixs[y * raw.pixx + x]) bits.set(x, y);
  assert.equal(
    new Decoder.default().decode(bits).getText(),
    '\x1d010950600013435210260922\x1d1126092217310930',
  );
});
test('GS1 rejects missing data, bad check digits, invalid dates and overlong lots', () => {
  for (const change of [
    { gtin: '' },
    { gtin: '09506000134353' },
    { lot: 'x'.repeat(21) },
    { productionDateGs1: '260230' },
    { expiryDateGs1: '311300' },
  ]) {
    setLabelData({ ...data, ...change });
    assert.throws(() => gs1Text(fields));
  }
  setLabelData({ ...data, lot: 'BATCH-A/1' });
  assert.equal(
    gs1Text({ ...fields, productionDate: '', expiryDate: '' }),
    '(01)09506000134352(10)BATCH-A/1',
  );
});
test('custom paper preserves physical dimensions and rejects impossible margins', () => {
  assert.deepEqual(
    paperSize(
      validatePage({
        format: 'custom',
        width: 100,
        height: 65,
        margin: 3,
        orientation: 'portrait',
      }),
    ),
    { width: 100, height: 65 },
  );
  assert.deepEqual(
    paperSize(
      validatePage({ format: 'a4', width: 100, height: 65, margin: 15, orientation: 'landscape' }),
    ),
    { width: 297, height: 210 },
  );
  assert.throws(() =>
    validatePage({ format: 'custom', width: 100, height: 65, margin: 33, orientation: 'portrait' }),
  );
});

test('standalone automatic values are leaves and old field-only boxes preserve formatting', () => {
  assert.equal(LabelValue.config.atom, true);
  assert.equal(LabelValue.config.content, undefined);
  const old = {
    type: 'canvasObject',
    attrs: { kind: 'text', lineHeight: 1.2, x: 12, width: 140 },
    content: [
      {
        type: 'paragraph',
        attrs: { textAlign: 'right' },
        content: [
          {
            type: 'labelField',
            attrs: { key: 'lot' },
            marks: [
              { type: 'bold' },
              { type: 'textStyle', attrs: { fontSize: '14pt', color: '#ff0000' } },
            ],
          },
        ],
      },
    ],
  };
  const mixed = {
    type: 'canvasObject',
    attrs: { kind: 'text' },
    content: [
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: 'LOT ' },
          { type: 'labelField', attrs: { key: 'lot' } },
        ],
      },
    ],
  };
  const upgraded = upgradeLabelObjects({ type: 'doc', content: [old, mixed] });
  assert.equal(upgraded.content[0].attrs.kind, 'field');
  assert.equal(upgraded.content[0].attrs.x, 12);
  assert.equal(upgraded.content[0].attrs.fontSize, '14pt');
  assert.equal(upgraded.content[0].attrs.bold, true);
  assert.deepEqual(upgraded.content[0].content, [{ type: 'labelValue', attrs: { key: 'lot' } }]);
  assert.equal(upgraded.content[1], mixed);
  assert.equal(old.attrs.kind, 'text');
  assert.equal(upgradeLabelObjects(upgraded), null);
  setLabelData({ lot: 'LOTTO-REALE' });
  assert.equal(resolveLabel('{{lot}}'), 'LOTTO-REALE');
  assert.equal(LabelValue.config.renderHTML({ node: { attrs: { key: 'lot' } } })[2], 'LOTTO-REALE');
});

test('tight GS1 code bounds omit internal padding without changing the encoded modules', async () => {
  const { matrixDimensions } = await import('./label-elements.js');
  const { normalizeMatrixObjects } = await import('./matrix-layout.js');
  setLabelData(data);
  const tight = { ...fields, tight: true },
    old = { ...fields, tight: false };
  assert.deepEqual(bwipjs.raw(matrixOptions(old)), bwipjs.raw(matrixOptions(tight)));
  const before = matrixDimensions(old),
    after = matrixDimensions(tight);
  assert.equal(before.width - after.width, 50);
  assert.equal(before.height - after.height, 50);
  const object = {
    type: 'canvasObject',
    attrs: { kind: 'matrix', x: 10, y: 20, width: 88, height: 90 },
    content: [{ type: 'gs1Matrix', attrs: old }],
  };
  const snapshot = structuredClone(object),
    doc = { type: 'doc', content: [object] };
  const adjusted = normalizeMatrixObjects(doc),
    scale = 88 / before.width;
  assert.deepEqual(object, snapshot);
  assert.equal(adjusted.content[0].attrs.x, 10 + 25 * scale);
  assert.equal(adjusted.content[0].attrs.y, 21 + 25 * scale);
  assert.equal(adjusted.content[0].attrs.width, after.width * scale);
  assert.equal(adjusted.content[0].attrs.height, after.height * scale);
  assert.equal(normalizeMatrixObjects(adjusted), null);
});
