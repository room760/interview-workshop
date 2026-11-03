package com.cqr.interviewworkshop.interview.application;

import com.cqr.interviewworkshop.interview.application.jobmatch.JobMatchResult;
import com.cqr.interviewworkshop.interview.shared.InterviewResponseParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InterviewResponseParserTest {

    private final InterviewResponseParser parser = new InterviewResponseParser();

    @Test
    void shouldExtractStructuredResultFromNestedWorkflowOutput() {
        String response = """
                {
                  "id": "demeanor-test",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": ""
                      },
                      "output": {
                        "panicLevel": 18,
                        "seriousnessLevel": 82,
                        "emoticonHandling": 74,
                        "compositeScore": 79
                      }
                    }
                  ]
                }
                """;

        Map<String, Object> result = parser.extractStructuredResult(
                response,
                "panicLevel",
                "seriousnessLevel",
                "emoticonHandling",
                "compositeScore"
        );

        assertNotNull(result);
        assertEquals(18, parser.parseScoreFromResponse(result, "panicLevel"));
        assertEquals(82, parser.parseScoreFromResponse(result, "seriousnessLevel"));
        assertEquals(74, parser.parseScoreFromResponse(result, "emoticonHandling"));
        assertEquals(79, parser.parseScoreFromResponse(result, "compositeScore"));
    }

    @Test
    void shouldParseDecimalScoreStringWithRounding() {
        Map<String, Object> result = Map.of("score", "79.6");

        Integer score = parser.parseScoreFromResponse(result, "score");

        assertEquals(80, score);
    }

    @Test
    void shouldParseJobMatchResultFromWorkflowContent() {
        String response = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\\\"matchScore\\\":82,\\\"matchedSkills\\\":[\\\"Java\\\"],\\\"missingSkills\\\":[\\\"Redis\\\"],\\\"gapAnalysis\\\":\\\"Needs cache practice\\\",\\\"suggestedAction\\\":\\\"Build a Redis demo\\\"}"
                      }
                    }
                  ]
                }
                """;

        JobMatchResult result = parser.parseAnalyzeResult(response);

        assertNotNull(result);
        assertEquals(82, result.getMatchScore());
        assertEquals("Java", result.getMatchedSkills().get(0));
        assertEquals("Redis", result.getMissingSkills().get(0));
    }
}
