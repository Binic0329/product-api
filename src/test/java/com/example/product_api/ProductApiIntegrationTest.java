package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

// 앱 전체를 띄워 Controller → Service → Repository → DB(H2)까지 실제로 동작하는지 검증한다
@SpringBootTest
class ProductApiIntegrationTest {

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
    void crudFlowWorksWithDatabase() throws Exception {
        Long id = createProduct("Keyboard", 50000);
        assertThat(productRepository.findById(id)).isPresent();

        mockMvc.perform(get("/products/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Keyboard"));

        mockMvc.perform(put("/products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Keyboard\",\"price\":55000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id));

        // 수정 내용이 실제로 DB에 반영됐는지 확인
        Product updated = productRepository.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Keyboard");
        assertThat(updated.getPrice()).isEqualTo(55000);

        mockMvc.perform(delete("/products/" + id))
            .andExpect(status().isNoContent());
        assertThat(productRepository.count()).isZero();

        mockMvc.perform(get("/products/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void databaseGeneratesDifferentIds() throws Exception {
        Long first = createProduct("Keyboard", 50000);
        Long second = createProduct("Mouse", 30000);

        assertThat(first).isNotNull();
        assertThat(second).isNotEqualTo(first);
        assertThat(productRepository.count()).isEqualTo(2);
    }

    @Test
    void invalidRequestDoesNotSaveAnything() throws Exception {
        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":5,\"name\":\"Keyboard\",\"price\":50000}"))
            .andExpect(status().isBadRequest());

        assertThat(productRepository.count()).isZero();
    }

    @Test
    void tooLongNameIsRejectedBeforeDatabase() throws Exception {
        String name = "a".repeat(300);   // DB 컬럼은 varchar(255)

        mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"price\":1000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("name must be at most 255 characters"));

        assertThat(productRepository.count()).isZero();
    }

    @Test
    void nameWith255CharactersIsSaved() throws Exception {
        Long id = createProduct("a".repeat(255), 1000);

        assertThat(productRepository.findById(id).orElseThrow().getName()).hasSize(255);
    }

    private Long createProduct(String name, int price) throws Exception {
        String response = mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"price\":" + price + "}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}
