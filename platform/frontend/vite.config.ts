import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// In development the browser talks only to this dev server, and the API paths below are forwarded to the
// gateway. That keeps everything same-origin, so the HttpOnly auth cookies and the XSRF cookie just work
// (no CORS, no SameSite trouble). Regex keys (^...) keep SPA routes such as /mine from matching /me.
const gateway = process.env.GATEWAY_URL ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      "^/auth/": gateway,
      "^/auctions(/|\\?|$)": gateway,
      "^/bids(/|\\?|$)": gateway,
      "^/me/": gateway,
      "^/ws/": { target: gateway, ws: true },
    },
  },
});
