package com.example.product_api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// PUT /products/{id}/description 요청 본문 전용 클래스
public class ProductDescriptionRequest {

    // DB 컬럼이 varchar(1000)이라서, 더 긴 설명은 DB에 가기 전에 400으로 거절한다
    @NotBlank(message = "description must not be blank")
    @Size(max = 1000, message = "description must be at most 1000 characters")
    private String description;

    public ProductDescriptionRequest() {

    }

    public ProductDescriptionRequest(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
