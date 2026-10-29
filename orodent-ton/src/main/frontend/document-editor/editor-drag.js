import { blockTypes, fields } from './laboratory-blocks.js';

// WebKit's native DataTransfer bridge can discard custom MIME types. Pointer
// gestures keep the semantic payload in this document instead of dragging labels.
export function mountEditorDrag(editor, insert) {
  const canvas = document.getElementById('canvas');
  const cleanups = [];
  let gesture, frame, suppressed, marker, ghost;
  const listen = (node, name, fn, capture = false) => {
    node.addEventListener(name, fn, capture);
    cleanups.push(() => node.removeEventListener(name, fn, capture));
  };
  const allowed = kind => Object.hasOwn(blockTypes, kind) || ['heading', 'text', 'columns'].includes(kind)
    || (kind?.startsWith('field:') && Object.hasOwn(fields, kind.slice(6)));
  function clear() {
    if (gesture?.dragging) suppressed = { source: gesture.source, until: Date.now() + 500 };
    if (gesture) { try { gesture.source.releasePointerCapture(gesture.pointer); } catch {} }
    gesture = null; cancelAnimationFrame(frame); ghost?.remove(); marker?.remove();
    ghost = marker = null; document.body.classList.remove('block-dragging');
  }
  function destination(x, y) {
    const rect = canvas.getBoundingClientRect();
    if (x < rect.left || x >= rect.right || y < rect.top || y >= rect.bottom) return null;
    return editor.view.posAtCoords({ left: x, top: y })?.pos ?? editor.state.doc.content.size;
  }
  function paint() {
    if (!gesture?.dragging || !editor.isEditable) { clear(); return; }
    const { x, y } = gesture, rect = canvas.getBoundingClientRect();
    if (x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom) {
      if (y < rect.top + 36) canvas.scrollTop -= 12;
      else if (y > rect.bottom - 36) canvas.scrollTop += 12;
    }
    gesture.position = destination(x, y);
    ghost.style.left = `${x + 14}px`; ghost.style.top = `${y + 14}px`;
    marker.hidden = gesture.position === null;
    if (gesture.position !== null) {
      const coords = editor.view.coordsAtPos(gesture.position);
      marker.style.left = `${Math.max(rect.left + 12, coords.left)}px`;
      marker.style.top = `${coords.top}px`;
    }
    frame = requestAnimationFrame(paint);
  }
  function start() {
    gesture.dragging = true; document.body.classList.add('block-dragging');
    ghost = document.createElement('div'); ghost.className = 'block-drag-ghost';
    ghost.textContent = gesture.kind ? (blockTypes[gesture.kind] || gesture.source.textContent.trim()) : 'Sposta blocco';
    marker = document.createElement('div'); marker.className = 'block-drop-marker';
    document.body.append(ghost, marker); paint();
  }
  listen(document, 'pointerdown', event => {
    if (!editor.isEditable || event.button !== 0 || gesture) return;
    suppressed = null;
    const source = event.target.closest('[data-insert],.block-handle');
    if (!source) return;
    const kind = source.dataset.insert;
    if (kind && !allowed(kind)) return;
    const blockId = kind ? null : source.closest('[data-block-id]')?.dataset.blockId;
    if (!kind && !blockId) return;
    gesture = { source, kind, blockId, pointer: event.pointerId, x: event.clientX, y: event.clientY,
      startX: event.clientX, startY: event.clientY, dragging: false };
    try { source.setPointerCapture(event.pointerId); } catch {}
    if (blockId) event.preventDefault();
  }, true);
  listen(document, 'pointermove', event => {
    if (!gesture || gesture.pointer !== event.pointerId) return;
    gesture.x = event.clientX; gesture.y = event.clientY;
    if (!gesture.dragging && Math.hypot(gesture.x - gesture.startX, gesture.y - gesture.startY) >= 6) start();
    if (gesture.dragging) event.preventDefault();
  }, true);
  listen(document, 'pointerup', event => {
    if (!gesture || gesture.pointer !== event.pointerId) return;
    const current = gesture, position = destination(event.clientX, event.clientY);
    clear();
    if (!editor.isEditable || !current.dragging || position === null) return;
    event.preventDefault(); event.stopPropagation();
    if (current.kind) { insert(current.kind, position); return; }
    let node, from;
    editor.state.doc.descendants((candidate, pos) => {
      if (candidate.type.name === 'tonBlock' && candidate.attrs.id === current.blockId) { node = candidate; from = pos; }
    });
    if (!node || (position >= from && position <= from + node.nodeSize)) return;
    const tr = editor.state.tr.deleteRange(from, from + node.nodeSize);
    const target = tr.mapping.map(position);
    tr.replaceRangeWith(target, target, node).scrollIntoView();
    editor.view.dispatch(tr); editor.commands.focus();
  }, true);
  listen(document, 'click', event => {
    if (suppressed && Date.now() <= suppressed.until && suppressed.source.contains(event.target)) {
      event.preventDefault(); event.stopImmediatePropagation(); suppressed = null;
    }
  }, true);
  listen(document, 'pointercancel', clear, true);
  listen(window, 'blur', clear);
  listen(document, 'keydown', event => {
    if (event.key === 'Escape' && gesture) { clear(); event.preventDefault(); event.stopPropagation(); }
  }, true);
  listen(document, 'dragstart', event => {
    if (event.target.closest('[data-insert],.ton-block')) event.preventDefault();
  }, true);
  // Also understand a semantic plain-text payload from engines which strip MIME
  // data, and from older editor windows. Never interpret a human-readable label.
  const kindFromDrop = event => {
    const raw = event.dataTransfer?.getData('application/x-orodent-block');
    const plain = event.dataTransfer?.getData('text/plain') || '';
    const kind = raw || (plain.startsWith('orodent-ton-block:v1:') ? plain.slice(21) : '');
    return allowed(kind) ? kind : null;
  };
  listen(canvas, 'dragover', event => {
    if (editor.isEditable && (kindFromDrop(event) || [...event.dataTransfer.types].includes('application/x-orodent-block'))) event.preventDefault();
  }, true);
  listen(canvas, 'drop', event => {
    const kind = kindFromDrop(event);
    if (!kind || !editor.isEditable) return;
    event.preventDefault(); event.stopImmediatePropagation();
    insert(kind, destination(event.clientX, event.clientY) ?? editor.state.doc.content.size);
  }, true);
  return () => { clear(); suppressed = null; cleanups.forEach(fn => fn()); };
}
