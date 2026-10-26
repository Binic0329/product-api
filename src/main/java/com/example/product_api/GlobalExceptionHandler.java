package com.example.product_api;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// ResponseEntityExceptionHandler를 상속하면 Spring MVC의 표준 예외(405, 415 등)도
// 원래 상태 코드를 유지한 채 이 클래스를 거치므로, 응답 형식을 한 곳에서 통일할 수 있다
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // @Valid 검증 실패: 잘못된 필드의 메시지를 모아서 반환
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    // JSON 문법 오류, 타입 불일치, int 필드(id) 누락 등 본문을 읽지 못한 경우
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "Malformed JSON request or missing required field");
    }

    // 그 밖의 Spring 예외와 ResponseStatusException(404, 409 등)이 모두 여기로 모인다
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex,
        Object body,
        HttpHeaders headers,
        HttpStatusCode statusCode,
        WebRequest request
    ) {
        String message = null;
        if (ex instanceof ResponseStatusException responseStatusException) {
            message = responseStatusException.getReason();
        }
        if (message == null) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            message = status != null ? status.getReasonPhrase() : "Error";
        }
        return ResponseEntity.status(statusCode)
            .headers(headers)
            .body(new ApiErrorResponse(statusCode.value(), message));
    }

    // 예상하지 못한 오류: 원인은 서버 로그에만 남기고 응답에는 상세 정보를 숨긴다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unexpected server error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(new ApiErrorResponse(status.value(), message));
    }
}
