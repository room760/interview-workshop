import { APP_BRAND_NAME } from "@/lib/branding";

type ChatPageHeaderProps = { selectedModelName?: string };

export default function ChatPageHeader({ selectedModelName }: ChatPageHeaderProps) {
  return (
    <div className="border-b border-slate-300 bg-white px-5 py-4 md:px-8">
      <h1 className="text-lg font-bold text-slate-800">AI 对话</h1>
      <p className="mt-1 text-sm text-slate-500">
        {APP_BRAND_NAME} · {selectedModelName ? `当前模型：${selectedModelName}` : "输入问题开始交流"}
      </p>
    </div>
  );
}
