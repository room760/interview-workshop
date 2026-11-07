package com.cqr.interviewworkshop.interview.flow.extraction;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.cqr.interviewworkshop.agent.application.BusinessAgentResolver;
import com.cqr.interviewworkshop.agent.application.BusinessAgentScene;
import com.cqr.interviewworkshop.agent.dao.entity.AgentPropertiesDO;
import com.cqr.interviewworkshop.interview.api.io.req.InterviewQuestionReqDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewQuestionRespDTO;
import com.cqr.interviewworkshop.interview.application.guard.core.InterviewAiGuardException;
import com.cqr.interviewworkshop.interview.application.guard.core.InterviewAiGuardStage;
import com.cqr.interviewworkshop.interview.application.guard.lock.InterviewAiSessionLockService;
import com.cqr.interviewworkshop.interview.shared.InterviewAiInvoker;
import com.cqr.interviewworkshop.interview.shared.InterviewResponseParser;
import com.cqr.interviewworkshop.interview.service.InterviewQuestionCacheService;
import com.cqr.interviewworkshop.interview.service.InterviewQuestionService;
import com.cqr.interviewworkshop.toolkit.xunfei.XingChenAIClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewQuestionExtractionService {

    private static final String EXTRACTION_PROMPT =
            "根据上传的简历提取技术面试问题。"
                    + "只输出纯JSON，禁止额外说明、markdown、多余文字。"
                    + "JSON固定key：questions、suggest、type、resumeScore。"
                    + "不允许闲聊、打招呼、解释性话术。";

    private final BusinessAgentResolver businessAgentResolver;
    private final XingChenAIClient xingChenAIClient;
    private final InterviewAiInvoker interviewAiInvoker;
    private final InterviewAiSessionLockService interviewAiSessionLockService;
    private final InterviewQuestionService interviewQuestionService;
    private final InterviewQuestionCacheService interviewQuestionCacheService;
    private final InterviewResponseParser interviewResponseParser;

    public InterviewQuestionRespDTO extractInterviewQuestions(InterviewQuestionReqDTO reqDTO) {
        InterviewQuestionRespDTO response = new InterviewQuestionRespDTO();
        response.setSessionId(reqDTO.getSessionId());
        response.setUserName(reqDTO.getUserName());
        AgentPropertiesDO agentProperties = businessAgentResolver.resolveRequired(BusinessAgentScene.INTERVIEW_QUESTION_EXTRACTION);
        reqDTO.setAgentId(agentProperties.getId());
        response.setIsSuccess(0);

        // 哈希计算是纯本地操作，在获取分布式锁之前完成，减少锁占用时间。
        String resumeContentHash = computeResumeHash(reqDTO.getResumePdf(), reqDTO.getSessionId());
        RLock heavyLock = null;
        long startTime = System.currentTimeMillis();
        try {
            heavyLock = interviewAiSessionLockService.acquire(reqDTO.getSessionId(), InterviewAiGuardStage.INTERVIEW_EXTRACTION);
            if (heavyLock == null) {
                response.setErrorMessage("AI_OVERLOADED: extraction is processing, please retry");
                return response;
            }

            // 上传到讯飞星辰的文件服务，获取一个文件 URL
            String fileUrl = uploadResumeIfPresent(reqDTO, agentProperties, response);
            if (fileUrl == null) {
                return response;
            }

            // 调用简历出题官
            String fullContent = interviewAiInvoker.callAiSyncWithFile(
                    EXTRACTION_PROMPT,
                    reqDTO.getSessionId(),
                    agentProperties,
                    fileUrl,
                    InterviewAiGuardStage.INTERVIEW_EXTRACTION,
                    interviewAiInvoker.buildSingleFlightKey(
                            InterviewAiGuardStage.INTERVIEW_EXTRACTION,
                            reqDTO.getSessionId(),
                            resumeContentHash
                    )
            );

            long responseTime = System.currentTimeMillis() - startTime;
            reqDTO.setResumeFileUrl(fileUrl);
            // 持久化原始响应到mongodb
            persistRawResponse(reqDTO, fullContent, responseTime);
            response.setResumeFileUrl(fileUrl);
            response.setResponseTime((int) responseTime);
            // 解析原始内容，提取面试题等业务数据，并初始化面试流程
            if (!populateStructuredResponse(reqDTO, response, fullContent)) {
                return response;
            }

            response.setIsSuccess(1);
            log.info("Interview question extraction completed, sessionId={}", reqDTO.getSessionId());
            return response;
        } catch (InterviewAiGuardException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.warn("Interview question extraction guarded failure, sessionId={}, code={}, message={}",
                    reqDTO.getSessionId(), e.getErrorCode(), e.getMessage());
            try {
                // 失败也落库，但仅记录错误信息；结构化字段覆盖保护在 service 层统一处理。
                interviewQuestionService.createFromAIResponse(
                        reqDTO,
                        "{\"error\":\"" + e.getMessage() + "\"}",
                        (int) responseTime,
                        null
                );
            } catch (Exception saveException) {
                log.error("Failed to save extraction guard error record: {}", saveException.getMessage());
            }
            response.setErrorMessage(e.getMessage());
            response.setIsSuccess(0);
            return response;
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("Interview question extraction failed: {}", e.getMessage(), e);
            try {
                interviewQuestionService.createFromAIResponse(
                        reqDTO,
                        "{\"error\":\"" + e.getMessage() + "\"}",
                        (int) responseTime,
                        null
                );
            } catch (Exception saveException) {
                log.error("Failed to save extraction error record: {}", saveException.getMessage());
            }

            response.setErrorMessage("interview question extraction failed: " + e.getMessage());
            response.setIsSuccess(0);
            return response;
        } finally {
            interviewAiSessionLockService.release(heavyLock);
        }
    }

    private String uploadResumeIfPresent(
            InterviewQuestionReqDTO reqDTO,
            AgentPropertiesDO agentProperties,
            InterviewQuestionRespDTO response) {
        if (reqDTO.getResumePdf() == null || reqDTO.getResumePdf().isEmpty()) {
            response.setErrorMessage("resume file does not exist");
            return null;
        }

        try {
            String fileUrl = xingChenAIClient.uploadFile(
                    reqDTO.getResumePdf(),
                    agentProperties.getApiKey(),
                    agentProperties.getApiSecret()
            );
            log.info("Resume uploaded successfully, url={}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("Resume upload failed: {}", e.getMessage());
            response.setErrorMessage("failed to upload resume file");
            return null;
        }
    }

    private void persistRawResponse(InterviewQuestionReqDTO reqDTO, String fullContent, long responseTime) {
        try {
            interviewQuestionService.createFromAIResponse(
                    reqDTO,
                    fullContent,
                    (int) responseTime,
                    null
            );
            log.info("Interview question response saved, sessionId={}", reqDTO.getSessionId());
        } catch (Exception e) {
            log.error("Failed to save interview question response, sessionId={}, error={}",
                    reqDTO.getSessionId(), e.getMessage());
        }
    }

    private boolean populateStructuredResponse(
            InterviewQuestionReqDTO reqDTO,
            InterviewQuestionRespDTO response,
            String fullContent) {
        try {
            log.info("Start parsing interview question response, sessionId={}, payloadLength={}, payloadHash={}",
                    reqDTO.getSessionId(),
                    fullContent == null ? 0 : fullContent.length(),
                    digestForLog(fullContent));
            //  1)  检查 AI 工作流错误
            String workflowErrorMessage = interviewResponseParser.extractWorkflowErrorMessage(fullContent);
            if (StrUtil.isNotBlank(workflowErrorMessage)) {
                response.setErrorMessage(workflowErrorMessage);
                log.warn("Interview question workflow returned error, sessionId={}, message={}",
                        reqDTO.getSessionId(), workflowErrorMessage);
                return false;
            }

            //  2)  把原始文本解析成 Map，读取各个业务字段
            String extractedContent = interviewResponseParser.extractContentFromInterviewResponse(fullContent);
            log.info("Extracted interview content summary, sessionId={}, contentLength={}, contentHash={}",
                    reqDTO.getSessionId(),
                    extractedContent == null ? 0 : extractedContent.length(),
                    digestForLog(extractedContent));
            if (StrUtil.isBlank(extractedContent)) {
                response.setErrorMessage("interview question response content is blank");
                return false;
            }
            Map<String, Object> responseMap = interviewResponseParser.extractStructuredResult(
                    extractedContent,
                    "questions",
                    "sugest",
                    "suggestions",
                    "resumeScore",
                    "type",
                    "smallTalk"
            );
            if (responseMap == null || responseMap.isEmpty()) {
                response.setErrorMessage("interview question response parse failed");
                log.warn("Interview question response parse failed, responseMap is null");
                return false;
            }
            log.info("Interview question response fields: {}", responseMap.keySet());

            //  3)  整理各个业务字段
            Map<String, Object> resumeContext = buildResumeContext(responseMap);
            if (!resumeContext.isEmpty()) {
                interviewQuestionCacheService.cacheResumeContext(reqDTO.getSessionId(), resumeContext);
            }
            List<String> questions = normalizeStringList(responseMap.get("questions"));
            if (questions.isEmpty()) {
                String smallTalk = interviewResponseParser.asString(responseMap.get("smallTalk"));
                response.setErrorMessage(StrUtil.isNotBlank(smallTalk)
                        ? "workflow fell back to smallTalk instead of interview questions"
                        : "workflow returned empty interview questions");
                log.warn("Interview question extraction returned no questions, sessionId={}, smallTalk={}",
                        reqDTO.getSessionId(), smallTalk);
                return false;
            }

            //  4)  写入 Redis 业务缓存
            interviewQuestionCacheService.cacheInterviewQuestions(reqDTO.getSessionId(), questions);
            //  5)  初始化面试流程
            Map<String, String> questionMap = interviewQuestionCacheService.getSessionInterviewQuestions(reqDTO.getSessionId());
            response.setQuestions(questionMap);
            response.setQuestionCount(questions.size());
            interviewQuestionCacheService.initInterviewFlow(reqDTO.getSessionId(), questions.size());
            //  6)  填充返回给前端的 response
            List<String> suggestions = normalizeSuggestions(responseMap);
            if (!suggestions.isEmpty()) {
                interviewQuestionCacheService.cacheInterviewSuggestions(reqDTO.getSessionId(), suggestions);
                Map<String, String> suggestionMap =
                        interviewQuestionCacheService.getSessionInterviewSuggestions(reqDTO.getSessionId());
                response.setSuggestions(suggestionMap);
                response.setSuggestionCount(suggestions.size());
            } else {
                log.warn("Interview question response does not contain suggestions");
            }
            // type 字段兼容历史别名，保证 interviewDirection/interviewType 在不同模型输出下都能回补。
            String interviewType = interviewResponseParser.asString(responseMap.get("type"));
            if (StrUtil.isBlank(interviewType)) {
                interviewType = interviewResponseParser.asString(responseMap.get("interviewType"));
            }
            if (StrUtil.isBlank(interviewType)) {
                interviewType = interviewResponseParser.asString(responseMap.get("direction"));
            }
            if (StrUtil.isBlank(interviewType)) {
                interviewType = interviewResponseParser.asString(responseMap.get("interviewDirection"));
            }
            if (StrUtil.isNotBlank(interviewType)) {
                interviewQuestionCacheService.cacheInterviewDirection(reqDTO.getSessionId(), interviewType);
                response.setInterviewType(interviewType);
            } else {
                log.warn("Interview question response does not contain type field");
            }
            Integer resumeScore = interviewResponseParser.parseScoreFromResponse(responseMap, "resumeScore");
            if (resumeScore != null) {
                interviewQuestionCacheService.cacheResumeScore(reqDTO.getSessionId(), resumeScore);
                response.setResumeScore(resumeScore);
            } else {
                log.warn("Interview question response does not contain valid resumeScore field");
            }

            //  7)  持久化结构化业务字段
            // 结构化二次落库用于 Redis 丢失后的恢复来源，避免报告阶段出现字段缺失。
            persistStructuredFields(reqDTO, questions, suggestions, resumeScore, interviewType, resumeContext);
            interviewQuestionCacheService.resetSessionScore(reqDTO.getSessionId());
            log.info("Session score reset, sessionId={}", reqDTO.getSessionId());
            return true;
        } catch (Exception cacheException) {
            response.setErrorMessage("failed to parse interview question response");
            log.error(
                    "Failed to cache interview question response, sessionId={}, error={}",
                    reqDTO.getSessionId(),
                    cacheException.getMessage()
            );
            return false;
        }
    }

    private List<String> normalizeSuggestions(Map<String, Object> responseMap) {
        if (responseMap == null || responseMap.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> suggestions = normalizeStringList(responseMap.get("sugest"));
        if (!suggestions.isEmpty()) {
            return suggestions;
        }
        return normalizeStringList(responseMap.get("suggestions"));
    }

    private List<String> normalizeStringList(Object value) {
        return interviewResponseParser.asStringList(value);
    }

    /**
     * 计算简历文件内容的 SHA-256 哈希，用于 single-flight 去重。
     * 相同文件内容产生相同哈希，避免因上传 URL 每次变化导致去重失效。
     *
     * @param resumePdf 简历文件
     * @param sessionId 会话标识，仅用于日志
     * @return 文件内容的 SHA-256 十六进制字符串，文件为空时返回 null
     */
    private String computeResumeHash(MultipartFile resumePdf, String sessionId) {
        if (resumePdf == null || resumePdf.isEmpty()) {
            log.debug("Resume PDF is null or empty, cannot compute content hash, sessionId={}", sessionId);
            return null;
        }

        try {
            byte[] fileBytes = resumePdf.getBytes();
            String hash = DigestUtil.sha256Hex(fileBytes);
            log.debug("Computed resume content hash, sessionId={}, hash={}", sessionId, hash);
            return hash;
        } catch (Exception e) {
            log.warn("Failed to read resume PDF bytes for content hashing, sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }

    private String digestForLog(String value) {
        if (StrUtil.isBlank(value)) {
            return "-";
        }
        return DigestUtil.sha256Hex(value).substring(0, 16);
    }

    private void persistStructuredFields(
            InterviewQuestionReqDTO reqDTO,
            List<String> questions,
            List<String> suggestions,
            Integer resumeScore,
            String interviewType,
            Map<String, Object> resumeContext) {
        try {
            interviewQuestionService.upsertStructuredExtraction(
                    reqDTO.getSessionId(),
                    reqDTO.getUserName(),
                    reqDTO.getAgentId(),
                    reqDTO.getResumeFileUrl(),
                    questions,
                    suggestions,
                    resumeScore,
                    interviewType,
                    resumeContext
            );
        } catch (Exception ex) {
            log.warn("Failed to persist structured extraction fields, sessionId={}, error={}",
                    reqDTO.getSessionId(), ex.getMessage(), ex);
        }
    }

    private Map<String, Object> buildResumeContext(Map<String, Object> responseMap) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (responseMap == null || responseMap.isEmpty()) {
            return context;
        }
        for (Map.Entry<String, Object> entry : responseMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if ("questions".equals(key) || "sugest".equals(key) || "suggestions".equals(key)) {
                continue;
            }
            context.put(key, value);
        }
        return context;
    }
}
