import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router";
import { useSignIn } from "../api/auth";
import { AuthShell } from "../components/AuthShell";
import { Button, ErrorNotice, TextField } from "../components/ui";
import { errorText } from "../lib/api";
import { safeNext } from "../lib/next";
import { useTitle } from "../lib/useTitle";
import { useSession } from "../store/session";

export function SignInPage() {
  useTitle("Sign in");
  const [params] = useSearchParams();
  const next = safeNext(params.get("next"));
  const navigate = useNavigate();
  const signedIn = useSession((s) => s.status === "authed");
  const signIn = useSignIn();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  if (signedIn && !signIn.isPending) return <Navigate to={next} replace />;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    signIn.mutate({ email: email.trim(), password }, { onSuccess: () => navigate(next, { replace: true }) });
  };

  return (
    <AuthShell
      title="Sign in"
      lead="Pick up where you left off."
      footer={
        <>
          New here?{" "}
          <Link to={`/signup${next !== "/" ? `?next=${encodeURIComponent(next)}` : ""}`} className="font-semibold text-link hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-5">
        <TextField label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {signIn.isError ? <ErrorNotice>{errorText(signIn.error)}</ErrorNotice> : null}
        <Button type="submit" disabled={signIn.isPending} className="w-full !py-3">
          {signIn.isPending ? "Signing in" : "Sign in"}
        </Button>
      </form>
    </AuthShell>
  );
}
