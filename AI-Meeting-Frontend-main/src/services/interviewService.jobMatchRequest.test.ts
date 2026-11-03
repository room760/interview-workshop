import { beforeEach, describe, expect, it, vi } from "vitest";

const { postMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
}));

vi.mock("@/lib/request", () => ({
  default: {
    post: postMock,
  },
  assertRequestAuthorized: vi.fn(),
  buildApiUrl: vi.fn(),
}));

import { interviewService } from "@/services/interviewService";

describe("interviewService JobMatch request", () => {
  beforeEach(() => {
    postMock.mockReset();
    postMock.mockResolvedValue({
      isSuccess: true,
      finished: false,
    });
  });

  it("sends trimmed job fields through the existing JSON answer endpoint", async () => {
    await interviewService.answerInterviewQuestion({
      sessionId: "session-1",
      questionNumber: "Q1",
      answerContent: "answer",
      requestId: "request-1",
      jobTitle: "  Java 后端开发工程师  ",
      jobRequirement: "  熟悉 Spring Boot  ",
      companyName: "  示例科技  ",
      officialDomain: "  example.com  ",
    });

    expect(postMock).toHaveBeenCalledWith(
      "/interview-workshop/v1/interview/sessions/session-1/interview/answer-json",
      {
        questionNumber: "Q1",
        answerContent: "answer",
        requestId: "request-1",
        jobTitle: "Java 后端开发工程师",
        jobRequirement: "熟悉 Spring Boot",
        companyName: "示例科技",
        officialDomain: "example.com",
      },
      expect.objectContaining({
        timeout: 180000,
      }),
    );
  });
});
