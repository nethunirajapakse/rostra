import { useRef, useState } from "react";
import { useDeleteImage, useReorderImages, useUploadImage } from "../api/auctions";
import { errorText } from "../lib/api";
import { imageProblem, MAX_IMAGES } from "../lib/images";
import type { Auction } from "../lib/types";
import { toast } from "../store/toasts";
import { PlusIcon } from "./icons";

/** For the seller, on their own open auction: add photos, remove them, choose the cover. */
export function PhotoManager({ auction }: { auction: Auction }) {
  const input = useRef<HTMLInputElement>(null);
  const upload = useUploadImage();
  const remove = useDeleteImage(auction.id);
  const reorder = useReorderImages(auction.id);
  const [busy, setBusy] = useState(false);
  const images = auction.images;

  const addFiles = async (list: FileList) => {
    const room = MAX_IMAGES - images.length;
    const chosen = Array.from(list);
    const skipped = chosen.slice(room);
    setBusy(true);
    for (const file of chosen.slice(0, room)) {
      const bad = imageProblem(file);
      if (bad) {
        toast.warn(bad);
        continue;
      }
      try {
        await upload.mutateAsync({ auctionId: auction.id, file });
      } catch (e) {
        toast.bad(`${file.name}: ${errorText(e)}`);
      }
    }
    if (skipped.length) toast.warn(`At most ${MAX_IMAGES} photos. ${skipped.length} not added.`);
    setBusy(false);
  };

  const makeCover = (id: string) => reorder.mutate([id, ...images.filter((i) => i.id !== id).map((i) => i.id)]);

  return (
    <section className="mt-10 border-t border-rule pt-6" aria-labelledby="photos-heading">
      <h2 id="photos-heading" className="text-xl font-semibold">
        Photos
      </h2>
      <p className="mt-1 text-sm text-quiet">Only you can see these controls. Photos can change until the auction ends.</p>

      {images.length > 0 ? (
        <ul className="mt-4 grid grid-cols-3 gap-3 sm:grid-cols-5">
          {images.map((img, i) => (
            <li key={img.id}>
              <div className="relative">
                <img src={img.url} alt="" className="aspect-square w-full rounded-ui border border-rule object-cover" />
                {i === 0 ? (
                  <span className="absolute left-1 top-1 rounded-[3px] bg-ink px-1.5 py-0.5 text-xs font-semibold text-signal">Cover</span>
                ) : null}
              </div>
              <div className="mt-1.5 flex flex-wrap justify-between gap-x-2 text-xs font-semibold whitespace-nowrap">
                {i === 0 ? (
                  <span />
                ) : (
                  <button type="button" disabled={reorder.isPending} onClick={() => makeCover(img.id)} className="text-link hover:underline">
                    Make cover
                  </button>
                )}
                <button
                  type="button"
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(img.id, { onError: (e) => toast.bad(errorText(e)) })}
                  className="text-alert hover:underline"
                >
                  Remove
                </button>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-4 text-quiet">No photos yet. Listings with photos get noticed.</p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => input.current?.click()}
          disabled={busy || images.length >= MAX_IMAGES}
          className="inline-flex items-center gap-2 rounded-ui border border-rule bg-surface px-3.5 py-2 text-sm font-semibold hover:border-quiet disabled:opacity-50"
        >
          <PlusIcon /> {busy ? "Uploading" : "Add photos"}
        </button>
        <span className="text-sm text-quiet">
          {images.length}/{MAX_IMAGES}. JPEG, PNG or WebP, up to 5 MB each.
        </span>
      </div>
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        hidden
        onChange={(e) => {
          if (e.target.files?.length) void addFiles(e.target.files);
          e.target.value = "";
        }}
      />
    </section>
  );
}
