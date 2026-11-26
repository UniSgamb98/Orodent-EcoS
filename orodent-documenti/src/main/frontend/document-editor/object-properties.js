import { NodeSelection } from '@tiptap/pm/state';
import { CanvasMultiSelection, deleteCanvasSelection } from './canvas-selection.js';
import { selectedObject } from './canvas-object.js';
import { objectName } from './object-properties-profile.js';
import { mountGeometryProperties } from './geometry-properties.js';
import { createSpecificProperties } from './specific-properties.js';
const $ = (id) => document.getElementById(id);
export function mountObjectProperties({
  editor,
  model,
  safe,
  notify,
  uniqueId,
  run,
  deselect,
  requestImageReplacement,
}) {
  let propertyKey = '';
  const geometry = mountGeometryProperties(editor, model, { uniqueId, refresh });
  const specifics = createSpecificProperties();
  function refresh(force = false) {
    const o = selectedObject(editor.state),
      root = $('selection-properties');
    const key =
      (editor.state.selection instanceof CanvasMultiSelection
        ? editor.state.selection.ids.join(',')
        : o
          ? o.pos + JSON.stringify(o.node.attrs) + JSON.stringify(o.node.firstChild?.attrs)
          : 'none') +
      editor.isEditable +
      editor.isActive('table');
    if (!force && (key === propertyKey || $('properties').contains(document.activeElement))) return;
    propertyKey = key;

    if (editor.state.selection instanceof CanvasMultiSelection) {
      root.innerHTML =
        '<span class="eyebrow">Selezione multipla</span><h2>' +
        editor.state.selection.ids.length +
        ' elementi selezionati</h2>' +
        '<p>Trascina uno degli elementi per spostarli insieme. Frecce: 1 px; Maiusc + frecce: 10 px.</p>' +
        '<div class="object-actions"><button id="delete-selection" class="danger">Elimina selezionati</button><button id="clear-selection">Deseleziona</button></div>';
      $('delete-selection').disabled = !editor.isEditable;
      $('delete-selection').onclick = () => deleteCanvasSelection(editor);
      $('clear-selection').onclick = deselect;
      return;
    }
    if (!o) {
      root.innerHTML =
        '<span class="eyebrow">Proprietà</span><h2>Seleziona un oggetto</h2><p>Inserisci un riquadro di testo, un’immagine o un codice. Trascina la maniglia per spostarlo; usa i bordi per ridimensionarlo.</p><p>Frecce: 1 px · Maiusc + frecce: 10 px.</p><div id="object-list"></div>';
      editor.state.doc.forEach((n, pos) => {
        if (n.type.name !== 'canvasObject') return;
        const b = document.createElement('button');
        b.className = 'object-list-item';
        b.textContent = objectName(n) + ' · Pagina ' + (n.attrs.page + 1);
        b.onclick = () => editor.commands.setNodeSelection(pos);
        $('object-list').append(b);
      });
      return;
    }
    const node =
      editor.state.selection instanceof NodeSelection &&
      editor.state.selection.node.type.name !== 'canvasObject'
        ? editor.state.selection.node
        : o.node.firstChild;
    root.innerHTML = geometry.render(o);
    const change = (id, action) => {
      const input = $(id);
      if (input) {
        input.disabled = !editor.isEditable;
        input.onchange = () => {
          safe(() => action(input));
          refresh(true);
        };
      }
    };
    const context = {
      editor,
      model,
      o,
      node,
      root,
      change,
      safe,
      run,
      refresh,
      notify,
      requestImageReplacement,
    };
    geometry.bind(context);
    specifics({ ...context, root: $('object-specific') });
  }

  function overflow() {
    const o = selectedObject(editor.state);
    if (!o || !$('object-overflow')) return;
    const content = document.querySelector(
      `[data-object-id="${o.node.attrs.id}"] .canvas-object-content`,
    );
    if (content)
      $('object-overflow').hidden =
        content.scrollHeight <= content.clientHeight + 1 &&
        content.scrollWidth <= content.clientWidth + 1;
  }
  return { refresh, overflow };
}
