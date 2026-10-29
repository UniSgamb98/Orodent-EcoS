import { optionDefaults, blockFontSizes, validateBlockOptions } from './block-options.js';
import { Extension, Node, mergeAttributes } from '@tiptap/core';
import Image from '@tiptap/extension-image';
import { columnSectionView } from './column-view.js';
import { layoutStyle, widthsOf, validateLayout, validWidths, equalWidths } from './layout-model.js';
import { blockTypes, fields, blockElement } from './laboratory-blocks.js';

export const TonBlock = Node.create({
  name: 'tonBlock', group: 'block', atom: true, draggable: true,
  addAttributes() {
    return {
      ...Object.fromEntries(Object.entries(optionDefaults).map(([key, value]) => [key, { default: value }])),
      id: { default: '' }, kind: { default: 'identity' }, title: { default: '' },
      compact: { default: false }, showPpm: { default: false }, showCoefficient: { default: false },
      fontFamily: { default: 'Arial' }, fontSize: { default: '9pt' }, color: { default: '#243e50' },
    };
  },
  parseHTML() {
    return [{ tag: 'section[data-ton-block]', getAttrs: element => {
      if (!Object.prototype.hasOwnProperty.call(blockTypes, element.dataset.tonBlock)) return false;
      try {
        const options = element.dataset.options ? JSON.parse(element.dataset.options) : {};
        const attrs = { ...optionDefaults, ...Object.fromEntries(Object.keys(optionDefaults).filter(key => Object.prototype.hasOwnProperty.call(options, key)).map(key => [key, options[key]])),
          id: element.dataset.blockId, kind: element.dataset.tonBlock, title: element.dataset.title,
          compact: element.dataset.compact === 'true', showPpm: (element.dataset.showPpm ?? element.dataset.engineering) === 'true', showCoefficient: (element.dataset.showCoefficient ?? element.dataset.engineering) === 'true',
          fontFamily: element.dataset.fontFamily || 'Arial', fontSize: element.dataset.fontSize || '9pt', color: element.dataset.color || '#243e50' };
        validateBlockOptions(attrs); return attrs;
      } catch { return false; }
    } }];
  },
  renderHTML({ node }) { return blockElement(node.attrs); },
  addNodeView() {
    return ({ node, editor, getPos }) => {
      const dom = blockElement(node.attrs, true);
      const select = event => {
        if (event.button !== 0 || event.target.closest('.block-handle')) return;
        event.preventDefault(); event.stopPropagation();
        editor.commands.setNodeSelection(getPos());
      };
      dom.addEventListener('pointerdown', select);
      return {
        dom,
        destroy() { dom.removeEventListener('pointerdown', select); },
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
  name: 'tonColumns', group: 'block', content: 'tonColumn{1,4}', isolating: true,
  addAttributes() { return { widths: { default: null }, gap: { default: 4.5 } }; },
  parseHTML() { return [{ tag: 'div[data-ton-columns]', contentElement: element => element.querySelector(':scope > .document-columns') || element,
    getAttrs: element => {
      try {
        const body = element.querySelector(':scope > .document-columns') || element;
        const count = [...body.children].filter(child => child.matches('[data-ton-column]')).length;
        const widths = element.dataset.widths ? JSON.parse(element.dataset.widths) : equalWidths(count);
        const gap = Number(element.dataset.gap ?? 4.5);
        return validWidths(widths, count) && Number.isFinite(gap) && gap >= 0 && gap <= 12 ? { widths, gap } : false;
      } catch { return false; }
    } }]; },
  renderHTML({ node }) { return ['div', { class: 'column-section', 'data-ton-columns': String(node.childCount), 'data-widths': JSON.stringify(widthsOf(node)), 'data-gap': String(node.attrs.gap) },
    ['div', { class: 'document-columns', style: layoutStyle(widthsOf(node), node.attrs.gap) }, 0]]; },
  addNodeView() { return columnSectionView; },
});
export const TonColumn = Node.create({
  name: 'tonColumn', content: 'block* paragraph', isolating: true,
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
    if (child.type.name === 'tonColumns') validateLayout(child);
    if (child.type.name === 'tonBlock') validateBlockOptions(child.attrs);
    if (child.type.name === 'tonBlock' && !Object.prototype.hasOwnProperty.call(blockTypes, child.attrs.kind)) throw new Error('Blocco non supportato.');
    if (child.type.name === 'tonBlock' && (typeof child.attrs.title !== 'string' || child.attrs.title.length > 100
        || typeof child.attrs.id !== 'string' || child.attrs.id.length > 100
        || typeof child.attrs.compact !== 'boolean' || typeof child.attrs.showPpm !== 'boolean' || typeof child.attrs.showCoefficient !== 'boolean')) throw new Error('Proprietà del blocco non valide.');
    if (child.type.name === 'tonBlock' && (!['Arial', 'Georgia', 'Verdana', 'Times New Roman'].includes(child.attrs.fontFamily)
        || !blockFontSizes.includes(child.attrs.fontSize)
        || !/^#[a-f\d]{6}$/i.test(child.attrs.color))) throw new Error('Stile del blocco non supportato.');
    if (child.type.name === 'tonField' && !Object.prototype.hasOwnProperty.call(fields, child.attrs.key)) throw new Error('Campo non supportato.');
    if (child.type.name === 'image' && !['25%', '50%', '75%', '100%'].includes(child.attrs.width)) throw new Error('Larghezza immagine non supportata.');
    if (child.type.name === 'image' && (!/^data:image\/(png|jpeg|webp);base64,[a-z\d+/=\r\n]+$/i.test(child.attrs.src || '') || child.attrs.src.length > 3_000_000)) {
      throw new Error('Sono ammesse immagini locali PNG, JPEG o WebP fino a 2 MB.');
    }
  });
}
