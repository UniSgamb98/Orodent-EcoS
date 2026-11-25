import { Fragment } from '@tiptap/pm/model';
import { NodeSelection } from '@tiptap/pm/state';
import { closeHistory } from '@tiptap/pm/history';
import { equalWidths, widthsOf, setColumnWidth, MAX_COLUMNS } from './layout-model.js';

export function sectionPath(state) {
  const { $from } = state.selection, path = [];
  for (let depth = 1; depth <= $from.depth; depth++) {
    const node = $from.node(depth);
    if (node.type.name === 'tonColumns') path.push({ node, pos: $from.before(depth), column: Math.min(node.childCount - 1, $from.index(depth)) });
  }
  if (state.selection instanceof NodeSelection && state.selection.node.type.name === 'tonColumns')
    path.push({ node: state.selection.node, pos: state.selection.from, column: 0 });
  return path;
}
const columnPosition = (section, index) => {
  let pos = section.pos + 1;
  for (let i = 0; i < index; i++) pos += section.node.child(i).nodeSize;
  return pos;
};
export function sectionBreadcrumb(state) {
  const selectedSection = state.selection instanceof NodeSelection && state.selection.node.type.name === 'tonColumns';
  return sectionPath(state).map((section, index, path) => `<button type="button" data-layout-select="${section.pos}">Sezione ${index + 1}</button>${selectedSection && index === path.length - 1 ? '' : `<span>›</span><button type="button" data-layout-select="${columnPosition(section, section.column)}">Colonna ${section.column + 1}</button>`}`).join('<span>›</span>');
}
export function columnProperties(section) {
  const widths = widthsOf(section.node), count = section.node.childCount;
  const preset = JSON.stringify(widths) === JSON.stringify(equalWidths(count)) ? 'equal' : ['60,40', '40,60', '70,30', '30,70'].includes(widths.join(',')) ? widths.join(',') : 'custom';
  return `<h2>Sezione a colonne</h2><p>Colonna ${section.column + 1} di ${count} · Le sezioni possono essere annidate.</p>
    <label for="layout-count">Numero di colonne</label><select id="layout-count">${[1, 2, 3, 4].map(n => `<option value="${n}" ${n === count ? 'selected' : ''}>${n}</option>`).join('')}</select>
    <label for="layout-preset">Distribuzione</label><select id="layout-preset">${[['custom','Personalizzata'],['equal','Uguali'],...(count === 2 ? [['60,40','60% / 40%'],['40,60','40% / 60%'],['70,30','70% / 30%'],['30,70','30% / 70%']] : [])].map(([value,label]) => `<option value="${value}" ${value === preset ? 'selected' : ''}>${label}</option>`).join('')}</select>
    <div class="section layout-widths">${widths.map((n, i) => `<label for="layout-width-${i}">Colonna ${i + 1} · %</label><input id="layout-width-${i}" data-layout-width="${i}" type="number" min="${count === 1 ? 100 : 8}" max="${100 - 8 * (count - 1)}" step="0.1" value="${n}" ${count === 1 ? 'disabled' : ''}>`).join('')}</div>
    <label for="layout-gap">Spazio tra colonne · mm</label><input id="layout-gap" type="number" min="0" max="12" step="0.5" value="${section.node.attrs.gap}">
    <div class="section row"><button type="button" class="line" data-layout-action="add" ${count >= MAX_COLUMNS ? 'disabled' : ''}>＋ Colonna</button><button type="button" class="line" data-layout-action="remove" ${count === 1 ? 'disabled' : ''}>Rimuovi colonna ${section.column + 1}</button></div>
    <div class="row"><button type="button" class="line" data-layout-action="row">＋ Riga sotto</button></div>
    <div class="row"><button type="button" class="line" data-layout-action="unwrap">Sciogli sezione</button></div>
    <p class="note">Trascina il divisore oppure usa le percentuali. Nelle sezioni annidate le larghezze si riferiscono alla colonna contenitore. Rimuovendo una colonna i suoi contenuti passano alla colonna vicina. Sciogliendo la sezione tutti i blocchi vengono conservati in sequenza.</p>`;
}
export function bindColumnControls(root, editor, section, notify, refresh) {
  function active() {
    const node = editor.state.doc.nodeAt(section.pos);
    if (!node || node.type.name !== 'tonColumns') throw new Error('Seleziona di nuovo la sezione.');
    return node;
  }
  function attrs(value) {
    const node = active();
    const tr = closeHistory(editor.state.tr).setNodeMarkup(section.pos, undefined, { ...node.attrs, ...value });
    if (editor.state.selection instanceof NodeSelection) tr.setSelection(NodeSelection.create(tr.doc, editor.state.selection.from));
    editor.view.dispatch(tr); refresh(true);
  }
  function replace(children) {
    const node = active(), next = node.type.create({ ...node.attrs, widths: equalWidths(children.length) }, children);
    const tr = closeHistory(editor.state.tr).replaceWith(section.pos, section.pos + node.nodeSize, next);
    tr.setSelection(NodeSelection.create(tr.doc, section.pos)); editor.view.dispatch(tr); refresh(true);
  }
  function remove(children, index) {
    const [removed] = children.splice(index, 1), target = index === 0 ? 0 : index - 1;
    const content = index === 0 ? removed.content.append(children[target].content) : children[target].content.append(removed.content);
    children[target] = children[target].copy(content);
  }
  function resize(count) {
    const node = active(), children = [];
    node.forEach(col => children.push(col));
    while (children.length > count) remove(children, children.length - 1);
    while (children.length < count) children.push(editor.schema.nodes.tonColumn.create(null, editor.schema.nodes.paragraph.create()));
    replace(children);
  }
  const change = (selector, action) => root.querySelectorAll(selector).forEach(input => {
    input.disabled ||= !editor.isEditable;
    input.onchange = () => {
      if (!editor.isEditable) return;
      try { action(input); } catch (error) { notify(error.message); refresh(true); }
    };
  });
  change('#layout-count', input => resize(Number(input.value)));
  change('#layout-preset', input => { if (input.value !== 'custom') attrs({ widths: input.value === 'equal' ? equalWidths(active().childCount) : input.value.split(',').map(Number) }); });
  change('[data-layout-width]', input => attrs({ widths: setColumnWidth(widthsOf(active()), Number(input.dataset.layoutWidth), Number(input.value)) }));
  change('#layout-gap', input => {
    const gap = Number(input.value);
    if (!Number.isFinite(gap) || gap < 0 || gap > 12) throw new Error('Spazio tra colonne: da 0 a 12 mm.');
    attrs({ gap });
  });
  root.querySelectorAll('[data-layout-action]').forEach(button => {
    button.disabled ||= !editor.isEditable;
    button.onclick = () => {
      if (!editor.isEditable) return;
      const node = active(), action = button.dataset.layoutAction;
      if (action === 'add' && node.childCount < MAX_COLUMNS) resize(node.childCount + 1);
      if (action === 'remove' && node.childCount > 1) {
        const children = []; node.forEach(col => children.push(col)); remove(children, Math.min(section.column, children.length - 1)); replace(children);
      }
      if (action === 'row') {
        const content = Array.from({ length: node.childCount }, () => editor.schema.nodes.tonColumn.create(null, editor.schema.nodes.paragraph.create()));
        const next = node.type.create(node.attrs, content), pos = section.pos + node.nodeSize;
        const tr = closeHistory(editor.state.tr).insert(pos, next); tr.setSelection(NodeSelection.create(tr.doc, pos)); editor.view.dispatch(tr); refresh(true);
      }
      if (action === 'unwrap') {
        const blocks = []; node.forEach(col => col.forEach(block => blocks.push(block)));
        editor.view.dispatch(closeHistory(editor.state.tr).replaceWith(section.pos, section.pos + node.nodeSize, Fragment.fromArray(blocks))); refresh(true);
      }
    };
  });
}
