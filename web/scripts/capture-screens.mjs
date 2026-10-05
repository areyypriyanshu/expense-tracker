#!/usr/bin/env node
/**
 * Captures real screenshots from the app running on an emulator.
 *
 * The site shows these in the hero and in the screen strip, so they have to be
 * the actual app rather than mockups. This script boots an AVD, installs the
 * debug APK, taps through to each screen, and writes a PNG per screen.
 *
 *   node scripts/capture-screens.mjs
 *
 * Exits non-zero if any capture fails. A silent partial capture is worse than
 * a failure, because the site would ship with a missing screen and no signal
 * that anything went wrong.
 */

import { execFile } from "node:child_process";
import { mkdir, readdir, stat, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { promisify } from "node:util";
import { fileURLToPath } from "node:url";

const exec = promisify(execFile);

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, "..");
const repoRoot = join(webRoot, "..");

const ANDROID_HOME = process.env.ANDROID_HOME ?? join(homedir(), "Library/Android/sdk");
const ADB = join(ANDROID_HOME, "platform-tools/adb");
const EMULATOR = join(ANDROID_HOME, "emulator/emulator");
const AVD = process.env.ANDROID_AVD ?? "Pixel_10";

const APK = join(
  repoRoot,
  "app/build/outputs/apk/debug/app-debug.apk",
);
const OUT_DIR = join(webRoot, "public/screens");

// `screencap` streams a whole PNG through stdout, and `uiautomator dump` returns
// a large XML document for a dense screen. Node's default 1 MB buffer is not
// enough for either once the app holds real data.
const adb = (args, options = {}) =>
  exec(ADB, args, { timeout: 120_000, maxBuffer: 64 * 1024 * 1024, ...options });
const shell = (command) => adb(["shell", command]);

/**
 * Navigation geometry, derived from the app rather than guessed.
 *
 * The bottom bar is a Row of five equally weighted cells inside a Surface that
 * is inset 16dp horizontally and 12dp from the bottom, with a height of 64dp
 * and the system's navigation-bar padding applied underneath. At this device's
 * density that places the row between roughly y = 2297 and y = 2465 in pixels,
 * and the usable row is centred in that band.
 *
 * The five cells are `Home, Transactions, Analytics, Budgets, Settings`
 * (see `Screen.bottomNavItems` in Navigation.kt), so a cell centre is
 * `width * (index + 0.5) / 5` across the full width, which matches the
 * `weight(1f)` layout exactly.
 *
 * `Recurring` and `Assistant` are not in the bottom bar. They are reached from
 * within Settings and Transactions respectively, so those two screens carry an
 * extra tap step rather than a nav index.
 */
const screens = [
  {
    file: "01-dashboard.png",
    label: "dashboard",
    steps: [],
  },
  {
    file: "02-transactions.png",
    label: "transactions",
    steps: [{ kind: "nav", index: 1 }],
  },
  {
    file: "03-analytics.png",
    label: "analytics",
    steps: [{ kind: "nav", index: 2 }],
  },
  {
    file: "04-budgets.png",
    label: "budgets",
    steps: [{ kind: "nav", index: 3 }],
  },
  {
    file: "08-settings.png",
    label: "settings",
    steps: [{ kind: "nav", index: 4 }],
  },
  {
    file: "05-recurring.png",
    label: "recurring",
    steps: [{ kind: "nav", index: 4 }, { kind: "text", contains: "Recurring Expenses" }],
  },
  {
    file: "07-assistant.png",
    label: "assistant",
    steps: [{ kind: "nav", index: 4 }, { kind: "text", contains: "Finance Assistant Chatbot" }],
  },
  {
    file: "06-receipt.png",
    label: "add expense with scan receipt",
    steps: [
      { kind: "nav", index: 1 },
      // No scroll step here. The screen hides the add button once the list
      // moves (`visible = !isScrolled` in TransactionsScreen.kt), so scrolling
      // to the top is what removes it. Freshly opened, the list is already at
      // the top and the button is on screen.
      //
      // Found by content description, which is how it is labelled for screen
      // readers, so the tap follows the button rather than a fixed pixel.
      { kind: "desc", contains: "Add Transaction" },
      { kind: "text", contains: "Scan receipt" },
    ],
  },
];

async function size() {
  const { stdout } = await shell("wm size");
  const match = /(\d+)x(\d+)/.exec(stdout);
  if (!match) throw new Error(`Could not read the device size from: ${stdout.trim()}`);

  // Density is needed to convert the app's 64dp navigation row into pixels.
  const { stdout: densityOut } = await shell("wm density");
  const density = Number.parseInt(/Physical density:\s*(\d+)/.exec(densityOut)?.[1] ?? "420", 10);
  const densityScale = density / 160;

  // The gesture inset is reported in the display frame as the third value in
  // `overrideNonDecorFrame`, for example [0,142][1080,2361]. The gap between
  // the bottom of that frame and the full screen height is the inset the app
  // pads itself away from.
  let systemInsetBottom = 0;
  try {
    const { stdout: displays } = await shell("dumpsys window displays");
    const frame = /overrideNonDecorFrame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]/.exec(displays);
    if (frame) {
      const frameBottom = Number(frame[4]);
      systemInsetBottom = Math.max(0, Number(match[2]) - frameBottom);
    }
  } catch {
    // A device that will not report the frame just uses the full height.
  }

  return {
    width: Number(match[1]),
    height: Number(match[2]),
    densityScale,
    systemInsetBottom,
  };
}

/**
 * Finds the y centre of the app's bottom navigation row by asking the device,
 * not by computing it.
 *
 * The bar is a 64dp Surface that sits 12dp above the system gesture inset, and
 * deriving that arithmetic from window insets is fragile across devices and
 * Android versions. Instead the accessibility tree is queried for the tab
 * labels and the midpoint of their shared bounds is taken. That is the same
 * source of truth a screen reader uses, so it cannot drift from what a person
 * would actually tap.
 */
/**
 * Asserts the app is the focused window.
 *
 * The failure this guards against is quiet: a tap that the launcher swallows
 * leaves the capture writing a screenshot of whatever is on screen, so every
 * file is produced and they all show the same thing. Checking the focus before
 * navigating turns that into an error naming the real problem.
 */
async function requireAppForeground(packageName) {
  const { stdout } = await shell("dumpsys window | grep mCurrentFocus");
  if (!stdout.includes(packageName)) {
    throw new Error(
      `The app is not in the foreground (focus is: ${stdout.trim()}). ` +
        `A tap is probably being read as a home gesture. ` +
        `Run the script with gesture navigation disabled.`,
    );
  }
}

/**
 * Dismisses the notification and SMS prompts shown on first launch.
 *
 * Both are skippable and both are photographed instead of the app otherwise.
 * Backing out is enough: neither prompt blocks the dashboard.
 */
async function dismissFirstLaunchPrompts() {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const { stdout } = await shell("dumpsys window | grep mCurrentFocus");
    if (!stdout.includes("com.expensetracker")) break;

    await shell("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1");
    const { stdout: xml } = await shell("cat /sdcard/ui.xml");

    const isPrompt =
      xml.includes("Turn on notifications") ||
      xml.includes("SMS Permission Required") ||
      xml.includes("UPI Sync");

    if (!isPrompt) break;

    await shell("input keyevent KEYCODE_BACK");
    await new Promise((resolve) => setTimeout(resolve, 2500));
  }
}

async function findNavRowY(device) {
  const labels = ["Home", "Transactions", "Analytics", "Budgets", "Settings"];
  const { stdout } = await shell("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && cat /sdcard/ui.xml");

  const centres = [];
  for (const label of labels) {
    const nodes = stdout.match(/<node[^>]*>/g) ?? [];
    for (const node of nodes) {
      const text = /text="([^"]*)"/.exec(node)?.[1] ?? "";
      if (text !== label) continue;
      const bounds = /bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/.exec(node);
      if (!bounds) continue;
      const [, , y1, , y2] = bounds.map(Number);
      centres.push((y1 + y2) / 2);
      break;
    }
  }

  if (centres.length === 0) {
    // Fall back to the arithmetic, which is right on a device that reports
    // insets the way this one does.
    return device.height - device.systemInsetBottom - Math.round((64 * device.densityScale) / 2);
  }

  return Math.round(centres.reduce((sum, value) => sum + value, 0) / centres.length);
}

/**
 * Forces three-button navigation.
 *
 * With gesture navigation enabled, a tap near the bottom of the screen is
 * read as a swipe towards home and the app goes to the launcher instead of
 * receiving it. That made the bottom bar untappable and every capture came
 * out as the same screen. Three-button navigation puts a real button strip
 * there, so the same tap reaches the app.
 */
async function forceThreeButtonNavigation() {
  const { stdout } = await shell(
    "cmd overlay list android | grep -E 'navbar.(gestural|threebutton)'",
  );

  if (!stdout.includes("gestural")) return;

  console.log("Switching the emulator to three-button navigation for the capture...");
  await shell("cmd overlay enable com.android.internal.systemui.navbar.threebutton");
  await shell("cmd overlay disable com.android.internal.systemui.navbar.gestural");
  await new Promise((resolve) => setTimeout(resolve, 4000));
}

async function isBooted() {
  try {
    const { stdout } = await shell("getprop sys.boot_completed");
    return stdout.trim() === "1";
  } catch {
    return false;
  }
}

async function waitForBoot(timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await isBooted()) return true;
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  return false;
}

/** `adb exec-out screencap -p` writes the PNG to stdout as binary. */
async function capture(file) {
  const { stdout } = await adb(["exec-out", "screencap", "-p"], { encoding: "buffer" });
  if (!stdout || stdout.length < 1024) {
    throw new Error(`Capture for ${file} produced ${stdout?.length ?? 0} bytes`);
  }
  await writeFile(file, stdout);
}

/**
 * Finds a node by visible text.
 *
 * Tapping a label rather than a guessed pixel is what makes this script
 * survive a layout change: if "Recurring" moves, the tap follows it.
 * Returns null when no node matches, so the caller can report a real failure
 * rather than silently capturing the wrong screen.
 */
async function findByText(needle) {
  return findNode((text) => text.includes(needle), "text");
}

/**
 * Finds a node by content description.
 *
 * Several controls in the app have no visible text at all, including the add
 * button, which is icon-only and labelled for screen readers instead. Those
 * cannot be found by text and need this.
 */
async function findByDescription(needle) {
  return findNode((text) => text.includes(needle), "content-desc");
}

async function findNode(matches, attribute) {
  await shell("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1");
  const { stdout } = await shell("cat /sdcard/ui.xml");

  const nodes = stdout.match(/<node[^>]*>/g) ?? [];
  for (const node of nodes) {
    const value = new RegExp(`${attribute}="([^"]*)"`).exec(node)?.[1] ?? "";
    if (!matches(value)) continue;

    const bounds = /bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/.exec(node);
    if (!bounds) continue;

    const [, x1, y1, x2, y2] = bounds.map(Number);
    return { x: Math.round((x1 + x2) / 2), y: Math.round((y1 + y2) / 2) };
  }

  return null;
}

/**
 * Scrolls the current list back to the top.
 *
 * Several screens hide their floating button the moment the list moves, so a
 * screen reached from a scrolled position has no button to find. Two swipes in
 * the scroll direction bring it back without overshooting into another screen
 * the way a single long swipe can.
 */
async function scrollToTop(device) {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await shell(
      `input swipe ${Math.round(device.width / 2)} ${Math.round(device.height * 0.8)} ` +
        `${Math.round(device.width / 2)} ${Math.round(device.height * 0.25)} 250`,
    );
    await new Promise((resolve) => setTimeout(resolve, 1200));
  }
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true });

  const device = await size().catch(() => null);
  if (!device) {
    const { stdout } = await exec(ADB, ["devices"]).catch(() => ({ stdout: "" }));
    if (!stdout.includes("device")) {
      console.error(`No emulator is running for AVD "${AVD}".`);
      console.error("Start it with:");
      console.error(`  ${EMULATOR} -avd ${AVD} -no-snapshot -no-boot-anim`);
      process.exit(1);
    }
    throw new Error("An emulator is attached but did not report a screen size.");
  }

  const booted = await isBooted();
  if (!booted) {
    console.log("Waiting for the device to finish booting...");
    if (!(await waitForBoot())) {
      console.error("The device did not finish booting in time.");
      process.exit(1);
    }
  }

  console.log(`Device is ${device.width}x${device.height}.`);

  await adb(["install", "-r", "-t", APK]);
  const packageName = "com.expensetracker";

  // Done before the app is launched, because the navigation bar changes where
  // the app's own bottom row sits, and the row is measured below.
  await forceThreeButtonNavigation();

  await shell(`am force-stop ${packageName}`);
  await shell(`am start -n ${packageName}/.MainActivity`);

  // Let the first frame land and any first-launch prompts settle.
  await new Promise((resolve) => setTimeout(resolve, 7000));
  await dismissFirstLaunchPrompts();
  await requireAppForeground(packageName);

  // Measured on the running app, so the row is found even on a device with an
  // unusual inset or a different navigation mode.
  device.navRowY = await findNavRowY(device);
  console.log(`Bottom navigation row is centred at y = ${device.navRowY}.`);

  const failures = [];

  for (const screen of screens) {
    // Always return to a known state before navigating. The wait is long
    // because the first frame after a cold start includes opening the Room
    // database and running the first-launch checks; tapping the nav bar
    // before that finishes lands on the wrong screen and the next step fails
    // with a label it cannot find.
    await shell(`am force-stop ${packageName}`);
    await shell(`am start -n ${packageName}/.MainActivity`);
    await new Promise((resolve) => setTimeout(resolve, 6000));

    // Dismiss the first-launch prompts if they appear. They only show once
    // per install, but a capture that hits them photographs a dialog instead
    // of the app.
    await dismissFirstLaunchPrompts();

    // Prove the app is in the foreground before navigating. A tap that reaches
    // the launcher instead is silent: the capture succeeds and every file
    // comes out showing the same screen, which is exactly what happened before
    // this check existed.
    await requireAppForeground(packageName);

    for (const step of screen.steps) {
      if (step.kind === "nav") {
        // Five equally weighted cells across the full width, matching the
        // `weight(1f)` layout, tapped on the row's measured centre.
        const x = Math.round((device.width / 5) * (step.index + 0.5));
        await shell(`input tap ${x} ${device.navRowY}`);
      } else if (step.kind === "scrollTop") {
        await scrollToTop(device);
      } else if (step.kind === "desc") {
        const target = await findByDescription(step.contains);
        if (!target) {
          throw new Error(
            `Could not find a control described as "${step.contains}". ` +
              `The screen may have hidden it, or it may have been renamed.`,
          );
        }
        await shell(`input tap ${target.x} ${target.y}`);
      } else if (step.kind === "tap") {
        await shell(`input tap ${step.x} ${step.y}`);
      } else {
        const target = await findByText(step.contains);
        if (!target) {
          throw new Error(`Could not find a node containing "${step.contains}"`);
        }
        await shell(`input tap ${target.x} ${target.y}`);
      }
      await new Promise((resolve) => setTimeout(resolve, 2500));
    }

    await new Promise((resolve) => setTimeout(resolve, 1200));

    const target = join(OUT_DIR, screen.file);
    try {
      await capture(target);
      const { size: bytes } = await stat(target);
      console.log(`  ${screen.file.padEnd(22)} ${screen.label.padEnd(12)} ${(bytes / 1024).toFixed(0)} KB`);
    } catch (error) {
      console.error(`  ${screen.file.padEnd(22)} FAILED: ${error.message}`);
      failures.push(screen.file);
    }
  }

  const written = await readdir(OUT_DIR).catch(() => []);
  console.log(`\nWrote ${written.length} screenshots to ${OUT_DIR}`);

  if (failures.length > 0) {
    console.error(`\n${failures.length} capture(s) failed: ${failures.join(", ")}`);
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});