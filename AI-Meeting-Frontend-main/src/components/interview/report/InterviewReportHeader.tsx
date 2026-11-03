import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { ROUTES } from "@/lib/constants";

export default function InterviewReportHeader() {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h1 className="text-2xl font-bold text-slate-800">面试报告</h1><p className="mt-1 text-sm text-slate-600">查看得分、逐题反馈和改进建议</p></div>
      <div className="flex gap-2">
        <Button asChild variant="outline"><Link to={ROUTES.interviewReport}>返回历史</Link></Button>
        <Button asChild><Link to={ROUTES.interviewIntro}>再练一次</Link></Button>
      </div>
    </div>
  );
}
