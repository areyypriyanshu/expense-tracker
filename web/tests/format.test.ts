import { describe, expect, it } from "vitest";

import { formatBytes, formatChecksum, formatCount, formatDate } from "@/lib/format";

describe("formatBytes", () => {
  it("handles zero and negatives without printing a unit twice", () => {
    expect(formatBytes(0)).toBe("0 B");
    expect(formatBytes(-1)).toBe("0 B");
    expect(formatBytes(Number.NaN)).toBe("0 B");
  });

  it("reports plain bytes below a kilobyte", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(1023)).toBe("1023 B");
  });

  it("switches units at exact powers of 1024", () => {
    expect(formatBytes(1024)).toBe("1 KB");
    expect(formatBytes(1048576)).toBe("1 MB");
    expect(formatBytes(1073741824)).toBe("1 GB");
  });

  it("reads the real 1.5.0 APK size", () => {
    // The size is read from the GitHub asset record, so this is the exact
    // number the page shows next to the download button.
    expect(formatBytes(22_061_048)).toBe("21.04 MB");
  });

  it("drops trailing zeros but keeps real precision", () => {
    expect(formatBytes(1_500_000)).toBe("1.43 MB");
    expect(formatBytes(21_099_999)).toBe("20.12 MB");
  });

  it("rounds a size to two decimals rather than truncating", () => {
    // 2046 bytes is 1.998 KB, which rounds to "2 KB". Every file manager
    // does the same, and the alternative, a long decimal, is noise on a
    // label whose job is to convey magnitude.
    expect(formatBytes(2046)).toBe("2 KB");
    expect(formatBytes(2047)).toBe("2 KB");
  });
});

describe("formatChecksum", () => {
  it("groups a hex digest into readable blocks", () => {
    const digest = "a".repeat(64);
    const grouped = formatChecksum(digest);
    expect(grouped.split(" ")).toHaveLength(8);
    expect(grouped.replace(/ /g, "")).toBe(digest);
  });

  it("strips separators and lowercases", () => {
    expect(formatChecksum("AB:CD")).toBe("abcd");
  });

  it("returns an empty string for empty input", () => {
    expect(formatChecksum("")).toBe("");
  });
});

describe("formatDate", () => {
  it("formats day first", () => {
    expect(formatDate("2026-09-16T00:00:00Z")).toBe("16 September 2026");
  });

  it("returns an empty string rather than printing Invalid Date", () => {
    expect(formatDate("")).toBe("");
    expect(formatDate("not a date")).toBe("");
  });

  it("does not shift the day across a timezone boundary", () => {
    // Midnight UTC read in a negative-offset timezone would become the
    // previous day, which is the kind of bug that makes a changelog wrong.
    expect(formatDate("2026-01-01T00:00:00Z")).toBe("1 January 2026");
  });
});

describe("formatCount", () => {
  it("groups thousands", () => {
    expect(formatCount(12345)).toBe("12,345");
  });

  it("never prints a negative count", () => {
    expect(formatCount(-5)).toBe("0");
  });
});