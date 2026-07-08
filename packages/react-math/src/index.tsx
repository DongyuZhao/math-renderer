import React, { useEffect, useState } from "react";
import {
    fallbackText as sharedFallbackText,
    renderLatexToSvg,
    type LatexToSvgPayload
} from "./generated/react-latex-to-svg.js";

export type MathDisplayMode = "inline" | "block";

export type MathRenderOptions = {
    displayMode?: MathDisplayMode;
    fontSize?: number;
    scale?: number;
};

export type MathViewBox = LatexToSvgPayload["viewBox"];

export type MathSvgResult = {
    ok: boolean;
    markup: string;
    viewBox: MathViewBox;
    fallbackText?: string;
    error?: string;
};

export type MathRenderFailureReason =
    "invalid-input" | "bridge-unavailable" | "mathjax-error" | "render-failed";

export type MathRenderFallback = {
    text: string;
    reason: MathRenderFailureReason;
    error?: string;
};

export type MathRenderedFormula = {
    svg: MathSvgResult;
};

export type MathRenderState =
    | { status: "pending" }
    | { status: "succeeded"; rendered: MathRenderedFormula }
    | { status: "failed"; fallback: MathRenderFallback };

export type MathProps = MathRenderOptions & {
    tex: string;
    className?: string;
    style?: React.CSSProperties;
    ariaLabel?: string;
};

export function fallbackTextForMath(tex: string, displayMode: MathDisplayMode = "inline") {
    return sharedFallbackText(tex, displayMode === "block");
}

export function renderMathToSvg(tex: string, options: MathRenderOptions = {}): MathSvgResult {
    const displayMode = options.displayMode ?? "inline";
    const payload = renderLatexToSvg(tex, {
        standalone: displayMode === "block",
        fontSize: options.fontSize,
        scale: options.scale
    });

    return {
        ok: payload.ok,
        markup: payload.markup,
        viewBox: payload.viewBox,
        fallbackText: payload.fallbackText ?? undefined,
        error: payload.error ?? undefined
    };
}

export async function* renderMath(
    tex: string,
    options: MathRenderOptions = {}
): AsyncGenerator<MathRenderState> {
    yield { status: "pending" };
    yield renderTerminalState(tex, options);
}

export function useMathRenderState(tex: string, options: MathRenderOptions = {}): MathRenderState {
    const displayMode = options.displayMode ?? "inline";
    const fontSize = options.fontSize ?? 16;
    const scale = options.scale ?? 1;
    const [state, setState] = useState<MathRenderState>({ status: "pending" });

    useEffect(() => {
        let active = true;

        async function run() {
            for await (const nextState of renderMath(tex, { displayMode, fontSize, scale })) {
                if (!active) {
                    return;
                }
                setState(nextState);
            }
        }

        void run();
        return () => {
            active = false;
        };
    }, [tex, displayMode, fontSize, scale]);

    return state;
}

export function Math({
    tex,
    displayMode = "inline",
    fontSize = 16,
    scale = 1,
    className,
    style,
    ariaLabel
}: MathProps) {
    const state = useMathRenderState(tex, { displayMode, fontSize, scale });
    const element = displayMode === "block" ? "div" : "span";
    const fallback = fallbackTextForMath(tex, displayMode);
    const label = ariaLabel ?? labelForState(state, fallback);
    const resolvedStyle = mathContainerStyle(style, fontSize, scale);

    if (state.status === "pending") {
        return React.createElement(element, {
            className,
            style: resolvedStyle,
            "aria-label": label
        });
    }

    if (state.status === "failed") {
        return React.createElement(
            element,
            {
                className,
                style: {
                    fontFamily: "monospace",
                    ...resolvedStyle
                },
                "aria-label": label
            },
            state.fallback.text
        );
    }

    const result = state.rendered.svg;
    return React.createElement(element, {
        className,
        style: resolvedStyle,
        role: "img",
        "aria-label": label,
        dangerouslySetInnerHTML: { __html: result.markup }
    });
}

function renderTerminalState(tex: string, options: MathRenderOptions): MathRenderState {
    const displayMode = options.displayMode ?? "inline";
    const fallback = fallbackTextForMath(tex, displayMode);

    if (isInvalidPositiveNumber(options.fontSize) || isInvalidPositiveNumber(options.scale)) {
        return {
            status: "failed",
            fallback: {
                text: fallback,
                reason: "invalid-input"
            }
        };
    }

    const svg = renderMathToSvg(tex, options);
    if (!svg.ok) {
        return {
            status: "failed",
            fallback: {
                text: svg.fallbackText ?? fallback,
                reason: "mathjax-error",
                error: svg.error
            }
        };
    }

    if (!svg.markup || svg.viewBox.width <= 0 || svg.viewBox.height <= 0) {
        return {
            status: "failed",
            fallback: {
                text: svg.fallbackText ?? fallback,
                reason: svg.error ? "bridge-unavailable" : "render-failed",
                error: svg.error
            }
        };
    }

    return {
        status: "succeeded",
        rendered: { svg }
    };
}

function labelForState(state: MathRenderState, fallback: string): string {
    switch (state.status) {
        case "pending":
            return fallback;
        case "succeeded":
            return state.rendered.svg.fallbackText ?? fallback;
        case "failed":
            return state.fallback.text;
    }
}

function isInvalidPositiveNumber(value: number | undefined): boolean {
    return value !== undefined && (!Number.isFinite(value) || value <= 0);
}

function mathContainerStyle(
    style: React.CSSProperties | undefined,
    fontSize: number,
    scale: number
): React.CSSProperties {
    if (!Number.isFinite(fontSize) || fontSize <= 0 || !Number.isFinite(scale) || scale <= 0) {
        return style ?? {};
    }

    return {
        fontSize: `${fontSize * scale}px`,
        ...style
    };
}
