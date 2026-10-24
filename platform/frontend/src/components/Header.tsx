import { Link, NavLink, useNavigate } from "react-router";
import { signOut } from "../api/auth";
import { buttonClass } from "../lib/ui";
import { useSession } from "../store/session";
import { NotificationBell } from "./NotificationBell";

const navLink = ({ isActive }: { isActive: boolean }) =>
  `rounded-ui px-3 py-2 text-sm font-medium ${isActive ? "bg-verdigris-tint text-verdigris-deep" : "text-quiet hover:text-ink"}`;

export function Header() {
  const user = useSession((s) => s.user);
  const status = useSession((s) => s.status);
  const navigate = useNavigate();

  return (
    <header className="border-b border-rule bg-marble">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3 sm:px-6">
        <Link to="/" className="font-display text-3xl leading-none text-ink" aria-label="Rostra, all auctions">
          Rostra
        </Link>
        <nav className="flex flex-1 items-center gap-1" aria-label="Main">
          <NavLink to="/" end className={navLink}>
            Auctions
          </NavLink>
          {user ? (
            <>
              <NavLink to="/mine" className={navLink}>
                My auctions
              </NavLink>
              <NavLink to="/sell" className={navLink}>
                Sell
              </NavLink>
            </>
          ) : null}
        </nav>
        <div className="flex items-center gap-2">
          {status === "authed" && user ? (
            <>
              <NotificationBell />
              <span className="hidden max-w-40 truncate text-sm text-quiet sm:block" title={user.email}>
                {user.name}
              </span>
              <button
                onClick={async () => {
                  await signOut();
                  navigate("/");
                }}
                className={buttonClass("quiet", "!py-2")}
              >
                Sign out
              </button>
            </>
          ) : status === "anon" ? (
            <>
              <Link to="/signin" className={buttonClass("quiet", "!py-2")}>
                Sign in
              </Link>
              <Link to="/signup" className={buttonClass("primary", "!py-2")}>
                Create account
              </Link>
            </>
          ) : null}
        </div>
      </div>
    </header>
  );
}
