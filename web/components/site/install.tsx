import { ArrowSquareOut, Fingerprint, ShieldWarning, WarningCircle } from "@phosphor-icons/react/dist/ssr";
import Link from "next/link";

import { DownloadButton } from "@/components/ui/download-button";
import { Reveal } from "@/components/ui/reveal";
import { formatBytes } from "@/lib/format";
import type { Release } from "@/lib/releases";
import { app, repo } from "@/lib/site";

/**
 * Install steps.
 *
 * Four steps, because sideloading an APK has four genuinely different stages
 * and each one has a way to go wrong. The most important line on the whole
 * page is the Play Protect warning, because a person installing a 21 MB file
 * from a web page will hit that dialog and needs to know it is expected rather
 * than alarming.
 */
export function Install({ latest }: { latest: Release | null }) {
  const steps = [
    {
      title: "Download the APK",
      body: latest
        ? `One file, ${formatBytes(latest.sizeBytes)}. It is the same APK attached to every release in the repository.`
        : "One file, attached to every release in the repository.",
    },
    {
      title: "Allow your file manager to install it",
      body: "Android blocks installs from outside the Play Store by default. Tap the downloaded file, allow the prompt, and return.",
    },
    {
      title: "Open it",
      body: `The app installs, then opens on a short setup that explains what it does. It needs Android ${app.minAndroid} or newer.`,
    },
    {
      title: "Grant only what you will use",
      body: "Nothing is requested up front beyond notifications. SMS appears when you turn on UPI sync, camera when you tap Scan Receipt.",
    },
  ];

  return (
    <section id="install" className="py-20 md:py-28">
      <div className="mx-auto max-w-[1200px] px-5 md:px-8">
        <Reveal>
          <h2 className="max-w-[20ch] text-4xl font-semibold tracking-tighter text-balance text-ink md:text-5xl">
            Installing it takes a minute
          </h2>
        </Reveal>

        {/* Horizontal on desktop, a single vertical rhythm on mobile. Four
            equal columns, because these are steps in an order and not
            features of different weight. */}
        <ol className="mt-14 grid grid-cols-1 gap-x-8 gap-y-10 md:grid-cols-2 lg:grid-cols-4">
          {steps.map((step, index) => (
            <Reveal key={step.title} delay={index * 0.06}>
              <li className="border-t border-hairline pt-5">
                <span className="font-mono text-[13px] text-ink-muted">
                  {String(index + 1).padStart(2, "0")}
                </span>
                <h3 className="mt-3 text-lg font-medium tracking-tight text-ink">{step.title}</h3>
                <p className="mt-2.5 leading-relaxed text-ink-muted">{step.body}</p>
              </li>
            </Reveal>
          ))}
        </ol>

        {/* The disclosure block. Separated from the steps above because it is a
            different kind of statement: not a step, a warning. */}
        <Reveal delay={0.1}>
          <div className="mt-16 rounded-lg border border-hairline bg-surface p-6 md:p-8">
            <div className="flex gap-4">
              <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-wash text-ink">
                <ShieldWarning size={19} weight="duotone" aria-hidden="true" />
              </span>
              <div>
                <h3 className="text-lg font-medium text-ink">Play Protect will warn you</h3>
                <p className="mt-2 max-w-[68ch] leading-relaxed text-ink-muted">
                  Android flags any app that did not come through the Play Store, including this one.
                  The warning is expected and is not a sign of a problem. Choose Install anyway to
                  continue. If Chrome pauses or gets stuck at 100% (22.06 MB), it is Android Safe
                  Browsing scanning the file: check your notification shade or Chrome Downloads and tap{" "}
                  <strong>Keep</strong> or <strong>Download anyway</strong> to finish saving it.
                </p>

                <div className="mt-5 flex flex-wrap items-center gap-x-6 gap-y-3">
                  <Link
                    href={repo.url}
                    className="inline-flex items-center gap-2 text-[15px] font-medium text-green underline decoration-green/30 underline-offset-4 transition-colors hover:decoration-green"
                  >
                    Read the source
                    <ArrowSquareOut size={15} weight="bold" aria-hidden="true" />
                  </Link>

                  <span className="inline-flex items-center gap-2 text-[14px] text-ink-muted">
                    <Fingerprint size={16} weight="duotone" aria-hidden="true" />
                    Signed with a personal release key, not a Play Store key
                  </span>
                </div>
              </div>
            </div>
          </div>
        </Reveal>

        {/* Closing action, and the only one on the page using the word
            "Download". Everywhere else says "See all releases". One label per
            intent. */}
        <Reveal delay={0.14}>
          <div className="mt-12 flex flex-wrap items-center gap-5">
            {latest ? (
              <DownloadButton
                href={latest.url}
                version={latest.version}
                size={formatBytes(latest.sizeBytes)}
              >
                Download APK
              </DownloadButton>
            ) : null}

            <p className="inline-flex items-center gap-2 text-[14px] text-ink-muted">
              <WarningCircle size={16} weight="duotone" aria-hidden="true" />
              Only install from this page or the repository releases.
            </p>
          </div>
        </Reveal>
      </div>
    </section>
  );
}