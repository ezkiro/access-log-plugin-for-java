package com.example.accesslog.mask;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSON body의 지정된 field를 재귀적으로 마스킹한다.
 *
 * <p>field 이름 비교는 대소문자를 무시한다. JSON 파싱에 실패하면 원본을 그대로 반환하지 않고
 * 마스킹된 placeholder를 반환하여 민감 정보 노출을 방지한다.
 */
public class JsonBodyMasker implements BodyMasker {

    private static final String MASK = "***";

    /** JSON 파싱 실패 시 노출을 막기 위한 placeholder. */
    private static final String UNPARSEABLE = "[unparseable body masked]";

    private final ObjectMapper objectMapper;
    private final Set<String> maskedFields;

    public JsonBodyMasker(ObjectMapper objectMapper, List<String> maskedFields) {
        this.objectMapper = objectMapper;
        this.maskedFields = maskedFields.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    @Override
    public String mask(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }

        if (maskedFields.isEmpty()) {
            return body;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            maskNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            // 파싱 실패한 body를 원문으로 남기면 민감 정보가 노출될 수 있으므로 마스킹한다.
            return UNPARSEABLE;
        }
    }

    private void maskNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List.copyOf(objectNode.propertyNames()).forEach(fieldName -> {
                if (maskedFields.contains(fieldName.toLowerCase())) {
                    objectNode.put(fieldName, MASK);
                } else {
                    maskNode(objectNode.get(fieldName));
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::maskNode);
        }
    }
}
