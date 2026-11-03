import { memo } from "react";
import { ScrollArea } from "@/components/ui/scroll-area";
import ChatBubble from "@/components/chat/ChatBubble";
import { type ChatMessage } from "@/lib/chat";
import { useAutoScroll } from "@/hooks/useAutoScroll";

type ChatListProps = {
  messages: ChatMessage[];
  topContent?: React.ReactNode;
  assistantAvatarSrc?: string;
  className?: string;
};

function ChatList({ messages, topContent, className }: ChatListProps) {
  const scrollRef = useAutoScroll(messages);
  return (
    <ScrollArea className={className} ref={scrollRef}>
      <div className="mx-auto max-w-3xl space-y-4 pb-10">
        {topContent}
        {messages.map((msg) => (
          <ChatBubble
            key={msg.id}
            role={msg.role}
            content={msg.content}
            reasoning={msg.reasoning}
            status={msg.status}
            variant={msg.variant}
            progressSteps={msg.progressSteps}
            activeProgressStep={msg.activeProgressStep}
          />
        ))}
      </div>
    </ScrollArea>
  );
}

export default memo(ChatList);
