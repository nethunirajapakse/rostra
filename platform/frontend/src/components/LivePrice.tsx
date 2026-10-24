import { formatMoney } from "../lib/format";
import { useChangeNonce } from "../lib/useChangeNonce";

/** A price that rolls in when it changes. */
export function LivePrice({ value, className = "" }: { value: number; className?: string }) {
  const nonce = useChangeNonce(value);
  return (
    <span key={nonce} className={`figures inline-block ${nonce > 0 ? "price-roll" : ""} ${className}`}>
      {formatMoney(value)}
    </span>
  );
}
