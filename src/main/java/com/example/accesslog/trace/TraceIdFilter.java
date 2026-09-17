package com.example.accesslog.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청별 traceId를 확정하고 MDC / response header에 전파하는 필터.
 *
 * <p>정책 우선순위:
 * <ol>
 *     <li>요청 헤더 {@code X-Trace-Id}가 있으면 사용</li>
 *     <li>없으면 서버에서 신규 생성</li>
 *     <li>MDC에 저장</li>
 *     <li>response header에 동일한 traceId 반환</li>
 * </ol>
 *
 * <p>access log / error log 보다 먼저 동작해야 하므로 가장 높은 우선순위로 등록한다.
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        MDC.put(TraceConstants.TRACE_ID, traceId);
        response.setHeader(TraceConstants.TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceConstants.TRACE_ID);
        }
    }
}
