// JavaFX WebKit can provide an empty `key`, while Chromium provides the modern key name.
export function editorKey(event) {
  if (event.key && event.key !== 'Unidentified') return event.key;
  const code = event.keyCode || event.which;
  const known = {
    8: 'Backspace',
    9: 'Tab',
    13: 'Enter',
    27: 'Escape',
    37: 'ArrowLeft',
    38: 'ArrowUp',
    39: 'ArrowRight',
    40: 'ArrowDown',
    46: 'Delete',
  };
  if (known[code]) return known[code];
  if (code >= 65 && code <= 90) return String.fromCharCode(code).toLowerCase();
  return (
    { Left: 'ArrowLeft', Right: 'ArrowRight', Up: 'ArrowUp', Down: 'ArrowDown' }[
      event.keyIdentifier
    ] ||
    event.keyIdentifier ||
    ''
  );
}
