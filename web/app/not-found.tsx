import Link from "next/link";

import { Footer, Nav } from "@/components/site/nav";

/**
 * Not found.
 *
 * Written as a composed state in the site's own language rather than a default
 * screen, because this site has three routes and a visitor who lands on a
 * fourth has almost certainly followed a stale link.
 */
export default function NotFound() {
  return (
    <>
      <Nav />
      <main id="main" className="py-28 md:py-36">
        <div className="mx-auto max-w-[640px] px-5 md:px-8">
          <p className="font-mono text-[13px] tracking-wide text-ink-muted uppercase">404</p>
          <h1 className="mt-5 text-4xl font-semibold tracking-tighter text-balance text-ink md:text-5xl">
            There is nothing at this address
          </h1>
          <p className="mt-5 leading-relaxed text-ink-muted">
            The page you asked for does not exist. The app, the releases and the privacy statement
            are all still where you left them.
          </p>
          <div className="mt-9 flex flex-wrap gap-4">
            <Link
              href="/"
              className="rounded-lg bg-green px-5 py-3 text-[15px] font-medium text-white transition-colors duration-200 hover:bg-green-hover"
            >
              Go to the start
            </Link>
            <Link
              href="/releases"
              className="rounded-lg border border-hairline px-5 py-3 text-[15px] font-medium text-ink transition-colors duration-200 hover:bg-wash"
            >
              All releases
            </Link>
          </div>
        </div>
      </main>
      <Footer />
    </>
  );
}