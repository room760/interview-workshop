
package com.cqr.interviewworkshop.interview.application.jobmatch.tool;

import cn.hutool.core.util.StrUtil;
import com.cqr.interviewworkshop.interview.application.toolcalling.AgentTool;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JobPostingSearchTool implements AgentTool {

    private static final String TOOL_NAME = "job_posting_search";

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_REQUIREMENT_LENGTH = 3000;
    private static final int MAX_COMPANY_LENGTH = 200;

    private final JobPostingSearchClient searchClient;

    @Override
    public ToolDefinition getDefinition() {
        ToolDefinition definition = new ToolDefinition();

        definition.setName(TOOL_NAME);
        definition.setDescription(
                "搜索企业招聘岗位信息。根据职位名称、岗位要求和公司名称"
                        + "查找招聘页面。若提供 officialDomain，则仅返回匹配该域名"
                        + "的搜索结果；未提供时，不能保证结果来自企业官网。"
        );

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put(
                "jobTitle",
                stringSchema("职位名称，例如 Java 后端开发工程师")
        );

        properties.put(
                "jobRequirement",
                stringSchema("岗位要求、技能要求或职责描述")
        );

        properties.put(
                "companyName",
                stringSchema("公司名称，可选")
        );

        properties.put(
                "officialDomain",
                stringSchema(
                        "公司官方招聘网站域名，可选，例如 careers.example.com；"
                                + "不要传入完整 URL"
                )
        );

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("jobTitle"));
        schema.put("additionalProperties", false);

        definition.setInputSchema(schema);

        return definition;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException(
                    "Tool arguments must not be null"
            );
        }

        String jobTitle = requiredString(
                arguments, "jobTitle", MAX_TITLE_LENGTH
        );

        String jobRequirement = optionalString(
                arguments, "jobRequirement",
                MAX_REQUIREMENT_LENGTH
        );

        String companyName = optionalString(
                arguments, "companyName", MAX_COMPANY_LENGTH
        );

        String officialDomain = optionalString(
                arguments, "officialDomain", 253
        );

        String query = buildQuery(
                companyName, jobTitle, jobRequirement
        );

        List<Map<String, Object>> results =
                searchClient.search(query, officialDomain);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        response.put("resultCount", results.size());
        response.put("results", results);
        response.put(
                "domainFilterApplied",
                StrUtil.isNotBlank(officialDomain)
        );

        response.put(
                "sourceNotice",
                StrUtil.isNotBlank(officialDomain)
                        ? "Results were filtered by the supplied domain. "
                        + "Domain ownership has not been independently verified."
                        : "Search results are not verified as official company sources."
        );

        return response;
    }

    private Map<String, Object> stringSchema(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description);
        return schema;
    }

    private String requiredString(
            Map<String, Object> arguments,
            String key,
            int maxLength) {

        String value = optionalString(arguments, key, maxLength);

        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(
                    key + " must not be blank"
            );
        }

        return value;
    }

    private String optionalString(
            Map<String, Object> arguments,
            String key,
            int maxLength) {

        Object value = arguments.get(key);

        if (value == null) {
            return null;
        }

        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                    key + " must be a string"
            );
        }

        String text = ((String) value).trim();

        if (text.length() > maxLength) {
            throw new IllegalArgumentException(
                    key + " exceeds maximum length of " + maxLength
            );
        }

        return text.isEmpty() ? null : text;
    }

    private String buildQuery(
            String companyName,
            String jobTitle,
            String jobRequirement) {

        StringBuilder query = new StringBuilder();

        append(query, companyName);
        append(query, jobTitle);
        append(query, jobRequirement);

        query.append(" 招聘 岗位 职位要求 官方招聘 careers jobs");

        return query.toString();
    }

    private void append(StringBuilder query, String value) {
        if (StrUtil.isBlank(value)) {
            return;
        }

        if (query.length() > 0) {
            query.append(' ');
        }

        query.append(value.trim());
    }
}
