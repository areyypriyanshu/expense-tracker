"use client";

import { CheckCircle, Paperclip, WarningCircle, X } from "@phosphor-icons/react";
import { useRef, useState } from "react";

import { ACCEPT_ATTRIBUTE, CATEGORIES, MAX_FILES, MAX_FILE_BYTES } from "@/lib/feedback";

/**
 * The feedback form.
 *
 * Client-side checks exist to give an immediate answer, never as the security
 * boundary: every one of them is trivially bypassed by posting to the route
 * directly. The checks that actually matter live in `lib/feedback.ts`, where
 * the file's bytes are read. What this component does is refuse to let someone
 * attach a 12 MB PDF in the first place, because finding out at submit time is
 * a poor experience.
 */
export function FeedbackForm({ configured }: { configured: boolean }) {
  const [pending, setPending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errorField, setErrorField] = useState<string | null>(null);
  const [files, setFiles] = useState<File[]>([]);
  const [fileNotice, setFileNotice] = useState<string | null>(null);

  const formRef = useRef<HTMLFormElement>(null);

  const onPick = (event: React.ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(event.target.files ?? []);
    setFileNotice(null);

    if (picked.length === 0) return;

    const before = files.length;
    const room = MAX_FILES - before;
    if (room <= 0) {
      setFileNotice(`You can attach at most ${MAX_FILES} images.`);
      event.target.value = "";
      return;
    }

    const accepted: File[] = [];
    let rejected: string | null = null;

    for (const file of picked) {
      if (accepted.length >= room) break;

      const type = file.type.toLowerCase();
      const isImage = type === "image/png" || type === "image/jpeg";
      const withinSize = file.size <= MAX_FILE_BYTES;

      if (!isImage) {
        rejected = `${file.name} is not a PNG or JPEG image.`;
        continue;
      }
      if (!withinSize) {
        rejected = `${file.name} is larger than ${megabytes(MAX_FILE_BYTES)}.`;
        continue;
      }
      accepted.push(file);
    }

    setFiles((current) => [...current, ...accepted]);
    if (rejected) setFileNotice(rejected);

    // Reset the input so picking the same file twice in a row still fires a
    // change event.
    event.target.value = "";
  };

  const remove = (index: number) => {
    setFiles((current) => current.filter((_, position) => position !== index));
    setFileNotice(null);
  };

  const onSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setErrorField(null);

    setPending(true);
    try {
      const form = event.currentTarget;
      const body = new FormData(form);

      for (const file of files) {
        body.append("attachment", file);
      }

      const response = await fetch("/api/feedback", { method: "POST", body });
      const payload = (await response.json().catch(() => null)) as {
        ok?: boolean;
        error?: string;
        field?: string;
      } | null;

      if (!response.ok || payload?.ok !== true) {
        setError(payload?.error ?? "That could not be sent. Please try again.");
        setErrorField(payload?.field ?? null);
        setPending(false);
        return;
      }

      setSent(true);
      setPending(false);
    } catch {
      setError("That could not be sent. Please try again.");
      setPending(false);
    }
  };

  if (!configured) {
    return (
      <div className="rounded-lg border border-hairline bg-wash p-6 md:p-8">
        <h2 className="text-lg font-medium text-ink">Feedback is not switched on yet</h2>
        <p className="mt-3 max-w-[62ch] leading-relaxed text-ink-muted">
          This form needs an access key before it can send anything. Until then, the repository
          issue tracker is the fastest way to reach me.
        </p>
        <a
          href="https://github.com/areyypriyanshu/expense-tracker/issues/new"
          className="mt-5 inline-block text-[15px] font-medium text-green underline decoration-green/30 underline-offset-4 transition-colors hover:decoration-green"
          rel="noopener noreferrer"
        >
          Open an issue on GitHub
        </a>
      </div>
    );
  }

  if (sent) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="rounded-lg border border-hairline bg-surface p-6 md:p-8"
      >
        <div className="flex gap-4">
          <CheckCircle size={26} weight="fill" className="shrink-0 text-positive" aria-hidden="true" />
          <div>
            <h2 className="text-lg font-medium text-ink">That is with me</h2>
            <p className="mt-3 max-w-[62ch] leading-relaxed text-ink-muted">
              Thanks for writing. I read every message, and I reply to most of them. If it is a bug,
              a screen shot helps more than anything else.
            </p>
            <button
              type="button"
              onClick={() => {
                formRef.current?.reset();
                setSent(false);
                setFiles([]);
              }}
              className="mt-5 rounded-lg border border-hairline px-5 py-3 text-[15px] font-medium text-ink transition-colors duration-200 hover:bg-wash"
            >
              Send another
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <form ref={formRef} onSubmit={onSubmit} noValidate className="space-y-6">
      {/* Honeypot. Hidden from people, irresistible to bots. A real submission
          never sends it, so anything that does is discarded by the route. */}
      <div className="hidden" aria-hidden="true">
        <label htmlFor="botcheck">Leave this empty</label>
        <input id="botcheck" name="botcheck" type="text" tabIndex={-1} autoComplete="off" />
      </div>

      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
        <Field
          id="name"
          label="Your name"
          error={errorField === "name" ? error : null}
          helper="So I know who I am replying to."
        >
          <input
            id="name"
            name="name"
            type="text"
            autoComplete="name"
            maxLength={80}
            required
            aria-invalid={errorField === "name"}
            aria-describedby={errorField === "name" ? "name-error" : undefined}
            className={inputClass(errorField === "name")}
          />
        </Field>

        <Field
          id="email"
          label="Email"
          error={errorField === "email" ? error : null}
          helper="Used only to reply. Never stored, never shared."
        >
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            maxLength={254}
            required
            aria-invalid={errorField === "email"}
            aria-describedby={errorField === "email" ? "email-error" : undefined}
            className={inputClass(errorField === "email")}
          />
        </Field>
      </div>

      <fieldset>
        <legend className="text-[15px] font-medium text-ink">What is this about?</legend>
        <div className="mt-3 flex flex-wrap gap-2.5">
          {CATEGORIES.map((category) => (
            <label
              key={category}
              // The input is sr-only, so the focus ring has to live on the
              // label or keyboard users get no visible focus at all.
              className="cursor-pointer rounded-lg border border-hairline px-4 py-2 text-[14px] text-ink transition-colors duration-200 hover:bg-wash has-checked:border-green has-checked:bg-green-wash has-checked:text-green has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-green"
            >
              <input
                type="radio"
                name="category"
                value={category}
                required
                className="sr-only"
              />
              {category}
            </label>
          ))}
        </div>
        {errorField === "category" ? <ErrorText>{error}</ErrorText> : null}
      </fieldset>

      <Field
        id="message"
        label="What happened, or what would you like to see?"
        error={errorField === "message" ? error : null}
        helper="What you were doing, what you expected, and what happened instead."
      >
        <textarea
          id="message"
          name="message"
          rows={6}
          maxLength={4000}
          required
          aria-invalid={errorField === "message"}
          aria-describedby={errorField === "message" ? "message-error" : undefined}
          className={`${inputClass(errorField === "message")} resize-y leading-relaxed`}
        />
      </Field>

      {/* Attachments. PNG and JPEG only, and the same check runs on the
          server before anything is forwarded. */}
      <div>
        <span className="text-[15px] font-medium text-ink">Screen shots (optional)</span>
        <p className="mt-1.5 text-[14px] leading-relaxed text-ink-muted">
          Up to {MAX_FILES} images, {megabytes(MAX_FILE_BYTES)} each. PNG and JPEG only. Anything
          else is refused, and every file is checked before it is sent.
        </p>

        <label
          htmlFor="attachment"
          className="mt-3 flex cursor-pointer items-center gap-2.5 rounded-lg border border-hairline px-4 py-3 text-[14px] text-ink transition-colors duration-200 hover:bg-wash has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-green"
        >
          <Paperclip size={17} aria-hidden="true" />
          Choose images
        </label>
        <input
          id="attachment"
          type="file"
          accept={ACCEPT_ATTRIBUTE}
          multiple
          onChange={onPick}
          className="sr-only"
        />

        {fileNotice ? (
          <p className="mt-3 flex items-start gap-2 text-[14px] text-negative">
            <WarningCircle size={16} weight="fill" className="mt-0.5 shrink-0" aria-hidden="true" />
            {fileNotice}
          </p>
        ) : null}

        {files.length > 0 ? (
          <ul className="mt-4 space-y-2">
            {files.map((file, index) => (
              <li
                key={`${file.name}-${index}`}
                className="flex items-center justify-between gap-3 rounded-lg border border-hairline-soft px-4 py-2.5"
              >
                <span className="truncate text-[14px] text-ink">{file.name}</span>
                <span className="flex shrink-0 items-center gap-3">
                  <span className="font-mono text-[12px] text-ink-muted">
                    {Math.round(file.size / 1024)} KB
                  </span>
                  <button
                    type="button"
                    onClick={() => remove(index)}
                    aria-label={`Remove ${file.name}`}
                    className="rounded-md p-1 text-ink-muted transition-colors duration-200 hover:bg-wash hover:text-ink"
                  >
                    <X size={15} weight="bold" aria-hidden="true" />
                  </button>
                </span>
              </li>
            ))}
          </ul>
        ) : null}
      </div>

      {error && !errorField ? (
        <p
          role="alert"
          className="flex items-start gap-2 rounded-lg border border-negative/30 bg-negative/5 px-4 py-3 text-[14px] text-negative"
        >
          <WarningCircle size={16} weight="fill" className="mt-0.5 shrink-0" aria-hidden="true" />
          {error}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-5 pt-2">
        <button
          type="submit"
          disabled={pending}
          className="rounded-lg bg-green px-6 py-3.5 text-[15px] font-medium text-white transition-colors duration-200 hover:bg-green-hover active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60"
        >
          {pending ? "Sending" : "Send feedback"}
        </button>
        <p className="text-[14px] text-ink-muted">
          Goes to my inbox. Nothing here is stored on this site.
        </p>
      </div>
    </form>
  );
}

function Field({
  id,
  label,
  helper,
  error,
  children,
}: {
  id: string;
  label: string;
  helper?: string;
  error?: string | null;
  children: React.ReactNode;
}) {
  return (
    <div>
      {/* Label above the control, helper under it, error last, so the reading
          order matches what the eye does. */}
      <label htmlFor={id} className="block text-[15px] font-medium text-ink">
        {label}
      </label>
      <div className="mt-2">{children}</div>
      {helper ? <p className="mt-1.5 text-[14px] text-ink-muted">{helper}</p> : null}
      {/* The id is what the control's aria-describedby points at, so the
          error is announced with the field rather than somewhere on the page. */}
      {error ? <ErrorText id={`${id}-error`}>{error}</ErrorText> : null}
    </div>
  );
}

function ErrorText({ children, id }: { children: React.ReactNode; id?: string }) {
  return (
    <p id={id} className="mt-1.5 flex items-start gap-1.5 text-[14px] text-negative" role="alert">
      <WarningCircle size={14} weight="fill" className="mt-0.5 shrink-0" aria-hidden="true" />
      {children}
    </p>
  );
}

/**
 * Renders a byte cap in the largest unit that keeps it readable.
 *
 * Rounding 1.4 MB to "1 MB" would understate the limit, and a person who
 * trusted the stated number could pick a file that is then refused.
 */
function megabytes(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  return `${Number.isInteger(mb) ? mb : mb.toFixed(1)} MB`;
}

function inputClass(hasError: boolean): string {
  return [
    "w-full rounded-lg border bg-surface px-4 py-3 text-[15px] text-ink",
    "placeholder:text-ink-muted/60",
    "transition-colors duration-200",
    hasError ? "border-negative" : "border-hairline hover:border-ink-muted/40",
  ].join(" ");
}