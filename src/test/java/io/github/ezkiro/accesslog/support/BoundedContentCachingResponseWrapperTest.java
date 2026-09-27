package io.github.ezkiro.accesslog.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedContentCachingResponseWrapperTest {

    @Test
    void forwardsFullResponseWhileCachingOnlyUpToLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        BoundedContentCachingResponseWrapper wrapper =
                new BoundedContentCachingResponseWrapper(response, 4);

        wrapper.getOutputStream().write("abcdef".getBytes(StandardCharsets.UTF_8));
        wrapper.flushCapturedContent();

        assertThat(response.getContentAsString()).isEqualTo("abcdef");
        assertThat(new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo("abcd");
        assertThat(wrapper.isOverflowed()).isTrue();
    }

    @Test
    void returnsSameWriterAndCapturesWrittenContent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        BoundedContentCachingResponseWrapper wrapper =
                new BoundedContentCachingResponseWrapper(response, 16);

        assertThat(wrapper.getWriter()).isSameAs(wrapper.getWriter());
        wrapper.getWriter().write("hello");
        wrapper.flushCapturedContent();

        assertThat(response.getContentAsString()).isEqualTo("hello");
        assertThat(new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(wrapper.isOverflowed()).isFalse();
    }
}
