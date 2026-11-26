import { NodeSelection } from '@tiptap/pm/state';
import { selectedObject, updateObject } from './canvas-object.js';
import { pagePixels, clampGeometry, mm } from './canvas-model.js';
import { measureLabel } from './label-elements.js';
import { objectName } from './object-properties-profile.js';
import { escape } from './laboratory-blocks.js';
const $ = (id) => document.getElementById(id);
export function mountGeometryProperties(editor, model, { uniqueId, refresh }) {
  function deleteObject() {
    const o = selectedObject(editor.state);
    if (o && editor.isEditable) {
      editor.view.dispatch(editor.state.tr.delete(o.pos, o.pos + o.node.nodeSize));
      refresh(true);
    }
  }
  function duplicate() {
    const o = selectedObject(editor.state);
    if (!o) return;
    const copy = o.node.toJSON();
    copy.attrs = clampGeometry(
      {
        ...copy.attrs,
        id: uniqueId(),
        x: copy.attrs.x + 10,
        y: copy.attrs.y + 10,
        z: Math.max(...editor.state.doc.content.content.map((n) => n.attrs.z)) + 1,
      },
      pagePixels(model.page()),
    );
    const at = editor.state.doc.content.size;
    editor.view.dispatch(editor.state.tr.insert(at, editor.schema.nodeFromJSON(copy)));
    editor.commands.setNodeSelection(at);
    refresh(true);
  }
  function layer(front) {
    const o = selectedObject(editor.state);
    if (!o) return;
    let items = [];
    editor.state.doc.forEach((n, p) => items.push({ node: n, pos: p }));
    items.sort((a, b) => a.node.attrs.z - b.node.attrs.z);
    items = items.filter((x) => x.pos !== o.pos);
    items[front ? 'push' : 'unshift'](o);
    let tr = editor.state.tr;
    items.forEach((x, i) => (tr = tr.setNodeMarkup(x.pos, undefined, { ...x.node.attrs, z: i })));
    tr.setSelection(NodeSelection.create(tr.doc, o.pos));
    editor.view.dispatch(tr);
    refresh(true);
  }
  function render(o) {
    const a = o.node.attrs;
    return `<span class="eyebrow">Oggetto selezionato</span><h2>${escape(objectName(o.node))}</h2><div class="geometry-grid">${[
      ['x', 'X'],
      ['y', 'Y'],
      ['width', 'Larghezza'],
      ['height', 'Altezza'],
    ]
      .map(
        ([key, label]) =>
          `<label>${label} · px<input id="object-${key}" type="number" step="1" min="${key === 'x' || key === 'y' ? 0 : 8}" value="${Math.round(a[key] * 100) / 100}"></label>`,
      )
      .join(
        '',
      )}</div><p class="physical-size">${mm(a.width).toFixed(1)} × ${mm(a.height).toFixed(1)} mm</p>${(model.page().count ?? 1) > 1 ? `<label>Pagina<select id="object-page">${Array.from({ length: model.page().count }, (_, i) => `<option value="${i}" ${i === a.page ? 'selected' : ''}>${i + 1}</option>`).join('')}</select></label>` : ''}<div class="object-actions"><button data-object="front">In primo piano</button><button data-object="back">Sullo sfondo</button><button data-object="fit" title="${a.kind === 'field' ? 'Dimensiona il riquadro sul valore visualizzato, senza cambiare il carattere' : 'Adatta solo l’altezza al contenuto; la larghezza non cambia'}">${a.kind === 'field' ? 'Adatta al dato' : 'Adatta altezza'}</button><button data-object="duplicate">Duplica</button><button data-object="delete" class="danger">Elimina oggetto</button></div><div id="object-specific"></div><p id="object-overflow" class="matrix-error" hidden>Il contenuto supera il riquadro: aumenta le dimensioni o riduci il testo.</p>`;
  }
  function bind({ o, node, root, change, safe }) {
    const a = o.node.attrs;
    for (const k of ['x', 'y', 'width', 'height'])
      change('object-' + k, (input) => {
        const value = Number(input.value);
        if (!Number.isFinite(value)) throw Error('Inserisci un valore numerico.');
        const object = selectedObject(editor.state),
          patch = { [k]: value };
        if (object?.node.attrs.keepRatio && (k === 'width' || k === 'height')) {
          const a = object.node.attrs;
          // The numeric controls follow the same ratio lock as dragging the handles.
          // This also avoids reintroducing contain letterboxing around a symbol.
          if (k === 'width') patch.height = (value * a.height) / a.width;
          else patch.width = (value * a.width) / a.height;
        }
        updateObject(editor, object, patch);
      });
    change('object-page', (input) =>
      updateObject(editor, selectedObject(editor.state), {
        page: Number(input.value),
      }),
    );
    root.querySelectorAll('[data-object]').forEach((b) => {
      b.disabled = !editor.isEditable;
      b.onmousedown = (e) => e.preventDefault();
      b.onclick = () =>
        safe(() => {
          ({
            front: () => layer(true),
            back: () => layer(false),
            duplicate,
            delete: deleteObject,
            fit: () => {
              if (a.kind === 'field') {
                updateObject(
                  editor,
                  selectedObject(editor.state),
                  measureLabel(node.attrs.key, a, node.attrs),
                );
                return;
              }
              const box = document.querySelector(
                `[data-object-id="${a.id}"] .canvas-object-content`,
              );
              const top = box.getBoundingClientRect().top,
                zoom = model.zoomValue();
              const h = Math.max(
                8,
                ...[...box.children].map((n) =>
                  Math.ceil((n.getBoundingClientRect().bottom - top) / zoom),
                ),
              );
              updateObject(editor, selectedObject(editor.state), { height: h });
            },
          })[b.dataset.object]();
        });
    });
  }
  return { render, bind };
}
