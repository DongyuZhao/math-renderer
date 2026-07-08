// Node loader for the common-formulas corpus. Uses the pure parser in
// parse-formulas.mjs (which is also safe to import in a browser bundle).

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { parseFormulas } from "./parse-formulas.mjs";

const fixturesDir = dirname(fileURLToPath(import.meta.url));
const tsvPath = join(fixturesDir, "common-formulas.tsv");

export function loadFormulas() {
    return parseFormulas(readFileSync(tsvPath, "utf8"));
}
