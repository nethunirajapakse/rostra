import type { AuctionStatus } from "../lib/types";

const LOOK: Record<AuctionStatus, { text: string; cls: string }> = {
  ACTIVE: { text: "Live", cls: "bg-verdigris-tint text-verdigris-deep" },
  SCHEDULED: { text: "Upcoming", cls: "bg-bronze-tint text-bronze" },
  ENDED: { text: "Ended", cls: "bg-rule/60 text-quiet" },
  CANCELLED: { text: "Cancelled", cls: "bg-alert-tint text-alert" },
  DRAFT: { text: "Draft", cls: "bg-rule/60 text-quiet" },
};

export function StatusBadge({ status }: { status: AuctionStatus }) {
  const look = LOOK[status];
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${look.cls}`}>
      {status === "ACTIVE" ? <span className="size-1.5 rounded-full bg-verdigris" aria-hidden /> : null}
      {look.text}
    </span>
  );
}
