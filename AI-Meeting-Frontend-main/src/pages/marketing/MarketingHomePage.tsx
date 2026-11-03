import { useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { BookOpen, ClipboardList, MessageSquare, UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { APP_BRAND_NAME } from "@/lib/branding";
import { ROUTES } from "@/lib/constants";
import { useAppSelector } from "@/store/hooks";

const modules = [
  {
    number: "01",
    icon: MessageSquare,
    title: "AI 对话",
    description: "选择模型，输入问题，查看回答和历史会话。",
  },
  {
    number: "02",
    icon: BookOpen,
    title: "模拟面试",
    description: "上传 PDF 简历，按题作答并查看每轮反馈。",
  },
  {
    number: "03",
    icon: ClipboardList,
    title: "面试报告",
    description: "回看问答记录、得分和改进建议。",
  },
] as const;

export default function MarketingHomePage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAppSelector((state) => state.user);

  const openFeature = useCallback(
    (path: string) => {
      if (isAuthenticated) {
        navigate(path);
        return;
      }
      navigate(ROUTES.auth, { state: { from: { pathname: path } } });
    },
    [isAuthenticated, navigate],
  );

  return (
    <div className="h-full overflow-y-auto bg-slate-100 text-slate-800">
      <header className="border-b border-slate-300 bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-5 py-4">
          <div className="flex items-center gap-3">
            <img src="/interview-workshop-mark.svg" alt="" className="h-9 w-9" />
            <div>
              <p className="text-lg font-bold leading-tight">{APP_BRAND_NAME}</p>
              <p className="text-xs text-slate-500">AI 模拟面试课程项目</p>
            </div>
          </div>
          <nav className="flex flex-wrap items-center gap-2" aria-label="主要功能">
            <Button variant="ghost" onClick={() => openFeature(ROUTES.chat)}>AI 对话</Button>
            <Button variant="ghost" onClick={() => openFeature(ROUTES.interviewIntro)}>模拟面试</Button>
            <Button variant="outline" onClick={() => navigate(isAuthenticated ? ROUTES.chat : ROUTES.auth)}>
              <UserRound className="h-4 w-4" />
              {isAuthenticated ? "进入系统" : "登录 / 注册"}
            </Button>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-5xl space-y-8 px-5 py-8 md:py-12">
        <section className="rounded-lg border border-slate-300 bg-white p-6 md:p-10">
          <p className="text-sm font-medium text-blue-700">课程项目 · 功能展示</p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight md:text-4xl">面试工坊</h1>
          <p className="mt-4 max-w-2xl text-base leading-7 text-slate-600">
            一个帮助同学准备面试的小系统。可以先和 AI 讨论知识点，再上传简历进行模拟面试，最后根据报告复盘。
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Button onClick={() => openFeature(ROUTES.interviewIntro)}>立即体验</Button>
            <Button variant="outline" onClick={() => openFeature(ROUTES.chat)}>试试 AI 对话</Button>
          </div>
        </section>

        <section aria-labelledby="modules-title">
          <h2 id="modules-title" className="mb-4 text-xl font-bold">主要功能</h2>
          <div className="grid gap-4 md:grid-cols-3">
            {modules.map(({ number, icon: Icon, title, description }) => (
              <article key={number} className="rounded-lg border border-slate-300 bg-white p-5">
                <div className="flex items-center justify-between">
                  <span className="flex h-10 w-10 items-center justify-center rounded-md bg-blue-100 text-blue-700"><Icon className="h-5 w-5" /></span>
                  <span className="text-sm text-slate-400">{number}</span>
                </div>
                <h3 className="mt-4 text-lg font-semibold">{title}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">{description}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="rounded-lg border border-slate-300 bg-white p-6" aria-labelledby="steps-title">
          <h2 id="steps-title" className="text-xl font-bold">怎么使用</h2>
          <ol className="mt-4 grid gap-3 text-sm text-slate-700 md:grid-cols-3">
            <li><strong className="mr-2 text-blue-700">1.</strong>注册或登录账号</li>
            <li><strong className="mr-2 text-blue-700">2.</strong>上传简历并用文字回答问题</li>
            <li><strong className="mr-2 text-blue-700">3.</strong>查看评分、反馈与历史记录</li>
          </ol>
        </section>
      </main>
      <footer className="border-t border-slate-300 bg-white px-5 py-5 text-center text-xs text-slate-500">
        面试工坊 · AI 面试练习系统
      </footer>
    </div>
  );
}
