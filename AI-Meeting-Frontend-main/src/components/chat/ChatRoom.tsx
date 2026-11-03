import { cn } from "@/lib/utils";
import ChatList from "@/components/chat/ChatList";
import ChatComposer from "@/components/chat/ChatComposer";
import { type ChatMessage } from "@/lib/chat";

type ChatRoomProps = {
  header?: React.ReactNode;
  topContent?: React.ReactNode;
  messages: ChatMessage[];
  inputValue: string;
  onInputChange: (value: string) => void;
  onSend?: () => void;
  inputPlaceholder?: string;
  inputDisabled?: boolean;
  assistantAvatarSrc?: string;
  composerLeading?: React.ReactNode;
  composerActions?: React.ReactNode;
  contentOverlay?: React.ReactNode;
  footer?: React.ReactNode;
  className?: string;
  customComposer?: React.ReactNode;
};

export default function ChatRoom({
  header, topContent, messages, inputValue, onInputChange, onSend,
  inputPlaceholder, inputDisabled, composerLeading, composerActions,
  contentOverlay, footer, className, customComposer,
}: ChatRoomProps) {
  return (
    <div className={cn("relative flex h-full flex-col bg-slate-100", className)}>
      {header}
      <div className="relative min-h-0 flex-1 overflow-hidden">
        <ChatList className="h-full px-4 py-5 md:px-8" messages={messages} topContent={topContent} />
        {contentOverlay}
      </div>
      <div className="relative z-30 border-t border-slate-300 bg-white p-4">
        <div className="mx-auto max-w-3xl">
          {customComposer ?? (
            <ChatComposer
              value={inputValue}
              onChange={onInputChange}
              onSend={onSend}
              placeholder={inputPlaceholder}
              disabled={inputDisabled}
              leading={composerLeading}
              actions={composerActions}
            />
          )}
          {footer}
        </div>
      </div>
    </div>
  );
}
