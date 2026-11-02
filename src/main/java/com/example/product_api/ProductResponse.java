package com.example.product_api;

// 상품 응답 전용 클래스. Entity를 그대로 응답하지 않고, JSON 필드와 순서(id, name, price, description)를 여기서 정한다
public record ProductResponse(Long id, String name, Integer price, String description) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getDescription()
        );
    }
}
