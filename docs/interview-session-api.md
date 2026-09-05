# Interview session API — phase 1

Phase 1 creates an immutable interview context and asynchronously prepares a flexible interview plan. It does not start the timer or create a fixed question list.

## Options

```http
GET /api/interview-session-options
Authorization: Bearer <access-token>
```

The response exposes the language codes and durations configured under `app.interview-session`, plus the supported interviewer styles.

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

Successful preparation changes the status to `READY` and returns the opening message and ordered focus areas. Focus areas are competencies or experience areas, not pre-generated questions.

Example:

```json
{
  "id": 501,
  "version": 2,
  "templateId": 101,
  "templateTitle": "Backend Java Developer",
  "profileId": 35,
  "profileName": "Minh profile",
  "status": "READY",
  "languageCode": "vi",
  "durationMinutes": 30,
  "interviewerStyle": "PROFESSIONAL",
  "jobContextSummary": "Vị trí Backend Java...",
  "candidateContextSummary": "Ứng viên có kinh nghiệm Spring Boot...",
  "openingMessage": "Chào Minh, hôm nay chúng ta sẽ có khoảng 30 phút...",
  "preparationErrorCode": null,
  "preparationErrorMessage": null,
  "focusAreas": [
    {
      "code": "JAVA_SPRING",
      "name": "Java and Spring Boot",
      "description": "Kiểm chứng cách ứng viên thiết kế và triển khai backend",
      "priority": "HIGH",
      "reason": "Yêu cầu chính của JD và xuất hiện trong CV",
      "plannedSeconds": 480,
      "evidenceStatus": "NOT_EXPLORED",
      "displayOrder": 0
    }
  ]
}
```

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
