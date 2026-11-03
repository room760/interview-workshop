
package com.cqr.interviewworkshop.interview.application.jobmatch.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Slf4j
public class JobPostingSearchClient {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$"
    );

    private final WebClient webClient;
    private final JobPostingSearchProperties properties;

    public JobPostingSearchClient(
            WebClient.Builder builder,
            JobPostingSearchProperties properties) {

        this.properties = properties;
        this.webClient = builder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public List<Map<String, Object>> search(
            String query,
            String officialDomain) {

        if (properties.getApiPassword() == null || properties.getApiPassword().isBlank()) {
            throw new IllegalStateException("XUNFEI_WANSOU_API_PASSWORD is not configured");
        }

        String normalizedDomain = normalizeDomain(officialDomain);

        Map<String, Object> searchParams = new LinkedHashMap<>();
        searchParams.put("query", query);
        searchParams.put("limit", resolveMaxResults());
        searchParams.put("enhance", Map.of(
                "open_full_text", false,
                "open_rerank", true
        ));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("search_params", searchParams);

        int timeoutSeconds = properties.getTimeoutSeconds() == null ? 10 : Math.max(1, properties.getTimeoutSeconds());
        String responseBody = webClient.post()
                .uri("/v2/search")
                .headers(headers ->
                        headers.setBearerAuth(
                                properties.getApiPassword().trim()
                        )
                )
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(timeoutSeconds));
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        JSONObject response = JSON.parseObject(responseBody);
        if (!response.getBooleanValue("success")
                || !"0".equals(response.getString("err_code"))) {
            throw new IllegalStateException(
                    "Xunfei search failed, error code: " + response.getString("err_code")
            );
        }
        JSONObject data = response.getJSONObject("data");
        JSONObject searchResults = data == null ? null : data.getJSONObject("search_results");
        JSONArray results = searchResults == null ? null : searchResults.getJSONArray("documents");
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> normalizedResults = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = item.getString("name");
            String url = item.getString("url");
            String content = item.getString("summary");
            if (url == null || !isHttpUrl(url)) {
                continue;
            }

            boolean domainMatched = normalizedDomain != null && matchesDomain(url, normalizedDomain);
            // 提供官网域名时，再执行一次本地过滤。
            if (normalizedDomain != null && !domainMatched) {
                continue;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("title", title);
            result.put("url", url);
            result.put("content", content);
            result.put("publishedDate", item.getString("published_date"));
            result.put("domainFilterMatched", domainMatched);

            normalizedResults.add(result);
        }

        return normalizedResults;
    }

    private int resolveMaxResults() {
        Integer configured = properties.getMaxResults();
        if (configured == null) {
            return 5;
        }
        return Math.max(1, Math.min(configured, 5));
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }

        String normalized = domain.trim()
                .toLowerCase(Locale.ROOT);

        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }

        if (!DOMAIN_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "officialDomain must be a hostname, "
                            + "for example careers.example.com"
            );
        }

        return normalized;
    }

    private boolean matchesDomain(String url, String expectedDomain) {
        try {
            String host = URI.create(url).getHost();

            if (host == null) {
                return false;
            }

            host = host.toLowerCase(Locale.ROOT);

            return host.equals(expectedDomain)
                    || host.endsWith("." + expectedDomain);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isHttpUrl(String url) {
        try {
            URI uri = URI.create(url);

            return ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
