
package com.cqr.interviewworkshop.interview.application.jobmatch.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("mcp-server")
public class JobResearchMcpServerConfig {

    @Bean
    public ToolCallbackProvider jobResearchMcpToolCallbackProvider(
            JobResearchMcpTools jobResearchMcpTools) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(jobResearchMcpTools)
                .build();
    }
}
