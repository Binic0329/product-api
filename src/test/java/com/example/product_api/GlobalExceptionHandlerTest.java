package com.example.product_api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private ProductService productService;
    private MockMvc mockMvc;

    // 500 처리를 확인하기 위한 테스트 전용 컨트롤러
    @RestController
    static class FailingController {
        @GetMapping("/test/fail")
        public String fail() {
            throw new IllegalStateException("secret internal detail");
        }
    }

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService), new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void validationErrorReturnsFieldMessage() throws Exception {
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \",\"price\":50000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("name must not be blank"));
    }

    @Test
    void multipleValidationErrorsAreJoined() throws Exception {
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":5,\"name\":\"\",\"price\":-1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                "id must not be provided, "
                    + "name must not be blank, "
                    + "price must be greater than or equal to 0"));
    }

    @Test
    void malformedJsonReturnsBadRequestWithoutInternalDetails() throws Exception {
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Malformed JSON request or missing required field"))
            .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void invalidPathIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/products/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void notFoundReturnsJson() throws Exception {
        when(productService.findById(999L))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        mockMvc.perform(get("/products/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void unexpectedErrorHidesInternalDetails() throws Exception {
        mockMvc.perform(get("/test/fail"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("Internal server error"))
            .andExpect(content().string(not(containsString("secret internal detail"))))
            .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
