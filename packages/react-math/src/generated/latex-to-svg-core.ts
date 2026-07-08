export type LatexToSvgOptions = {
    standalone?: boolean;
    fontSize?: number;
    scale?: number;
};

export type LatexToSvgViewBox = {
    minX: number;
    minY: number;
    width: number;
    height: number;
};

export type LatexToSvgPayload = {
    ok: boolean;
    svg: string;
    markup: string;
    viewBox: LatexToSvgViewBox;
    fallbackText: string | null;
    error: string | null;
};

export type LatexToSvgRuntime<NodeType = unknown> = {
    convert(
        tex: string,
        options: { display: boolean; em: number; ex: number; scale: number }
    ): NodeType;
    findAll(node: NodeType, name: string): NodeType[];
    setAttribute(node: NodeType, name: string, value: string): void;
    outerHTML(node: NodeType): string;
    hasMathError(node: NodeType): boolean;
};

export const defaultTexPackages = [
    "base",
    "ams",
    "newcommand",
    "noundefined",
    "require",
    "autoload",
    "configmacros",
    "mhchem"
];

export const svgOutputOptions = {
    fontCache: "none"
} as const;

export const zeroViewBox: LatexToSvgViewBox = {
    minX: 0,
    minY: 0,
    width: 0,
    height: 0
};

export function fallbackText(tex: string, standalone: boolean): string {
    return standalone ? String.raw`\[${tex}\]` : String.raw`\(${tex}\)`;
}

export function renderLatexToSvgJson<NodeType>(
    runtime: LatexToSvgRuntime<NodeType>,
    tex: string,
    options: LatexToSvgOptions = {}
): string {
    return JSON.stringify(renderLatexToSvgPayload(runtime, tex, options));
}

export function renderLatexToSvgPayload<NodeType>(
    runtime: LatexToSvgRuntime<NodeType>,
    tex: string,
    options: LatexToSvgOptions = {}
): LatexToSvgPayload {
    const source = safeText(tex);
    const standalone = Boolean(options.standalone);
    const fallback = fallbackText(source, standalone);

    try {
        const fontSize = positiveNumber(options.fontSize, 16);
        const root = runtime.convert(source, {
            display: standalone,
            em: fontSize,
            ex: fontSize / 2,
            scale: positiveNumber(options.scale, 1)
        });
        const svg = runtime.findAll(root, "svg")[0];
        if (!svg) {
            throw new Error("MathJax did not return an SVG node.");
        }

        const group = runtime.findAll(svg, "g")[0];
        if (group) {
            runtime.setAttribute(group, "fill", "currentColor");
            runtime.setAttribute(group, "stroke", "currentColor");
        }

        const markup = sanitizeMarkup(runtime.outerHTML(svg));
        const mathError = runtime.hasMathError(root) || hasMathErrorMarkup(markup);
        return {
            ok: !mathError,
            svg: markup,
            markup,
            viewBox: parseViewBox(markup) ?? zeroViewBox,
            fallbackText: mathError ? fallback : null,
            error: mathError ? "MathJax reported a TeX parse error." : null
        };
    } catch (error) {
        return {
            ok: false,
            svg: "",
            markup: "",
            viewBox: zeroViewBox,
            fallbackText: fallback,
            error: error instanceof Error ? error.message : String(error)
        };
    }
}

export function parseLatexToSvgJson(json: string): LatexToSvgPayload {
    const payload = JSON.parse(json) as Partial<LatexToSvgPayload>;
    const markup = sanitizeMarkup(String(payload.markup ?? payload.svg ?? ""));
    const viewBox = parsePayloadViewBox(payload.viewBox) ?? parseViewBox(markup) ?? zeroViewBox;
    const ok = Boolean(payload.ok) && !hasMathErrorMarkup(markup);

    return {
        ok,
        svg: markup,
        markup,
        viewBox,
        fallbackText: nullableString(payload.fallbackText),
        error: nullableString(payload.error)
    };
}

export function sanitizeMarkup(markup: string): string {
    return String(markup || "")
        .replace(/<script[\s\S]*?<\/script>/gi, "")
        .replace(/\son[a-z]+\s*=\s*"[^"]*"/gi, "")
        .replace(/\son[a-z]+\s*=\s*'[^']*'/gi, "")
        .replace(/\son[a-z]+\s*=\s*[^>\s]+/gi, "")
        .replace(/javascript:/gi, "");
}

export function parseViewBox(markup: string): LatexToSvgViewBox | undefined {
    const match = markup.match(/viewBox\s*=\s*["']([^"']+)["']/i);
    if (!match) {
        return undefined;
    }

    const values = match[1]
        .split(/[\s,]+/)
        .filter(Boolean)
        .map((value) => Number(value));
    if (values.length !== 4 || values.some((value) => !Number.isFinite(value))) {
        return undefined;
    }

    return {
        minX: values[0],
        minY: values[1],
        width: values[2],
        height: values[3]
    };
}

export function hasMathErrorMarkup(markup: string): boolean {
    return /data-mml-node=["']merror["']/i.test(markup) || /<mjx-merror\b/i.test(markup);
}

export function hasMathErrorNode(node: unknown): boolean {
    if (!node || typeof node !== "object") {
        return false;
    }

    const candidate = node as {
        attributes?: Record<string, unknown>;
        children?: unknown[];
        childNodes?: unknown[];
    };
    if (candidate.attributes?.["data-mml-node"] === "merror") {
        return true;
    }

    const children = candidate.children ?? candidate.childNodes ?? [];
    return children.some(hasMathErrorNode);
}

function positiveNumber(value: unknown, fallback: number): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function safeText(value: unknown): string {
    return String(value == null ? "" : value);
}

function nullableString(value: unknown): string | null {
    return typeof value === "string" && value.length > 0 ? value : null;
}

function parsePayloadViewBox(value: unknown): LatexToSvgViewBox | undefined {
    if (typeof value === "string") {
        return parseViewBox(`viewBox="${value}"`);
    }

    if (typeof value !== "object" || value === null) {
        return undefined;
    }

    const raw = value as Partial<Record<keyof LatexToSvgViewBox, unknown>>;
    const minX = Number(raw.minX);
    const minY = Number(raw.minY);
    const width = Number(raw.width);
    const height = Number(raw.height);
    if (![minX, minY, width, height].every(Number.isFinite)) {
        return undefined;
    }

    return {
        minX,
        minY,
        width,
        height
    };
}
