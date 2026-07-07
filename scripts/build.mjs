#!/usr/bin/env node
/**
 * Bundles src/mathjax-jsc.js into a single IIFE that can be evaluated inside
 * JavaScriptCore (no require, no Node built-ins).
 *
 * Output: swift/Sources/MathRenderer/Resources/MathJax/mathjax-renderer.js
 */

import { build } from 'esbuild';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { mkdirSync } from 'fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = join(__dirname, '..');
const outDir = join(
  root,
  'swift',
  'Sources',
  'MathRenderer',
  'Resources',
  'MathJax'
);

mkdirSync(outDir, { recursive: true });

await build({
  entryPoints: [join(root, 'src', 'mathjax-jsc.js')],
  bundle: true,
  format: 'iife',
  globalName: '_MathRendererMathJaxModule',
  platform: 'neutral',   // no Node or browser built-in assumptions
  target: ['es2017'],
  outfile: join(outDir, 'mathjax-renderer.js'),
  minify: false,
  // JavaScriptCore has no process/Buffer; stub them out
  define: {
    'process.env.NODE_ENV': '"production"',
  },
  banner: {
    js: '/* Auto-generated — do not edit. Run: npm run build */',
  },
});

console.log('✅  mathjax-renderer.js written to', outDir);
