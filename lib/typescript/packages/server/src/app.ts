import type { ViewElement, AppContext } from "@pathland/view";

/** The application's root-view factory. A fresh root is created per session. */
export interface PathlandApp {
  newRoot(context: AppContext): ViewElement;
}
