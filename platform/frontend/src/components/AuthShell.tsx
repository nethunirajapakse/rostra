import type { ReactNode } from "react";

export function AuthShell({ title, lead, children, footer }: { title: string; lead: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="mx-auto max-w-md pt-4 sm:pt-10">
      <h1 className="font-display text-5xl leading-[1.05]">{title}</h1>
      <p className="mt-3 text-lg text-quiet">{lead}</p>
      <div className="mt-8 rounded-ui border border-rule bg-slab p-6 sm:p-8">{children}</div>
      <p className="mt-5 text-center text-sm text-quiet">{footer}</p>
    </div>
  );
}
