package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 앱 전체를 띄워 Controller → Service → Stub AI, 그리고 DB(H2) 조회까지 실제로 연결되는지 검증한다
@SpringBootTest
class ProductDescriptionIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        productRepository.deleteAll();   // 테스트용 H2 DB만 비운다
    }

    @Test
    void generateDescriptionUsesStubAndDoesNotChangeDatabase() throws Exception {
        Long id = productRepository.save(new Product("Keyboard", 50000)).getId();

        mockMvc.perform(post("/products/" + id + "/generate-description"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(id))
            .andExpect(jsonPath("$.description").value("Keyboard은(는) 50,000원에 판매되는 상품입니다."));

        // 설명은 응답으로만 돌려주고 DB에는 저장하지 않으므로, 상품 정보와 개수가 그대로여야 한다
        assertThat(productRepository.count()).isEqualTo(1);
        Product saved = productRepository.findById(id).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Keyboard");
        assertThat(saved.getPrice()).isEqualTo(50000);
    }

    @Test
    void missingProductReturnsNotFound() throws Exception {
        mockMvc.perform(post("/products/999999/generate-description"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Product not found"));

        assertThat(productRepository.count()).isZero();
    }
}
