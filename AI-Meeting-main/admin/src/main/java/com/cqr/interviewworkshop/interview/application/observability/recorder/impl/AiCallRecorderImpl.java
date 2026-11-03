package com.cqr.interviewworkshop.interview.application.observability.recorder.impl;

import com.cqr.interviewworkshop.interview.dao.entity.AiCallRecordDO;
import com.cqr.interviewworkshop.interview.application.observability.recorder.AiCallContext;
import com.cqr.interviewworkshop.interview.application.observability.recorder.AiCallRecorder;
import com.cqr.interviewworkshop.interview.application.observability.service.AiCallRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AiCallRecorderImpl implements AiCallRecorder {
    private final AiCallRecordService aiCallRecordService;

    @Override
    public AiCallContext start(String traceId, String agentName, String modelName) {
        AiCallContext context = new AiCallContext();
        context.setTraceId(traceId);
        context.setAgentName(agentName);
        context.setModelName(modelName);
        context.setStartTime(LocalDateTime.now());
        return context;
    }

    @Override
    public void success(AiCallContext context, Integer inputTokens, Integer outputTokens) {
        AiCallRecordDO record = buildBaseRecord(context);
        LocalDateTime endTime = LocalDateTime.now();
        record.setEndTime(endTime);
        record.setLatency(
                Duration.between(
                        context.getStartTime(),
                        endTime
                ).toMillis()
        );
        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        if(inputTokens != null && outputTokens != null){
            record.setTotalTokens(inputTokens + outputTokens);
        }
        record.setStatus("SUCCESS");
        aiCallRecordService.save(record);
    }

    @Override
    public void failed(AiCallContext context, String errorCode, String errorMessage) {
        AiCallRecordDO record = buildBaseRecord(context);
        LocalDateTime endTime = LocalDateTime.now();
        record.setEndTime(endTime);
        record.setLatency(
                Duration.between(
                        context.getStartTime(),
                        endTime
                ).toMillis()
        );
        record.setStatus("FAILED");
        record.setErrorCode(errorCode);
        record.setErrorMessage(errorMessage);
        aiCallRecordService.save(record);
    }

    private AiCallRecordDO buildBaseRecord(AiCallContext context){
        AiCallRecordDO record = new AiCallRecordDO();
        record.setTraceId(context.getTraceId());
        record.setAgentName(context.getAgentName());
        record.setModelName(context.getModelName());
        record.setStartTime(context.getStartTime());
        return record;
    }
}
