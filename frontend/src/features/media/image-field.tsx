"use client";

import { useQuery } from "@tanstack/react-query";
import { ImageUp, Trash2 } from "lucide-react";
import { useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { errorMessage } from "@/lib/api/client";
import { mediaApi } from "@/lib/api/endpoints";
import { safeHttpsUrl } from "@/lib/format";
import type { MediaPurpose } from "@/types/api";

const ALLOWED_TYPES = ["image/jpeg", "image/png", "image/webp"];

/**
 * Image input. When the server has Cloudinary configured, files go straight from the browser to
 * Cloudinary with a server-issued signature (the API never sees the bytes). Otherwise, or in
 * addition, an https:// link from an approved host can be pasted. The URL is validated again by the API.
 */
export function ImageField({ label, purpose, url, onChange, error }: {
  label: string;
  purpose: MediaPurpose;
  url: string;
  onChange: (url: string, assetId: string | null) => void;
  error?: string;
}) {
  const id = useId();
  const config = useQuery({ queryKey: ["media-config"], queryFn: mediaApi.config, staleTime: 600_000 });
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string>();
  const preview = safeHttpsUrl(url);

  async function upload(file: File) {
    setUploadError(undefined);
    const max = config.data?.maxBytes ?? 5 * 1024 * 1024;
    if (!ALLOWED_TYPES.includes(file.type)) {
      setUploadError("Use a JPEG, PNG or WebP image.");
      return;
    }
    if (file.size > max) {
      setUploadError(`The image must be smaller than ${Math.round(max / 1024 / 1024)} MB.`);
      return;
    }
    setUploading(true);
    try {
      const sig = await mediaApi.sign(purpose);
      const form = new FormData();
      form.append("file", file);
      form.append("api_key", sig.apiKey);
      form.append("timestamp", String(sig.timestamp));
      form.append("signature", sig.signature);
      form.append("folder", sig.folder);
      form.append("allowed_formats", sig.allowedFormats);
      const res = await fetch(sig.uploadUrl, { method: "POST", body: form });
      if (!res.ok) throw new Error("The image upload failed. Your recipe text is unchanged; try again or paste a link.");
      const body = (await res.json()) as { secure_url?: string; public_id?: string };
      if (!body.secure_url) throw new Error("The image service did not return a secure link.");
      onChange(body.secure_url, body.public_id ?? null);
    } catch (e) {
      setUploadError(errorMessage(e));
    } finally {
      setUploading(false);
    }
  }

  const shownError = uploadError ?? error;
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={`${id}-url`} className="text-sm font-medium">{label}</label>
      {preview && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={preview} alt="" className="h-32 w-48 rounded-lg object-cover ring-1 ring-line" referrerPolicy="no-referrer" />
      )}
      <div className="flex flex-wrap gap-2">
        <Input id={`${id}-url`} type="url" placeholder="https://res.cloudinary.com/…" value={url}
          onChange={(e) => onChange(e.target.value, null)} className="min-w-0 flex-1" invalid={!!shownError} />
        {config.data?.uploadsEnabled && (
          <label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-lg border border-line bg-surface px-3 text-sm font-medium hover:bg-sunken">
            <ImageUp aria-hidden className="h-4 w-4" /> {uploading ? "Uploading…" : "Upload"}
            <input type="file" accept={ALLOWED_TYPES.join(",")} className="sr-only" disabled={uploading}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void upload(f);
                e.target.value = "";
              }} />
          </label>
        )}
        {url && (
          <Button variant="ghost" onClick={() => onChange("", null)} aria-label="Remove image"><Trash2 aria-hidden className="h-4 w-4" /></Button>
        )}
      </div>
      {config.data && !config.data.uploadsEnabled && (
        <p className="text-xs text-muted">Uploads are not configured on this server. Paste an https link from: {config.data.allowedHosts.join(", ")}.</p>
      )}
      {shownError && <p role="alert" className="text-xs font-medium text-danger">{shownError}</p>}
    </div>
  );
}
