package com.example.accesslog.integration;

import com.example.accesslog.AccessLogEntry;
import com.example.accesslog.AccessLogWriter;
import com.example.accesslog.trace.TraceConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "access-log.exception-handler.enabled=true",
                "access-log.max-body-length=5000"
        }
)
class AccessLogIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CapturingAccessLogWriter writer;

    @BeforeEach
    void clear() {
        writer.entries.clear();
    }

    @Test
    void logsRequestResponsePairAndPreservesResponseBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> req = new HttpEntity<>("{\"name\":\"kim\",\"password\":\"1234\"}", headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/echo", req, String.class);

        // 응답 body가 보존되어야 한다 (copyBodyToResponse).
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("kim");
        // 응답 header에 traceId가 포함된다.
        assertThat(response.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER)).isNotBlank();

        AccessLogEntry entry = writer.last();
        assertThat(entry.logType()).isEqualTo("access");
        assertThat(entry.method()).isEqualTo("POST");
        assertThat(entry.uri()).isEqualTo("/api/echo");
        assertThat(entry.status()).isEqualTo(200);
        assertThat(entry.traceId()).isNotBlank();
        // request body의 민감 필드는 마스킹된다.
        assertThat(entry.request().body()).contains("kim");
        assertThat(entry.request().body()).doesNotContain("1234");
        assertThat(entry.request().body()).contains("***");
        // response body도 기록된다.
        assertThat(entry.response().body()).contains("kim");
    }

    @Test
    void logsAccessPairEvenWhenExceptionOccurs() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/boom", null, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        // GlobalExceptionHandler가 일관된 ErrorResponse를 반환한다.
        assertThat(response.getBody()).contains("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody()).contains("traceId");

        AccessLogEntry entry = writer.last();
        assertThat(entry.status()).isEqualTo(500);
        assertThat(entry.uri()).isEqualTo("/api/boom");
        // 예외가 발생해도 response pair가 유지된다.
        assertThat(entry.response().body()).contains("INTERNAL_SERVER_ERROR");
    }

    @Test
    void doesNotLogExcludedPattern() {
        restTemplate.getForEntity("/health", String.class);
        assertThat(writer.entries).isEmpty();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        CapturingAccessLogWriter capturingAccessLogWriter() {
            return new CapturingAccessLogWriter();
        }

        @RestController
        static class TestController {

            @PostMapping(value = "/api/echo", consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
            public String echo(@RequestBody String body) {
                return body;
            }

            @PostMapping("/api/boom")
            public String boom() {
                throw new IllegalStateException("boom");
            }
        }
    }

    static class CapturingAccessLogWriter implements AccessLogWriter {
        final List<AccessLogEntry> entries = new CopyOnWriteArrayList<>();

        @Override
        public void write(AccessLogEntry entry) {
            entries.add(entry);
        }

        AccessLogEntry last() {
            assertThat(entries).isNotEmpty();
            return entries.get(entries.size() - 1);
        }
    }
}
