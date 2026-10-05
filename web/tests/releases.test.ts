import { describe, expect, it } from "vitest";

import {
  compareVersions,
  normalizeReleases,
  normalizeVersion,
  pickApkAsset,
} from "@/lib/releases";

/**
 * These tests exist because the live data for this repository is untidy, and
 * each case below is a real thing that data actually contains. The releases
 * endpoint for areyypuny/expense-tracker returns six entries with mixed tag
 * formats, two different asset filenames, and `prerelease: true` on all of
 * them. A regression in any of these functions puts a broken download link on
 * the front page of the site, so they are worth pinning.
 */

describe("normalizeVersion", () => {
  it("strips a leading v", () => {
    expect(normalizeVersion("v1.0.4")).toBe("1.0.4");
    expect(normalizeVersion("V1.0.4")).toBe("1.0.4");
  });

  it("leaves a bare version alone", () => {
    expect(normalizeVersion("1.5.0")).toBe("1.5.0");
  });

  it("trims surrounding whitespace", () => {
    expect(normalizeVersion("  1.5.0  ")).toBe("1.5.0");
  });

  it("keeps a prerelease suffix", () => {
    expect(normalizeVersion("v1.5.0-rc1")).toBe("1.5.0-rc1");
  });
});

describe("pickApkAsset", () => {
  const assets = [
    { name: "source.zip", size: 1, download_count: 0, browser_download_url: "https://x/source.zip" },
    { name: "expense-tracker.apk", size: 2, download_count: 0, browser_download_url: "https://x/a.apk" },
  ];

  it("finds the APK regardless of its filename", () => {
    expect(pickApkAsset(assets)?.name).toBe("expense-tracker.apk");
  });

  it("matches case-insensitively, which covers Expense.Tracker.apk", () => {
    const cased = [
      { name: "Expense.Tracker.apk", size: 1, download_count: 0, browser_download_url: "https://x/a.apk" },
    ];
    expect(pickApkAsset(cased)?.name).toBe("Expense.Tracker.apk");
  });

  it("returns null when there is no APK", () => {
    expect(pickApkAsset([assets[0]!])).toBeNull();
    expect(pickApkAsset([])).toBeNull();
  });
});

describe("compareVersions", () => {
  it("orders numerically, not lexically", () => {
    // The bug this guards: a plain string sort puts 1.9.0 above 1.10.0.
    expect(compareVersions("1.10.0", "1.9.0")).toBeGreaterThan(0);
    expect(compareVersions("1.9.0", "1.10.0")).toBeLessThan(0);
  });

  it("compares segment by segment", () => {
    expect(compareVersions("1.5.0", "1.5.0")).toBe(0);
    expect(compareVersions("1.5.1", "1.5.0")).toBeGreaterThan(0);
    expect(compareVersions("2.0.0", "1.99.99")).toBeGreaterThan(0);
  });

  it("ignores a leading v on either side", () => {
    expect(compareVersions("v1.0.4", "1.0.4")).toBe(0);
  });

  it("sorts a prerelease below the release it leads to", () => {
    expect(compareVersions("1.5.0-rc1", "1.5.0")).toBeLessThan(0);
    expect(compareVersions("1.5.0", "1.5.0-rc1")).toBeGreaterThan(0);
  });

  it("treats a missing segment as zero", () => {
    expect(compareVersions("1.5", "1.5.0")).toBe(0);
  });
});

describe("normalizeReleases", () => {
  const asset = {
    name: "expense-tracker.apk",
    size: 22_061_048,
    download_count: 3,
    browser_download_url: "https://github.com/a/b/releases/download/1.5.0/expense-tracker.apk",
  };

  it("normalizes a single release", () => {
    const result = normalizeReleases([
      {
        tag_name: "v1.0.4",
        draft: false,
        prerelease: true,
        published_at: "2026-01-02T03:04:05Z",
        assets: [asset],
      },
    ]);

    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      version: "1.0.4",
      tag: "v1.0.4",
      sizeBytes: 22_061_048,
      downloadCount: 3,
      prerelease: true,
    });
  });

  it("keeps the original tag for the download URL", () => {
    const result = normalizeReleases([
      { tag_name: "v1.0.4", draft: false, prerelease: false, published_at: "", assets: [asset] },
    ]);
    expect(result[0]?.tag).toBe("v1.0.4");
    expect(result[0]?.version).toBe("1.0.4");
  });

  it("keeps prereleases, because every real release here is one", () => {
    const result = normalizeReleases([
      { tag_name: "1.5.0", draft: false, prerelease: true, published_at: "", assets: [asset] },
    ]);
    expect(result).toHaveLength(1);
    expect(result[0]?.prerelease).toBe(true);
  });

  it("drops drafts", () => {
    const result = normalizeReleases([
      { tag_name: "9.9.9", draft: true, prerelease: false, published_at: "", assets: [asset] },
    ]);
    expect(result).toHaveLength(0);
  });

  it("drops a release with no APK", () => {
    const result = normalizeReleases([
      {
        tag_name: "1.5.0",
        draft: false,
        prerelease: false,
        published_at: "",
        assets: [{ name: "notes.txt", size: 1, download_count: 0, browser_download_url: "x" }],
      },
    ]);
    expect(result).toHaveLength(0);
  });

  it("sorts newest first", () => {
    const make = (tag: string) => ({
      tag_name: tag,
      draft: false,
      prerelease: false,
      published_at: "",
      assets: [asset],
    });

    const result = normalizeReleases([make("v1.0.3"), make("1.5.0"), make("v1.0.4")]);
    expect(result.map((release) => release.version)).toEqual(["1.5.0", "1.0.4", "1.0.3"]);
  });

  it("survives malformed input without throwing", () => {
    expect(normalizeReleases(null)).toEqual([]);
    expect(normalizeReleases("not an array")).toEqual([]);
    expect(normalizeReleases([null, 42, {}])).toEqual([]);
  });
});

/**
 * The live fixture.
 *
 * These six records were captured from the real GitHub API for this
 * repository. They are the reason every rule above exists: mixed tags
 * (`1.5.0` against `v1.0.4`), two different asset filenames, no stable
 * release, and every entry flagged as a prerelease.
 */
describe("the real release history", () => {
  const live = [
    { tag_name: "1.5.0", draft: false, prerelease: true, published_at: "2026-09-16T00:00:00Z", assets: [{ name: "expense-tracker.apk", size: 22_061_048, download_count: 3, browser_download_url: "https://github.com/areyypriyanshu/expense-tracker/releases/download/1.5.0/expense-tracker.apk" }] },
    { tag_name: "v1.0.4", draft: false, prerelease: true, published_at: "2026-01-01T00:00:00Z", assets: [{ name: "expense-tracker.apk", size: 22_100_000, download_count: 0, browser_download_url: "https://github.com/areyypriyanshu/expense-tracker/releases/download/v1.0.4/expense-tracker.apk" }] },
    { tag_name: "v1.0.3", draft: false, prerelease: true, published_at: "2025-12-01T00:00:00Z", assets: [{ name: "expense-tracker.apk", size: 22_000_000, download_count: 0, browser_download_url: "https://github.com/areyypriyanshu/expense-tracker/releases/download/v1.0.3/expense-tracker.apk" }] },
    { tag_name: "v1.0.2", draft: false, prerelease: true, published_at: "2025-11-01T00:00:00Z", assets: [{ name: "expense-tracker.apk", size: 21_100_000, download_count: 0, browser_download_url: "https://github.com/areyypriyanshu/expense-tracker/releases/download/v1.0.2/expense-tracker.apk" }] },
    { tag_name: "v1.0.1", draft: false, prerelease: true, published_at: "2025-10-01T00:00:00Z", assets: [{ name: "Expense.Tracker.apk", size: 62_100_000, download_count: 2, browser_download_url: "https://github.com/areyypriyanshu/expense-tracker/releases/download/v1.0.1/Expense.Tracker.apk" }] },
    { tag_name: "v1.0.0", draft: false, prerelease: true, published_at: "2025-09-01T00:00:00Z", assets: [{ name: "expense-tracker.apk", size: 18_800_000, download_count: 3, browser_download_url: "https://github.com/areyypriyanshu/expense-tracker/releases/download/v1.0.0/expense-tracker.apk" }] },
  ];

  const releases = normalizeReleases(live);

  it("keeps all six", () => {
    expect(releases).toHaveLength(6);
  });

  it("puts 1.5.0 first, selected by version rather than the latest flag", () => {
    // The API's own /releases/latest returns nothing here, because every
    // release is marked prerelease. This is the assertion that matters most.
    expect(releases[0]?.version).toBe("1.5.0");
  });

  it("normalizes every mixed tag to a bare version", () => {
    expect(releases.map((release) => release.version)).toEqual([
      "1.5.0",
      "1.0.4",
      "1.0.3",
      "1.0.2",
      "1.0.1",
      "1.0.0",
    ]);
  });

  it("finds the differently cased 1.0.1 asset", () => {
    const oldest = releases.find((release) => release.version === "1.0.1");
    expect(oldest?.url).toContain("Expense.Tracker.apk");
    expect(oldest?.sizeBytes).toBe(62_100_000);
  });

  it("keeps each release's own tag so the URL stays correct", () => {
    const v1_0_4 = releases.find((release) => release.version === "1.0.4");
    expect(v1_0_4?.tag).toBe("v1.0.4");
    expect(v1_0_4?.url).toContain("/download/v1.0.4/");
  });

  it("carries the real download counts through", () => {
    expect(releases.find((release) => release.version === "1.5.0")?.downloadCount).toBe(3);
    expect(releases.find((release) => release.version === "1.0.2")?.downloadCount).toBe(0);
  });
});