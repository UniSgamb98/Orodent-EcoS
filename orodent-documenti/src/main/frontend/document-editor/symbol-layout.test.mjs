import test from 'node:test';
import assert from 'node:assert/strict';
import { inflateSync } from 'node:zlib';
import { readFileSync } from 'node:fs';
import symbols from './symbols.json' with { type: 'json' };
import { symbolSize, normalizeSymbolObject, normalizeSymbolObjects } from './symbol-layout.js';

// Independent PNG decoder for the versioned, non-interlaced RGBA artwork.
function rgba(src) {
  const png = Buffer.from(src.split(',')[1], 'base64'),
    chunks = [];
  const width = png.readUInt32BE(16),
    height = png.readUInt32BE(20);
  assert.equal(png[24], 8);
  assert.equal(png[25], 6);
  assert.equal(png[28], 0);
  for (let offset = 8; offset < png.length; ) {
    const length = png.readUInt32BE(offset);
    if (png.toString('ascii', offset + 4, offset + 8) === 'IDAT')
      chunks.push(png.subarray(offset + 8, offset + 8 + length));
    offset += length + 12;
  }
  const raw = inflateSync(Buffer.concat(chunks)),
    stride = width * 4;
  const pixels = Buffer.alloc(stride * height);
  let offset = 0;
  for (let y = 0; y < height; y++) {
    const filter = raw[offset++];
    assert.ok(filter <= 4);
    for (let x = 0; x < stride; x++) {
      const pos = y * stride + x;
      const a = x >= 4 ? pixels[pos - 4] : 0;
      const b = y ? pixels[pos - stride] : 0;
      const c = y && x >= 4 ? pixels[pos - stride - 4] : 0;
      const p = a + b - c,
        pa = Math.abs(p - a),
        pb = Math.abs(p - b),
        pc = Math.abs(p - c);
      const paeth = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      pixels[pos] = raw[offset++] + [0, a, b, Math.floor((a + b) / 2), paeth][filter];
    }
  }
  return { width, height, pixels };
}

test('all library symbols have tight alpha bounds and retain every original visible pixel', () => {
  for (const symbol of symbols) {
    const current = rgba(symbol.src),
      original = rgba(symbol.legacy.src);
    assert.equal(current.width, symbol.width);
    assert.equal(current.height, symbol.height);
    assert.deepEqual(
      readFileSync(new URL(`symbols/${symbol.key}.png`, import.meta.url)),
      Buffer.from(symbol.src.split(',')[1], 'base64'),
    );
    const { width, height, pixels } = current;
    const alpha = (x, y) => pixels[(y * width + x) * 4 + 3];
    for (const edge of [
      Array.from({ length: width }, (_, x) => alpha(x, 0)),
      Array.from({ length: width }, (_, x) => alpha(x, height - 1)),
      Array.from({ length: height }, (_, y) => alpha(0, y)),
      Array.from({ length: height }, (_, y) => alpha(width - 1, y)),
    ])
      assert.ok(
        edge.some((a) => a > 0),
        symbol.key + ': transparent outer margin',
      );
    const b = symbol.legacy.bounds;
    for (let y = 0; y < height; y++)
      for (let x = 0; x < width; x++) {
        const from = ((y + b.y) * original.width + x + b.x) * 4,
          to = (y * width + x) * 4;
        // RGB of fully transparent pixels has no visual meaning and can be normalized by PNG tools.
        assert.equal(pixels[to + 3], original.pixels[from + 3], symbol.key);
        if (pixels[to + 3])
          assert.deepEqual(
            pixels.subarray(to, to + 3),
            original.pixels.subarray(from, from + 3),
            symbol.key,
          );
      }
  }
});

function object(src, width = 25, height = 25) {
  return {
    type: 'canvasObject',
    attrs: { kind: 'image', x: 10.25, y: 20.75, width, height, page: 1, z: 4 },
    content: [{ type: 'image', attrs: { src, alt: 'REF' } }],
  };
}
test('old square symbol boxes become tight without moving or scaling the visible artwork', () => {
  const symbol = symbols.find((s) => s.key === 'cataloguenumber');
  const old = object(symbol.legacy.src),
    snapshot = structuredClone(old);
  const adjusted = normalizeSymbolObject(old);
  assert.deepEqual(old, snapshot);
  assert.deepEqual(adjusted.attrs, {
    ...old.attrs,
    x: 10.25 + 31 / 16,
    y: 20.75 + 93 / 16,
    width: 338 / 16,
    height: 212 / 16,
  });
  assert.equal(adjusted.content[0].attrs.src, symbol.src);
  assert.equal(normalizeSymbolObject(adjusted), null);
  const document = { type: 'doc', content: [old] };
  assert.equal(normalizeSymbolObjects(normalizeSymbolObjects(document)), null);
});
test('contain letterboxing is removed for cropped symbols, but uploads and GS1 are untouched', () => {
  const symbol = symbols.find((s) => s.key === 'modelnumber');
  const adjusted = normalizeSymbolObject(object(symbol.src, 80, 80));
  assert.equal(adjusted.attrs.width, 80);
  assert.equal(adjusted.attrs.height, (80 * symbol.height) / symbol.width);
  assert.equal(adjusted.attrs.y, 20.75 + (80 - adjusted.attrs.height) / 2);
  assert.equal(normalizeSymbolObject(object('data:image/png;base64,custom-upload')), null);
  const matrix = object(symbol.legacy.src);
  matrix.attrs.kind = 'matrix';
  assert.equal(normalizeSymbolObject(matrix), null);
});
test('new symbols use their natural ratio, with an 8mm longest side', () => {
  for (const symbol of symbols) {
    const size = symbolSize(symbol, (96 / 25.4) * 8);
    assert.ok(Math.abs(size.width / size.height - symbol.width / symbol.height) < 1e-12);
    assert.ok(Math.abs(Math.max(size.width, size.height) - (96 / 25.4) * 8) < 1e-12);
    assert.ok(Math.min(size.width, size.height) >= 8);
    assert.equal(normalizeSymbolObject(object(symbol.src, size.width, size.height)), null);
  }
});
