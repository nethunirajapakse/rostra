import { useMutation } from "@tanstack/react-query";
import { api } from "../lib/api";
import type { User } from "../lib/types";
import { hasSignedInBefore, useSession } from "../store/session";
import { queryClient } from "./queryClient";

/** Called once at startup: restores the session from the cookies, if there are any. */
export async function bootstrapSession(): Promise<void> {
  const { setUser, clear } = useSession.getState();
  if (!hasSignedInBefore()) {
    clear();
    return;
  }
  try {
    setUser(await api.get<User>("/auth/me"));
  } catch {
    clear();
  }
}

async function establishSession() {
  queryClient.clear(); // nothing cached for the previous visitor may leak into this session
  useSession.getState().setUser(await api.get<User>("/auth/me"));
}

export interface SignUpInput {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export const useSignIn = () =>
  useMutation({
    mutationFn: async (input: { email: string; password: string }) => {
      await api.post("/auth/signin", input);
      await establishSession();
    },
  });

export const useSignUp = () =>
  useMutation({
    mutationFn: async (input: SignUpInput) => {
      await api.post("/auth/signup", input);
      await api.post("/auth/signin", { email: input.email, password: input.password });
      await establishSession();
    },
  });

export async function signOut(): Promise<void> {
  try {
    await api.post("/auth/signout");
  } finally {
    useSession.getState().clear();
    queryClient.clear();
  }
}
