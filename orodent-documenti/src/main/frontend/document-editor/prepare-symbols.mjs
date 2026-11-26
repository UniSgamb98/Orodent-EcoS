// Development-only artwork preparation. ImageMagick removes transparent canvas without resampling.
// Original bytes remain in the catalogue so editable old templates can be adjusted exactly.
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = dirname(fileURLToPath(import.meta.url)),
  catalogue = join(root, 'symbols.json');
const entries = JSON.parse(readFileSync(catalogue, 'utf8')),
  scratch = mkdtempSync(join(tmpdir(), 'ecos-symbols-'));
try {
  for (const entry of entries) {
    const original = entry.legacy?.src ?? entry.src,
      input = join(scratch, entry.key + '.png');
    writeFileSync(input, Buffer.from(original.split(',')[1], 'base64'));
    const info = execFileSync('identify', ['-format', '%w %h %@', input], {
      encoding: 'utf8',
    }).trim();
    const match = /^(\d+) (\d+) (\d+)x(\d+)\+(\d+)\+(\d+)$/.exec(info);
    if (!match) throw Error('Unexpected transparent bounds for ' + entry.key + ': ' + info);
    const [, w, h, cw, ch, x, y] = match.map(Number),
      output = join(root, 'symbols', entry.key + '.png');
    execFileSync('convert', [
      input,
      '-trim',
      '+repage',
      '-depth',
      '8',
      '-define',
      'png:color-type=6',
      '-define',
      'png:exclude-chunk=date,time',
      output,
    ]);
    entry.src = 'data:image/png;base64,' + readFileSync(output).toString('base64');
    entry.width = cw;
    entry.height = ch;
    entry.legacy = { src: original, width: w, height: h, bounds: { x, y, width: cw, height: ch } };
    console.log(`${entry.key}: ${w}×${h} → ${cw}×${ch}`);
  }
  writeFileSync(catalogue, JSON.stringify(entries) + '\n');
} finally {
  rmSync(scratch, { recursive: true, force: true });
}
