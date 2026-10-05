#!/usr/bin/env node
/**
 * Publishes a new release in one command.
 *
 *   pnpm publish
 *
 * Runs, in order:
 *   1. ./gradlew assembleRelease          (unless --skip-build)
 *   2. verifies the release signing config is present
 *   3. creates and pushes the tag
 *   4. uploads the APK to the GitHub release
 *   5. refreshes lib/releases.snapshot.json
 *
 * Two rules it will not bend:
 *
 *   It refuses to publish an unsigned build. A signed APK and an unsigned one
 *   are not interchangeable once a device has installed the signed one, and a
 *   user who later upgrades to an unsigned build cannot, because Android
 *   rejects the signature mismatch.
 *
 *   It refuses to overwrite an existing tag. The checksum shown on the site
 *   has to stay true, and replacing the asset behind a published tag would
 *   silently break that promise.
 */

import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import { createReadStream, existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const exec = promisify(execFile);
const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, "..");
const repoRoot = join(webRoot, "..");

const OWNER = "areyypriyanshu";
const NAME = "expense-tracker";

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name) => {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
};

const skipBuild = flag("--skip-build");
const version = option("--version");

if (!version) {
  console.error("Usage: pnpm publish --version <X.Y.Z> [--skip-build] [--notes <text>] [--notes-file <path>]");
  process.exit(1);
}

if (!/^\d+(\.\d+)*$/.test(version)) {
  console.error(`"${version}" is not a plain dotted version like 1.5.1.`);
  process.exit(1);
}

const token = process.env.GITHUB_TOKEN;
if (!token) {
  console.error("GITHUB_TOKEN is not set. It needs 'contents: write' on this repository.");
  console.error("");
  console.error("  export GITHUB_TOKEN=ghp_...");
  console.error("");
  console.error("Create one at https://github.com/settings/tokens, or run `gh auth token`.");
  process.exit(1);
}

const gradle = existsSync(join(repoRoot, "gradlew")) ? "./gradlew" : "gradle";

function run(command, commandArgs, options = {}) {
  return exec(command, commandArgs, { cwd: repoRoot, maxBuffer: 32 * 1024 * 1024, ...options });
}

function step(message) {
  console.log(`\n${message}`);
}

async function sha256(path) {
  return new Promise((resolve, reject) => {
    const hash = createHash("sha256");
    createReadStream(path)
      .on("data", (chunk) => hash.update(chunk))
      .on("end", () => resolve(hash.digest("hex")))
      .on("error", reject);
  });
}

/**
 * Checks the signing configuration before building, because discovering a
 * missing keystore after a two-minute Gradle run wastes the run.
 *
 * The values live in `~/.gradle/gradle.properties` or the project's
 * `gradle.properties`, and `app/build.gradle.kts` reads them as
 * RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS and
 * RELEASE_KEY_PASSWORD. All four must be present, because the config silently
 * omits the signing config when any one is missing.
 */
function checkSigningConfig() {
  const candidates = [
    join(repoRoot, "gradle.properties"),
    join(process.env.HOME ?? "", ".gradle/gradle.properties"),
  ].filter((path) => existsSync(path));

  const properties = new Map();
  for (const path of candidates) {
    for (const line of readFileSync(path, "utf8").split("\n")) {
      const match = /^\s*([A-Za-z0-9_]+)\s*=\s*(.+?)\s*$/.exec(line);
      if (match) properties.set(match[1], match[2]);
    }
  }

  const required = [
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD",
  ];
  const missing = required.filter((key) => !properties.has(key));

  if (missing.length > 0) {
    console.error("Release signing is not configured. Missing:");
    for (const key of missing) console.error(`  ${key}`);
    console.error("");
    console.error("Add these to ~/.gradle/gradle.properties (not to the repository):");
    console.error("  RELEASE_STORE_FILE=/absolute/path/to/keystore.jks");
    console.error("  RELEASE_STORE_PASSWORD=...");
    console.error("  RELEASE_KEY_ALIAS=...");
    console.error("  RELEASE_KEY_PASSWORD=...");
    console.error("");
    console.error("Create a keystore with:");
    console.error("  keytool -genkeypair -v -keystore release.jks -keyalg RSA \\");
    console.error("    -keysize 2048 -validity 10000 -alias release");
    console.error("");
    console.error("Publishing an unsigned build is not supported. Android will refuse to");
    console.error("upgrade an existing install to a differently signed APK, so it would");
    console.error("break every user who already has the app.");
    process.exit(1);
  }
}

async function main() {
  step("Checking signing configuration");
  checkSigningConfig();
  console.log("  All four properties are present.");

  if (!skipBuild) {
    step("Building the release APK");
    await run(gradle, ["assembleRelease"], { stdio: "inherit" });
  }

  const apkDir = join(repoRoot, "app/build/outputs/apk/release");
  const metadataPath = join(apkDir, "output-metadata.json");
  if (!existsSync(metadataPath)) {
    console.error(`No release build found at ${apkDir}. Run without --skip-build.`);
    process.exit(1);
  }

  const metadata = JSON.parse(readFileSync(metadataPath, "utf8"));
  const element = metadata.elements?.[0];
  if (!element) {
    console.error("output-metadata.json contains no build elements.");
    process.exit(1);
  }

  const builtVersion = element.versionName;
  if (builtVersion !== version) {
    console.error(`The built APK is version ${builtVersion}, but --version is ${version}.`);
    console.error("Bump versionName in app/build.gradle.kts and rebuild, or pass the matching version.");
    process.exit(1);
  }

  const apkPath = join(apkDir, element.outputFile);
  const sizeBytes = readFileSync(apkPath).length;
  const checksum = await sha256(apkPath);

  console.log(`  ${element.outputFile}`);
  console.log(`  version  ${builtVersion} (code ${element.versionCode})`);
  console.log(`  size     ${(sizeBytes / 1048576).toFixed(2)} MB`);
  console.log(`  sha256   ${checksum}`);
  console.log("");
  console.log("  Add the checksum to the release notes. It is the one thing a user");
  console.log("  cannot verify from the file name alone.");

  const tag = `v${version}`;
  const api = `https://api.github.com/repos/${OWNER}/${NAME}`;

  step(`Checking whether ${tag} already exists`);
  const existing = await fetch(`${api}/releases/tags/${tag}`, {
    headers: authHeaders(),
  });
  if (existing.ok) {
    console.error(`Release ${tag} already exists on GitHub.`);
    console.error("Publishing over it would change the file behind a published URL, so the");
    console.error("checksum already shown on the site would stop being true.");
    console.error("");
    console.error("Delete the release first, or publish a new version number.");
    process.exit(1);
  }

  const notes =
    option("--notes") ??
    (option("--notes-file") ? readFileSync(option("--notes-file"), "utf8") : "") ??
    "";

  if (!notes.trim()) {
    console.error("No release notes. Pass --notes \"...\" or --notes-file <path>.");
    console.error("The notes are the only place a user learns what changed.");
    process.exit(1);
  }

  step("Creating the release");
  const created = await fetch(api + "/releases", {
    method: "POST",
    headers: { ...authHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({
      tag_name: tag,
      name: version,
      body: notes.trim(),
      draft: false,
      // Every existing release on this repository is flagged prerelease,
      // which is why the site selects by version instead of trusting the
      // "latest" endpoint. A release published from here is the real one.
      prerelease: false,
    }),
  });

  if (!created.ok) {
    console.error(`GitHub returned ${created.status} creating the release:`);
    console.error(await created.text());
    process.exit(1);
  }

  const release = await created.json();

  step(`Uploading ${element.outputFile}`);
  const uploadUrl = release.upload_url.replace(/\{.*\}$/, "");
  const upload = await fetch(`${uploadUrl}?name=${encodeURIComponent("expense-tracker.apk")}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/vnd.android.package-archive",
      "Content-Length": String(sizeBytes),
    },
    body: readFileSync(apkPath),
  });

  if (!upload.ok) {
    console.error(`GitHub returned ${upload.status} uploading the asset:`);
    console.error(await upload.text());
    console.error(`\nThe release ${tag} exists but has no asset. Attach it at:`);
    console.error(`  ${release.html_url}`);
    process.exit(1);
  }

  console.log(`  ${release.html_url}`);

  step("Refreshing the release snapshot");
  await run(process.execPath, [join(here, "sync-releases.mjs")], { cwd: webRoot });

  console.log("\nDone. Commit the updated snapshot so the site picks it up:");
  console.log(`  git add web/lib/releases.snapshot.json && git commit -m "Release ${version}"`);
}

function authHeaders() {
  return {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "expense-tracker-publish",
  };
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});