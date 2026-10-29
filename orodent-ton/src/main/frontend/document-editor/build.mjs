import { build } from 'esbuild';
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const output = resolve(root, '../../resources/ton/document-editor');
const check = process.argv.includes('--check');
const result = await build({
  absWorkingDir: root,
  entryPoints: ['editor.js'],
  bundle: true,
  write: false,
  minify: true,
  format: 'iife',
  target: 'safari15',
  charset: 'utf8',
  legalComments: 'inline',
  metafile: true,
});
const script = Buffer.from(result.outputFiles[0].contents).toString('utf8');
const css = await readFile(join(root, 'editor.css'), 'utf8');
if (/<\/script/i.test(script) || /<\/style/i.test(css)) {
  throw new Error('Unsafe closing tag in embedded editor resource.');
}
const scriptHash = createHash('sha256').update(script).digest('base64');
const template = await readFile(join(root, 'index.html'), 'utf8');
const page = template.replace('__EDITOR_SCRIPT_SHA256__', scriptHash)
  .replace('<!-- LOCAL_STYLE -->', () => `<style>${css}</style>`)
  .replace('<!-- LOCAL_SCRIPT -->', () => `<script>${script}</script>`);
const files = new Map([['index.html', Buffer.from(page)]]);

// Attribute the actual runtime packages included by the bundler, not dev tools.
const names = new Set();
for (const input of Object.keys(result.metafile.inputs)) {
  const bits = input.replaceAll('\\', '/').split('/');
  const start = bits.lastIndexOf('node_modules') + 1;
  if (!start) continue;
  names.add(bits[start].startsWith('@') ? bits.slice(start, start + 2).join('/') : bits[start]);
}
const packages = [];
const notices = ['Third-party software distributed with Orodent-EcoS document editor.',
  'These files are bundled locally. No CDN or online editor service is used.', ''];
for (const name of [...names].sort()) {
  const directory = join(root, 'node_modules', name);
  const pkg = JSON.parse(await readFile(join(directory, 'package.json'), 'utf8'));
  const licenseFiles = (await readdir(directory)).filter(n => /^(licen[cs]e|copying)(\.|$)/i.test(n)).sort();
  if (!licenseFiles.length) throw new Error(`License text missing for ${name}`);
  packages.push({ name, version: pkg.version, license: pkg.license });
  notices.push(`===== ${name}@${pkg.version} (${pkg.license}) =====`);
  for (const license of licenseFiles) notices.push((await readFile(join(directory, license), 'utf8')).trim(), '');
}
files.set('THIRD_PARTY_NOTICES.txt', Buffer.from(notices.join('\n')));
const manifest = {
  schemaVersion: 1,
  tiptapVersion: '3.32.1',
  packages,
  files: Object.fromEntries([...files].map(([name, bytes]) => [name, {
    bytes: bytes.length,
    sha256: createHash('sha256').update(bytes).digest('hex'),
  }])),
};
files.set('bundle-manifest.json', Buffer.from(JSON.stringify(manifest, null, 2) + '\n'));
if (!check) await mkdir(output, { recursive: true });
for (const [name, bytes] of files) {
  if (check) {
    if (!(await readFile(join(output, name))).equals(bytes)) {
      throw new Error(`Bundled asset differs from source: ${name}. Run npm run build.`);
    }
  } else await writeFile(join(output, name), bytes);
}
console.log(`${check ? 'Verified' : 'Built'} ${files.size} offline resources, ${packages.length} runtime packages.`);
