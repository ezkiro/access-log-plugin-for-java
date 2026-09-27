package io.github.ezkiro.accesslog.support;

import io.github.ezkiro.accesslog.AccessLogProperties;
import io.github.ezkiro.accesslog.mask.BodyMasker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
            HttpServletRequest request,
            AccessLogProperties properties,
            BodyMasker masker
    ) {
        String contentType = request.getContentType();
        if (isExcludedContentType(contentType, properties)) {
            return omittedPlaceholder(contentType);
        }

        if (!(request instanceof BoundedContentCachingRequestWrapper cachedRequest)) {
            return null;
        }
        if (cachedRequest.isOverflowed()) {
            return overflowPlaceholder(properties);
        }

        byte[] content = cachedRequest.getContentAsByteArray();
        return toLoggableBody(content, request.getCharacterEncoding(), properties, masker);
    }

    public static String extractResponseBody(
            HttpServletResponse response,
            AccessLogProperties properties,
            BodyMasker masker
    ) {
        String contentType = response.getContentType();
        if (isExcludedContentType(contentType, properties)) {
            return omittedPlaceholder(contentType);
        }

        if (!(response instanceof BoundedContentCachingResponseWrapper cachedResponse)) {
            return null;
        }
        if (cachedResponse.isOverflowed()) {
            return overflowPlaceholder(properties);
        }

        byte[] content = cachedResponse.getContentAsByteArray();
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
        String lower = contentType.toLowerCase(Locale.ROOT);
        return properties.getExcludedContentTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(lower::startsWith);
    }

    private static String omittedPlaceholder(String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return "[multipart omitted]";
        }
        return "[binary omitted]";
    }

    private static String overflowPlaceholder(AccessLogProperties properties) {
        return "[body omitted: exceeds " + properties.getMaxBodyCacheSize() + " byte cache limit]";
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
