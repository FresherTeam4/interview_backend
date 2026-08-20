package com.baseProject.myBaseProject.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.baseProject.myBaseProject.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface QuestionRepository extends JpaRepository<Question, Long>,
        JpaSpecificationExecutor<Question> {
    Optional<Question> findByContentFingerprint(String contentFingerprint);

    List<Question> findAllByContentFingerprintIn(Collection<String> contentFingerprints);

    @Override
    @EntityGraph(attributePaths = {"techStacks", "technologies", "createdBy"})
    Optional<Question> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"createdBy"})
    Page<Question> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"createdBy"})
    Page<Question> findAll(Specification<Question> specification, Pageable pageable);
}
