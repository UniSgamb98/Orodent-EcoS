import test from 'node:test';
import assert from 'node:assert/strict';
import { alignmentTargets, snapMove, snapResize, groupBounds } from './alignment.js';
import { groupDelta } from './selection-geometry.js';
import { propertyProfile, objectName } from './object-properties-profile.js';
const size = { width: 200, height: 150 };
test('nearby edges align on both axes without changing dimensions', () => {
  const targets = alignmentTargets(
    [{ id: 'other', x: 50, y: 40, width: 20, height: 20 }],
    size,
    10,
  );
  const result = snapMove({ x: 39, y: 29, width: 10, height: 10, page: 0 }, targets, size, 3);
  assert.equal(result.attrs.x, 40);
  assert.equal(result.attrs.y, 30);
  assert.equal(result.attrs.width, 10);
  assert.equal(result.attrs.height, 10);
  assert.deepEqual(result.guides, [
    { axis: 'x', value: 50 },
    { axis: 'y', value: 40 },
  ]);
});
test('the screen threshold remains small at high zoom and fractional centres stay precise', () => {
  const targets = { x: [{ value: 50 }], y: [] },
    a = { x: 36, y: 30, width: 10, height: 10 };
  assert.equal(snapMove(a, targets, size, 6 / 2).attrs.x, 36);
  assert.equal(snapMove(a, targets, size, 6).attrs.x, 40);
  const centred = snapMove(
    { x: 54, y: 20, width: 20, height: 10 },
    { x: [{ value: 65.5 }], y: [] },
    size,
    3,
  );
  assert.equal(centred.attrs.x, 55.5);
  assert.deepEqual(groupDelta([centred.attrs], 0.35, 0, size, false), { x: 0.35, y: 0 });
});
test('resize snaps only the dragged edges and never crosses the minimum size or page boundary', () => {
  const targets = { x: [{ value: 50 }], y: [{ value: 25 }] };
  const result = snapResize({ x: 51, y: 30, width: 29, height: 20 }, 'w', targets, size, 3);
  assert.equal(result.attrs.x, 50);
  assert.equal(result.attrs.width, 30);
  assert.equal(result.attrs.y, 30);
  assert.equal(
    snapResize({ x: 51, y: 30, width: 8, height: 20 }, 'w', { x: [{ value: 53 }], y: [] }, size, 3)
      .attrs.width,
    8,
  );
  assert.equal(
    snapMove({ x: 1, y: 20, width: 10, height: 10 }, { x: [{ value: -1 }], y: [] }, size, 3).attrs
      .x,
    1,
  );
});
test('group bounding boxes and targets include page, margins and centres without mixing pages', () => {
  assert.deepEqual(
    groupBounds([
      { x: 10, y: 20, width: 30, height: 15, page: 0 },
      { x: 60, y: 50, width: 20, height: 10, page: 0 },
    ]),
    { x: 10, y: 20, width: 70, height: 40, page: 0 },
  );
  assert.equal(
    groupBounds([
      { x: 0, y: 0, width: 10, height: 10, page: 0 },
      { x: 0, y: 0, width: 10, height: 10, page: 1 },
    ]),
    null,
  );
  assert.deepEqual(
    alignmentTargets([], size, 10).x.map((t) => t.value),
    [0, 100, 200, 10, 190],
  );
});
test('capabilities distinguish common typography, free paragraph tools and field identity', () => {
  const text = { attrs: { kind: 'text' } },
    field = { attrs: { kind: 'field' }, firstChild: { attrs: { key: 'height' } } };
  assert.equal(propertyProfile(text).text, true);
  assert.equal(propertyProfile(text).paragraph, true);
  assert.equal(propertyProfile(field).text, true);
  assert.equal(propertyProfile(field).paragraph, false);
  assert.equal(objectName(field), 'Altezza nominale');
  assert.equal(Boolean(propertyProfile({ attrs: { kind: 'image' } }).text), false);
});

test('group targets expose only their outer edges, never member edges or group centres', async () => {
  const { alignmentObjects, validateGroups } = await import('./canvas-groups.js');
  const objects = [
    { id: 'a', groupId: 'grp-test', page: 0, x: 20, y: 30, width: 10, height: 10 },
    { id: 'b', groupId: 'grp-test', page: 0, x: 60, y: 60, width: 20, height: 10 },
  ];
  validateGroups(objects);
  const targets = alignmentTargets(alignmentObjects(objects), size, 10);
  assert.deepEqual(
    targets.x.filter((t) => t.id).map((t) => t.value),
    [20, 80],
  );
  assert.deepEqual(
    targets.y.filter((t) => t.id).map((t) => t.value),
    [30, 70],
  );
  const centres = { x: [{ value: 50 }], y: [] };
  assert.equal(
    snapMove({ x: 39, y: 0, width: 20, height: 10 }, centres, size, 3, true).attrs.x,
    39,
  );
  assert.equal(snapMove({ x: 39, y: 0, width: 20, height: 10 }, centres, size, 3).attrs.x, 40);
  const groupTargets = alignmentTargets([{ x: 40, y: 40, width: 20, height: 20 }], size, 10, true);
  assert.deepEqual(
    groupTargets.x.map((t) => t.value),
    [0, 200, 10, 190, 40, 60],
  );
  assert.throws(() => validateGroups(objects.slice(0, 1)), /almeno due/);
  assert.throws(() => validateGroups([objects[0], { ...objects[1], page: 1 }]), /stessa pagina/);
});
