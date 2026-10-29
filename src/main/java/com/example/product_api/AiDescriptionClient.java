package com.example.product_api;

// AI 모델 호출부를 인터페이스로 분리해, Service는 어떤 AI(Stub, Bedrock, Gemini 등)를 쓰는지 모르게 한다
// 구현체는 각 AI SDK의 예외를 AiClientException / AiTimeoutException으로 바꿔서 던져야 한다
public interface AiDescriptionClient {

    String generate(String name, Integer price);
}
