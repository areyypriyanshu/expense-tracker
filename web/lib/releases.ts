import "server-only";

import { releaseSnapshot } from "./releases.snapshot";
import { repo } from "./site";

/**
 * Release data, read from the GitHub Releases API at build time.
 *
 * There is no backend. The APK bytes live on GitHub, and this module turns
 * GitHub's release records into the small shape the site renders. Every rule
 * below exists because the live data for this repository violated the tidy
 * assumption: tags are written both as `1.5.0` and `v1.0.4`, the APK asset is
 * spelled both `expense-tracker.apk` and `Expense.Tracker.apk`, and all six
 * published releases are flagged as prereleases, which means the API's own
 * "latest" endpoint returns nothing usable.
 */

export type Release = {
  /** Display version, normalized to have no leading `v`. */
  version: string;
  /** The tag exactly as GitHub stores it, used to build the download URL. */
  tag: string;
  /** Direct link to the APK asset on GitHub's CDN. */
  url: string;
  /** Size of the APK in bytes, straight from the asset record. */
  sizeBytes: number;
  /** GitHub's own download counter. Never invented, never reset locally. */
  downloadCount: number;
  /** ISO 8601 publication date. */
  publishedAt: string;
  prerelease: boolean;
  draft: boolean;
};

/** The subset of GitHub's release payload this module actually reads. */
type GithubAsset = {
  name: string;
  size: number;
  download_count: number;
  browser_download_url: string;
};

type GithubRelease = {
  tag_name: string;
  draft: boolean;
  prerelease: boolean;
  published_at: string | null;
  assets: GithubAsset[];
};

/**
 * Strips the optional leading `v` and the whitespace GitHub sometimes keeps.
 *
 * The repository uses both spellings, so this cannot assume either.
 */
export function normalizeVersion(tag: string): string {
  return tag.trim().replace(/^v/i, "");
}

/**
 * Finds the APK asset regardless of how it is spelled.
 *
 * `Expense.Tracker.apk` and `expense-tracker.apk` both resolve here. Matching
 * the extension is enough; matching the exact filename would silently produce
 * a broken download button the day someone renames the asset.
 */
export function pickApkAsset(assets: GithubAsset[]): GithubAsset | null {
  return assets.find((asset) => asset.name.toLowerCase().endsWith(".apk")) ?? null;
}

/**
 * Compares two version strings numerically, segment by segment.
 *
 * A plain string sort would put `1.10.0` below `1.9.0` because "1" sorts
 * before "9" at the second character. Every release list on this site is
 * ordered by this function, so the bug would be visible immediately.
 *
 * Non-numeric suffixes (`1.5.0-rc1`) compare lower than the bare version, which
 * is the convention a reader expects: a release candidate sits below the
 * release it leads to.
 */
export function compareVersions(a: string, b: string): number {
  const parse = (version: string) => {
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

  // Same numbers: a suffix makes the version older than the plain one.
  if (left.prerelease === right.prerelease) return 0;
  if (left.prerelease === "") return 1;
  if (right.prerelease === "") return -1;
  return left.prerelease.localeCompare(right.prerelease);
}

/**
 * Turns raw API records into the shape the site renders.
 *
 * Exported separately from `getReleases` so the test suite can run it against
 * recorded live data without touching the network.
 *
 * A release is kept when it has at least one APK asset. Drafts are dropped,
 * because a draft's asset is not downloadable by anyone reading the site.
 * Prereleases are kept and labelled, because all six existing releases are
 * flagged that way and dropping them would empty the page.
 */
export function normalizeReleases(raw: unknown): Release[] {
  if (!Array.isArray(raw)) return [];

  return raw
    .filter((entry): entry is GithubRelease => {
      if (typeof entry !== "object" || entry === null) return false;
      const release = entry as Partial<GithubRelease>;
      return (
        typeof release.tag_name === "string" &&
        release.tag_name.length > 0 &&
        release.draft !== true &&
        Array.isArray(release.assets)
      );
    })
    .map((entry): Release | null => {
      const asset = pickApkAsset(entry.assets);
      // No APK means nothing to download, so there is no reason to show it.
      if (!asset) return null;

      return {
        version: normalizeVersion(entry.tag_name),
        tag: entry.tag_name,
        url: asset.browser_download_url,
        sizeBytes: asset.size,
        downloadCount: asset.download_count,
        publishedAt: entry.published_at ?? "",
        prerelease: entry.prerelease,
        draft: false,
      };
    })
    .filter((release): release is Release => release !== null)
    .sort((a, b) => compareVersions(b.version, a.version));
}

let cached: Promise<Release[]> | null = null;

/**
 * The newest release with an APK, or null when the repository has none.
 *
 * Selection is by version, never by GitHub's `latest` endpoint, because every
 * release on this repository is marked `prerelease: true` and that endpoint
 * therefore reports no release at all.
 */
export function getLatestRelease(releases: Release[]): Release | null {
  return releases[0] ?? null;
}

/**
 * Fetches the release list once per build.
 *
 * `revalidate` keeps the fetch off the critical path of every request while
 * still letting a newly published release appear without a redeploy.
 *
 * If GitHub is unreachable or rate-limited, the committed snapshot is used and
 * the page still renders. A transient GitHub outage must not fail a deploy.
 */
export async function getReleases(): Promise<Release[]> {
  if (cached) return cached;

  cached = (async () => {
    try {
      const headers: Record<string, string> = {
        Accept: "application/vnd.github+json",
        "User-Agent": "expense-tracker-site",
        "X-GitHub-Api-Version": "2022-11-28",
      };

      // A token raises the anonymous limit of 60 requests per hour to 5000.
      // It only needs public read access.
      if (process.env.GITHUB_TOKEN) {
        headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
      }

      const response = await fetch(`${repo.api}/releases?per_page=100`, {
        headers,
        next: { revalidate: 3600 },
      });

      if (!response.ok) {
        throw new Error(`GitHub releases API returned ${response.status}`);
      }

      const normalized = normalizeReleases(await response.json());
      return normalized.length > 0 ? normalized : snapshot();
    } catch (error) {
      console.warn(
        `[releases] GitHub fetch failed, using the committed snapshot: ${String(error)}`,
      );
      return snapshot();
    }
  })();

  return cached;
}

/**
 * The last known good release list, committed alongside the code.
 *
 * Regenerate it with `pnpm sync` after publishing. It exists so the site has
 * something to render when the API is down, not as a data source.
 */
function snapshot(): Release[] {
  return releaseSnapshot;
}

/** Clears the per-build cache. Used by the tests. */
export function resetReleaseCache(): void {
  cached = null;
}