package com.example.product_api;

// AI가 제때 응답하지 않은 경우. Service에서 504로 바뀐다
public class AiTimeoutException extends AiClientException {

    public AiTimeoutException(String message) {
        super(message);
    }

    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
