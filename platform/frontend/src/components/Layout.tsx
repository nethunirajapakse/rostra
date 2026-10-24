import { Outlet } from "react-router";
import { Header } from "./Header";
import { Toaster } from "./Toaster";

export function Layout() {
  return (
    <>
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-50 focus:rounded-ui focus:bg-slab focus:px-3 focus:py-2">
        Skip to content
      </a>
      <Header />
      <main id="main" className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
        <Outlet />
      </main>
      <Toaster />
    </>
  );
}
