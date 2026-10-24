import { create } from "zustand";

export type SocketState = "off" | "connecting" | "live";

interface LiveState {
  socket: SocketState;
  setSocket: (socket: SocketState) => void;
}

export const useLive = create<LiveState>((set) => ({
  socket: "off",
  setSocket: (socket) => set({ socket }),
}));
