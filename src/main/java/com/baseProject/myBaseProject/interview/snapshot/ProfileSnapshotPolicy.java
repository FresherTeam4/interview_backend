package com.baseProject.myBaseProject.interview.snapshot;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
public class ProfileSnapshotPolicy {

    private static final Set<String> ALLOWED_PROFILE_FIELDS = Set.of(
            "headline",
            "targetPosition",
            "seniorityLevel",
            "yearsExperience",
            "educations",
            "skills",
            "projects");
    private static final Set<String> FORBIDDEN_FIELD_FRAGMENTS = Set.of(
            "email",
            "password",
            "token",
            "secret",
            "storagekey",
            "googleid",
            "rawcv",
            "filebytes",
            "binary");

    /** Kiểm tra snapshot chỉ chứa dữ liệu profile được phép gửi vào Interview Engine. */
    public void validate(JsonNode profileJson) {
        if (profileJson == null || !profileJson.isObject()) {
            throw new IllegalArgumentException("Profile snapshot must be a JSON object");
        }

        Set<String> rootFields = new HashSet<>();
        profileJson.fieldNames().forEachRemaining(rootFields::add);
        if (!ALLOWED_PROFILE_FIELDS.containsAll(rootFields)) {
            throw new IllegalArgumentException("Profile snapshot contains unsupported root fields");
        }
        rejectSensitiveFields(profileJson);
    }

    /** Duyệt toàn bộ cây JSON và chặn tên field có dấu hiệu chứa dữ liệu nhạy cảm. */
    private void rejectSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (FORBIDDEN_FIELD_FRAGMENTS.stream().anyMatch(normalized::contains)) {
                    throw new IllegalArgumentException(
                            "Profile snapshot contains a sensitive field");
                }
                rejectSensitiveFields(node.get(name));
            }
        } else if (node.isArray()) {
            node.forEach(this::rejectSensitiveFields);
        }
    }
}
