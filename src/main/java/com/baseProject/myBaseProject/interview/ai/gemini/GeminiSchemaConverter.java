package com.baseProject.myBaseProject.interview.ai.gemini;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GeminiSchemaConverter {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private GeminiSchemaConverter() {
    }

    public static String toSpringAiSchema(JsonMapper jsonMapper, String schemaContract) {
        Map<String, Object> schema = jsonMapper.readValue(schemaContract, JSON_OBJECT);
        normalizeSchema(schema);
        return jsonMapper.writeValueAsString(schema);
    }

    @SuppressWarnings("unchecked")
    private static void normalizeSchema(Map<String, Object> schema) {
        schema.remove("additionalProperties");

        Object type = schema.get("type");
        if (type instanceof List<?> types) {
            List<Object> nonNullTypes = new ArrayList<>();
            boolean nullable = false;
            for (Object value : types) {
                if ("null".equals(value)) {
                    nullable = true;
                } else {
                    nonNullTypes.add(value);
                }
            }
            schema.put("type", nonNullTypes.size() == 1 ? nonNullTypes.get(0) : nonNullTypes);
            if (nullable) {
                schema.put("nullable", true);
            }
        }

        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> propertyMap) {
            for (Object child : propertyMap.values()) {
                if (child instanceof Map<?, ?> childSchema) {
                    normalizeSchema((Map<String, Object>) childSchema);
                }
            }
        }
        Object items = schema.get("items");
        if (items instanceof Map<?, ?> itemSchema) {
            normalizeSchema((Map<String, Object>) itemSchema);
        }
    }
}
