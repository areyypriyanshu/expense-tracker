import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

export default defineConfig({
  test: {
    environment: "node",
    include: ["tests/**/*.test.ts"],
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./", import.meta.url)),
      // `server-only` throws on import from anything that is not a React
      // Server Component, which is exactly what it is for: this module reads
      // a token and must never reach the browser. Vitest has no such graph,
      // so the guard is stubbed here. The functions under test are pure data
      // transforms that touch no secret and perform no request.
      "server-only": fileURLToPath(
        new URL("./tests/stubs/server-only.ts", import.meta.url),
      ),
    },
  },
});