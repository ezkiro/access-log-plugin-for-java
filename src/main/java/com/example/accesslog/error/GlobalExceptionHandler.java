package com.example.accesslog.error;

import com.example.accesslog.trace.TraceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 기본 제공 GlobalExceptionHandler.
 *
 * <p>예외 상세(stack trace)는 access log가 아닌 {@code APP_ERROR} logger로 분리 기록하고,
 * 클라이언트에는 traceId를 포함한 일관된 {@link ErrorResponse}를 반환한다.
 * 동일 traceId로 access log와 error log를 grouping 할 수 있다.
 *
 * <p>예외 유형별 로그 레벨 정책:
 * <ul>
 *     <li>Validation error (400) - warn (stack trace 생략)</li>
 *     <li>Unexpected exception (500) - error + stack trace</li>
 * </ul>
 *
 * <p>각 서비스가 자체 advice를 제공하면 이 핸들러는 비활성화된다
 * (auto-configuration의 {@code @ConditionalOnMissingBean} 및 opt-in property 참고).
 * 서비스별 business exception은 각 서비스에서 추가로 처리하는 것을 권장한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger errorLog = LoggerFactory.getLogger("APP_ERROR");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String traceId = MDC.get(TraceConstants.TRACE_ID);

        // validation 오류는 노이즈가 크므로 stack trace 없이 warn 으로만 남긴다.
        errorLog.warn("Validation failed. traceId={}, message={}", traceId, e.getMessage());

        ErrorResponse response = new ErrorResponse(
                "INVALID_REQUEST",
                "Invalid request",
                traceId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        String traceId = MDC.get(TraceConstants.TRACE_ID);

        errorLog.error(
                "Unhandled exception. traceId={}, exceptionType={}, message={}",
                traceId,
                e.getClass().getName(),
                e.getMessage(),
                e
        );

        ErrorResponse response = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Internal server error",
                traceId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
