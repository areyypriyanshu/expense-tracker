import { ImageResponse } from "next/og";

import { site } from "@/lib/site";

/**
 * The app's launcher mark, drawn 1:1 from
 * app/src/main/res/drawable/ic_launcher_foreground.xml. It is the mark the site
 * serves at /favicon.svg and shows in the nav and footer, so the card carries
 * the same one rather than a simplified stand-in.
 */
function Mark() {
  return (
    <svg width={56} height={56} viewBox="0 0 108 108" style={{ borderRadius: 14, overflow: "hidden" }}>
      <path fill="#0F3D34" d="M0,0h108v108h-108z" />
      <path fill="#F6F3EA" d="M54,54m-28,0a28,28 0,1 0,56 0a28,28 0,1 0,-56 0" />
      <path
        fill="#D9D3C3"
        d="M31.5,70c5,7.3 13.2,12 22.5,12c9.3,0 17.5,-4.7 22.5,-12c-5.7,3.3 -13.3,5.2 -22.5,5.2s-16.8,-1.9 -22.5,-5.2z"
      />
      <path
        stroke="#0F3D34"
        strokeWidth="4.5"
        strokeLinecap="round"
        fill="none"
        d="M42,39h26M42,47h22M42,39c9,0 14,4 14,10.5c0,7 -5.2,11 -14,11l19,18"
      />
      <path fill="#DFAF3F" d="M70,38m-4,0a4,4 0,1 0,8 0a4,4 0,1 0,-8 0" />
    </svg>
  );
}

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
          <Mark />
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