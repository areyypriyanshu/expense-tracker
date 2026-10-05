import Image from "next/image";

/**
 * A phone frame for the screenshots.
 *
 * The bezel is CSS, not an image, because it never changes and a PNG of it
 * would be a large asset that says nothing. The screen inside is the real
 * capture from the emulator, taken by scripts/capture-screens.mjs.
 *
 * Sizing is set by the parent through the `className` prop, which is why the
 * frame is a plain wrapper rather than a fixed-size component.
 */
export function PhoneFrame({
  src,
  alt,
  className = "",
  priority = false,
  sizes = "(min-width: 1024px) 320px, 45vw",
}: {
  src: string;
  alt: string;
  className?: string;
  priority?: boolean;
  sizes?: string;
}) {
  return (
    <div
      className={`relative overflow-hidden rounded-[28px] border border-hairline bg-ink p-2 shadow-[0_24px_60px_-32px_rgba(29,33,31,0.45)] ${className}`}
    >
      {/* The notch sits over the top of the screen, matching the emulator
          capture which includes the device's own status bar. */}
      <div className="relative overflow-hidden rounded-[20px] bg-bone">
        <Image
          src={src}
          alt={alt}
          width={900}
          height={1950}
          priority={priority}
          sizes={sizes}
          className="h-auto w-full"
        />
      </div>
    </div>
  );
}