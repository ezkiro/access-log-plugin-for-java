package com.example.accesslog;

/**
 * Access log entry를 실제 출력 대상(logger, stdout, 외부 시스템 등)에 기록하는 전략.
 */
public interface AccessLogWriter {

    void write(AccessLogEntry entry);
}
