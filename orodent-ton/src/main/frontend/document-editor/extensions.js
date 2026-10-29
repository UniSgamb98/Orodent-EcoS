import { Extension, Node, mergeAttributes } from '@tiptap/core';
import Image from '@tiptap/extension-image';
import { blockTypes, fields, blockElement } from './laboratory-blocks.js';

export const TonBlock = Node.create({
  name: 'tonBlock', group: 'block', atom: true, draggable: true,
  addAttributes() {
    return {
      id: { default: '' }, kind: { default: 'identity' }, title: { default: '' },
      compact: { default: false }, engineering: { default: false },
      fontFamily: { default: 'Arial' }, fontSize: { default: '9pt' }, color: { default: '#243e50' },
    };
  },
  parseHTML() {
    return [{ tag: 'section[data-ton-block]', getAttrs: element => Object.prototype.hasOwnProperty.call(blockTypes, element.dataset.tonBlock) ? ({
      id: element.dataset.blockId, kind: element.dataset.tonBlock, title: element.dataset.title,
      compact: element.dataset.compact === 'true', engineering: element.dataset.engineering === 'true',
      fontFamily: element.dataset.fontFamily || 'Arial', fontSize: element.dataset.fontSize || '9pt', color: element.dataset.color || '#243e50',
    }) : false }];
  },
  renderHTML({ node }) { return blockElement(node.attrs); },
  addNodeView() {
    return ({ node }) => {
      const dom = blockElement(node.attrs, true);
      return {
        dom,
        update(next) {
          if (next.type !== node.type) return false;
          node = next;
          const replacement = blockElement(next.attrs, true);
          const selected = dom.classList.contains('ProseMirror-selectednode');
          dom.className = replacement.className;
          if (selected) dom.classList.add('ProseMirror-selectednode');
          for (const key of Object.keys(replacement.dataset)) dom.dataset[key] = replacement.dataset[key];
          dom.style.cssText = replacement.style.cssText;
          dom.replaceChildren(...replacement.childNodes);
          return true;
        },
        selectNode() { dom.classList.add('ProseMirror-selectednode'); },
        deselectNode() { dom.classList.remove('ProseMirror-selectednode'); },
        ignoreMutation() { return true; },
      };
    };
  },
});

export const TonField = Node.create({
  name: 'tonField', group: 'inline', inline: true, atom: true,
  addAttributes() { return { key: { default: 'production' } }; },
  parseHTML() { return [{ tag: 'span[data-ton-field]', getAttrs: e => Object.prototype.hasOwnProperty.call(fields, e.dataset.tonField) ? ({ key: e.dataset.tonField }) : false }]; },
  renderHTML({ node }) {
    const field = fields[node.attrs.key];
    if (!field) throw new Error('Campo automatico non supportato.');
    return ['span', { class: 'ton-field', 'data-ton-field': node.attrs.key, title: field[0], contenteditable: 'false' }, field[1]];
  },
});

export const TonColumns = Node.create({
  name: 'tonColumns', group: 'block', content: 'tonColumn tonColumn', isolating: true,
  parseHTML() { return [{ tag: 'div[data-ton-columns]' }]; },
  renderHTML() { return ['div', { class: 'document-columns', 'data-ton-columns': '2' }, 0]; },
});
export const TonColumn = Node.create({
  name: 'tonColumn', content: 'block+', isolating: true,
  parseHTML() { return [{ tag: 'div[data-ton-column]' }]; },
  renderHTML() { return ['div', { class: 'document-column', 'data-ton-column': '' }, 0]; },
});

export const TableAppearance = Extension.create({
  name: 'tableAppearance',
  addGlobalAttributes() {
    return [{ types: ['table'], attributes: {
      striped: { default: false, parseHTML: e => e.dataset.striped === 'true', renderHTML: a => ({ 'data-striped': String(a.striped) }) },
      compact: { default: false, parseHTML: e => e.dataset.compact === 'true', renderHTML: a => ({ 'data-compact': String(a.compact) }) },
    } }];
  },
});

export const LocalImage = Image.extend({
  addAttributes() {
    return { ...this.parent?.(), width: {
      default: '100%', parseHTML: e => e.style.width || '100%', renderHTML: a => ({ style: `width:${a.width}` }),
    } };
  },
  parseHTML() {
    return ['png', 'jpeg', 'webp'].map(type => ({ tag: `img[src^="data:image/${type};base64,"]` }));
  },
  renderHTML({ HTMLAttributes }) { return ['img', mergeAttributes(this.options.HTMLAttributes, HTMLAttributes)]; },
}).configure({ allowBase64: true });

export function validateDocument(node) {
  node.check();
  node.descendants(child => {
    if (child.type.name === 'tonBlock' && !Object.prototype.hasOwnProperty.call(blockTypes, child.attrs.kind)) throw new Error('Blocco non supportato.');
    if (child.type.name === 'tonBlock' && (typeof child.attrs.title !== 'string' || child.attrs.title.length > 100
        || typeof child.attrs.id !== 'string' || child.attrs.id.length > 100
        || typeof child.attrs.compact !== 'boolean' || typeof child.attrs.engineering !== 'boolean')) throw new Error('Proprietà del blocco non valide.');
    if (child.type.name === 'tonBlock' && (!['Arial', 'Georgia', 'Verdana', 'Times New Roman'].includes(child.attrs.fontFamily)
        || !['9pt', '10pt', '11pt', '12pt', '14pt', '18pt', '24pt'].includes(child.attrs.fontSize)
        || !/^#[a-f\d]{6}$/i.test(child.attrs.color))) throw new Error('Stile del blocco non supportato.');
    if (child.type.name === 'tonField' && !Object.prototype.hasOwnProperty.call(fields, child.attrs.key)) throw new Error('Campo non supportato.');
    if (child.type.name === 'image' && !['25%', '50%', '75%', '100%'].includes(child.attrs.width)) throw new Error('Larghezza immagine non supportata.');
    if (child.type.name === 'image' && (!/^data:image\/(png|jpeg|webp);base64,[a-z\d+/=\r\n]+$/i.test(child.attrs.src || '') || child.attrs.src.length > 3_000_000)) {
      throw new Error('Sono ammesse immagini locali PNG, JPEG o WebP fino a 2 MB.');
    }
  });
}
