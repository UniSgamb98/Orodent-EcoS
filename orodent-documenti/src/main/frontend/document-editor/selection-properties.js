import {
  selectedCanvasObjects,
  groupCanvasSelection,
  ungroupCanvasSelection,
  deleteCanvasSelection,
  moveCanvasSelection,
} from './canvas-selection.js';
import { selectionGroup } from './canvas-groups.js';
import { groupBounds } from './alignment.js';

export function renderSelectionProperties({ editor, model, root, safe, refresh, deselect }) {
  const attrs = selectedCanvasObjects(editor.state).map((o) => o.node.attrs),
    grouped = selectionGroup(attrs),
    bounds = groupBounds(attrs),
    anyGroups = attrs.some((a) => a.groupId);
  root.innerHTML = `<span class="eyebrow">${grouped ? 'Gruppo' : 'Selezione multipla'}</span>
    <h2>${attrs.length} elementi ${grouped ? 'raggruppati' : 'selezionati'}</h2>
    <p>Trascina un elemento per spostare la selezione. Ctrl + clic aggiunge o toglie elementi.</p>
    ${
      grouped
        ? `<div class="geometry-grid">${[
            ['x', 'X'],
            ['y', 'Y'],
            ['width', 'Larghezza'],
            ['height', 'Altezza'],
          ]
            .map(
              ([key, label]) =>
                `<label>${label} · px<input id="group-${key}" type="number" step="1" min="0" value="${Math.round(bounds[key] * 100) / 100}" ${['width', 'height'].includes(key) ? 'readonly' : ''}></label>`,
            )
            .join('')}</div>`
        : ''
    }
    <div class="object-actions">${!grouped ? `<button id="group-selection" ${!bounds ? 'disabled' : ''}>Raggruppa</button>` : ''}
    ${anyGroups ? '<button id="ungroup-selection">Separa gruppo</button>' : ''}
    <button id="delete-selection" class="danger">Elimina selezionati</button>
    <button id="clear-selection">Deseleziona</button></div>
    <p>${grouped ? 'Snap sui soli bordi esterni del gruppo.' : !bounds ? 'Per raggruppare seleziona elementi della stessa pagina.' : 'Raggruppa per conservare la selezione nel modello.'}</p>`;
  const bind = (id, action) => {
    const button = root.querySelector('#' + id);
    if (!button) return;
    button.disabled ||= !editor.isEditable;
    button.onmousedown = (e) => e.preventDefault();
    button.onclick = () =>
      safe(() => {
        action();
        refresh(true);
      });
  };
  bind('group-selection', () => groupCanvasSelection(editor));
  bind('ungroup-selection', () => ungroupCanvasSelection(editor));
  bind('delete-selection', () => deleteCanvasSelection(editor));
  root.querySelector('#clear-selection').onclick = deselect;
  if (grouped)
    for (const key of ['x', 'y']) {
      const input = root.querySelector('#group-' + key);
      input.disabled = !editor.isEditable;
      input.onchange = () =>
        safe(() => {
          const value = Number(input.value);
          if (!Number.isFinite(value) || value < 0) throw Error('Inserisci una posizione valida.');
          moveCanvasSelection(
            editor,
            key === 'x' ? value - bounds.x : 0,
            key === 'y' ? value - bounds.y : 0,
            model.page(),
            true,
          );
          refresh(true);
        });
    }
}
