#!/usr/bin/env node
/**
 * Refreshes lib/releases.snapshot.json from the GitHub Releases API.
 *
 * Run this after publishing a release. The snapshot is what the site falls
 * back to when the API is unreachable at build time, so a stale snapshot
 * means a stale page during an outage.
 *
 *   node scripts/sync-releases.mjs
 */

import { writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");

const OWNER = "areyypriyanshu";
const NAME = "expense-tracker";

const normalizeVersion = (tag) => String(tag).trim().replace(/^v/i, "");

const pickApkAsset = (assets) =>
  (assets ?? []).find((asset) => asset.name.toLowerCase().endsWith(".apk")) ?? null;

const compareVersions = (a, b) => {
  const parse = (version) => {
    const [core = "", ...rest] = version.trim().replace(/^v/i, "").split("-");
    return {
      numbers: core.split(".").map((segment) => Number.parseInt(segment, 10) || 0),
      prerelease: rest.join("-"),
    };
  };

  const left = parse(a);
  const right = parse(b);
  const length = Math.max(left.numbers.length, right.numbers.length);

  for (let index = 0; index < length; index += 1) {
    const diff = (left.numbers[index] ?? 0) - (right.numbers[index] ?? 0);
    if (diff !== 0) return diff;
  }

  if (left.prerelease === right.prerelease) return 0;
  if (left.prerelease === "") return 1;
  if (right.prerelease === "") return -1;
  return left.prerelease.localeCompare(right.prerelease);
};

const headers = {
  Accept: "application/vnd.github+json",
  "User-Agent": "expense-tracker-site",
  "X-GitHub-Api-Version": "2022-11-28",
};

if (process.env.GITHUB_TOKEN) {
  headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
}

const url = `https://api.github.com/repos/${OWNER}/${NAME}/releases?per_page=100`;
const response = await fetch(url, { headers });

if (!response.ok) {
  console.error(`GitHub returned ${response.status} for ${url}`);
  console.error("If this is a rate limit, set GITHUB_TOKEN and try again.");
  process.exit(1);
}

const raw = await response.json();

const releases = (Array.isArray(raw) ? raw : [])
  .filter((entry) => entry && typeof entry.tag_name === "string" && entry.draft !== true)
  .map((entry) => {
    const asset = pickApkAsset(entry.assets);
    if (!asset) return null;
    return {
      version: normalizeVersion(entry.tag_name),
      tag: entry.tag_name,
      url: asset.browser_download_url,
      sizeBytes: asset.size,
      downloadCount: asset.download_count,
      publishedAt: entry.published_at ?? "",
      prerelease: Boolean(entry.prerelease),
      draft: false,
    };
  })
  .filter(Boolean)
  .sort((a, b) => compareVersions(b.version, a.version));

if (releases.length === 0) {
  console.error("No published releases carry an APK asset. Nothing to snapshot.");
  process.exit(1);
}

// The comment lives in a sibling TypeScript file rather than inside the JSON,
// because a `.json` file cannot carry one and still parse.
const target = join(root, "lib", "releases.snapshot.json");
await writeFile(target, `${JSON.stringify(releases, null, 2)}\n`, "utf8");

console.log(`Wrote ${releases.length} releases to ${target}`);
for (const release of releases) {
  const mb = (release.sizeBytes / 1048576).toFixed(1);
  const flag = release.prerelease ? " (prerelease)" : "";
  console.log(`  ${release.version.padEnd(9)} ${mb.padStart(6)} MB  ${release.downloadCount} downloads${flag}`);
}