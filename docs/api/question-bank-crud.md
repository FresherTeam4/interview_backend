# Question Bank CRUD API — US-10

## Scope

The Sprint 1 API allows an `EVENT_ADMIN` to create, read, update and deactivate
Question Bank records. Searching and domain-specific filtering belong to US-11
and are intentionally not implemented here.

All endpoints require an access token with role `EVENT_ADMIN`:

```http
Authorization: Bearer <access-token>
```

## Endpoints

| Method | Path | Result |
|---|---|---|
| `POST` | `/api/admin/questions` | Create a question |
| `GET` | `/api/admin/questions/{id}` | Get one question |
| `GET` | `/api/admin/questions?page=0&size=20` | Get a page, sorted by latest update |
| `PUT` | `/api/admin/questions/{id}` | Replace a question |
| `DELETE` | `/api/admin/questions/{id}` | Soft-delete a question |
| `GET` | `/api/admin/tech-stacks?activeOnly=true` | Get options for the question form |

Page size must be from 1 to 50.

## Search and filtering

The paginated list endpoint accepts optional filters. Filters are combined with
`AND`; omitted parameters do not restrict the result.

```http
GET /api/admin/questions?keyword=spring&active=true&techStackId=2&level=JUNIOR&questionType=TECHNICAL&difficulty=MEDIUM&page=0&size=20
```

| Parameter | Type | Meaning |
|---|---|---|
| `keyword` | string | Case-insensitive substring search in Vietnamese and English content; maximum 200 characters |
| `active` | boolean | Filter active or inactive questions |
| `techStackId` | positive integer | Filter by a Tech Stack id |
| `unclassified` | boolean | With `true`, return questions whose Tech Stack is null |
| `level` | enum | `FRESHER`, `JUNIOR`, `MID`, or `SENIOR` |
| `questionType` | enum | `BEHAVIORAL`, `TECHNICAL`, or `CASE_STUDY` |
| `difficulty` | enum | `EASY`, `MEDIUM`, or `HARD` |
| `page` | integer | Zero-based page number |
| `size` | integer | Page size from 1 to 50 |

`techStackId` and `unclassified=true` are mutually exclusive. Sending both
returns `400 INVALID_QUESTION`. An empty or whitespace-only keyword is treated
as no keyword filter. Existing clients may continue calling the endpoint with
only `page` and `size`.

## Create request

```json
{
  "contentVi": "Dependency Injection trong Spring là gì?",
  "contentEn": "What is Dependency Injection in Spring?",
  "techStackId": 2,
  "level": "JUNIOR",
  "questionType": "TECHNICAL",
  "difficulty": "MEDIUM",
  "companyRef": null,
  "active": true
}
```

`createdById` is taken from the authenticated account. The client cannot choose
another creator. A `TECHNICAL` question must reference an active Tech Stack.
General behavioral and case-study questions may use `techStackId: null`.

Successful creation returns `201 Created`, a `Location` header and the created
resource.

## Update request and optimistic locking

`PUT` is a full replacement and therefore requires all mandatory question
fields, `active`, and `version`:

```json
{
  "contentVi": "Dependency Injection trong Spring hoạt động như thế nào?",
  "contentEn": "How does Dependency Injection work in Spring?",
  "techStackId": 2,
  "level": "JUNIOR",
  "questionType": "TECHNICAL",
  "difficulty": "MEDIUM",
  "companyRef": null,
  "active": true,
  "version": 0
}
```

Always send the `version` from the latest GET response. If another request has
already changed that question, the API returns `409 Conflict` with code
`QUESTION_VERSION_CONFLICT`. Reload the resource before retrying.

## Delete behavior

`DELETE` returns `204 No Content` and changes `is_active` to `false`. It does not
physically remove the row, because future interview history can reference the
question. A question can be reactivated with `PUT` and `active: true`.

## Important response codes

| Status | Meaning |
|---|---|
| `200` | Read or update succeeded |
| `201` | Create succeeded |
| `204` | Deactivation succeeded |
| `400` | Invalid body, enum, paging, or question rules |
| `401` | Missing or invalid access token |
| `403` | Authenticated account is not an `EVENT_ADMIN` |
| `404` | Question, creator, or active Tech Stack not found |
| `409` | Stale question version or database constraint conflict |

OpenAPI documentation is available at `/swagger-ui.html` when Swagger is enabled.
