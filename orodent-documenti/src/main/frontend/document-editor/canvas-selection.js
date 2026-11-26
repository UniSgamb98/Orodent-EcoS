import { expandGroupIds } from './canvas-groups.js';
import { groupBounds } from './alignment.js';
import { Extension } from '@tiptap/core';
import { Selection, SelectionRange, NodeSelection, Plugin } from '@tiptap/pm/state';
import { GapCursor } from '@tiptap/pm/gapcursor';
import { Fragment, Slice, DOMSerializer } from '@tiptap/pm/model';
import { closeHistory } from '@tiptap/pm/history';
import { Decoration, DecorationSet } from '@tiptap/pm/view';
import { pagePixels } from './canvas-model.js';
import { editorKey } from './keyboard.js';
import { selectionRect, insideSelection, groupDelta } from './selection-geometry.js';

export function canvasObjects(doc) {
  const result = [];
  doc.forEach((node, pos) => {
    if (node.type.name === 'canvasObject') result.push({ node, pos });
  });
  return result;
}
function matching(doc, ids) {
  const set = new Set(ids);
  return canvasObjects(doc).filter((o) => set.has(o.node.attrs.id));
}
export function selectCanvasObjects(doc, ids) {
  const objects = matching(
    doc,
    expandGroupIds(
      canvasObjects(doc).map((o) => o.node.attrs),
      ids,
    ),
  );
  if (!objects.length) return new GapCursor(doc.resolve(0));
  if (objects.length === 1) return NodeSelection.create(doc, objects[0].pos);
  return new CanvasMultiSelection(doc, objects);
}
export function selectedCanvasObjects(state) {
  const s = state.selection;
  if (s instanceof CanvasMultiSelection) return matching(state.doc, s.ids);
  if (s instanceof NodeSelection && s.node.type.name === 'canvasObject')
    return [{ node: s.node, pos: s.from }];
  for (let depth = s.$from.depth; depth > 0; depth--)
    if (s.$from.node(depth).type.name === 'canvasObject')
      return [{ node: s.$from.node(depth), pos: s.$from.before(depth) }];
  return [];
}
let nextGroup = 0;
export function groupCanvasSelection(editor) {
  const objects = selectedCanvasObjects(editor.state);
  if (!editor.isEditable || objects.length < 2) return false;
  if (new Set(objects.map((o) => o.node.attrs.page)).size !== 1)
    throw Error('Puoi raggruppare solo elementi della stessa pagina.');
  let groupId;
  do {
    groupId = 'grp-' + Date.now().toString(36) + '-' + ++nextGroup;
  } while (canvasObjects(editor.state.doc).some((o) => o.node.attrs.groupId === groupId));
  return setGroups(editor, objects, groupId);
}
export function ungroupCanvasSelection(editor) {
  const objects = selectedCanvasObjects(editor.state);
  if (!editor.isEditable || !objects.some((o) => o.node.attrs.groupId)) return false;
  return setGroups(editor, objects, '');
}
function setGroups(editor, objects, groupId) {
  let tr = closeHistory(editor.state.tr);
  for (const o of objects) tr = tr.setNodeMarkup(o.pos, undefined, { ...o.node.attrs, groupId });
  tr.setSelection(
    selectCanvasObjects(
      tr.doc,
      objects.map((o) => o.node.attrs.id),
    ),
  );
  editor.view.dispatch(tr);
  editor.view.dispatch(closeHistory(editor.state.tr));
  return true;
}
class CanvasBookmark {
  constructor(ids) {
    this.ids = ids;
  }
  map() {
    return this;
  } // Stable object IDs survive edits and undo position mapping.
  resolve(doc) {
    return selectCanvasObjects(doc, this.ids);
  }
}
export class CanvasMultiSelection extends Selection {
  constructor(doc, objects) {
    const ranges = objects.map(
      (o) => new SelectionRange(doc.resolve(o.pos), doc.resolve(o.pos + o.node.nodeSize)),
    );
    super(ranges[0].$from, ranges[ranges.length - 1].$to, ranges);
    this.ids = objects.map((o) => o.node.attrs.id);
    this.visible = false;
  }
  map(doc) {
    return selectCanvasObjects(doc, this.ids);
  }
  eq(other) {
    return (
      other instanceof CanvasMultiSelection &&
      this.from === other.from &&
      this.to === other.to &&
      this.ids.join(',') === other.ids.join(',')
    );
  }
  content() {
    return new Slice(Fragment.fromArray(this.ranges.map((r) => r.$from.nodeAfter)), 0, 0);
  }
  toJSON() {
    return { type: 'canvasMulti', ids: this.ids };
  }
  static fromJSON(doc, json) {
    return selectCanvasObjects(doc, json.ids);
  }
  getBookmark() {
    return new CanvasBookmark(this.ids);
  }
}
Selection.jsonID('canvasMulti', CanvasMultiSelection);

export function moveCanvasSelection(editor, dx, dy, page, exact = false) {
  const selection = editor.state.selection;
  if (!(selection instanceof CanvasMultiSelection) || !editor.isEditable) return false;
  const objects = matching(editor.state.doc, selection.ids),
    delta = groupDelta(
      objects.map((o) => o.node.attrs),
      dx,
      dy,
      pagePixels(page),
      !exact,
    );
  if (!delta.x && !delta.y) return true;
  let tr = closeHistory(editor.state.tr);
  for (const o of objects)
    tr = tr.setNodeMarkup(o.pos, undefined, {
      ...o.node.attrs,
      x: o.node.attrs.x + delta.x,
      y: o.node.attrs.y + delta.y,
    });
  tr.setSelection(selectCanvasObjects(tr.doc, selection.ids));
  editor.view.dispatch(tr);
  editor.view.dispatch(closeHistory(editor.state.tr));
  return true;
}
export function deleteCanvasSelection(editor) {
  const selection = editor.state.selection;
  if (!(selection instanceof CanvasMultiSelection) || !editor.isEditable) return false;
  let tr = closeHistory(editor.state.tr);
  for (const o of matching(editor.state.doc, selection.ids).reverse())
    tr = tr.delete(o.pos, o.pos + o.node.nodeSize);
  tr.setSelection(new GapCursor(tr.doc.resolve(0)));
  editor.view.dispatch(tr);
  editor.view.dispatch(closeHistory(editor.state.tr));
  return true;
}
export const CanvasSelectionExtension = Extension.create({
  name: 'canvasSelection',
  addProseMirrorPlugins() {
    return [
      new Plugin({
        appendTransaction(transactions, oldState, state) {
          const objects = selectedCanvasObjects(state);
          const expanded = expandGroupIds(
            canvasObjects(state.doc).map((o) => o.node.attrs),
            objects.map((o) => o.node.attrs.id),
          );
          if (expanded.length > objects.length)
            return state.tr.setSelection(selectCanvasObjects(state.doc, expanded));
          return null;
        },
        props: {
          decorations(state) {
            if (!(state.selection instanceof CanvasMultiSelection)) return null;
            return DecorationSet.create(
              state.doc,
              matching(state.doc, state.selection.ids).map((o) =>
                Decoration.node(o.pos, o.pos + o.node.nodeSize, {
                  class: 'canvas-multi-selected',
                }),
              ),
            );
          },
          handleDOMEvents: {
            cut: (view, event) => {
              if (!(view.state.selection instanceof CanvasMultiSelection)) return false;
              event.preventDefault();
              if (!event.clipboardData || !this.editor.isEditable) return true;
              const div = document.createElement('div');
              div.append(
                DOMSerializer.fromSchema(view.state.schema).serializeFragment(
                  view.state.selection.content().content,
                ),
              );
              event.clipboardData.setData('text/html', div.innerHTML);
              event.clipboardData.setData('text/plain', div.textContent);
              deleteCanvasSelection(this.editor);
              return true;
            },
          },
        },
      }),
    ];
  },
});

export function mountCanvasSelection(editor, model) {
  const canvas = document.getElementById('canvas'),
    paper = document.getElementById('paper');
  const marquee = document.createElement('div');
  marquee.className = 'canvas-marquee';
  marquee.hidden = true;
  document.body.append(marquee);
  const groupOutline = document.createElement('div');
  groupOutline.className = 'canvas-group-outlines';
  groupOutline.hidden = true;
  paper.append(groupOutline);
  function paintGroupOutline(delta = { x: 0, y: 0 }) {
    const groups = new Map();
    for (const { node } of selectedCanvasObjects(editor.state)) {
      if (!node.attrs.groupId) continue;
      const members = groups.get(node.attrs.groupId) ?? [];
      members.push(node.attrs);
      groups.set(node.attrs.groupId, members);
    }
    groupOutline.replaceChildren();
    groupOutline.hidden = !groups.size;
    for (const [id, members] of groups) {
      const b = groupBounds(members),
        step = pagePixels(model.page()).height + 24;
      if (!b) continue;
      const outline = document.createElement('div');
      outline.className = 'canvas-group-outline';
      outline.dataset.groupId = id;
      outline.innerHTML = '<span>Gruppo</span>';
      Object.assign(outline.style, {
        left: b.x + delta.x + 'px',
        top: b.y + delta.y + b.page * step + 'px',
        width: b.width + 'px',
        height: b.height + 'px',
      });
      groupOutline.append(outline);
    }
  }
  let drag = null,
    closed = false;
  const point = (e) => {
    const r = paper.getBoundingClientRect(),
      z = model.zoomValue();
    return { x: (e.clientX - r.left) / z, y: (e.clientY - r.top) / z };
  };
  function selectedIds() {
    return selectedCanvasObjects(editor.state).map((o) => o.node.attrs.id);
  }
  function restorePreview() {
    for (const o of drag
      ? matching(
          editor.state.doc,
          drag.objects.map((item) => item.node.attrs.id),
        )
      : []) {
      const dom = editor.view.nodeDOM(o.pos);
      if (dom) {
        dom.style.left = o.node.attrs.x + 'px';
        dom.style.top = `calc(${o.node.attrs.y}px + ${o.node.attrs.page} * var(--page-step,0px))`;
      }
    }
  }
  function finish(commit) {
    if (!drag) return;
    const d = drag;
    model.alignment.clear();
    restorePreview();
    drag = null;
    paintGroupOutline();
    document.removeEventListener('pointermove', move);
    document.removeEventListener('pointerup', up);
    document.removeEventListener('pointercancel', cancel);
    document.removeEventListener('keydown', escape, true);
    canvas.removeEventListener('scroll', scroll);
    marquee.hidden = true;
    document.body.classList.remove('canvas-selecting', 'canvas-moving-group');
    try {
      if (canvas.hasPointerCapture?.(d.pointer)) canvas.releasePointerCapture(d.pointer);
    } catch {}
    if (!closed && !editor.isDestroyed) {
      if (!commit)
        editor.view.dispatch(editor.state.tr.setSelection(d.bookmark.resolve(editor.state.doc)));
      else if (d.mode === 'move')
        moveCanvasSelection(editor, d.delta.x, d.delta.y, model.page(), true);
      editor.view.focus();
    }
  }
  function update(e) {
    if (!drag) return;
    drag.last = { clientX: e.clientX, clientY: e.clientY, altKey: e.altKey };
    const p = point(e);
    if (drag.mode === 'move') {
      drag.rawDelta = { x: p.x - drag.start.x, y: p.y - drag.start.y };
      drag.delta = groupDelta(
        drag.objects.map((o) => o.node.attrs),
        drag.rawDelta.x,
        drag.rawDelta.y,
        pagePixels(model.page()),
      );
      if (drag.alignment) {
        const base = drag.alignment.bounds;
        const snapped = model.alignment.adjust(
          { ...base, x: base.x + drag.delta.x, y: base.y + drag.delta.y },
          drag.alignment,
          { bypass: e.altKey },
        );
        drag.delta = groupDelta(
          drag.objects.map((o) => o.node.attrs),
          snapped.x - base.x,
          snapped.y - base.y,
          pagePixels(model.page()),
          false,
        );
      }
      paintGroupOutline(drag.delta);
      for (const o of drag.objects) {
        const dom = editor.view.nodeDOM(o.pos);
        if (dom) {
          dom.style.left = o.node.attrs.x + drag.delta.x + 'px';
          dom.style.top = `calc(${o.node.attrs.y + drag.delta.y}px + ${o.node.attrs.page} * var(--page-step,0px))`;
        }
      }
      return;
    }
    const rect = selectionRect(drag.start, p),
      bounds = paper.getBoundingClientRect(),
      z = model.zoomValue();
    if (rect.width * z < 3 && rect.height * z < 3 && !drag.started) return;
    drag.started = true;
    marquee.hidden = false;
    Object.assign(marquee.style, {
      left: bounds.left + rect.x * z + 'px',
      top: bounds.top + rect.y * z + 'px',
      width: rect.width * z + 'px',
      height: rect.height * z + 'px',
    });
    const ids = canvasObjects(editor.state.doc)
      .filter((o) => insideSelection(rect, o.node.attrs, pagePixels(model.page()).height))
      .map((o) => o.node.attrs.id);
    editor.view.dispatch(
      editor.state.tr.setSelection(
        selectCanvasObjects(editor.state.doc, [...drag.baseIds, ...ids]),
      ),
    );
  }
  function move(e) {
    if (drag && e.pointerId === drag.pointer) {
      e.preventDefault();
      update(e);
    }
  }
  function scroll() {
    if (drag?.last) update(drag.last);
  }
  function up(e) {
    if (drag && e.pointerId === drag.pointer) {
      update(e);
      finish(true);
    }
  }
  function cancel() {
    finish(false);
  }
  function escape(e) {
    if (drag && editorKey(e) === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      finish(false);
    }
  }
  function down(e) {
    if (!model.canvas() || !editor.isEditable || e.button !== 0 || e.target.closest('.canvas-top'))
      return;
    const bounds = canvas.getBoundingClientRect();
    if (
      e.clientX >= bounds.left + canvas.clientWidth ||
      e.clientY >= bounds.top + canvas.clientHeight
    )
      return;
    const objectDOM = e.target.closest('.canvas-object');
    if (e.target.closest('.canvas-resize') && !e.ctrlKey && !e.metaKey) return; // Shift keeps the existing resize proportions.
    if (
      objectDOM &&
      e.shiftKey &&
      objectDOM.dataset.kind === 'text' &&
      !(editor.state.selection instanceof NodeSelection) &&
      !(editor.state.selection instanceof CanvasMultiSelection)
    ) {
      const from = editor.state.selection.$from;
      for (let depth = from.depth; depth > 0; depth--)
        if (from.node(depth).attrs.id === objectDOM.dataset.objectId) return;
    }
    let ids = selectedIds();
    if (objectDOM && (e.shiftKey || e.ctrlKey || e.metaKey)) {
      const id = objectDOM.dataset.objectId;
      const clicked = expandGroupIds(
        canvasObjects(editor.state.doc).map((o) => o.node.attrs),
        [id],
      );
      const remove = clicked.every((id) => ids.includes(id));
      e.preventDefault();
      e.stopImmediatePropagation();
      editor.view.dispatch(
        editor.state.tr.setSelection(
          selectCanvasObjects(
            editor.state.doc,
            remove ? ids.filter((x) => !clicked.includes(x)) : [...ids, ...clicked],
          ),
        ),
      );
      editor.view.focus();
      return;
    }
    if (objectDOM?.dataset.groupId && !ids.includes(objectDOM.dataset.objectId)) {
      editor.view.dispatch(
        editor.state.tr.setSelection(
          selectCanvasObjects(editor.state.doc, [objectDOM.dataset.objectId]),
        ),
      );
      ids = selectedIds();
    }
    const group = editor.state.selection instanceof CanvasMultiSelection;
    if (objectDOM && (!group || !ids.includes(objectDOM.dataset.objectId))) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    drag = {
      mode: objectDOM ? 'move' : 'select',
      pointer: e.pointerId,
      start: point(e),
      bookmark: editor.state.selection.getBookmark(),
      baseIds: e.shiftKey || e.ctrlKey || e.metaKey ? ids : [],
      objects: objectDOM ? matching(editor.state.doc, ids) : [],
      delta: { x: 0, y: 0 },
      started: false,
    };
    drag.alignment = objectDOM ? model.alignment.begin(drag.objects) : null;
    if (!objectDOM && !e.shiftKey && !e.ctrlKey && !e.metaKey)
      editor.view.dispatch(
        editor.state.tr.setSelection(new GapCursor(editor.state.doc.resolve(0))),
      );
    document.body.classList.add(objectDOM ? 'canvas-moving-group' : 'canvas-selecting');
    canvas.setPointerCapture?.(e.pointerId);
    document.addEventListener('pointermove', move, { passive: false });
    document.addEventListener('pointerup', up);
    document.addEventListener('pointercancel', cancel);
    document.addEventListener('keydown', escape, true);
    canvas.addEventListener('scroll', scroll);
    editor.view.focus();
  }
  const changed = ({ transaction }) => {
    if (drag && transaction.docChanged) cancel();
    paintGroupOutline();
  };
  editor.on('transaction', changed);
  canvas.addEventListener('pointerdown', down, true);
  window.addEventListener('orodent-zoom-change', cancel);
  return () => {
    closed = true;
    finish(false);
    marquee.remove();
    groupOutline.remove();
    editor.off('transaction', changed);
    canvas.removeEventListener('pointerdown', down, true);
    window.removeEventListener('orodent-zoom-change', cancel);
  };
}
