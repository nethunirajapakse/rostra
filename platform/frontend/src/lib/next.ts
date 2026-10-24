/** Where to go after signing in. Only same-site paths are accepted, so a crafted link cannot bounce people elsewhere. */
export function safeNext(value: string | null): string {
  return value && value.startsWith("/") && !value.startsWith("//") && !value.startsWith("/\\") ? value : "/";
}
