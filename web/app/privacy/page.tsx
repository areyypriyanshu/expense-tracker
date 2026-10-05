import type { Metadata } from "next";
import Link from "next/link";

import { Footer, Nav } from "@/components/site/nav";
import { Reveal } from "@/components/ui/reveal";
import { app, localData, permissions, repo } from "@/lib/site";

export const metadata: Metadata = {
  title: "Privacy",
  description:
    "What Expense Tracker stores, what it requests, and why nothing you record leaves your phone.",
  alternates: { canonical: "/privacy" },
};

export default function PrivacyPage() {
  return (
    <>
      <Nav />
      <main id="main" className="py-20 md:py-24">
        <div className="mx-auto max-w-[760px] px-5 md:px-8">
          <Reveal>
            <h1 className="text-4xl font-semibold tracking-tighter text-balance text-ink md:text-5xl">
              Privacy
            </h1>
            <p className="mt-6 text-lg leading-relaxed text-ink-muted">
              This is the whole policy, in full. There is no cookie banner on this site because
              nothing on it tracks you either.
            </p>
          </Reveal>

          <Reveal delay={0.05}>
            <section className="mt-14">
              <h2 className="text-2xl font-medium tracking-tight text-ink">What is stored</h2>
              <p className="mt-4 leading-relaxed text-ink-muted">
                A SQLite database inside the app&apos;s private storage, holding:
              </p>
              <ul className="mt-4 space-y-2.5">
                {localData.map((item) => (
                  <li key={item} className="flex items-start gap-3 leading-relaxed text-ink-muted">
                    <span className="mt-2.5 h-1 w-1 shrink-0 rounded-full bg-green" aria-hidden="true" />
                    {item}
                  </li>
                ))}
              </ul>
              <p className="mt-5 leading-relaxed text-ink-muted">
                Uninstalling the app deletes all of it. There is no server-side copy, because there
                is no server.
              </p>
            </section>
          </Reveal>

          <Reveal delay={0.05}>
            <section className="mt-14 border-t border-hairline pt-10">
              <h2 className="text-2xl font-medium tracking-tight text-ink">What is requested</h2>
              <p className="mt-4 leading-relaxed text-ink-muted">
                Three permissions, all optional, each asked for only when you use the feature that
                needs it.
              </p>
              <dl className="mt-8 space-y-8">
                {permissions.map((permission) => (
                  <div key={permission.id}>
                    <dt className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
                      <span className="text-lg font-medium text-ink">{permission.name}</span>
                      <code className="font-mono text-[12px] text-ink-muted">{permission.id}</code>
                    </dt>
                    <dd className="mt-2 leading-relaxed text-ink-muted">{permission.why}</dd>
                  </div>
                ))}
              </dl>
            </section>
          </Reveal>

          <Reveal delay={0.05}>
            <section className="mt-14 border-t border-hairline pt-10">
              <h2 className="text-2xl font-medium tracking-tight text-ink">What leaves the device</h2>
              <p className="mt-4 leading-relaxed text-ink-muted">
                Nothing. The receipt scanner is an on-device text recognition model bundled with the
                app, so a photograph of a receipt is read locally and never uploaded. The assistant
                answers from the local database using a parser that runs in the app. There is no
                analytics SDK, no crash reporting service and no advertising identifier.
              </p>
              <p className="mt-5 leading-relaxed text-ink-muted">
                The one network request this app can make is fetching exchange rates, which is
                cached on the device so conversions keep working offline. Currency rates are fetched
                from a public exchange rate feed and contain nothing about you.
              </p>
            </section>
          </Reveal>

          <Reveal delay={0.05}>
            <section className="mt-14 border-t border-hairline pt-10">
              <h2 className="text-2xl font-medium tracking-tight text-ink">This website</h2>
              <p className="mt-4 leading-relaxed text-ink-muted">
                The site is a set of static pages. It sets no cookies, runs no analytics and embeds
                nothing from a third party. Download links point at GitHub, so GitHub&apos;s own
                privacy policy and log files apply to the download itself.
              </p>
            </section>
          </Reveal>

          <Reveal delay={0.05}>
            <section className="mt-14 border-t border-hairline pt-10">
              <h2 className="text-2xl font-medium tracking-tight text-ink">Check it yourself</h2>
              <p className="mt-4 leading-relaxed text-ink-muted">
                Every claim above is verifiable in the source. The app targets Android{" "}
                {app.targetSdk} and needs {app.minAndroid} or newer.
              </p>
              <Link
                href={repo.url}
                className="mt-4 inline-block text-[15px] font-medium text-green underline decoration-green/30 underline-offset-4 transition-colors hover:decoration-green"
                rel="noopener noreferrer"
              >
                {repo.slug}
              </Link>
            </section>
          </Reveal>
        </div>
      </main>
      <Footer />
    </>
  );
}