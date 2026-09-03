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

import java.time.Instant;

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
            String jobDescriptionHash,
            Instant now) {
        SessionContextSnapshot snapshot = new SessionContextSnapshot();
        snapshot.session = session;
        snapshot.snapshotSchemaVersion = snapshotSchemaVersion.strip();
        snapshot.profileJson = profileJson.deepCopy();
        snapshot.jobDescriptionText = jobDescriptionText;
        snapshot.jobDescriptionHash = jobDescriptionHash;
        snapshot.createdAt = now;
        return snapshot;
    }
}
