// Persistent groups are flat membership sets. Children retain their coordinates,
// content and layer order; printing does not need to reconstruct nested transforms.
import { groupBounds } from './alignment.js';

export function expandGroupIds(objects, ids) {
  const selected = new Set(ids);
  const groups = new Set(
    objects.filter((a) => selected.has(a.id) && a.groupId).map((a) => a.groupId),
  );
  return objects.filter((a) => selected.has(a.id) || groups.has(a.groupId)).map((a) => a.id);
}
export function selectionGroup(objects) {
  const id = objects[0]?.groupId;
  return objects.length > 1 && id && objects.every((a) => a.groupId === id) ? id : null;
}
export function validateGroups(objects) {
  const groups = new Map();
  for (const a of objects) {
    if (!a.groupId) continue;
    if (typeof a.groupId !== 'string' || !/^grp-[a-zA-Z0-9_-]{1,64}$/.test(a.groupId))
      throw Error('Identificatore del gruppo non valido.');
    const members = groups.get(a.groupId) ?? [];
    members.push(a);
    groups.set(a.groupId, members);
  }
  for (const members of groups.values())
    if (members.length < 2 || new Set(members.map((a) => a.page)).size !== 1)
      throw Error('Un gruppo deve contenere almeno due elementi della stessa pagina.');
}
export function alignmentObjects(objects) {
  const result = [],
    groups = new Map();
  for (const a of objects) {
    if (!a.groupId) result.push(a.kind === 'group' ? { ...a, edgesOnly: true } : a);
    else {
      const members = groups.get(a.groupId) ?? [];
      members.push(a);
      groups.set(a.groupId, members);
    }
  }
  for (const [id, members] of groups) result.push({ ...groupBounds(members), id, edgesOnly: true });
  return result;
}
