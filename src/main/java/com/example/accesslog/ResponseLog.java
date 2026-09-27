package com.example.accesslog;

import com.example.accesslog.mask.BodyMasker;
import com.example.accesslog.support.BodyExtractor;
import com.example.accesslog.support.HeaderExtractor;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

/**
 * Access log의 response 부분.
 */
public record ResponseLog(
        Map<String, String> headers,
        String body
) {
    public static ResponseLog from(
            HttpServletResponse response,
            AccessLogProperties properties,
            BodyMasker masker
    ) {
        Map<String, String> headers = properties.isIncludeResponseHeaders()
                ? HeaderExtractor.extract(response, properties)
                : Map.of();

        String body = properties.isIncludeResponseBody()
                ? BodyExtractor.extractResponseBody(response, properties, masker)
                : null;

        return new ResponseLog(headers, body);
    }
}
