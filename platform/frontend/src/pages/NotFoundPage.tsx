import { Link } from "react-router";
import { useTitle } from "../lib/useTitle";

export function NotFoundPage() {
  useTitle("Page not found");
  return (
    <div className="py-12">
      <h1 className="font-display text-5xl">That page does not exist</h1>
      <p className="mt-3 text-lg text-quiet">Check the address, or go back to the auctions.</p>
      <Link to="/" className="mt-5 inline-block font-semibold text-verdigris hover:underline">
        All auctions
      </Link>
    </div>
  );
}
