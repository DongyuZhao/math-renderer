'use strict';
const { renderToSVG } = require('..');
const assert = require('assert');

// Inline formula
{
  const result = renderToSVG('x^2 + y^2 = r^2');
  assert.strictEqual(result.ok, true, 'inline: ok should be true');
  assert.ok(result.markup.includes('<svg'), 'inline: markup should contain <svg');
  assert.ok(result.viewBox, 'inline: viewBox should be present');
  console.log('✅  inline formula ok, viewBox:', result.viewBox);
}

// Display (standalone) formula
{
  const result = renderToSVG('\\int_0^\\infty e^{-x^2} dx = \\frac{\\sqrt{\\pi}}{2}', { standalone: true });
  assert.strictEqual(result.ok, true, 'display: ok should be true');
  assert.ok(result.markup.includes('<svg'), 'display: markup should contain <svg');
  console.log('✅  display formula ok');
}

// Invalid LaTeX — should return error gracefully
{
  const result = renderToSVG('\\unknowncommand{bad}');
  // MathJax may still produce markup for unknown commands; just check it doesn't throw
  assert.ok(result !== null && typeof result === 'object', 'error path: result is object');
  console.log('✅  invalid LaTeX handled gracefully, ok:', result.ok);
}

console.log('\nAll tests passed.');
