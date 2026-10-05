import { ImageResponse } from "next/og";

import { site } from "@/lib/site";

export const alt = `${site.name}: ${site.tagline}`;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

/**
 * The OpenGraph card, generated rather than committed as a binary.
 *
 * It uses only brand colours and system-rendered type, so it needs no font
 * file and no external fetch at build time. The two rules the card follows are
 * the same two the page follows: one accent colour, and no claim on it that
 * the page does not also make.
 */
export default function OpenGraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          backgroundColor: "#FAF8F2",
          padding: 72,
          color: "#1D211F",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 18 }}>
          <div
            style={{
              width: 56,
              height: 56,
              borderRadius: 28,
              backgroundColor: "#0F3D34",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <div
              style={{
                width: 28,
                height: 4,
                backgroundColor: "#FAF8F2",
                borderRadius: 2,
              }}
            />
          </div>
          <div style={{ fontSize: 30, color: "#69736E" }}>{site.name}</div>
        </div>

        <div style={{ display: "flex", flexDirection: "column" }}>
          <div
            style={{
              fontSize: 78,
              fontWeight: 600,
              letterSpacing: -2.6,
              lineHeight: 1.04,
              maxWidth: 940,
            }}
          >
            Expense tracking that keeps your data on your phone
          </div>
          <div style={{ fontSize: 34, color: "#69736E", marginTop: 26 }}>
            No account, no server, no tracking.
          </div>
        </div>

        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 14,
            borderTop: "1px solid #E1DACE",
            paddingTop: 28,
          }}
        >
          <div style={{ width: 44, height: 3, backgroundColor: "#DFAF3F" }} />
          <div style={{ fontSize: 28, color: "#69736E" }}>Android 8.0 or newer</div>
        </div>
      </div>
    ),
    size,
  );
}