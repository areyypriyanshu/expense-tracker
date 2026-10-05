import Link from "next/link";

import { Reveal } from "@/components/ui/reveal";
import { repo, site } from "@/lib/site";

/**
 * Navigation.
 *
 * One line on desktop at 68px, well under the 80px cap. Mobile collapses to a
 * disclosure menu, which is the only client component on an otherwise static
 * server-rendered header.
 */
export function Nav() {
  const links = [
    { href: "#app", label: "App" },
    { href: "#privacy", label: "Privacy" },
    { href: "#install", label: "Install" },
    { href: "/releases", label: "Releases" },
    { href: "/feedback", label: "Feedback" },
  ];

  return (
    <header className="sticky top-0 z-50 border-b border-hairline bg-bone/85 backdrop-blur-md">
      <nav
        aria-label="Main"
        className="mx-auto flex h-[68px] max-w-[1200px] items-center justify-between gap-6 px-5 md:px-8"
      >
        <Link
          href="/"
          className="flex items-center gap-2.5 text-[15px] font-medium text-ink transition-opacity duration-200 hover:opacity-70"
        >
          <Mark />
          {site.name}
        </Link>

        {/* Desktop links. Short labels so the row never wraps at 1024px. */}
        <ul className="hidden items-center gap-7 md:flex">
          {links.map((link) => (
            <li key={link.href}>
              <Link
                href={link.href}
                className="text-[15px] text-ink-muted transition-colors duration-200 hover:text-ink"
              >
                {link.label}
              </Link>
            </li>
          ))}
        </ul>

        <a
          href="#install"
          className="hidden rounded-lg bg-green px-4 py-2 text-[14px] font-medium text-white transition-colors duration-200 hover:bg-green-hover md:inline-flex"
        >
          Download
        </a>

        {/* Mobile: a native disclosure, no JS. It is a real <details>, so it
            is keyboard operable and works with JavaScript disabled. */}
        <details className="group md:hidden">
          <summary className="flex cursor-pointer list-none items-center rounded-md border border-hairline px-3 py-1.5 text-[14px] text-ink marker:hidden">
            Menu
            <span
              className="ml-1.5 inline-block transition-transform duration-200 group-open:rotate-45"
              aria-hidden="true"
            >
              +
            </span>
          </summary>
          <ul className="absolute right-5 left-5 mt-2 rounded-lg border border-hairline bg-surface py-2 shadow-[0_16px_40px_-24px_rgba(29,33,31,0.4)]">
            {links.map((link) => (
              <li key={link.href}>
                <Link
                  href={link.href}
                  className="block px-4 py-2.5 text-[15px] text-ink transition-colors hover:bg-wash"
                >
                  {link.label}
                </Link>
              </li>
            ))}
            <li className="mt-1 border-t border-hairline-soft px-4 pt-3 pb-2">
              <a
                href="#install"
                className="block rounded-lg bg-green px-4 py-2.5 text-center text-[14px] font-medium text-white"
              >
                Download
              </a>
            </li>
          </ul>
        </details>
      </nav>
    </header>
  );
}

/**
 * The brand mark.
 *
 * Drawn from the app's own launcher icon: a coin with a horizontal slot, in
 * the app's primary green. It is the one hand-drawn SVG on the site, and it is
 * a logo rather than an icon, which is why it is not sourced from the icon
 * library the interface uses.
 */
export function Mark({ size = 22 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      role="img"
      aria-label={`${site.name} logo`}
    >
      <circle cx="12" cy="12" r="11" fill="#0F3D34" />
      <path d="M7.5 9.75h9M7.5 14.25h9" stroke="#FAF8F2" strokeWidth="1.75" strokeLinecap="round" />
    </svg>
  );
}

/**
 * Footer.
 *
 * Quiet by design. It carries the one fact a reader might want after deciding
 * not to download: this is an independent project, not a Play Store listing,
 * and the source is public.
 */
export function Footer() {
  return (
    <footer className="border-t border-hairline py-14">
      <div className="mx-auto max-w-[1200px] px-5 md:px-8">
        <div className="flex flex-wrap items-start justify-between gap-8">
          <div>
            <div className="flex items-center gap-2.5 text-[15px] font-medium text-ink">
              <Mark />
              {site.name}
            </div>
            <p className="mt-3 max-w-[44ch] leading-relaxed text-[14px] text-ink-muted">
              An independent Android app. Not distributed through the Play Store, and it asks for
              nothing it does not say it needs.
            </p>
          </div>

          <ul className="flex flex-col gap-2.5 text-[14px]">
            <li>
              <Link href="/" className="text-ink-muted transition-colors hover:text-ink">
                Home
              </Link>
            </li>
            <li>
              <Link href="/releases" className="text-ink-muted transition-colors hover:text-ink">
                All releases
              </Link>
            </li>
            <li>
              <Link href="/privacy" className="text-ink-muted transition-colors hover:text-ink">
                Privacy
              </Link>
            </li>
            <li>
              <Link href="/feedback" className="text-ink-muted transition-colors hover:text-ink">
                Feedback
              </Link>
            </li>
          </ul>

          <ul className="flex flex-col gap-2.5 text-[14px]">
            <li>
              <a
                href={repo.url}
                className="text-ink-muted transition-colors hover:text-ink"
                rel="noopener noreferrer"
              >
                Source code
              </a>
            </li>
            <li>
              <a
                href={`${repo.url}/releases`}
                className="text-ink-muted transition-colors hover:text-ink"
                rel="noopener noreferrer"
              >
                Releases on GitHub
              </a>
            </li>
          </ul>
        </div>

        <div className="mt-12 border-t border-hairline-soft pt-6">
          <Reveal>
            <p className="text-[13px] text-ink-muted">
              Built with Kotlin and Jetpack Compose. Source available under the licence published
              with the repository.
            </p>
          </Reveal>
        </div>
      </div>
    </footer>
  );
}