import { NotebookPen, Video, VideoOff } from "lucide-react";
import { Button } from "@/components/ui/button";

type InterviewHeaderProps = {
  isReady: boolean;
  currentQuestionNumber: string | null;
  isCurrentQuestionFollowUp: boolean;
  currentFollowUpCount: number;
  isInterviewFinished: boolean;
  totalInterviewScore: number | null;
  isCameraOpen: boolean;
  isEndingInterview: boolean;
  onToggleCamera: () => void;
  onOpenSketchpad: () => void;
  onEndInterview: () => void;
};

export default function InterviewHeader({
  isReady, currentQuestionNumber, isCurrentQuestionFollowUp, currentFollowUpCount,
  isInterviewFinished, totalInterviewScore, isCameraOpen, isEndingInterview,
  onToggleCamera, onOpenSketchpad, onEndInterview,
}: InterviewHeaderProps) {
  return (
    <div className="border-b border-slate-300 bg-white px-5 py-4 md:px-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold text-slate-800">模拟面试</h1>
          <p className="mt-1 text-sm text-slate-500">
            {isInterviewFinished ? "本次面试已结束" : isReady ? "请阅读题目并输入回答" : "请先上传 PDF 简历"}
            {currentQuestionNumber && !isInterviewFinished ? ` · 第 ${currentQuestionNumber} 题` : ""}
            {isCurrentQuestionFollowUp && !isInterviewFinished ? ` · 第 ${currentFollowUpCount} 次追问` : ""}
            {totalInterviewScore !== null ? ` · 当前总分 ${totalInterviewScore}` : ""}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" onClick={onOpenSketchpad}><NotebookPen className="h-4 w-4" />回答提纲</Button>
          <Button variant="outline" size="sm" onClick={onToggleCamera}>
            {isCameraOpen ? <Video className="h-4 w-4" /> : <VideoOff className="h-4 w-4" />}
            {isCameraOpen ? "关闭摄像头" : "摄像头分析（可选）"}
          </Button>
          <Button variant="destructive" size="sm" disabled={isEndingInterview} onClick={onEndInterview}>
            {isEndingInterview ? "处理中..." : "结束面试"}
          </Button>
        </div>
      </div>
    </div>
  );
}
