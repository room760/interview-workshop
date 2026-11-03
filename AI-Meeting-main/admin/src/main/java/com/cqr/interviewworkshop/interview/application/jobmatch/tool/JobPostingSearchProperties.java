
package com.cqr.interviewworkshop.interview.application.jobmatch.tool;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "interview-workshop-agent.job-posting-search")
public class JobPostingSearchProperties {

    /**
     * 通过环境变量 XUNFEI_WANSOU_API_PASSWORD 注入，不写入源码。
     */
    private String apiPassword = "";

    private String baseUrl = "https://search-api-open.cn-huabei-1.xf-yun.com";

    private Integer maxResults = 5;

    private Integer timeoutSeconds = 10;
}
