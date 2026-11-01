package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// ai.client 설정에 따라 AiDescriptionClient 구현체가 하나만 등록되는지 검증한다 (Bean 충돌 방지)
// 앱 전체가 아닌 두 구현체만 올린 작은 Spring 컨텍스트를 사용한다
class AiClientSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        // 실제 Spring Boot 앱처럼 "5s" 같은 문자열을 Duration으로 바꿀 수 있게 한다
        .withInitializer(context -> context.getBeanFactory()
            .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withUserConfiguration(StubAiDescriptionClient.class, GeminiAiDescriptionClient.class)
        .withPropertyValues(
            "ai.gemini.base-url=http://localhost:1/v1beta",   // 컨텍스트만 확인하므로 실제로 호출하지 않는다
            "ai.gemini.model=test-model",
            "ai.gemini.connect-timeout=5s",
            "ai.gemini.read-timeout=20s"
        );

    @Test
    void stubIsUsedWhenAiClientIsMissing() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AiDescriptionClient.class);
            assertThat(context).hasSingleBean(StubAiDescriptionClient.class);
        });
    }

    @Test
    void stubIsUsedWhenAiClientIsStub() {
        runner.withPropertyValues("ai.client=stub").run(context -> {
            assertThat(context).hasSingleBean(AiDescriptionClient.class);
            assertThat(context).hasSingleBean(StubAiDescriptionClient.class);
        });
    }

    @Test
    void geminiIsUsedWhenAiClientIsGemini() {
        runner.withPropertyValues("ai.client=gemini", "ai.gemini.api-key=test-api-key").run(context -> {
            assertThat(context).hasSingleBean(AiDescriptionClient.class);
            assertThat(context).hasSingleBean(GeminiAiDescriptionClient.class);
            assertThat(context).doesNotHaveBean(StubAiDescriptionClient.class);
        });
    }

    // Gemini 모드인데 키가 없으면 서버(컨텍스트) 시작이 실패해야 한다
    @Test
    void geminiWithoutApiKeyFailsToStart() {
        runner.withPropertyValues("ai.client=gemini", "ai.gemini.api-key=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .rootCause()
                .hasMessage("GEMINI_API_KEY is not set");
        });
    }
}
