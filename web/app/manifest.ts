import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Expense Tracker",
    short_name: "Expense",
    description:
      "A quiet Android app for expenses, budgets and receipts. No account, no server, no tracking.",
    start_url: "/",
    display: "standalone",
    background_color: "#FAF8F2",
    theme_color: "#FAF8F2",
    icons: [
      {
        src: "/icon.svg",
        sizes: "any",
        type: "image/svg+xml",
      },
    ],
  };
}