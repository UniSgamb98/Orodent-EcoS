import { alignmentTargets, snapMove, snapResize, groupBounds } from './alignment.js';
import { pagePixels, px } from './canvas-model.js';

export function createAlignmentGuides(editor, getPage, getZoom) {
  const root = document.getElementById('alignment-guides'),
    toggle = document.getElementById('snap-toggle');
  let enabled = false;
  const clear = () => root.replaceChildren();
  function change() {
    enabled = !enabled;
    toggle.setAttribute('aria-pressed', String(enabled));
    toggle.title = enabled
      ? 'Snap attivo. Alt durante il trascinamento lo sospende.'
      : 'Attiva lo snap a bordi, centri e margini';
    clear();
  }
  toggle.addEventListener('click', change);
  function begin(objects) {
    if (!enabled) return null;
    const attrs = objects.map((o) => o.node?.attrs ?? o),
      bounds = groupBounds(attrs);
    if (!bounds) return null;
    const exclude = new Set(attrs.map((a) => a.id)),
      others = [];
    editor.state.doc.forEach((node) => {
      const a = node.attrs;
      if (node.type.name === 'canvasObject' && a.page === bounds.page && !exclude.has(a.id))
        others.push(a);
    });
    const page = pagePixels(getPage());
    return { bounds, page, targets: alignmentTargets(others, page, px(getPage().margin)) };
  }
  function show(guides, page, size) {
    root.replaceChildren();
    for (const guide of guides) {
      const line = document.createElement('div');
      line.className = 'alignment-guide alignment-guide-' + guide.axis;
      line.dataset.axis = guide.axis;
      line.dataset.value = String(guide.value);
      if (guide.axis === 'x')
        Object.assign(line.style, {
          left: guide.value + 'px',
          top: page * (size.height + 24) + 'px',
          height: size.height + 'px',
        });
      else
        Object.assign(line.style, {
          top: page * (size.height + 24) + guide.value + 'px',
          left: '0px',
          width: size.width + 'px',
        });
      root.append(line);
    }
  }
  function adjust(attrs, session, { edge = null, bypass = false, keepRatio = false } = {}) {
    if (!enabled || !session || bypass || (edge && keepRatio)) {
      clear();
      return attrs;
    }
    const result = edge
      ? snapResize(attrs, edge, session.targets, session.page, 6 / getZoom())
      : snapMove(attrs, session.targets, session.page, 6 / getZoom());
    show(result.guides, attrs.page, session.page);
    return result.attrs;
  }
  return {
    begin,
    adjust,
    clear,
    dispose() {
      clear();
      toggle.removeEventListener('click', change);
    },
  };
}
