const base = { width: 16, height: 16, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 2, strokeLinecap: "round", strokeLinejoin: "round", "aria-hidden": true } as const;

export function EyeIcon({ filled = false }: { filled?: boolean }) {
  return (
    <svg {...base}>
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" fill={filled ? "currentColor" : "none"} fillOpacity={filled ? 0.18 : 0} />
      <circle cx="12" cy="12" r="3" fill={filled ? "currentColor" : "none"} />
    </svg>
  );
}

export function ImageIcon() {
  return (
    <svg {...base} width={24} height={24} strokeWidth={1.5}>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <circle cx="9" cy="10" r="1.6" />
      <path d="m21 16-5-5-9 9" />
    </svg>
  );
}

export function PlusIcon() {
  return (
    <svg {...base}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}
