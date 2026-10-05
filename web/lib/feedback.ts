/**
 * Feedback validation.
 *
 * Everything here is a pure function over plain data, with no framework and no
 * network, so the rules can be tested directly. The rules exist because a
 * feedback form is an open upload endpoint on a public site, and the only
 * reliable way to keep it from becoming a file drop is to check what actually
 * arrived rather than what it claims to be.
 */

export const RATE_LIMIT = {
  /** Messages allowed from one client in the window. */
  MAX: 5,
  WINDOW_MS: 10 * 60 * 1000,
} as const;

/**
 * Per-file and whole-request caps.
 *
 * MAX_FILE_BYTES is per file. MAX_TOTAL_BYTES is the one that actually
 * matters, because a serverless function accepts a request body of about
 * 4.5 MB and three files of MAX_FILE_BYTES each would come to 9 MB, which the
 * platform rejects with a bare 413 before any code here runs. The total sits
 * under the ceiling with room for the form fields, so an honest user following
 * the form's own instructions never hits the limit.
 */
export const MAX_FILE_BYTES = 1_400_000;
export const MAX_FILES = 3;
export const MAX_TOTAL_BYTES = 3_600_000;

/**
 * The only accepted image formats.
 *
 * A fixed list of two, both of which are formats a screenshot or a photo
 * actually arrives in. Anything else is refused, including SVG, which is XML
 * and therefore a document rather than a picture.
 */
export const ALLOWED_TYPES = {
  "image/png": {
    extensions: ["png"],
    label: "PNG",
    /**
     * The eight-byte PNG signature. A file can claim `image/png` and contain
     * anything at all; this is what settles it.
     */
    magic: [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a],
  },
  "image/jpeg": {
    extensions: ["jpg", "jpeg"],
    label: "JPEG",
    /** Start of image marker followed by the next two bytes. */
    magic: [0xff, 0xd8, 0xff],
  },
} as const;

export type AllowedType = keyof typeof ALLOWED_TYPES;

/**
 * For the `accept` attribute on the file input.
 *
 * Derived from ALLOWED_TYPES rather than written out by hand, so it cannot
 * drift from what the server accepts. Browsers treat `accept` as a filter hint
 * only, which is why the real check happens in validate.
 */
export const ACCEPT_ATTRIBUTE = Object.entries(ALLOWED_TYPES)
  .flatMap(([mime, spec]) => [mime, ...spec.extensions.map((extension) => `.${extension}`)])
  .join(",");

/** What the form offers to pick from, written the way a person would say it. */
export const CATEGORIES = [
  "Bug",
  "Feature request",
  "Question",
  "Something else",
] as const;

export type Category = (typeof CATEGORIES)[number];

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export type FieldName = "name" | "email" | "category" | "message";

export type FeedbackInput = {
  name: string;
  email: string;
  category: string;
  message: string;
  version?: string;
  files: File[];
};

export type ValidationResult =
  | { ok: true; value: { name: string; email: string; category: string; message: string } }
  | { ok: false; error: string; field?: FieldName };

function extensionOf(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot === -1 ? "" : name.slice(dot + 1).toLowerCase();
}

/**
 * Escapes text for interpolation into HTML.
 *
 * The feedback message is written by a stranger and ends up in an inbox, so
 * every field goes through this on the way out. Without it a submission can
 * carry markup, and an image in a mail client is often loaded without being
 * opened, which would turn the form into a way to send a link that appears to
 * come from the site owner.
 *
 * The ampersand is replaced first so the entities the later replacements
 * introduce are not escaped a second time and left as literal text.
 */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/**
 * The part of a filename after the last separator.
 *
 * A multipart upload's filename is entirely the sender's to choose, so
 * `../../../etc/cron.d/x.png` is a legal value. The file is never written to
 * disk here, and the name is passed to the mail provider as JSON rather than
 * interpolated into anything, so this is defence in depth rather than the
 * thing standing between the site and a traversal. It still means a name that
 * arrives in an inbox is a name and not a path.
 */
function safeFileName(name: string): string {
  const base = name.split(/[/\\]/).pop() ?? "";
  return base.replace(/[^\w.\- ]/g, "_").slice(0, 80) || "attachment";
}

/**
 * Checks that the file really begins with the signature for its declared type.
 *
 * The extension and the MIME type on a multipart upload are both supplied by
 * whoever sends it, so agreeing with each other proves nothing. The signature
 * is the part that cannot be faked without producing a real file of that
 * format.
 *
 * What this does and does not establish, stated plainly: it proves the upload
 * begins with a real image header and not, say, a ZIP local-file header or a
 * Windows executable. It does not prove the rest of the file is a well-formed
 * image, because only the first bytes are read. A full check needs an image
 * decoder, which is out of proportion here: what the file is used for is being
 * attached to an email, and it is never decoded, served, or written anywhere on
 * the site.
 */
export async function isRealImageOfType(file: File, type: string): Promise<boolean> {
  const spec = ALLOWED_TYPES[type as AllowedType];
  if (!spec) return false;

  // A file too short to contain any signature is not a real image of any type.
  const header = new Uint8Array(await file.slice(0, spec.magic.length).arrayBuffer());
  if (header.length < spec.magic.length) return false;

  return spec.magic.every((byte, index) => header[index] === byte);
}

async function validateFiles(files: File[]): Promise<string | null> {
  if (files.length === 0) return null;

  if (files.length > MAX_FILES) {
    return `Attach at most ${MAX_FILES} images.`;
  }

  // The whole-request check comes before the per-file loop, so an oversized
  // submission is refused for the reason that actually applies rather than
  // for whichever file happens to be checked first.
  const total = files.reduce((sum, file) => sum + file.size, 0);
  if (total > MAX_TOTAL_BYTES) {
    return `Those images add up to more than ${Math.round(MAX_TOTAL_BYTES / (1024 * 1024))} MB.`;
  }

  for (const file of files) {
    if (file.size > MAX_FILE_BYTES) {
      return `${file.name} is larger than ${Math.round(MAX_FILE_BYTES / (1024 * 1024))} MB.`;
    }

    const type = file.type.toLowerCase();
    const spec = ALLOWED_TYPES[type as AllowedType];

    if (!spec) {
      // Naming the accepted formats rather than echoing back whatever was
      // sent, so the message stays short and cannot be used to reflect
      // arbitrary input back at the page.
      return "Only PNG and JPEG images can be attached.";
    }

    const extension = extensionOf(file.name);
    if (!spec.extensions.includes(extension as never)) {
      return "Only PNG and JPEG images can be attached.";
    }

    if (!(await isRealImageOfType(file, type))) {
      // The important one. The file claims to be an image and does not begin
      // like one, which is the shape of an attempt to smuggle something else
      // through.
      return `${safeFileName(file.name)} is not a valid ${spec.label} image.`;
    }
  }

  return null;
}

/**
 * Control characters that must never reach a mail header.
 *
 * CR and LF are the ones that matter: a value carrying them can close a header
 * line and append one of its own, so a "subject" built from user input becomes
 * a place to inject `Bcc:` or a second `Subject:`. This applies to the name and
 * the category because both are interpolated into the mail subject.
 */
const CONTROL_CHARACTERS = /[\r\n\u0000-\u001F\u007F]/;

export async function validate(input: FeedbackInput): Promise<ValidationResult> {
  const name = input.name.trim();
  const email = input.email.trim();
  const category = input.category.trim();
  const message = input.message.trim();

  if (name.length === 0) return { ok: false, error: "Tell us your name.", field: "name" };
  if (name.length > 80) {
    return { ok: false, error: "That name is too long.", field: "name" };
  }
  if (CONTROL_CHARACTERS.test(name)) {
    return { ok: false, error: "That name has characters it cannot contain.", field: "name" };
  }

  if (email.length === 0) {
    return { ok: false, error: "We need an email address to reply to.", field: "email" };
  }
  if (email.length > 254 || !EMAIL_PATTERN.test(email)) {
    return { ok: false, error: "That email address does not look right.", field: "email" };
  }
  // An address can carry a line break too, and it is used as the reply-to.
  if (CONTROL_CHARACTERS.test(email)) {
    return { ok: false, error: "That email address does not look right.", field: "email" };
  }

  // Checked against the fixed list rather than merely for length. The category
  // goes into the mail subject, so an unconstrained string here is a header
  // injection point, not just untidy input.
  if (!(CATEGORIES as readonly string[]).includes(category)) {
    return { ok: false, error: "Pick what this is about.", field: "category" };
  }

  // The message is body text and is escaped, so newlines are legitimate here
  // and only NUL and the other C0 controls are refused.
  if (message.length === 0) return { ok: false, error: "Write a message.", field: "message" };
  if (message.length > 4000) {
    return { ok: false, error: "Keep it under 4000 characters.", field: "message" };
  }

  const fileError = await validateFiles(input.files);
  if (fileError) return { ok: false, error: fileError };

  return { ok: true, value: { name, email, category, message } };
}