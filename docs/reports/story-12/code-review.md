# Story 12 코드 리뷰 보고서

- 대상: Story 12 AI 설명 품질 측정과 프롬프트 개선
- 보고서 작성일: 2026-11-27 / Story 진행 시점: 2026-11-27 (커밋 `18f7f1d`, PR #19 병합 커밋 `e91fe18`)
- 작성 방식: 작성자(AI)의 **자체 검토**입니다. 독립 리뷰가 아닙니다.

## 1. 용어

| 용어 | 뜻 |
|---|---|
| 프롬프트 | AI에게 주는 지시문. 이 프로젝트에서는 `GeminiAiDescriptionClient.SYSTEM_INSTRUCTION`에 있는 규칙 목록 |
| system instruction | Gemini API에서 "이 대화 전체에 적용할 규칙"을 담는 칸. 상품 정보(사용자 메시지)와 따로 보냅니다 |
| 채점기 | AI가 쓴 설명이 규칙을 지켰는지 기계적으로 검사하는 코드. 여기서는 `DescriptionQualityChecker` |
| 응답 기록 | 실제 Gemini가 돌려준 설명을 JSON 파일로 저장해 둔 것 (`src/test/resources/eval/prompt-vN.json`) |
| 샘플 | 측정에 쓰는 고정된 상품 8개. 버전마다 같은 샘플로 비교합니다 |
| 의심(suspect) | 이름·가격에 없는 숫자나 영문 단어. 지어낸 사양일 수 있지만, 실패로 세지 않고 사람이 다시 봅니다 |
| 비결정성 | 같은 입력이어도 AI 응답이 매번 조금씩 달라지는 성질 |

## 2. 작업 목표와 구현 범위

**목표**: AI가 만든 상품 설명이 프롬프트 규칙을 얼마나 지키는지 **숫자로 재고**, 그 결과를 근거로 프롬프트를 고친 뒤 **같은 방법으로 다시 재서 비교**합니다.

> 해설: Story 9에서 "한국어 1~3문장, 이름·가격만 사용, 지어내지 말 것" 같은 규칙을 프롬프트에 넣었지만, AI가 실제로 지키는지는 확인한 적이 없었습니다. 예를 들어 `Logi Pro Headset, 129,000원`의 설명에 줄바꿈이 섞여 나와도 아무도 알 수 없었습니다. 이번 작업으로 "샘플 8개 중 몇 개가 규칙을 지켰는가"를 버전마다 셀 수 있게 됐습니다.

**범위에 포함**
- 채점기 `DescriptionQualityChecker`와 그 테스트 (테스트 코드에만 둠)
- 실제 응답 수집 스크립트 `scripts/collect-descriptions.ps1`
- 실제 Gemini 응답 기록 4개(`prompt-v1.json` ~ `prompt-v4.json`)와 기록 채점 테스트 `DescriptionQualityEvaluationTest`
- 프롬프트 규칙 5줄 추가 (`GeminiAiDescriptionClient.java:36, 42–45`)
- README(품질 측정 절, 테스트 표, 구조, 개발 과정), CLAUDE.md(프롬프트 규칙, 측정 규칙, 확인용 서버 명령 등)

**범위에서 제외**
- 실제 API에서 규칙 위반 응답을 거부하는 것. 상태 코드·오류 메시지·응답 형식은 그대로입니다 (설계 결정 1)
- 상품 이름에 지시문을 넣는 공격(프롬프트 인젝션) 샘플. 채점 규칙 선택에서 제외했습니다
- 버전당 여러 번 기록해 평균을 내는 통계적 측정 (9절 후속 조치)

## 3. 수용 기준별 충족 여부

| 기준 | 결과 | 근거 |
|---|---|---|
| 규칙 준수를 자동으로 채점 | 충족 | `DescriptionQualityChecker.check()`가 위반 목록(`problems`)과 의심 목록(`suspects`)을 돌려줌 |
| 채점기가 정확한지 검증 | 충족 | `DescriptionQualityCheckerTest`: 규칙별 정상·위반·경계값(3/4문장, 이름 뺀 300/301자, 내용 한글 19/20자, 가격 표기 6가지, 존댓말 5가지) |
| 테스트가 외부 서비스를 호출하지 않음 | 충족 | `DescriptionQualityEvaluationTest`는 `src/test/resources/eval/`의 JSON만 읽음 (`DescriptionQualityEvaluationTest.java:25–30`) |
| 실제 Gemini 응답으로 측정 | 충족 | v1~v4 각 8건, 모두 HTTP 200 (사용자 제공: 사용자가 Gemini 모드 서버를 띄우고 스크립트 실행) |
| 프롬프트 개선 효과를 비교 | 충족 | 최종 기준으로 v1 3/8 → v2 0/8 → v3 5/8 → v4 7/8 (8절) |
| API 계약 유지 | 충족 | 운영 코드 변경은 `SYSTEM_INSTRUCTION` 문자열뿐. Controller·Service·예외 처리 변경 없음 |
| API 키가 저장소에 들어가지 않음 | 충족 | 스크립트는 키를 다루지 않음. 기록 JSON에는 이름·가격·상태 코드·설명만 있음. 커밋·브랜치 기록에서 Google 키 형식(`AIza`) 0건, `gitleaks` 검사 `no leaks found` (로컬 실행 결과) |

## 4. 변경 파일과 목적

| 파일 | 변경 | 목적 |
|---|---|---|
| `src/main/java/.../GeminiAiDescriptionClient.java` | 수정 | `SYSTEM_INSTRUCTION`에 규칙 5줄 추가 (존댓말, 줄바꿈 금지, 이름 한 번, 용도 설명, 홍보 문구 금지) |
| `src/test/java/.../DescriptionQualityChecker.java` | 추가 | 채점기. 측정 전용이라 테스트 코드에 둠 |
| `src/test/java/.../DescriptionQualityCheckerTest.java` | 추가 | 채점기 규칙별 단위 테스트 |
| `src/test/java/.../DescriptionQualityEvaluationTest.java` | 추가 | 응답 기록 채점. 이전 버전은 점수만 출력, 현재 버전은 실패 1건 이하 강제 |
| `src/test/resources/eval/prompt-v1.json` ~ `v4.json` | 추가 | 실제 Gemini 응답 기록 (사용자가 수집) |
| `scripts/collect-descriptions.ps1` | 추가 | 샘플 등록 → 설명 생성 → 스크립트가 만든 상품만 삭제 → JSON 저장 |
| `README.md` | 수정 | "AI 설명 품질 측정" 절, 테스트 표·개수, 프로젝트 구조, 개발 과정 표 |
| `CLAUDE.md` | 수정 | 프롬프트 규칙, 품질 측정 규칙, 테스트 표, H2 확인용 서버 명령, Jackson 3 패키지, `!` 명령 안내 |

## 5. 실제 실행과 데이터 흐름

**기록 (사용자가 실행)**
```
[창 1] Gemini 모드 서버: AI_CLIENT=gemini, GEMINI_API_KEY 설정 후 mvnw spring-boot:run
[창 2] collect-descriptions.ps1 -Output "src/test/resources/eval/prompt-v4.json"
   샘플 8개 각각:
     POST /products {"name":"텀블러","price":15000}          → 201, id
     POST /products/{id}/generate-description               → 200 {"productId":..,"description":".."}
        서버 내부: ProductDescriptionService → GeminiAiDescriptionClient → Gemini API
     DELETE /products/{id}                                  → 204 (스크립트가 만든 상품만)
   → [{"name","price","status","description"}, ...] 를 JSON 파일로 저장
```

**채점 (테스트, 외부 호출 없음)**
```
DescriptionQualityEvaluationTest
  load("prompt-v4.json")  → List<Recorded> 8건
  score()  → 각 건마다 status가 200이면 DescriptionQualityChecker.check(name, price, description)
           → PASS/FAIL, problems, suspects를 출력하고 통과 개수 반환
  현재 버전: 통과 개수 >= 7 이어야 테스트 성공
```

> 해설: 기록과 채점을 나눈 이유는 두 가지입니다. ① 이 프로젝트의 규칙상 테스트는 외부 서비스를 호출할 수 없고, API 키는 사용자만 다룹니다. ② 같은 기록을 몇 번이든 다시 채점할 수 있어서, 채점 규칙을 바꿨을 때 v1~v4를 모두 새 규칙으로 다시 비교할 수 있습니다. 실제로 이번 작업에서 채점 규칙을 세 번 추가했고, 그때마다 과거 기록을 다시 채점했습니다.

## 6. 설계 결정

개발자가 아래 선택지 중 추천안을 골랐습니다(작업 중 발견한 문제에 대한 결정 포함).

| # | 결정 | 고른 것 | 대안 | 선택 이유 |
|---|---|---|---|---|
| 1 | 검사 적용 범위 | 측정에만 사용 | 실행 중에도 위반 응답을 502로 거부 | API 계약을 바꾸지 않음. 단서 기반 검사가 틀리면 정상 설명까지 거부할 위험 |
| 2 | 실제 응답 수집 | 응답 기록 + 자동 채점 | 별도 평가 실행기 / 수동 점검표 | 외부 호출 없이 재현 가능. 버전 간 비교가 쉬움 |
| 3 | 채점 규칙 | 형식, 가격 정확성, 지어낸 사양 의심 | 지시문 무시(프롬프트 인젝션)도 포함 | 개발자 선택. 인젝션 샘플은 이번 범위에서 제외 |
| 4 | 수집 방법 | PowerShell 스크립트 | `api.http` 수동 복사 | 8건 × 여러 버전을 같은 조건으로 반복. 직접 만든 상품만 삭제 |
| 5 | 통과 기준 | 현재 버전만 기준 강제 | 기준 없이 기록만 | 개선 효과를 테스트로 보장 |
| 6 | v2가 빈약해진 문제 | v3 진행 + "내용 없음" 검사 추가 | v2 그대로 두고 한계만 기록 | 점수는 좋아졌지만 설명이 쓸모없어진 것을 측정하지 못한 채점기의 빈틈을 메움 |
| 7 | 255자 이름의 길이 초과 | 이름을 뺀 본문으로 길이 측정 | 기준 유지 / 프롬프트로 대응 | 이름 길이는 AI가 정하지 않음 |
| 8 | 존댓말 통일 | 말투 검사 + 프롬프트 v4 | (개발자 요청) | v3에서 `~이다` 3건 |
| 9 | v4 1건이 내용 기준에 1자 모자람 | 기준 20자 유지, 현재 버전은 실패 1건까지 허용 | 기준을 17자로 낮춤 / v5 | 결과를 보고 기준을 낮추지 않음. 비결정성 때문에 기록 한 번의 1건 차이는 우연일 수 있음 |

관련 ADR은 없습니다. `docs/adr/0001`(AI 초안과 저장 분리)의 "AI 출력은 사람이 검토한다"는 원칙과 결정 1(측정만, 거부하지 않음)이 같은 방향입니다.

## 7. 자체 리뷰 발견 사항

검토 범위: `DescriptionQualityChecker.java`, `DescriptionQualityCheckerTest.java`, `DescriptionQualityEvaluationTest.java`, `collect-descriptions.ps1`, `GeminiAiDescriptionClient.java` 변경분, README·CLAUDE.md 변경분.

**확정한 결함 없음.** 지금 기록(v1~v4)의 채점 결과에는 영향이 없지만, 다른 문장에서는 오판할 수 있는 지점이 있습니다.

| 심각도 | 위치 | 문제 | 영향 | 제안 |
|---|---|---|---|---|
| 낮음 | `DescriptionQualityChecker.java:20` | 가격 패턴이 숫자와 `원` 사이 공백을 허용해서, `12 원하는`처럼 숫자 뒤에 "원"으로 시작하는 단어가 오면 가격으로 읽습니다 | 가격 불일치로 잘못 실패 처리될 수 있음 (현재 기록에는 해당 문장 없음) | `원` 뒤에 한글이 이어지는 경우(`원하`, `원래` 등)를 제외하거나, 숫자와 `원` 사이 공백을 허용하지 않기 |
| 낮음 | `DescriptionQualityChecker.java:23` | 따옴표 검사에 `'`가 포함되어 영어 소유격(`Kid's`)도 형식 위반이 됩니다. 상품 이름 안의 `'`는 이름을 빼고 검사하므로 괜찮습니다 | 설명에 영어 표현이 섞이면 잘못 실패할 수 있음 | 실제로 문제가 되면 `'`를 검사에서 빼기 |
| 낮음 | `DescriptionQualityChecker.java:52–57` | 문장이 `입니다)`나 `입니다~`처럼 끝나면 존댓말인데도 실패로 셉니다 | 드문 형태라 영향 작음 | 끝의 괄호·물결을 지운 뒤 검사 |
| 낮음 | `DescriptionQualityChecker.java:17` | 내용 기준 20자는 v2 빈 설명(7~16자)과 정상 설명 사이에서 고른 값이라 근거가 약합니다 | v4 텀블러처럼 짧지만 쓸모 있는 설명(19자)이 실패할 수 있음 | 샘플과 기록이 늘어나면 기준을 다시 검토 (결과에 맞춰 바로 낮추지는 않음) |
| 정보 | `DescriptionQualityEvaluationTest.java:61–69` | 버전마다 기록이 한 번뿐이라 1~2건 차이는 우연일 수 있습니다 | v3 5/8과 v4 7/8의 차이 중 일부는 비결정성일 수 있음 | 버전당 여러 번 기록해 평균·범위를 보는 방식 (후속) |
| 정보 | `scripts/collect-descriptions.ps1:56–58` | 삭제 요청 중 서버가 꺼지면 그 샘플 상품이 DB에 남습니다 | 실행 중 서버를 끄지 않으면 생기지 않음 | 남으면 `api.http`로 해당 id만 삭제 |
| 정보 | `scripts/collect-descriptions.ps1` | 로컬 MySQL로 실행하면 샘플 8개가 잠깐 생겼다 지워지면서 id 번호가 8개씩 소모됩니다 | 기존 데이터에는 영향 없음 | Docker 스택이나 H2 확인용 서버로 실행하면 피할 수 있음 |

## 8. 검증 결과

| 출처 | 내용 |
|---|---|
| 사용자 제공 (실제 Gemini, 로컬 MySQL) | 프롬프트 v1~v4 각각 서버를 다시 시작하고 스크립트 실행. 4번 모두 8건 HTTP 200 |
| 로컬 실행 결과 (AI, 기록 채점) | 최종 채점 기준으로 v1 3/8, v2 0/8, v3 5/8, v4 7/8 (아래 표) |
| 로컬 실행 결과 (AI, 스크립트 동작) | Docker Desktop이 꺼져 있어 H2 메모리 DB + Stub 모드 서버(포트 18080)로 스크립트를 실행. 8건 200, 한글 정상, 종료 후 상품 목록 `[]`. 실제 MySQL은 건드리지 않음. 확인 후 서버 종료 |
| 로컬 실행 결과 (AI, 키 유출 검사) | 커밋·push한 브랜치 기록에서 `AIza` 0건, `gitleaks` `no leaks found`. `GEMINI_API_KEY`는 설정 파일의 `${...}` 참조 2곳뿐 |
| 테스트 | 각 테스트의 역할은 CLAUDE.md "테스트" 표의 `DescriptionQualityCheckerTest`, `DescriptionQualityEvaluationTest` 행 참고 |
| 미검증 | 버전당 반복 기록(비결정성 크기), 프롬프트 인젝션 샘플, Docker 스택에서의 스크립트 실행 |

**버전별 결과 (최종 채점 기준으로 다시 채점)**

| 버전 | 추가한 프롬프트 규칙 | 통과 | 실패 내용 |
|---|---|---|---|
| v1 | (Story 9의 최초 프롬프트) | 3/8 | 이름 반복 5건, 줄바꿈 1건 |
| v2 | 줄바꿈 금지, 이름 한 번, 홍보 문구 금지 | 0/8 | 8건 모두 내용 없음 (이름·가격을 뺀 한글 7~16자) |
| v3 | 이름에서 알 수 있는 용도 설명 | 5/8 | 반말(`~이다`) 3건 |
| v4 | 존댓말로 끝내기 | 7/8 | 텀블러 내용 19자 (기준 20자) |

**작업 중 채점 기준을 바꾼 기록**
- v2 측정 후: "내용 없음" 검사 추가. 이 검사가 없을 때 v2는 8/8이었습니다. 처음에 "v2는 8/8" 테스트를 넣었다가 v3, v4 순서로 기준 대상을 옮겼습니다.
- v3 측정 후: 길이를 "전체 300자"에서 "이름을 뺀 본문 300자"로 변경 (255자 이름 샘플이 이름 한 번만으로 323자).
- v3 측정 후(개발자 요청): 존댓말 검사 추가.
- 모두 이번 Story에서 새로 만든 테스트만 바꿨습니다. 기존 테스트의 기대값은 바꾸지 않았습니다.

## 9. 놓치기 쉬운 부분과 후속 조치

- **점수와 품질은 다릅니다.** v2는 금지 규칙을 늘리자 규칙 위반이 줄었지만 설명은 Stub 문구(`OO은(는) N원에 판매되는 상품입니다.`)와 비슷해졌습니다. 채점기는 정의한 규칙만 봅니다. 사람이 기록을 직접 읽는 단계를 빼면 안 됩니다.
- **프롬프트를 바꾸면** 서버를 다시 시작한 뒤 새 기록(`prompt-v5.json`)을 받고, `DescriptionQualityEvaluationTest`의 "현재 프롬프트" 테스트를 새 파일로 옮깁니다 (CLAUDE.md).
- **Claude Code 입력란의 `!` 명령은 Git Bash로 실행**됩니다. PowerShell 스크립트는 별도 PowerShell 창이나 `! pwsh -File ...`로 실행합니다.
- **키를 터미널에 직접 입력하면 PowerShell 명령 기록 파일에 남을 수 있습니다.** 이번에 기록 파일에서 2줄을 확인해 지웠습니다(사용자 제공). 앞으로는 `Read-Host -MaskInput`으로 입력하면 기록에 남지 않습니다.
- 후속 후보: 가격 패턴 오판 수정, 버전당 반복 기록, 프롬프트 인젝션 샘플 추가. 모두 필요할 때 별도 작업으로 진행합니다.
