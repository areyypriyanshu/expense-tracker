"use client";

import { CheckCircle, DownloadSimple } from "@phosphor-icons/react";
import { useState } from "react";

/**
 * The primary download control.
 *
 * It is a real link to the GitHub asset, not a fetch. That matters: the APK is
 * 21 MB, GitHub's CDN serves it directly, and a fetch would buffer the whole
 * file through the browser before saving it, which fails on a phone on a weak
 * connection.
 *
 * The press state is a 2% scale-down, so the button feels physical rather
 * than animated. The label never wraps: the button grows instead of
 * constraining its own width.
 */
export function DownloadButton({
  href,
  version,
  size,
  variant = "primary",
  children,
}: {
  href: string;
  version: string;
  size: string;
  variant?: "primary" | "quiet";
  children?: React.ReactNode;
}) {
  const [pressed, setPressed] = useState(false);

  const base =
    "inline-flex items-center gap-2.5 rounded-lg text-[15px] font-medium transition-colors duration-200 active:scale-[0.98] whitespace-nowrap";

  const styles =
    variant === "primary"
      ? "bg-green px-6 py-3.5 text-white hover:bg-green-hover"
      : "border border-hairline bg-transparent px-5 py-3 text-ink hover:bg-wash";

  return (
    <a
      href={href}
      download
      onPointerDown={() => setPressed(true)}
      onPointerUp={() => setPressed(false)}
      onPointerLeave={() => setPressed(false)}
      style={pressed ? { transform: "scale(0.98)" } : undefined}
      className={`${base} ${styles}`}
    >
      {variant === "primary" ? (
        <DownloadSimple size={19} weight="bold" aria-hidden="true" />
      ) : null}
      <span>{children ?? "Download APK"}</span>
      {/* The size is real data from the release record, so it never drifts
          from the file the button actually serves. */}
      <span className="font-mono text-[13px] opacity-70">{size}</span>
      <span className="sr-only">
        {`, version ${version}, ${size}`}
      </span>
    </a>
  );
}

/**
 * A checksum with a copy button.
 *
 * The value is only rendered when one exists. This site does not have stored
 * checksums, because computing them at build time would require downloading
 * every APK on every build; when `checksum` is absent the component renders
 * nothing rather than a placeholder.
 */
export function Checksum({ checksum }: { checksum?: string }) {
  const [copied, setCopied] = useState(false);

  if (!checksum) return null;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(checksum);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard access can be denied. The value stays selectable on screen,
      // so the failure is not a dead end.
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
      <code className="font-mono text-[13px] break-all text-ink-muted">{checksum}</code>
      <button
        type="button"
        onClick={copy}
        className="inline-flex items-center gap-1.5 rounded-md border border-hairline px-2.5 py-1 text-[13px] text-ink-muted transition-colors duration-200 hover:bg-wash hover:text-ink"
      >
        {copied ? (
          <CheckCircle size={15} weight="fill" className="text-positive" aria-hidden="true" />
        ) : null}
        {copied ? "Copied" : "Copy"}
      </button>
    </div>
  );
}