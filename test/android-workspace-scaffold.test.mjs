import { readFile } from "node:fs/promises";
import test from "node:test";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { join } from "node:path";

const repoRoot = new URL("../", import.meta.url);

test("root Gradle settings declare the Android library and sample modules", async () => {
    const settings = await readFile(new URL("settings.gradle.kts", repoRoot), "utf8");

    assert.match(settings, /include\(":packages:compose-math"\)/);
    assert.match(
        settings,
        /project\(":packages:compose-math"\)\.projectDir = file\("packages\/compose-math"\)/
    );
    assert.match(settings, /include\(":samples:compose-math-sample"\)/);
    assert.match(
        settings,
        /project\(":samples:compose-math-sample"\)\.projectDir = file\("samples\/compose-math-sample\/app"\)/
    );
});

test("Android sample directory does not contain an independent Gradle workspace", () => {
    const sampleRoot = join(repoRoot.pathname, "samples/compose-math-sample");
    const forbiddenEntries = [
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle.properties",
        "gradle",
        "gradlew",
        "gradlew.bat"
    ];

    for (const entry of forbiddenEntries) {
        assert.equal(existsSync(join(sampleRoot, entry)), false, `${entry} should not exist`);
    }
});
