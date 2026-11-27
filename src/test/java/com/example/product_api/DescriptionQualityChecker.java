package com.example.product_api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// AI가 만든 상품 설명이 프롬프트 규칙(GeminiAiDescriptionClient.SYSTEM_INSTRUCTION)을 지켰는지 채점한다 (Story 12)
// 품질 측정 전용이라 테스트 코드에만 둔다. 실제 API 응답은 이 검사로 거부하지 않는다
final class DescriptionQualityChecker {

    static final int MAX_LENGTH = 300;
    static final int MAX_SENTENCES = 3;
    static final int MIN_CONTENT_HANGUL = 20;

    // "25,000원", "25000 원", "2만 5천원", "5천원" 같은 가격 표현
    private static final Pattern PRICE = Pattern.compile("(\\d[\\d,]*)\\s*(만|천)?\\s*(?:(\\d+)\\s*천)?\\s*원");
    // 문장 끝: 마침표·물음표·느낌표 뒤에 공백이나 글 끝 ("1.5kg"의 점은 문장 끝이 아님)
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?。](?=\\s|$)");
    private static final Pattern MARKDOWN = Pattern.compile("[*#`\"“”‘’'「」]|(?m)^\\s*(?:[-•]|\\d+\\.)\\s");
    private static final Pattern DIGITS = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z][A-Za-z0-9-]*");

    private DescriptionQualityChecker() {
    }

    // problems: 규칙 위반(실패), suspects: 지어낸 사양 의심(사람이 다시 볼 목록, 실패 아님)
    record Result(List<String> problems, List<String> suspects) {
        boolean passed() {
            return problems.isEmpty();
        }
    }

    static Result check(String name, int price, String description) {
        List<String> problems = new ArrayList<>();
        if (description == null || description.isBlank()) {
            problems.add("empty");
            return new Result(problems, List.of());
        }
        String text = description.strip();
        // 상품 이름 안의 숫자·영문·기호는 규칙 위반이 아니므로 이름을 지운 본문으로 검사한다
        String body = text.replace(name.strip(), " ");

        int sentences = countSentences(text);
        if (sentences < 1 || sentences > MAX_SENTENCES) {
            problems.add("sentences=" + sentences);
        }
        // 존댓말 통일: 모든 문장이 "~니다" 또는 "~요"로 끝나야 한다 (프롬프트 v4 규칙)
        for (String sentence : SENTENCE_END.split(text)) {
            String s = sentence.strip();
            if (!s.isEmpty() && !s.endsWith("니다") && !s.endsWith("요")) {
                problems.add("not-polite");
                break;
            }
        }
        // 이름 길이(최대 255자)는 AI가 정하지 않으므로 이름을 뺀 길이로 잰다
        int length = text.replace(name.strip(), "").length();
        if (length > MAX_LENGTH) {
            problems.add("length=" + length);
        }
        if (!mostlyKorean(body)) {
            problems.add("not-korean");
        }
        if (MARKDOWN.matcher(body).find() || text.contains("\n")) {
            problems.add("format");
        }
        // 이름을 두 번 이상 쓰면 같은 말을 반복하는 설명이 된다 (프롬프트 v2 규칙)
        int nameCount = text.split(Pattern.quote(name.strip()), -1).length - 1;
        if (nameCount > 1) {
            problems.add("name-repeated=" + nameCount);
        }

        Set<String> suspects = new LinkedHashSet<>();
        Matcher priceMatcher = PRICE.matcher(body);
        StringBuilder withoutPrices = new StringBuilder();
        while (priceMatcher.find()) {
            long mentioned = parsePrice(priceMatcher);
            if (mentioned != price) {
                problems.add("price=" + priceMatcher.group().strip());
            }
            priceMatcher.appendReplacement(withoutPrices, " ");
        }
        priceMatcher.appendTail(withoutPrices);

        // 이름과 가격을 빼면 남는 말이 거의 없는 설명("OO는 N원에 판매합니다")은 쓸모가 없다 (프롬프트 v3 규칙)
        long contentHangul = countHangul(withoutPrices.toString());
        if (contentHangul < MIN_CONTENT_HANGUL) {
            problems.add("too-thin=" + contentHangul);
        }

        // 이름·가격에 없는 숫자(용량, 크기 등)와 영문 단어(브랜드 등)는 지어냈을 가능성이 있다
        Matcher digits = DIGITS.matcher(withoutPrices);
        while (digits.find()) {
            suspects.add(digits.group());
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        Matcher latin = LATIN_WORD.matcher(withoutPrices);
        while (latin.find()) {
            if (!lowerName.contains(latin.group().toLowerCase(Locale.ROOT))) {
                suspects.add(latin.group());
            }
        }
        return new Result(problems, List.copyOf(suspects));
    }

    static int countSentences(String text) {
        String[] parts = SENTENCE_END.split(text.strip());
        int count = 0;
        for (String part : parts) {
            if (!part.isBlank()) {
                count++;
            }
        }
        return count;
    }

    // 글자(공백·숫자·기호 제외) 중 한글이 절반 이상이면 한국어로 본다. 글자가 하나도 없으면 한국어 아님
    static boolean mostlyKorean(String text) {
        long letters = text.codePoints().filter(Character::isLetter).count();
        return letters > 0 && countHangul(text) * 2 >= letters;
    }

    static long countHangul(String text) {
        return text.codePoints()
            .filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL)
            .count();
    }

    private static long parsePrice(Matcher m) {
        long value = Long.parseLong(m.group(1).replace(",", ""));
        if ("만".equals(m.group(2))) {
            value *= 10_000;
            if (m.group(3) != null) {
                value += Long.parseLong(m.group(3)) * 1_000;
            }
        } else if ("천".equals(m.group(2))) {
            value *= 1_000;
        }
        return value;
    }
}
