import { labelFields } from './label-elements.js';
import { editorKey } from './keyboard.js';
import { Node } from '@tiptap/core';
import { NodeSelection, TextSelection } from '@tiptap/pm/state';
import { closeHistory } from '@tiptap/pm/history';
import {
  pagePixels,
  objectStyle,
  validateObject,
  moveGeometry,
  resizeGeometry,
} from './canvas-model.js';

let alignmentProvider = () => null;
export function setCanvasAlignmentProvider(provider) {
  alignmentProvider = provider;
}
let settings = () => ({ format: 'a4', orientation: 'portrait', count: 1 });
export function setCanvasPageProvider(provider) {
  settings = provider;
}
export function selectedObject(state) {
  const s = state.selection;
  if (s instanceof NodeSelection && s.node.type.name === 'canvasObject')
    return { node: s.node, pos: s.from };
  for (let depth = s.$from.depth; depth > 0; depth--)
    if (s.$from.node(depth).type.name === 'canvasObject')
      return { node: s.$from.node(depth), pos: s.$from.before(depth) };
  return null;
}
export function updateObject(editor, object, patch) {
  if (!object || !editor.isEditable) return false;
  const attrs = { ...object.node.attrs, ...patch };
  validateObject(attrs, settings());
  const tr = editor.state.tr.setNodeMarkup(object.pos, undefined, attrs);
  if (editor.state.selection instanceof NodeSelection)
    tr.setSelection(NodeSelection.create(tr.doc, object.pos));
  editor.view.dispatch(tr);
  return true;
}
export function selectObjectText(editor, object) {
  if (!object) return;
  const from = object.pos + 1,
    to = object.pos + object.node.nodeSize - 1;
  editor.view.dispatch(
    editor.state.tr.setSelection(
      TextSelection.between(editor.state.doc.resolve(from), editor.state.doc.resolve(to)),
    ),
  );
}
export const CanvasObject = Node.create({
  name: 'canvasObject',
  group: 'positioned',
  content: 'block+',
  isolating: true,
  selectable: true,
  draggable: false,
  addAttributes() {
    return Object.fromEntries(
      Object.entries({
        id: 'obj-1',
        kind: 'text',
        x: 0,
        y: 0,
        width: 180,
        height: 40,
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
        groupId: '',
      }).map(([key, value]) => [key, { default: value }]),
    );
  },
  parseHTML() {
    return [
      {
        tag: 'div[data-canvas-object]',
        contentElement: '.canvas-object-content',
        getAttrs: (e) => {
          try {
            const attrs = JSON.parse(e.dataset.canvasObject);
            validateObject(attrs);
            return attrs;
          } catch {
            return false;
          }
        },
      },
    ];
  },
  renderHTML({ node }) {
    return [
      'div',
      {
        class: 'canvas-object',
        'data-canvas-object': JSON.stringify(node.attrs),
        'data-object-id': node.attrs.id,
        'data-kind': node.attrs.kind,
        'data-canvas-page': String(node.attrs.page),
        style: objectStyle(node.attrs),
      },
      ['div', { class: 'canvas-object-content' }, 0],
    ];
  },
  addNodeView() {
    return ({ node, editor, getPos }) => {
      const dom = document.createElement('div');
      dom.className = 'canvas-object';
      const contentDOM = document.createElement('div');
      contentDOM.className = 'canvas-object-content';
      const grip = document.createElement('div');
      grip.className = 'canvas-object-grip';
      grip.contentEditable = 'false';
      grip.title = 'Trascina per spostare';
      const handles = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'].map((edge) => {
        const h = document.createElement('span');
        h.className = 'canvas-resize canvas-resize-' + edge;
        h.dataset.edge = edge;
        h.contentEditable = 'false';
        return h;
      });
      dom.append(contentDOM, grip, ...handles);
      let drag = null,
        closed = false;
      const paint = (attrs) => {
        dom.style.cssText = objectStyle(attrs);
        dom.dataset.objectId = attrs.id;
        dom.dataset.kind = attrs.kind;
        dom.dataset.groupId = attrs.groupId ?? '';
        contentDOM.contentEditable = attrs.kind === 'field' ? 'false' : 'inherit';
        dom.dataset.canvasPage = attrs.page;
        grip.textContent =
          attrs.kind === 'field'
            ? (labelFields[node.firstChild?.attrs.key] ?? 'Dato automatico')
            : {
                text: 'Testo',
                field: 'Dato automatico',
                image: 'Immagine',
                matrix: 'GS1',
                table: 'Tabella',
                block: 'Blocco automatico',
                group: 'Gruppo',
              }[attrs.kind];
      };
      const finish = (commit = false) => {
        if (!drag) return;
        const current = drag;
        drag = null;
        alignmentProvider()?.clear();
        document.removeEventListener('pointermove', move);
        document.removeEventListener('pointerup', up);
        document.removeEventListener('pointercancel', cancel);
        document.removeEventListener('keydown', escape, true);
        try {
          if (dom.hasPointerCapture?.(current.pointer)) dom.releasePointerCapture(current.pointer);
        } catch {}
        paint(node.attrs);
        dom.classList.remove('canvas-dragging');
        if (commit && !closed && editor.isEditable && Number.isInteger(getPos())) {
          const tr = closeHistory(editor.state.tr).setNodeMarkup(
            getPos(),
            undefined,
            current.attrs,
          );
          tr.setSelection(NodeSelection.create(tr.doc, getPos()));
          editor.view.dispatch(tr);
          editor.view.focus();
          editor.view.dispatch(closeHistory(editor.state.tr));
        }
        dom.dispatchEvent(
          new CustomEvent('canvas-object-preview', {
            bubbles: true,
            detail: {
              id: node.attrs.id,
              ...(commit ? current.attrs : node.attrs),
            },
          }),
        );
      };
      const move = (e) => {
        if (!drag || e.pointerId !== drag.pointer) return;
        e.preventDefault();
        const dx = (e.clientX - drag.x) / drag.zoom,
          dy = (e.clientY - drag.y) / drag.zoom;
        drag.attrs = drag.edge
          ? resizeGeometry(
              drag.original,
              dx,
              dy,
              drag.edge,
              drag.size,
              e.shiftKey || drag.original.keepRatio,
            )
          : moveGeometry(drag.original, dx, dy, drag.size);
        drag.attrs =
          alignmentProvider()?.adjust(drag.attrs, drag.alignment, {
            edge: drag.edge,
            bypass: e.altKey,
            keepRatio: e.shiftKey || drag.original.keepRatio,
          }) ?? drag.attrs;
        paint(drag.attrs);
        dom.dispatchEvent(
          new CustomEvent('canvas-object-preview', {
            bubbles: true,
            detail: { ...drag.attrs },
          }),
        );
      };
      const up = (e) => {
        if (drag && e.pointerId === drag.pointer) finish(true);
      };
      const cancel = () => finish(false);
      const escape = (e) => {
        if (editorKey(e) === 'Escape' && drag) {
          e.preventDefault();
          e.stopPropagation();
          finish(false);
        }
      };
      const down = (e) => {
        if (!editor.isEditable || e.button !== 0) return;
        const control = e.target.closest('.canvas-resize,.canvas-object-grip');
        const moveBody = ['image', 'matrix', 'block', 'field'].includes(node.attrs.kind);
        if (!control && e.target !== dom && !moveBody) return;
        e.preventDefault();
        e.stopPropagation();
        editor.commands.setNodeSelection(getPos());
        editor.view.focus();
        const paper = document.getElementById('paper'),
          zoom = Number.parseFloat(getComputedStyle(paper).getPropertyValue('--zoom')) || 1;
        drag = {
          pointer: e.pointerId,
          x: e.clientX,
          y: e.clientY,
          zoom,
          edge: control?.dataset.edge,
          alignment: alignmentProvider()?.begin([node.attrs]),
          original: { ...node.attrs },
          attrs: { ...node.attrs },
          size: pagePixels(settings()),
        };
        dom.classList.add('canvas-dragging');
        dom.setPointerCapture?.(e.pointerId);
        document.addEventListener('pointermove', move, { passive: false });
        document.addEventListener('pointerup', up);
        document.addEventListener('pointercancel', cancel);
        document.addEventListener('keydown', escape, true);
      };
      dom.addEventListener('pointerdown', down, true);
      window.addEventListener('orodent-zoom-change', cancel);
      paint(node.attrs);
      return {
        dom,
        contentDOM,
        update(next) {
          if (next.type !== node.type) return false;
          node = next;
          if (!drag) paint(next.attrs);
          return true;
        },
        selectNode() {
          dom.classList.add('ProseMirror-selectednode');
        },
        deselectNode() {
          dom.classList.remove('ProseMirror-selectednode');
        },
        stopEvent(e) {
          return Boolean(e.target.closest('.canvas-object-grip,.canvas-resize'));
        },
        ignoreMutation(m) {
          if (node.attrs.kind === 'field') return m.type !== 'selection';
          return m.type !== 'selection' && !contentDOM.contains(m.target);
        },
        destroy() {
          closed = true;
          finish(false);
          dom.removeEventListener('pointerdown', down, true);
          window.removeEventListener('orodent-zoom-change', cancel);
        },
      };
    };
  },
});
export const CanvasDocument = Node.create({
  name: 'doc',
  topNode: true,
  content: '(block | positioned)*',
});
