package com.baseProject.myBaseProject.service.impl;

import java.util.List;

import com.baseProject.myBaseProject.constant.PaginationConstant;
import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.question.QuestionCreateRequest;
import com.baseProject.myBaseProject.dto.question.QuestionResponse;
import com.baseProject.myBaseProject.dto.question.QuestionUpdateRequest;
import com.baseProject.myBaseProject.dto.question.TechStackSummaryResponse;
import com.baseProject.myBaseProject.entity.Question;
import com.baseProject.myBaseProject.entity.TechStack;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.QuestionType;
import com.baseProject.myBaseProject.exception.InvalidQuestionException;
import com.baseProject.myBaseProject.exception.QuestionVersionConflictException;
import com.baseProject.myBaseProject.exception.ResourceNotFoundException;
import com.baseProject.myBaseProject.mapper.QuestionMapper;
import com.baseProject.myBaseProject.repository.QuestionRepository;
import com.baseProject.myBaseProject.repository.TechStackRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
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
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("updatedAt"),
            Sort.Order.desc("id")
    );

    private final QuestionRepository questionRepository;
    private final TechStackRepository techStackRepository;
    private final UserAccountRepository userAccountRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public QuestionResponse create(QuestionCreateRequest request, Long creatorId) {
        validateTechStackRequirement(request.questionType(), request.techStackId());
        TechStack techStack = findActiveTechStack(request.techStackId());
        UserAccount creator = userAccountRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator account not found"));

        Question question = Question.builder()
                .contentVi(request.contentVi().trim())
                .contentEn(normalizeOptional(request.contentEn()))
                .techStack(techStack)
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
        validatePage(page, size);
        Page<QuestionResponse> result = questionRepository
                .findAll(PageRequest.of(page, size, DEFAULT_SORT))
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

        validateTechStackRequirement(request.questionType(), request.techStackId());
        question.setContentVi(request.contentVi().trim());
        question.setContentEn(normalizeOptional(request.contentEn()));
        question.setTechStack(findActiveTechStack(request.techStackId()));
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

    private Question findQuestion(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question %d not found".formatted(id)));
    }

    private TechStack findActiveTechStack(Integer id) {
        if (id == null) {
            return null;
        }
        return techStackRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active tech stack %d not found".formatted(id)
                ));
    }

    private static void validateTechStackRequirement(QuestionType type, Integer techStackId) {
        if (type == QuestionType.TECHNICAL && techStackId == null) {
            throw new InvalidQuestionException("Technical questions require a tech stack");
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

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
