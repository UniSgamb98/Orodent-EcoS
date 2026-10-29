// CSS transforms have consistent client coordinates in JavaFX WebKit and browsers.
// CSS `zoom` does not, which misplaces pointer interactions in older WebKit builds.
export function sizePaper(zoom) {
  const space = document.getElementById('paper-space'), paper = document.getElementById('paper');
  const width = paper.offsetWidth * zoom, height = Math.ceil(paper.offsetHeight * zoom) + 43;
  if (space.style.height !== `${height}px`) space.style.height = `${height}px`;
  space.style.minWidth = `${Math.ceil(width) + 50}px`;
  const left = Math.max(25, (space.clientWidth - width) / 2);
  if (paper.style.left !== `${left}px`) paper.style.left = `${left}px`;
}
export function watchPaper(zoom) {
  let frame, closed = false;
  const schedule = () => {
    if (closed || frame) return;
    frame = requestAnimationFrame(() => { frame = null; if (!closed) sizePaper(zoom()); });
  };
  const observer = new ResizeObserver(schedule);
  observer.observe(document.getElementById('paper')); observer.observe(document.getElementById('canvas'));
  schedule();
  return () => { closed = true; cancelAnimationFrame(frame); observer.disconnect(); };
}
