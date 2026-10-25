import { ImageIcon } from "./icons";

/** A square-cropped photo, or a quiet placeholder when the auction has none. */
export function AuctionPhoto({ src, alt, className = "" }: { src: string | null; alt: string; className?: string }) {
  return src ? (
    <img src={src} alt={alt} loading="lazy" decoding="async" className={`bg-canvas object-cover ${className}`} />
  ) : (
    <div className={`flex items-center justify-center bg-canvas text-quiet/60 ${className}`} role="img" aria-label="No photo">
      <ImageIcon />
    </div>
  );
}
