/**
 * Stub for the `server-only` guard, used only by the test runner.
 *
 * The real package throws on import from anything that is not a React Server
 * Component, which is the behaviour that keeps `lib/releases.ts` and the
 * token it reads out of the browser bundle. Vitest has no such graph, so the
 * import would fail before a single assertion ran.
 *
 * The functions under test are pure data transforms. None of them reads an
 * environment variable or performs a request, so nothing is lost by letting
 * them import cleanly here.
 */
export {};