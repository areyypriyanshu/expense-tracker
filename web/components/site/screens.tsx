import { Reveal } from "@/components/ui/reveal";

/**
 * The screen strip.
 *
 * Native scroll-snap and nothing else: no carousel arrows, no pagination dots,
 * no JS scroll listener. It stays keyboard reachable, it works with a
 * trackpad, and it costs nothing at runtime.
 */
const screens = [
  {
    src: "/screens/01-dashboard.png",
    alt: "Home screen showing this month's spending, a bar chart of recent days, and recent transactions.",
  },
  {
    src: "/screens/02-transactions.png",
    alt: "The transaction list with a search bar and category filter chips along the top.",
  },
  {
    src: "/screens/03-analytics.png",
    alt: "Analytics screen with a category donut chart and a bar chart of spending by day.",
  },
  {
    src: "/screens/04-budgets.png",
    alt: "Budget screen showing a monthly limit, how much is spent, and a progress bar toward the threshold.",
  },
  {
    src: "/screens/05-recurring.png",
    alt: "Recurring expenses screen listing subscriptions and bills that repeat on a schedule.",
  },
  {
    src: "/screens/06-receipt.png",
    alt: "Receipt scanning screen with the camera viewfinder and the extracted total.",
  },
  {
    src: "/screens/07-assistant.png",
    alt: "The finance assistant screen with a plain-language question and its answer.",
  },
  {
    src: "/screens/08-settings.png",
    alt: "Settings screen with base currency, budget threshold and data export options.",
  },
];

export function Screens() {
  return (
    <section id="screens" className="overflow-hidden py-20 md:py-28">
      <div className="mx-auto max-w-[1200px] px-5 md:px-8">
        <Reveal>
          <h2 className="max-w-[18ch] text-4xl font-semibold tracking-tighter text-balance text-ink md:text-5xl">
            Every screen, captured from the real app
          </h2>
          <p className="mt-5 max-w-[56ch] text-lg leading-relaxed text-ink-muted">
            Taken on an emulator running the build you download. Scroll sideways for the rest.
          </p>
        </Reveal>
      </div>

      {/* Full-bleed on purpose. A strip constrained to the container reads as
          a carousel with clipped edges; this reads as a shelf. */}
      <Reveal delay={0.06}>
        <div className="strip-scroll mt-14 flex gap-5 overflow-x-auto pb-4 md:pl-8">
          {screens.map((screen, index) => (
            <figure
              key={screen.src}
              className={`shrink-0 ${index === 0 ? "ml-5 md:ml-0" : ""}`}
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={screen.src}
                alt={screen.alt}
                width={900}
                height={1950}
                loading={index < 2 ? "eager" : "lazy"}
                className="h-[440px] w-auto rounded-lg border border-hairline object-cover object-top"
              />
            </figure>
          ))}
          {/* Trailing spacer so the last card can reach the snap point. */}
          <div className="w-1 shrink-0 md:w-8" aria-hidden="true" />
        </div>
      </Reveal>
    </section>
  );
}