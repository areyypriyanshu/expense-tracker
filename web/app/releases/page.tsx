import type { Metadata } from "next";
import Link from "next/link";

import { Footer, Nav } from "@/components/site/nav";
import { DownloadButton } from "@/components/ui/download-button";
import { Reveal } from "@/components/ui/reveal";
import { formatBytes, formatCount, formatDate } from "@/lib/format";
import { getReleases } from "@/lib/releases";
import { repo } from "@/lib/site";

export const revalidate = 3600;

export const metadata: Metadata = {
  title: "Releases",
  description:
    "Every published version of Expense Tracker for Android, with the real file size of each build and a direct download.",
  alternates: { canonical: "/releases" },
};

export default async function ReleasesPage() {
  const releases = await getReleases();

  return (
    <>
      <Nav />
      <main id="main" className="py-20 md:py-24">
        <div className="mx-auto max-w-[1200px] px-5 md:px-8">
          <Reveal>
            <h1 className="text-4xl font-semibold tracking-tighter text-balance text-ink md:text-5xl">
              All releases
            </h1>
            <p className="mt-5 max-w-[58ch] text-lg leading-relaxed text-ink-muted">
              {releases.length > 0
                ? `${releases.length} builds, newest first. Sizes are read from the file attached to each GitHub release.`
                : "No releases have been published yet."}
            </p>
          </Reveal>

          {releases.length === 0 ? (
            <div className="mt-14 rounded-lg border border-hairline bg-surface px-6 py-12 text-center">
              <p className="text-ink-muted">Nothing published yet.</p>
              <Link
                href={repo.url + "/releases"}
                className="mt-4 inline-block text-[15px] font-medium text-green underline decoration-green/30 underline-offset-4"
              >
                Check the repository
              </Link>
            </div>
          ) : (
            <ol className="mt-14">
              {releases.map((release, index) => (
                <Reveal key={release.tag} delay={Math.min(index, 6) * 0.04}>
                  <li className="relative border-l border-hairline py-8 pl-7 first:pt-0">
                    <span
                      className={`absolute -left-[5px] top-9 h-2.5 w-2.5 rounded-full first:top-1 ${
                        index === 0 ? "bg-green" : "bg-hairline"
                      }`}
                      aria-hidden="true"
                    />

                    <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1.5">
                      <h2 className="font-mono text-2xl font-medium tracking-tight text-ink">
                        {release.version}
                      </h2>
                      {release.prerelease ? (
                        <span className="rounded-md bg-wash px-2 py-0.5 font-mono text-[11px] text-ink-muted">
                          prerelease
                        </span>
                      ) : null}
                      <span className="text-[14px] text-ink-muted">
                        {formatDate(release.publishedAt)}
                      </span>
                    </div>

                    <div className="mt-4 flex flex-wrap items-center gap-x-6 gap-y-4">
                      <DownloadButton
                        href={release.url}
                        version={release.version}
                        size={formatBytes(release.sizeBytes)}
                        variant="quiet"
                      >
                        Download
                      </DownloadButton>

                      <span className="font-mono text-[13px] text-ink-muted">
                        {formatBytes(release.sizeBytes)}
                      </span>

                      {release.downloadCount > 0 ? (
                        <span className="text-[14px] text-ink-muted">
                          {formatCount(release.downloadCount)} downloads from the repository
                        </span>
                      ) : null}
                    </div>

                    <p className="mt-3 font-mono text-[12px] break-all text-ink-muted/70">
                      {release.url}
                    </p>
                  </li>
                </Reveal>
              ))}
            </ol>
          )}
        </div>
      </main>
      <Footer />
    </>
  );
}