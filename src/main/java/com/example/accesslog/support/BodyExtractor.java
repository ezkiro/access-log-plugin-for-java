package com.example.accesslog.support;

import com.example.accesslog.AccessLogProperties;
import com.example.accesslog.mask.BodyMasker;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * request / response body를 안전하게 추출한다.
 *
 * <p>처리 순서:
 * <ol>
 *     <li>content-type이 제외 대상이면 placeholder 반환 (binary/대용량 보호)</li>
 *     <li>captured byte를 charset에 맞춰 문자열로 변환</li>
 *     <li>민감 field 마스킹</li>
 *     <li>{@code max-body-length}로 truncate</li>
 * </ol>
 */
public final class BodyExtractor {

    private static final String TRUNCATED_SUFFIX = "...(truncated)";

    private BodyExtractor() {
    }

    public static String extractRequestBody(
            ContentCachingRequestWrapper request,
            AccessLogProperties properties,
            BodyMasker masker
    ) {
        String contentType = request.getContentType();
        if (isExcludedContentType(contentType, properties)) {
            return omittedPlaceholder(contentType);
        }

        byte[] content = request.getContentAsByteArray();
        return toLoggableBody(content, request.getCharacterEncoding(), properties, masker);
    }

    public static String extractResponseBody(
            ContentCachingResponseWrapper response,
            AccessLogProperties properties,
            BodyMasker masker
    ) {
        String contentType = response.getContentType();
        if (isExcludedContentType(contentType, properties)) {
            return omittedPlaceholder(contentType);
        }

        byte[] content = response.getContentAsByteArray();
        return toLoggableBody(content, response.getCharacterEncoding(), properties, masker);
    }

    private static String toLoggableBody(
            byte[] content,
            String characterEncoding,
            AccessLogProperties properties,
            BodyMasker masker
    ) {
        if (content == null || content.length == 0) {
            return null;
        }

        Charset charset = resolveCharset(characterEncoding);
        String body = new String(content, charset);

        String masked = masker.mask(body);
        return truncate(masked, properties.getMaxBodyLength());
    }

    private static boolean isExcludedContentType(String contentType, AccessLogProperties properties) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return properties.getExcludedContentTypes().stream()
                .map(String::toLowerCase)
                .anyMatch(lower::startsWith);
    }

    private static String omittedPlaceholder(String contentType) {
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return "[multipart omitted]";
        }
        return "[binary omitted]";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + TRUNCATED_SUFFIX;
    }

    private static Charset resolveCharset(String characterEncoding) {
        if (characterEncoding == null || characterEncoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(characterEncoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
