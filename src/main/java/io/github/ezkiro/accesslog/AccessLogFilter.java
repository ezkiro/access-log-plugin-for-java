package io.github.ezkiro.accesslog;

import io.github.ezkiro.accesslog.mask.BodyMasker;
import io.github.ezkiro.accesslog.support.BoundedContentCachingRequestWrapper;
import io.github.ezkiro.accesslog.support.BoundedContentCachingResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP request/response 전체 흐름을 capture 하여 access log를 남기는 필터.
 *
 * <p>body 기록이 활성화된 경우에만 bounded caching wrapper를 사용하고,
 * 처리 완료 후 elapsed time과 함께 {@link AccessLogEntry}를 작성한다.
 * response는 클라이언트로 즉시 전달되며 로그용 복사본만 제한된 크기로 보관한다.
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

        HttpServletRequest wrappedRequest = properties.isIncludeRequestBody()
                ? wrapRequest(request, properties.getMaxBodyCacheSize())
                : request;

        HttpServletResponse wrappedResponse = properties.isIncludeResponseBody()
                ? wrapResponse(response, properties.getMaxBodyCacheSize())
                : response;

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            if (wrappedResponse instanceof BoundedContentCachingResponseWrapper cachedResponse) {
                cachedResponse.flushCapturedContent();
            }
            long elapsedMs = System.currentTimeMillis() - startedAt;

            AccessLogEntry entry = AccessLogEntry.from(
                    wrappedRequest,
                    wrappedResponse,
                    elapsedMs,
                    properties,
                    bodyMasker
            );
            accessLogWriter.write(entry);
        }
    }

    private static BoundedContentCachingRequestWrapper wrapRequest(HttpServletRequest request, int cacheLimit) {
        if (request instanceof BoundedContentCachingRequestWrapper cached) {
            return cached;
        }
        return new BoundedContentCachingRequestWrapper(request, cacheLimit);
    }

    private static BoundedContentCachingResponseWrapper wrapResponse(HttpServletResponse response, int cacheLimit) {
        if (response instanceof BoundedContentCachingResponseWrapper cached) {
            return cached;
        }
        return new BoundedContentCachingResponseWrapper(response, cacheLimit);
    }
}
