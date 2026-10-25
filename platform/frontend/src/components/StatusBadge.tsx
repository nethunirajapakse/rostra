import type { AuctionStatus } from "../lib/types";

const LOOK: Record<AuctionStatus, { text: string; cls: string }> = {
  ACTIVE: { text: "Live", cls: "bg-good-tint text-good" },
  SCHEDULED: { text: "Upcoming", cls: "bg-signal-tint text-warn" },
  ENDED: { text: "Ended", cls: "bg-rule/60 text-quiet" },
  CANCELLED: { text: "Cancelled", cls: "bg-alert-tint text-alert" },
  DRAFT: { text: "Draft", cls: "bg-rule/60 text-quiet" },
};

export function StatusBadge({ status }: { status: AuctionStatus }) {
  const look = LOOK[status];
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-[3px] px-2 py-0.5 text-xs font-bold ${look.cls}`}>
      {status === "ACTIVE" ? <span className="size-1.5 rounded-full bg-good" aria-hidden /> : null}
      {look.text}
    </span>
  );
}
