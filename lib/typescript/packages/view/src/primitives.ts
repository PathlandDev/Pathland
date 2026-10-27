import {
  COMPONENT_BUTTON,
  COMPONENT_DIVIDER,
  COMPONENT_HSTACK,
  COMPONENT_ICON,
  COMPONENT_IMAGE,
  COMPONENT_SCROLLVIEW,
  COMPONENT_SLIDER,
  COMPONENT_SPACER,
  COMPONENT_TEXT,
  COMPONENT_TEXT_FIELD,
  COMPONENT_VSTACK,
  COMPONENT_ZSTACK,
  LISTEN_POINTER_DOWN,
  LISTEN_POINTER_UP,
  PROP_ALIGNMENT,
  PROP_EVENT_LISTENERS,
  PROP_ICON_NAME,
  PROP_IMAGE_SOURCE,
  PROP_MAX_VALUE,
  PROP_MIN_VALUE,
  PROP_PROMPT,
  PROP_SPACING,
  PROP_VALUE,
  VAL_F32,
  VAL_STRING,
  VAL_U32,
} from "@pathland/protocol";
import { InputSignal, type MaybeSignal, type WritableSignal } from "@pathland/signals";
import { element, type ViewChildren, type ViewElement, type ViewProperty } from "./element";

export type Alignment =
  | "leading"
  | "center"
  | "trailing"
  | "topLeading"
  | "top"
  | "topTrailing"
  | "leadingCenter"
  | "trailingCenter"
  | "bottomLeading"
  | "bottom"
  | "bottomTrailing";

const ALIGNMENT_CODES: Record<Alignment, number> = {
  leading: 0,
  center: 1,
  trailing: 2,
  topLeading: 0,
  top: 3,
  topTrailing: 7,
  leadingCenter: 5,
  trailingCenter: 6,
  bottomLeading: 8,
  bottom: 4,
  bottomTrailing: 2,
};

export interface VStackProps {
  spacing?: number;
  alignment?: Alignment;
}

export type HStackProps = VStackProps;
export type ZStackProps = Pick<VStackProps, "alignment">;

function stackProps(props: VStackProps): ViewProperty[] {
  const out: ViewProperty[] = [];
  if (props.spacing !== undefined) {
    out.push({ property: PROP_SPACING, valueType: VAL_F32, value: props.spacing });
  }
  if (props.alignment !== undefined) {
    out.push({ property: PROP_ALIGNMENT, valueType: VAL_F32, value: ALIGNMENT_CODES[props.alignment] });
  }
  return out;
}

function stack(component: number, props: VStackProps, children: ViewChildren): ViewElement {
  return element({ component, props: stackProps(props), children });
}

export function VStack(props: VStackProps, children: ViewChildren): ViewElement {
  return stack(COMPONENT_VSTACK, props, children);
}

export function HStack(props: HStackProps, children: ViewChildren): ViewElement {
  return stack(COMPONENT_HSTACK, props, children);
}

export function ZStack(props: ZStackProps, children: ViewChildren): ViewElement {
  return stack(COMPONENT_ZSTACK, props, children);
}

export function Spacer(): ViewElement {
  return element({ component: COMPONENT_SPACER });
}

export function Divider(): ViewElement {
  return element({ component: COMPONENT_DIVIDER });
}

export function ScrollView(_props: Record<string, never>, children: ViewChildren): ViewElement {
  return element({ component: COMPONENT_SCROLLVIEW, children });
}

export interface TextProps {
  text: MaybeSignal<string>;
}

export function Text(props: TextProps): ViewElement {
  return element({ component: COMPONENT_TEXT, textSignal: InputSignal.from(props.text) });
}

export interface ImageProps {
  source: MaybeSignal<string>;
}

export function Image(props: ImageProps): ViewElement {
  return element({
    component: COMPONENT_IMAGE,
    props: [{ property: PROP_IMAGE_SOURCE, valueType: VAL_STRING, value: InputSignal.from(props.source) }],
  });
}

export interface IconProps {
  name: MaybeSignal<string>;
}

export function Icon(props: IconProps): ViewElement {
  return element({
    component: COMPONENT_ICON,
    props: [{ property: PROP_ICON_NAME, valueType: VAL_STRING, value: InputSignal.from(props.name) }],
  });
}

export interface TextFieldProps {
  placeholder?: string;
  text: WritableSignal<string>;
}

export function TextField(props: TextFieldProps): ViewElement {
  const viewProps: ViewProperty[] = [];
  if (props.placeholder !== undefined) {
    viewProps.push({ property: PROP_PROMPT, valueType: VAL_STRING, value: props.placeholder });
  }
  return element({ component: COMPONENT_TEXT_FIELD, props: viewProps, textBinding: props.text });
}

export interface ButtonProps {
  label?: string;
  action?: () => void;
  children?: ViewChildren;
}

export function Button(props: ButtonProps): ViewElement {
  return element({
    component: COMPONENT_BUTTON,
    props: [
      {
        property: PROP_EVENT_LISTENERS,
        valueType: VAL_U32,
        value: LISTEN_POINTER_DOWN | LISTEN_POINTER_UP,
      },
    ],
    children: props.children ?? [],
    textSignal: props.label !== undefined ? InputSignal.from(props.label) : undefined,
    tapAction: props.action,
  });
}

export interface SliderProps {
  value: WritableSignal<number>;
  min?: number;
  max?: number;
}

export function Slider(props: SliderProps): ViewElement {
  const min = props.min ?? 0;
  const max = props.max ?? 1;
  return element({
    component: COMPONENT_SLIDER,
    props: [
      { property: PROP_VALUE, valueType: VAL_F32, value: props.value },
      { property: PROP_MIN_VALUE, valueType: VAL_F32, value: min },
      { property: PROP_MAX_VALUE, valueType: VAL_F32, value: max },
    ],
    valueBinding: props.value,
  });
}
