package com.cqr.interviewworkshop.interview.application.toolcalling.strategy;

import com.cqr.interviewworkshop.ai.dao.entity.AiPropertiesDO;
import com.cqr.interviewworkshop.interview.application.toolcalling.config.ToolCallingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolCallingStrategyResolverTest {

    private ToolCallingStrategy nativeStrategy;
    private ToolCallingStrategy fallbackStrategy;
    private ToolCallingStrategyResolver resolver;

    @BeforeEach
    void setUp() {
        ToolCallingProperties properties = new ToolCallingProperties();
        properties.setModelModes(Map.of(
                "3", ToolCallingMode.FALLBACK,
                "4", ToolCallingMode.NATIVE,
                "18", ToolCallingMode.DISABLED
        ));

        nativeStrategy = mock(ToolCallingStrategy.class);
        fallbackStrategy = mock(ToolCallingStrategy.class);
        when(nativeStrategy.getName()).thenReturn("native");
        when(fallbackStrategy.getName()).thenReturn("fallback");
        when(nativeStrategy.supports(any(AiPropertiesDO.class))).thenReturn(true);
        when(fallbackStrategy.supports(any(AiPropertiesDO.class))).thenReturn(true);

        resolver = new ToolCallingStrategyResolver(
                properties,
                List.of(nativeStrategy, fallbackStrategy)
        );
    }

    @Test
    void shouldResolveNativeByAiPropertiesId() {
        assertSame(nativeStrategy, resolver.resolve(aiProperties(4L)));
    }

    @Test
    void shouldResolveFallbackByAiPropertiesId() {
        assertSame(fallbackStrategy, resolver.resolve(aiProperties(3L)));
    }

    @Test
    void shouldRejectDisabledModel() {
        assertThrows(IllegalStateException.class, () -> resolver.resolve(aiProperties(18L)));
    }

    @Test
    void shouldRejectUnconfiguredModel() {
        assertThrows(IllegalStateException.class, () -> resolver.resolve(aiProperties(99L)));
    }

    private AiPropertiesDO aiProperties(Long id) {
        AiPropertiesDO properties = new AiPropertiesDO();
        properties.setId(id);
        properties.setAiType("deepseek");
        properties.setApiKey("test-key");
        properties.setModelName("deepseek-chat");
        return properties;
    }
}
