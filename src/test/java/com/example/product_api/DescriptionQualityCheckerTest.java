package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// 채점기가 규칙 위반을 정확히 잡는지 확인한다 (Story 12)
class DescriptionQualityCheckerTest {

    private static DescriptionQualityChecker.Result check(String name, int price, String description) {
        return DescriptionQualityChecker.check(name, price, description);
    }

    @Test
    void goodDescriptionPasses() {
        var result = check("Wireless Mouse", 25000,
            "Wireless Mouse는 선 없이 깔끔하게 쓸 수 있는 마우스입니다. 25,000원에 만나 보세요.");

        assertThat(result.passed()).isTrue();
        assertThat(result.suspects()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankDescriptionFails(String description) {
        assertThat(check("텀블러", 15000, description).problems()).containsExactly("empty");
    }

    @Test
    void threeSentencesPassFourSentencesFail() {
        String three = "가볍고 튼튼합니다. 물을 오래 담아 둘 수 있습니다. 매일 들고 다니기 좋습니다.";

        assertThat(check("텀블러", 15000, three).passed()).isTrue();
        assertThat(check("텀블러", 15000, three + " 좋습니다.").problems()).containsExactly("sentences=4");
    }

    @Test
    void sentenceWithoutFinalPeriodCountsAsOne() {
        assertThat(DescriptionQualityChecker.countSentences("가볍고 튼튼한 텀블러")).isEqualTo(1);
    }

    @Test
    void decimalPointIsNotSentenceEnd() {
        assertThat(DescriptionQualityChecker.countSentences("무게는 1.5kg입니다.")).isEqualTo(1);
    }

    @Test
    void length300PassesAnd301Fails() {
        String ok = "가".repeat(296) + "입니다.";
        String tooLong = "가".repeat(297) + "입니다.";

        assertThat(check("텀블러", 15000, ok).passed()).isTrue();
        assertThat(check("텀블러", 15000, tooLong).problems()).containsExactly("length=301");
    }

    @Test
    void lengthExcludesProductName() {
        // 255자 이름 + 본문 300자 = 555자여도 본문 기준으로 통과, 본문 301자면 실패
        String longName = "무선마우스".repeat(51);

        assertThat(check(longName, 45000, longName + "가".repeat(296) + "입니다.").passed()).isTrue();
        assertThat(check(longName, 45000, longName + "가".repeat(297) + "입니다.").problems())
            .containsExactly("length=301");
    }

    @Test
    void contentWith20HangulPassesAnd19Fails() {
        // 이름("텀블러")과 가격("15,000원")을 뺀 나머지 한글 수로 판단한다
        assertThat(check("텀블러", 15000, "텀블러 15,000원 " + "가".repeat(17) + "입니다.").passed()).isTrue();
        assertThat(check("텀블러", 15000, "텀블러 15,000원 " + "가".repeat(16) + "입니다.").problems())
            .containsExactly("too-thin=19");
    }

    @Test
    void nameAndPriceOnlyDescriptionIsTooThin() {
        // 프롬프트 v2에서 실제로 나온 형태
        assertThat(check("기계식 키보드", 89000, "기계식 키보드는 89,000원에 구매할 수 있습니다.").problems())
            .containsExactly("too-thin=10");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "음료를 담아 마실 때 쓰는 용품입니다. 가격은 15,000원입니다.",
        "음료를 담아 마실 때 쓰는 용품이에요. 가볍게 들고 다니기 좋아요."
    })
    void politeEndingsPass(String description) {
        assertThat(check("텀블러", 15000, description).problems()).doesNotContain("not-polite");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "음료를 담아 마실 때 사용하는 용품이며, 가격은 15,000원이다.",
        "음료를 담아 마실 때 쓰는 용품입니다. 가격은 15,000원이다.",
        "음료를 담아 마실 때 쓰는 용품"
    })
    void plainOrMixedEndingsFail(String description) {
        assertThat(check("텀블러", 15000, description).problems()).contains("not-polite");
    }

    @Test
    void englishDescriptionFails() {
        assertThat(check("텀블러", 15000, "A simple tumbler for everyday use.").problems())
            .contains("not-korean");
    }

    @Test
    void englishProductNameDoesNotCountAgainstKorean() {
        // 이름을 빼고 보면 본문은 한글뿐이다
        var result = check("Logi Pro Wireless Gaming Headset", 129000,
            "Logi Pro Wireless Gaming Headset은 게임할 때 소리를 듣고 대화하기 좋은 헤드셋입니다.");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void halfHangulIsKorean() {
        assertThat(DescriptionQualityChecker.mostlyKorean("가나ab")).isTrue();
        assertThat(DescriptionQualityChecker.mostlyKorean("가abc")).isFalse();
        assertThat(DescriptionQualityChecker.mostlyKorean("123 !!")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "**가벼운** 텀블러입니다.",
        "# 텀블러\n가벼운 텀블러입니다.",
        "\"가벼운 텀블러\"입니다.",
        "- 가벼운 텀블러입니다.",
        "가벼운 텀블러입니다.\n들고 다니기 좋습니다."
    })
    void markdownQuotesOrLineBreaksFail(String description) {
        assertThat(check("텀블러", 15000, description).problems()).contains("format");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "25000 | 25,000원에 판매합니다.",
        "25000 | 25000 원에 판매합니다.",
        "25000 | 2만 5천원에 판매합니다.",
        "20000 | 2만원에 판매합니다.",
        "5000  | 5천원에 판매합니다.",
        "0     | 0원에 판매합니다."
    })
    void correctPriceHasNoPriceProblem(int price, String description) {
        assertThat(check("텀블러", price, description).problems()).noneMatch(p -> p.startsWith("price"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "25000 | 30,000원에 판매합니다. | price=30,000원",
        "25000 | 2만원에 판매합니다.    | price=2만원",
        "0     | 1,000원에 판매합니다.  | price=1,000원"
    })
    void wrongPriceFails(int price, String description, String problem) {
        assertThat(check("텀블러", price, description).problems()).contains(problem);
    }

    @Test
    void noPriceMentionPasses() {
        assertThat(check("텀블러", 15000, "가볍게 들고 다니며 물이나 커피를 담아 마시기 좋은 텀블러입니다.").passed())
            .isTrue();
    }

    @Test
    void nameOnceOrNotAtAllIsFineTwiceFails() {
        assertThat(check("텀블러", 15000, "가벼운 텀블러입니다.").problems()).noneMatch(p -> p.startsWith("name"));
        assertThat(check("텀블러", 15000, "가벼운 컵입니다.").problems()).noneMatch(p -> p.startsWith("name"));
        assertThat(check("텀블러", 15000, "텀블러를 소개합니다. 가벼운 텀블러입니다.").problems())
            .contains("name-repeated=2");
    }

    @Test
    void numbersInNameAreNotSuspects() {
        var result = check("USB-C 케이블 2m", 9900,
            "USB-C 케이블 2m는 9,900원이며 노트북이나 휴대폰을 충전하고 연결할 때 쓰기 좋은 케이블입니다.");

        assertThat(result.passed()).isTrue();
        assertThat(result.suspects()).isEmpty();
    }

    @Test
    void inventedNumbersAndEnglishWordsAreSuspectsButNotFailures() {
        var result = check("텀블러", 15000, "500ml 용량의 Stanley 텀블러로 12시간 동안 따뜻한 음료를 오래 보온해 줍니다.");

        assertThat(result.passed()).isTrue();
        assertThat(result.suspects()).containsExactly("500", "12", "ml", "Stanley");
    }
}
