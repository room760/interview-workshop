
package com.cqr.interviewworkshop.interview.application.jobmatch.mcp;

import com.cqr.interviewworkshop.interview.application.jobmatch.tool.JobKnowledgeSearchTool;
import com.cqr.interviewworkshop.interview.application.jobmatch.tool.JobPostingSearchTool;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Profile("mcp-server")
@RequiredArgsConstructor
public class JobResearchMcpTools {

    private final JobPostingSearchTool jobPostingSearchTool;
    private final JobKnowledgeSearchTool jobKnowledgeSearchTool;

    @Tool(
            name = "job_posting_search",
            description = "搜索目标公司的招聘岗位信息，"
                    + "返回招聘要求和搜索来源。"
    )
    public Map<String, Object> searchJobPostings(
            @ToolParam(
                    description = "目标岗位名称，例如 Java 后端开发工程师"
            )
            String jobTitle,

            @ToolParam(
                    description = "岗位技能要求，可选",
                    required = false
            )
            String jobRequirement,

            @ToolParam(
                    description = "公司名称，可选",
                    required = false
            )
            String companyName,

            @ToolParam(
                    description = "官方招聘域名，可选，只传域名"
                            + "，不要传完整 URL",
                    required = false
            )
            String officialDomain) {

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("jobTitle", jobTitle);
        arguments.put("jobRequirement", jobRequirement);
        arguments.put("companyName", companyName);
        arguments.put("officialDomain", officialDomain);

        return jobPostingSearchTool.execute(arguments);
    }

    @Tool(
            name = "job_knowledge_search",
            description = "在 Interview Workshop 的岗位知识库中检索相关知识，"
                    + "返回知识内容、来源和标签。"
    )
    public Map<String, Object> searchJobKnowledge(
            @ToolParam(
                    description = "需要检索的岗位技能、技术概念或知识问题"
            )
            String query) {

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", query);

        return jobKnowledgeSearchTool.execute(arguments);
    }
}
