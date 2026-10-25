import { useEffect } from "react";
import { Route, Routes } from "react-router";
import { bootstrapSession } from "./api/auth";
import { Layout } from "./components/Layout";
import { LiveBridge } from "./components/LiveBridge";
import { RequireAuth } from "./components/RequireAuth";
import { AuctionDetailPage } from "./pages/AuctionDetailPage";
import { AuctionsPage } from "./pages/AuctionsPage";
import { CreateAuctionPage } from "./pages/CreateAuctionPage";
import { InboxPage } from "./pages/InboxPage";
import { MyAuctionsPage } from "./pages/MyAuctionsPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { SignInPage } from "./pages/SignInPage";
import { SignUpPage } from "./pages/SignUpPage";
import { WatchlistPage } from "./pages/WatchlistPage";
import { useSession } from "./store/session";

export default function App() {
  const status = useSession((s) => s.status);

  useEffect(() => {
    void bootstrapSession();
  }, []);

  // Do not flash a signed-out page at someone who is signed in.
  if (status === "unknown") return null;

  return (
    <>
      <LiveBridge />
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<AuctionsPage />} />
          <Route path="lot/:id" element={<AuctionDetailPage />} />
          <Route path="signin" element={<SignInPage />} />
          <Route path="signup" element={<SignUpPage />} />
          <Route element={<RequireAuth />}>
            <Route path="sell" element={<CreateAuctionPage />} />
            <Route path="mine" element={<MyAuctionsPage />} />
            <Route path="watchlist" element={<WatchlistPage />} />
            <Route path="inbox" element={<InboxPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </>
  );
}
