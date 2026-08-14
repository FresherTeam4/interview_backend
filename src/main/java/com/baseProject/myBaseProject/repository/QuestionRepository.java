package com.baseProject.myBaseProject.repository;

import java.util.Optional;

import com.baseProject.myBaseProject.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    @Override
    @EntityGraph(attributePaths = {"techStack", "createdBy"})
    Optional<Question> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"techStack", "createdBy"})
    Page<Question> findAll(Pageable pageable);
}
