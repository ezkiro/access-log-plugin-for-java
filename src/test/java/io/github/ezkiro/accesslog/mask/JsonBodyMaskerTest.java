package io.github.ezkiro.accesslog.mask;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonBodyMaskerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BodyMasker masker = new JsonBodyMasker(
            objectMapper,
            List.of("password", "token", "accessToken", "secret")
    );

    @Test
    void masksTopLevelFieldCaseInsensitively() {
        String body = "{\"name\":\"kim\",\"Password\":\"1234\"}";

        String masked = masker.mask(body);

        assertThat(masked).contains("\"name\":\"kim\"");
        assertThat(masked).contains("\"Password\":\"***\"");
        assertThat(masked).doesNotContain("1234");
    }

    @Test
    void masksNestedAndArrayFields() {
        String body = "{\"user\":{\"token\":\"abc\"},\"items\":[{\"secret\":\"s1\"},{\"secret\":\"s2\"}]}";

        String masked = masker.mask(body);

        assertThat(masked).doesNotContain("abc");
        assertThat(masked).doesNotContain("s1");
        assertThat(masked).doesNotContain("s2");
        assertThat(masked).contains("\"token\":\"***\"");
        assertThat(masked).contains("\"secret\":\"***\"");
    }

    @Test
    void returnsPlaceholderForUnparseableBody() {
        String body = "not-a-json password=1234";

        String masked = masker.mask(body);

        assertThat(masked).isEqualTo("[unparseable body masked]");
        assertThat(masked).doesNotContain("1234");
    }

    @Test
    void returnsBlankAndNullAsIs() {
        assertThat(masker.mask(null)).isNull();
        assertThat(masker.mask("")).isEmpty();
    }
}
