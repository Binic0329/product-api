package com.example.product_api;

// 모든 API 오류 응답의 공통 JSON 형식: {"status":400,"message":"..."}
public record ApiErrorResponse(int status, String message) {
}
