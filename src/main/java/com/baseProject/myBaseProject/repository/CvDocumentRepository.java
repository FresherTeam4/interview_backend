package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvDocumentRepository extends JpaRepository<CvDocument, Long> {

    /**
     * Every CV the user still keeps, newest first. Feeds the "my CVs" list, the CV picker
     * shown when starting an interview, and the parsing-status poll.
     *
     * <p>{@code active} here means "not removed by the user" — removal is a soft delete,
     * because past interview sessions must keep pointing at the CV they ran against.
     */
    List<CvDocument> findByUserIdAndActiveTrueOrderByUploadedAtDesc(Long userId);

    /**
     * Ownership-scoped fetch for everything the user acts on: status poll, re-parse,
     * delete. A stray id — someone else's, or one already removed — comes back empty so
     * the caller answers 404 without leaking whether that id exists.
     */
    Optional<CvDocument> findByIdAndUserIdAndActiveTrue(Long id, Long userId);

    /**
     * Same ownership check but ignoring the soft-delete flag, used only to hand out a
     * download link. A session run months ago must still be able to show the CV behind
     * it, even after the user removed that CV from their list.
     */
    Optional<CvDocument> findByIdAndUserId(Long id, Long userId);

    /** Enforces the per-user cap on kept CVs before accepting another upload. */
    long countByUserIdAndActiveTrue(Long userId);

    Optional<CvDocument> findFirstByUserIdAndChecksumSha256AndStatusOrderByUploadedAtDesc(
            Long userId, String checksumSha256, CvDocumentStatus status);

    /**
     * Used once at startup to clear rows stranded in {@code PARSING} by a restart: the
     * async job that owned them died with the previous process, so nothing will ever
     * finish them. Safe as an unconditional sweep only because a single instance is
     * assumed — a second instance booting would trample the first one's live parses.
     */
    List<CvDocument> findByStatus(CvDocumentStatus status);
}
