import test from 'node:test';
import assert from 'node:assert/strict';
import bwipjs from 'bwip-js';
import zxing from '@zxing/library';
import Decoder from '@zxing/library/cjs/core/datamatrix/decoder/Decoder.js';
import { gs1Text, setLabelData, matrixOptions } from './label-elements.js';
import { validatePage, paperSize } from './page-settings.js';

const fields={gtin:'{{gtin}}',lot:'{{lot}}',productionDate:'{{productionDateGs1}}',expiryDate:'{{expiryDateGs1}}',size:20};
const data={gtin:'09506000134352',lot:'260922',productionDateGs1:'260922',expiryDateGs1:'310930'};
test('an independent DataMatrix decoder sees FNC1 and the separator after variable AI 10',()=>{
  setLabelData(data);
  const [raw]=bwipjs.raw(matrixOptions(fields));
  const bits=new zxing.BitMatrix(raw.pixx,raw.pixy);
  for(let y=0;y<raw.pixy;y++)for(let x=0;x<raw.pixx;x++)if(raw.pixs[y*raw.pixx+x])bits.set(x,y);
  assert.equal(new Decoder.default().decode(bits).getText(),'\x1d010950600013435210260922\x1d1126092217310930');
});
test('GS1 rejects missing data, bad check digits, invalid dates and overlong lots',()=>{
  for(const change of [{gtin:''},{gtin:'09506000134353'},{lot:'x'.repeat(21)},{productionDateGs1:'260230'},{expiryDateGs1:'311300'}]) {
    setLabelData({...data,...change});assert.throws(()=>gs1Text(fields));
  }
  setLabelData({...data,lot:'BATCH-A/1'});
  assert.equal(gs1Text({...fields,productionDate:'',expiryDate:''}),'(01)09506000134352(10)BATCH-A/1');
});
test('custom paper preserves physical dimensions and rejects impossible margins',()=>{
  assert.deepEqual(paperSize(validatePage({format:'custom',width:100,height:65,margin:3,orientation:'portrait'})),{width:100,height:65});
  assert.deepEqual(paperSize(validatePage({format:'a4',width:100,height:65,margin:15,orientation:'landscape'})),{width:297,height:210});
  assert.throws(()=>validatePage({format:'custom',width:100,height:65,margin:33,orientation:'portrait'}));
});
