import { useState } from "react";
import type { AuctionImage } from "../lib/types";

/** One large photo with a strip of thumbnails underneath. Renders nothing when there are no photos. */
export function PhotoGallery({ images, title }: { images: AuctionImage[]; title: string }) {
  const [selected, setSelected] = useState(0);
  if (images.length === 0) return null;
  const current = images[Math.min(selected, images.length - 1)];

  return (
    <div>
      <div className="overflow-hidden rounded-ui border border-rule bg-slab">
        <img src={current.url} alt={title} className="aspect-[4/3] w-full object-contain" />
      </div>
      {images.length > 1 ? (
        <ul className="mt-3 flex gap-2" aria-label="Photos">
          {images.map((img, i) => (
            <li key={img.id}>
              <button
                type="button"
                onClick={() => setSelected(i)}
                aria-label={`Show photo ${i + 1} of ${images.length}`}
                aria-current={img === current}
                className={`block overflow-hidden rounded-ui border-2 ${img === current ? "border-verdigris" : "border-transparent hover:border-rule"}`}
              >
                <img src={img.url} alt="" loading="lazy" className="size-16 object-cover sm:size-20" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
