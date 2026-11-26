// Pure geometry: reference pixels, with a screen-pixel tolerance supplied by the caller.
function marks(start, length) {
  return [start, start + length / 2, start + length];
}
export function alignmentTargets(objects, page, margin = 0) {
  const x = [0, page.width / 2, page.width].map((value) => ({ value })),
    y = [0, page.height / 2, page.height].map((value) => ({ value }));
  if (margin > 0) {
    x.push({ value: margin }, { value: page.width - margin });
    y.push({ value: margin }, { value: page.height - margin });
  }
  for (const a of objects) {
    x.push(
      ...marks(a.x, a.width).map((value) => ({
        value,
        id: a.id,
      })),
    );
    y.push(
      ...marks(a.y, a.height).map((value) => ({
        value,
        id: a.id,
      })),
    );
  }
  return { x, y };
}
function nearest(anchors, targets, tolerance, valid) {
  let best = null;
  for (const anchor of anchors)
    for (const target of targets) {
      const delta = target.value - anchor;
      if (
        Math.abs(delta) <= tolerance &&
        (!best || Math.abs(delta) < Math.abs(best.delta) - 1e-7) &&
        valid(delta)
      )
        best = { delta, value: target.value };
    }
  return best;
}
export function snapMove(a, targets, size, tolerance) {
  const x = nearest(
      marks(a.x, a.width),
      targets.x,
      tolerance,
      (d) => a.x + d >= 0 && a.x + a.width + d <= size.width + 0.001,
    ),
    y = nearest(
      marks(a.y, a.height),
      targets.y,
      tolerance,
      (d) => a.y + d >= 0 && a.y + a.height + d <= size.height + 0.001,
    );
  return {
    attrs: { ...a, x: a.x + (x?.delta ?? 0), y: a.y + (y?.delta ?? 0) },
    guides: [
      ...(x ? [{ axis: 'x', value: x.value }] : []),
      ...(y ? [{ axis: 'y', value: y.value }] : []),
    ],
  };
}
export function snapResize(a, edge, targets, size, tolerance) {
  const horizontal = edge.includes('w') ? 'w' : edge.includes('e') ? 'e' : null,
    vertical = edge.includes('n') ? 'n' : edge.includes('s') ? 's' : null;
  const validX = (d) =>
      horizontal === 'w'
        ? a.x + d >= 0 && a.width - d >= 8
        : a.width + d >= 8 && a.x + a.width + d <= size.width + 0.001,
    validY = (d) =>
      vertical === 'n'
        ? a.y + d >= 0 && a.height - d >= 8
        : a.height + d >= 8 && a.y + a.height + d <= size.height + 0.001;
  const x = horizontal
      ? nearest([horizontal === 'w' ? a.x : a.x + a.width], targets.x, tolerance, validX)
      : null,
    y = vertical
      ? nearest([vertical === 'n' ? a.y : a.y + a.height], targets.y, tolerance, validY)
      : null;
  const result = { ...a };
  if (x) {
    if (horizontal === 'w') {
      result.x += x.delta;
      result.width -= x.delta;
    } else result.width += x.delta;
  }
  if (y) {
    if (vertical === 'n') {
      result.y += y.delta;
      result.height -= y.delta;
    } else result.height += y.delta;
  }
  return {
    attrs: result,
    guides: [
      ...(x ? [{ axis: 'x', value: x.value }] : []),
      ...(y ? [{ axis: 'y', value: y.value }] : []),
    ],
  };
}
export function groupBounds(objects) {
  if (!objects.length || new Set(objects.map((a) => a.page)).size !== 1) return null;
  const x = Math.min(...objects.map((a) => a.x)),
    y = Math.min(...objects.map((a) => a.y));
  return {
    x,
    y,
    width: Math.max(...objects.map((a) => a.x + a.width)) - x,
    height: Math.max(...objects.map((a) => a.y + a.height)) - y,
    page: objects[0].page,
  };
}
