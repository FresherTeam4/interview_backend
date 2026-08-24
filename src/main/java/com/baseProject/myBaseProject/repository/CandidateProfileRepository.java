package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CandidateProfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    /**
     * Every profile the user can still pick from, newest first — one per CV they kept.
     *
     * <p>The {@code CvDocumentActiveTrue} part is not decoration: removing a CV is a soft
     * delete, so its profile row survives for old sessions but must disappear from the
     * user's list and from the interview picker. Filtering here is what makes that true
     * everywhere instead of at each call site.
     */
    List<CandidateProfile> findByUserIdAndCvDocumentActiveTrueOrderByCreatedAtDesc(Long userId);

    /**
     * Ownership-scoped fetch behind read, edit and confirm. Also the gate an interview
     * session checks: empty means 404, present but {@link CandidateProfile#isConfirmed()}
     * false means the user has not pressed "Information is correct" yet — two different
     * answers, which is why this returns the row rather than a boolean.
     */
    Optional<CandidateProfile> findByIdAndUserIdAndCvDocumentActiveTrue(Long id, Long userId);

    /**
     * Guard before building a profile from a finished parse. The database already forbids
     * a second profile for the same CV ({@code uq_candidate_profiles_cv_document}); this
     * turns that into a readable failure instead of a constraint violation if a parse job
     * ever runs twice for one document.
     */
    boolean existsByCvDocumentId(Long cvDocumentId);
}
