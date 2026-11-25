import { watchPaper } from './paper-layout.js';
export function mountEditorZoom(model) {
  const select = document.getElementById('zoom'), canvas = document.getElementById('canvas');
  const stopSizing = watchPaper(model.zoomValue);
  const levels = [25, 33, 40, 50, 60, 65, 70, 75, 80, 85, 90, 95, 100, 110, 120, 125, 150, 175, 200];
  function sync() {
    const percent = Math.round(model.zoomValue() * 100);
    if (!levels.includes(percent)) levels.push(percent);
    select.replaceChildren(...levels.sort((a, b) => a - b).map(value => {
      const option = document.createElement('option'); option.value = String(value / 100); option.textContent = `${value}%`; return option;
    }));
    select.value = String(percent / 100);
  }
  function change() { model.zoom(Number(select.value)); sync(); }
  let accumulated = 0;
  function wheel(event) {
    if (!event.ctrlKey) return;
    event.preventDefault();
    if (!document.getElementById('preview-dialog').hidden || !document.getElementById('table-dialog').hidden) return;
    accumulated += event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? canvas.clientHeight : 1);
    if (Math.abs(accumulated) < 40) return;
    const next = Math.max(.25, Math.min(2, Math.round((model.zoomValue() + (accumulated < 0 ? .05 : -.05)) * 100) / 100));
    accumulated = 0; model.zoom(next, { x: event.clientX, y: event.clientY }); sync();
  }
  select.addEventListener('change', change);
  document.addEventListener('wheel', wheel, { capture: true, passive: false });
  sync();
  return () => { stopSizing(); select.removeEventListener('change', change); document.removeEventListener('wheel', wheel, true); };
}
