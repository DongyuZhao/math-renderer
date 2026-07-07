/**
 * Bridge layer loaded after mathjax-renderer.js (the bundled engine).
 * No imports — depends only on the global MathJax object that the bundle
 * exposes via globalThis.MathJax.
 *
 * Exposes on globalThis.MathRendererMathJax:
 *   renderJSON(latex, options) → JSON string
 *
 * options shape:
 *   { standalone: boolean, fontSize: number, scale: number }
 *
 * Return value (JSON-serialised string):
 *   { markup: string, viewBox: string|null, ok: boolean, error: string|null }
 */

// ── Helpers ──────────────────────────────────────────────────────────────────

function extractViewBox(markup) {
  var m = markup.match(/viewBox\s*=\s*["']([^"']+)["']/i);
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
  var opts = options || {};
  var em = typeof opts.fontSize === 'number' && opts.fontSize > 0 ? opts.fontSize : 16;
  var ex = em / 2;
  var containerWidth = 1200;
  var display = opts.standalone === true;

  try {
    var rawMarkup = MathJax.tex2svg(latex, { display: display, em: em, ex: ex, containerWidth: containerWidth });
    var markup = sanitize(rawMarkup);
    var viewBox = extractViewBox(markup);

    return JSON.stringify({
      markup: markup,
      viewBox: viewBox,
      ok: true,
      error: null,
    });
  } catch (err) {
    var fallbackText = display ? '$$' + latex + '$$' : '$' + latex + '$';
    return JSON.stringify({
      markup: '',
      viewBox: null,
      ok: false,
      error: String(err && err.message ? err.message : err),
      fallbackText: fallbackText,
    });
  }
}

// ── Expose on globalThis so JSC / JavaScriptEngine can call it ───────────────

globalThis.MathRendererMathJax = { renderJSON: renderJSON };
