# Deploying the site

The site is static. It has no backend, no database and no file storage: the
APKs live on GitHub Releases and the pages read the release list from the GitHub
API at build time. That is the whole architecture, and it is why it deploys
free anywhere.

## Vercel (free)

The zero-configuration path.

1. Push this repository to GitHub. It is already there.
2. Import the repo at [vercel.com/new](https://vercel.com/new).
3. Set the **Root Directory** to `web`.
4. Add one environment variable:

   ```
   NEXT_PUBLIC_SITE_URL=https://your-domain.example
   ```

5. Deploy.

`GITHUB_TOKEN` is optional. Without it, builds are limited to GitHub's 60
anonymous API requests per hour, and a build that hits the limit falls back to
the committed snapshot in `lib/releases.snapshot.json`, which is a valid page.
To avoid that, add a token with public read access under
**Settings → Environment Variables**.

## Any static host (free)

Cloudflare Pages, Netlify, GitHub Pages and similar all work.

```bash
pnpm build
# The output directory is .next, but the portable version is:
npx @vercel/nft --help   # or export the site and serve it as static HTML
```

Because the site revalidates release data hourly, a purely static export would
show a stale release list until the next deploy. Run `pnpm sync`, commit the
updated snapshot and redeploy when you publish. If you would rather the page
always show the newest release, use a host that supports Next.js incremental
regeneration (Vercel, or the Docker image below), which does not need any
configuration at all.

## Docker (self-hosted, free tier)

Oracle Cloud Always Free and similar give a small VM on a permanent free tier,
which is enough: this site serves HTML and a handful of small images.

```bash
cd web
docker build -t expense-tracker-site .
docker run -p 3000:3000 \
  -e NEXT_PUBLIC_SITE_URL=https://your-domain.example \
  expense-tracker-site
```

## Publishing a new app release

The site never handles the APK. Publishing is one command:

```bash
export GITHUB_TOKEN=ghp_...          # needs contents: write
cd web
pnpm publish --version 1.5.1 --notes-file ../notes.md
```

That runs `assembleRelease`, refuses to proceed if release signing is not
configured, creates the tag, uploads the APK to the GitHub release, and
refreshes `lib/releases.snapshot.json`.

### Release signing is required

`publish.mjs` will not publish an unsigned build, and that rule is not
flexible. Android refuses to install a differently signed APK over an existing
one, so shipping an unsigned build after a signed one breaks the upgrade path
for everyone who already has the app.

Create a keystore once:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA \
  -keysize 2048 -validity 10000 -alias release
```

Then add these to `~/.gradle/gradle.properties`, **not** to the repository:

```properties
RELEASE_STORE_FILE=/absolute/path/to/release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=release
RELEASE_KEY_PASSWORD=...
```

Keep that file and the keystore backed up. Losing either means existing
installs cannot be updated.

## Refreshing the screenshots

The screenshots on the site are real captures from the emulator, not mockups.
Two commands are needed, in order.

```bash
# Start an emulator first:
~/Library/Android/sdk/emulator/emulator -avd Pixel_10 -no-snapshot -no-boot-anim &

cd web
pnpm seed    # loads a month of realistic transactions into the emulator
pnpm capture # captures one PNG per screen
```

`pnpm seed` is not optional. The app seeds its categories but no transactions,
so without it every screen shows a zero total and empty charts, which is what
made the first version of this site look broken. It writes a month of data dated
relative to today, so the dashboard, the analytics charts and the budget
progress all have something real to show and stay correct when the month rolls
over.

`pnpm capture` installs the debug APK, drives the app with `adb`, and writes a
PNG per screen into `public/screens/`. It exits non-zero if any screen fails, so
a broken capture cannot pass silently. Commit the result.

Both scripts switch the emulator to three-button navigation for the run. With
gesture navigation on, a tap near the bottom of the screen is read as a swipe
towards home, which makes the app's own navigation bar untappable and silently
produces eight identical screenshots.

## Local development

```bash
cd web
pnpm install
pnpm dev          # http://localhost:3000
pnpm test         # 43 unit tests, no network needed
pnpm lint
pnpm typecheck
pnpm build
```

## Commands

| Command | What it does |
| --- | --- |
| `pnpm dev` | Development server |
| `pnpm build` | Production build, fully static |
| `pnpm test` | Unit tests |
| `pnpm lint` | ESLint |
| `pnpm typecheck` | TypeScript, no emit |
| `pnpm sync` | Refresh the release snapshot from GitHub |
| `pnpm seed` | Load demo transactions into the emulator |
| `pnpm capture` | Re-capture screenshots from the emulator |
| `pnpm publish` | Build, tag, upload and sync a new release |