package com.example.product_api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ProductControllerTest {

    private ProductService productService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 가짜 Service를 넣어 Controller의 HTTP 처리(검증, 상태 코드)만 검증한다
        productService = mock(ProductService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void getProductsReturnsList() throws Exception {
        when(productService.findAll()).thenReturn(List.of(product(1L, "Keyboard", 50000)));

        mockMvc.perform(get("/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Keyboard"));
    }

    @Test
    void getProductReturnsProduct() throws Exception {
        when(productService.findById(1L)).thenReturn(product(1L, "Keyboard", 50000));

        mockMvc.perform(get("/products/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Keyboard"))
            .andExpect(jsonPath("$.price").value(50000));
    }

    @Test
    void createProductReturnsSavedProductWithGeneratedId() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(product(1L, "Keyboard", 50000));

        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Keyboard\",\"price\":50000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Keyboard"));
    }

    @Test
    void createProductRejectsBodyWithId() throws Exception {
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":5,\"name\":\"Keyboard\",\"price\":50000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("id must not be provided"));

        verifyNoInteractions(productService);
    }

    @Test
    void createProductReturnsBadRequestForInvalidInput() throws Exception {
        String[] invalidBodies = {
            "{\"price\":50000}",                         // name 누락
            "{\"name\":\"\",\"price\":50000}",           // name 빈 문자열
            "{\"name\":\"   \",\"price\":50000}",        // name 공백만
            "{\"name\":\"Keyboard\",\"price\":-1}",      // price < 0
            "{\"name\":\"Keyboard\"}"                    // price 누락
        };

        for (String body : invalidBodies) {
            mockMvc.perform(post("/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        // 잘못된 요청은 Service까지 가지 않으므로 아무것도 저장되지 않는다
        verifyNoInteractions(productService);
    }

    @Test
    void createProductAllowsZeroPrice() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(product(1L, "Free Sticker", 0));

        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Free Sticker\",\"price\":0}"))
            .andExpect(status().isCreated());
    }

    @Test
    void createProductAllowsNameWith255Characters() throws Exception {
        String name = "a".repeat(255);
        when(productService.create(any(ProductRequest.class))).thenReturn(product(1L, name, 1000));

        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"price\":1000}"))
            .andExpect(status().isCreated());
    }

    @Test
    void createProductRejectsNameWith256Characters() throws Exception {
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "a".repeat(256) + "\",\"price\":1000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("name must be at most 255 characters"));

        // 검증에서 막히므로 Service(DB)까지 가지 않는다
        verifyNoInteractions(productService);
    }

    @Test
    void updateProductSucceedsWithValidInput() throws Exception {
        when(productService.update(eq(1L), any(ProductRequest.class)))
            .thenReturn(product(1L, "New Keyboard", 55000));

        mockMvc.perform(put("/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Keyboard\",\"price\":55000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("New Keyboard"))
            .andExpect(jsonPath("$.price").value(55000));
    }

    @Test
    void updateProductReturnsBadRequestForInvalidInput() throws Exception {
        String[] invalidBodies = {
            "{\"name\":\"   \",\"price\":55000}",                // name 공백만
            "{\"price\":55000}",                                 // name 누락
            "{\"name\":\"New Keyboard\",\"price\":-1}",          // price < 0
            "{\"name\":\"New Keyboard\"}",                       // price 누락
            "{\"id\":1,\"name\":\"New Keyboard\",\"price\":55000}", // id 포함
            "{\"name\":\"" + "a".repeat(256) + "\",\"price\":55000}"   // name 256자
        };

        for (String body : invalidBodies) {
            mockMvc.perform(put("/products/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        // 잘못된 요청은 Service까지 가지 않으므로 기존 상품이 바뀌지 않는다
        verifyNoInteractions(productService);
    }

    @Test
    void updateProductReturnsNotFoundWhenProductDoesNotExist() throws Exception {
        when(productService.update(eq(99L), any(ProductRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        mockMvc.perform(put("/products/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ghost\",\"price\":1000}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteProductReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/products/1"))
            .andExpect(status().isNoContent());

        verify(productService).delete(1L);
    }

    @Test
    void deleteProductReturnsNotFoundWhenProductDoesNotExist() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"))
            .when(productService).delete(99L);

        mockMvc.perform(delete("/products/99"))
            .andExpect(status().isNotFound());
    }

    // DB에서 읽어 온 것처럼 id가 채워진 Product를 만든다
    private Product product(Long id, String name, Integer price) {
        Product product = new Product(name, price);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
