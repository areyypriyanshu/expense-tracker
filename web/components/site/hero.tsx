import { ArrowDown, CheckCircle, HandCoins, Receipt, Robot, ShieldCheck } from "@phosphor-icons/react/dist/ssr";
import Link from "next/link";

import { DownloadButton } from "@/components/ui/download-button";
import { PhoneFrame } from "@/components/ui/phone-frame";
import { Reveal } from "@/components/ui/reveal";
import { formatBytes } from "@/lib/format";
import type { Release } from "@/lib/releases";
import { app, repo } from "@/lib/site";

/**
 * Hero, asymmetric split.
 *
 * VARIANCE is 6, so the layout is deliberately not centered: the value
 * proposition sits left and the product sits right, the way a print spread
 * reads. Four text elements total, which is the maximum the design contract
 * allows in a hero: eyebrow, headline, subtext, actions.
 */
export function Hero({ latest }: { latest: Release | null }) {
  return (
    <section className="relative overflow-hidden pt-20 pb-16 md:pt-24 md:pb-24">
      {/* The single permitted use of gold on the page: a hairline along the
          top of the hero. It marks the brand's presence without introducing a
          second accent anywhere else. */}
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-gold/40 to-transparent" />

      <div className="mx-auto grid max-w-[1200px] items-center gap-12 px-5 md:px-8 lg:grid-cols-[1.05fr_0.95fr] lg:gap-16">
        <div>
          {latest ? (
            <p className="font-mono text-[13px] tracking-wide text-ink-muted uppercase">
              Android, version {latest.version}
            </p>
          ) : null}

          <h1 className="mt-5 text-5xl font-semibold tracking-tighter text-balance text-ink md:text-6xl">
            Expense tracking that keeps your data on your phone
          </h1>

          <p className="mt-6 max-w-[62ch] text-lg leading-relaxed text-ink-muted">
            A quiet Android app for expenses, budgets and receipts. No account, no server, no
            tracking.
          </p>

          <div className="mt-9 flex flex-wrap items-center gap-4">
            {latest ? (
              <DownloadButton href={latest.url} version={latest.version} size={formatBytes(latest.sizeBytes)}>
                Download APK
              </DownloadButton>
            ) : null}

            <Link
              href="#install"
              className="inline-flex items-center gap-2 rounded-lg border border-hairline px-5 py-3 text-[15px] font-medium text-ink transition-colors duration-200 hover:bg-wash"
            >
              How to install
              <ArrowDown size={16} weight="bold" aria-hidden="true" />
            </Link>
          </div>

          {latest ? (
            <p className="mt-6 flex items-center gap-2 text-[14px] text-ink-muted">
              <ShieldCheck size={16} weight="fill" className="text-green" aria-hidden="true" />
              {formatBytes(latest.sizeBytes)}, needs {app.minAndroid} or newer
            </p>
          ) : null}
        </div>

        {/* The real dashboard, captured from the emulator. */}
        <div className="flex justify-center lg:justify-end">
          <Reveal delay={0.08}>
            <PhoneFrame
              src="/screens/01-dashboard.png"
              alt="The Expense Tracker home screen, showing this month's spending, a bar chart of recent days, and the most recent transactions."
              className="w-[280px] md:w-[320px]"
              priority
            />
          </Reveal>
        </div>
      </div>
    </section>
  );
}

/**
 * Feature bento, exactly five cells.
 *
 * The count is deliberate. Seven features exist in the app, but a bento with
 * seven cells has no rhythm and no way to place a hero cell. Five in a 2+3
 * arrangement gives the grid a lead cell, and each cell is sized by how much
 * it has to say rather than being an identical box.
 */
const features = [
  {
    icon: HandCoins,
    title: "UPI messages become expenses",
    body: "Read transaction SMS on the phone, pull out the amount, merchant and reference, and file it under a category the words suggest.",
    // Two columns wide, one row tall. The trace visual is short, so a row
    // span would leave a hole in the middle of the card on desktop.
    span: "lg:col-span-2",
    visual: "trace" as const,
  },
  {
    icon: Receipt,
    title: "Scan a receipt",
    body: "Point the camera at paper. The total, merchant and date are read on device.",
    span: "",
    visual: "receipt" as const,
  },
  {
    icon: ShieldCheck,
    title: "Budgets that speak up",
    body: "Set a limit per category or for the month. A notification at the threshold, and nothing louder than that.",
    span: "",
    visual: "budget" as const,
  },
  {
    icon: Robot,
    title: "Ask in plain words",
    body: "How much did I spend on food in June? Answered from the local database, with no server and no model in the loop.",
    span: "lg:col-span-2",
    visual: "assistant" as const,
  },
];

export function Features() {
  return (
    <section id="app" className="py-20 md:py-28">
      <div className="mx-auto max-w-[1200px] px-5 md:px-8">
        <Reveal>
          <h2 className="max-w-[20ch] text-4xl font-semibold tracking-tighter text-balance text-ink md:text-5xl">
            Everything it does, it does on your phone
          </h2>
          <p className="mt-5 max-w-[58ch] text-lg leading-relaxed text-ink-muted">
            There is no account to create and no server to call. Every feature below works with the
            phone in aeroplane mode.
          </p>
        </Reveal>

        {/* Grid, never flex percentage math. */}
        <div className="mt-14 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {features.map((feature, index) => (
            <Reveal key={feature.title} delay={index * 0.06} className={feature.span}>
              <FeatureCell {...feature} />
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

function FeatureCell({
  icon: Icon,
  title,
  body,
  visual,
}: {
  icon: typeof HandCoins;
  title: string;
  body: string;
  span: string;
  visual: "trace" | "receipt" | "budget" | "assistant";
}) {
  return (
    <article className="flex h-full flex-col rounded-lg border border-hairline bg-surface p-6 md:p-7">
      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-green-wash text-green">
        <Icon size={19} weight="duotone" aria-hidden="true" />
      </span>

      <h3 className="mt-5 text-xl font-medium tracking-tight text-ink">{title}</h3>
      <p className="mt-3 leading-relaxed text-ink-muted">{body}</p>

      {/* The flex-1 spacer. A grid row stretches every card to the height of
          the tallest one in that row, and the extra space has to go somewhere.
          Placing it after the last content element pushes the card's content
          to the top and the empty band to the bottom, which is the only
          position that reads as breathing room rather than a layout bug. */}
      <div className="mt-3 flex-1" aria-hidden="true" />

      {/* Two cells carry a visual, so the grid is not five identical text
          boxes. The others end at the paragraph rather than padding out with
          an illustration that says nothing. */}
      {visual === "trace" ? <UpiTrace /> : null}
      {visual === "receipt" ? <ReceiptShot /> : null}
      {visual === "assistant" ? <AssistantAsk /> : null}
    </article>
  );
}

/**
 * A real SMS thread, transcribed from the format `UpiSmsParser` reads. The
 * wording is the shape a real bank sends, which is what makes the cell worth
 * reading: it shows what the parser is actually matching against.
 */
function UpiTrace() {
  return (
    <div className="mt-7 space-y-3 border-t border-hairline-soft pt-6">
      <div className="rounded-lg border border-hairline-soft bg-bone px-4 py-3">
        <p className="font-mono text-[12px] leading-relaxed text-ink-muted">
          HDFC BANK: Rs 450.00 debited for UPI to SWIGGY on 2026-09-14, ref 523394871
        </p>
      </div>

      <div className="flex items-center gap-2 text-[13px] text-ink-muted">
        <span className="h-px flex-1 bg-hairline" aria-hidden="true" />
        <CheckCircle size={15} weight="fill" className="text-positive" aria-hidden="true" />
        <span>Filed as Food and Dining, today</span>
      </div>
    </div>
  );
}

/**
 * A real capture rather than an illustration of one.
 *
 * Rendered full-height, not cropped. A fixed-height crop on a tall image
 * hides the bottom of the screen, which is where the scan button the caption
 * points at actually is. The card is a portrait, so the height is driven by
 * the aspect ratio, with a sensible cap so it does not dominate the cell.
 */
function ReceiptShot() {
  return (
    <div className="mt-6 max-w-[220px]">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src="/screens/06-receipt.png"
        alt="The Add Expense screen, with the Scan receipt button that reads a total off a photographed bill."
        className="w-full rounded-lg border border-hairline-soft"
        loading="lazy"
      />
    </div>
  );
}

/** A real question and the real shape of the answer. */
function AssistantAsk() {
  return (
    <div className="mt-6 space-y-2 border-t border-hairline-soft pt-6">
      <p className="ml-auto max-w-[80%] rounded-lg rounded-tr-sm bg-green px-4 py-2 text-right text-[14px] text-white">
        How much did I spend on food in June?
      </p>
      <p className="max-w-[85%] rounded-lg rounded-tl-sm border border-hairline-soft bg-bone px-4 py-2 text-[14px] text-ink">
        4,180 across 31 transactions, averaging 135 a day.
      </p>
    </div>
  );
}