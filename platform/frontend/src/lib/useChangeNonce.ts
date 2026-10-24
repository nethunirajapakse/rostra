import { useState } from "react";

/** Counts up whenever `value` changes (not on first render). Used as a React key to replay an animation. */
export function useChangeNonce(value: unknown): number {
  const [prev, setPrev] = useState(value);
  const [nonce, setNonce] = useState(0);
  if (value !== prev) {
    setPrev(value);
    setNonce((n) => n + 1);
  }
  return nonce;
}
