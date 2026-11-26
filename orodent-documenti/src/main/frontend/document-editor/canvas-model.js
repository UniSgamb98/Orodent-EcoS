// Logical pixels use the CSS reference resolution (96 dpi), independent of zoom and printer dpi.
export const PX_PER_MM = 96 / 25.4;
export const MAX_PAGES = 50;
export const objectKinds = ['text', 'field', 'image', 'matrix', 'table', 'block', 'group'];
export const px = (mm) => mm * PX_PER_MM;
export const mm = (pixels) => pixels / PX_PER_MM;
export function pagePixels(page) {
  const landscape = page.orientation === 'landscape';
  return {
    width: px(page.format === 'custom' ? page.width : landscape ? 297 : 210),
    height: px(page.format === 'custom' ? page.height : landscape ? 210 : 297),
    count: page.count ?? 1,
  };
}
export function validateObject(attrs, page) {
  if (!attrs || !/^obj-[a-zA-Z0-9_-]{1,64}$/.test(attrs.id) || !objectKinds.includes(attrs.kind))
    throw new Error('Oggetto del documento non valido.');
  if (
    attrs.groupId !== undefined &&
    (typeof attrs.groupId !== 'string' ||
      (attrs.groupId && !/^grp-[a-zA-Z0-9_-]{1,64}$/.test(attrs.groupId)))
  )
    throw new Error('Identificatore del gruppo non valido.');
  for (const key of ['x', 'y', 'width', 'height', 'z', 'page'])
    if (!Number.isFinite(attrs[key])) throw new Error('Coordinate dell’oggetto non valide.');
  if (
    attrs.x < 0 ||
    attrs.y < 0 ||
    attrs.width < 8 ||
    attrs.height < 8 ||
    attrs.width > 40000 ||
    attrs.height > 40000 ||
    !Number.isInteger(attrs.z) ||
    attrs.z < 0 ||
    attrs.z > 1000000 ||
    !Number.isInteger(attrs.page) ||
    attrs.page < 0 ||
    attrs.page >= MAX_PAGES
  )
    throw new Error('Dimensioni o posizione dell’oggetto non valide.');
  if (page) {
    const size = pagePixels(page);
    if (
      attrs.page >= size.count ||
      attrs.x + attrs.width > size.width + 0.1 ||
      attrs.y + attrs.height > size.height + 0.1
    )
      throw new Error(
        'Un oggetto supera i limiti della pagina. Spostalo, ridimensionalo o aumenta il formato.',
      );
  }
  if (
    !['Arial', 'Georgia', 'Verdana', 'Times New Roman'].includes(attrs.fontFamily) ||
    !/^\d+(\.\d+)?pt$/.test(attrs.fontSize) ||
    Number.parseFloat(attrs.fontSize) < 3 ||
    Number.parseFloat(attrs.fontSize) > 200 ||
    !/^#[a-f\d]{6}$/i.test(attrs.color) ||
    !['left', 'center', 'right', 'justify'].includes(attrs.textAlign) ||
    !Number.isFinite(attrs.lineHeight) ||
    attrs.lineHeight < 0.8 ||
    attrs.lineHeight > 4 ||
    !['bold', 'italic', 'underline', 'keepRatio'].every((k) => typeof attrs[k] === 'boolean')
  )
    throw new Error('Stile dell’oggetto non valido.');
}
export function clampGeometry(attrs, size) {
  const width = Math.max(8, Math.min(attrs.width, Math.floor(size.width))),
    height = Math.max(8, Math.min(attrs.height, Math.floor(size.height)));
  return {
    ...attrs,
    width,
    height,
    x: Math.max(0, Math.min(attrs.x, Math.floor(size.width - width))),
    y: Math.max(0, Math.min(attrs.y, Math.floor(size.height - height))),
  };
}
export function moveGeometry(attrs, dx, dy, size) {
  return clampGeometry(
    { ...attrs, x: attrs.x + Math.round(dx), y: attrs.y + Math.round(dy) },
    size,
  );
}
export function resizeGeometry(attrs, dx, dy, edge, size, keepRatio = attrs.keepRatio) {
  let left = attrs.x,
    top = attrs.y,
    right = attrs.x + attrs.width,
    bottom = attrs.y + attrs.height;
  if (edge.includes('e')) right = Math.max(left + 8, Math.min(size.width, right + dx));
  if (edge.includes('s')) bottom = Math.max(top + 8, Math.min(size.height, bottom + dy));
  if (edge.includes('w')) left = Math.min(right - 8, Math.max(0, left + dx));
  if (edge.includes('n')) top = Math.min(bottom - 8, Math.max(0, top + dy));
  if (keepRatio) {
    const ratio = attrs.width / attrs.height;
    let w = right - left,
      h = bottom - top;
    if (edge === 'n' || edge === 's') w = h * ratio;
    else if (edge === 'e' || edge === 'w') h = w / ratio;
    else if (Math.abs(dx / attrs.width) >= Math.abs(dy / attrs.height)) h = w / ratio;
    else w = h * ratio;
    const factor = Math.min(
      1,
      (edge.includes('w') ? right : size.width - left) / w,
      (edge.includes('n') ? bottom : size.height - top) / h,
    );
    w *= factor;
    h *= factor;
    if (w < 8 || h < 8) return { ...attrs };
    if (edge.includes('w')) left = right - w;
    else right = left + w;
    if (edge.includes('n')) top = bottom - h;
    else bottom = top + h;
  }
  return { ...attrs, x: left, y: top, width: right - left, height: bottom - top };
}
export function objectStyle(a) {
  return `--object-width:${a.width}px;--object-height:${a.height}px;left:${a.x}px;top:calc(${a.y}px + ${a.page} * var(--page-step,0px));width:${a.width}px;height:${a.height}px;z-index:${a.z};font-family:${a.fontFamily};font-size:${a.fontSize};line-height:${a.lineHeight};color:${a.color};text-align:${a.textAlign};font-weight:${a.bold ? '700' : '400'};font-style:${a.italic ? 'italic' : 'normal'};text-decoration:${a.underline ? 'underline' : 'none'};`;
}
