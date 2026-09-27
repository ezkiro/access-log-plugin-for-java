package com.example.accesslog;

import com.example.accesslog.mask.BodyMasker;
import com.example.accesslog.support.QueryStringExtractor;
import com.example.accesslog.trace.TraceConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

/**
 * 하나의 HTTP transaction을 표현하는 access log entry.
 *
 * <p>예외 발생 여부와 무관하게 클라이언트에게 반환된 response를 기준으로 request/response pair를 남긴다.
 * 예외 상세(stack trace 등)는 이 entry에 포함하지 않고 별도 application error log로 분리한다.
 *
 * <p>{@code request.body} / {@code response.body}는 원본 문자열(JSON이면 JSON 문자열)로 기록된다.
 */
public record AccessLogEntry(
        String logType,
        String traceId,
        String method,
        String uri,
        String queryString,
        String clientIp,
        String userAgent,
        int status,
        long elapsedMs,
        RequestLog request,
        ResponseLog response
) {
    public static AccessLogEntry from(
            HttpServletRequest request,
            HttpServletResponse response,
            long elapsedMs,
            AccessLogProperties properties,
            BodyMasker masker
    ) {
        return new AccessLogEntry(
                "access",
                MDC.get(TraceConstants.TRACE_ID),
                request.getMethod(),
                request.getRequestURI(),
                QueryStringExtractor.extract(request, properties),
                resolveClientIp(request),
                request.getHeader("User-Agent"),
                response.getStatus(),
                elapsedMs,
                RequestLog.from(request, properties, masker),
                ResponseLog.from(response, properties, masker)
        );
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
