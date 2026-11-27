# Story 11 학습 보고서

- 대상: Story 11 Dockerfile + Docker Compose
- 보고서 작성일: 2026-11-21 / Story 진행 시점: 2026-11-21
- 인용 코드: 모두 현재 저장소의 원문입니다(줄 번호는 현재 파일 기준). `pom.xml`의 제거된 설정만 커밋 `b5eb7b5`의 변경 전 내용을 인용했습니다.

## 1. 용어 정리

| 용어 | 풀이 |
|---|---|
| 이미지 | 컨테이너를 찍어 내는 틀. "Java 21 JRE + 우리 jar"가 들어 있는 읽기 전용 묶음 |
| 컨테이너 | 이미지로 실행한 프로세스 하나. 지워도 이미지는 남아서 언제든 다시 만들 수 있습니다 |
| 베이스 이미지 | `FROM`으로 고르는 시작 이미지. 여기서는 `eclipse-temurin`(Java 배포판)과 `mysql` |
| 멀티 스테이지 빌드 | 빌드용 이미지와 실행용 이미지를 나누고, 실행용에는 결과물만 복사하는 방식 |
| Compose | 여러 컨테이너를 `compose.yaml` 하나로 함께 띄우는 도구. `docker compose` 명령 |
| 서비스 | Compose에서 컨테이너 하나의 설정. 이름(`db`, `app`)이 내부 네트워크 주소가 됩니다 |
| 볼륨 | 컨테이너를 지워도 남는 저장 공간 |
| healthcheck | "준비 완료"를 판단하는 명령. 실행 중(running)과 준비 완료(healthy)는 다릅니다 |
| 변수 치환(interpolation) | `compose.yaml`의 `${...}`를 `.env`나 터미널 환경 변수 값으로 바꾸는 Compose 기능 |
| 완화된 바인딩(relaxed binding) | 환경 변수 `SPRING_DATASOURCE_URL`을 설정 키 `spring.datasource.url`로 알아듣는 Spring Boot 기능 |

## 2. 무엇을 하는 작업인가

`.env`에 컨테이너 DB 계정 세 줄을 적고 `docker compose up --build -d`를 실행하면, MySQL 8.4 컨테이너와 상품 API 컨테이너가 함께 뜹니다. `api.http`로 `Monitor, 200,000원`을 등록하면 새 DB라서 id 1번이 되고, 컨테이너를 지웠다 다시 만들어도 이 상품은 남습니다. PC에 원래 있던 로컬 MySQL(최대 id 8번까지 데이터가 있는 DB)은 전혀 바뀌지 않습니다.

> 해설: 이미지는 "밀키트", 컨테이너는 "밀키트로 만든 요리"에 가깝습니다. 밀키트에는 재료(JRE, jar)와 조리법(`ENTRYPOINT`)이 다 들어 있어서, 어느 주방(PC)에서 만들어도 같은 맛이 납니다. 요리를 버려도(`docker compose down`) 밀키트(이미지)와 냉장고에 넣어 둔 재료(볼륨의 DB 데이터)는 남아 있습니다.

Story 11에서는 **Java 코드를 한 줄도 바꾸지 않았습니다.** 바뀐 것은 "앱을 어디서, 어떤 환경으로 띄우느냐"뿐입니다. 그래서 상품 API의 규칙(검증, 오류 형식, 상태 코드)은 그대로이고, 확인할 것은 "컨테이너 안에서 앱과 MySQL이 제대로 연결되는가"입니다.

## 3. 요청이 지나가는 길

```
[PC] VS Code api.http
  POST http://localhost:8080/products  {"name":"Monitor","price":200000}
        │  호스트 8080 → 컨테이너 8080 (compose.yaml:33 ports)
        ▼
[app 컨테이너] java -jar app.jar (사용자 app)
  ProductController → ProductService → ProductRepository
        │  jdbc:mysql://db:3306/product_api (compose.yaml:27)
        ▼
[db 컨테이너] MySQL 8.4  ── 데이터 폴더 /var/lib/mysql ── 볼륨 db-data
        │  INSERT → id 1
        ▼
응답 201 {"id":1,"name":"Monitor","price":200000,"description":null}
```

로컬 MySQL(호스트의 3306)은 이 길 어디에도 없습니다. `db` 컨테이너의 3306은 Compose 내부 네트워크에만 열려 있습니다.

## 4. 핵심 코드 읽기

### 4-1. Dockerfile: 빌드 단계

`Dockerfile:1–7` (원문)
```dockerfile
# 1단계: jar 빌드 (테스트는 CI와 로컬 mvnw test가 담당하므로 건너뜀)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN chmod +x mvnw && ./mvnw -B package -DskipTests
```

- 전체 역할: `docker compose up --build`(또는 `docker build`)가 호출합니다. 입력은 저장소의 `.mvn`, `mvnw`, `pom.xml`, `src`이고, 출력은 이 단계 안의 `/app/target/*.jar`입니다. 이 단계의 이미지는 최종 결과에 들어가지 않습니다.

| 줄 | 역할 | 빠지거나 바뀌면 | 문법 |
|---|---|---|---|
| `FROM eclipse-temurin:21-jdk AS build` | JDK 21이 든 이미지에서 시작하고, 이 단계에 `build`라는 이름을 붙임 | JDK가 없으면 컴파일 불가. 이름이 없으면 2단계에서 `--from=build`로 가리킬 수 없음 | Dockerfile `FROM 이미지:태그 AS 이름`. 태그 `21-jdk`는 이미지 제작자가 정한 이름 |
| `WORKDIR /app` | 이후 명령의 작업 폴더를 `/app`으로 지정(없으면 만듦) | 루트(`/`)에 파일이 흩어짐 | Dockerfile 명령. 셸의 `cd`와 비슷하지만 이후 모든 줄에 유지 |
| `COPY .mvn/ .mvn/` 등 | 빌드에 필요한 파일만 이미지 안으로 복사 | `src`가 없으면 빌드할 코드가 없음 | `COPY 원본(빌드 컨텍스트 기준) 대상(WORKDIR 기준)`. 복사 대상은 `.dockerignore`로 한 번 더 걸러짐 |
| `RUN chmod +x mvnw && ./mvnw -B package -DskipTests` | Maven Wrapper로 jar를 만듦 | `chmod`가 없으면 Windows에서 복사한 `mvnw`에 실행 권한이 없을 수 있음 | `RUN`은 이미지를 만드는 중에 셸 명령 실행. `&&`는 앞이 성공해야 뒤를 실행. `-B`는 Maven 배치(비대화형) 모드, `-DskipTests`는 Maven 속성으로 테스트 생략 |

> 해설: `mvnw`가 Linux 컨테이너에서 돌 수 있는 이유는 `.gitattributes`의 `/mvnw text eol=lf` 덕분입니다. Windows 줄바꿈(CRLF)이 섞이면 Linux 셸이 `\r`을 명령의 일부로 읽어 `not found` 오류가 납니다.

### 4-2. Dockerfile: 실행 단계

`Dockerfile:9–16` (원문)
```dockerfile
# 2단계: 실행용 이미지 (JRE + jar만 포함, root가 아닌 사용자로 실행)
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

| 줄 | 역할 | 빠지거나 바뀌면 | 문법 |
|---|---|---|---|
| `FROM eclipse-temurin:21-jre` | 새 단계 시작. 실행만 하면 되므로 JDK가 아닌 JRE | JDK를 쓰면 이미지가 커지고 컴파일러가 남음 | 두 번째 `FROM`부터 새 단계. 앞 단계의 파일은 자동으로 넘어오지 않음 |
| `RUN useradd --system --uid 1001 app` | 관리자 권한 없는 사용자 `app` 생성 | 없으면 앱이 root로 실행됨 | Linux 명령 `useradd`. `--system`은 로그인용이 아닌 서비스 계정 |
| `COPY --from=build /app/target/*.jar app.jar` | 1단계 결과 jar만 가져옴 | 이 줄이 없으면 실행할 jar가 없음 | `--from=단계이름`으로 다른 단계의 파일 복사. `*`는 파일 이름 패턴 |
| `USER app` | 이후 명령과 컨테이너 실행을 `app` 사용자로 | 빠지면 root로 실행. 앱에 취약점이 있을 때 피해 범위가 커짐 | Dockerfile 명령 |
| `EXPOSE 8080` | "이 컨테이너는 8080을 쓴다"는 문서 표시 | 빠져도 동작은 같음. 실제 연결은 `compose.yaml`의 `ports` | Dockerfile 명령(메타데이터) |
| `ENTRYPOINT ["java", "-jar", "app.jar"]` | 컨테이너가 시작될 때 실행할 명령 | 없으면 컨테이너가 할 일 없이 끝남 | JSON 배열 형식(exec form). 셸을 거치지 않고 `java`가 직접 1번 프로세스가 되어 종료 신호를 바로 받음 |

### 4-3. compose.yaml: db 서비스

`compose.yaml:4–19` (원문)
```yaml
  db:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: product_api
      MYSQL_USER: ${DB_USERNAME:?DB_USERNAME is not set}
      MYSQL_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is not set}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is not set}
    volumes:
      - db-data:/var/lib/mysql
    # 포트를 호스트에 열지 않는다 (로컬 MySQL 3306과 충돌 방지, 앱만 내부망으로 접속)
    healthcheck:
      # 127.0.0.1(TCP)로 확인: 초기화 중 임시 서버(소켓 전용)를 준비 완료로 착각하지 않기 위함
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "--silent"]
      interval: 5s
      timeout: 3s
      retries: 20
```

- 전체 역할: 공식 `mysql:8.4` 이미지로 DB 컨테이너를 만듭니다. 입력은 `.env`(또는 터미널 환경 변수)의 계정 값, 출력은 `db:3306`에서 접속을 받는 MySQL입니다.

먼저 세 종류의 문법을 구분합니다.
- **YAML 문법**: 들여쓰기로 계층을 표현합니다. `키: 값`, 목록은 `- 항목`. 탭이 아닌 공백만 씁니다.
- **Compose 기능**: `image`, `environment`, `volumes`, `healthcheck`, `${...}` 변수 치환
- **MySQL 이미지 기능**: `MYSQL_DATABASE`, `MYSQL_USER` 같은 변수 이름은 Compose가 아니라 `mysql` 이미지의 시작 스크립트가 정한 약속입니다. 데이터 폴더가 비어 있을 때 한 번만 이 값으로 DB와 계정을 만듭니다.

| 줄 | 역할 | 빠지거나 바뀌면 | 문법 |
|---|---|---|---|
| `${DB_USERNAME:?DB_USERNAME is not set}` | `.env`의 값을 넣고, 비었거나 없으면 **컨테이너를 만들기 전에** 이 메시지로 실패 | `${DB_USERNAME}`만 쓰면 빈 값으로 진행되어 원인을 찾기 어려운 오류가 남 | Compose 변수 치환. `:?메시지` = 필수 값 |
| `db-data:/var/lib/mysql` | MySQL 데이터 폴더를 이름 있는 볼륨에 연결 | 빠지면 `docker compose down`할 때마다 데이터가 사라짐 | `볼륨이름:컨테이너경로`. 볼륨 이름은 `compose.yaml:35–36`의 `volumes:`에 선언 |
| `ports` 없음 | 호스트에 3306을 열지 않음 | `"3306:3306"`을 쓰면 로컬 MySQL과 포트 충돌로 시작 실패 | 키를 쓰지 않는 것 자체가 설정 |
| `mysqladmin ping -h 127.0.0.1` | TCP로 접속해 서버가 살아 있는지 확인 | `-h localhost`면 초기화용 임시 서버(소켓 전용)에 성공해서 준비 전에 healthy가 될 수 있음 | `["CMD", ...]`는 셸 없이 명령 실행. `mysqladmin ping`은 접속이 거부돼도 서버가 살아 있으면 성공을 돌려주므로 비밀번호가 필요 없음 |
| `interval / timeout / retries` | 5초마다, 3초 안에 응답, 20번까지 시도 | `retries`가 너무 작으면 첫 초기화가 끝나기 전에 unhealthy | Compose 기간 표기(`5s`) |

### 4-4. compose.yaml: app 서비스

`compose.yaml:21–33` (원문)
```yaml
  app:
    build: .
    depends_on:
      db:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/product_api
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      AI_CLIENT: ${AI_CLIENT:-stub}
      GEMINI_API_KEY: ${GEMINI_API_KEY:-}
    ports:
      - "${APP_PORT:-8080}:8080"
```

| 줄 | 역할 | 빠지거나 바뀌면 | 문법 |
|---|---|---|---|
| `build: .` | 현재 폴더의 `Dockerfile`로 이미지를 만듦 | `image:`만 있으면 미리 만든 이미지를 받아야 함 | Compose. `.`은 빌드 컨텍스트(복사 원본의 기준 폴더) |
| `condition: service_healthy` | `db`가 healthy가 된 뒤에 앱 시작 | `depends_on: [db]`만 쓰면 "시작"만 기다려서, 앱이 먼저 접속을 시도하다 실패할 수 있음 | Compose `depends_on`의 긴 형식 |
| `SPRING_DATASOURCE_URL` | `application.properties`의 `localhost:3306`을 `db:3306`으로 덮어씀 | 없으면 앱 컨테이너가 자기 자신의 3306을 찾다가 실패 | Spring Boot 완화된 바인딩: 대문자 + `_`를 소문자 + `.`로 대응. 환경 변수가 설정 파일보다 우선 |
| `DB_USERNAME: ${DB_USERNAME}` | 앱이 쓸 계정(`application.properties`의 `${DB_USERNAME}`)으로 전달 | — | 여기서는 `:?`가 없지만 `db` 서비스에서 이미 필수 검사를 하므로 빈 값으로 시작하지 않음 |
| `${AI_CLIENT:-stub}` | 값이 없으면 `stub` | — | `:-기본값` = 비었거나 없을 때 기본값 사용 |
| `"${APP_PORT:-8080}:8080"` | 호스트 포트(기본 8080) → 컨테이너 8080 | 따옴표가 없으면 YAML이 `:`가 든 값을 다르게 해석할 수 있음 | `호스트포트:컨테이너포트` |

### 4-5. 값이 정해지는 우선순위

`compose.yaml`의 `${DB_PASSWORD}`에 들어갈 값은 아래 순서로 정해집니다(Compose 규칙).

1. **docker compose를 실행한 터미널의 환경 변수**
2. `.env` 파일
3. `:-` 기본값

이 PC에는 `spring-boot:run`용으로 `DB_USERNAME`, `DB_PASSWORD`가 Windows 환경 변수로 설정되어 있어서, 그대로 실행하면 1번이 이깁니다. 그래서 확인할 때 새 터미널에서 `Remove-Item Env:DB_USERNAME, Env:DB_PASSWORD`를 먼저 실행했습니다(그 창에서만 지워지고 Windows 설정은 그대로입니다).

### 4-6. .env.example과 .gitignore

`.env.example:1–5` (원문)
```
# 복사해서 .env로 저장한 뒤 값을 채운다 (.env는 Git에 올라가지 않음)
# 컨테이너 MySQL 전용 계정. 로컬 MySQL과 별개이며 처음 실행할 때 이 값으로 만들어진다
DB_USERNAME=
DB_PASSWORD=
MYSQL_ROOT_PASSWORD=
```

- `키=값` 형식이고 `=` 앞뒤에 공백을 두지 않습니다. 커밋되는 것은 값이 빈 이 양식뿐이고, 실제 값은 `.env`에 둡니다. `.gitignore`에 `.env` 한 줄을 추가해 Git이 이 파일을 무시합니다(`.env.example`은 이름이 달라 무시되지 않습니다).
- `DB_USERNAME`에 `root`를 쓰면 안 됩니다. `MYSQL_USER`는 일반 계정을 만드는 변수라 `mysql` 이미지가 `root`를 거부합니다.

### 4-7. 제거한 local 프로필

커밋 `b5eb7b5`에서 제거한 `pom.xml` 설정 (변경 전 원문, 일부 발췌)
```xml
				<configuration>
					<!-- mvnw spring-boot:run 으로 실행할 때만 local 프로필 적용 (테스트에는 적용되지 않음) -->
					<profiles>
						<profile>local</profile>
					</profiles>
				</configuration>
```

- 역할: `spring-boot:run`일 때만 `application-local.properties`를 추가로 읽게 했습니다.
- 제거한 이유: Story 10에서 그 파일의 내용이 `spring.jpa.hibernate.ddl-auto=validate` 한 줄이 되었는데, `application.properties`에도 같은 값이 있습니다. 읽어도 결과가 같은 설정 층이라 지웠습니다.
- 차이: 없습니다. `spring-boot:run`, 테스트, Docker 모두 `application.properties` 기준으로 동작합니다.

## 5. 성공과 실패 사례

### 성공 1: 새 DB에 등록·수정·삭제 (사용자 제공)
- 입력: `POST /products {"name":"Monitor","price":200000}`
- 처리: app 컨테이너 → `db:3306` → Flyway가 만든 `products` 테이블에 INSERT
- 결과: `201 {"id":1,"name":"Monitor","price":200000,"description":null}`
- 기대값과 비교: 새 DB라 id 1 → 일치. 이어서 수정 200(`Wide Monitor`, `250000`), 삭제용 상품 201(`id 2`) → 삭제 204 → 모두 일치

### 성공 2: 컨테이너를 지워도 데이터 유지 (사용자 제공)
- 입력: `docker compose down` → `docker compose up -d` → `GET /products`
- 처리: 컨테이너는 새로 만들어지지만 볼륨 `db-data`는 그대로 연결
- 결과: `[{"id":1,"name":"Wide Monitor",...},{"id":3,"name":"Wireless Mouse",...,"description":"가볍게 들고 다니며 쓰기 좋은 무선 마우스입니다."}]`
- 기대값과 비교: 삭제한 id 2는 없고 id 1, 3과 저장한 설명이 남음 → 일치. `down` 출력에도 컨테이너와 네트워크만 `Removed`로 나오고 볼륨은 없었습니다.

### 성공 3: 로컬 MySQL은 그대로 (사용자 제공)
- 입력: 로컬 MySQL에서 `SELECT id, name, price FROM product_api.products ORDER BY id DESC LIMIT 5;`
- 결과: 최대 id 8(`Wireless Mouse Pro`, 이전 Story에서 만든 행), 새 행 없음
- 기대값과 비교: 컨테이너 요청이 로컬로 갔다면 id 9 이상이 생겼어야 함 → 생기지 않음, 일치

### 의도한 실패 1: 계정 값 누락 (로컬 실행 결과)
- 입력: `DB_PASSWORD`를 비운 채 `docker compose config`
- 결과: `required variable DB_PASSWORD is missing a value: DB_PASSWORD is not set`
- 의미: 컨테이너를 만들기 전에 멈춥니다. 빈 비밀번호로 DB가 만들어지는 것보다 나은 **정상 동작**입니다.

### 실패 2: 볼륨을 만든 뒤 비밀번호를 바꿈 (로컬 실행 결과, 검증 중 실수로 발생)
- 입력: 첫 `up`은 비밀번호 A로 실행 → `down`(볼륨 유지) → 비밀번호 B로 `up`
- 처리: `mysql` 이미지는 데이터 폴더가 비어 있지 않으면 계정을 다시 만들지 않음 → DB에는 여전히 A
- 결과: 앱이 B로 접속하다 실패해 컨테이너가 종료(`service "app" is not running`)
- 교훈: 비밀번호를 바꾸려면 `docker compose down -v`로 볼륨을 지우고 새로 시작해야 합니다(컨테이너 DB 데이터가 모두 삭제됨). README 6절에 주의점으로 적었습니다.

### 실패 3(설명용 예측): 3306을 호스트에 연다면
- `ports: ["3306:3306"]`을 넣으면, 로컬 MySQL이 이미 3306을 쓰고 있어 `db` 컨테이너가 포트 할당 오류로 시작하지 못할 것으로 예상됩니다. 이번에는 이 설정을 넣지 않아 직접 확인하지는 않았습니다.

## 6. 검증 한계와 사용자가 할 일

- 컨테이너에서 Gemini 모드(`AI_CLIENT=gemini`)는 확인하지 않았습니다. 확인하려면 `.env`에 `AI_CLIENT=gemini`와 키를 적고 `docker compose up -d` 후 `[설명 1]`을 보내고, `docker compose logs app`에 키가 찍히지 않았는지 봅니다.
- 400·404 요청은 Docker와 무관하게 Controller·Service에서 처리되므로 컨테이너에서 다시 보내지 않았습니다.
- 평소 사용: 시작 `docker compose up -d`, 종료 `docker compose down`. **`down -v`는 데이터 삭제**입니다.
- 터미널을 새로 열면 `Remove-Item Env:DB_USERNAME, Env:DB_PASSWORD`를 다시 해야 `.env` 값이 쓰입니다.

## 7. 이해도 확인 질문

**Q1. `compose.yaml`에서 `SPRING_DATASOURCE_URL`을 빼면 어떻게 될까요?**

> 해설: 앱은 `application.properties`의 `jdbc:mysql://localhost:3306/product_api`로 접속합니다. 컨테이너 안의 `localhost`는 app 컨테이너 자신이고, 그 안에는 MySQL이 없으므로 연결에 실패해 앱이 시작되지 않습니다. 로컬 PC의 MySQL로 접속되는 것도 아닙니다.

**Q2. `depends_on: [db]`만 쓰고 healthcheck를 빼면 무엇이 달라질까요?**

> 해설: Compose는 `db` 컨테이너가 "시작"되기만 하면 앱을 띄웁니다. 그런데 MySQL은 첫 실행 때 DB와 계정을 만드는 데 몇 초 이상 걸립니다. 그동안 앱이 접속을 시도하면 Flyway가 연결에 실패하고 앱이 종료될 수 있습니다. `condition: service_healthy`는 "접속을 받을 준비가 됐다"까지 기다립니다.

**Q3. `.env`에 `DB_PASSWORD`를 적었는데도 앱이 다른 비밀번호로 접속을 시도합니다. 가능한 원인 두 가지는?**

> 해설: (1) 실행한 터미널에 `DB_PASSWORD` 환경 변수가 있어 `.env`보다 우선 적용됐습니다. (2) 볼륨이 이전 실행 때의 비밀번호로 이미 만들어져 있어서, DB 쪽 비밀번호가 `.env`와 다릅니다. (1)은 `Remove-Item Env:DB_PASSWORD`, (2)는 데이터를 버려도 될 때 `down -v`로 해결합니다.
