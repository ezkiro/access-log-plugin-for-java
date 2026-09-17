package com.example.accesslog.error;

/**
 * 일관된 에러 응답 형태. traceId를 포함하여 CS/장애 대응 시 access log 및 error log와 연결한다.
 */
public record ErrorResponse(
        String code,
        String message,
        String traceId
) {
}
