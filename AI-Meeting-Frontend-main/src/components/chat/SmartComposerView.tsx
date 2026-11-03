import { Send } from "lucide-react";
import { useTextareaAutosize } from "@/hooks/useTextareaAutosize";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

export type SmartComposerViewProps = {
  value: string;
  onChange: (value: string) => void;
  onSend?: () => void;
  placeholder?: string;
  disabled?: boolean;
  showDefaultLeading?: boolean;
  leading?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
};

export default function SmartComposerView({
  value,
  onChange,
  onSend,
  placeholder = "在这里输入内容...",
  disabled = false,
  leading,
  actions,
  className,
}: SmartComposerViewProps) {
  const textareaRef = useTextareaAutosize(value);
  return (
    <div className={cn("rounded-lg border border-slate-300 bg-white p-3", className)}>
      <Textarea
        ref={textareaRef}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            if (value.trim() && !disabled) onSend?.();
          }
        }}
        placeholder={placeholder}
        rows={2}
        className="max-h-40 min-h-16 resize-none border-0 px-1 py-1 text-sm shadow-none focus-visible:ring-0"
        disabled={disabled}
      />
      <div className="mt-2 flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-2">
        <div className="flex items-center gap-2">{leading}{actions}</div>
        <Button type="button" size="sm" onClick={onSend} disabled={disabled || !value.trim()}>
          <Send className="h-4 w-4" />发送
        </Button>
      </div>
    </div>
  );
}
