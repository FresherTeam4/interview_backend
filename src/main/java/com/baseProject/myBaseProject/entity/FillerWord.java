package com.baseProject.myBaseProject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Filler words to count, kept in the database rather than hardcoded so the list can be tuned
 * without a redeploy.
 */
@Entity
@Table(
        name = "filler_word_dictionary",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_filler_word_dictionary_word",
                columnNames = {"language_code", "word"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FillerWord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    /** um, uh, like, you know, ... */
    @Column(nullable = false, length = 50)
    private String word;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
