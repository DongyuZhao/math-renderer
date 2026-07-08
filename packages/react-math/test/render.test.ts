import { describe, expect, it } from "vitest";

import { loadFormulas } from "../../mathjax-core/fixtures/formulas.mjs";
import {
    fallbackTextForMath,
    renderMath,
    renderMathToSvg,
    type MathRenderState
} from "../src/index.js";

async function collectStates(stream: AsyncGenerator<MathRenderState>) {
    const states: MathRenderState[] = [];
    for await (const state of stream) {
        states.push(state);
    }
    return states;
}

describe("renderMathToSvg", () => {
    it("returns sanitized inline SVG", () => {
        const svg = renderMathToSvg("x^2 + y^2 = z^2", {
            displayMode: "inline",
            fontSize: 16,
            scale: 1
        });

        expect(svg.ok).toBe(true);
        expect(svg.markup).toMatch(/<svg/i);
        expect(svg.markup).toMatch(/viewBox=/i);
        expect(svg.markup).toMatch(/currentColor/i);
        expect(svg.markup).not.toMatch(/<script/i);
        expect(svg.markup).not.toMatch(/javascript:/i);
        expect(svg.viewBox.width).toBeGreaterThan(0);
        expect(svg.viewBox.height).toBeGreaterThan(0);
    });

    it("keeps fallback text for invalid block TeX", () => {
        const svg = renderMathToSvg("\\frac{", {
            displayMode: "block",
            fontSize: 16,
            scale: 1
        });

        expect(svg.ok).toBe(false);
        expect(svg.fallbackText).toBe(String.raw`\[\frac{\]`);
    });

    it("renders chemistry via mhchem without a MathJax error", () => {
        const svg = renderMathToSvg("\\ce{2H2 + O2 -> 2H2O}", {
            displayMode: "block",
            fontSize: 18,
            scale: 1
        });

        expect(svg.ok).toBe(true);
        expect(svg.markup).not.toMatch(/data-mml-node=["']merror["']/i);
    });
});

describe("fallbackTextForMath", () => {
    it("uses standard LaTeX math delimiters", () => {
        expect(fallbackTextForMath("x+1", "inline")).toBe(String.raw`\(x+1\)`);
        expect(fallbackTextForMath("x+1", "block")).toBe(String.raw`\[x+1\]`);
    });
});

describe("renderMath", () => {
    it("publishes pending then succeeded", async () => {
        const states = await collectStates(
            renderMath("x^2 + y^2 = z^2", { displayMode: "inline", fontSize: 16, scale: 1 })
        );

        expect(states.length).toBe(2);
        expect(states[0]).toEqual({ status: "pending" });
        expect(states[1].status).toBe("succeeded");
        if (states[1].status === "succeeded") {
            expect(states[1].rendered.svg.ok).toBe(true);
            expect(states[1].rendered.svg.markup).toMatch(/<svg/i);
        }
    });

    it("publishes failed for invalid input", async () => {
        const states = await collectStates(
            renderMath("x+y", { displayMode: "inline", fontSize: 0, scale: 1 })
        );

        expect(states).toEqual([
            { status: "pending" },
            { status: "failed", fallback: { text: String.raw`\(x+y\)`, reason: "invalid-input" } }
        ]);
    });

    it("publishes failed for MathJax errors", async () => {
        const states = await collectStates(
            renderMath("\\frac{", { displayMode: "block", fontSize: 16, scale: 1 })
        );

        expect(states.length).toBe(2);
        expect(states[0]).toEqual({ status: "pending" });
        expect(states[1].status).toBe("failed");
        if (states[1].status === "failed") {
            expect(states[1].fallback.reason).toBe("mathjax-error");
            expect(states[1].fallback.text).toBe(String.raw`\[\frac{\]`);
        }
    });
});

describe("common formula corpus", () => {
    const formulas = loadFormulas();

    it.each(formulas.map((formula) => [formula.id, formula] as const))(
        "renders %s without a fallback or error node",
        (_id, formula) => {
            const rendered = renderMathToSvg(formula.tex, formula.options);

            expect(rendered.ok, rendered.error ?? rendered.fallbackText ?? "").toBe(true);
            expect(rendered.fallbackText).toBeUndefined();
            expect(rendered.markup).toMatch(/<svg/i);
            expect(rendered.markup).not.toMatch(/data-mml-node=["']merror["']/i);
            expect(rendered.viewBox.width).toBeGreaterThan(0);
            expect(rendered.viewBox.height).toBeGreaterThan(0);
        }
    );
});
