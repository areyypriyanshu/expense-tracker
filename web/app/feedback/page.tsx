import type { Metadata } from "next";
import { GithubLogo } from "@phosphor-icons/react/dist/ssr";

import { Footer, Nav } from "@/components/site/nav";
import { FeedbackForm } from "@/components/site/feedback-form";
import { Reveal } from "@/components/ui/reveal";
import { feedbackEnabled } from "@/lib/feedback-config";
import { repo } from "@/lib/site";

export const metadata: Metadata = {
  title: "Feedback",
  description:
    "Report a bug, ask for a feature, or tell me what is wrong with Expense Tracker. Screen shots welcome.",
  alternates: { canonical: "/feedback" },
};

/**
 * Rendered per request rather than at build time.
 *
 * The page asks whether feedback is configured, and that answer comes from
 * environment variables which Next substitutes while building. Prerendered, it
 * would bake in whatever was true on the build machine and show "not switched
 * on yet" on a deployment where the route itself works perfectly well.
 */
export const dynamic = "force-dynamic";

export default function FeedbackPage() {
  return (
    <>
      <Nav />
      <main id="main" className="py-20 md:py-24">
        <div className="mx-auto max-w-[1200px] px-5 md:px-8">
          {/* Vertical stack for the heading, not the left-headline /
              right-explainer pairing the other sections use. */}
          <Reveal>
            <div className="max-w-[24ch]">
              <h1 className="text-4xl font-semibold tracking-tighter text-balance text-ink md:text-5xl">
                Tell me what is wrong with it
              </h1>
            </div>
            <p className="mt-5 max-w-[58ch] text-lg leading-relaxed text-ink-muted">
              Bugs, missing features, questions, or something that simply looks wrong. I read every
              message.
            </p>
          </Reveal>

          <div className="mt-14 grid grid-cols-1 gap-12 lg:grid-cols-[1.35fr_0.65fr] lg:gap-16">
            <Reveal>
              <FeedbackForm configured={feedbackEnabled()} />
            </Reveal>

            {/* The secondary route, and the honest account of what happens
                to a message once it is sent. */}
            <Reveal delay={0.08}>
              <aside className="space-y-8 lg:pt-1">
                <div>
                  <h2 className="text-[15px] font-medium text-ink">Already on GitHub?</h2>
                  <p className="mt-2.5 leading-relaxed text-ink-muted">
                    The issue tracker works the same way and is a good fit if you want to follow along
                    as something gets fixed.
                  </p>
                  <a
                    href={`${repo.url}/issues`}
                    rel="noopener noreferrer"
                    className="mt-4 inline-flex items-center gap-2 text-[15px] font-medium text-green underline decoration-green/30 underline-offset-4 transition-colors hover:decoration-green"
                  >
                    <GithubLogo size={17} weight="fill" aria-hidden="true" />
                    Open the issue tracker
                  </a>
                </div>

                <div className="border-t border-hairline pt-8">
                  <h2 className="text-[15px] font-medium text-ink">What happens to it</h2>
                  <ul className="mt-3 space-y-2.5">
                    {[
                      "It goes to an inbox, not a database. Nothing here is stored on this site.",
                      "Your email address is used only to reply to you.",
                      "Attachments are checked before they are sent, and only PNG and JPEG are accepted.",
                    ].map((point) => (
                      <li key={point} className="flex items-start gap-2.5 text-[14px] leading-relaxed text-ink-muted">
                        <span className="mt-2 h-1 w-1 shrink-0 rounded-full bg-green" aria-hidden="true" />
                        {point}
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="border-t border-hairline pt-8">
                  <h2 className="text-[15px] font-medium text-ink">Reporting a crash</h2>
                  <p className="mt-2.5 leading-relaxed text-ink-muted">
                    Android keeps the log. Settings, then Apps, then Expense Tracker, then Force stop
                    shows nothing useful, but a screen shot of the dialog the crash produced is
                    usually enough to work from.
                  </p>
                </div>
              </aside>
            </Reveal>
          </div>
        </div>
      </main>
      <Footer />
    </>
  );
}