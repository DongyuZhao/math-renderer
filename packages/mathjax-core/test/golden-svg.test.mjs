import assert from "node:assert/strict";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

import { loadFormulas } from "../fixtures/formulas.mjs";
import { renderNativeSvg } from "./support/render-native.mjs";

// Golden-SVG baseline: asserts mathjax-core's generated runtime produces the
// exact SVG markup we committed for every formula in the corpus. This is the
// single source of truth for SVG *generation* correctness across
// React/Swift/Android (all three render this same markup); each platform then
// layers pixel snapshots for *rasterization*.
//
// Rendered through mathjax-core's own generated bundle (the one Swift/Android
// load), so this test depends on no consumer package.
//
// Regenerate goldens with: npm run test:shared:update

const goldenDir = join(dirname(fileURLToPath(import.meta.url)), "golden", "svg");
const update =
    process.env.UPDATE_MATH_SNAPSHOTS === "1" || process.argv.includes("--test-update-snapshots");

const formulas = loadFormulas();

for (const formula of formulas) {
    test(`golden SVG: ${formula.id}`, () => {
        const rendered = renderNativeSvg(formula.tex, formula.options);

        assert.equal(
            rendered.ok,
            true,
            `${formula.id} failed to render: ${rendered.error ?? rendered.fallbackText}`
        );
        assert.doesNotMatch(
            rendered.markup,
            /data-mml-node=["']merror["']/i,
            `${formula.id} produced a MathJax error node`
        );

        const goldenPath = join(goldenDir, `${formula.id}.svg`);
        const actual = `${rendered.markup}\n`;

        if (update) {
            mkdirSync(goldenDir, { recursive: true });
            writeFileSync(goldenPath, actual);
            return;
        }

        assert.ok(
            existsSync(goldenPath),
            `Missing golden for ${formula.id}. Run: npm run test:shared:update`
        );
        assert.equal(actual, readFileSync(goldenPath, "utf8"), `${formula.id}.svg`);
    });
}

// Guard against orphaned goldens when a formula is renamed/removed.
test("golden SVG directory has no stale files", () => {
    if (update || !existsSync(goldenDir)) {
        return;
    }
    const expected = new Set(formulas.map((formula) => `${formula.id}.svg`));
    const stale = readdirSync(goldenDir).filter(
        (name) => name.endsWith(".svg") && !expected.has(name)
    );
    assert.deepEqual(stale, [], `Stale golden SVG files: ${stale.join(", ")}`);
});
