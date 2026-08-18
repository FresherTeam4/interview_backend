package com.baseProject.myBaseProject.repository.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.baseProject.myBaseProject.dto.question.QuestionFilter;
import com.baseProject.myBaseProject.entity.Question;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class QuestionSpecifications {
    private static final char LIKE_ESCAPE = '\\';

    private QuestionSpecifications() {
    }

    public static Specification<Question> withFilter(QuestionFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            query.distinct(true);

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
            if (hasValues(filter.techStackIds())) {
                predicates.add(root.join("techStacks", JoinType.INNER)
                        .get("id")
                        .in(filter.techStackIds()));
            } else if (Boolean.TRUE.equals(filter.unclassified())) {
                predicates.add(criteriaBuilder.isEmpty(root.get("techStacks")));
            }
            if (hasValues(filter.technologyIds())) {
                predicates.add(root.join("technologies", JoinType.INNER)
                        .get("id")
                        .in(filter.technologyIds()));
            }
            if (hasValues(filter.levels())) {
                predicates.add(root.get("level").in(filter.levels()));
            }
            if (hasValues(filter.questionTypes())) {
                predicates.add(root.get("questionType").in(filter.questionTypes()));
            }
            if (hasValues(filter.difficulties())) {
                predicates.add(root.get("difficulty").in(filter.difficulties()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasValues(List<?> values) {
        return values != null && !values.isEmpty();
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
