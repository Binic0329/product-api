package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

// 상품 등록 → AI 초안 생성(저장 안 함) → 사람이 검토한 설명 저장 → 조회까지 전체 흐름을 H2로 검증한다 (Story 10)
@SpringBootTest
class DescriptionReviewFlowIntegrationTest {

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
    void generatedDraftIsSavedOnlyAfterReview() throws Exception {
        // ① 상품 등록: 설명은 비어 있다
        Long id = createProduct("Keyboard", 50000);
        assertThat(productRepository.findById(id).orElseThrow().getDescription()).isNull();

        // ② AI 초안 생성 (테스트는 Stub 모드): 응답만 오고 DB에는 저장되지 않는다
        mockMvc.perform(post("/products/" + id + "/generate-description"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Keyboard은(는) 50,000원에 판매되는 상품입니다."));
        assertThat(productRepository.findById(id).orElseThrow().getDescription()).isNull();

        // ③④ 사람이 초안을 고쳐서 저장
        mockMvc.perform(put("/products/" + id + "/description")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"타건감이 좋은 키보드입니다.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.description").value("타건감이 좋은 키보드입니다."));

        Product saved = productRepository.findById(id).orElseThrow();
        assertThat(saved.getDescription()).isEqualTo("타건감이 좋은 키보드입니다.");
        assertThat(saved.getName()).isEqualTo("Keyboard");
        assertThat(saved.getPrice()).isEqualTo(50000);

        // ⑤ 조회하면 저장한 설명이 함께 나온다
        mockMvc.perform(get("/products/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("타건감이 좋은 키보드입니다."));
    }

    // 이름·가격 수정(PUT /products/{id})은 저장된 설명을 지우지 않는다
    @Test
    void updatingProductKeepsSavedDescription() throws Exception {
        Long id = createProduct("Keyboard", 50000);
        saveDescription(id, "타건감이 좋은 키보드입니다.");

        mockMvc.perform(put("/products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Keyboard\",\"price\":55000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("타건감이 좋은 키보드입니다."));

        Product updated = productRepository.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Keyboard");
        assertThat(updated.getDescription()).isEqualTo("타건감이 좋은 키보드입니다.");
    }

    @Test
    void savedDescriptionCanBeReplaced() throws Exception {
        Long id = createProduct("Keyboard", 50000);
        saveDescription(id, "첫 번째 설명입니다.");
        saveDescription(id, "다시 검토한 설명입니다.");

        assertThat(productRepository.findById(id).orElseThrow().getDescription()).isEqualTo("다시 검토한 설명입니다.");
    }

    @Test
    void descriptionWith1000CharactersIsSaved() throws Exception {
        Long id = createProduct("Keyboard", 50000);
        saveDescription(id, "가".repeat(1000));

        assertThat(productRepository.findById(id).orElseThrow().getDescription()).hasSize(1000);
    }

    @Test
    void tooLongDescriptionIsRejectedBeforeDatabase() throws Exception {
        Long id = createProduct("Keyboard", 50000);
        saveDescription(id, "기존 설명입니다.");

        mockMvc.perform(put("/products/" + id + "/description")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"" + "가".repeat(1001) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("description must be at most 1000 characters"));

        // 거절된 요청은 기존 설명을 바꾸지 않는다
        assertThat(productRepository.findById(id).orElseThrow().getDescription()).isEqualTo("기존 설명입니다.");
    }

    @Test
    void savingDescriptionForMissingProductReturnsNotFound() throws Exception {
        mockMvc.perform(put("/products/999999/description")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"설명\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Product not found"));

        assertThat(productRepository.count()).isZero();
    }

    private Long createProduct(String name, int price) throws Exception {
        String response = mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"price\":" + price + "}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.description").value(nullValue()))   // 등록 시 설명은 비어 있음
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void saveDescription(Long id, String description) throws Exception {
        mockMvc.perform(put("/products/" + id + "/description")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"" + description + "\"}"))
            .andExpect(status().isOk());
    }
}
