# 1단계: jar 빌드 (테스트는 CI와 로컬 mvnw test가 담당하므로 건너뜀)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN chmod +x mvnw && ./mvnw -B package -DskipTests

# 2단계: 실행용 이미지 (JRE + jar만 포함, root가 아닌 사용자로 실행)
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
