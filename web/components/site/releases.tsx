import { ArrowUpRight } from "@phosphor-icons/react/dist/ssr";
import Link from "next/link";

import { DownloadButton } from "@/components/ui/download-button";
import { Reveal } from "@/components/ui/reveal";
import { formatBytes, formatCount, formatDate } from "@/lib/format";
import type { Release } from "@/lib/releases";

/**
 * The release timeline.
 *
 * A vertical list with a rule down the left, not an accordion and not a table.
 * An accordion would hide the thing a visitor came to read, which is what
 * changed, and a table would push the notes into a column too narrow to hold
 * a sentence.
 *
 * The GitHub download count is shown because it is real, and it is labelled as
 * belonging to the repository rather than to the website, because that is
 * where GitHub counts it.
 */
export function Releases({ releases }: { releases: Release[] }) {
  if (releases.length === 0) return null;

  return (
    <section id="releases" className="py-20 md:py-28">
      <div className="mx-auto max-w-[1200px] px-5 md:px-8">
        <Reveal>
          <div className="flex flex-wrap items-end justify-between gap-6">
            <h2 className="max-w-[16ch] text-4xl font-semibold tracking-tighter text-balance text-ink md:text-5xl">
              Every release, with its real file size
            </h2>
            <Link
              href="/releases"
              className="inline-flex items-center gap-1.5 text-[15px] font-medium text-green underline decoration-green/30 underline-offset-4 transition-colors hover:decoration-green"
            >
              See all releases
              <ArrowUpRight size={16} weight="bold" aria-hidden="true" />
            </Link>
          </div>
        </Reveal>

        <ol className="mt-14">
          {releases.slice(0, 4).map((release, index) => (
            <Reveal key={release.tag} delay={index * 0.05}>
              <li className="relative border-l border-hairline py-7 pl-7 first:pt-0">
                {/* The node on the rule. The first release gets the filled
                    marker so the newest is identifiable at a glance. */}
                <span
                  className={`absolute -left-[5px] top-8 h-2.5 w-2.5 rounded-full first:top-1 ${
                    index === 0 ? "bg-green" : "bg-hairline"
                  }`}
                  aria-hidden="true"
                />

                <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1.5">
                  <h3 className="font-mono text-lg font-medium text-ink">{release.version}</h3>
                  {release.prerelease ? (
                    <span className="rounded-md bg-wash px-2 py-0.5 font-mono text-[11px] text-ink-muted">
                      prerelease
                    </span>
                  ) : null}
                  <span className="text-[14px] text-ink-muted">{formatDate(release.publishedAt)}</span>
                </div>

                <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-3">
                  <DownloadButton
                    href={release.url}
                    version={release.version}
                    size={formatBytes(release.sizeBytes)}
                    variant="quiet"
                  >
                    Download
                  </DownloadButton>

                  {release.downloadCount > 0 ? (
                    <span className="text-[14px] text-ink-muted">
                      {formatCount(release.downloadCount)} downloads from the repository
                    </span>
                  ) : null}
                </div>
              </li>
            </Reveal>
          ))}
        </ol>
      </div>
    </section>
  );
}