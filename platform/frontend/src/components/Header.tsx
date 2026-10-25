import { useId } from "react";
import { Link, NavLink, useNavigate, useSearchParams } from "react-router";
import { signOut } from "../api/auth";
import { buttonClass } from "../lib/ui";
import { useSession } from "../store/session";
import { NotificationBell } from "./NotificationBell";

const navLink = ({ isActive }: { isActive: boolean }) =>
  `border-b-[3px] px-1 py-2 text-sm font-semibold ${isActive ? "border-signal text-white" : "border-transparent text-white/70 hover:text-white"}`;

export function Header() {
  const user = useSession((s) => s.user);
  const status = useSession((s) => s.status);
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const searchId = useId();
  const q = params.get("q") ?? "";

  return (
    <header className="bg-ink text-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-5 gap-y-3 px-4 py-3 sm:px-6">
        <Link to="/" className="flex items-center gap-2 font-display text-3xl font-extrabold leading-none" aria-label="Rostra, all auctions">
          <span className="size-3 bg-signal" aria-hidden />
          Rostra
        </Link>

        <form
          role="search"
          key={q}
          onSubmit={(e) => {
            e.preventDefault();
            const value = new FormData(e.currentTarget).get("q")?.toString().trim() ?? "";
            navigate(value ? `/?q=${encodeURIComponent(value)}` : "/");
          }}
          className="order-last flex w-full min-w-0 sm:order-none sm:w-auto sm:flex-1"
        >
          <label htmlFor={searchId} className="sr-only">
            Search auctions
          </label>
          <input
            id={searchId}
            name="q"
            type="search"
            defaultValue={q}
            placeholder="Search auctions"
            className="min-w-0 flex-1 rounded-l-ui border-0 bg-white px-3.5 py-2.5 text-base text-ink placeholder:text-quiet"
          />
          <button type="submit" className="rounded-r-ui bg-signal px-4 text-sm font-bold text-ink hover:bg-signal-deep">
            Search
          </button>
        </form>

        <div className="ml-auto flex items-center gap-2 sm:ml-0">
          {status === "authed" && user ? (
            <>
              <NotificationBell />
              <span className="hidden max-w-32 truncate text-sm text-white/70 lg:block" title={user.email}>
                {user.name}
              </span>
              <button
                onClick={async () => {
                  await signOut();
                  navigate("/");
                }}
                className="rounded-ui border border-white/30 px-3 py-2 text-sm font-semibold hover:border-white"
              >
                Sign out
              </button>
            </>
          ) : status === "anon" ? (
            <>
              <Link to="/signin" className="rounded-ui border border-white/30 px-3 py-2 text-sm font-semibold hover:border-white">
                Sign in
              </Link>
              <Link to="/signup" className={buttonClass("primary", "!py-2")}>
                Create account
              </Link>
            </>
          ) : null}
        </div>
      </div>

      <nav className="border-t border-white/10" aria-label="Main">
        <div className="mx-auto flex max-w-6xl items-center gap-5 px-4 sm:px-6">
          <NavLink to="/" end className={navLink}>
            Auctions
          </NavLink>
          {user ? (
            <>
              <NavLink to="/watchlist" className={navLink}>
                Watchlist
              </NavLink>
              <NavLink to="/mine" className={navLink}>
                My auctions
              </NavLink>
              <NavLink to="/sell" className={navLink}>
                Sell
              </NavLink>
            </>
          ) : null}
        </div>
      </nav>
    </header>
  );
}
