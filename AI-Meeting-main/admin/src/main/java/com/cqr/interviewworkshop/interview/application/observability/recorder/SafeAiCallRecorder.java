package com.cqr.interviewworkshop.interview.application.observability.recorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SafeAiCallRecorder {
    private final AiCallRecorder aiCallRecorder;
    public AiCallContext start(String traceId, String agentName, String modelName){
        try {
            return aiCallRecorder.start(traceId, agentName, modelName);
        }catch(Exception e){
            log.warn("save ai call record failed", e);
        }
        return null;
    }
    public void success(AiCallContext context,Integer inputTokens,Integer outputTokens){
        if(context == null){
            return;
        }
        try {
            aiCallRecorder.success(context, inputTokens, outputTokens);
        }catch(Exception e){
            log.warn("save ai call record failed", e);
        }
    }
    public void failed(AiCallContext context, String errorCode, String errorMessage){
        try {
            aiCallRecorder.failed(context, errorCode, errorMessage);
        }catch(Exception e){
            log.warn("save ai failure record failed", e);
        }
    }
}
