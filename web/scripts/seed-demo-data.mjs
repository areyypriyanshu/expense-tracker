#!/usr/bin/env node
/**
 * Seeds the emulator's database with a realistic month of transactions.
 *
 * Why this exists
 * ---------------
 * The app seeds its default categories but no transactions, so a fresh install
 * shows an empty dashboard: a zero total, an empty bar chart and an empty
 * donut. That makes for a poor screenshot and a poor first impression on the
 * website, and it hides the parts of the app worth looking at.
 *
 * Why dates are generated rather than fixed
 * -----------------------------------------
 * The dashboard reads "This Month", "This Week" and "Today" from the current
 * date. Fixed dates would fall out of those windows the moment the month
 * turned over, and the screenshots would quietly go back to showing zero. Every
 * date here is computed from today, so a capture is always current.
 *
 * How it works
 * ------------
 * The device has no sqlite3 binary, so the database is pulled with `run-as`,
 * written locally, and pushed back. The app must be stopped while this runs,
 * or Room will overwrite the file from its in-memory state on next write.
 *
 *   node scripts/seed-demo-data.mjs
 */

import { execFile } from "node:child_process";
import { existsSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const exec = promisify(execFile);

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, "..");

const ANDROID_HOME = process.env.ANDROID_HOME ?? join(homedir(), "Library/Android/sdk");
const ADB = join(ANDROID_HOME, "platform-tools/adb");
const SQLITE = process.env.SQLITE3 ?? join(ANDROID_HOME, "platform-tools/sqlite3");

const PACKAGE = "com.expensetracker";
const DEVICE = process.env.ANDROID_SERIAL ?? "emulator-5554";
const DB_NAME = "expense_database";

const adb = (args, options = {}) =>
  exec(ADB, ["-s", DEVICE, ...args], { maxBuffer: 64 * 1024 * 1024, ...options });

const adbShell = (command) => adb(["shell", command]);
const runAs = (command) => adbShell(`run-as ${PACKAGE} ${command}`);

/** Runs a SQL script against a file and returns stdout. */
function runSqlite(dbPath, script) {
  return new Promise((resolve, reject) => {
    const child = execFile(SQLITE, [dbPath], { maxBuffer: 32 * 1024 * 1024 }, (error, stdout) => {
      if (error) return reject(error);
      resolve(stdout);
    });
    child.stdin?.end(script);
  });
}

/**
 * The demo month.
 *
 * Amounts are drawn from a fixed table rather than randomly, so a capture is
 * reproducible: the same run produces the same screenshots, which makes a diff
 * between two captures meaningful. Every category in this list is one the app
 * seeds by default, so nothing references a category that does not exist.
 */
const SPEND = [
  // category, note, amount
  ["Food & Dining", "Swiggy order", 450],
  ["Food & Dining", "Zomato", 620],
  ["Food & Dining", "Groceries and snacks", 1180],
  ["Food & Dining", "Filter coffee", 80],
  ["Food & Dining", "Dinner with family", 2340],
  ["Transportation", "Uber to office", 340],
  ["Transportation", "Metro recharge", 500],
  ["Transportation", "Petrol", 2400],
  ["Transportation", "Auto to airport", 780],
  ["Shopping", "Amazon order", 1499],
  ["Shopping", "Running shoes", 2799],
  ["Shopping", "Headphones", 3499],
  ["Entertainment", "Movie tickets", 900],
  ["Entertainment", "Streaming subscription", 649],
  ["Bills & Utilities", "Electricity bill", 1850],
  ["Bills & Utilities", "Mobile postpaid", 799],
  ["Bills & Utilities", "Internet broadband", 1199],
  ["Healthcare", "Pharmacy", 640],
  ["Healthcare", "Dental checkup", 1200],
  ["Education", "Online course", 2999],
  ["Personal Care", "Haircut", 400],
  ["Personal Care", "Toiletries", 950],
  ["Travel", "Weekend trip", 6400],
  ["Groceries", "Weekly groceries", 2450],
  ["Groceries", "Fruits and vegetables", 780],
  ["Other", "Gift for a friend", 1500],
  ["Other", "Stationery", 320],
];

const INCOME = [
  ["Salary", "Monthly salary", 85000],
  ["Refund", "Amazon refund", 1299],
];

const pad = (n) => String(n).padStart(2, "0");

/** ISO local date-time, which is the format Room stores a LocalDateTime as. */
function iso(date) {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

function buildInserts(now) {
  const rows = [];
  const year = now.getFullYear();
  const month = now.getMonth();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const today = now.getDate();

  let id = 1000;
  const push = (category, note, amount, day, hour, isIncome) => {
    // Day is clamped to the month and never later than today, so the data can
    // never claim a future expense.
    const clampedDay = Math.min(Math.max(1, day), daysInMonth, today);
    const when = new Date(year, month, clampedDay, hour, (day * 7) % 60, 0);
    rows.push(
      `INSERT INTO transactions (id, amount, currency, category, note, date, isRecurring, recurringRuleId, isIncome)` +
        ` VALUES (${id}, ${amount}, 'INR', '${category.replace(/'/g, "''")}', '${note.replace(/'/g, "''")}',` +
        ` '${iso(when)}', 0, NULL, ${isIncome ? 1 : 0});`,
    );
    id += 1;
  };

  // Spending is spread across the month so the bar chart has a shape instead
  // of a flat block, rather than every entry landing on the same day.
  const step = Math.max(1, Math.round((daysInMonth - 1) / SPEND.length));
  SPEND.forEach(([category, note, amount], index) => {
    const day = 1 + ((index * step) % Math.max(1, today - 1 || 1));
    // Vary the amount slightly so identical prices do not look synthetic.
    const jitter = ((index % 5) - 2) * 0.03;
    push(category, note, Math.round(amount * (1 + jitter)), day, 8 + (index % 12), false);
  });

  // Guarantee activity on today and yesterday so "Today" and "This Week" are
  // never empty in a capture.
  if (today >= 1) push("Food & Dining", "Lunch", 380, today, 13, false);
  if (today >= 2) push("Transportation", "Auto to work", 120, today - 1, 9, false);
  if (today >= 3) push("Food & Dining", "Breakfast", 210, today - 2, 8, false);

  for (const [category, note, amount] of INCOME) {
    push(category, note, amount, Math.min(2, today), 10, true);
  }

  return rows;
}

function buildBudgets() {
  // The dashboard renders a card per budget, and a DAILY budget shows up
  // alongside the monthly ones. Without one the daily card renders as
  // "0.00 of 0.00", which is the exact empty-looking state these screenshots
  // are meant to avoid. Two days of spending are also in the seeded data, so
  // the daily figure is non-zero.
  const budgets = [
    ["Food & Dining", 8000, "MONTHLY"],
    ["Transportation", 6000, "MONTHLY"],
    ["Shopping", 10000, "MONTHLY"],
    ["Entertainment", 3000, "MONTHLY"],
    ["Overall", 2000, "DAILY"],
  ];
  const rows = [];
  let id = 2000;
  for (const [category, limit, period] of budgets) {
    rows.push(
      `INSERT INTO budgets (id, category, \`limit\`, currency, period, alertThreshold)` +
        ` VALUES (${id}, '${category}', ${limit}, 'INR', '${period}', 0.8);`,
    );
    id += 1;
  }
  return rows;
}

function buildRecurring() {
  const rows = [];
  const today = new Date();
  const next = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 12, 9, 0, 0);
  const rules = [
    ["Rent", "Bills & Utilities", 18000, "MONTHLY"],
    ["Netflix", "Entertainment", 649, "MONTHLY"],
    ["Gym membership", "Personal Care", 1500, "MONTHLY"],
  ];
  let id = 3000;
  for (const [note, category, amount, frequency] of rules) {
    rows.push(
      `INSERT INTO recurring_rules (id, amount, currency, category, note, frequency, nextDate, isActive)` +
        ` VALUES (${id}, ${amount}, 'INR', '${category}', '${note}', '${frequency}', '${iso(next)}', 1);`,
    );
    id += 1;
  }
  return rows;
}

async function main() {
  if (!existsSync(SQLITE)) {
    console.error(`sqlite3 not found at ${SQLITE}. Set SQLITE3 to its path.`);
    process.exit(1);
  }

  const boot = await adbShell("getprop sys.boot_completed").then((r) => r.stdout.trim());
  if (boot !== "1") {
    console.error("The emulator is not booted.");
    process.exit(1);
  }

  const work = mkdtempSync(join(tmpdir(), "expense-seed-"));
  const localDb = join(work, DB_NAME);

  // The database does not exist until the app has been launched once, which is
  // also what seeds the default categories. On a fresh install, or after
  // `pm clear`, the app is started here rather than failing on a missing file
  // further down.
  const appHasDatabase = async () => {
    try {
      const { stdout } = await adb([
        "exec-out",
        "run-as",
        PACKAGE,
        "sh",
        "-c",
        "test -d databases && echo yes || echo no",
      ]);
      return stdout.includes("yes");
    } catch {
      return false;
    }
  };

  if (!(await appHasDatabase())) {
    console.log("No database yet. Launching the app once so it creates one...");
    await adbShell(`monkey -p ${PACKAGE} -c android.intent.category.LAUNCHER 1`);
    await new Promise((resolve) => setTimeout(resolve, 8000));
    await adbShell(`am force-stop ${PACKAGE}`);

    if (!(await appHasDatabase())) {
      console.error("The app did not create a database. Check that it is installed.");
      process.exit(1);
    }
  }

  // Pull all three files. The write-ahead log holds recent commits, so pulling
  // only the main database would silently discard them.
  console.log("Pulling the database...");
  for (const suffix of ["", "-wal", "-shm"]) {
    const remote = `databases/${DB_NAME}${suffix}`;
    try {
      const { stdout } = await adb(["exec-out", "run-as", PACKAGE, "cat", remote], {
        encoding: "buffer",
      });
      if (stdout?.length) writeFileSync(`${localDb}${suffix}`, stdout);
    } catch {
      // A missing -wal or -shm is fine; it just means nothing is pending.
    }
  }

  if (!existsSync(localDb)) {
    console.error("Could not read the database from the device.");
    process.exit(1);
  }

  // Force a checkpoint so everything lands in the main file, and drop the
  // stale log so the pushed database is self-contained.
  await runSqlite(localDb, "PRAGMA wal_checkpoint(TRUNCATE);").catch(() => {});
  for (const suffix of ["-wal", "-shm"]) {
    const path = `${localDb}${suffix}`;
    if (existsSync(path)) rmSync(path, { force: true });
  }

  const now = new Date();
  const monthName = now.toLocaleString("en-GB", { month: "long", year: "numeric" });

  console.log(`Seeding ${monthName}...`);

  // Clear what is there. The app's own seeded categories are left alone,
  // because deleting them would break the category colours the app maps by
  // name and the screenshots would lose their intended palette.
  const script = [
    "BEGIN;",
    "DELETE FROM transactions;",
    "DELETE FROM budgets;",
    "DELETE FROM recurring_rules;",
    "DELETE FROM sqlite_sequence WHERE name IN ('transactions','budgets','recurring_rules');",
    ...buildInserts(now),
    ...buildBudgets(),
    ...buildRecurring(),
    "COMMIT;",
    "VACUUM;",
  ].join("\n");

  await runSqlite(localDb, script);

  const count = await runSqlite(localDb, "SELECT COUNT(*) FROM transactions;");
  const spend = await runSqlite(
    localDb,
    "SELECT ROUND(SUM(amount),0) FROM transactions WHERE isIncome = 0;",
  );
  console.log(`  ${count.trim()} transactions, ${Number(spend.trim()).toLocaleString("en-IN")} total spend`);

  console.log("Pushing the database back...");
  // The app must not be running, or Room will write its cached state back over
  // this file the next time anything is inserted.
  await adbShell(`am force-stop ${PACKAGE}`);

  // Any write-ahead log left by the previous database is removed first. Room
  // replays it on open, so leaving it behind would resurrect the old data and
  // undo everything that was just seeded.
  await runAs(`rm -f databases/${DB_NAME}-wal databases/${DB_NAME}-shm`).catch(() => {});

  // Push to a path the app sandbox can read. `adb push` writes as the shell
  // user, so the file is world-readable by default, which is what lets
  // `run-as` copy it into place.
  const staging = "/data/local/tmp/seed.db";
  await adb(["push", localDb, staging], { maxBuffer: 64 * 1024 * 1024 });

  await runAs(`cp ${staging} databases/${DB_NAME}`);
  // `rm` as the app user cannot touch /data/local/tmp, so the staged copy is
  // left for the shell to clear. It is overwritten on the next run.
  await adbShell(`rm -f ${staging}`).catch(() => {});

  const verified = await runAs(`ls databases/`);
  console.log(`  app sandbox now has: ${verified.stdout.trim().split("\n").join(", ")}`);

  const confirm = await runAs(`cat databases/${DB_NAME}`).then(
    (r) => r.stdout.length,
    () => 0,
  );
  if (confirm === 0) {
    console.error("The database did not land in the app sandbox. Check the push.");
    process.exit(1);
  }

  rmSync(work, { recursive: true, force: true });
  console.log("\nDone. Run `pnpm capture` to take the screenshots.");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});