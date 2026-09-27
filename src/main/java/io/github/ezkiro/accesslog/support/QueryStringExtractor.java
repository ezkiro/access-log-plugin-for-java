package io.github.ezkiro.accesslog.support;

import io.github.ezkiro.accesslog.AccessLogProperties;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 원본 query string의 구조를 유지하면서 지정된 parameter 값을 마스킹한다.
 */
public final class QueryStringExtractor {

    private static final String MASK = "***";

    private QueryStringExtractor() {
    }

    public static String extract(HttpServletRequest request, AccessLogProperties properties) {
        if (!properties.isIncludeQueryString()) {
            return null;
        }

        String queryString = request.getQueryString();
        if (queryString == null || queryString.isEmpty()) {
            return queryString;
        }

        Set<String> maskedParameters = properties.getMaskedQueryParameters().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return Arrays.stream(queryString.split("&", -1))
                .map(part -> maskPart(part, maskedParameters))
                .collect(Collectors.joining("&"));
    }

    private static String maskPart(String part, Set<String> maskedParameters) {
        int equalsIndex = part.indexOf('=');
        String rawName = equalsIndex >= 0 ? part.substring(0, equalsIndex) : part;
        String decodedName = decode(rawName);

        if (!maskedParameters.contains(decodedName.toLowerCase(Locale.ROOT))) {
            return part;
        }

        return rawName + "=" + MASK;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }
}
