// All selection geometry uses reference pixels on the unscaled canvas.
export function selectionRect(a, b) {
  return {
    x: Math.min(a.x, b.x),
    y: Math.min(a.y, b.y),
    width: Math.abs(a.x - b.x),
    height: Math.abs(a.y - b.y),
  };
}
export function insideSelection(rect, object, pageHeight) {
  const y = object.y + object.page * (pageHeight + 24);
  return (
    object.x >= rect.x - 0.1 &&
    y >= rect.y - 0.1 &&
    object.x + object.width <= rect.x + rect.width + 0.1 &&
    y + object.height <= rect.y + rect.height + 0.1
  );
}
export function groupDelta(objects, dx, dy, size, round = true) {
  if (!objects.length) return { x: 0, y: 0 };
  const minX = Math.max(...objects.map((a) => -a.x)),
    maxX = Math.min(...objects.map((a) => size.width - a.x - a.width)),
    minY = Math.max(...objects.map((a) => -a.y)),
    maxY = Math.min(...objects.map((a) => size.height - a.y - a.height));
  return {
    x: Math.max(minX, Math.min(maxX, round ? Math.round(dx) : dx)),
    y: Math.max(minY, Math.min(maxY, round ? Math.round(dy) : dy)),
  };
}
