package com.example.product_api;

import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 실제 AI를 호출하지 않고 고정 문구를 돌려주는 구현체 (Story 8)
// ai.client가 없거나 stub일 때 등록된다. gemini이면 GeminiAiDescriptionClient가 대신 등록된다
@Component
@ConditionalOnProperty(name = "ai.client", havingValue = "stub", matchIfMissing = true)
public class StubAiDescriptionClient implements AiDescriptionClient {

    // 서버의 기본 언어 설정과 관계없이 항상 "50,000"처럼 쉼표가 찍히도록 Locale.KOREA를 명시한다
    @Override
    public String generate(String name, Integer price) {
        return String.format(Locale.KOREA, "%s은(는) %,d원에 판매되는 상품입니다.", name, price);
    }
}
