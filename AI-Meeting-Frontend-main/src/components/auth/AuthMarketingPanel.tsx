import { Link } from "react-router-dom";
import { APP_BRAND_NAME } from "@/lib/branding";
import { ROUTES } from "@/lib/constants";

export default function AuthMarketingPanel() {
  return (
    <div className="rounded-lg border border-slate-300 bg-white p-7">
      <Link to={ROUTES.home} className="text-sm text-blue-700 hover:underline">← 返回项目首页</Link>
      <h1 className="mt-6 text-3xl font-bold text-slate-800">欢迎使用{APP_BRAND_NAME}</h1>
      <p className="mt-3 text-sm leading-7 text-slate-600">
        登录后可以保存 AI 对话、上传简历参加模拟面试，并查看自己的面试报告。
      </p>
      <ol className="mt-6 space-y-3 border-t border-slate-200 pt-5 text-sm text-slate-700">
        <li>1. 选择模型进行文字对话</li>
        <li>2. 上传 PDF 简历并回答面试题</li>
        <li>3. 查看评分与改进建议</li>
      </ol>
    </div>
  );
}
