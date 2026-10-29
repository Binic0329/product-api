package com.example.product_api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ProductDescriptionControllerTest {

    private ProductDescriptionService productDescriptionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 가짜 Service를 넣어 Controller의 HTTP 처리(상태 코드, 응답 형식)만 검증한다
        productDescriptionService = mock(ProductDescriptionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductDescriptionController(productDescriptionService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void generateDescriptionReturnsOk() throws Exception {
        when(productDescriptionService.generate(1L))
            .thenReturn(new ProductDescriptionResponse(1L, "Keyboard은(는) 50,000원에 판매되는 상품입니다."));

        mockMvc.perform(post("/products/1/generate-description"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(1))
            .andExpect(jsonPath("$.description").value("Keyboard은(는) 50,000원에 판매되는 상품입니다."));
    }

    @Test
    void missingProductReturnsNotFound() throws Exception {
        when(productDescriptionService.generate(99L))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        mockMvc.perform(post("/products/99/generate-description"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void aiErrorReturnsBadGateway() throws Exception {
        when(productDescriptionService.generate(1L))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service error"));

        mockMvc.perform(post("/products/1/generate-description"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.message").value("AI service error"));
    }

    @Test
    void aiTimeoutReturnsGatewayTimeout() throws Exception {
        when(productDescriptionService.generate(1L))
            .thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI service timeout"));

        mockMvc.perform(post("/products/1/generate-description"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.status").value(504))
            .andExpect(jsonPath("$.message").value("AI service timeout"));
    }

    // 실제 Service를 넣어, AI 예외의 내부 메시지가 응답까지 새어 나가지 않는지 확인한다
    @Test
    void aiErrorDoesNotExposeInternalDetails() throws Exception {
        ProductService productService = mock(ProductService.class);
        AiDescriptionClient aiDescriptionClient = mock(AiDescriptionClient.class);
        Product product = new Product("Keyboard", 50000);
        ReflectionTestUtils.setField(product, "id", 1L);
        when(productService.findById(1L)).thenReturn(product);
        when(aiDescriptionClient.generate("Keyboard", 50000))
            .thenThrow(new AiClientException("secret internal detail"));
        MockMvc realServiceMockMvc = MockMvcBuilders
            .standaloneSetup(new ProductDescriptionController(
                new ProductDescriptionService(productService, aiDescriptionClient)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        realServiceMockMvc.perform(post("/products/1/generate-description"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("AI service error"))
            .andExpect(content().string(not(containsString("secret"))))
            .andExpect(content().string(not(containsString("AiClientException"))));
    }

    @Test
    void unexpectedErrorReturnsInternalServerError() throws Exception {
        when(productDescriptionService.generate(1L)).thenThrow(new IllegalStateException("bug"));

        mockMvc.perform(post("/products/1/generate-description"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void invalidPathIdReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(post("/products/abc/generate-description"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(productDescriptionService);
    }

    @Test
    void getMethodIsNotAllowed() throws Exception {
        mockMvc.perform(get("/products/1/generate-description"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405));
        verifyNoInteractions(productDescriptionService);
    }
}
