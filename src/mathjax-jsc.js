/**
 * Entry point bundled by esbuild into a self-contained IIFE.
 * When evaluated inside JavaScriptCore the global `MathRendererMathJax`
 * object is exposed with a single `renderJSON(latex, options)` method.
 *
 * options shape:
 *   { standalone: boolean, fontSize: number, scale: number }
 *
 * Return value (JSON-serialised string):
 *   { markup: string, viewBox: string|null, ok: boolean, error: string|null }
 */

import { liteAdaptor } from 'mathjax-full/js/adaptors/liteAdaptor.js';
import { RegisterHTMLHandler } from 'mathjax-full/js/handlers/html.js';
import { TeX } from 'mathjax-full/js/input/tex.js';
import { SVG } from 'mathjax-full/js/output/svg.js';
import { mathjax } from 'mathjax-full/js/mathjax.js';
import { AllPackages } from 'mathjax-full/js/input/tex/AllPackages.js';

// ── Set up MathJax document (created once, reused for all renders) ──────────

const adaptor = liteAdaptor();
RegisterHTMLHandler(adaptor);

const tex = new TeX({ packages: AllPackages });
const svgOutput = new SVG({ fontCache: 'local' });
const doc = mathjax.document('', { InputJax: tex, OutputJax: svgOutput });

// ── Helpers ──────────────────────────────────────────────────────────────────

function extractViewBox(markup) {
  const m = markup.match(/viewBox\s*=\s*["']([^"']+)["']/i);
  return m ? m[1] : null;
}

function sanitize(markup) {
  return markup
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/\son[a-zA-Z]+\s*=\s*"[^"]*"/g, '')
    .replace(/\son[a-zA-Z]+\s*=\s*'[^']*'/g, '')
    .replace(/\son[a-zA-Z]+\s*=\s*[^>\s]+/g, '')
    .replace(/javascript:/gi, '');
}

// ── Public API ───────────────────────────────────────────────────────────────

function renderJSON(latex, options) {
  const opts = options || {};
  const em = typeof opts.fontSize === 'number' && opts.fontSize > 0 ? opts.fontSize : 16;
  const ex = em / 2;
  const containerWidth = 1200;
  const display = opts.standalone === true;

  try {
    const node = doc.convert(latex, {
      display,
      em,
      ex,
      containerWidth,
    });

    const markup = sanitize(adaptor.outerHTML(node));
    const viewBox = extractViewBox(markup);

    return JSON.stringify({
      markup,
      viewBox,
      ok: true,
      error: null,
    });
  } catch (err) {
    const fallbackText = display ? '$$' + latex + '$$' : '$' + latex + '$';
    return JSON.stringify({
      markup: '',
      viewBox: null,
      ok: false,
      error: String(err && err.message ? err.message : err),
      fallbackText,
    });
  }
}

// ── Expose on globalThis so JSC can call it ──────────────────────────────────

globalThis.MathRendererMathJax = { renderJSON };
