package com.baseProject.myBaseProject.ai.gemini;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/** Provider-specific mechanics shared by Gemini adapters; business failures stay in each adapter. */
public final class GeminiAdapterSupport {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private GeminiAdapterSupport() {
    }

    public enum FailureKind {
        TIMEOUT,
        RATE_LIMITED,
        CREDENTIAL_REJECTED,
        CLIENT_ERROR,
        SERVER_ERROR,
        API_ERROR,
        NETWORK_ERROR,
        UNEXPECTED
    }

    public record ClassifiedFailure(FailureKind kind, int statusCode) {
    }

    public static ClassifiedFailure classify(RuntimeException exception) {
        ClientException clientError = findCause(exception, ClientException.class);
        if (clientError != null) {
            if (clientError.code() == 408) {
                return new ClassifiedFailure(FailureKind.TIMEOUT, 408);
            }
            if (clientError.code() == 429) {
                return new ClassifiedFailure(FailureKind.RATE_LIMITED, 429);
            }
            if (clientError.code() == 401 || clientError.code() == 403) {
                return new ClassifiedFailure(
                        FailureKind.CREDENTIAL_REJECTED, clientError.code());
            }
            return new ClassifiedFailure(FailureKind.CLIENT_ERROR, clientError.code());
        }

        ServerException serverError = findCause(exception, ServerException.class);
        if (serverError != null) {
            return new ClassifiedFailure(FailureKind.SERVER_ERROR, serverError.code());
        }

        ApiException apiError = findCause(exception, ApiException.class);
        if (apiError != null) {
            return new ClassifiedFailure(FailureKind.API_ERROR, apiError.code());
        }

        if (findCause(exception, InterruptedIOException.class) != null
                || findCause(exception, HttpTimeoutException.class) != null
                || findCause(exception, TimeoutException.class) != null) {
            return new ClassifiedFailure(FailureKind.TIMEOUT, 0);
        }

        if (findCause(exception, GenAiIOException.class) != null) {
            return new ClassifiedFailure(FailureKind.NETWORK_ERROR, 0);
        }

        return new ClassifiedFailure(FailureKind.UNEXPECTED, 0);
    }

    public static String readClasspathResource(ResourceLoader resourceLoader, String location) {
        Resource resource = resourceLoader.getResource(location);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot read AI contract resource " + location, exception);
        }
    }

    public static String toSpringAiSchema(JsonMapper jsonMapper, String schemaContract) {
        Map<String, Object> schema = jsonMapper.readValue(schemaContract, JSON_OBJECT);
        normalizeSchema(schema);
        return jsonMapper.writeValueAsString(schema);
    }

    public static String extractModelOutput(ChatResponse response) {
        if (response == null) {
            return null;
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            return null;
        }
        return generation.getOutput().getText();
    }

    public static String stripCodeFences(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        String content = firstLineEnd < 0 ? "" : trimmed.substring(firstLineEnd + 1);
        return (content.endsWith("```")
                ? content.substring(0, content.length() - 3)
                : content).strip();
    }

    public static String extractModelName(ChatResponse response, String defaultModel) {
        if (response == null || response.getMetadata() == null) {
            return defaultModel;
        }
        String responseModel = response.getMetadata().getModel();
        return responseModel == null || responseModel.isBlank()
                ? defaultModel
                : responseModel;
    }

    public static Integer extractTotalTokens(ChatResponse response) {
        if (response == null
                || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) {
            return null;
        }
        Integer totalTokens = response.getMetadata().getUsage().getTotalTokens();
        return totalTokens == null || totalTokens == 0 ? null : totalTokens;
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
            schema.put("type", nonNullTypes.size() == 1
                    ? nonNullTypes.get(0)
                    : nonNullTypes);
            if (nullable) {
                schema.put("nullable", true);
            }
        }

        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?> values && values.contains(null)) {
            List<Object> withoutNull = values.stream()
                    .filter(value -> value != null)
                    .map(value -> (Object) value)
                    .toList();
            if (withoutNull.isEmpty()) {
                schema.remove("enum");
            } else {
                schema.put("enum", withoutNull);
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

        for (String combiner : List.of("anyOf", "allOf", "oneOf")) {
            Object branches = schema.get(combiner);
            if (branches instanceof List<?> branchList) {
                for (Object branch : branchList) {
                    if (branch instanceof Map<?, ?> branchSchema) {
                        normalizeSchema((Map<String, Object>) branchSchema);
                    }
                }
            }
        }
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return null;
    }
}
