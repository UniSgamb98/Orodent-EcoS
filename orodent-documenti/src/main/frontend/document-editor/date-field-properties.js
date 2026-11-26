import { defaultDateFormat, validateFieldDateFormat } from './date-format.js';
import { labelValueText } from './label-elements.js';
import { escape } from './laboratory-blocks.js';

export const dateFieldProperties = {
  render({ node }) {
    return `<label>Formato data<input id="date-format" type="text" maxlength="64" spellcheck="false" autocomplete="off" value="${escape(node.attrs.dateFormat || defaultDateFormat)}" placeholder="dd/mm/yyyy" aria-describedby="date-format-help date-format-error"></label>
      <p id="date-format-help">dd = giorno · mm = mese · yyyy = anno · yy = anno a due cifre. Numeri e separatori restano fissi: yymm00 → 260900.</p>
      <p>Anteprima: <output id="date-format-preview">${escape(labelValueText(node.attrs))}</output></p>
      <p id="date-format-error" class="matrix-error" role="alert" hidden></p>`;
  },
  bind({ node, editor, root, change }) {
    const input = root.querySelector('#date-format'),
      preview = root.querySelector('#date-format-preview'),
      error = root.querySelector('#date-format-error');
    function apply() {
      validateFieldDateFormat(node.attrs.key, input.value);
      const attrs = { ...node.attrs, dateFormat: input.value };
      const value = labelValueText(attrs);
      if (editor.isEditable)
        editor.commands.updateAttributes(node.type.name, { dateFormat: input.value });
      preview.textContent = value;
      input.setAttribute('aria-invalid', 'false');
      error.hidden = true;
    }
    change('date-format', apply);
    input.oninput = () => {
      try {
        apply();
      } catch (invalid) {
        input.setAttribute('aria-invalid', 'true');
        error.textContent = invalid.message;
        error.hidden = false;
      }
    };
  },
};
