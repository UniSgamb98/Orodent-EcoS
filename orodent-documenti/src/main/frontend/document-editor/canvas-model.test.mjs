import test from 'node:test';
import assert from 'node:assert/strict';
import { pagePixels, moveGeometry, resizeGeometry, validateObject, px } from './canvas-model.js';
const page = { format: 'custom', width: 102, height: 38, count: 1 };
const object = {
  id: 'obj-1',
  kind: 'text',
  x: 10,
  y: 20,
  width: 180,
  height: 42,
  page: 0,
  z: 0,
  fontFamily: 'Arial',
  fontSize: '11pt',
  lineHeight: 1.2,
  color: '#000000',
  textAlign: 'left',
  bold: false,
  italic: false,
  underline: false,
  keepRatio: false,
};
test('sheet dimensions retain the specified aspect ratio independent of screen zoom', () => {
  const size = pagePixels(page);
  assert.equal(size.width / size.height, 102 / 38);
  assert.equal(size.width, px(102));
});
test('one-pixel movement and resizing use page coordinates and keep objects on the page', () => {
  const size = pagePixels(page),
    moved = moveGeometry(object, 1, -1, size);
  assert.equal(moved.x, 11);
  assert.equal(moved.y, 19);
  assert.equal(moveGeometry({ ...object, x: 10.25 }, 1, 0, size).x, 11.25);
  const clipped = moveGeometry(object, 1000, 1000, size);
  assert.ok(clipped.x + clipped.width <= size.width);
  assert.ok(clipped.y + clipped.height <= size.height);
  const resized = resizeGeometry(object, 20, 10, 'se', size);
  assert.equal(resized.width, 200);
  assert.equal(resized.height, 52);
  const fromLeft = resizeGeometry(object, -100, 0, 'w', size);
  assert.equal(fromLeft.x, 0);
  assert.equal(fromLeft.x + fromLeft.width, object.x + object.width);
});
test('image resize keeps proportions and validates bounds and styles', () => {
  const size = pagePixels(page),
    image = { ...object, kind: 'image', width: 50, height: 50, keepRatio: true };
  const resized = resizeGeometry(image, 25, 10, 'se', size);
  assert.equal(resized.width, resized.height);
  assert.equal(resized.width, 75);
  validateObject(object, page);
  for (const patch of [
    { x: 500 },
    { width: 2 },
    { page: 1 },
    { fontSize: '12pt;position:fixed' },
    { z: -1 },
  ])
    assert.throws(() => validateObject({ ...object, ...patch }, page));
});
