import { Footer, Nav } from "@/components/site/nav";
import { Features, Hero } from "@/components/site/hero";
import { Install } from "@/components/site/install";
import { Privacy } from "@/components/site/privacy";
import { Releases } from "@/components/site/releases";
import { Screens } from "@/components/site/screens";
import { getLatestRelease, getReleases } from "@/lib/releases";
import { app, repo, site } from "@/lib/site";

export const revalidate = 3600;

export default async function HomePage() {
  const releases = await getReleases();
  const latest = getLatestRelease(releases);

  return (
    <>
      <Nav />
      <main id="main">
        {latest ? null : <NoRelease />}

        <Hero latest={latest} />
        <Features />
        <Screens />
        <Privacy />
        <Install latest={latest} />
        <Releases releases={releases} />

        {latest ? <StructuredData latest={latest} /> : null}
      </main>
      <Footer />
    </>
  );
}

/**
 * The empty state.
 *
 * Rendered when the repository has no published APK. It has to be a composed
 * state rather than a hero with a missing button, because "no release yet" is
 * a legitimate condition for a repository that has not shipped one.
 */
function NoRelease() {
  return (
    <div className="border-b border-hairline bg-wash">
      <div className="mx-auto max-w-[1200px] px-5 py-4 md:px-8">
        <p className="text-[14px] text-ink-muted">
          No release is published yet. The rest of this page still describes the app, and the
          download button appears as soon as a build is attached to a GitHub release.
        </p>
      </div>
    </div>
  );
}

/**
 * SoftwareApplication structured data.
 *
 * Only rendered when there is a real release, because every field is read from
 * that release. There is no aggregate rating: the app has none, and inventing
 * one inside a JSON-LD block would put a false claim in the one place a
 * scraper reads without checking.
 */
function StructuredData({
  latest,
}: {
  latest: Awaited<ReturnType<typeof getReleases>>[number];
}) {
  const data = {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: site.name,
    description: site.description,
    url: site.url,
    applicationCategory: "FinanceApplication",
    operatingSystem: `Android ${app.minAndroid} or newer`,
    softwareVersion: latest.version,
    offers: {
      "@type": "Offer",
      price: "0",
      priceCurrency: "INR",
    },
    codeRepository: repo.url,
    downloadUrl: latest.url,
    softwareRequirements: `Android ${app.minAndroid}`,
    screenshot: `${site.url}/screens/01-dashboard.png`,
  };

  return (
    <script
      type="application/ld+json"
      // The object is built from typed data above, with no user input in it.
      dangerouslySetInnerHTML={{ __html: JSON.stringify(data) }}
    />
  );
}