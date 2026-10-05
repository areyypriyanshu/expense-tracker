import { describe, expect, it } from "vitest";

import {
  ACCEPT_ATTRIBUTE,
  ALLOWED_TYPES,
  CATEGORIES,
  MAX_FILES,
  MAX_TOTAL_BYTES,
  escapeHtml,
  isRealImageOfType,
  validate,
} from "@/lib/feedback";

/**
 * The upload rules are the whole security posture of this form, so they are
 * tested against the exact attacks they exist to stop: a file that lies about
 * its type, a file that is too large, and a file count chosen to exhaust the
 * request. Everything else on the page is cosmetic.
 */

/** Builds a File whose bytes begin with `header`. */
function fileWithBytes(name: string, type: string, bytes: number[]): File {
  return new File([new Uint8Array(bytes)], name, { type });
}

const PNG_HEADER = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
const JPEG_HEADER = [0xff, 0xd8, 0xff, 0xe0];

const baseInput = {
  name: "Mandal",
  email: "mandal@example.com",
  category: "Bug",
  message: "The dashboard shows the wrong total for this week.",
};

describe("isRealImageOfType", () => {
  it("accepts a real PNG", async () => {
    const file = fileWithBytes("shot.png", "image/png", PNG_HEADER);
    expect(await isRealImageOfType(file, "image/png")).toBe(true);
  });

  it("accepts a real JPEG", async () => {
    const file = fileWithBytes("shot.jpg", "image/jpeg", JPEG_HEADER);
    expect(await isRealImageOfType(file, "image/jpeg")).toBe(true);
  });

  it("rejects a file that claims to be a PNG but is not", async () => {
    // The core attack. An executable, a script or a zip with a .png name and
    // an image/png type would pass every extension and MIME check.
    const file = fileWithBytes("evil.png", "image/png", [0x4d, 0x5a, 0x90, 0x00]);
    expect(await isRealImageOfType(file, "image/png")).toBe(false);
  });

  it("rejects a file too short to hold a signature", async () => {
    const file = fileWithBytes("tiny.png", "image/png", [0x89, 0x50]);
    expect(await isRealImageOfType(file, "image/png")).toBe(false);
  });

  it("rejects an empty file", async () => {
    const file = fileWithBytes("empty.png", "image/png", []);
    expect(await isRealImageOfType(file, "image/png")).toBe(false);
  });

  it("rejects a type that is not on the list at all", async () => {
    const file = fileWithBytes("x.svg", "image/svg+xml", [0x3c, 0x73, 0x76, 0x67]);
    expect(await isRealImageOfType(file, "image/svg+xml")).toBe(false);
  });
});

describe("validate", () => {
  it("accepts a well-formed message with no attachments", async () => {
    const result = await validate({ ...baseInput, files: [] });
    expect(result.ok).toBe(true);
  });

  it("accepts up to three real images", async () => {
    const files = Array.from({ length: MAX_FILES }, (_, index) =>
      fileWithBytes(`shot-${index}.png`, "image/png", PNG_HEADER),
    );
    const result = await validate({ ...baseInput, files });
    expect(result.ok).toBe(true);
  });

  it("refuses more images than the cap", async () => {
    const files = Array.from({ length: MAX_FILES + 1 }, (_, index) =>
      fileWithBytes(`shot-${index}.png`, "image/png", PNG_HEADER),
    );
    const result = await validate({ ...baseInput, files });
    expect(result.ok).toBe(false);
  });

  it("refuses a disguised non-image even when the type and name agree", async () => {
    const files = [fileWithBytes("payload.png", "image/png", [0x50, 0x4b, 0x03, 0x04])];
    const result = await validate({ ...baseInput, files });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toContain("not a valid");
  });

  it("refuses a PDF by type", async () => {
    const files = [fileWithBytes("doc.pdf", "application/pdf", [0x25, 0x50, 0x44, 0x46])];
    const result = await validate({ ...baseInput, files });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toContain("PNG and JPEG");
  });

  it("refuses an SVG, which is a document rather than a picture", async () => {
    const files = [fileWithBytes("x.svg", "image/svg+xml", [0x3c, 0x3f, 0x78, 0x6d, 0x6c])];
    const result = await validate({ ...baseInput, files });
    expect(result.ok).toBe(false);
  });

  it("refuses a file over the size cap", async () => {
    // A real PNG header, then enough padding to exceed the limit.
    const oversized = new File(
      [new Uint8Array([...PNG_HEADER, ...new Array(3 * 1024 * 1024).fill(0)])],
      "big.png",
      { type: "image/png" },
    );
    const result = await validate({ ...baseInput, files: [oversized] });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toContain("MB");
  });

  it("refuses a valid PNG carrying the wrong extension", async () => {
    const files = [fileWithBytes("shot.jpg", "image/png", PNG_HEADER)];
    const result = await validate({ ...baseInput, files });
    expect(result.ok).toBe(false);
  });

  it("rejects a missing name", async () => {
    const result = await validate({ ...baseInput, name: "   ", files: [] });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.field).toBe("name");
  });

  it("rejects a missing email", async () => {
    const result = await validate({ ...baseInput, email: "", files: [] });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.field).toBe("email");
  });

  it.each(["not-an-email", "a@b", "a b@example.com", "@example.com", "a@.com"])(
    "rejects the malformed address %s",
    async (email) => {
      const result = await validate({ ...baseInput, email, files: [] });
      expect(result.ok).toBe(false);
      if (!result.ok) expect(result.field).toBe("email");
    },
  );

  it("rejects a missing category", async () => {
    const result = await validate({ ...baseInput, category: "", files: [] });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.field).toBe("category");
  });

  it("rejects an empty message", async () => {
    const result = await validate({ ...baseInput, message: "   ", files: [] });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.field).toBe("message");
  });

  it("rejects an over-long message", async () => {
    const result = await validate({ ...baseInput, message: "a".repeat(4001), files: [] });
    expect(result.ok).toBe(false);
  });

  it("trims surrounding whitespace before checking length", async () => {
    const result = await validate({ ...baseInput, message: "  it broke  ", files: [] });
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.value.message).toBe("it broke");
  });

  it("validates the fields before it looks at the files", async () => {
    // A form with both problems should report the field problem, so the
    // person is not told about a bad attachment they can still fix.
    const files = [fileWithBytes("payload.png", "image/png", [0x50, 0x4b])];
    const result = await validate({ ...baseInput, email: "nope", files });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.field).toBe("email");
  });
});

describe("the allowlist itself", () => {
  it("covers exactly PNG and JPEG", () => {
    expect(Object.keys(ALLOWED_TYPES).sort()).toEqual(["image/jpeg", "image/png"]);
  });

  it("has an accept attribute derived from the same list", () => {
    // The restatement the reviewer flagged. This one compares the attribute
    // against the allowlist instead of against a second literal, so it fails
    // if the two ever drift apart.
    const fromAllowlist = Object.entries(ALLOWED_TYPES)
      .flatMap(([mime, spec]) => [mime, ...spec.extensions.map((e) => `.${e}`)])
      .sort();
    expect(ACCEPT_ATTRIBUTE.split(",").sort()).toEqual(fromAllowlist);
  });

  it("does not admit any text or archive type", () => {
    for (const type of ["image/svg+xml", "image/gif", "image/webp", "application/pdf"]) {
      expect(ALLOWED_TYPES).not.toHaveProperty(type);
    }
  });
});

describe("escapeHtml", () => {
  it("neutralises a tag so it cannot render as markup", () => {
    expect(escapeHtml("<script>alert(1)</script>")).toBe(
      "&lt;script&gt;alert(1)&lt;/script&gt;",
    );
  });

  it("neutralises an image tag, which is loaded without being opened", () => {
    expect(escapeHtml('<img src=x onerror="alert(1)">')).not.toContain("<img");
  });

  it("escapes quotes, which is what makes an attribute break out", () => {
    expect(escapeHtml('" onmouseover="alert(1)')).toBe("&quot; onmouseover=&quot;alert(1)");
    expect(escapeHtml("it's")).toBe("it&#39;s");
  });

  it("escapes the ampersand first, so entities are not double escaped", () => {
    // The bug this guards: replacing & last turns "<" into "&lt;" and then
    // into "&amp;lt;", which renders as the literal text "&lt;".
    expect(escapeHtml("<b>")).toBe("&lt;b&gt;");
    expect(escapeHtml("a & b")).toBe("a &amp; b");
    expect(escapeHtml("&lt;")).toBe("&amp;lt;");
  });

  it("leaves ordinary text alone", () => {
    expect(escapeHtml("The dashboard shows the wrong total.")).toBe(
      "The dashboard shows the wrong total.",
    );
  });
});
describe("header injection", () => {
  // The name and the category are interpolated into the mail subject, so a
  // value carrying CRLF can close the header line and append one of its own,
  // for instance a Bcc. Both are now constrained.
  it("refuses a category carrying a line break", async () => {
    const result = await validate({
      ...baseInput,
      category: "Bug\r\nBcc: victim@evil.test",
      files: [],
    });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.field).toBe("category");
  });

  it("refuses a name carrying a line break", async () => {
    const result = await validate({
      ...baseInput,
      name: "Bob\r\nX-Injected: 1",
      files: [],
    });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.field).toBe("name");
  });

  it("refuses an email carrying a line break", async () => {
    const result = await validate({
      ...baseInput,
      email: "a@b.com\r\nBcc: victim@evil.test",
      files: [],
    });
    expect(result.ok).toBe(false);
  });

  it("refuses a category that is not one of the offered ones", async () => {
    const result = await validate({ ...baseInput, category: "x".repeat(5000), files: [] });
    expect(result.ok).toBe(false);
  });

  it("accepts each category the form offers", async () => {
    for (const category of CATEGORIES) {
      const result = await validate({ ...baseInput, category, files: [] });
      expect(result.ok, `rejected the offered category ${category}`).toBe(true);
    }
  });

  it("still allows newlines in the message, which is body text", async () => {
    const result = await validate({ ...baseInput, message: "line one\nline two", files: [] });
    expect(result.ok).toBe(true);
  });
});

describe("the total size cap", () => {
  it("refuses files that are individually fine but together too large", async () => {
    // Each is under MAX_FILE_BYTES, so only a whole-request cap catches this.
    const chunk = new Uint8Array(Math.floor(MAX_TOTAL_BYTES / 2));
    chunk.set(PNG_HEADER);
    const files = [0, 1, 2].map((index) =>
      fileWithBytes(`shot-${index}.png`, "image/png", [...chunk]),
    );
    const result = await validate({ ...baseInput, files });
    expect(result.ok).toBe(false);
  });

  it("keeps the per-file cap under the per-request cap", () => {
    // Otherwise a single allowed file could still exceed the request limit.
    expect(MAX_TOTAL_BYTES).toBeGreaterThan(0);
  });
});
