import { validateMatrix } from './label-elements.js';
import { escape } from './laboratory-blocks.js';
import { blockProperties, bindBlockProperties } from './block-properties.js';
import {
  automaticFieldProperties,
  bindAutomaticFieldProperties,
} from './automatic-field-properties.js';
const $ = (id) => document.getElementById(id);
export function createSpecificProperties() {
  let blockTab = 'content';
  const handlers = {
    field: { render: automaticFieldProperties, bind: bindAutomaticFieldProperties },
    image: {
      render: ({ node }) =>
        '<label>Descrizione immagine<input id="image-alt" value="' +
        escape(node.attrs.alt ?? '') +
        '"></label><button id="replace-image">Sostituisci immagine dal PC</button>',
      bind: ({ editor, change, requestImageReplacement }) => {
        change('image-alt', (input) =>
          editor.commands.updateAttributes('image', { alt: input.value }),
        );
        $('replace-image').disabled = !editor.isEditable;
        $('replace-image').onclick = requestImageReplacement;
      },
    },
    matrix: {
      render: ({ node }) =>
        '<h3>Dati GS1</h3>' +
        [
          ['gtin', 'GTIN · AI 01'],
          ['lot', 'Lotto · AI 10'],
          ['productionDate', 'Produzione · AI 11'],
          ['expiryDate', 'Scadenza · AI 17'],
        ]
          .map(
            ([key, label]) =>
              `<label>${label}<input id="gs1-${key}" value="${escape(node.attrs[key])}"></label>`,
          )
          .join('') +
        '<p>Usa {{gtin}}, {{lot}}, {{productionDateGs1}}, {{expiryDateGs1}}. Le date sono facoltative. FNC1 e separatori sono automatici.</p>',
      bind: ({ editor, node, change }) => {
        for (const key of ['gtin', 'lot', 'productionDate', 'expiryDate'])
          change('gs1-' + key, (input) => {
            const attrs = { ...node.attrs, [key]: input.value };
            validateMatrix(attrs);
            editor.commands.updateAttributes('gs1Matrix', attrs);
          });
      },
    },
    block: {
      render: ({ node }) => blockProperties(node.attrs, blockTab),
      bind: ({ root, editor, node, refresh, notify }) =>
        bindBlockProperties(
          root,
          editor,
          node.attrs,
          (tab) => {
            blockTab = tab;
          },
          refresh,
          notify,
        ),
    },
    table: {
      render: () =>
        '<p>Fai clic in una cella per modificare la tabella.</p><div class="object-actions">' +
        [
          ['addRowAfter', '＋ Riga'],
          ['addColumnAfter', '＋ Colonna'],
          ['deleteRow', '− Riga'],
          ['deleteColumn', '− Colonna'],
          ['mergeCells', 'Unisci celle'],
          ['splitCell', 'Dividi cella'],
        ]
          .map(([c, label]) => `<button data-table-command="${c}">${label}</button>`)
          .join('') +
        '</div>',
      bind: ({ root, editor, safe, run }) =>
        root.querySelectorAll('[data-table-command]').forEach((b) => {
          b.disabled = !editor.isEditable || !editor.isActive('table');
          b.onmousedown = (e) => e.preventDefault();
          b.onclick = () => safe(() => run(b.dataset.tableCommand));
        }),
    },
  };
  return (context) => {
    const handler = handlers[context.o.node.attrs.kind];
    context.root.innerHTML = handler?.render(context) ?? '';
    handler?.bind(context);
  };
}
