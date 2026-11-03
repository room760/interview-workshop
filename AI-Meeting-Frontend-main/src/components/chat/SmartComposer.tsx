import SmartComposerView, { type SmartComposerViewProps } from "@/components/chat/SmartComposerView";

export type SmartComposerProps = SmartComposerViewProps;

export default function SmartComposer(props: SmartComposerProps) {
  return <SmartComposerView {...props} />;
}
