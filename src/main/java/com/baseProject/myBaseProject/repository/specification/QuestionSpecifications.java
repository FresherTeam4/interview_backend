package com.baseProject.myBaseProject.repository.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.baseProject.myBaseProject.dto.question.QuestionFilter;
import com.baseProject.myBaseProject.entity.Question;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class QuestionSpecifications {
    private static final char LIKE_ESCAPE = '\\';

    private QuestionSpecifications() {
    }

    public static Specification<Question> withFilter(QuestionFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            String keyword = normalizeKeyword(filter.keyword());
            if (keyword != null) {
                String pattern = "%" + escapeLike(keyword) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("contentVi")),
                                pattern,
                                LIKE_ESCAPE
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("contentEn")),
                                pattern,
                                LIKE_ESCAPE
                        )
                ));
            }

            if (filter.active() != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), filter.active()));
            }
            if (filter.techStackId() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("techStack").get("id"),
                        filter.techStackId()
                ));
            } else if (Boolean.TRUE.equals(filter.unclassified())) {
                predicates.add(criteriaBuilder.isNull(root.get("techStack")));
            }
            if (filter.level() != null) {
                predicates.add(criteriaBuilder.equal(root.get("level"), filter.level()));
            }
            if (filter.questionType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("questionType"), filter.questionType()));
            }
            if (filter.difficulty() != null) {
                predicates.add(criteriaBuilder.equal(root.get("difficulty"), filter.difficulty()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
