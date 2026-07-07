/**
 * Entry point bundled by esbuild into a self-contained IIFE.
 * Sets up MathJax with liteAdaptor and exposes a startup-style global
 * `MathJax` object that mathjax-bridge.js (loaded after this bundle)
 * can use without any imports of its own.
 *
 * Exposed API on globalThis.MathJax:
 *   startup.adaptor   – liteAdaptor instance
 *   startup.document  – mathjax document
 *   tex2svg(latex, convertOptions) → raw SVG markup string (throws on error)
 */

import { liteAdaptor } from 'mathjax-full/js/adaptors/liteAdaptor.js';
import { RegisterHTMLHandler } from 'mathjax-full/js/handlers/html.js';
import { TeX } from 'mathjax-full/js/input/tex.js';
import { SVG } from 'mathjax-full/js/output/svg.js';
import { mathjax } from 'mathjax-full/js/mathjax.js';
import { AllPackages } from 'mathjax-full/js/input/tex/AllPackages.js';

const adaptor = liteAdaptor();
RegisterHTMLHandler(adaptor);

const tex = new TeX({ packages: AllPackages });
const svgOutput = new SVG({ fontCache: 'local' });
const doc = mathjax.document('', { InputJax: tex, OutputJax: svgOutput });

// Startup-style global so mathjax-bridge.js can call MathJax.tex2svg()
// without importing anything.
globalThis.MathJax = {
  startup: {
    adaptor,
    document: doc,
  },
  tex2svg(latex, options) {
    const node = doc.convert(latex, options || {});
    return adaptor.outerHTML(node);
  },
};
