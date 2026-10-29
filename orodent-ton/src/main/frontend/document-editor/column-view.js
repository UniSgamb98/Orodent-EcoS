import { NodeSelection } from '@tiptap/pm/state';
import { closeHistory } from '@tiptap/pm/history';
import { layoutStyle, widthsOf, resizePair } from './layout-model.js';

export function columnSectionView({ node, editor, getPos }) {
  const dom = document.createElement('div'); dom.className = 'column-section';
  const toolbar = document.createElement('div'); toolbar.className = 'layout-tools'; toolbar.contentEditable = 'false';
  const contentDOM = document.createElement('div'); contentDOM.className = 'document-columns';
  const handles = document.createElement('div'); handles.className = 'layout-handles'; handles.contentEditable = 'false';
  dom.append(toolbar, contentDOM, handles);
  let current = node, drag, frame, warningFrame, closed = false;
  const buttons = [];
  function select(pos) { if (!closed) editor.commands.setNodeSelection(pos); }
  function apply(widths = widthsOf(current)) {
    dom.dataset.tonColumns = String(current.childCount); dom.dataset.widths = JSON.stringify(widths); dom.dataset.gap = String(current.attrs.gap);
    contentDOM.style.cssText = layoutStyle(widths, current.attrs.gap);
    let cumulative = 0;
    [...handles.children].forEach((handle, i) => {
      cumulative += widths[i];
      // Convert the grid gap to a fraction of its own box, including at any zoom.
      handle.style.left = `calc(${cumulative}% + ${(i + .5 - cumulative / 100 * (current.childCount - 1)) * current.attrs.gap}mm)`;
      handle.setAttribute('aria-valuenow', String(widths[i]));
      handle.setAttribute('aria-valuemax', String(widths[i] + widths[i + 1] - 8));
      handle.title = `${widths[i]}% / ${widths[i + 1]}% · Trascina o usa le frecce`;
    });
  }
  function checkOverflow() {
    cancelAnimationFrame(warningFrame);
    warningFrame = requestAnimationFrame(() => {
      if (closed) return;
      let tooWide = false;
      for (const col of contentDOM.children) {
        // Nested warnings stay attached to the section actually causing them.
        const bad = [...col.querySelectorAll('.lab-table')].some(table => table.closest('.document-columns') === contentDOM
          && (table.getBoundingClientRect().width > table.parentElement.getBoundingClientRect().width + 2 || table.scrollWidth > table.clientWidth + 2));
        // Keep warnings on our wrapper: mutating ProseMirror-owned column
        // attributes can interfere with native text selection.
        tooWide ||= bad;
      }
      if (dom.classList.contains('layout-overflow') !== tooWide) dom.classList.toggle('layout-overflow', tooWide);
    });
  }
  function stop(commit) {
    if (!drag) return;
    const saved = drag; drag = null; cancelAnimationFrame(frame); frame = null;
    try { saved.handle.releasePointerCapture(saved.pointer); } catch {}
    document.body.classList.remove('layout-resizing');
    if (commit && editor.isEditable && editor.state.doc.nodeAt(getPos())?.eq(saved.node)) {
      const widths = saved.widths;
      if (JSON.stringify(widths) !== JSON.stringify(widthsOf(saved.node))) {
        const tr = closeHistory(editor.state.tr).setNodeMarkup(getPos(), undefined, { ...saved.node.attrs, widths });
        tr.setSelection(NodeSelection.create(tr.doc, getPos())); editor.view.dispatch(tr);
      }
    }
    apply(); checkOverflow();
    dom.dispatchEvent(new CustomEvent('orodent-layout-preview', { bubbles: true, detail: { pos: getPos(), widths: widthsOf(current) } }));
  }
  function move(event) {
    if (!drag || event.pointerId !== drag.pointer) return;
    event.preventDefault(); drag.x = event.clientX;
    if (frame) return;
    frame = requestAnimationFrame(() => {
      frame = null;
      if (!drag) return;
      drag.widths = resizePair(widthsOf(drag.node), drag.index, (drag.x - drag.startX) / drag.available * 100);
      apply(drag.widths); checkOverflow();
      dom.dispatchEvent(new CustomEvent('orodent-layout-preview', { bubbles: true, detail: { pos: getPos(), widths: drag.widths } }));
    });
  }
  function end(event) { if (drag && event.pointerId === drag.pointer) { move(event); if (frame) { cancelAnimationFrame(frame); frame = null; drag.widths = resizePair(widthsOf(drag.node), drag.index, (event.clientX - drag.startX) / drag.available * 100); } stop(true); } }
  function cancel() { stop(false); }
  function escape(event) { if (drag && event.key === 'Escape') { event.preventDefault(); event.stopImmediatePropagation(); stop(false); } }
  function renderControls() {
    toolbar.replaceChildren(); handles.replaceChildren(); buttons.length = 0;
    const section = document.createElement('button'); section.type = 'button'; section.className = 'layout-select';
    section.textContent = `▥ Sezione · ${current.childCount} ${current.childCount === 1 ? 'colonna' : 'colonne'}`;
    section.onclick = () => select(getPos()); toolbar.append(section); buttons.push(section);
    current.forEach((col, relative, index) => {
      const button = document.createElement('button'); button.type = 'button'; button.className = 'layout-column-select';
      button.textContent = `Col. ${index + 1}`; const inner = relative;
      button.onclick = () => select(getPos() + 1 + inner); toolbar.append(button); buttons.push(button);
      if (index === current.childCount - 1) return;
      const handle = document.createElement('div'); handle.className = 'layout-divider'; handle.tabIndex = 0;
      handle.setAttribute('role', 'separator'); handle.setAttribute('aria-orientation', 'vertical');
      handle.setAttribute('aria-label', `Larghezza colonne ${index + 1} e ${index + 2}`);
      handle.setAttribute('aria-valuemin', '8'); handle.setAttribute('aria-valuemax', String(widthsOf(current)[index] + widthsOf(current)[index + 1] - 8));
      handle.onpointerdown = event => {
        if (!editor.isEditable || event.button !== 0) return;
        event.preventDefault(); event.stopPropagation(); select(getPos());
        const rect = contentDOM.getBoundingClientRect(), scale = rect.width / contentDOM.offsetWidth;
        const available = rect.width - parseFloat(getComputedStyle(contentDOM).columnGap) * scale * (current.childCount - 1);
        if (available <= 0) return;
        drag = { pointer: event.pointerId, startX: event.clientX, x: event.clientX, index, node: current, widths: widthsOf(current), handle, available };
        try { handle.setPointerCapture(event.pointerId); } catch {}
        document.body.classList.add('layout-resizing');
      };
      handle.onkeydown = event => {
        if (!editor.isEditable || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
        event.preventDefault(); event.stopPropagation();
        const widths = resizePair(widthsOf(current), index, (event.key === 'ArrowLeft' ? -1 : 1) * (event.shiftKey ? 5 : 1));
        const tr = closeHistory(editor.state.tr).setNodeMarkup(getPos(), undefined, { ...current.attrs, widths });
        tr.setSelection(NodeSelection.create(tr.doc, getPos())); editor.view.dispatch(tr);
      };
      handles.append(handle);
    });
    apply();
  }
  const observer = new ResizeObserver(checkOverflow); observer.observe(contentDOM);
  document.addEventListener('pointermove', move, true); document.addEventListener('pointerup', end, true);
  document.addEventListener('pointercancel', cancel, true); document.addEventListener('keydown', escape, true); window.addEventListener('blur', cancel); window.addEventListener('orodent-zoom-change', cancel);
  renderControls();
  return {
    dom, contentDOM,
    update(next) {
      if (next.type !== current.type) return false;
      const countChanged = next.childCount !== current.childCount;
      current = next;
      if (countChanged) renderControls(); else apply();
      checkOverflow(); return true;
    },
    selectNode() { dom.classList.add('ProseMirror-selectednode'); },
    deselectNode() { dom.classList.remove('ProseMirror-selectednode'); },
    stopEvent(event) { return toolbar.contains(event.target) || handles.contains(event.target); },
    ignoreMutation(mutation) {
      if (mutation.type === 'selection') return false;
      if (!contentDOM.contains(mutation.target)) return true;
      if (mutation.type !== 'attributes') return false;
      // Grid geometry belongs to the NodeView; child mutations belong to ProseMirror.
      return mutation.target === contentDOM;
    },
    destroy() {
      closed = true; cancel(); observer.disconnect(); cancelAnimationFrame(warningFrame);
      document.removeEventListener('pointermove', move, true); document.removeEventListener('pointerup', end, true);
      document.removeEventListener('pointercancel', cancel, true); document.removeEventListener('keydown', escape, true); window.removeEventListener('blur', cancel); window.removeEventListener('orodent-zoom-change', cancel);
      for (const button of buttons) button.onclick = null;
    },
  };
}
