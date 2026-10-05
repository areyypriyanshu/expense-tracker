import { LockKey, WifiSlash } from "@phosphor-icons/react/dist/ssr";
import Link from "next/link";

import { Reveal } from "@/components/ui/reveal";
import { app, localData, permissions, repo } from "@/lib/site";

/**
 * Privacy, as a ledger rather than a claim.
 *
 * The claim on the left is short. The right side is the part a reader can
 * actually check: a list of what stays on the device, and a list of the three
 * permissions the app can ask for, each with the sentence that explains it.
 *
 * Every permission is optional. Saying that explicitly, next to the list, is
 * the difference between a disclosure and a reassurance.
 */
export function Privacy() {
  return (
    <section id="privacy" className="py-20 md:py-28">
      <div className="mx-auto max-w-[1200px] px-5 md:px-8">
        {/* Vertical stack, not a left-headline / right-explainer pair. */}
        <Reveal>
          <div className="max-w-[22ch]">
            <h2 className="text-4xl font-semibold tracking-tighter text-balance text-ink md:text-5xl">
              Nothing you record leaves the device
            </h2>
          </div>
          <p className="mt-5 max-w-[58ch] text-lg leading-relaxed text-ink-muted">
            There is no account, no analytics service and no sync. The database is a Room
            database in the app&apos;s own private storage, and the assistant answers from it
            directly.
          </p>
        </Reveal>

        <div className="mt-14 grid grid-cols-1 gap-12 lg:grid-cols-[0.9fr_1.1fr] lg:gap-16">
          {/* Left: the two facts that matter, stated once each. */}
          <Reveal>
            <div className="space-y-8">
              <div className="flex gap-4">
                <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-green-wash text-green">
                  <LockKey size={19} weight="duotone" aria-hidden="true" />
                </span>
                <div>
                  <h3 className="text-lg font-medium text-ink">Stored locally</h3>
                  <p className="mt-2 leading-relaxed text-ink-muted">
                    Every row below lives in a SQLite database inside the app sandbox. Uninstall
                    and it is gone.
                  </p>
                  <ul className="mt-4 space-y-2">
                    {localData.map((item) => (
                      <li key={item} className="flex items-center gap-2.5 text-[15px] text-ink">
                        <span className="h-1 w-1 rounded-full bg-green" aria-hidden="true" />
                        {item}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              <div className="flex gap-4">
                <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-green-wash text-green">
                  <WifiSlash size={19} weight="duotone" aria-hidden="true" />
                </span>
                <div>
                  <h3 className="text-lg font-medium text-ink">Works offline</h3>
                  <p className="mt-2 leading-relaxed text-ink-muted">
                    Receipt scanning runs on a text recognition model bundled with the app.
                    Exchange rates are cached locally. Open it on a plane and it behaves the same.
                  </p>
                </div>
              </div>
            </div>
          </Reveal>

          {/* Right: the disclosure ledger. Hairline rows, not cards, because
              these are specifications rather than ideas. */}
          <Reveal delay={0.08}>
            <div className="rounded-lg border border-hairline bg-surface">
              <div className="border-b border-hairline px-6 py-5">
                <h3 className="text-lg font-medium text-ink">What the app can ask for</h3>
                <p className="mt-2 text-[15px] leading-relaxed text-ink-muted">
                  All three are optional and requested only when you use the feature that needs
                  them. Grant none of them and the app still tracks expenses by hand.
                </p>
              </div>

              <dl>
                {permissions.map((permission, index) => (
                  <div
                    key={permission.id}
                    className={`px-6 py-5 ${
                      index > 0 ? "border-t border-hairline-soft" : ""
                    }`}
                  >
                    <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                      <dt className="font-medium text-ink">{permission.name}</dt>
                      <span className="rounded-md bg-wash px-2 py-0.5 font-mono text-[11px] text-ink-muted">
                        {permission.id}
                      </span>
                    </div>
                    <dd className="mt-2 leading-relaxed text-ink-muted">{permission.why}</dd>
                  </div>
                ))}
              </dl>

              <div className="border-t border-hairline px-6 py-5">
                <p className="text-[15px] leading-relaxed text-ink-muted">
                  The app targets Android {app.targetSdk} and needs {app.minAndroid} or newer.
                  Source is at{" "}
                  <Link
                    href={repo.url}
                    className="text-green underline decoration-green/30 underline-offset-4 transition-colors hover:decoration-green"
                  >
                    {repo.slug}
                  </Link>
                  .
                </p>
              </div>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}