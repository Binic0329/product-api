package com.example.product_api;

// AI 호출 실패 (오류 응답, 연결 실패 등). Service에서 502로 바뀐다
public class AiClientException extends RuntimeException {

    public AiClientException(String message) {
        super(message);
    }

    public AiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
