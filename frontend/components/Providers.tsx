"use client";

import { useEffect, useRef } from "react";
import { Provider } from "react-redux";
import { resetAll } from "@/store/tasksSlice";
import { makeStore, type AppStore } from "@/store";
import { setUnauthorizedHandler } from "@/lib/api/client";

export function Providers({ children }: { children: React.ReactNode }) {
  const storeRef = useRef<AppStore | null>(null);
  if (!storeRef.current) {
    storeRef.current = makeStore();
  }
  const store = storeRef.current;

  useEffect(() => {
    setUnauthorizedHandler(() => {
      store.dispatch(resetAll());
      if (typeof window === "undefined" || window.location.pathname.startsWith("/login")) {
        return;
      }
      const next = `${window.location.pathname}${window.location.search}`;
      window.location.assign(`/login?next=${encodeURIComponent(next)}`);
    });
    return () => setUnauthorizedHandler(null);
  }, [store]);

  return <Provider store={store}>{children}</Provider>;
}