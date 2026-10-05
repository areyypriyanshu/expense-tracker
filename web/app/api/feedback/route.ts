import "server-only";

import { NextResponse } from "next/server";

import {
  USER_AGENT,
  feedbackFromEmail,
  feedbackToEmail,
  resendApiKey,
  resendEndpoint,
} from "@/lib/feedback-config";
import { RATE_LIMIT, escapeHtml, validate } from "@/lib/feedback";

/**
 * The largest request this function will accept.
 *
 * A serverless function accepts roughly 4.5 MB, and the multipart framing and
 * the text fields sit on top of the files, so the ceiling here is set below
 * that rather than at it.
 */
const MAX_REQUEST_BYTES = 4_200_000;

export const runtime = "nodejs";

/**
 * Receives a feedback message and forwards it by email.
 *
 * The form posts here rather than straight to the mail provider so that every
 * upload is inspected first. A provider accepts whatever it is handed, so
 * posting to it directly would mean accepting an archive, an `.apk`, or a file
 * whose extension lies about its contents, and passing all of it along.
 * Nothing leaves this route until `validate` has read the file's actual bytes.
 *
 * A Route Handler rather than a Server Action, because Server Actions cap the
 * request body near 1 MB and a screenshot exceeds that.
 */

/**
 * In-memory rate limiter.
 *
 * Bounded per instance rather than globally, since each serverless instance
 * keeps its own map. That stops casual abuse and scripted retries; it is not a
 * shared limiter and is not treated as one. The provider's daily cap is the
 * backstop, and this is what keeps the form from being a way to spend it.
 */
const recent = new Map<string, number[]>();

/** Hard ceiling on tracked callers, enforced regardless of age. */
const MAX_TRACKED_CLIENTS = 1000;

/**
 * Identifies the caller.
 *
 * `x-forwarded-for` is appended to by each proxy, so the rightmost entry is the
 * one the closest trusted hop recorded and was not chosen by the client. The
 * leftmost entry is client-supplied: reading it would let anyone rotate the
 * header on every request and reset their own count.
 */
function clientKey(request: Request): string {
  const forwarded = request.headers.get("x-forwarded-for");
  const chain = (forwarded ?? "")
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean);
  // A single value means no proxy appended its own, so it is the only one
  // there is and there is nothing safer to prefer.
  return chain.at(-1) ?? request.headers.get("x-real-ip") ?? "unknown";
}

function isRateLimited(address: string): boolean {
  const now = Date.now();
  const window = RATE_LIMIT.WINDOW_MS;

  // Reclaim this address first. Without this, every address ever seen stays in
  // the map for the lifetime of the instance.
  const previous = recent.get(address);
  if (previous) {
    const live = previous.filter((time) => now - time < window);
    if (live.length === 0) recent.delete(address);
    else recent.set(address, live);
  }

  const hits = recent.get(address) ?? [];
  if (hits.length >= RATE_LIMIT.MAX) return true;

  hits.push(now);
  recent.set(address, hits);

  // The map is keyed on a header, so an unlimited number of distinct values can
  // arrive. Ageing alone does not bound it, because an attacker simply does not
  // let anything age. Past the cap the oldest entries are dropped regardless.
  if (recent.size > MAX_TRACKED_CLIENTS) {
    const byAge = [...recent.entries()].sort((a, b) => (a[1][0] ?? 0) - (b[1][0] ?? 0));
    for (const [key] of byAge.slice(0, recent.size - MAX_TRACKED_CLIENTS)) {
      recent.delete(key);
    }
  }

  return false;
}

export async function POST(request: Request) {
  const apiKey = resendApiKey();
  const toEmail = feedbackToEmail();

  if (!apiKey || !toEmail) {
    return NextResponse.json(
      { ok: false, error: "Feedback is not configured on this deployment." },
      { status: 503 },
    );
  }

  if (isRateLimited(clientKey(request))) {
    return NextResponse.json(
      { ok: false, error: "Too many messages from this connection. Try again later." },
      { status: 429 },
    );
  }

  // Refused before the body is read. `formData()` below buffers the whole
  // request in memory, so without this a caller can make this function hold
  // whatever the platform will hand it. The declared length is only a hint, so
  // this is a cheap early exit, not the limit itself: the real ceiling is
  // enforced in validate, once the actual file sizes are known.
  const declaredLength = Number(request.headers.get("content-length") ?? 0);
  if (Number.isFinite(declaredLength) && declaredLength > MAX_REQUEST_BYTES) {
    return NextResponse.json(
      { ok: false, error: "That message is too large to send." },
      { status: 413 },
    );
  }

  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return NextResponse.json(
      { ok: false, error: "That request could not be read." },
      { status: 400 },
    );
  }

  // A field no person can see and every bot fills in. Answering with success
  // and dropping the message teaches the bot nothing, and a real person who
  // trips it by accident still sees the form work.
  const botcheck = form.get("botcheck");
  if (typeof botcheck === "string" && botcheck !== "") {
    return NextResponse.json({ ok: true });
  }

  // Every File entry is kept, including an empty one, so validation decides
  // rather than this filter. A zero-byte upload is a real submission that
  // should be answered with a reason, not silently discarded and reported as
  // a successful message with no attachment.
  const files = form
    .getAll("attachment")
    .filter((entry): entry is File => entry instanceof File);

  const result = await validate({
    name: String(form.get("name") ?? ""),
    email: String(form.get("email") ?? ""),
    category: String(form.get("category") ?? ""),
    message: String(form.get("message") ?? ""),
    files,
  });

  if (!result.ok) {
    return NextResponse.json(
      { ok: false, error: result.error, field: result.field },
      { status: 400 },
    );
  }

  // Re-read here rather than reusing what validation read. The bytes checked
  // are the bytes sent, which is the only arrangement where the check means
  // anything.
  const attachments = await Promise.all(
    files.map(async (file) => ({
      filename: file.name,
      content: Buffer.from(await file.arrayBuffer()).toString("base64"),
    })),
  );

  const { name, email, category, message } = result.value;
  const received = new Date().toISOString().replace("T", " ").slice(0, 16);

  const attachmentList = attachments.length
    ? `<p><strong>${attachments.length}</strong> image(s) attached: ${attachments
        .map((file) => escapeHtml(file.filename))
        .join(", ")}</p>`
    : "";

  // Everything from the form is escaped before it reaches the markup. The
  // message is the only field a stranger fully controls, and an unescaped one
  // in an inbox is a phishing link with the site's name on it.
  const html = `<div style="font-family:system-ui,-apple-system,Segoe UI,sans-serif;font-size:15px;line-height:1.6;color:#1D211F">
  <h2 style="margin:0 0 4px;font-size:18px">${escapeHtml(category)}</h2>
  <p style="margin:0 0 16px;color:#69736E;font-size:13px">Received ${escapeHtml(received)} UTC from the website</p>
  <table cellpadding="0" cellspacing="0" style="font-size:14px;border-collapse:collapse">
    <tr><td style="padding:2px 16px 2px 0;color:#69736E">From</td><td style="padding:2px 0"><strong>${escapeHtml(name)}</strong></td></tr>
    <tr><td style="padding:2px 16px 2px 0;color:#69736E">Reply to</td><td style="padding:2px 0">${escapeHtml(email)}</td></tr>
  </table>
  <hr style="border:0;border-top:1px solid #E1DACE;margin:16px 0">
  <p style="margin:0 0 16px;white-space:pre-wrap">${escapeHtml(message)}</p>
  ${attachmentList}
</div>`;

  const text = [
    `${category} from ${name} <${email}>`,
    "",
    message,
    attachments.length ? `\nAttachments: ${attachments.map((a) => a.filename).join(", ")}` : "",
  ]
    .filter((line) => line !== undefined)
    .join("\n");

  try {
    const response = await fetch(resendEndpoint(), {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        // Mandatory. Without it Resend answers 403 with an error code that
        // reads like a bad key rather than a missing header.
        "User-Agent": USER_AGENT,
      },
      body: JSON.stringify({
        from: feedbackFromEmail(),
        to: [toEmail],
        // Snake_case for the REST API. The Node SDK spells it replyTo, and
        // sending that here would be silently dropped.
        reply_to: email,
        subject: `Feedback: ${category} from ${name}`,
        html,
        text,
        ...(attachments.length > 0 ? { attachments } : {}),
      }),
    });

    const payload = (await response.json().catch(() => null)) as {
      name?: string;
      message?: string;
    } | null;

    if (!response.ok) {
      // Logged in full, returned in general terms. The provider's message can
      // name the API key and the destination address, neither of which belongs
      // in a reply to a stranger.
      console.error(
        `[feedback] delivery failed: ${response.status} ${payload?.name ?? "unknown"} ${payload?.message ?? ""}`,
      );
      return NextResponse.json(
        { ok: false, error: "That could not be sent. Please try again." },
        { status: 502 },
      );
    }

    return NextResponse.json({ ok: true });
  } catch (error) {
    console.error("[feedback] request failed:", error);
    return NextResponse.json(
      { ok: false, error: "That could not be sent. Please try again." },
      { status: 502 },
    );
  }
}