import test from 'node:test';
import assert from 'node:assert/strict';
import { formatDate, validateDateFormat, validateFieldDateFormat } from './date-format.js';
import {
  labelValueText,
  LabelValue,
  LabelField,
  setLabelData,
  gs1Text,
  upgradeLabelObjects,
} from './label-elements.js';

test('requested masks substitute complete tokens and preserve fixed digits and separators', () => {
  for (const [mask, expected] of [
    ['dd/mm/yyyy', '22/09/2026'],
    ['mm/yyyy', '09/2026'],
    ['mm/yy', '09/26'],
    ['ddmmyyyy', '22092026'],
    ['yymmdd', '260922'],
    ['yymm00', '260900'],
    ['yyyy-mm-dd', '2026-09-22'],
    ['DD.MM.YYYY', '22.09.2026'],
    ['00/mm/yy', '00/09/26'],
    ['dd dd', '22 22'],
    ['', '22/09/2026'],
    ['0000', '0000'],
  ])
    assert.equal(formatDate('22/09/2026', mask), expected, mask);
});
test('formatting is strict and independent of timezone or JavaScript date normalization', () => {
  assert.equal(formatDate('29/02/2024', 'yymmdd'), '240229');
  assert.equal(formatDate('01/01/2001', 'ddmmyyyy'), '01012001');
  assert.equal(formatDate('29/02/2000', 'dd/mm/yy'), '29/02/00');
  assert.equal(formatDate('', 'mm/yy'), '');
  for (const value of [
    '29/02/2026',
    '31/04/2026',
    '00/09/2026',
    '22/00/2026',
    '22/13/2026',
    '01/01/0000',
    '29/02/1900',
    '2026-09-22',
    '2/9/2026',
  ])
    assert.throws(() => formatDate(value, 'mm/yy'), /Data non valida/);
  for (const mask of [
    'd/m/yyyy',
    'yyy',
    'mmm',
    'yyyyy',
    'dd/MM/YYYY HH:mm',
    null,
    123,
    'y'.repeat(65),
    '<script>',
  ])
    assert.throws(() => validateDateFormat(mask), /Formato data non valido/);
  assert.throws(() => validateFieldDateFormat('lot', 'yymm00'));
});
test('production and expiry masks are independent, persist as node attributes and do not change GS1', () => {
  const data = {
    productionDate: '22/09/2026',
    expiryDate: '30/09/2031',
    productionDateGs1: '260922',
    expiryDateGs1: '310930',
    gtin: '09506000134352',
    lot: '260922',
  };
  setLabelData(data);
  const gs1 = {
    gtin: '{{gtin}}',
    lot: '{{lot}}',
    productionDate: '{{productionDateGs1}}',
    expiryDate: '{{expiryDateGs1}}',
  };
  const before = gs1Text(gs1),
    production = { key: 'productionDate', dateFormat: 'yymm00' },
    expiry = { key: 'expiryDate', dateFormat: 'mm/yy' };
  assert.equal(labelValueText(production), '260900');
  assert.equal(labelValueText(expiry), '09/31');
  assert.equal(labelValueText({ key: 'productionDate' }), '22/09/2026');
  assert.equal(LabelValue.config.renderHTML({ node: { attrs: production } })[2], '260900');
  assert.equal(LabelField.config.renderHTML({ node: { attrs: expiry } })[2], '09/31');
  assert.equal(gs1Text(gs1), before);
  assert.equal(data.productionDate, '22/09/2026');
  const old = {
    type: 'doc',
    content: [
      {
        type: 'canvasObject',
        attrs: { kind: 'text' },
        content: [{ type: 'paragraph', content: [{ type: 'labelField', attrs: expiry }] }],
      },
    ],
  };
  assert.deepEqual(upgradeLabelObjects(old).content[0].content[0].attrs, expiry);
  assert.equal(
    LabelValue.config
      .parseHTML()[0]
      .getAttrs({ dataset: { labelValue: 'expiryDate', dateFormat: 'yymm00' } }).dateFormat,
    'yymm00',
  );
  setLabelData(null);
});
