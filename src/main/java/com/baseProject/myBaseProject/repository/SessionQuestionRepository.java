package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SessionQuestionRepository extends Repository<SessionQuestion, Long> {

    List<SessionQuestion> saveAll(Iterable<SessionQuestion> questions);

    long countBySessionId(Long sessionId);

    List<SessionQuestion> findBySessionIdAndSessionUserIdOrderByOrdinalAsc(
            Long sessionId,
            Long userId);

    @Query("""
            select session.id
            from InterviewSession session
            join SessionContextSnapshot snapshot on snapshot.session = session
            where session.profile.id = :profileId
              and snapshot.jobDescriptionHash = :jobDescriptionHash
              and session.id <> :excludedSessionId
              and exists (
                  select question.id
                  from SessionQuestion question
                  where question.session = session
              )
            order by session.createdAt desc, session.id desc
            """)
    List<Long> findRecentComparableSessionIds(
            @Param("profileId") Long profileId,
            @Param("jobDescriptionHash") String jobDescriptionHash,
            @Param("excludedSessionId") Long excludedSessionId,
            Pageable pageable);

    @Query("""
            select new com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection(
                question.session.id,
                question.questionSignature,
                question.questionText)
            from SessionQuestion question
            where question.session.id in :sessionIds
            order by question.session.createdAt desc,
                     question.session.id desc,
                     question.ordinal asc
            """)
    List<QuestionHistoryProjection> findHistoryBySessionIds(
            @Param("sessionIds") Collection<Long> sessionIds);
}
