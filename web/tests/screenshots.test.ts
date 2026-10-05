import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Every screenshot the site references must exist on disk.
 *
 * This exists because it already went wrong once. The scan-receipt card
 * referenced `06-receipt.png` while the file on disk was `07-receipt.png`,
 * and the result was a broken image on the live site that no type check and
 * no build step caught. A string path in a `src` attribute is not typed, so
 * nothing else in the toolchain can see it go stale.
 *
 * The test reads the component source directly rather than importing it,
 * because the components render JSX and pull in Next's image loader.
 */

const webRoot = join(__dirname, "..");
const screensDir = join(webRoot, "public", "screens");

const componentFiles = [
  join(webRoot, "components", "site", "screens.tsx"),
  join(webRoot, "components", "site", "hero.tsx"),
];

const referenced = new Set<string>();

for (const file of componentFiles) {
  const source = readFileSync(file, "utf8");
  for (const match of source.matchAll(/\/screens\/([\w-]+\.png)/g)) {
    referenced.add(match[1]!);
  }
}

describe("screenshots", () => {
  it("finds screenshot references in the components", () => {
    // A silent zero would make every test below pass for the wrong reason.
    expect(referenced.size).toBeGreaterThan(0);
  });

  it("has a file for every screenshot the site references", () => {
    const missing = [...referenced].filter((name) => !existsSync(join(screensDir, name)));
    expect(
      missing,
      `Missing screenshot files: ${missing.join(", ")}. Either capture them or fix the path.`,
    ).toEqual([]);
  });

  it("has no unreferenced files left in the directory", () => {
    // A leftover file is usually the other half of a rename that was only
    // half applied, which is how the original mismatch happened.
    const onDisk = readdirSync(screensDir).filter((name) => name.endsWith(".png"));
    const orphans = onDisk.filter((name) => !referenced.has(name));
    expect(
      orphans,
      `Unused screenshots: ${orphans.join(", ")}. Delete them or reference them.`,
    ).toEqual([]);
  });

  it("numbers the files consecutively from 01", () => {
    // The gap at 07 is what made the mismatch invisible. Numbering the
    // captures in the order they are listed means a missing one is a gap.
    const onDisk = readdirSync(screensDir).filter((name) => name.endsWith(".png")).sort();
    const numbers = onDisk.map((name) => Number.parseInt(name.slice(0, 2), 10));

    expect(numbers).toEqual(numbers.map((_, index) => index + 1));
  });
});