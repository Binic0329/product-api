package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// ai.client=gemini로 앱 전체를 띄우고, Gemini 주소만 가짜 서버(localhost)로 바꿔
// Controller → Service → GeminiAiDescriptionClient → (가짜) Gemini까지 실제로 연결되는지 검증한다
@SpringBootTest(properties = {
    "ai.client=gemini",
    "ai.gemini.model=test-model",
    "ai.gemini.api-key=test-api-key",
    "ai.gemini.connect-timeout=2s",
    "ai.gemini.read-timeout=5s"
})
class GeminiDescriptionIntegrationTest {

    private static final FakeGeminiServer gemini = new FakeGeminiServer();

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("ai.gemini.base-url", gemini::baseUrl);
    }

    @AfterAll
    static void stopServer() {
        gemini.stop();
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AiDescriptionClient aiDescriptionClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        productRepository.deleteAll();   // 테스트용 H2 DB만 비운다
        gemini.reset();
    }

    @Test
    void geminiClientIsSelected() {
        assertThat(aiDescriptionClient).isInstanceOf(GeminiAiDescriptionClient.class);
    }

    @Test
    void generateDescriptionUsesGeminiAndDoesNotChangeDatabase() throws Exception {
        Long id = productRepository.save(new Product("Wireless Mouse", 25000)).getId();
        gemini.respondWithText("가볍게 쓰기 좋은 무선 마우스입니다.");

        mockMvc.perform(post("/products/" + id + "/generate-description"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(id))
            .andExpect(jsonPath("$.description").value("가볍게 쓰기 좋은 무선 마우스입니다."));

        assertThat(gemini.lastRequestBody()).contains("Wireless Mouse").contains("25,000원");
        // 설명은 응답으로만 돌려주고 DB에는 저장하지 않는다
        Product saved = productRepository.findById(id).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Wireless Mouse");
        assertThat(saved.getPrice()).isEqualTo(25000);
        assertThat(productRepository.count()).isEqualTo(1);
    }

    @Test
    void missingProductReturnsNotFoundWithoutCallingGemini() throws Exception {
        mockMvc.perform(post("/products/999999/generate-description"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Product not found"));

        assertThat(gemini.requestCount()).isZero();
    }

    @Test
    void geminiServerErrorReturnsBadGateway() throws Exception {
        Long id = productRepository.save(new Product("Keyboard", 50000)).getId();
        gemini.respond(500, "{\"error\":{\"code\":500,\"message\":\"internal secret detail\"}}");

        mockMvc.perform(post("/products/" + id + "/generate-description"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.message").value("AI service error"));
    }

    @Test
    void geminiAuthFailureReturnsBadGateway() throws Exception {
        Long id = productRepository.save(new Product("Keyboard", 50000)).getId();
        gemini.respond(401, "{\"error\":{\"code\":401,\"status\":\"UNAUTHENTICATED\"}}");

        mockMvc.perform(post("/products/" + id + "/generate-description"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("AI service error"));
    }

    @Test
    void geminiRateLimitReturnsServiceUnavailable() throws Exception {
        Long id = productRepository.save(new Product("Keyboard", 50000)).getId();
        gemini.respond(429, "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}");

        mockMvc.perform(post("/products/" + id + "/generate-description"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("AI service unavailable"));
    }

    @Test
    void blankGeminiTextReturnsBadGateway() throws Exception {
        Long id = productRepository.save(new Product("Keyboard", 50000)).getId();
        gemini.respondWithText("");

        mockMvc.perform(post("/products/" + id + "/generate-description"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("AI service error"));
    }
}
