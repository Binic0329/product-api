package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// 가짜 Gemini 서버(localhost)에 실제 HTTP 요청을 보내, 요청 형식·응답 파싱·오류 구분을 검증한다
class GeminiAiDescriptionClientTest {

    private static final String API_KEY = "test-api-key";

    private FakeGeminiServer server;
    private GeminiAiDescriptionClient client;

    @BeforeEach
    void setUp() {
        server = new FakeGeminiServer();
        client = client(server.baseUrl(), Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void returnsTextFromGeminiResponse() {
        server.respondWithText("  가볍게 쓰기 좋은 무선 마우스입니다.  ");

        String description = client.generate("Wireless Mouse", 25000);

        assertThat(description).isEqualTo("가볍게 쓰기 좋은 무선 마우스입니다.");
    }

    // 모델 경로, 키 헤더, 프롬프트(상품 정보 + 규칙)가 제대로 실려 가는지 확인한다
    @Test
    void sendsModelPathApiKeyHeaderAndPrompt() {
        server.respondWithText("설명");

        client.generate("Wireless Mouse", 25000);

        assertThat(server.lastPath()).isEqualTo("/v1beta/models/test-model:generateContent");
        assertThat(server.lastApiKeyHeader()).isEqualTo(API_KEY);
        assertThat(server.lastRequestBody())
            .contains("Wireless Mouse")
            .contains("25,000원")
            .contains("systemInstruction")
            .contains("지어내지 않는다")
            .doesNotContain(API_KEY);   // 키는 본문이 아니라 헤더로만 보낸다
    }

    @Test
    void joinsMultipleTextParts() {
        server.respond(200, """
            {"candidates":[{"content":{"parts":[{"text":"첫 문장입니다. "},{"text":"둘째 문장입니다."}]}}]}
            """);

        assertThat(client.generate("Keyboard", 50000)).isEqualTo("첫 문장입니다. 둘째 문장입니다.");
    }

    // 텍스트가 비어 있으면 빈 문자열을 돌려주고, Service가 502로 처리한다
    @Test
    void blankTextIsReturnedAsBlank() {
        server.respondWithText("   ");

        assertThat(client.generate("Keyboard", 50000)).isBlank();
    }

    // 401/403 인증 실패, 400 잘못된 요청, 5xx 장애는 502로 이어지는 AiClientException
    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 500, 503})
    void errorStatusBecomesAiClientException(int status) {
        server.respond(status, "{\"error\":{\"code\":" + status + ",\"message\":\"error\"}}");

        assertThatThrownBy(() -> client.generate("Keyboard", 50000))
            .isExactlyInstanceOf(AiClientException.class)
            .hasMessage("Gemini returned HTTP " + status)
            .hasMessageNotContaining(API_KEY);
    }

    // 429(요청 한도 초과)는 다시 시도할 수 있는 상황이라 503으로 이어지는 AiUnavailableException
    @Test
    void tooManyRequestsBecomesAiUnavailableException() {
        server.respond(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}");

        assertThatThrownBy(() -> client.generate("Keyboard", 50000))
            .isExactlyInstanceOf(AiUnavailableException.class);
    }

    @Test
    void slowResponseBecomesAiTimeoutException() {
        GeminiAiDescriptionClient shortTimeoutClient = client(server.baseUrl(), Duration.ofMillis(300));
        server.respondWithText("늦은 응답");
        server.respondAfterDelay(2000);

        assertThatThrownBy(() -> shortTimeoutClient.generate("Keyboard", 50000))
            .isExactlyInstanceOf(AiTimeoutException.class);
    }

    // 서버가 아예 없으면 타임아웃이 아니라 연결 실패 (502)
    @Test
    void connectionFailureBecomesAiClientException() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();   // 빈 포트를 얻은 뒤 바로 닫는다
        }
        GeminiAiDescriptionClient noServerClient =
            client("http://localhost:" + closedPort + "/v1beta", Duration.ofSeconds(5));

        assertThatThrownBy(() -> noServerClient.generate("Keyboard", 50000))
            .isExactlyInstanceOf(AiClientException.class)
            .hasMessage("Gemini connection failed");
    }

    @Test
    void noCandidatesBecomesAiClientException() {
        server.respond(200, "{\"candidates\":[]}");

        assertThatThrownBy(() -> client.generate("Keyboard", 50000))
            .isExactlyInstanceOf(AiClientException.class);
    }

    // 안전 정책 등으로 프롬프트가 차단되면 candidates 없이 promptFeedback만 온다
    @Test
    void blockedPromptBecomesAiClientException() {
        server.respond(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

        assertThatThrownBy(() -> client.generate("Keyboard", 50000))
            .isExactlyInstanceOf(AiClientException.class)
            .hasMessageContaining("SAFETY");
    }

    @Test
    void missingPartsBecomesAiClientException() {
        server.respond(200, "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}");

        assertThatThrownBy(() -> client.generate("Keyboard", 50000))
            .isExactlyInstanceOf(AiClientException.class);
    }

        // 글자가 있어도 끝까지 쓰지 못하고 멈춘 응답은 쓸 수 없는 응답이다 (502)
    @Test
    void truncatedTextBecomesAiClientException() {
        server.respond(200, """
            {"candidates":[{"content":{"parts":[{"text":"가볍게 쓰기 좋은"}]},"finishReason":"MAX_TOKENS"}]}
            """);

        assertThatThrownBy(() -> client.generate("Keyboard", 50000))
            .isExactlyInstanceOf(AiClientException.class)
            .hasMessageContaining("MAX_TOKENS");
    }

    @Test
    void nonJsonResponseBecomesAiClientException() {
        server.respond(200, "<html>not json</html>");

        assertThatThrownBy(() -> client.generate("Keyboard", 50000))
            .isExactlyInstanceOf(AiClientException.class)
            .hasMessage("Gemini response could not be read");
    }

    // 키가 없으면 객체를 만들 때(서버 시작 시) 바로 실패한다
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankApiKeyFailsAtStartup(String apiKey) {
        assertThatThrownBy(() -> new GeminiAiDescriptionClient(
                server.baseUrl(), "test-model", apiKey, Duration.ofSeconds(1), Duration.ofSeconds(1)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("GEMINI_API_KEY is not set");
    }

    @Test
    void userPromptContainsOnlyGivenProductInfo() {
        assertThat(GeminiAiDescriptionClient.userPrompt("Wireless Mouse", 25000))
            .contains("상품 이름: Wireless Mouse")
            .contains("가격: 25,000원");
    }

    private GeminiAiDescriptionClient client(String baseUrl, Duration readTimeout) {
        return new GeminiAiDescriptionClient(baseUrl, "test-model", API_KEY, Duration.ofSeconds(2), readTimeout);
    }
}
