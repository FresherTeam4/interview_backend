package com.baseProject.myBaseProject.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.question.QuestionCreateRequest;
import com.baseProject.myBaseProject.dto.question.QuestionFilter;
import com.baseProject.myBaseProject.dto.question.QuestionResponse;
import com.baseProject.myBaseProject.dto.question.QuestionUpdateRequest;
import com.baseProject.myBaseProject.entity.Question;
import com.baseProject.myBaseProject.entity.TechStack;
import com.baseProject.myBaseProject.entity.Technology;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;
import com.baseProject.myBaseProject.enums.TechnologyType;
import com.baseProject.myBaseProject.exception.InvalidQuestionException;
import com.baseProject.myBaseProject.exception.QuestionVersionConflictException;
import com.baseProject.myBaseProject.exception.ResourceNotFoundException;
import com.baseProject.myBaseProject.repository.QuestionRepository;
import com.baseProject.myBaseProject.repository.TechStackRepository;
import com.baseProject.myBaseProject.repository.TechnologyRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {
    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private TechStackRepository techStackRepository;

    @Mock
    private TechnologyRepository technologyRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private QuestionServiceImpl service;

    @Test
    void createTechnicalQuestionUsesMultipleClassificationsAndNormalizesText() {
        TechStack backend = techStack(2, "BACKEND");
        TechStack devops = techStack(4, "DEVOPS");
        Technology java = technology(1, "JAVA", TechnologyType.LANGUAGE);
        Technology docker = technology(28, "DOCKER", TechnologyType.TOOL);
        UserAccount creator = UserAccount.builder().id(10L).build();
        QuestionCreateRequest request = new QuestionCreateRequest(
                "  Dependency Injection la gi?  ",
                "   ",
                Set.of(2, 4),
                Set.of(1, 28),
                QuestionLevel.JUNIOR,
                QuestionType.TECHNICAL,
                QuestionDifficulty.MEDIUM,
                "  FPT  ",
                null
        );

        when(techStackRepository.findAllByIdInAndActiveTrue(Set.of(2, 4)))
                .thenReturn(List.of(devops, backend));
        when(technologyRepository.findAllByIdInAndActiveTrue(Set.of(1, 28)))
                .thenReturn(List.of(docker, java));
        when(userAccountRepository.findById(10L)).thenReturn(Optional.of(creator));
        when(questionRepository.saveAndFlush(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(100L);
            return question;
        });

        QuestionResponse result = service.create(request, 10L);

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.contentVi()).isEqualTo("Dependency Injection la gi?");
        assertThat(result.contentEn()).isNull();
        assertThat(result.companyRef()).isEqualTo("FPT");
        assertThat(result.techStacks()).extracting("code").containsExactly("BACKEND", "DEVOPS");
        assertThat(result.technologies()).extracting("code").containsExactly("DOCKER", "JAVA");
        assertThat(result.createdById()).isEqualTo(10L);
        assertThat(result.active()).isTrue();
        verify(entityManager).refresh(any(Question.class));
    }

    @Test
    void createTechnicalQuestionRejectsMissingTechStack() {
        QuestionCreateRequest request = new QuestionCreateRequest(
                "Cau hoi",
                null,
                null,
                null,
                QuestionLevel.FRESHER,
                QuestionType.TECHNICAL,
                QuestionDifficulty.EASY,
                null,
                true
        );

        assertThatThrownBy(() -> service.create(request, 10L))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessage("Technical questions require at least one tech stack");

        verify(questionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsUnknownTechnologyIds() {
        QuestionCreateRequest request = new QuestionCreateRequest(
                "Cau hoi",
                null,
                Set.of(2),
                Set.of(999),
                QuestionLevel.FRESHER,
                QuestionType.TECHNICAL,
                QuestionDifficulty.EASY,
                null,
                true
        );
        when(techStackRepository.findAllByIdInAndActiveTrue(Set.of(2)))
                .thenReturn(List.of(techStack(2, "BACKEND")));
        when(technologyRepository.findAllByIdInAndActiveTrue(Set.of(999)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Active technologies not found: [999]");

        verify(questionRepository, never()).saveAndFlush(any());
    }

    @Test
    void getByIdReturnsNotFoundForUnknownQuestion() {
        when(questionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Question 999 not found");
    }

    @Test
    void updateRejectsStaleVersion() {
        Question question = Question.builder()
                .id(7L)
                .contentVi("Current")
                .level(QuestionLevel.JUNIOR)
                .questionType(QuestionType.BEHAVIORAL)
                .difficulty(QuestionDifficulty.MEDIUM)
                .version(3)
                .active(true)
                .build();
        QuestionUpdateRequest request = new QuestionUpdateRequest(
                "Changed",
                null,
                null,
                null,
                QuestionLevel.JUNIOR,
                QuestionType.BEHAVIORAL,
                QuestionDifficulty.MEDIUM,
                null,
                true,
                2
        );
        when(questionRepository.findById(7L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.update(7L, request))
                .isInstanceOf(QuestionVersionConflictException.class);

        verify(questionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletePerformsSoftDelete() {
        Question question = Question.builder().id(8L).active(true).build();
        when(questionRepository.findById(8L)).thenReturn(Optional.of(question));

        service.delete(8L);

        assertThat(question.isActive()).isFalse();
        verify(questionRepository).saveAndFlush(question);
        verify(questionRepository, never()).delete(any(Question.class));
    }

    @Test
    void getAllRejectsOversizedPage() {
        assertThatThrownBy(() -> service.getAll(0, 51))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessage("Page size must be between 1 and 50");
    }

    @Test
    void searchUsesMultiValueSpecificationAndKeepsPaginationMetadata() {
        QuestionFilter filter = new QuestionFilter(
                "spring",
                true,
                List.of(2, 4),
                false,
                List.of(1, 12),
                List.of(QuestionLevel.JUNIOR, QuestionLevel.MID),
                List.of(QuestionType.TECHNICAL),
                List.of(QuestionDifficulty.MEDIUM, QuestionDifficulty.HARD)
        );
        when(questionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        PageResponse<QuestionResponse> result = service.search(filter, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        verify(questionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void searchRejectsConflictingTechStackFilters() {
        QuestionFilter filter = new QuestionFilter(
                null,
                null,
                List.of(2),
                true,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.search(filter, 0, 20))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessage("Tech stack ids and unclassified=true cannot be used together");

        verify(questionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    private static TechStack techStack(int id, String code) {
        return TechStack.builder()
                .id(id)
                .code(code)
                .nameVi(code)
                .nameEn(code)
                .active(true)
                .build();
    }

    private static Technology technology(int id, String code, TechnologyType type) {
        return Technology.builder()
                .id(id)
                .code(code)
                .nameVi(code)
                .nameEn(code)
                .technologyType(type)
                .active(true)
                .build();
    }
}
