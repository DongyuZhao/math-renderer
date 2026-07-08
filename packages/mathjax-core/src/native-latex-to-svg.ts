import type { LatexToSvgOptions } from "./latex-to-svg-core";

declare const LatexToSvgCore: typeof import("./latex-to-svg-core");

type NativeMathJaxGlobal = {
    MathJax?: {
        config: {
            startup: {
                input: string[];
                output: string;
                adaptor: string;
                handler: string;
                document: string;
                typeset: boolean;
            };
            tex?: {
                packages?: string[];
            };
            svg?: {
                fontCache?: string;
            };
        };
        startup: {
            defaultReady(): void;
            document: {
                convert(
                    tex: string,
                    options: { display: boolean; em: number; ex: number; scale: number }
                ): unknown;
            };
            adaptor: {
                tags(node: unknown, name: string): unknown[];
                setAttribute(node: unknown, name: string, value: string): void;
                outerHTML(node: unknown): string;
            };
        };
    };
    __MathRenderLatexToSvgReady?: boolean;
    MathRenderLatexToSvg?: {
        renderJSON(tex: string, options?: LatexToSvgOptions): string;
    };
};

export function installLatexToSvgBridge(globalObject: NativeMathJaxGlobal): void {
    const runtime = createGlobalRuntime(globalObject);
    globalObject.MathRenderLatexToSvg = {
        renderJSON(tex: string, options?: LatexToSvgOptions): string {
            return LatexToSvgCore.renderLatexToSvgJson(runtime, tex, options);
        }
    };
}

export function createGlobalRuntime(globalObject: NativeMathJaxGlobal) {
    return {
        convert(tex: string, options: { display: boolean; em: number; ex: number; scale: number }) {
            ensureReady(globalObject);
            return globalObject.MathJax!.startup.document.convert(tex, options);
        },
        findAll(node: unknown, name: string) {
            ensureReady(globalObject);
            return globalObject.MathJax!.startup.adaptor.tags(node, name);
        },
        setAttribute(node: unknown, name: string, value: string) {
            ensureReady(globalObject);
            globalObject.MathJax!.startup.adaptor.setAttribute(node, name, value);
        },
        outerHTML(node: unknown) {
            ensureReady(globalObject);
            return globalObject.MathJax!.startup.adaptor.outerHTML(node);
        },
        hasMathError(node: unknown) {
            return LatexToSvgCore.hasMathErrorNode(node);
        }
    };
}

function ensureReady(globalObject: NativeMathJaxGlobal): void {
    if (globalObject.__MathRenderLatexToSvgReady) {
        return;
    }

    const mathJax = globalObject.MathJax;
    if (!mathJax) {
        throw new Error("MathJax is not loaded.");
    }

    mathJax.config.startup.input = ["tex"];
    mathJax.config.startup.output = "svg";
    mathJax.config.startup.adaptor = "liteAdaptor";
    mathJax.config.startup.handler = "HTMLHandler";
    mathJax.config.startup.document = "";
    mathJax.config.startup.typeset = false;
    mathJax.config.tex = mathJax.config.tex ?? {};
    mathJax.config.svg = mathJax.config.svg ?? {};
    mathJax.config.tex.packages = [...LatexToSvgCore.defaultTexPackages];
    mathJax.config.svg.fontCache = LatexToSvgCore.svgOutputOptions.fontCache;
    mathJax.startup.defaultReady();
    globalObject.__MathRenderLatexToSvgReady = true;
}
