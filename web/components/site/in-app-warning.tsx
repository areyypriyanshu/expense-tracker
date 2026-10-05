"use client";

import { WarningCircle } from "@phosphor-icons/react";
import { useState } from "react";

/**
 * Detects if the site is being opened inside an in-app browser (WhatsApp,
 * Instagram, Facebook, Telegram, etc.).
 *
 * In-app browsers lack proper download manager support and cannot write APKs
 * to device storage, causing downloads to freeze at 100% (22.06 MB). This banner
 * alerts the user immediately and instructs them to open the page in Chrome or
 * their device browser.
 */
export function InAppWarning() {
  const [inApp] = useState(() => {
    if (typeof window === "undefined") return false;
    
    const ua =
      navigator.userAgent ||
      navigator.vendor ||
      (window as unknown as { opera?: string }).opera ||
      "";
    const isInstagram = /Instagram/i.test(ua);
    const isFB = /FBAN|FBAV/i.test(ua);
    const isWhatsApp = /WhatsApp/i.test(ua);
    const isTelegram = /Telegram/i.test(ua);
    const isTwitter = /Twitter|X/i.test(ua);
    const isLinkedIn = /LinkedInApp/i.test(ua);
    const isSnapchat = /Snapchat/i.test(ua);

    return isInstagram || isFB || isWhatsApp || isTelegram || isTwitter || isLinkedIn || isSnapchat;
  });

  if (!inApp) return null;

  return (
    <div className="border-b border-negative/30 bg-negative/10 px-5 py-3.5 text-center text-[14px] text-ink">
      <div className="mx-auto flex max-w-[1200px] items-center justify-center gap-2.5">
        <WarningCircle size={18} weight="fill" className="shrink-0 text-negative" aria-hidden="true" />
        <span>
          <strong>App browser detected.</strong> APK downloads often get stuck at 100% here. Tap the{" "}
          <strong>⋮</strong> or <strong>⋯</strong> menu in the top right and select{" "}
          <strong>Open in Chrome</strong> or browser.
        </span>
      </div>
    </div>
  );
}
