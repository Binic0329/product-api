# Story 11 코드 리뷰 보고서

- 대상: Story 11 Dockerfile + Docker Compose (앱과 MySQL을 컨테이너로 함께 실행)
- 보고서 작성일: 2026-11-21 / Story 진행 시점: 2026-11-21 (커밋 `b5eb7b5`, PR #16 병합 커밋 `3d00878`)
- 작성 방식: 작성자(AI)의 **자체 검토**입니다. 독립 리뷰가 아닙니다.

## 1. 용어

| 용어 | 뜻 |
|---|---|
| 컨테이너 | 앱과 그 실행 환경(OS 층, JRE 등)을 묶어 PC와 분리된 공간에서 돌리는 실행 단위 |
| 이미지 | 컨테이너를 만드는 틀. `Dockerfile`로 만듭니다. 이미지 하나로 컨테이너를 여러 번 만들 수 있습니다 |
| Dockerfile | 이미지를 만드는 순서를 적은 파일 (어떤 이미지에서 시작해 무엇을 복사하고 실행할지) |
| 멀티 스테이지 빌드 | Dockerfile 안에서 "빌드용 이미지"와 "실행용 이미지"를 나누고, 실행용에는 결과물(jar)만 옮기는 방식 |
| Docker Compose | 여러 컨테이너(여기서는 앱 + MySQL)를 `compose.yaml` 한 파일로 함께 띄우는 도구 |
| 서비스 | `compose.yaml`에서 컨테이너 하나의 설정 단위. 여기서는 `db`, `app` |
| 볼륨(Volume) | 컨테이너를 지워도 남는 저장 공간. MySQL 데이터 폴더를 여기에 둡니다 |
| healthcheck | 컨테이너가 "실제로 일할 준비가 됐는지" 주기적으로 확인하는 명령 |
| `.env` | Compose가 자동으로 읽는 변수 파일. 실제 계정 값을 두고 Git에는 올리지 않습니다 |
| non-root 사용자 | 관리자(root) 권한이 없는 일반 사용자. 컨테이너 안 앱을 이 사용자로 실행합니다 |
| 프로필(Profile) | 실행 환경별로 설정 파일을 골라 쓰는 Spring 기능. 이번에 `local` 프로필을 제거했습니다 |

## 2. 작업 목표와 구현 범위

**목표**: Java와 MySQL을 직접 설치하지 않아도, `.env`에 계정을 적고 `docker compose up --build -d` 한 줄로 앱과 MySQL을 함께 띄웁니다. 실제 데이터가 든 로컬 MySQL은 건드리지 않습니다.

> 해설: 지금까지는 이 프로젝트를 실행하려면 "Java 21 설치 → MySQL 8.4 설치 → DB와 계정 만들기 → 환경 변수 설정 → `mvnw spring-boot:run`"을 거쳐야 했습니다(README 실행 방법 1~3절). 이번 작업 후에는 Docker만 있으면 `.env` 세 줄을 채우고 명령 한 줄로 같은 결과를 얻습니다. 새 DB에서 시작하므로 `Wide Monitor, 250,000원`을 등록하면 id 1번이 됩니다.

**범위에 포함**
- `Dockerfile`, `compose.yaml`, `.env.example`, `.dockerignore` 추가
- `.gitignore`에 `.env` 추가
- 기본 설정과 내용이 같던 `application-local.properties`와 `pom.xml`의 `local` 프로필 설정 제거 (Story 10 이후 Story 11로 미뤄 둔 결정)
- README(Docker 실행 방법·주의점, 기술 스택, 프로젝트 구조, 로드맵), CLAUDE.md(Docker 규칙, 명령어, 기술 스택), `api.http`(서버 실행 안내 주석) 갱신

**범위에서 제외**
- 배포(서버, 도메인, HTTPS). 2026-11-21에 배포를 하지 않기로 결정했습니다(README 로드맵).
- Java 코드와 `application.properties` 변경. 환경 변수로 DB 주소만 바꿔 끼웁니다.
- CI에서 `docker build` 확인 (설계 결정 7 참고)

## 3. 수용 기준별 충족 여부

| 기준 | 결과 | 근거 |
|---|---|---|
| 명령 한 줄로 앱 + MySQL 실행 | 충족 | `docker compose up --build -d` 후 `Started ProductApiApplication` 로그 확인 (사용자 제공) |
| 새 DB에 테이블 자동 생성 | 충족 | 앱 로그 `Successfully applied 2 migrations to schema product_api, now at version v2` (로컬 실행 결과) |
| 컨테이너 DB에서 CRUD·설명 기능 동작 | 충족 | `api.http`로 등록 201, 수정 200, 삭제 204, 설명 생성 200, 설명 저장 200 (사용자 제공) |
| 컨테이너를 지웠다 다시 만들어도 데이터 유지 | 충족 | `docker compose down` → `up -d` 후 `GET /products`에 id 1, 3 유지, 삭제한 id 2 없음 (사용자 제공) |
| 로컬 MySQL과 충돌·영향 없음 | 충족 | MySQL 포트를 호스트에 열지 않음(`compose.yaml:13`). 로컬 `product_api.products`에 새 행 없음 (사용자 제공) |
| 비밀 값이 저장소에 들어가지 않음 | 충족 | `.gitignore`의 `.env` 규칙, 커밋 직전 `git status`에 `.env` 없음. `.env.example`은 값이 비어 있음 |
| 계정 값이 없으면 시작 전에 실패 | 충족 | `DB_PASSWORD`를 지우고 `docker compose config` → `required variable DB_PASSWORD is missing a value` (로컬 실행 결과) |
| 앱이 root가 아닌 사용자로 실행 | 충족 | `docker compose exec app whoami` → `app` (로컬 실행 결과) |
| 기존 동작 유지 | 충족 | Java 소스와 테스트 코드 변경 없음. `spring-boot:run` 경로는 `application.properties`만 읽도록 바뀌었지만, 제거한 파일의 내용(`ddl-auto=validate`)이 기본 설정과 같아 동작 차이 없음 |

## 4. 변경 파일과 목적

| 파일 | 변경 | 목적 |
|---|---|---|
| `Dockerfile` | 추가 | JDK 이미지에서 jar 빌드 → JRE 이미지에서 non-root로 실행 |
| `compose.yaml` | 추가 | `db`(MySQL 8.4 + 볼륨 + healthcheck)와 `app` 정의, 시작 순서 지정 |
| `.env.example` | 추가 | `.env` 양식. 변수 이름만 있고 값은 비어 있음 |
| `.dockerignore` | 추가 | `target/`, `.git/`, `docs/`, `.env` 등을 이미지 빌드 재료에서 제외 |
| `.gitignore` | 수정 | `.env` 추가 |
| `src/main/resources/application-local.properties` | 삭제 | 내용이 `ddl-auto=validate` 한 줄로 기본 설정과 같았음 |
| `pom.xml` | 수정 | `spring-boot-maven-plugin`의 `local` 프로필 설정 제거 |
| `README.md` | 수정 | 실행 방법 6절(Docker), 기술 스택, 프로젝트 구조, 로드맵 Story 11 |
| `CLAUDE.md` | 수정 | 프로필 문구 정리, Docker 규칙, 명령어, 기술 스택 |
| `api.http` | 수정 | 3번째 줄 서버 실행 안내에서 `local 프로필` 문구 제거, Docker 실행 추가 |

## 5. 실제 실행과 데이터 흐름

```
docker compose up --build -d
  │
  ├─ (1) app 이미지 빌드 (Dockerfile)
  │     build 단계: eclipse-temurin:21-jdk + ./mvnw package -DskipTests → target/*.jar
  │     실행 단계: eclipse-temurin:21-jre + app.jar, 사용자 app
  │
  ├─ (2) db 컨테이너 시작 (mysql:8.4)
  │     볼륨 db-data가 비어 있으면: product_api DB와 .env의 계정을 새로 만듦
  │     healthcheck: mysqladmin ping -h 127.0.0.1 → 5초마다, 최대 20회
  │
  ├─ (3) db가 healthy가 되면 app 컨테이너 시작 (depends_on: service_healthy)
  │     SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/product_api → Flyway V1, V2 적용 → 서버 시작
  │
  └─ (4) 호스트 localhost:8080 → app 컨테이너 8080
        api.http 요청 → Controller → Service → Repository → db 컨테이너 MySQL → 응답 JSON
```

> 해설: `db:3306`의 `db`는 `compose.yaml`의 서비스 이름입니다. Compose가 만든 내부 네트워크에서는 서비스 이름이 그대로 주소가 됩니다. 앱 컨테이너 안에서 `localhost`는 "앱 컨테이너 자기 자신"이라, 기존 설정(`localhost:3306`)으로는 MySQL을 찾지 못합니다. 그래서 환경 변수로 주소만 바꿨습니다.

설정 값이 정해지는 순서:
1. `application.properties`의 `spring.datasource.url=jdbc:mysql://localhost:3306/product_api`
2. Compose가 넘긴 환경 변수 `SPRING_DATASOURCE_URL` → Spring Boot의 완화된 바인딩(relaxed binding) 규칙으로 `spring.datasource.url`에 대응하며, 환경 변수가 설정 파일보다 우선합니다.

## 6. 설계 결정

개발자가 아래 선택지 중 추천안(A)을 모두 골랐습니다.

| # | 결정 | 고른 것 | 대안 | 선택 이유 |
|---|---|---|---|---|
| 1 | Dockerfile 방식 | 멀티 스테이지 | PC에서 만든 jar 복사 | PC에 빌드 도구가 없어도 되고, 오래된 jar가 들어가는 실수가 없음. 실행 이미지에 JDK·소스가 남지 않음 |
| 2 | 이미지 빌드 중 테스트 | 건너뜀(`-DskipTests`) | 빌드 때도 실행 | 테스트는 CI와 `mvnw test`가 담당. 빌드마다 시간이 더 듦 |
| 3 | MySQL 포트 | 호스트에 열지 않음 | 3307로 열기 | 로컬 MySQL(3306)과 충돌하지 않고, 컨테이너 DB에 밖에서 접속할 경로를 만들지 않음 |
| 4 | 계정 전달 | `.env` + `.env.example` | 터미널 환경 변수만 사용 | 매번 설정하지 않아도 됨. `.env`는 Git 제외, 양식만 커밋 |
| 5 | 시작 순서 | healthcheck + `service_healthy` | `depends_on`만(시작 순서만 보장) | MySQL이 접속을 받기 전에 앱이 떠서 연결에 실패하는 문제를 막음 |
| 6 | `local` 프로필 | 제거 | 유지 | 기본 설정과 내용이 같아 의미 없는 설정 층이었음 |
| 7 | CI `docker build` | 추가 안 함 | 추가 | 범위를 작게 유지. 필요하면 별도 `chore` 작업 |

관련 ADR은 없습니다(`docs/adr`에는 0001 "AI 초안과 저장 분리"만 있습니다).

## 7. 자체 리뷰 발견 사항

검토 범위: `Dockerfile`, `compose.yaml`, `.env.example`, `.dockerignore`, `.gitignore`, `pom.xml` 변경분, README·CLAUDE.md·`api.http` 변경분.

**확정한 결함 없음.** 아래는 결함은 아니지만 알아 둘 개선 후보입니다.

| 심각도 | 위치 | 문제 | 영향 | 제안 |
|---|---|---|---|---|
| 낮음 | `Dockerfile:4–7` | 의존성 다운로드 층을 따로 두지 않았습니다. `src`가 바뀌면 `./mvnw package`가 Maven 라이브러리를 처음부터 다시 받습니다 | 코드를 고칠 때마다 이미지 빌드가 느림 (동작에는 영향 없음) | 빌드가 자주 느려지면 `pom.xml`만 먼저 복사해 `dependency:go-offline`을 실행하는 층 추가 |
| 낮음 | `Dockerfile:2,10`, `compose.yaml:5` | `eclipse-temurin:21`, `mysql:8.4`는 세부 버전을 고정하지 않은 태그입니다 | 시간이 지나면 다른 패치 버전으로 빌드될 수 있음 | 재현성이 중요해지면 `mysql:8.4.x`처럼 고정 |
| 낮음 | `compose.yaml:8–10, 28–29` | Compose는 `.env`보다 **터미널 환경 변수를 우선**합니다. 이 PC에는 `spring-boot:run`용 `DB_USERNAME`·`DB_PASSWORD`가 설정되어 있습니다 | 새 터미널에서 그냥 실행하면 `.env` 값이 무시됨 (README 6절에 안내) | 실행 전 `Remove-Item Env:DB_USERNAME, Env:DB_PASSWORD` (그 창에서만) |
| 낮음 | `compose.yaml:11–12` | MySQL 계정은 볼륨이 비어 있을 때 한 번만 만들어집니다 | 나중에 `.env` 비밀번호를 바꾸면 앱이 접속하지 못함. 검증 중 실제로 재현됨(8절) | README 6절에 안내. 바꿔야 하면 `down -v`(컨테이너 DB 데이터 삭제) 후 재시작 |
| 정보 | `compose.yaml:16` | healthcheck가 계정 없이 `mysqladmin ping`을 실행합니다 | `mysqladmin ping`은 접속이 거부돼도 서버가 살아 있으면 성공으로 끝나므로 의도대로 동작. 비밀번호를 명령에 넣지 않아도 됨 | 유지 |
| 정보 | `compose.yaml:31` | `GEMINI_API_KEY`가 환경 변수로 컨테이너에 전달됩니다 | `docker inspect`로 볼 수 있음. 로컬 PC 전용이라 현재는 문제 아님 | 서버에 올리게 되면 Docker secrets 등 검토 |

## 8. 검증 결과

| 출처 | 내용 |
|---|---|
| 로컬 실행 결과 (AI, Windows PowerShell, Docker Desktop) | `docker build` 성공. 별도 프로젝트 이름 `-p product-api-verify`, 포트 18080, 그 명령 안에서만 쓴 임시 계정으로 `compose up` → Flyway V1·V2 적용, POST 201, 설명 생성 200(Stub 문구 `도커 확인용은(는) 1,000원에 판매되는 상품입니다.`), `down` 후 `up`에서 데이터 유지, `whoami` → `app`, `DB_PASSWORD` 누락 시 실패. 확인용 컨테이너·볼륨·이미지는 삭제 |
| 로컬 실행 중 실패 1건 (AI) | 두 번째 `up`에서 비밀번호를 다른 값으로 넘겨 앱이 DB 접속에 실패. 설계 결함이 아니라 테스트 절차 실수였고, "볼륨은 처음 계정을 유지한다"는 주의점으로 README에 반영 |
| 사용자 제공 (실제 `.env`, 사용자의 Compose 프로젝트) | `api.http`: 전체 조회 200(빈 목록) → [정상 1] 201 `id 1 Monitor` → [정상 3] 200 `Wide Monitor 250000` → [삭제 1] 201 `id 2` → [삭제 2] 204 → [설명 1] 200 `Wide Monitor은(는) 250,000원에 판매되는 상품입니다.` → [설명 3] 201 `id 3` → [저장 1] 200 설명 저장. `down` → `up -d` 후 id 1, 3 유지. 로컬 MySQL 최대 id 8, 새 행 없음 |
| 테스트 | Java 소스·테스트 변경 없음. 테스트가 무엇을 확인하는지는 CLAUDE.md "테스트" 표 참고 |
| 미검증 | `AI_CLIENT=gemini`로 컨테이너에서 실제 Gemini 호출, `api.http`의 400·404 요청(Docker와 무관하게 Controller에서 처리), CI에서의 이미지 빌드 |

## 9. 놓치기 쉬운 부분과 후속 조치

- **`spring-boot:run`과 Compose는 둘 다 8080**을 씁니다. 동시에 쓰려면 `.env`에 `APP_PORT=18080`.
- **`docker compose down -v`는 컨테이너 DB 데이터를 지웁니다.** 평소에는 `down`만 씁니다.
- 컨테이너 DB와 로컬 MySQL은 **다른 DB**입니다. 로컬 데이터가 컨테이너로 옮겨지지 않습니다.
- `docs/reports/story-05-06/`의 `application-local.properties` 설명은 당시 상태 기록이라 그대로 두었습니다. 지금은 이 파일이 없습니다.
- 후속 후보: 의존성 캐시 층, 이미지 태그 고정, CI `docker build` 확인(모두 필요할 때 별도 작업).
