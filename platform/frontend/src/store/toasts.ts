import { create } from "zustand";

export type ToastTone = "info" | "good" | "warn" | "bad";

export interface Toast {
  id: number;
  tone: ToastTone;
  text: string;
  /** Optional in-app destination, e.g. the auction a notification is about. */
  to?: string;
}

interface ToastState {
  toasts: Toast[];
  push: (tone: ToastTone, text: string, to?: string) => void;
  dismiss: (id: number) => void;
}

let nextId = 1;

export const useToasts = create<ToastState>((set, get) => ({
  toasts: [],
  push: (tone, text, to) => {
    const id = nextId++;
    set((s) => ({ toasts: [...s.toasts.slice(-3), { id, tone, text, to }] }));
    window.setTimeout(() => get().dismiss(id), 6000);
  },
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

export const toast = {
  info: (text: string, to?: string) => useToasts.getState().push("info", text, to),
  good: (text: string, to?: string) => useToasts.getState().push("good", text, to),
  warn: (text: string, to?: string) => useToasts.getState().push("warn", text, to),
  bad: (text: string, to?: string) => useToasts.getState().push("bad", text, to),
};
