package com.example.product_api;

// AI를 일시적으로 쓸 수 없는 경우 (요청 한도 초과 429 등). Service에서 503으로 바뀐다
public class AiUnavailableException extends AiClientException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
