package com.baseProject.myBaseProject.service.impl;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.baseProject.myBaseProject.constant.PaginationConstant;
import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.question.QuestionCreateRequest;
import com.baseProject.myBaseProject.dto.question.QuestionFilter;
import com.baseProject.myBaseProject.dto.question.QuestionResponse;
import com.baseProject.myBaseProject.dto.question.QuestionUpdateRequest;
import com.baseProject.myBaseProject.dto.question.TechStackSummaryResponse;
import com.baseProject.myBaseProject.dto.question.TechnologySummaryResponse;
import com.baseProject.myBaseProject.entity.Question;
import com.baseProject.myBaseProject.entity.TechStack;
import com.baseProject.myBaseProject.entity.Technology;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.QuestionType;
import com.baseProject.myBaseProject.enums.TechnologyType;
import com.baseProject.myBaseProject.exception.InvalidQuestionException;
import com.baseProject.myBaseProject.exception.QuestionVersionConflictException;
import com.baseProject.myBaseProject.exception.ResourceNotFoundException;
import com.baseProject.myBaseProject.mapper.QuestionMapper;
import com.baseProject.myBaseProject.repository.QuestionRepository;
import com.baseProject.myBaseProject.repository.TechStackRepository;
import com.baseProject.myBaseProject.repository.TechnologyRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.repository.specification.QuestionSpecifications;
import com.baseProject.myBaseProject.service.QuestionService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionServiceImpl implements QuestionService {
    private static final int MAX_KEYWORD_LENGTH = 200;
    private static final int MAX_FILTER_VALUES = 20;
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("updatedAt"),
            Sort.Order.desc("id")
    );

    private final QuestionRepository questionRepository;
    private final TechStackRepository techStackRepository;
    private final TechnologyRepository technologyRepository;
    private final UserAccountRepository userAccountRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public QuestionResponse create(QuestionCreateRequest request, Long creatorId) {
        Set<TechStack> techStacks = findActiveTechStacks(request.techStackIds());
        Set<Technology> technologies = findActiveTechnologies(request.technologyIds());
        validateTechStackRequirement(request.questionType(), techStacks);
        UserAccount creator = userAccountRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator account not found"));

        Question question = Question.builder()
                .contentVi(request.contentVi().trim())
                .contentEn(normalizeOptional(request.contentEn()))
                .techStacks(techStacks)
                .technologies(technologies)
                .level(request.level())
                .questionType(request.questionType())
                .difficulty(request.difficulty())
                .companyRef(normalizeOptional(request.companyRef()))
                .createdBy(creator)
                .active(request.active() == null || request.active())
                .build();

        Question saved = questionRepository.saveAndFlush(question);
        entityManager.refresh(saved);
        return QuestionMapper.toResponse(saved);
    }

    @Override
    public QuestionResponse getById(Long id) {
        return QuestionMapper.toResponse(findQuestion(id));
    }

    @Override
    public PageResponse<QuestionResponse> getAll(int page, int size) {
        return search(QuestionFilter.empty(), page, size);
    }

    @Override
    public PageResponse<QuestionResponse> search(QuestionFilter filter, int page, int size) {
        validatePage(page, size);
        validateFilter(filter);
        Page<QuestionResponse> result = questionRepository
                .findAll(
                        QuestionSpecifications.withFilter(filter),
                        PageRequest.of(page, size, DEFAULT_SORT)
                )
                .map(QuestionMapper::toResponse);
        return PageResponse.from(result);
    }

    @Override
    @Transactional
    public QuestionResponse update(Long id, QuestionUpdateRequest request) {
        Question question = findQuestion(id);
        if (question.getVersion() != request.version()) {
            throw new QuestionVersionConflictException(id);
        }

        Set<TechStack> techStacks = findActiveTechStacks(request.techStackIds());
        Set<Technology> technologies = findActiveTechnologies(request.technologyIds());
        validateTechStackRequirement(request.questionType(), techStacks);
        question.setContentVi(request.contentVi().trim());
        question.setContentEn(normalizeOptional(request.contentEn()));
        question.setTechStacks(techStacks);
        question.setTechnologies(technologies);
        question.setLevel(request.level());
        question.setQuestionType(request.questionType());
        question.setDifficulty(request.difficulty());
        question.setCompanyRef(normalizeOptional(request.companyRef()));
        question.setActive(request.active());

        try {
            Question saved = questionRepository.saveAndFlush(question);
            entityManager.refresh(saved);
            return QuestionMapper.toResponse(saved);
        } catch (OptimisticLockingFailureException ex) {
            throw new QuestionVersionConflictException(id);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Question question = findQuestion(id);
        if (question.isActive()) {
            question.setActive(false);
            questionRepository.saveAndFlush(question);
        }
    }

    @Override
    public List<TechStackSummaryResponse> getTechStacks(boolean activeOnly) {
        List<TechStack> techStacks = activeOnly
                ? techStackRepository.findAllByActiveTrueOrderByNameEnAsc()
                : techStackRepository.findAllByOrderByNameEnAsc();
        return techStacks.stream()
                .map(QuestionMapper::toTechStackResponse)
                .toList();
    }

    @Override
    public List<TechnologySummaryResponse> getTechnologies(
            boolean activeOnly,
            TechnologyType type
    ) {
        List<Technology> technologies = activeOnly
                ? technologyRepository.findAllByActiveTrueOrderByTechnologyTypeAscNameEnAsc()
                : technologyRepository.findAllByOrderByTechnologyTypeAscNameEnAsc();
        return technologies.stream()
                .filter(technology -> type == null || technology.getTechnologyType() == type)
                .map(QuestionMapper::toTechnologyResponse)
                .toList();
    }

    private Question findQuestion(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question %d not found".formatted(id)));
    }

    private Set<TechStack> findActiveTechStacks(Collection<Integer> ids) {
        Set<Integer> requestedIds = normalizeIds(ids);
        if (requestedIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<TechStack> found = techStackRepository.findAllByIdInAndActiveTrue(requestedIds);
        validateAllIdsFound(
                requestedIds,
                found.stream().map(TechStack::getId).collect(java.util.stream.Collectors.toSet()),
                "Active tech stacks"
        );
        return found.stream()
                .sorted(Comparator.comparing(TechStack::getCode))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Technology> findActiveTechnologies(Collection<Integer> ids) {
        Set<Integer> requestedIds = normalizeIds(ids);
        if (requestedIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Technology> found = technologyRepository.findAllByIdInAndActiveTrue(requestedIds);
        validateAllIdsFound(
                requestedIds,
                found.stream().map(Technology::getId).collect(java.util.stream.Collectors.toSet()),
                "Active technologies"
        );
        return found.stream()
                .sorted(Comparator.comparing(Technology::getCode))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<Integer> normalizeIds(Collection<Integer> ids) {
        return ids == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);
    }

    private static void validateAllIdsFound(
            Set<Integer> requestedIds,
            Set<Integer> foundIds,
            String resourceName
    ) {
        Set<Integer> missingIds = new LinkedHashSet<>(requestedIds);
        missingIds.removeAll(foundIds);
        if (!missingIds.isEmpty()) {
            throw new ResourceNotFoundException(
                    "%s not found: %s".formatted(resourceName, missingIds)
            );
        }
    }

    private static void validateTechStackRequirement(
            QuestionType type,
            Set<TechStack> techStacks
    ) {
        if (type == QuestionType.TECHNICAL && techStacks.isEmpty()) {
            throw new InvalidQuestionException("Technical questions require at least one tech stack");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidQuestionException("Page must not be negative");
        }
        if (size < 1 || size > PaginationConstant.MAX_PAGE_SIZE) {
            throw new InvalidQuestionException(
                    "Page size must be between 1 and %d".formatted(PaginationConstant.MAX_PAGE_SIZE)
            );
        }
    }

    private static void validateFilter(QuestionFilter filter) {
        if (filter == null) {
            throw new InvalidQuestionException("Question filter is required");
        }
        validateFilterIds(filter.techStackIds(), "Tech stack ids");
        validateFilterIds(filter.technologyIds(), "Technology ids");
        validateFilterValues(filter.levels(), "Levels");
        validateFilterValues(filter.questionTypes(), "Question types");
        validateFilterValues(filter.difficulties(), "Difficulties");
        if (hasValues(filter.techStackIds()) && Boolean.TRUE.equals(filter.unclassified())) {
            throw new InvalidQuestionException(
                    "Tech stack ids and unclassified=true cannot be used together"
            );
        }
        if (filter.keyword() != null && filter.keyword().trim().length() > MAX_KEYWORD_LENGTH) {
            throw new InvalidQuestionException(
                    "Keyword must not exceed %d characters".formatted(MAX_KEYWORD_LENGTH)
            );
        }
    }

    private static void validateFilterIds(List<Integer> ids, String fieldName) {
        validateFilterValues(ids, fieldName);
        if (hasValues(ids) && ids.stream().anyMatch(id -> id == null || id < 1)) {
            throw new InvalidQuestionException("%s must contain only positive ids".formatted(fieldName));
        }
    }

    private static void validateFilterValues(List<?> values, String fieldName) {
        if (values != null && values.size() > MAX_FILTER_VALUES) {
            throw new InvalidQuestionException(
                    "%s must not contain more than %d values".formatted(
                            fieldName,
                            MAX_FILTER_VALUES
                    )
            );
        }
    }

    private static boolean hasValues(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
