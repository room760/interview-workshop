package com.cqr.interviewworkshop.interview.application.jobmatch.application;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.cqr.interviewworkshop.agent.dao.entity.AgentPropertiesDO;
import com.cqr.interviewworkshop.interview.application.guard.core.InterviewAiGuardException;
import com.cqr.interviewworkshop.interview.application.guard.core.InterviewAiGuardStage;
import com.cqr.interviewworkshop.interview.application.jobmatch.JobMatchContext;
import com.cqr.interviewworkshop.interview.application.jobmatch.JobMatchResult;
import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.JobKnowledgeRetriever;
import com.cqr.interviewworkshop.interview.application.prompt.core.PromptService;
import com.cqr.interviewworkshop.interview.shared.InterviewAiInvoker;
import com.cqr.interviewworkshop.interview.shared.InterviewResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobMatchService{

    private static final String KEY_JOB_TITLE = "jobTitle";
    private static final String KEY_JOB_REQUIREMENT = "jobRequirement";
    private static final String KEY_QUESTION = "question";
    private static final String KEY_ANSWER = "answer";
    private static final String KEY_AGENT_USER_INPUT = "AGENT_USER_INPUT";
    private static final String KEY_DOCUMENTS = "documents";
    private static final String KEY_RETRIEVED_KNOWLEDGE = "retrieved_knowledge";
    private static final String JOB_MATCH_PROMPT_CODE = "INTERVIEW_JOB_MATCH";

    private final InterviewAiInvoker interviewAiInvoker;
    private final InterviewResponseParser interviewResponseParser;
    private final JobKnowledgeRetriever jobKnowledgeRetriever;
    private final PromptService promptService;
    private final JobMatchToolCallingService jobMatchToolCallingService;

    public JobMatchResult analyze(
            String sessionId,
            String requestId,
            String questionNumber,
            JobMatchContext context,
            AgentPropertiesDO analyzerAgent) {
        JobMatchResult analyzeResult = analyzeByJobAgent(
                sessionId,requestId,questionNumber,context,analyzerAgent);
        return analyzeResult;
    }
    private JobMatchResult analyzeByJobAgent(
            String sessionId,
            String requestId,
            String questionNumber,
            JobMatchContext context,
            AgentPropertiesDO analyzerAgent){
        try{
            Map<String,Object> parameters = buildAnalyzeWorkflowParameters(sessionId, requestId, questionNumber, context);
            logWorkflowParameters(
                    "analyze",
                    sessionId,
                    requestId,
                    questionNumber,
                    analyzerAgent,
                    parameters
            );
            String workflowResponse = interviewAiInvoker.callAiSyncWithParameters(
                    sessionId + "_analyze",
                    analyzerAgent,
                    parameters,
                    InterviewAiGuardStage.INTERVIEW_JOB_MATCH,
                    interviewAiInvoker.buildSingleFlightKey(
                            InterviewAiGuardStage.INTERVIEW_JOB_MATCH,
                            sessionId,
                            questionNumber,
                            context.getAnswer()
                    )
            );

            JobMatchResult parsed = interviewResponseParser.parseAnalyzeResult(workflowResponse);
            return parsed;
        }catch (InterviewAiGuardException ex){
            throw ex;
        }catch (Exception ex){
            log.warn("Analyze workflow invocation failed, sessionId: {}", sessionId, ex);
            return null;
        }
    }
    private Map<String,Object> buildAnalyzeWorkflowParameters(
            String sessionId,
            String requestId,
            String questionNumber,
            JobMatchContext context){

        String toolResearchContext = null;
        try {
            toolResearchContext = jobMatchToolCallingService.collectResearch(
                            sessionId,
                            requestId,
                            questionNumber,
                            context
                    );
        } catch (RuntimeException ex) {
            log.warn(
                    "JobMatch Tool Calling unavailable, falling back to legacy RAG, " + "sessionId={}, requestId={}",
                    sessionId,
                    requestId,
                    ex
            );
        }

        List<JobKnowledgeDocument> documents;
        String retriedKnowledge;
        if (StrUtil.isNotBlank(toolResearchContext)) {
            // 已经获得统一工具体系返回的有效证据，
            // 避免再次执行一次重复的 RAG 检索。
            documents = List.of();
            context.setRetrievedDocuments(documents);
            retriedKnowledge = toolResearchContext;
        } else {
            // Tool Calling 未配置、执行失败或没有有效工具结果时，
            // 保留之前已经跑通的 RAG 逻辑。
            String retrievalQuery = buildRetrievalQuery(context);
            documents = jobKnowledgeRetriever.retrieve(retrievalQuery);
            context.setRetrievedDocuments(documents);
            retriedKnowledge = buildRetrievedKnowledgeText(documents);
        }

        Map<String,Object> parameters = new LinkedHashMap<>();
        parameters.put(KEY_JOB_TITLE,context.getJobTitle());
        parameters.put(KEY_JOB_REQUIREMENT,context.getJobRequirement());
        parameters.put(KEY_QUESTION,context.getQuestion());
        parameters.put(KEY_ANSWER,context.getAnswer());
        parameters.put(KEY_DOCUMENTS,documents);
        parameters.put(KEY_RETRIEVED_KNOWLEDGE,retriedKnowledge);

        String prompt = promptService.renderPrompt(JOB_MATCH_PROMPT_CODE,parameters);
        parameters.put(KEY_AGENT_USER_INPUT,prompt);
        return parameters;
    }
    private String buildRetrievalQuery(JobMatchContext context) {
        StringBuilder query = new StringBuilder();
        appendQueryPart(query, context.getJobTitle());
        appendQueryPart(query, context.getJobRequirement());
        appendQueryPart(query, context.getQuestion());
        appendQueryPart(query, context.getAnswer());
        return query.toString();
    }
    private void appendQueryPart(StringBuilder query, String value) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        if (query.length() > 0) {
            query.append("\n");
        }
        query.append(value.trim());
    }

    private String buildRetrievedKnowledgeText(List<JobKnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return "暂无相关岗位知识召回结果";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            JobKnowledgeDocument document = documents.get(i);
            if (document == null || StrUtil.isBlank(document.getContent())) {
                continue;
            }
            builder.append("【参考知识 ")
                    .append(i + 1)
                    .append("】")
                    .append("\n");
            builder.append(document.getContent().trim());
            if (StrUtil.isNotBlank(document.getSource())) {
                builder.append("\n来源：")
                        .append(document.getSource());
            }
            if (StrUtil.isNotBlank(document.getTag())) {
                builder.append("\n标签：")
                        .append(document.getTag());
            }
            builder.append("\n\n");
        }
        if (builder.length() == 0) {
            return "暂无相关岗位知识召回结果";
        }
        return builder.toString().trim();
    }

    private void logWorkflowParameters(
            String stage,
            String sessionId,
            String requestId,
            String questionNumber,
            AgentPropertiesDO agent,
            Map<String, Object> parameters) {
        Map<String, Object> debugView = new LinkedHashMap<>();
        if (parameters != null) {
            parameters.forEach((key, value) -> {
                if (value == null) {
                    return;
                }
                if (KEY_AGENT_USER_INPUT.equals(key) || KEY_ANSWER.equals(key) || KEY_RETRIEVED_KNOWLEDGE.equals(key)) {
                    debugView.put(key, clip(String.valueOf(value), 300));
                    return;
                }
                debugView.put(key, value);
            });
        }

        log.info(
                "Workflow request stage={}, sessionId={}, requestId={}, questionNumber={}, flowId={}, params={}",
                stage,
                sessionId,
                requestId,
                questionNumber,
                agent == null ? null : agent.getApiFlowId(),
                JSON.toJSONString(debugView)
        );
    }
    private String clip(String value, int maxLen) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.length() <= maxLen) {
            return cleaned;
        }
        return cleaned.substring(0, maxLen);
    }

}
