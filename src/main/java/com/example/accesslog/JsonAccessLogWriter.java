package com.example.accesslog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access log entry를 JSON 한 줄로 직렬화하여 {@code ACCESS_LOG} logger에 INFO로 기록한다.
 *
 * <p>logger name을 분리해 두면 logback 등에서 access log 전용 appender로 라우팅할 수 있다.
 */
public class JsonAccessLogWriter implements AccessLogWriter {

    /** access log 전용 logger. logback에서 별도 appender로 분리 권장. */
    private static final Logger log = LoggerFactory.getLogger("ACCESS_LOG");

    private final ObjectMapper objectMapper;

    public JsonAccessLogWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(AccessLogEntry entry) {
        try {
            log.info(objectMapper.writeValueAsString(entry));
        } catch (Exception e) {
            // 로깅 실패가 요청 처리에 영향을 주지 않도록 삼킨다.
            log.warn("Failed to write access log", e);
        }
    }
}
