package com.example.accesslog;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Access log 동작을 제어하는 설정값.
 *
 * <pre>
 * access-log:
 *   enabled: true
 *   include-request-headers: true
 *   include-request-body: true
 *   include-response-headers: true
 *   include-response-body: true
 *   max-body-length: 5000
 *   max-body-cache-size: 65536
 *   include-query-string: true
 *   masked-query-parameters: [password, token, accessToken, refreshToken, secret]
 *   exclude-patterns: [/actuator, /health]
 *   masked-headers: [authorization, cookie, set-cookie, x-api-key]
 *   masked-fields: [password, token, accessToken, refreshToken, secret]
 *   excluded-content-types: [multipart/, text/event-stream, ...]
 * </pre>
 */
@ConfigurationProperties(prefix = "access-log")
public class AccessLogProperties {

    /** 모듈 활성화 여부. */
    private boolean enabled = true;

    /** request header 기록 여부. */
    private boolean includeRequestHeaders = true;

    /** request body 기록 여부. */
    private boolean includeRequestBody = true;

    /** response header 기록 여부. */
    private boolean includeResponseHeaders = true;

    /** response body 기록 여부. */
    private boolean includeResponseBody = true;

    /** body 기록 최대 길이. 초과분은 truncate 된다. */
    private int maxBodyLength = 5000;

    /** request/response body를 메모리에 보관할 최대 byte 수. */
    private int maxBodyCacheSize = 64 * 1024;

    /** query string 기록 여부. */
    private boolean includeQueryString = true;

    /** 값을 마스킹할 query parameter 이름 목록 (대소문자 무시). */
    private List<String> maskedQueryParameters = List.of(
            "password",
            "token",
            "accesstoken",
            "refreshtoken",
            "secret"
    );

    /** access log 자체를 남기지 않을 URI prefix 목록. */
    private List<String> excludePatterns = List.of(
            "/actuator",
            "/health"
    );

    /** 값을 마스킹할 header 이름 목록 (대소문자 무시). */
    private List<String> maskedHeaders = List.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key"
    );

    /** 값을 마스킹할 JSON body field 이름 목록 (대소문자 무시). */
    private List<String> maskedFields = List.of(
            "password",
            "token",
            "accesstoken",
            "refreshtoken",
            "secret"
    );

    /** body 기록을 생략할 content-type prefix 목록. */
    private List<String> excludedContentTypes = List.of(
            "multipart/",
            "text/event-stream",
            "application/octet-stream",
            "application/pdf",
            "image/",
            "video/"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIncludeRequestHeaders() {
        return includeRequestHeaders;
    }

    public void setIncludeRequestHeaders(boolean includeRequestHeaders) {
        this.includeRequestHeaders = includeRequestHeaders;
    }

    public boolean isIncludeRequestBody() {
        return includeRequestBody;
    }

    public void setIncludeRequestBody(boolean includeRequestBody) {
        this.includeRequestBody = includeRequestBody;
    }

    public boolean isIncludeResponseHeaders() {
        return includeResponseHeaders;
    }

    public void setIncludeResponseHeaders(boolean includeResponseHeaders) {
        this.includeResponseHeaders = includeResponseHeaders;
    }

    public boolean isIncludeResponseBody() {
        return includeResponseBody;
    }

    public void setIncludeResponseBody(boolean includeResponseBody) {
        this.includeResponseBody = includeResponseBody;
    }

    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    public void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }

    public int getMaxBodyCacheSize() {
        return maxBodyCacheSize;
    }

    public void setMaxBodyCacheSize(int maxBodyCacheSize) {
        if (maxBodyCacheSize <= 0) {
            throw new IllegalArgumentException("access-log.max-body-cache-size must be greater than zero");
        }
        this.maxBodyCacheSize = maxBodyCacheSize;
    }

    public boolean isIncludeQueryString() {
        return includeQueryString;
    }

    public void setIncludeQueryString(boolean includeQueryString) {
        this.includeQueryString = includeQueryString;
    }

    public List<String> getMaskedQueryParameters() {
        return maskedQueryParameters;
    }

    public void setMaskedQueryParameters(List<String> maskedQueryParameters) {
        this.maskedQueryParameters = maskedQueryParameters;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public List<String> getMaskedHeaders() {
        return maskedHeaders;
    }

    public void setMaskedHeaders(List<String> maskedHeaders) {
        this.maskedHeaders = maskedHeaders;
    }

    public List<String> getMaskedFields() {
        return maskedFields;
    }

    public void setMaskedFields(List<String> maskedFields) {
        this.maskedFields = maskedFields;
    }

    public List<String> getExcludedContentTypes() {
        return excludedContentTypes;
    }

    public void setExcludedContentTypes(List<String> excludedContentTypes) {
        this.excludedContentTypes = excludedContentTypes;
    }
}
