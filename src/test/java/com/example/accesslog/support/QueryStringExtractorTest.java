package com.example.accesslog.support;

import com.example.accesslog.AccessLogProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryStringExtractorTest {

    @Test
    void masksRepeatedAndEncodedParameterNamesCaseInsensitively() {
        AccessLogProperties properties = new AccessLogProperties();
        properties.setMaskedQueryParameters(List.of("token", "accessToken"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("token=one&TOKEN=two&access%54oken=three&plain=value");

        String queryString = QueryStringExtractor.extract(request, properties);

        assertThat(queryString)
                .isEqualTo("token=***&TOKEN=***&access%54oken=***&plain=value")
                .doesNotContain("one", "two", "three");
    }

    @Test
    void canDisableQueryStringLogging() {
        AccessLogProperties properties = new AccessLogProperties();
        properties.setIncludeQueryString(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("token=secret");

        assertThat(QueryStringExtractor.extract(request, properties)).isNull();
    }

    @Test
    void keepsMalformedAndUnmaskedPartsWithoutThrowing() {
        AccessLogProperties properties = new AccessLogProperties();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("%ZZ=value&plain&empty=");

        assertThat(QueryStringExtractor.extract(request, properties))
                .isEqualTo("%ZZ=value&plain&empty=");
    }
}
