package io.github.ezkiro.accesslog.support;

import io.github.ezkiro.accesslog.AccessLogProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * request / response 의 header를 추출하고 민감 header를 마스킹한다.
 */
public final class HeaderExtractor {

    private static final String MASK = "***";

    private HeaderExtractor() {
    }

    public static Map<String, String> extract(HttpServletRequest request, AccessLogProperties properties) {
        Set<String> masked = lowercased(properties.getMaskedHeaders());
        Map<String, String> result = new LinkedHashMap<>();

        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return result;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            result.put(name, maskIfNeeded(name, request.getHeader(name), masked));
        }
        return result;
    }

    public static Map<String, String> extract(HttpServletResponse response, AccessLogProperties properties) {
        Set<String> masked = lowercased(properties.getMaskedHeaders());
        Map<String, String> result = new LinkedHashMap<>();

        Collection<String> names = response.getHeaderNames();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            result.put(name, maskIfNeeded(name, response.getHeader(name), masked));
        }
        return result;
    }

    private static String maskIfNeeded(String name, String value, Set<String> masked) {
        return masked.contains(name.toLowerCase()) ? MASK : value;
    }

    private static Set<String> lowercased(java.util.List<String> values) {
        return values.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }
}
