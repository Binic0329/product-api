package com.example.product_api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

// 테스트 전용 가짜 Gemini 서버. 내 PC(localhost)에서만 열리고 실제 Google 서버는 호출하지 않는다
// JDK에 들어 있는 HttpServer를 써서 의존성을 추가하지 않는다
class FakeGeminiServer {

    private final HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();

    private volatile int status = 200;
    private volatile String body = "{}";
    private volatile long delayMillis = 0;

    private volatile String lastPath;
    private volatile String lastApiKeyHeader;
    private volatile String lastRequestBody;

    FakeGeminiServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);   // 0: 빈 포트 자동 선택
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            lastPath = exchange.getRequestURI().getPath();
            lastApiKeyHeader = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);   // 타임아웃 상황을 흉내 낸다
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (InterruptedException | IOException ex) {
                // 클라이언트가 타임아웃으로 먼저 연결을 끊으면 여기로 온다
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/v1beta";
    }

    void respond(int status, String body) {
        this.status = status;
        this.body = body;
        this.delayMillis = 0;
    }

    void respondWithText(String text) {
        respond(200, """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP"}]}
            """.formatted(text));
    }

    void respondAfterDelay(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    void reset() {
        respond(200, "{}");
        requestCount.set(0);
        lastPath = null;
        lastApiKeyHeader = null;
        lastRequestBody = null;
    }

    int requestCount() {
        return requestCount.get();
    }

    String lastPath() {
        return lastPath;
    }

    String lastApiKeyHeader() {
        return lastApiKeyHeader;
    }

    String lastRequestBody() {
        return lastRequestBody;
    }

    void stop() {
        server.stop(0);
    }
}
