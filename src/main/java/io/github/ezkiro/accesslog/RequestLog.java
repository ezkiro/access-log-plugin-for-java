package io.github.ezkiro.accesslog;

import io.github.ezkiro.accesslog.mask.BodyMasker;
import io.github.ezkiro.accesslog.support.BodyExtractor;
import io.github.ezkiro.accesslog.support.HeaderExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Access log의 request 부분.
 */
public record RequestLog(
        Map<String, String> headers,
        String body
) {
    public static RequestLog from(
            HttpServletRequest request,
            AccessLogProperties properties,
            BodyMasker masker
    ) {
        Map<String, String> headers = properties.isIncludeRequestHeaders()
                ? HeaderExtractor.extract(request, properties)
                : Map.of();

        String body = properties.isIncludeRequestBody()
                ? BodyExtractor.extractRequestBody(request, properties, masker)
                : null;

        return new RequestLog(headers, body);
    }
}
