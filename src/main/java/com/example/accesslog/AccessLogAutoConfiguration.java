package com.example.accesslog;

import com.example.accesslog.error.GlobalExceptionHandler;
import com.example.accesslog.mask.BodyMasker;
import com.example.accesslog.mask.JsonBodyMasker;
import com.example.accesslog.trace.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Access log 모듈 auto-configuration.
 *
 * <p>servlet 웹 애플리케이션에서 {@code access-log.enabled=true}(기본값)일 때
 * trace 필터와 access log 필터, writer, masker 를 등록한다.
 * 모든 빈은 {@code @ConditionalOnMissingBean}으로 등록되어 서비스에서 커스터마이징할 수 있다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@EnableConfigurationProperties(AccessLogProperties.class)
@ConditionalOnProperty(
        prefix = "access-log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AccessLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper accessLogObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public BodyMasker bodyMasker(ObjectMapper objectMapper, AccessLogProperties properties) {
        return new JsonBodyMasker(objectMapper, properties.getMaskedFields());
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessLogWriter accessLogWriter(ObjectMapper objectMapper) {
        return new JsonAccessLogWriter(objectMapper);
    }

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilterRegistration(
            AccessLogWriter accessLogWriter,
            AccessLogProperties properties,
            BodyMasker bodyMasker
    ) {
        FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AccessLogFilter(accessLogWriter, properties, bodyMasker));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * 기본 GlobalExceptionHandler. opt-in 이며({@code access-log.exception-handler.enabled=true}),
     * 서비스가 자체 advice를 제공하면 등록되지 않는다.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "access-log.exception-handler",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public GlobalExceptionHandler accessLogGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
