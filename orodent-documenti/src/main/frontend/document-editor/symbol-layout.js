import symbols from './symbols.json' with { type: 'json' };

const sources = new Map();
for (const symbol of symbols) {
  sources.set(symbol.src, {
    symbol,
    width: symbol.width,
    height: symbol.height,
    bounds: { x: 0, y: 0, width: symbol.width, height: symbol.height },
  });
  if (symbol.legacy) sources.set(symbol.legacy.src, { symbol, ...symbol.legacy });
}
export function symbolSize(symbol, longSide) {
  const scale = longSide / Math.max(symbol.width, symbol.height);
  return { width: symbol.width * scale, height: symbol.height * scale };
}
export function normalizeSymbolObject(object) {
  if (
    object.type !== 'canvasObject' ||
    object.attrs?.kind !== 'image' ||
    object.content?.length !== 1
  )
    return null;
  const image = object.content[0],
    source = sources.get(image.attrs?.src);
  if (image.type !== 'image' || !source) return null;
  const a = object.attrs,
    b = source.bounds,
    scale = Math.min(a.width / source.width, a.height / source.height);
  const attrs = {
    ...a,
    x: a.x + (a.width - source.width * scale) / 2 + b.x * scale,
    y: a.y + (a.height - source.height * scale) / 2 + b.y * scale,
    width: b.width * scale,
    height: b.height * scale,
  };
  // Preserve historical artwork rather than enlarging it to meet the existing 8px minimum.
  if (attrs.width < 8 || attrs.height < 8) return null;
  if (
    image.attrs.src === source.symbol.src &&
    ['x', 'y', 'width', 'height'].every((k) => Math.abs(attrs[k] - a[k]) < 1e-7)
  )
    return null;
  return {
    ...object,
    attrs,
    content: [{ ...image, attrs: { ...image.attrs, src: source.symbol.src } }],
  };
}
export function normalizeSymbolObjects(doc) {
  let changed = false;
  const content = (doc.content ?? []).map((object) => {
    const normalized = normalizeSymbolObject(object);
    if (normalized) {
      changed = true;
      return normalized;
    }
    return object;
  });
  return changed ? { ...doc, content } : null;
}
