import test from 'node:test';
import assert from 'node:assert/strict';
import { Schema } from '@tiptap/pm/model';
import { EditorState, NodeSelection } from '@tiptap/pm/state';
import { history, undo } from '@tiptap/pm/history';
import {
  CanvasMultiSelection,
  groupCanvasSelection,
  ungroupCanvasSelection,
  selectCanvasObjects,
  moveCanvasSelection,
  deleteCanvasSelection,
} from './canvas-selection.js';
import { groupDelta, insideSelection, selectionRect } from './selection-geometry.js';
const schema = new Schema({
  nodes: {
    doc: { content: 'canvasObject*' },
    text: {},
    paragraph: { content: 'text*' },
    canvasObject: {
      content: 'paragraph+',
      attrs: {
        id: {},
        groupId: { default: '' },
        x: { default: 10 },
        y: { default: 20 },
        width: { default: 20 },
        height: { default: 10 },
        page: { default: 0 },
      },
    },
  },
});
function fixture() {
  const doc = schema.node(
    'doc',
    null,
    ['a', 'b', 'c'].map((id, i) =>
      schema.node(
        'canvasObject',
        { id, x: 10 + i * 50 },
        schema.node('paragraph', null, schema.text(id)),
      ),
    ),
  );
  let state = EditorState.create({ doc, plugins: [history()] });
  const editor = {
    get state() {
      return state;
    },
    isEditable: true,
    view: {
      dispatch(tr) {
        state = state.apply(tr);
      },
    },
  };
  state = state.apply(state.tr.setSelection(selectCanvasObjects(doc, ['a', 'c'])));
  return editor;
}
test('rubber band contains complete objects in both directions and across pages', () => {
  const rect = selectionRect({ x: 100, y: 100 }, { x: 10, y: 20 });
  assert.deepEqual(rect, { x: 10, y: 20, width: 90, height: 80 });
  assert.equal(insideSelection(rect, { x: 10, y: 20, width: 90, height: 80, page: 0 }, 150), true);
  assert.equal(insideSelection(rect, { x: 0, y: 20, width: 20, height: 10, page: 0 }, 150), false);
  assert.equal(
    insideSelection(
      { x: 0, y: 170, width: 100, height: 40 },
      { x: 10, y: 5, width: 20, height: 20, page: 1 },
      150,
    ),
    true,
  );
});
test('shared movement preserves distances and clamps the whole group, including fractional positions', () => {
  const objects = [
    { x: 10.5, y: 20, width: 20, height: 10 },
    { x: 100.5, y: 50, width: 20, height: 10 },
  ];
  assert.deepEqual(groupDelta(objects, 200, 200, { width: 130, height: 70 }), {
    x: 9.5,
    y: 10,
  });
  assert.deepEqual(groupDelta(objects, -200, -200, { width: 130, height: 70 }), {
    x: -10.5,
    y: -20,
  });
});
test('discontiguous selections copy and delete only the selected nodes, and undo restores them', () => {
  const e = fixture(),
    selection = e.state.selection;
  assert.ok(selection instanceof CanvasMultiSelection);
  assert.deepEqual(
    selection.content().content.content.map((n) => n.attrs.id),
    ['a', 'c'],
  );
  assert.deepEqual(selection.toJSON(), {
    type: 'canvasMulti',
    ids: ['a', 'c'],
  });
  assert.equal(selectCanvasObjects(e.state.doc, ['b']) instanceof NodeSelection, true);
  const saved = selection.getBookmark();
  deleteCanvasSelection(e);
  assert.equal(e.state.doc.childCount, 1);
  assert.equal(e.state.doc.firstChild.attrs.id, 'b');
  assert.equal(
    undo(e.state, (tr) => e.view.dispatch(tr)),
    true,
  );
  assert.deepEqual(saved.resolve(e.state.doc).ids, ['a', 'c']);
});
test('moving a selection is one undo step and preserves unselected objects', () => {
  const e = fixture();
  moveCanvasSelection(e, 5, 3, { format: 'custom', width: 100, height: 65 });
  assert.equal(e.state.doc.child(0).attrs.x, 15);
  assert.equal(e.state.doc.child(1).attrs.x, 60);
  assert.equal(e.state.doc.child(2).attrs.x, 115);
  assert.equal(e.state.selection.ids.length, 2);
  assert.equal(
    undo(e.state, (tr) => e.view.dispatch(tr)),
    true,
  );
  assert.equal(e.state.doc.child(0).attrs.x, 10);
  assert.equal(e.state.doc.child(2).attrs.x, 110);
});

test('persistent groups survive JSON reload, expand single clicks and ungroup without changing geometry', () => {
  const e = fixture(),
    before = e.state.doc.toJSON();
  groupCanvasSelection(e);
  const id = e.state.doc.child(0).attrs.groupId;
  assert.match(id, /^grp-/);
  assert.equal(e.state.doc.child(2).attrs.groupId, id);
  assert.equal(e.state.doc.child(1).attrs.groupId, '');
  const saved = schema.nodeFromJSON(e.state.doc.toJSON());
  assert.deepEqual(selectCanvasObjects(saved, ['a']).ids, ['a', 'c']);
  moveCanvasSelection(e, 5, 3, { format: 'custom', width: 100, height: 65 });
  assert.equal(e.state.doc.child(2).attrs.x, 115);
  undo(e.state, (tr) => e.view.dispatch(tr));
  assert.equal(e.state.doc.child(2).attrs.x, 110);
  ungroupCanvasSelection(e);
  assert.equal(e.state.doc.child(0).attrs.groupId, '');
  assert.ok(selectCanvasObjects(e.state.doc, ['a']) instanceof NodeSelection);
  undo(e.state, (tr) => e.view.dispatch(tr));
  assert.deepEqual(selectCanvasObjects(e.state.doc, ['c']).ids, ['a', 'c']);
  undo(e.state, (tr) => e.view.dispatch(tr));
  assert.deepEqual(e.state.doc.toJSON(), before);
});
test('grouping across pages is rejected without mutating the document', () => {
  const e = fixture();
  e.view.dispatch(
    e.state.tr.setNodeMarkup(
      e.state.doc.child(0).nodeSize + e.state.doc.child(1).nodeSize,
      undefined,
      { ...e.state.doc.child(2).attrs, page: 1 },
    ),
  );
  const before = e.state.doc.toJSON();
  assert.throws(() => groupCanvasSelection(e), /stessa pagina/);
  assert.deepEqual(e.state.doc.toJSON(), before);
});
