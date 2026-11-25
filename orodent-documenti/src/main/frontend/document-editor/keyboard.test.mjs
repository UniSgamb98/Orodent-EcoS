import test from 'node:test';
import assert from 'node:assert/strict';
import { editorKey } from './keyboard.js';
test('normalizes the empty key events emitted by native JavaFX WebKit', () => {
  assert.equal(editorKey({ key: '', keyIdentifier: 'Right', keyCode: 39 }), 'ArrowRight');
  assert.equal(editorKey({ key: '', keyIdentifier: 'U+001B', keyCode: 27 }), 'Escape');
  assert.equal(editorKey({ key: '', keyIdentifier: 'Enter', keyCode: 13 }), 'Enter');
  assert.equal(editorKey({ key: '', keyIdentifier: 'U+0041', keyCode: 65 }), 'a');
  assert.equal(editorKey({ key: 'ArrowDown', keyCode: 40 }), 'ArrowDown');
});
