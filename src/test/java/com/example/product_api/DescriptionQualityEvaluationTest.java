package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// 실제 Gemini 응답 기록(scripts/collect-descriptions.ps1로 수집)을 채점한다 (Story 12)
// 테스트는 기록 파일만 읽고 외부 서비스를 호출하지 않는다
class DescriptionQualityEvaluationTest {

    static final int SAMPLE_COUNT = 8;

    record Recorded(String name, int price, int status, String description) {
    }

    static List<Recorded> load(String file) throws IOException {
        try (InputStream in = DescriptionQualityEvaluationTest.class.getResourceAsStream("/eval/" + file)) {
            assertThat(in).as("기록 파일 " + file).isNotNull();
            return JsonMapper.builder().build().readValue(in, new TypeReference<List<Recorded>>() { });
        }
    }

    // 샘플별 결과를 출력하고 통과 개수를 돌려준다. 생성 실패(200 아님)는 규칙 위반으로 센다
    static long score(String file, List<Recorded> records) {
        long passed = 0;
        for (Recorded r : records) {
            DescriptionQualityChecker.Result result = r.status() == 200
                ? DescriptionQualityChecker.check(r.name(), r.price(), r.description())
                : new DescriptionQualityChecker.Result(List.of("status=" + r.status()), List.of());
            if (result.passed()) {
                passed++;
            }
            String shortName = r.name().length() > 20 ? r.name().substring(0, 20) + "…" : r.name();
            System.out.printf("[%s] %s %s problems=%s suspects=%s%n",
                file, result.passed() ? "PASS" : "FAIL", shortName, result.problems(), result.suspects());
        }
        System.out.printf("[%s] 통과 %d/%d%n", file, passed, records.size());
        return passed;
    }

    // v1(최초), v2(반복 줄이기 → 설명이 빈약해짐), v3(용도 설명 → 반말 섞임)는 점수만 기록한다
    // 기록이 샘플 전체를 담고 있는지만 확인
    @ParameterizedTest
    @ValueSource(strings = {"prompt-v1.json", "prompt-v2.json", "prompt-v3.json"})
    void earlierPromptsAreScoredWithoutThreshold(String file) throws IOException {
        List<Recorded> records = load(file);

        assertThat(records).hasSize(SAMPLE_COUNT);
        score(file, records);
    }

    @Test
    void currentPromptV4FailsAtMostOneSample() throws IOException {
        List<Recorded> records = load("prompt-v4.json");

        // 현재 프롬프트(v4)는 실패 1건까지 허용한다. 같은 프롬프트도 응답이 매번 달라서 기록 한 번의 1건 차이는 우연일 수 있다
        // (v4 기록: 텀블러가 내용 기준 20자에 1자 모자라 7/8). 지어낸 사양 의심은 실패로 보지 않는다
        assertThat(records).hasSize(SAMPLE_COUNT);
        assertThat(score("prompt-v4.json", records)).isGreaterThanOrEqualTo(SAMPLE_COUNT - 1);
    }
}
