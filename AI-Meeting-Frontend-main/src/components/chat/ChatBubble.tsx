import { cn } from "@/lib/utils";
import { CHAT_ROLES, type ChatRole } from "@/lib/constants";
import {
  CHAT_MESSAGE_STATUS,
  CHAT_MESSAGE_VARIANT,
  type ChatMessageStatus,
  type ChatMessageVariant,
} from "@/lib/chat";
import ChatReasoningPanel from "@/components/chat/ChatReasoningPanel";
import ChatMessageContent from "@/components/chat/ChatMessageContent";
import ChatProgressBubble from "@/components/chat/ChatProgressBubble";

type ChatBubbleProps = {
  role: ChatRole;
  content: string;
  reasoning?: string;
  status?: ChatMessageStatus;
  variant?: ChatMessageVariant;
  progressSteps?: string[];
  activeProgressStep?: number;
};

export default function ChatBubble({
  role,
  content,
  reasoning,
  status,
  variant,
  progressSteps,
  activeProgressStep = 0,
}: ChatBubbleProps) {
  const isUser = role === CHAT_ROLES.user;
  const isStreaming = status === CHAT_MESSAGE_STATUS.streaming;
  const messageVariant = variant || CHAT_MESSAGE_VARIANT.default;
  const isProgress = !isUser && messageVariant === CHAT_MESSAGE_VARIANT.progress;
  const label = isUser
    ? "我"
    : messageVariant === CHAT_MESSAGE_VARIANT.feedback
      ? "评分反馈"
      : messageVariant === CHAT_MESSAGE_VARIANT.jobMatch
        ? "岗位匹配"
        : messageVariant === CHAT_MESSAGE_VARIANT.followUp
          ? "追问"
          : "AI 助手";
  const shouldRenderMessage = isProgress || Boolean(content) || !reasoning;

  return (
    <div className={cn("flex w-full", isUser ? "justify-end" : "justify-start")}>
      <div className="max-w-[90%] space-y-2 md:max-w-[85%]">
        {!isUser && reasoning ? (
          <ChatReasoningPanel reasoning={reasoning} isStreaming={isStreaming} hasContent={Boolean(content)} />
        ) : null}
        {shouldRenderMessage ? (
          isProgress ? (
            <ChatProgressBubble label={content} steps={progressSteps ?? []} activeStep={activeProgressStep} />
          ) : (
            <div className={cn("rounded-lg border px-4 py-3", isUser ? "border-blue-200 bg-blue-50" : "border-slate-300 bg-white")}>
              <p className="mb-2 text-xs font-semibold text-slate-500">{label}</p>
              <ChatMessageContent content={content} isStreaming={isStreaming} showStreamingCursor={Boolean(content) || !reasoning} />
            </div>
          )
        ) : null}
      </div>
    </div>
  );
}
