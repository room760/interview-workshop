package com.cqr.interviewworkshop.interview.application.jobmatch.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "interview-workshop-agent.rag.hybrid")
public class RagProperties {
    private boolean enabled = true;
    private boolean fallbackToVector = true;
    private boolean rerankEnabled = true;
    private int chunkSize = 400;
    private int chunkOverlap = 60;
    private int candidateLimit = 20;
    private int rerankLimit = 15;
    private int topK = 5;
    private int rrfK = 60;
    private long rerankTimeoutMillis = 100;
    private String indexPath = "${user.home}/.interview-workshop-agent/rag-lucene";
}
