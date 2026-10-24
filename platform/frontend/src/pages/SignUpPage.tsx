import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router";
import { useSignUp } from "../api/auth";
import { AuthShell } from "../components/AuthShell";
import { Button, ErrorNotice, TextField } from "../components/ui";
import { errorText } from "../lib/api";
import { safeNext } from "../lib/next";
import { useTitle } from "../lib/useTitle";
import { useSession } from "../store/session";

export function SignUpPage() {
  useTitle("Create account");
  const [params] = useSearchParams();
  const next = safeNext(params.get("next"));
  const navigate = useNavigate();
  const signedIn = useSession((s) => s.status === "authed");
  const signUp = useSignUp();
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "", password: "" });
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }));

  if (signedIn && !signUp.isPending) return <Navigate to={next} replace />;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    signUp.mutate({ ...form, email: form.email.trim() }, { onSuccess: () => navigate(next, { replace: true }) });
  };

  return (
    <AuthShell
      title="Create an account"
      lead="Bid on anything that is live, or list something of your own."
      footer={
        <>
          Already have an account?{" "}
          <Link to={`/signin${next !== "/" ? `?next=${encodeURIComponent(next)}` : ""}`} className="font-semibold text-verdigris hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-5">
        <div className="grid gap-5 sm:grid-cols-2">
          <TextField label="First name" autoComplete="given-name" value={form.firstName} onChange={set("firstName")} required autoFocus />
          <TextField label="Last name" autoComplete="family-name" value={form.lastName} onChange={set("lastName")} required />
        </div>
        <TextField label="Email" type="email" autoComplete="email" value={form.email} onChange={set("email")} required />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          value={form.password}
          onChange={set("password")}
          hint="At least 8 characters, with an upper and a lower case letter, a number and a symbol."
          required
        />
        {signUp.isError ? <ErrorNotice>{errorText(signUp.error)}</ErrorNotice> : null}
        <Button type="submit" disabled={signUp.isPending} className="w-full !py-3">
          {signUp.isPending ? "Creating account" : "Create account"}
        </Button>
      </form>
    </AuthShell>
  );
}
