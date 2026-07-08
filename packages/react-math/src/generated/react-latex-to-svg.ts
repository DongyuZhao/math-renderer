import type { LiteElement } from "mathjax-full/js/adaptors/lite/Element.js";
import { liteAdaptor } from "mathjax-full/js/adaptors/liteAdaptor.js";
import { RegisterHTMLHandler } from "mathjax-full/js/handlers/html.js";
import "mathjax-full/js/input/tex/ams/AmsConfiguration.js";
import "mathjax-full/js/input/tex/autoload/AutoloadConfiguration.js";
import "mathjax-full/js/input/tex/configmacros/ConfigMacrosConfiguration.js";
import "mathjax-full/js/input/tex/mhchem/MhchemConfiguration.js";
import "mathjax-full/js/input/tex/newcommand/NewcommandConfiguration.js";
import "mathjax-full/js/input/tex/noundefined/NoUndefinedConfiguration.js";
import "mathjax-full/js/input/tex/require/RequireConfiguration.js";
import { mathjax } from "mathjax-full/js/mathjax.js";
import { TeX } from "mathjax-full/js/input/tex.js";
import { SVG } from "mathjax-full/js/output/svg.js";
import {
    defaultTexPackages,
    fallbackText,
    hasMathErrorNode,
    parseLatexToSvgJson,
    renderLatexToSvgJson,
    svgOutputOptions,
    type LatexToSvgOptions,
    type LatexToSvgPayload,
    type LatexToSvgRuntime
} from "./latex-to-svg-core.js";

export {
    fallbackText,
    parseLatexToSvgJson,
    renderLatexToSvgJson,
    type LatexToSvgOptions,
    type LatexToSvgPayload
} from "./latex-to-svg-core.js";

const adaptor = liteAdaptor();
RegisterHTMLHandler(adaptor);

const texInput = new TeX({ packages: [...defaultTexPackages] });
const svgOutput = new SVG(svgOutputOptions);
const mathDocument = mathjax.document("", {
    InputJax: texInput,
    OutputJax: svgOutput
});

export function renderLatexToSvg(tex: string, options: LatexToSvgOptions = {}): LatexToSvgPayload {
    return parseLatexToSvgJson(renderLatexToSvgJson(mathJaxRuntime, tex, options));
}

const mathJaxRuntime: LatexToSvgRuntime<LiteElement> = {
    convert(tex: string, options: { display: boolean; em: number; ex: number; scale: number }) {
        return mathDocument.convert(String(tex ?? ""), options) as LiteElement;
    },
    findAll(node: LiteElement, name: string) {
        return adaptor.tags(node, name);
    },
    setAttribute(node: LiteElement, name: string, value: string) {
        adaptor.setAttribute(node, name, value);
    },
    outerHTML(node: LiteElement) {
        return adaptor.outerHTML(node);
    },
    hasMathError(node: LiteElement): boolean {
        return hasMathErrorNode(node);
    }
};
