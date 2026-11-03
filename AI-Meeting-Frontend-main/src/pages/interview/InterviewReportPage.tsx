import { Link, useLocation } from "react-router-dom";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import InterviewReportHeader from "@/components/interview/report/InterviewReportHeader";
import InterviewScoreAndRadarCard from "@/components/interview/report/InterviewScoreAndRadarCard";
import InterviewConclusionCard from "@/components/interview/report/InterviewConclusionCard";
import InterviewNextActionsCard from "@/components/interview/report/InterviewNextActionsCard";
import InterviewQaReplayCard from "@/components/interview/report/InterviewQaReplayCard";
import { useInterviewReportData } from "@/hooks/interview/report/useInterviewReportData";
import { useInterviewRecords } from "@/hooks/interview/records/useInterviewRecords";
import { buildReportSearch, getReportSessionIdFromLocation } from "@/lib/interviewReportRoute";
import { ROUTES } from "@/lib/constants";

export default function InterviewReportPage() {
  const location = useLocation();
  const reportSessionId = getReportSessionIdFromLocation(location);
  const records = useInterviewRecords({ enabled: !reportSessionId });
  const {
    isRecordLoading, recordError, resumeScore, interviewScore, compositeScore,
    isCompositeEstimated, radarPoints, sortedSuggestions, interviewDirection,
    qaReviews, reviewFeedback,
  } = useInterviewReportData(reportSessionId);

  if (!reportSessionId) {
    return (
      <div className="h-full overflow-y-auto bg-slate-100 px-5 py-8">
        <div className="mx-auto max-w-4xl space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div><h1 className="text-2xl font-bold">历史面试报告</h1><p className="mt-1 text-sm text-slate-600">选择一次面试，查看得分和问答记录。</p></div>
            <Button asChild variant="outline"><Link to={ROUTES.interviewIntro}>开始新面试</Link></Button>
          </div>
          {records.status === "pending" && <p className="text-sm text-slate-600">正在加载记录...</p>}
          {records.status === "error" && <p role="alert" className="text-sm text-red-700">加载失败，请稍后重试。</p>}
          {records.status === "success" && records.interviewRecords.length === 0 && (
            <Card className="border-slate-300 p-6 text-sm text-slate-600">还没有面试报告。完成一次模拟面试后会显示在这里。</Card>
          )}
          <div className="space-y-3">
            {records.interviewRecords.map((record) => (
              <Link key={record.sessionId} to={`${ROUTES.interviewReport}${buildReportSearch(record.sessionId)}`} state={{ sessionId: record.sessionId }} className="block rounded-lg border border-slate-300 bg-white p-5 hover:border-blue-500">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-semibold text-slate-800">{record.interviewDirection || "模拟面试"}</span>
                  <span className="text-sm text-blue-700">查看报告 →</span>
                </div>
                <p className="mt-2 text-sm text-slate-500">{record.startTime || record.createTime || "日期未知"} · 面试得分：{record.interviewScore ?? "--"}</p>
              </Link>
            ))}
          </div>
          {records.hasNextPage && <Button variant="outline" disabled={records.isFetchingNextPage} onClick={() => void records.fetchNextPage()}>{records.isFetchingNextPage ? "加载中..." : "加载更多"}</Button>}
        </div>
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto bg-slate-100 px-5 py-8">
      <div className="mx-auto max-w-5xl space-y-5">
        <InterviewReportHeader />
        <div className="grid items-start gap-5 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="space-y-5">
            <InterviewScoreAndRadarCard resumeScore={resumeScore} interviewScore={interviewScore} compositeScore={compositeScore} isCompositeEstimated={isCompositeEstimated} radarPoints={radarPoints} />
            <InterviewNextActionsCard reviewFeedback={reviewFeedback} sortedSuggestions={sortedSuggestions} isRecordLoading={isRecordLoading} recordError={recordError} />
          </div>
          <InterviewConclusionCard interviewDirection={interviewDirection} reviewFeedback={reviewFeedback} isRecordLoading={isRecordLoading} recordError={recordError} />
        </div>
        <InterviewQaReplayCard qaReviews={qaReviews} isRecordLoading={isRecordLoading} recordError={recordError} />
      </div>
    </div>
  );
}
