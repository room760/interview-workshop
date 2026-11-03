package com.cqr.interviewworkshop.interview.application.jobmatch.tool;

import cn.hutool.core.util.StrUtil;
import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.JobKnowledgeRetriever;
import com.cqr.interviewworkshop.interview.application.toolcalling.AgentTool;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JobKnowledgeSearchTool implements AgentTool {
    private static final String TOOL_NAME = "job_knowledge_search";
    private static final int MAX_QUERY_LENGTH = 2000;

    private final JobKnowledgeRetriever jobKnowledgeRetriever;

    @Override
    public ToolDefinition getDefinition() {
        ToolDefinition definition = new ToolDefinition();
        definition.setName(TOOL_NAME);
        definition.setDescription("在岗位知识库中检索与职位要求、面试问题或候选人回答" + "相关的参考知识。返回相关文档、来源和标签，" + "不负责生成最终的岗位匹配结论。");

        Map<String,Object> querySchema = new LinkedHashMap<>();
        querySchema.put("type","string");
        querySchema.put("description","用于检索岗位知识的查询文本");

        Map<String,Object> properties = new LinkedHashMap<>();
        properties.put("query",querySchema);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        schema.put("additionalProperties", false);

        definition.setInputSchema(schema);
        return definition;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("Tool arguments must not be null");
        }

        Object queryValue = arguments.get("query");
        if (!(queryValue instanceof String)) {
            throw new IllegalArgumentException("query must be a string");
        }
        String query = ((String) queryValue).trim();
        if (StrUtil.isBlank(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query exceeds maximum length of " + MAX_QUERY_LENGTH
            );
        }

        // 复用原有的 RAG 检索链路，不重新实现向量检索。
        List<JobKnowledgeDocument> documents = jobKnowledgeRetriever.retrieve(query);
        List<Map<String, Object>> results = new ArrayList<>();
        if (documents != null) {
            for (JobKnowledgeDocument document : documents) {
                if (document == null || StrUtil.isBlank(document.getContent())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", document.getContent());
                if (StrUtil.isNotBlank(document.getSource())) {
                    item.put("source", document.getSource());
                }
                if (StrUtil.isNotBlank(document.getTag())) {
                    item.put("tag", document.getTag());
                }
                results.add(item);
            }
        }

        // 返回结构化数据，由 ToolExecutor 统一序列化为 JSON。
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        response.put("count", results.size());
        response.put("results", results);
        return response;
    }
}
