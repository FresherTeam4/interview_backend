package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CandidateProfile;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    List<CandidateProfile> findByUserIdAndCvDocumentActiveTrueOrderByCreatedAtDesc(Long userId);
    Optional<CandidateProfile> findByIdAndUserIdAndCvDocumentActiveTrue(Long id, Long userId);

    /** Serializes the short final diversity check for scripts sharing one profile. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from CandidateProfile profile where profile.id = :profileId")
    Optional<CandidateProfile> findByIdForUpdate(@Param("profileId") Long profileId);

    /**
     * Hồ sơ của đúng một CV, để {@code GET /api/cvs/{cvId}} điền được ba trường
     * {@code profile*} trong {@code CvDocumentResponse}.
     *
     * <p>Không kiểm chủ sở hữu ở đây vì chỗ gọi đã kiểm rồi: nó tìm được {@code CvDocument}
     * bằng {@code findByIdAndUserIdAndActiveTrue}, nên CV này chắc chắn của người gọi và hồ sơ
     * duy nhất treo trên CV đó cũng vậy. Thêm {@code userId} vào đây chỉ là kiểm hai lần cùng
     * một điều.
     */
    Optional<CandidateProfile> findByCvDocumentId(Long cvDocumentId);


    @EntityGraph(attributePaths = "cvDocument")
    List<CandidateProfile> findByCvDocumentIdIn(Collection<Long> cvDocumentIds);

    /**
     * Guard before building a profile from a finished parse. The database already forbids
     * a second profile for the same CV ({@code uq_candidate_profiles_cv_document}); this
     * turns that into a readable failure instead of a constraint violation if a parse job
     * ever runs twice for one document.
     */
    boolean existsByCvDocumentId(Long cvDocumentId);
}
