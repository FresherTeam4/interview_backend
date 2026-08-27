package com.baseProject.myBaseProject.entity;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(
        name = "session_context_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_session_context_snapshots_session",
                        columnNames = "session_id")
        }
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionContextSnapshot {

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true, updatable = false)
    private InterviewSession session;

    @Column(name = "snapshot_schema_version", nullable = false, length = 20, updatable = false)
    private String snapshotSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_json", nullable = false, updatable = false, columnDefinition = "JSON")
    private JsonNode profileJson;

    @Column(
            name = "job_description_text",
            nullable = false,
            updatable = false,
            columnDefinition = "MEDIUMTEXT")
    private String jobDescriptionText;

    @Column(
            name = "job_description_hash",
            nullable = false,
            length = 64,
            updatable = false,
            columnDefinition = "CHAR(64)")
    private String jobDescriptionHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SessionContextSnapshot create(
            InterviewSession session,
            String snapshotSchemaVersion,
            JsonNode profileJson,
            String jobDescriptionText,
            Instant now) {
        validateProfileJson(profileJson);
        if (snapshotSchemaVersion == null
                || snapshotSchemaVersion.isBlank()
                || snapshotSchemaVersion.length() > 20) {
            throw new IllegalArgumentException("Snapshot schema version must contain 1-20 characters");
        }
        if (jobDescriptionText == null || jobDescriptionText.isBlank()) {
            throw new IllegalArgumentException("Job description snapshot must not be blank");
        }

        SessionContextSnapshot snapshot = new SessionContextSnapshot();
        snapshot.session = session;
        snapshot.snapshotSchemaVersion = snapshotSchemaVersion.strip();
        snapshot.profileJson = profileJson.deepCopy();
        snapshot.jobDescriptionText = jobDescriptionText;
        snapshot.jobDescriptionHash = sha256Hex(normalizeLineEndings(jobDescriptionText));
        snapshot.createdAt = now;
        return snapshot;
    }

    private static void validateProfileJson(JsonNode profileJson) {
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

    private static void rejectSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (FORBIDDEN_FIELD_FRAGMENTS.stream().anyMatch(normalized::contains)) {
                    throw new IllegalArgumentException("Profile snapshot contains a sensitive field");
                }
                rejectSensitiveFields(node.get(name));
            }
        } else if (node.isArray()) {
            node.forEach(SessionContextSnapshot::rejectSensitiveFields);
        }
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but not available", exception);
        }
    }
}
