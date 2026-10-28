import { render, type RenderOptions } from "@testing-library/react";
import { Provider } from "react-redux";
import { makeStore, type AppStore } from "@/store";

export function renderWithStore(
  ui: React.ReactElement,
  store: AppStore = makeStore(),
  options?: Omit<RenderOptions, "wrapper">,
) {
  return {
    store,
    ...render(<Provider store={store}>{ui}</Provider>, options),
  };
}