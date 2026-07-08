import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
    base: "./",
    plugins: [react()],
    // mathjax-full ships Node-oriented code that references `__dirname`/`__filename`
    // for its dynamic-require paths. Those paths aren't exercised for static
    // rendering, but the bare identifiers throw `__dirname is not defined` in the
    // browser bundle. Define them so the SVG actually renders client-side.
    define: {
        __dirname: JSON.stringify("/"),
        __filename: JSON.stringify("/index.js")
    }
});
