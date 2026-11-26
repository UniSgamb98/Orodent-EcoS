import { NodeSelection } from '@tiptap/pm/state';
import { selectedObject, updateObject, selectObjectText } from './canvas-object.js';
import { propertyProfile } from './object-properties-profile.js';
const $ = (id) => document.getElementById(id);
export function mountTextProperties(editor, { on, safe, refresh }) {
  const hasRange = () =>
    !editor.state.selection.empty && !(editor.state.selection instanceof NodeSelection);
  function format(patch, command) {
    const o = selectedObject(editor.state);
    if (!o || !propertyProfile(o.node).text || !editor.isEditable) return;
    if (o.node.attrs.kind === 'field') {
      updateObject(editor, o, patch);
      refresh();
      return;
    }
    if (hasRange() && command) {
      command(editor.chain().focus()).run();
      return;
    }
    if (command) {
      selectObjectText(editor, o);
      command(editor.chain().focus()).run();
    }
    const current = { node: editor.state.doc.nodeAt(o.pos), pos: o.pos };
    updateObject(editor, current, patch);
    editor.commands.setNodeSelection(o.pos);
    refresh();
  }
  function run(command) {
    const o = selectedObject(editor.state);
    if (['bold', 'italic', 'underline'].includes(command))
      return format(
        { [command]: !o?.node.attrs[command] },
        hasRange()
          ? (c) =>
              c[
                {
                  bold: 'toggleBold',
                  italic: 'toggleItalic',
                  underline: 'toggleUnderline',
                }[command]
              ]()
          : (c) =>
              c[
                {
                  bold: 'unsetBold',
                  italic: 'unsetItalic',
                  underline: 'unsetUnderline',
                }[command]
              ](),
      );
    if (command.startsWith('align-'))
      return format({ textAlign: command.slice(6) }, (c) => c.setTextAlign(command.slice(6)));
    if (['bulletList', 'orderedList'].includes(command))
      return format({}, (c) =>
        c[command === 'bulletList' ? 'toggleBulletList' : 'toggleOrderedList'](),
      );
    return false;
  }
  const formats = {
    'font-family': (v) => format({ fontFamily: v }, (c) => c.setFontFamily(v)),
    'font-size': (v) => format({ fontSize: v }, (c) => c.setFontSize(v)),
    'text-color': (v) => format({ color: v }, (c) => c.setColor(v)),
    'text-highlight': (v) => format({}, (c) => c.setBackgroundColor(v)),
    'line-height': (v) => format({ lineHeight: Number(v) }, (c) => c.setLineHeight(v)),
    'text-type': (v) =>
      format(
        {
          fontSize: v === 'paragraph' ? '11pt' : v === '1' ? '24pt' : v === '2' ? '18pt' : '14pt',
        },
        (c) => (v === 'paragraph' ? c.setParagraph() : c.setHeading({ level: Number(v) })),
      ),
  };
  Object.entries(formats).forEach(([id, action]) =>
    on($(id), 'change', () => safe(() => action($(id).value))),
  );

  function update(o, editable) {
    const profile = propertyProfile(o?.node),
      a = o?.node.attrs,
      root = $('text-properties');
    root.hidden = !profile.text;
    root.classList.toggle('automatic-field', Boolean(profile.text && !profile.paragraph));
    if (a)
      for (const [id, value] of [
        ['font-family', a.fontFamily],
        ['font-size', a.fontSize],
        ['line-height', String(a.lineHeight)],
        ['text-color', a.color],
        [
          'text-type',
          o.node.firstChild?.type.name === 'heading'
            ? String(o.node.firstChild.attrs.level)
            : 'paragraph',
        ],
      ])
        if (document.activeElement !== $(id)) $(id).value = value;
    root
      .querySelectorAll('button,input,select')
      .forEach((n) => (n.disabled = !editable || !profile.text));
    for (const c of ['bold', 'italic', 'underline'])
      document
        .querySelector(`[data-command="${c}"]`)
        .setAttribute('aria-pressed', String(Boolean(a?.[c])));
  }
  return {
    run,
    update,
    handles: (command) =>
      ['bold', 'italic', 'underline', 'bulletList', 'orderedList'].includes(command) ||
      command.startsWith('align-'),
  };
}
