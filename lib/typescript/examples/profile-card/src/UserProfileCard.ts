import { view, InputSignal } from "@pathland/view";
import { Text, Image, HStack, VStack, TextField } from "@pathland/view/primitives";
import { padding, cornerRadius, background, fontSize, foregroundStyle } from "@pathland/view/modifiers";
import { MaybeSignal, WritableSignal } from "@pathland/signals";

export interface UserProfileCardProps {
  username: MaybeSignal<string>;
  status?: MaybeSignal<string>;
  searchQuery: WritableSignal<string>;
}

@view
export class UserProfileCard {
  body(props: UserProfileCardProps) {
    const usernameSignal = InputSignal.from(props.username);
    const statusSignal = InputSignal.from(props.status, "Offline");

    return VStack({ spacing: 12 }, () => [
      HStack({ spacing: 16 }, () => [
        Image({ source: "avatar.png" }).modifiers(
          cornerRadius(24),
          padding(4),
        ),

        VStack({ alignment: "leading", spacing: 4 }, () => [
          Text({ text: usernameSignal }).modifiers(
            fontSize(18),
            foregroundStyle("#111827"),
          ),

          Text({ text: statusSignal }).modifiers(
            fontSize(12),
            foregroundStyle("#10B981"),
          ),
        ]),
      ]),

      TextField({
        placeholder: "Search user...",
        text: props.searchQuery,
      }),
    ]).modifiers(
      background("#FFFFFF"),
      cornerRadius(12),
      padding(16),
    );
  }
}
