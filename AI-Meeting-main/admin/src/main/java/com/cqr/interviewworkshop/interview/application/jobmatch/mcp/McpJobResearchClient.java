
package com.cqr.interviewworkshop.interview.application.jobmatch.mcp;

import cn.hutool.core.util.StrUtil;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("mcp-client")
@RequiredArgsConstructor
public class McpJobResearchClient {

    private final List<McpSyncClient> mcpSyncClients;

    /**
     * 通过 MCP 调用岗位搜索与知识检索工具。
     */
    public String collectResearch(
            String companyName,
            String jobTitle,
            String officialDomain,
            String userMessage) {

        if (mcpSyncClients.isEmpty()) {
            throw new IllegalStateException(
                    "MCP Client 未配置，请检查 mcp-client Profile"
            );
        }

        // 当前项目只配置一个岗位研究 MCP Server。
        McpSyncClient client = mcpSyncClients.get(0);

        StringBuilder context = new StringBuilder();

        // 1. 搜索企业岗位信息。
        if (StrUtil.isNotBlank(jobTitle)) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("jobTitle", jobTitle);

            putIfNotBlank(arguments, "companyName", companyName);
            putIfNotBlank(arguments, "officialDomain", officialDomain);

            arguments.put(
                    "jobRequirement",
                    "查询岗位技能要求、技术栈和招聘职责"
            );

            String result = callTool(
                    client,
                    "job_posting_search",
                    arguments
            );

            context.append("【企业岗位搜索结果】\n")
                    .append(result)
                    .append("\n\n");
        }

        // 2. 检索岗位相关知识。
        String query = buildKnowledgeQuery(
                companyName,
                jobTitle,
                userMessage
        );

        String knowledgeResult = callTool(
                client,
                "job_knowledge_search",
                Map.of("query", query)
        );

        context.append("【RAG 知识检索结果】\n")
                .append(knowledgeResult)
                .append('\n');

        return context.toString();
    }

    private String callTool(
            McpSyncClient client,
            String toolName,
            Map<String, Object> arguments) {

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest(toolName, arguments)
        );

        if (Boolean.TRUE.equals(result.isError())) {
            throw new IllegalStateException(
                    "MCP 工具调用失败：" + toolName
            );
        }

        if (result.content() == null) {
            return "";
        }

        return result.content().stream()
                .filter(item -> item instanceof McpSchema.TextContent)
                .map(item -> ((McpSchema.TextContent) item).text())
                .collect(Collectors.joining("\n"));
    }

    private String buildKnowledgeQuery(
            String companyName,
            String jobTitle,
            String userMessage) {

        return StrUtil.blankToDefault(companyName, "")
                + "\n"
                + StrUtil.blankToDefault(jobTitle, "")
                + "\n"
                + StrUtil.blankToDefault(userMessage, "")
                + "\n岗位技能要求、核心技术、面试知识与学习路线";
    }

    private void putIfNotBlank(
            Map<String, Object> arguments,
            String key,
            String value) {

        if (StrUtil.isNotBlank(value)) {
            arguments.put(key, value);
        }
    }
}
