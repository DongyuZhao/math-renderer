import { expect, test } from "vitest";
import { render } from "vitest-browser-react";

import { parseFormulas } from "../../mathjax-core/fixtures/parse-formulas.mjs";
import rawTsv from "../../mathjax-core/fixtures/common-formulas.tsv?raw";
import { Math } from "../src/index.js";

// Pixel (rasterization) snapshots — one per corpus formula, matching the shared
// golden SVGs 1:1 (baseline `<id>-<browser>-<platform>.png` ↔ golden `<id>.svg`).
// Rendered in a real browser via Vitest Browser Mode; the MathJax SVG uses
// embedded vector glyphs, so a white/black target is deterministic per machine.
//
// Record baselines: npm run test:pixel:update    Verify: npm run test:pixel

const formulas = parseFormulas(rawTsv);

for (const formula of formulas) {
    test(`pixel: ${formula.id}`, async () => {
        const screen = await render(
            <div
                data-testid="pixel-target"
                style={{
                    display: "inline-block",
                    padding: 16,
                    background: "#ffffff",
                    color: "#000000"
                }}
            >
                <Math
                    tex={formula.tex}
                    displayMode={formula.displayMode}
                    fontSize={formula.fontSize}
                    scale={formula.scale}
                />
            </div>
        );

        const target = screen.getByTestId("pixel-target");
        // Wait for the async render to swap the pending state for real SVG.
        await expect.poll(() => target.element().querySelector("svg")).not.toBeNull();

        await expect.element(target).toMatchScreenshot(formula.id, {
            comparatorName: "pixelmatch",
            comparatorOptions: { allowedMismatchedPixelRatio: 0.02 }
        });
    });
}
