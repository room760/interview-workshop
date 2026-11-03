package com.cqr.interviewworkshop.interview.application.jobmatch.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobPostingSearchClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsXunfeiRequestAndFiltersOfficialDomain() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JSONObject> request = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/search", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = """
                    {"success":true,"err_code":"0","data":{"search_results":{"documents":[
                      {"name":"Java Engineer","summary":"招聘 Java 开发","url":"https://careers.example.com/jobs/1","published_date":"2026-11-01"},
                      {"name":"Other","summary":"其他来源","url":"https://other.example.com/jobs/2"},
                      {"name":"Unsafe","summary":"无效链接","url":"javascript:alert(1)"}
                    ]}}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        JobPostingSearchProperties properties = new JobPostingSearchProperties();
        properties.setApiPassword("test-password");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        JobPostingSearchClient client = new JobPostingSearchClient(WebClient.builder(), properties);

        List<Map<String, Object>> results = client.search("Java 招聘", "careers.example.com");

        assertEquals("Bearer test-password", authorization.get());
        JSONObject params = request.get().getJSONObject("search_params");
        assertEquals("Java 招聘", params.getString("query"));
        assertEquals(5, params.getIntValue("limit"));
        assertEquals(false, params.getJSONObject("enhance").getBooleanValue("open_full_text"));
        assertEquals(1, results.size());
        assertEquals("Java Engineer", results.get(0).get("title"));
        assertEquals("招聘 Java 开发", results.get(0).get("content"));
        assertEquals("2026-11-01", results.get(0).get("publishedDate"));
        assertTrue((Boolean) results.get(0).get("domainFilterMatched"));
    }

    @Test
    void surfacesXunfeiBusinessErrors() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/search", exchange -> {
            byte[] response = "{\"success\":false,\"err_code\":\"11200\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        JobPostingSearchProperties properties = new JobPostingSearchProperties();
        properties.setApiPassword("test-password");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        JobPostingSearchClient client = new JobPostingSearchClient(WebClient.builder(), properties);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.search("Java 招聘", null));
        assertTrue(error.getMessage().contains("11200"));
    }
}
