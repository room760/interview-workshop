import { describe, expect, it } from "vitest";
import { AppError, ErrorCode } from "@/lib/errors";
import {
  type AnswerInterviewQuestionResult,
  interviewService,
  normalizeInterviewAnswer,
} from "@/services/interviewService";

describe("normalizeInterviewAnswer", () => {
  it("keeps isFollowUp and followUpNeeded independent", () => {
    const payload = {
      is_follow_up: false,
      follow_up_needed: true,
      next_question: "请说明缓存一致性方案",
      next_question_number: "1-F1",
      follow_up_count: "1",
      finished: false,
      isSuccess: true,
    } as unknown as AnswerInterviewQuestionResult;
    const normalized = normalizeInterviewAnswer(payload);

    expect(normalized.isFollowUp).toBe(false);
    expect(normalized.followUpNeeded).toBe(true);
    expect(normalized.nextQuestionNumber).toBe("1-F1");
    expect(normalized.followUpCount).toBe(1);
  });

  it("normalizes follow-up flags and score fields from mixed naming", () => {
    const payload = {
      isFollowUp: true,
      followUpNeeded: false,
      total_score: "88",
      score_comment: "回答结构清晰",
      next_question: "继续展开事务隔离级别的选择依据",
      next_question_number: "2-F2",
      follow_up_count: 2,
      finished: "false",
    } as unknown as AnswerInterviewQuestionResult;
    const normalized = normalizeInterviewAnswer(payload);

    expect(normalized.isFollowUp).toBe(true);
    expect(normalized.followUpNeeded).toBe(false);
    expect(normalized.totalScore).toBe(88);
    expect(normalized.feedback).toBe("回答结构清晰");
    expect(normalized.nextQuestionNumber).toBe("2-F2");
    expect(normalized.followUpCount).toBe(2);
    expect(normalized.finished).toBe(false);
  });

  it("normalizes the nested JobMatch response without changing interview fields", () => {
    const payload = {
      isSuccess: true,
      score: 86,
      job_match: {
        match_score: "82",
        matched_skills: ["Java", "Spring Boot"],
        missing_skills: ["Kubernetes"],
        gap_analysis: "云原生经验需要补充",
        suggested_action: "完成一个 Kubernetes 部署练习",
      },
    } as unknown as AnswerInterviewQuestionResult;

    const normalized = normalizeInterviewAnswer(payload);

    expect(normalized.score).toBe(86);
    expect(normalized.jobMatch).toEqual({
      matchScore: 82,
      matchedSkills: ["Java", "Spring Boot"],
      missingSkills: ["Kubernetes"],
      gapAnalysis: "云原生经验需要补充",
      suggestedAction: "完成一个 Kubernetes 部署练习",
    });
  });
});

describe("interviewService.answerInterviewQuestion", () => {
  it("rejects empty questionNumber before request", async () => {
    const error = await interviewService
      .answerInterviewQuestion({
        sessionId: "session-1",
        questionNumber: "   ",
        answerContent: "answer",
      })
      .catch((caught) => caught);

    expect(error).toBeInstanceOf(AppError);
    expect((error as AppError).code).toBe(ErrorCode.CLIENT_VALIDATION_ERROR);
  });
});
