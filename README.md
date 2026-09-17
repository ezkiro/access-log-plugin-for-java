# common-web-logging-starter

Spring Boot 3 기반 REST API access log 공용 모듈. [spring-boot-access-log-design.md](spring-boot-access-log-design.md) 설계안 구현체.

```
Access Log  = request + response + status + elapsedMs
Error Log   = exception detail + stack trace
Grouping    = same traceId
```

Access log는 HTTP transaction(request/response pair)을 기록하고, 예외 상세는 별도 application error log로 분리한다. 두 로그는 동일한 `traceId`로 grouping 된다.

## 요구 사항

- Java 17+
- Spring Boot 3.x (servlet 웹 애플리케이션)

## 빌드

```bash
./gradlew build          # 컴파일 + 테스트
./gradlew publishToMavenLocal   # 로컬 maven 저장소에 설치
```

## 사용 방법

### 1) 의존성 추가

```gradle
dependencies {
    implementation 'com.example:common-web-logging-starter:0.1.0'
}
```

starter이므로 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`를 통해 자동 등록된다. 별도 `@Import` 불필요.

### 2) 설정 (application.yml)

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
  # 기본 제공 GlobalExceptionHandler 사용 시 (opt-in, 기본 false)
  exception-handler:
    enabled: false
```

### 3) logback logger 분리

[docs/logback-spring-example.xml](docs/logback-spring-example.xml) 참고. `ACCESS_LOG` / `APP_ERROR` logger를 분리하여 별도 appender로 라우팅한다.

## 구성 요소

| 클래스 | 역할 |
|---|---|
| `TraceIdFilter` | `X-Trace-Id` 확인/생성, MDC 저장, 응답 헤더 반영 (가장 높은 우선순위) |
| `AccessLogFilter` | request/response body capture, elapsed time 측정, access log 기록 |
| `AccessLogProperties` | `access-log.*` 설정 바인딩 |
| `AccessLogEntry` / `RequestLog` / `ResponseLog` | access log 구조 |
| `HeaderExtractor` / `BodyExtractor` | header/body 추출, 마스킹·content-type 제외·truncate |
| `BodyMasker` / `JsonBodyMasker` | JSON body 민감 필드 재귀 마스킹 |
| `AccessLogWriter` / `JsonAccessLogWriter` | `ACCESS_LOG` logger에 JSON 한 줄 기록 |
| `AccessLogAutoConfiguration` | 필터/빈 자동 등록 (모든 빈 `@ConditionalOnMissingBean`) |
| `ErrorResponse` / `GlobalExceptionHandler` | traceId 포함 일관 에러 응답 + `APP_ERROR` 기록 (opt-in) |

## 커스터마이징

모든 빈은 `@ConditionalOnMissingBean`으로 등록되므로 서비스에서 동일 타입 빈을 정의하면 대체된다.

```java
@Bean
AccessLogWriter myAccessLogWriter(ObjectMapper om) {
    return entry -> kafkaTemplate.send("access-log", om.writeValueAsString(entry));
}
```

## 설계와의 차이 / 구현 메모

- `request.body` / `response.body`는 설계의 record 정의대로 **String**으로 기록된다. JSON 본문은 JSON 문자열로 직렬화된다(중첩 객체로 풀지 않음).
- JSON 파싱 실패 body는 원문을 남기지 않고 `[unparseable body masked]` placeholder로 대체하여 민감 정보 노출을 방지한다(설계 16장 운영 권장 정책 반영).
- binary/대용량 content-type은 `[multipart omitted]` / `[binary omitted]`로 대체한다.
- 기본 `GlobalExceptionHandler`는 서비스 advice와의 충돌을 피하기 위해 `access-log.exception-handler.enabled=true` opt-in이며 `@ConditionalOnMissingBean`이다.
```
