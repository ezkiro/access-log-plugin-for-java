package io.github.ezkiro.accesslog.trace;

/**
 * Trace 관련 상수.
 *
 * <p>access log와 application error log를 동일한 traceId로 grouping 하기 위한
 * MDC key와 HTTP header 이름을 정의한다.
 */
public final class TraceConstants {

    /** MDC에 저장되는 traceId key. */
    public static final String TRACE_ID = "traceId";

    /** traceId 전달/응답에 사용되는 HTTP header 이름. */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private TraceConstants() {
    }
}
