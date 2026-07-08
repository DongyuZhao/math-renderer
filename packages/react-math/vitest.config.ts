import { fileURLToPath } from "node:url";

import react from "@vitejs/plugin-react";
import { playwright } from "@vitest/browser-playwright";
import { defineConfig } from "vitest/config";

const repoRoot = fileURLToPath(new URL("../..", import.meta.url));

// mathjax-full references `__dirname`/`__filename` for its Node dynamic-require
// paths; those aren't hit for static rendering, but the bare identifiers crash
// the browser bundle. Shim them so <Math> renders real SVG in Browser Mode.
const nodeGlobalShims = {
    __dirname: JSON.stringify("/"),
    __filename: JSON.stringify("/index.js"),
    global: "globalThis"
};

export default defineConfig({
    plugins: [react()],
    define: nodeGlobalShims,
    // Allow importing the shared corpus fixtures from packages/mathjax-core.
    server: { fs: { allow: [repoRoot] } },
    test: {
        projects: [
            {
                extends: true,
                test: {
                    name: "unit",
                    environment: "jsdom",
                    globals: true,
                    setupFiles: ["./test/setup.ts"],
                    include: ["test/**/*.test.{ts,tsx}"],
                    exclude: ["test/**/*.browser.test.{ts,tsx}"]
                }
            },
            {
                extends: true,
                test: {
                    name: "browser",
                    include: ["test/**/*.browser.test.{ts,tsx}"],
                    browser: {
                        enabled: true,
                        provider: playwright(),
                        headless: true,
                        instances: [{ browser: "chromium" }]
                    }
                }
            }
        ]
    }
});
