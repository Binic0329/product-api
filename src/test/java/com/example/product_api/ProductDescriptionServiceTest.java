package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ProductDescriptionServiceTest {

    private ProductService productService;
    private AiDescriptionClient aiDescriptionClient;
    private ProductDescriptionService productDescriptionService;

    @BeforeEach
    void setUp() {
        // 가짜 ProductService와 가짜 AI를 넣어, 실제 DB나 AI 호출 없이 Service 규칙만 검증한다
        productService = mock(ProductService.class);
        aiDescriptionClient = mock(AiDescriptionClient.class);
        productDescriptionService = new ProductDescriptionService(productService, aiDescriptionClient);
    }

    @Test
    void generateReturnsDescriptionFromAi() {
        when(productService.findById(1L)).thenReturn(product(1L, "Keyboard", 50000));
        when(aiDescriptionClient.generate("Keyboard", 50000)).thenReturn("좋은 키보드입니다.");

        ProductDescriptionResponse response = productDescriptionService.generate(1L);

        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.description()).isEqualTo("좋은 키보드입니다.");
        verify(aiDescriptionClient).generate("Keyboard", 50000);
    }

    // 없는 상품이면 AI를 호출하지 않는다 (불필요한 비용 방지)
    @Test
    void generateThrowsNotFoundWithoutCallingAi() {
        when(productService.findById(99L))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        assertThatThrownBy(() -> productDescriptionService.generate(99L))
            .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND, "Product not found"));
        verifyNoInteractions(aiDescriptionClient);
    }

    @Test
    void aiFailureBecomesBadGateway() {
        when(productService.findById(1L)).thenReturn(product(1L, "Keyboard", 50000));
        when(aiDescriptionClient.generate("Keyboard", 50000))
            .thenThrow(new AiClientException("model returned 500"));

        assertThatThrownBy(() -> productDescriptionService.generate(1L))
            .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_GATEWAY, "AI service error"));
    }

    @Test
    void aiTimeoutBecomesGatewayTimeout() {
        when(productService.findById(1L)).thenReturn(product(1L, "Keyboard", 50000));
        when(aiDescriptionClient.generate("Keyboard", 50000))
            .thenThrow(new AiTimeoutException("no response in 10s"));

        assertThatThrownBy(() -> productDescriptionService.generate(1L))
            .satisfies(ex -> assertStatus(ex, HttpStatus.GATEWAY_TIMEOUT, "AI service timeout"));
    }

    // null, 빈 문자열, 공백만 있는 응답은 쓸 수 없는 응답이므로 502
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void blankAiResponseBecomesBadGateway(String description) {
        when(productService.findById(1L)).thenReturn(product(1L, "Keyboard", 50000));
        when(aiDescriptionClient.generate("Keyboard", 50000)).thenReturn(description);

        assertThatThrownBy(() -> productDescriptionService.generate(1L))
            .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_GATEWAY, "AI service error"));
    }

    // AI 예외가 아닌 오류(우리 코드의 버그 등)는 502로 감추지 않고 그대로 올려 500으로 처리되게 한다
    @Test
    void unexpectedErrorIsNotConvertedToAiError() {
        when(productService.findById(1L)).thenReturn(product(1L, "Keyboard", 50000));
        when(aiDescriptionClient.generate("Keyboard", 50000))
            .thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> productDescriptionService.generate(1L))
            .isInstanceOf(IllegalStateException.class);
    }

    // DB에서 읽어 온 것처럼 id가 채워진 Product를 만든다
    private Product product(Long id, String name, Integer price) {
        Product product = new Product(name, price);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private void assertStatus(Throwable ex, HttpStatus expected, String reason) {
        assertThat(ex).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(expected);
        assertThat(((ResponseStatusException) ex).getReason()).isEqualTo(reason);
    }
}
