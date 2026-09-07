# Interview session API — phases 1 and 2

Phase 1 creates an immutable interview context and asynchronously prepares a flexible interview plan. It does not start the timer or create a fixed question list.

## Options

```http
GET /api/interview-session-options
Authorization: Bearer <access-token>
```

The response exposes the language codes and durations configured under `app.interview-session`, plus the supported interviewer styles.

Interviewer styles change tone and probing behavior while preserving the same evidence standard:

- `FRIENDLY`: warm and encouraging, with gentle but evidence-based follow-ups.
- `PROFESSIONAL`: neutral, concise, and structured.
- `CHALLENGING`: direct and rigorous about claims and trade-offs while remaining respectful.

## Create a session

```http
POST /api/interview-sessions
Authorization: Bearer <access-token>
Idempotency-Key: 01991f7e-a4d4-7fb9-ae21-57989102fe04
Content-Type: application/json

{
  "templateId": 101,
  "profileId": 35,
  "languageCode": "vi",
  "durationMinutes": 30,
  "interviewerStyle": "PROFESSIONAL"
}
```

The endpoint returns `202 Accepted`. The initial state is normally `PREPARING`; preparation can finish before the response is read, so clients must use the returned status rather than assuming it.

The same user and `Idempotency-Key` return the original session when all request options match. Reusing the key with different options returns `409 INTERVIEW_SESSION_IDEMPOTENCY_CONFLICT`.

Eligible inputs:

- The profile belongs to the authenticated user, its CV is active, and the profile is confirmed.
- The template is confirmed and not archived.
- The template belongs to the authenticated user or is currently published.

Creation stores both source foreign keys and immutable JSON snapshots. Later profile changes do not change the AI context for this session.

## Poll preparation

```http
GET /api/interview-sessions/{sessionId}
Authorization: Bearer <access-token>
```

Successful preparation changes the status to `READY`. The response only exposes data needed by the client to display and poll the preparation state. AI context and focus areas remain internal to the backend.

Example:

```json
{
  "id": 501,
  "status": "READY",
  "templateTitle": "Backend Java Developer",
  "profileName": "Minh profile",
  "languageCode": "vi",
  "durationMinutes": 30,
  "interviewerStyle": "PROFESSIONAL",
  "preparationErrorCode": null,
  "preparationErrorMessage": null,
  "preparedAt": "2026-09-06T08:00:00Z"
}
```

The backend loads summaries, the opening message, conversation state, and ordered focus areas directly from persisted session data when invoking the interview AI. Clients never receive or submit this internal context.

## Retry preparation

```http
POST /api/interview-sessions/{sessionId}/preparation/retry
Authorization: Bearer <access-token>
```

Only `PREPARATION_FAILED` sessions can be retried. The endpoint returns `202 Accepted` and redispatches the session from `PREPARING`; the asynchronous worker may already have advanced the returned status. The original snapshots and user-selected options are reused.

## State transitions in phase 1

```text
NULL -> PREPARING
PREPARING -> READY
PREPARING -> PREPARATION_FAILED
PREPARATION_FAILED -> PREPARING
```

Every transition is appended to `interview_session_transitions`.

## Start the interview

```http
POST /api/interview-sessions/{sessionId}/start
Authorization: Bearer <access-token>
```

Only a `READY` session can start. The operation atomically changes it to
`IN_PROGRESS`, sets `startedAt` and `deadlineAt`, and persists the prepared
opening message as interviewer turn `0`. Calling start again while the session
is already `IN_PROGRESS` returns the current conversation without resetting the
timer.

The response contains the server clock state and persisted turns:

```json
{
  "sessionId": 501,
  "status": "IN_PROGRESS",
  "startedAt": "2026-09-06T08:00:00Z",
  "deadlineAt": "2026-09-06T08:30:00Z",
  "remainingSeconds": 1800,
  "currentTurnIndex": 0,
  "turns": [
    {
      "id": 9001,
      "turnIndex": 0,
      "role": "INTERVIEWER",
      "content": "Xin chào...",
      "candidateIntent": null,
      "action": "OPENING",
      "focusAreaCode": null,
      "requestId": null,
      "processingStatus": null,
      "processingErrorCode": null,
      "createdAt": "2026-09-06T08:00:00Z"
    }
  ]
}
```

## Submit an answer

```http
POST /api/interview-sessions/{sessionId}/answers
Authorization: Bearer <access-token>
Idempotency-Key: 01991f7e-a4d4-7fb9-ae21-57989102fe05
Content-Type: application/json

{
  "expectedTurnIndex": 0,
  "answer": "Tôi đã xây dựng một REST API bằng Spring Boot..."
}
```

`expectedTurnIndex` is the interviewer turn being answered. It prevents an old
browser tab from answering the wrong question. The idempotency key belongs to
this candidate answer and can be reused to retry after an AI timeout. Reusing
it with different content or a different turn returns a conflict.

Candidate turns expose `requestId`, `processingStatus`, and a standardized
`processingErrorCode`. Once processed, they also expose the primary
`candidateIntent` detected from the complete message. After a reload, the
frontend can resubmit a `FAILED` candidate turn with its original request ID
and content. A `PROCESSING` turn means another request is still generating the
interviewer response; its intent remains `null` until AI processing succeeds.

The backend commits the candidate turn before calling AI. The generated reply
is committed in a second short transaction, so a slow model call never holds a
database lock. AI chooses one action:

- `EXPLORE`: introduce or continue a relevant focus area.
- `FOLLOW_UP`: investigate evidence or reasoning from the latest answer.
- `HANDLE_REQUEST`: repeat, clarify, answer, acknowledge, or redirect before
  continuing the interview.
- `CLOSE`: finish naturally without another question.

There is no fixed question list, difficulty, or follow-up quota. The server
validates every selected focus area and prevents evidence from moving backward.
The same AI call classifies the candidate message and creates the interviewer
response; there is no additional classifier request. Candidate intents cover
answers, repeat or clarification requests, thinking time, candidate questions,
inability or refusal to answer, corrections, end requests, social/meta turns,
off-topic or inappropriate content, and an `OTHER` fallback. Evidence may still
be extracted from factual job-relevant statements anywhere in the message.
When the candidate types an end request, AI may provide the natural closing
turn while the session records `CANDIDATE_FINISHED` as the end reason.

## Resume the conversation

```http
GET /api/interview-sessions/{sessionId}/conversation
Authorization: Bearer <access-token>
```

This returns the timer and the complete persisted turn history. The frontend
can reconstruct the interview after refresh, reconnect, or opening another tab.

## Finish early

```http
POST /api/interview-sessions/{sessionId}/finish
Authorization: Bearer <access-token>
```

The backend advances the session to `SCORING` without adding an artificial
interviewer turn. A scheduler applies the same transition when `deadlineAt` is
reached. The response exposes `endReason` and `endedAt`, so the frontend owns
the localized completion screen:

```json
{
  "status": "SCORING",
  "endReason": "CANDIDATE_FINISHED",
  "endedAt": "2026-09-06T08:18:00Z",
  "remainingSeconds": 0
}
```

Possible reasons are `AI_COMPLETED`, `TIME_EXPIRED`, `CANDIDATE_FINISHED`, and
`SYSTEM_TERMINATED`. Only an actual AI response with action `CLOSE` is stored as
an interviewer closing turn. Actual scoring is the next implementation phase.

## State transitions added in phase 2

```text
READY -> IN_PROGRESS
IN_PROGRESS -> SCORING
```

Each transition updates the session activity time and appends an audit record
in the same transaction.
