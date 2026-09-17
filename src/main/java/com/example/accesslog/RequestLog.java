package com.example.accesslog;

import com.example.accesslog.mask.BodyMasker;
import com.example.accesslog.support.BodyExtractor;
import com.example.accesslog.support.HeaderExtractor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.Map;

/**
 * Access log의 request 부분.
 */
public record RequestLog(
        Map<String, String> headers,
        String body
) {
    public static RequestLog from(
            ContentCachingRequestWrapper request,
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
