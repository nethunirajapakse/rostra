import { create } from "zustand";
import type { User } from "../lib/types";

type Status = "unknown" | "authed" | "anon";

interface SessionState {
  status: Status;
  user: User | null;
  setUser: (user: User) => void;
  clear: () => void;
}

// A readable hint that this browser has signed in before. The real tokens are HttpOnly cookies that
// scripts cannot see, so without a hint every anonymous visitor would trigger two pointless 401 calls.
const HINT_KEY = "rostra.signedIn";
const readHint = () => {
  try {
    return localStorage.getItem(HINT_KEY) === "1";
  } catch {
    return false;
  }
};
const writeHint = (on: boolean) => {
  try {
    if (on) localStorage.setItem(HINT_KEY, "1");
    else localStorage.removeItem(HINT_KEY);
  } catch {
    /* storage can be unavailable (private mode); the hint is only an optimisation */
  }
};

export const useSession = create<SessionState>((set) => ({
  status: "unknown",
  user: null,
  setUser: (user) => {
    writeHint(true);
    set({ status: "authed", user });
  },
  clear: () => {
    writeHint(false);
    set({ status: "anon", user: null });
  },
}));

export const hasSignedInBefore = readHint;
