package com.example.product_api;

// 상품 설명 생성 API 응답: {"productId":1,"description":"..."} (DB에는 저장하지 않음)
public record ProductDescriptionResponse(Long productId, String description) {
}
