# Spring Boot REST API Access Log 공용 모듈 설계안

## 1. 목적

Spring Boot 기반 Java 서비스에서 REST API 호출에 대한 access log를 공통 방식으로 남기기 위한 공용 모듈을 설계한다.

본 설계의 핵심 방향은 다음과 같다.

```text
Access Log  = request + response + status + elapsedMs
Error Log   = exception detail + stack trace
Grouping    = same traceId
```

즉, REST API 호출 결과는 항상 HTTP transaction 관점에서 `request`와 `response` pair로 access log에 남기고, exception 상세 정보는 별도의 application error log로 남긴다. 두 로그는 동일한 `traceId`를 사용하여 운영 로그 시스템에서 grouping 가능하게 한다.

---

## 2. 설계 원칙

### 2.1 Access Log의 역할

Access log는 HTTP 요청과 응답의 결과를 기록한다.

포함 대상:

- HTTP method
- URI
- query string
- client IP
- user agent
- request header
- request body
- response status
- response body
- elapsed time
- traceId

exception 발생 여부와 관계없이, 클라이언트에게 반환된 response를 기준으로 남긴다.

### 2.2 Application Error Log의 역할

Exception 상세 정보는 access log가 아닌 application error log에 남긴다.

포함 대상:

- traceId
- exception type
- exception message
- root cause
- stack trace
- business context, if needed

이를 통해 access log는 API 호출 통계와 요청/응답 분석에 집중하고, application log는 장애 원인 분석에 집중할 수 있다.

---

## 3. 권장 모듈 구조

```text
common-web-logging-starter
 ├─ TraceIdFilter
 ├─ AccessLogFilter
 ├─ AccessLogProperties
 ├─ AccessLogEntry
 ├─ RequestLog
 ├─ ResponseLog
 ├─ AccessLogWriter
 ├─ JsonAccessLogWriter
 ├─ BodyMasker
 └─ AccessLogAutoConfiguration
```

Exception 처리는 별도 공용 exception 모듈 또는 각 서비스의 `GlobalExceptionHandler`에서 담당한다.

```text
GlobalExceptionHandler
 ├─ ErrorResponse 생성
 ├─ APP_ERROR logger에 exception 상세 기록
 └─ traceId 포함한 response 반환
```

---

## 4. 전체 처리 흐름

```text
Client
  -> TraceIdFilter
      - request header에서 traceId 확인
      - 없으면 신규 traceId 생성
      - MDC에 traceId 저장
      - response header에 traceId 추가
  -> AccessLogFilter
      - request body capture
      - response body capture
      - elapsed time 측정
  -> DispatcherServlet
  -> Controller
  -> GlobalExceptionHandler, if exception occurs
      - error response 생성
      - APP_ERROR logger로 exception 상세 기록
  -> AccessLogFilter finally
      - request + response access log 기록
      - response body copy
  -> Client
```

---

## 5. 로그 포맷

### 5.1 정상 응답 Access Log 예시

```json
{
  "logType": "access",
  "traceId": "7f3b2a9c1e4d",
  "method": "POST",
  "uri": "/api/users",
  "queryString": "type=admin",
  "clientIp": "10.0.1.25",
  "userAgent": "Mozilla/5.0",
  "status": 200,
  "elapsedMs": 34,
  "request": {
    "headers": {
      "content-type": "application/json"
    },
    "body": {
      "name": "kim"
    }
  },
  "response": {
    "headers": {
      "content-type": "application/json"
    },
    "body": {
      "id": 1,
      "name": "kim"
    }
  }
}
```

### 5.2 Exception 발생 시 Access Log 예시

Exception이 발생해도 access log는 request와 response pair를 유지한다.

```json
{
  "logType": "access",
  "traceId": "7f3b2a9c1e4d",
  "method": "POST",
  "uri": "/api/users",
  "queryString": null,
  "clientIp": "10.0.1.25",
  "userAgent": "Mozilla/5.0",
  "status": 500,
  "elapsedMs": 37,
  "request": {
    "headers": {
      "content-type": "application/json"
    },
    "body": {
      "name": "kim"
    }
  },
  "response": {
    "headers": {
      "content-type": "application/json"
    },
    "body": {
      "code": "INTERNAL_SERVER_ERROR",
      "message": "Internal server error",
      "traceId": "7f3b2a9c1e4d"
    }
  }
}
```

### 5.3 Application Error Log 예시

```json
{
  "logType": "application_error",
  "traceId": "7f3b2a9c1e4d",
  "exceptionType": "java.lang.IllegalStateException",
  "message": "User state is invalid",
  "stackTrace": "..."
}
```

---

## 6. TraceId 설계

### 6.1 TraceId 정책

우선순위:

1. 요청 헤더 `X-Trace-Id`가 있으면 사용
2. 없으면 서버에서 신규 생성
3. 생성 또는 수신한 traceId를 MDC에 저장
4. response header에도 동일한 traceId 반환

권장 헤더명:

```text
X-Trace-Id
```

### 6.2 TraceConstants

```java
public final class TraceConstants {
    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private TraceConstants() {
    }
}
```

### 6.3 TraceIdFilter 예시

```java
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        MDC.put(TraceConstants.TRACE_ID, traceId);
        response.setHeader(TraceConstants.TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceConstants.TRACE_ID);
        }
    }
}
```

---

## 7. AccessLogFilter 설계

### 7.1 구현 방향

`OncePerRequestFilter`를 사용한다.

`HandlerInterceptor`보다 `Filter`가 적합한 이유:

| 방식 | 장점 | 한계 |
|---|---|---|
| HandlerInterceptor | Controller 전후 처리 쉬움 | request/response body capture에 부적합 |
| AOP | 특정 method 단위 로깅 쉬움 | HTTP access log 관점에는 부적합 |
| OncePerRequestFilter | HTTP request/response 전체 흐름 추적 가능 | body wrapper 처리 필요 |

### 7.2 AccessLogFilter 예시

```java
public class AccessLogFilter extends OncePerRequestFilter {

    private final AccessLogWriter accessLogWriter;
    private final AccessLogProperties properties;

    public AccessLogFilter(
            AccessLogWriter accessLogWriter,
            AccessLogProperties properties
    ) {
        this.accessLogWriter = accessLogWriter;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        return properties.getExcludePatterns().stream()
                .anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long startedAt = System.currentTimeMillis();

        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request);

        ContentCachingResponseWrapper wrappedResponse =
                new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long elapsedMs = System.currentTimeMillis() - startedAt;

            try {
                AccessLogEntry entry = AccessLogEntry.from(
                        wrappedRequest,
                        wrappedResponse,
                        elapsedMs,
                        properties
                );

                accessLogWriter.write(entry);
            } finally {
                wrappedResponse.copyBodyToResponse();
            }
        }
    }
}
```

주의사항:

`ContentCachingResponseWrapper`를 사용하는 경우 반드시 `copyBodyToResponse()`를 호출해야 한다. 그렇지 않으면 클라이언트가 응답 body를 받지 못할 수 있다.

---

## 8. AccessLogEntry 설계

```java
public record AccessLogEntry(
        String logType,
        String traceId,
        String method,
        String uri,
        String queryString,
        String clientIp,
        String userAgent,
        int status,
        long elapsedMs,
        RequestLog request,
        ResponseLog response
) {
    public static AccessLogEntry from(
            ContentCachingRequestWrapper request,
            ContentCachingResponseWrapper response,
            long elapsedMs,
            AccessLogProperties properties
    ) {
        return new AccessLogEntry(
                "access",
                MDC.get(TraceConstants.TRACE_ID),
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                resolveClientIp(request),
                request.getHeader("User-Agent"),
                response.getStatus(),
                elapsedMs,
                RequestLog.from(request, properties),
                ResponseLog.from(response, properties)
        );
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
```

---

## 9. RequestLog / ResponseLog 설계

### 9.1 RequestLog

```java
public record RequestLog(
        Map<String, String> headers,
        String body
) {
    public static RequestLog from(
            ContentCachingRequestWrapper request,
            AccessLogProperties properties
    ) {
        return new RequestLog(
                HeaderExtractor.extract(request, properties),
                BodyExtractor.extractRequestBody(request, properties)
        );
    }
}
```

### 9.2 ResponseLog

```java
public record ResponseLog(
        Map<String, String> headers,
        String body
) {
    public static ResponseLog from(
            ContentCachingResponseWrapper response,
            AccessLogProperties properties
    ) {
        return new ResponseLog(
                HeaderExtractor.extract(response, properties),
                BodyExtractor.extractResponseBody(response, properties)
        );
    }
}
```

---

## 10. Exception 처리 설계

### 10.1 기본 방향

Exception 상세는 access log에 포함하지 않는다.

대신 `GlobalExceptionHandler`에서 다음을 수행한다.

1. exception을 적절한 `ErrorResponse`로 변환
2. `APP_ERROR` logger로 exception 상세 기록
3. error response에 traceId 포함
4. AccessLogFilter는 최종 response를 access log에 남김

### 10.2 ErrorResponse 예시

```java
public record ErrorResponse(
        String code,
        String message,
        String traceId
) {
}
```

### 10.3 GlobalExceptionHandler 예시

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger errorLog =
            LoggerFactory.getLogger("APP_ERROR");

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {

        String traceId = MDC.get(TraceConstants.TRACE_ID);

        errorLog.error(
                "Unhandled exception. traceId={}, exceptionType={}, message={}",
                traceId,
                e.getClass().getName(),
                e.getMessage(),
                e
        );

        ErrorResponse response = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Internal server error",
                traceId
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
```

---

## 11. 예외 유형별 로그 정책

모든 예외를 error level로 남기면 운영 로그 노이즈가 커진다. 예외 성격에 따라 로그 레벨을 다르게 가져가는 것이 좋다.

| 예외 유형 | HTTP status | Application log 정책 |
|---|---:|---|
| Validation error | 400 | 생략 또는 warn |
| Business exception | 400 / 409 / 422 | info 또는 warn |
| Authentication error | 401 | warn |
| Authorization error | 403 | warn |
| Unexpected exception | 500 | error + stack trace |
| External API failure | 502 / 503 / 504 | warn 또는 error |

### Validation 예외 처리 예시

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException e
) {
    ErrorResponse response = new ErrorResponse(
            "INVALID_REQUEST",
            "Invalid request",
            MDC.get(TraceConstants.TRACE_ID)
    );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
}
```

---

## 12. 설정값 설계

### 12.1 application.yml 예시

```yaml
access-log:
  enabled: true
  include-request-headers: true
  include-request-body: true
  include-response-headers: true
  include-response-body: true
  max-body-length: 5000
  exclude-patterns:
    - /actuator
    - /health
  masked-headers:
    - authorization
    - cookie
    - set-cookie
    - x-api-key
  masked-fields:
    - password
    - token
    - accessToken
    - refreshToken
    - secret
  excluded-content-types:
    - multipart/
    - text/event-stream
    - application/octet-stream
    - application/pdf
    - image/
    - video/
```

### 12.2 AccessLogProperties 예시

```java
@ConfigurationProperties(prefix = "access-log")
public class AccessLogProperties {

    private boolean enabled = true;
    private boolean includeRequestHeaders = true;
    private boolean includeRequestBody = true;
    private boolean includeResponseHeaders = true;
    private boolean includeResponseBody = true;
    private int maxBodyLength = 5000;

    private List<String> excludePatterns = List.of(
            "/actuator",
            "/health"
    );

    private List<String> maskedHeaders = List.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key"
    );

    private List<String> maskedFields = List.of(
            "password",
            "token",
            "accessToken",
            "refreshToken",
            "secret"
    );

    private List<String> excludedContentTypes = List.of(
            "multipart/",
            "text/event-stream",
            "application/octet-stream",
            "application/pdf",
            "image/",
            "video/"
    );

    // getters/setters
}
```

---

## 13. AccessLogWriter 설계

### 13.1 Interface

```java
public interface AccessLogWriter {
    void write(AccessLogEntry entry);
}
```

### 13.2 JSON Writer

```java
public class JsonAccessLogWriter implements AccessLogWriter {

    private static final Logger log = LoggerFactory.getLogger("ACCESS_LOG");

    private final ObjectMapper objectMapper;

    public JsonAccessLogWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(AccessLogEntry entry) {
        try {
            log.info(objectMapper.writeValueAsString(entry));
        } catch (Exception e) {
            log.warn("Failed to write access log", e);
        }
    }
}
```

---

## 14. Auto Configuration

Spring Boot starter 형태로 제공하면 각 서비스에서는 dependency와 설정만 추가하면 된다.

### 14.1 AutoConfiguration 예시

```java
@AutoConfiguration
@EnableConfigurationProperties(AccessLogProperties.class)
@ConditionalOnProperty(
        prefix = "access-log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AccessLogAutoConfiguration {

    @Bean
    public AccessLogWriter accessLogWriter(ObjectMapper objectMapper) {
        return new JsonAccessLogWriter(objectMapper);
    }

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>();

        registration.setFilter(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");

        return registration;
    }

    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilter(
            AccessLogWriter accessLogWriter,
            AccessLogProperties properties
    ) {
        FilterRegistrationBean<AccessLogFilter> registration =
                new FilterRegistrationBean<>();

        registration.setFilter(new AccessLogFilter(accessLogWriter, properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");

        return registration;
    }
}
```

### 14.2 Spring Boot 3 AutoConfiguration 등록

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

파일 내용:

```text
com.example.accesslog.AccessLogAutoConfiguration
```

### 14.3 Spring Boot 2 지원이 필요한 경우

Spring Boot 2를 지원해야 한다면 다음 파일을 사용한다.

```text
META-INF/spring.factories
```

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.example.accesslog.AccessLogAutoConfiguration
```

---

## 15. Logback 설정 예시

Access log와 application error log의 logger name을 분리한다.

```xml
<logger name="ACCESS_LOG" level="INFO" additivity="false">
    <appender-ref ref="ACCESS_LOG_APPENDER"/>
</logger>

<logger name="APP_ERROR" level="INFO" additivity="false">
    <appender-ref ref="APPLICATION_APPENDER"/>
</logger>
```

JSON 로그를 사용하는 경우 MDC의 `traceId`가 모든 로그에 포함되도록 encoder 설정을 구성한다.

예시 로그 구조:

```json
{
  "timestamp": "2026-06-05T10:15:30.123+09:00",
  "level": "ERROR",
  "logger": "APP_ERROR",
  "traceId": "7f3b2a9c1e4d",
  "message": "Unhandled exception..."
}
```

---

## 16. 민감 정보 마스킹

Request/response body를 access log에 남기는 경우 마스킹은 필수다.

### 16.1 마스킹 대상

| 위치 | 대상 |
|---|---|
| Header | Authorization, Cookie, Set-Cookie, X-Api-Key |
| Request Body | password, token, accessToken, refreshToken, secret |
| Response Body | 개인정보, 인증 토큰, 내부 보안 정보 |
| Query String | token, password, key |

### 16.2 JSON Body Masker 예시

```java
public class JsonBodyMasker {

    private final ObjectMapper objectMapper;
    private final Set<String> maskedFields;

    public JsonBodyMasker(ObjectMapper objectMapper, List<String> maskedFields) {
        this.objectMapper = objectMapper;
        this.maskedFields = maskedFields.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public String mask(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            maskNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return body;
        }
    }

    private void maskNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode child = objectNode.get(fieldName);

                if (maskedFields.contains(fieldName.toLowerCase())) {
                    objectNode.put(fieldName, "***");
                } else {
                    maskNode(child);
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskNode);
        }
    }
}
```

주의: JSON 파싱에 실패한 body를 그대로 남길 경우 민감 정보가 노출될 수 있다. 운영 환경에서는 JSON 파싱 실패 시 원문을 남기지 않는 정책도 고려해야 한다.

---

## 17. 제외해야 할 요청/응답

다음 유형은 body logging 대상에서 제외하는 것이 안전하다.

| 유형 | 이유 |
|---|---|
| multipart/form-data | 파일 내용이 로그에 남을 수 있음 |
| text/event-stream | streaming 응답 |
| application/octet-stream | binary 응답 |
| application/pdf | 대용량/binary 응답 |
| image/* | binary 응답 |
| video/* | binary 응답 |
| file download API | 로그 용량 급증 |

예시 표현:

```json
{
  "request": {
    "body": "[multipart omitted]"
  }
}
```

```json
{
  "response": {
    "body": "[binary omitted]"
  }
}
```

---

## 18. 주의사항

### 18.1 ContentCachingRequestWrapper

`ContentCachingRequestWrapper`는 request body를 자동으로 미리 읽지 않는다. 실제로 downstream에서 읽은 body만 cache된다.

일반적인 `@RequestBody` API는 문제 없지만, body를 읽지 않는 요청에서는 body가 비어 있을 수 있다.

### 18.2 ContentCachingResponseWrapper

`ContentCachingResponseWrapper`를 사용하면 반드시 다음을 호출해야 한다.

```java
wrappedResponse.copyBodyToResponse();
```

누락 시 클라이언트 응답 body가 비어 있을 수 있다.

### 18.3 Error Response 생성 위치

Exception 발생 시 response body를 access log에 남기려면 `GlobalExceptionHandler`에서 일관된 `ErrorResponse`를 반환해야 한다.

Exception이 처리되지 않고 WAS까지 전파되면 response body가 비어 있거나 Spring Boot 기본 error response 형태로 남을 수 있다.

### 18.4 로그 크기 제한

`max-body-length` 제한은 필수다.

권장 기본값:

```yaml
access-log:
  max-body-length: 5000
```

응답 body가 큰 API는 endpoint 단위로 body logging 제외 옵션을 두는 것이 좋다.

---

## 19. 운영 관점 권장사항

### 19.1 로그 조회 기준

장애 분석 시 기본 조회 흐름:

```text
traceId로 검색
  -> ACCESS_LOG에서 request/response 확인
  -> APP_ERROR에서 exception stack trace 확인
  -> 필요 시 application business log 확인
```

### 19.2 로그 레벨 정책

| Logger | Level | 용도 |
|---|---|---|
| ACCESS_LOG | INFO | 모든 API 호출 기록 |
| APP_ERROR | WARN / ERROR | 예외 및 장애 분석 |
| Application Logger | DEBUG / INFO / WARN / ERROR | 비즈니스 흐름 기록 |

### 19.3 TraceId 응답 포함

ErrorResponse에 traceId를 포함하면 클라이언트 문의, CS, 장애 대응 시 유용하다.

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Internal server error",
  "traceId": "7f3b2a9c1e4d"
}
```

---

## 20. 최종 결론

본 설계에서는 access log와 exception log의 책임을 분리한다.

```text
Access Log
  - HTTP request/response transaction 기록
  - 정상/예외 여부와 관계없이 request + response pair 유지
  - API 통계, latency 분석, 요청/응답 추적에 사용

Application Error Log
  - exception 상세 기록
  - stack trace 포함
  - 장애 원인 분석에 사용

TraceId
  - access log와 application error log를 연결하는 공통 key
```

이 구조는 다음 측면에서 유리하다.

- access log의 구조가 일관된다.
- API 호출 통계와 장애 분석이 분리된다.
- stack trace로 인한 access log 비대화를 막을 수 있다.
- 동일 traceId로 request, response, exception을 쉽게 grouping할 수 있다.
- 운영 로그 시스템에서 검색과 집계가 쉬워진다.
