package com.cqr.interviewworkshop.interview.application.prompt.core;

import java.util.Map;

public interface PromptService {
    String getPrompt(String promptCode);
    String renderPrompt(String promptCode, Map<String,Object> parameters);
}
