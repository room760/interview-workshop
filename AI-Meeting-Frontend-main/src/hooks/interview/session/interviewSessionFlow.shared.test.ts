import { describe, expect, it } from "vitest";
import {
  buildJobMatchMessage,
  JOB_MATCH_EMPTY_TEXT,
} from "@/hooks/interview/session/interviewSessionFlow.shared";

describe("buildJobMatchMessage", () => {
  it("renders only fields provided by the backend JobMatch DTO", () => {
    const message = buildJobMatchMessage({
      matchScore: 88,
      matchedSkills: ["Java", "Spring Boot"],
      missingSkills: ["Kubernetes"],
      gapAnalysis: "容器编排实践不足",
      suggestedAction: "补充部署实战",
    });

    expect(message).toContain("岗位匹配度：88 分");
    expect(message).toContain("已匹配技能");
    expect(message).toContain("待补充技能");
    expect(message).toContain("容器编排实践不足");
    expect(message).toContain("补充部署实战");
  });

  it("returns a non-blocking empty state when JobMatch is unavailable", () => {
    expect(buildJobMatchMessage(null)).toBe(JOB_MATCH_EMPTY_TEXT);
  });
});
