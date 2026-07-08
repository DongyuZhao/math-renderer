// Pure parser for the common-formulas corpus — no Node built-ins, so it works
// in a browser bundle (Vitest Browser Mode imports the TSV via `?raw`) as well
// as in Node. The fs-based loaders live in formulas.mjs.

export const EXPECTED_HEADER = "id\tcategory\tlevel\tdisplayMode\tfontSize\tscale\ttex";

/**
 * @param {string} tsvText raw contents of common-formulas.tsv
 * @returns {Array<{id:string,category:string,level:string,displayMode:"inline"|"block",fontSize:number,scale:number,tex:string,options:{displayMode:string,fontSize:number,scale:number}}>}
 */
export function parseFormulas(tsvText) {
    const [header, ...lines] = tsvText.trim().split(/\r?\n/);
    if (header !== EXPECTED_HEADER) {
        throw new Error(
            `common-formulas.tsv header mismatch.\n  expected: ${EXPECTED_HEADER}\n  actual:   ${header}`
        );
    }

    return lines.map((line) => {
        const [id, category, level, displayMode, fontSize, scale, tex] = line.split("\t");
        if (!id || !tex) {
            throw new Error(`Malformed fixture row: ${line}`);
        }
        if (displayMode !== "inline" && displayMode !== "block") {
            throw new Error(`Invalid displayMode "${displayMode}" in row: ${line}`);
        }

        return {
            id,
            category,
            level,
            displayMode,
            fontSize: Number(fontSize),
            scale: Number(scale),
            tex,
            options: { displayMode, fontSize: Number(fontSize), scale: Number(scale) }
        };
    });
}
