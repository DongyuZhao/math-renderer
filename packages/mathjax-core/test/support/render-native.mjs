// Renders LaTeX to SVG through mathjax-core's OWN generated runtime — the exact
// bundle Swift (JavaScriptCore) and Android (QuickJS) load — by evaluating it in
// a Node vm sandbox. This keeps the shared golden-SVG baseline self-contained:
// it depends only on mathjax-core's generated artifacts, not on any consumer
// package (e.g. react-math).

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const generatedDir = join(dirname(fileURLToPath(import.meta.url)), "../../generated");

let cachedContext;

function runtimeContext() {
    if (cachedContext) {
        return cachedContext;
    }
    const runtime = readFileSync(join(generatedDir, "mathjax-core-runtime.js"), "utf8");
    const bridge = readFileSync(join(generatedDir, "latex-to-svg.js"), "utf8");

    const sandbox = {};
    sandbox.global = sandbox;
    sandbox.globalThis = sandbox;
    cachedContext = vm.createContext(sandbox);
    // Both files expect `this` to be the global object.
    vm.runInContext(`(function () {\n${runtime}\n}).call(this);`, cachedContext);
    vm.runInContext(`(function () {\n${bridge}\n}).call(this);`, cachedContext);
    return cachedContext;
}

/**
 * @param {string} tex
 * @param {{displayMode?: "inline"|"block", fontSize?: number, scale?: number}} options
 * @returns {{ok: boolean, markup: string, viewBox: object, fallbackText: string|null, error: string|null}}
 */
export function renderNativeSvg(tex, options = {}) {
    const { displayMode = "inline", fontSize, scale } = options;
    const bridgeOptions = { standalone: displayMode === "block", fontSize, scale };
    const json = vm.runInContext(
        `MathRenderLatexToSvg.renderJSON(${JSON.stringify(tex)}, ${JSON.stringify(bridgeOptions)})`,
        runtimeContext()
    );
    return JSON.parse(json);
}
