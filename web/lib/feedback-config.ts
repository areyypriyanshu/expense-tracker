import "server-only";

/**
 * Feedback delivery configuration.
 *
 * The form posts to the site's own route, which forwards to Resend. The first
 * choice was Web3Forms, and it does not work for this job:
 *
 *   - their free tier does not include file attachments. Their documentation
 *     states it directly: "This is a PRO feature."
 *   - their API rejects server-side calls with a 403, so there is nowhere to
 *     put the checks that stop a disguised file being forwarded.
 *
 * Resend allows a server-side call, accepts attachments on the free tier, and
 * needs no database. The cost is that the site now has one route talking to a
 * third party, which is a deliberate trade for inspecting every upload before
 * it leaves.
 */

/**
 * The send endpoint.
 *
 * Read inside a function rather than at module scope on purpose. Next.js
 * replaces `process.env.X` at build time, so a top-level constant bakes in
 * whatever the value was during the build and ignores it at runtime. Reading
 * it per request is what lets a deployment point somewhere else without a
 * rebuild, and what lets the route be tested against a local stand-in.
 */
export function resendEndpoint(): string {
  return process.env.FEEDBACK_ENDPOINT ?? "https://api.resend.com/emails";
}

/**
 * From resend.com/pricing: 3,000 emails a month, and 100 a day.
 *
 * The daily cap is the one that matters for a public form. It resets at
 * midnight UTC rather than rolling, so a burst cannot quietly eat a whole day.
 */
export const RESEND_FREE_MONTHLY_EMAILS = 3000;
export const RESEND_FREE_DAILY_EMAILS = 100;

/**
 * Configuration, read per request.
 *
 * Every value here comes from the environment at call time rather than at
 * module load, because Next.js substitutes `process.env.X` while building. A
 * top-level constant would capture the build machine's value and keep it,
 * which is wrong for secrets and makes the endpoint impossible to redirect
 * without a redeploy.
 */

export function resendApiKey(): string {
  return process.env.RESEND_API_KEY ?? "";
}

/**
 * Where feedback is delivered.
 *
 * This must be the address registered on the Resend account. The shared
 * onboarding sender domain can only deliver to that one address, and sending
 * to anyone else returns a 403 from Resend.
 */
export function feedbackToEmail(): string {
  return process.env.FEEDBACK_TO_EMAIL ?? "";
}

/**
 * The address messages are sent from.
 *
 * Defaults to Resend's shared onboarding domain, which is enough here because
 * every message goes to the owner. Set this to a verified domain to send from
 * somewhere of your own.
 */
export function feedbackFromEmail(): string {
  return process.env.FEEDBACK_FROM_EMAIL ?? "Expense Tracker <onboarding@resend.dev>";
}

/**
 * Required by Resend on every request.
 *
 * A request without it is rejected with a 403 and an opaque error code, which
 * reads like a bad API key rather than a missing header.
 */
export const USER_AGENT = "expense-tracker-site/1.0";

/**
 * The form is live only when the key and the destination are both present.
 *
 * Checked rather than assumed, so a deployment missing one variable shows
 * "not switched on yet" instead of failing when someone submits.
 */
export function feedbackEnabled(): boolean {
  return Boolean(resendApiKey() && feedbackToEmail());
}