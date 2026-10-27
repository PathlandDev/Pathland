import { computed } from "@pathland/signals";
import { State } from "@pathland/state";
import type { AppContext, ViewElement } from "@pathland/view";
import { Text, VStack } from "@pathland/view/primitives";
import { fontSize, foregroundStyle } from "@pathland/view/modifiers";
import type { PathlandApp } from "@pathland/server";
import { UserProfileCard } from "./UserProfileCard";

/** The example root: the reference card plus a live search echo. */
export class ProfileCardApp implements PathlandApp {
  private query = new State<string>("");

  newRoot(context: AppContext): ViewElement {
    context.state.connect(this);
    const searchQuery = this.query.signal;
    const echo = computed(() =>
      searchQuery() ? `Searching for "${searchQuery()}"` : "Type to search",
    );

    return VStack({ spacing: 8 }, () => [
      new UserProfileCard().body({
        username: "Ada Lovelace",
        status: "Online",
        searchQuery,
      }),
      Text({ text: echo }).modifiers(fontSize(12), foregroundStyle("#6B7280")),
    ]);
  }
}
