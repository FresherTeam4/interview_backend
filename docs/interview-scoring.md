# Interview scoring

## Processing flow

When an interview closes, the session and its end reason are committed in
`SCORING` before an asynchronous worker is dispatched. The worker claims the
session in a short transaction, calls AI without holding a database lock, and
then persists the assessment, all focus-area results, the `COMPLETED` session
state, and its audit transition in one transaction.

AI, validation, queue, or persistence failures move the session to
`SCORING_FAILED`. The user can retry explicitly. A startup recovery listener
also marks scoring work interrupted by a server restart as retryable failure.

## Scoring input

The scoring engine receives:

- the immutable job template snapshot and job context summary;
- the session focus areas, priorities, and rolling evidence summaries;
- all interviewer turns and only completed candidate turns;
- candidate intents, interviewer actions, and focus-area turn tags;
- the rolling conversation summary, end reason, actual duration, and language.

Candidate profile and CV data are deliberately excluded. They help prepare a
relevant interview but do not prove performance. Interviewer style is also
excluded because tone must not change the evaluation standard.

Raw candidate turns are the authoritative evidence. Rolling summaries only
help the model navigate a long conversation.

## AI and backend responsibilities

The AI returns one assessment for every focus area. A scored area must cite at
least one completed candidate turn. It also returns communication feedback,
overall strengths, improvements, and an action plan.

The backend validates focus-area codes and evidence turn ids before accepting
the result. It calculates all aggregate values itself:

```text
priority weight: HIGH = 3, MEDIUM = 2, LOW = 1
coverage factor: NOT_EXPLORED = 0, PARTIAL = 0.5, SUFFICIENT = 1

technical score = weighted mean of scored focus areas
overall score = technical score * 0.8 + communication score * 0.2
```

When weighted coverage is below 60 percent, the technical details and feedback
remain available but the overall score is `null` and overall confidence is
`LOW`.

The thresholds and aggregate weights are configured under
`app.interview-scoring`.

## Persistence model

`interview_assessments` stores one final aggregate report per session.
`interview_focus_area_results` stores the individual focus-area results and
their evidence turn ids. JSON columns are limited to report lists that are read
and written as a whole.

Migration: `database_docs/migrations/005-interview-scoring.sql`.

## API

```text
GET  /api/interview-sessions/{sessionId}/report
POST /api/interview-sessions/{sessionId}/scoring/retry
```

The report endpoint returns a status-only payload while scoring, a normalized
error while scoring has failed, and the complete persisted report after the
session reaches `COMPLETED`.
