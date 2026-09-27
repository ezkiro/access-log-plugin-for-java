package io.github.ezkiro.accesslog.mask;

/**
 * Body 문자열의 민감 정보를 마스킹하는 전략.
 */
public interface BodyMasker {

    /**
     * 주어진 body의 민감 필드를 마스킹한 결과를 반환한다.
     *
     * @param body 원본 body (null 가능)
     * @return 마스킹된 body
     */
    String mask(String body);
}
