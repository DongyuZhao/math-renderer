import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Math } from "../src/index.js";

// The <Math> wrapper carries role="img" + our aria-label; the MathJax <svg>
// inside also has role="img" but no accessible name, so we target the wrapper
// via its name to disambiguate.

describe("<Math>", () => {
    it("renders an accessible SVG image on success", async () => {
        render(<Math tex="x^2 + y^2 = z^2" displayMode="inline" fontSize={16} />);

        const img = await screen.findByRole("img", {
            name: String.raw`\(x^2 + y^2 = z^2\)`
        });
        expect(img.querySelector("svg")).not.toBeNull();
    });

    it("renders monospace fallback text on a MathJax error", async () => {
        const { container } = render(<Math tex="\frac{" displayMode="block" fontSize={16} />);

        await waitFor(() => {
            expect(container.textContent).toBe(String.raw`\[\frac{\]`);
        });
        expect(container.querySelector("svg")).toBeNull();
    });

    it("renders chemistry equations", async () => {
        const { container } = render(
            <Math tex="\ce{2H2 + O2 -> 2H2O}" displayMode="block" fontSize={18} />
        );

        await waitFor(() => {
            expect(container.querySelector("svg")).not.toBeNull();
        });
        expect(container.innerHTML).not.toMatch(/data-mml-node=["']merror["']/i);
    });

    it("matches the rendered DOM snapshot", async () => {
        const { container } = render(
            <Math tex="a^2 + b^2 = c^2" displayMode="inline" fontSize={16} />
        );

        await waitFor(() => {
            expect(container.querySelector("svg")).not.toBeNull();
        });
        expect(container.firstChild).toMatchSnapshot();
    });
});
