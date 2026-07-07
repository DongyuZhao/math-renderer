import { test } from 'node:test';
import assert from 'node:assert/strict';
import { renderToSVG } from '../index.js';

// ── Inline formula ────────────────────────────────────────────────────────────

test('inline formula: ok is true', () => {
  const result = renderToSVG('x^2 + y^2 = r^2');
  assert.equal(result.ok, true);
});

test('inline formula: markup contains <svg', () => {
  const result = renderToSVG('x^2 + y^2 = r^2');
  assert.ok(result.markup.includes('<svg'));
});

test('inline formula: viewBox is present', () => {
  const result = renderToSVG('x^2 + y^2 = r^2');
  assert.ok(result.viewBox, 'viewBox should be a non-empty string');
});

// ── Display (standalone) formula ──────────────────────────────────────────────

test('display formula: ok is true', () => {
  const result = renderToSVG('\\int_0^\\infty e^{-x^2} dx = \\frac{\\sqrt{\\pi}}{2}',
    { standalone: true });
  assert.equal(result.ok, true);
});

test('display formula: markup contains <svg', () => {
  const result = renderToSVG('E = mc^2', { standalone: true });
  assert.ok(result.markup.includes('<svg'));
});

// ── Custom options ────────────────────────────────────────────────────────────

test('custom fontSize option: ok is true', () => {
  const result = renderToSVG('a^2 + b^2 = c^2', { fontSize: 24 });
  assert.equal(result.ok, true);
  assert.ok(result.markup.includes('<svg'));
});

test('custom scale option: ok is true', () => {
  const result = renderToSVG('\\frac{1}{2}', { scale: 2 });
  assert.equal(result.ok, true);
});

test('all options combined: ok is true', () => {
  const result = renderToSVG('\\sum_{n=1}^{\\infty} \\frac{1}{n^2}',
    { standalone: true, fontSize: 20, scale: 2 });
  assert.equal(result.ok, true);
  assert.ok(result.markup.includes('<svg'));
});

// ── Return-value shape ────────────────────────────────────────────────────────

test('result is always an object with ok, markup, and viewBox keys', () => {
  const result = renderToSVG('x');
  assert.equal(typeof result, 'object');
  assert.ok(result !== null);
  assert.ok('ok' in result);
  assert.ok('markup' in result);
  assert.ok('viewBox' in result);
});

test('markup does not contain <script tags (sanitisation)', () => {
  const result = renderToSVG('x^2');
  assert.ok(!result.markup.toLowerCase().includes('<script'));
});

test('markup does not contain javascript: (sanitisation)', () => {
  const result = renderToSVG('x^2');
  assert.ok(!result.markup.toLowerCase().includes('javascript:'));
});

// ── Error / unknown command handling ─────────────────────────────────────────

test('unknown LaTeX command: result is an object and does not throw', () => {
  const result = renderToSVG('\\unknowncommand{bad}');
  assert.equal(typeof result, 'object');
  assert.ok(result !== null);
});

test('empty string: result is an object and does not throw', () => {
  const result = renderToSVG('');
  assert.equal(typeof result, 'object');
  assert.ok(result !== null);
});

