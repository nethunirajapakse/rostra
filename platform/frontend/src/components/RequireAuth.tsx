import { Navigate, Outlet, useLocation } from "react-router";
import { useSession } from "../store/session";

export function RequireAuth() {
  const status = useSession((s) => s.status);
  const location = useLocation();
  if (status === "anon") {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/signin?next=${next}`} replace />;
  }
  return <Outlet />;
}
