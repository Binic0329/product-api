package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StubAiDescriptionClientTest {

    private final StubAiDescriptionClient client = new StubAiDescriptionClient();

    @Test
    void generatesDescriptionWithNameAndFormattedPrice() {
        assertThat(client.generate("Keyboard", 50000))
            .isEqualTo("Keyboard은(는) 50,000원에 판매되는 상품입니다.");
    }

    @Test
    void zeroPriceIsFormattedAsZero() {
        assertThat(client.generate("Free Sticker", 0))
            .isEqualTo("Free Sticker은(는) 0원에 판매되는 상품입니다.");
    }

    @Test
    void largePriceHasThousandSeparators() {
        assertThat(client.generate("Laptop", Integer.MAX_VALUE))
            .isEqualTo("Laptop은(는) 2,147,483,647원에 판매되는 상품입니다.");
    }

    // 상품명 최대 길이(255자)도 잘리지 않고 그대로 들어간다
    @Test
    void maxLengthNameIsKeptAsIs() {
        String name = "a".repeat(255);

        assertThat(client.generate(name, 1000)).startsWith(name + "은(는) ");
    }
}
