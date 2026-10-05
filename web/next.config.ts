import type { NextConfig } from "next";

const config: NextConfig = {
  // The Dockerfile runs this as a standalone server. It is harmless when the
  // site is deployed to a static host instead.
  output: "standalone",
  reactStrictMode: true,
  images: {
    // Screenshots are captured from the emulator and committed to the repo, so
    // they are same-origin relative paths. No remote loader is needed.
    formats: ["image/avif", "image/webp"],
  },
};

export default config;