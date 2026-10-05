/**
 * Display formatting.
 *
 * Every function here turns a raw number or string from the API into something
 * a person reads. They are exported and tested independently because each one
 * has an edge case that would otherwise surface as odd copy on the page: a
 * release that is 1,048,576 bytes should read as exactly 1 MB, not 1.0 MB.
 */

/**
 * Formats a byte count using binary units.
 *
 * Two significant decimals, trailing zeros trimmed, so 22061048 reads as
 * "21.0 MB" and 1048576 reads as "1 MB". Uses 1024-based units because that
 * is what Android's own package manager reports.
 */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";

  const units = ["B", "KB", "MB", "GB"];
  let value = bytes;
  let unit = 0;

  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }

  // A whole number reads better without decimals ("1 MB" not "1.00 MB"), but
  // anything fractional keeps two so a size is never rounded into a lie.
  const rounded = Math.round(value * 100) / 100;
  const text = Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(2).replace(/\.?0+$/, "");

  return `${text} ${units[unit]}`;
}

/**
 * Groups a hex checksum into blocks of eight for readability.
 *
 * A 64-character string on one line is unreadable and impossible to compare
 * against the output of `shasum` without squinting. Grouping does not change
 * the value, only how it wraps on screen.
 */
export function formatChecksum(checksum: string): string {
  const hex = checksum.replace(/[^0-9a-f]/gi, "").toLowerCase();
  if (hex.length === 0) return "";

  return hex.match(/.{1,8}/g)?.join(" ") ?? hex;
}

/**
 * Formats an ISO date as a readable, unambiguous day.
 *
 * "12 March 2026" rather than "Mar 12, 2026", because the site is written for
 * an audience where day-first ordering is the common one. Invalid or missing
 * input returns an empty string rather than "Invalid Date", which would print
 * literally on the page.
 */
export function formatDate(iso: string): string {
  if (!iso) return "";

  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";

  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(date);
}

/** Formats a count with thousands separators: 12345 becomes "12,345". */
export function formatCount(value: number): string {
  return new Intl.NumberFormat("en-US").format(Math.max(0, Math.trunc(value)));
}

/** "Android 8.0 or newer". Single source for the OS floor across the site. */
export function androidRequirement(minAndroid: string): string {
  return `Android ${minAndroid} or newer`;
}