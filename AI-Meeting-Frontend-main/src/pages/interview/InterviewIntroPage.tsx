import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, FileText, ListChecks, BarChart3 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ROUTES } from "@/lib/constants";
import { interviewService, type InterviewConversationItem } from "@/services/interviewService";

const ACTIVE_INTERVIEW_STATUSES = new Set(["DRAFT", "RESUME_UPLOADING", "READY", "IN_PROGRESS"]);
const steps = [
  { icon: FileText, title: "上传简历", description: "选择 PDF 文件，系统根据简历准备问题。" },
  { icon: ListChecks, title: "逐题回答", description: "阅读题目，用文字作答并查看反馈。" },
  { icon: BarChart3, title: "查看报告", description: "查看得分、能力图和每道题的记录。" },
] as const;

export default function InterviewIntroPage() {
  const [latestActiveSession, setLatestActiveSession] = useState<InterviewConversationItem | null>(null);
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const response = await interviewService.pageInterviewConversations({ current: 1, size: 10 });
        if (!cancelled) {
          setLatestActiveSession(response.records.find((item) => item.status ? ACTIVE_INTERVIEW_STATUSES.has(item.status) : false) ?? null);
        }
      } catch (error) {
        if (!cancelled) console.error("Failed to load latest interview session:", error);
      }
    };
    void load();
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="h-full overflow-y-auto bg-slate-100 px-5 py-8">
      <div className="mx-auto max-w-4xl space-y-6">
        <div className="rounded-lg border border-slate-300 bg-white p-6 md:p-8">
          <p className="text-sm font-medium text-blue-700">面试练习</p>
          <h1 className="mt-2 text-2xl font-bold text-slate-800">开始一次模拟面试</h1>
          <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-600">
            准备一份 PDF 简历。上传后系统会生成面试题，你可以按顺序输入回答，结束后查看评分与建议。
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Button asChild><Link to={ROUTES.interviewRoom}>开始新面试 <ArrowRight className="h-4 w-4" /></Link></Button>
            {latestActiveSession && (
              <Button asChild variant="outline"><Link to={`${ROUTES.interviewRoom}/${encodeURIComponent(latestActiveSession.sessionId)}`}>继续上次面试</Link></Button>
            )}
            <Button asChild variant="outline"><Link to={ROUTES.interviewReport}>查看历史报告</Link></Button>
          </div>
        </div>
        <section aria-labelledby="interview-steps-title">
          <h2 id="interview-steps-title" className="mb-4 text-lg font-bold text-slate-800">使用流程</h2>
          <div className="grid gap-4 md:grid-cols-3">
            {steps.map(({ icon: Icon, title, description }, index) => (
              <div key={title} className="rounded-lg border border-slate-300 bg-white p-5">
                <div className="flex items-center justify-between text-blue-700"><Icon className="h-5 w-5" /><span className="text-xs">步骤 {index + 1}</span></div>
                <h3 className="mt-4 font-semibold text-slate-800">{title}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">{description}</p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
