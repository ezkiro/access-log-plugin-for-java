package com.example.accesslog;

import com.example.accesslog.mask.BodyMasker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * HTTP request/response 전체 흐름을 capture 하여 access log를 남기는 필터.
 *
 * <p>{@link ContentCachingRequestWrapper} / {@link ContentCachingResponseWrapper}로 body를 cache 하고,
 * 처리 완료 후 elapsed time과 함께 {@link AccessLogEntry}를 작성한다.
 *
 * <p>예외가 발생하더라도 {@code finally}에서 access log를 남기며,
 * {@code copyBodyToResponse()}로 캐시된 응답 body를 실제 응답으로 복사한다.
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private final AccessLogWriter accessLogWriter;
    private final AccessLogProperties properties;
    private final BodyMasker bodyMasker;

    public AccessLogFilter(
            AccessLogWriter accessLogWriter,
            AccessLogProperties properties,
            BodyMasker bodyMasker
    ) {
        this.accessLogWriter = accessLogWriter;
        this.properties = properties;
        this.bodyMasker = bodyMasker;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return properties.getExcludePatterns().stream()
                .anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long startedAt = System.currentTimeMillis();

        ContentCachingRequestWrapper wrappedRequest =
                wrapRequest(request);

        ContentCachingResponseWrapper wrappedResponse =
                wrapResponse(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long elapsedMs = System.currentTimeMillis() - startedAt;

            try {
                AccessLogEntry entry = AccessLogEntry.from(
                        wrappedRequest,
                        wrappedResponse,
                        elapsedMs,
                        properties,
                        bodyMasker
                );
                accessLogWriter.write(entry);
            } finally {
                // 캐시된 응답 body를 실제 클라이언트 응답으로 복사한다. 누락 시 빈 응답이 나갈 수 있다.
                wrappedResponse.copyBodyToResponse();
            }
        }
    }

    private static ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper cached) {
            return cached;
        }
        return new ContentCachingRequestWrapper(request);
    }

    private static ContentCachingResponseWrapper wrapResponse(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper cached) {
            return cached;
        }
        return new ContentCachingResponseWrapper(response);
    }
}
