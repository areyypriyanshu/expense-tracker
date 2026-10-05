/**
 * Site-wide constants.
 *
 * Everything here is a fact about the app or the repository, not a preference.
 * The app metadata mirrors `app/build.gradle.kts` and `strings.xml`, and the
 * repository slug mirrors `git remote -v`. If either changes, this is the only
 * file that should need editing.
 */

export const site = {
  name: "Expense Tracker",
  tagline: "Expense tracking that keeps your data on your phone",
  description:
    "A quiet Android app for expenses, budgets and receipts. No account, no server, no tracking. Works offline, reads UPI SMS on device, and scans receipts on device.",
  url: process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000",
  locale: "en_IN",
} as const;

/** Matches `git remote -v` on areyypriyanshu/expense-tracker. */
export const repo = {
  owner: "areyypriyanshu",
  name: "expense-tracker",
  get slug() {
    return `${this.owner}/${this.name}`;
  },
  get url() {
    return `https://github.com/${this.slug}`;
  },
  get api() {
    return `https://api.github.com/repos/${this.slug}`;
  },
} as const;

/**
 * App facts, all of them readable out of the Android project.
 *
 * `minSdk` and `targetSdk` come from `app/build.gradle.kts` (minSdk 26 is
 * Android 8.0, which is the real floor for an install), and the permissions
 * list is the set declared in `AndroidManifest.xml` and documented in the
 * project README. Nothing here is estimated.
 */
export const app = {
  packageName: "com.expensetracker",
  minSdk: 26,
  minAndroid: "8.0",
  targetSdk: 34,
  /** First release published through this pipeline. */
  firstRelease: "1.0.0",
} as const;

/**
 * The three runtime permissions the app can request, each with the one
 * sentence that explains why a person should or should not grant it. Written
 * for a reader deciding whether to tap Allow, not for a developer.
 */
export const permissions = [
  {
    id: "android.permission.READ_SMS",
    name: "SMS",
    required: false,
    why: "Reads UPI transaction messages on this phone and turns them into expense entries. Without it, add expenses by hand and everything else still works.",
  },
  {
    id: "android.permission.POST_NOTIFICATIONS",
    name: "Notifications",
    required: false,
    why: "Tells you when a UPI message was turned into an expense. Android asks for this once, and declining it does not disable SMS syncing.",
  },
  {
    id: "android.permission.CAMERA",
    name: "Camera",
    required: false,
    why: "Used only when you tap Scan Receipt, to read the total and merchant off paper. The photo never leaves the device.",
  },
] as const;

/** What stays in the app's own database, stated as a list a reader can check. */
export const localData = [
  "Transactions and amounts",
  "Categories and budgets",
  "Recurring rules",
  "Cached exchange rates",
  "Assistant answers",
] as const;