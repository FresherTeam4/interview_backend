# ERD — Core Database — Sprint 1

> Nguyễn Xuân Tùng — Task 4.1 / US-04  
> Authentication tables (`user_accounts`, `refresh_tokens`) are owned by Thái Văn Trường
> and are included here because the core tables reference them.

```mermaid
erDiagram
    USER_ACCOUNTS ||--o{ REFRESH_TOKENS : "owns"
    USER_ACCOUNTS o|--o{ QUESTIONS : "created by"
    USER_ACCOUNTS ||--o{ INTERVIEW_SESSIONS : "starts"
    TECH_STACKS o|--o{ QUESTIONS : "classifies"
    INTERVIEW_SESSIONS ||--o| REPORTS : "produces"

    USER_ACCOUNTS {
        bigint id PK
        varchar full_name
        varchar email UK
        varchar password_hash "nullable"
        varchar google_id UK "nullable, Google sub"
        varchar avatar_url
        varchar role "EVENT_ADMIN or PARTICIPANT"
        boolean enabled
        datetime created_at
        datetime updated_at
    }

    REFRESH_TOKENS {
        bigint id PK
        varchar token_hash UK
        varchar family_id
        datetime issued_at
        datetime expires_at
        datetime revoked_at
        bigint user_id FK
    }

    TECH_STACKS {
        int id PK
        varchar code UK
        varchar name_vi
        varchar name_en
        boolean is_active
        datetime created_at
        datetime updated_at
    }

    QUESTIONS {
        bigint id PK
        text content_vi
        text content_en
        int tech_stack_id FK
        varchar level
        varchar question_type
        varchar difficulty
        varchar company_ref
        bigint created_by FK
        boolean is_active
        int version
        datetime created_at
        datetime updated_at
    }

    INTERVIEW_SESSIONS {
        bigint id PK
        bigint user_id FK
        varchar target_position
        mediumtext jd_text
        varchar cv_snapshot_url
        varchar status
        smallint current_round_order
        varchar video_call_url
        datetime started_at
        datetime ended_at
        datetime created_at
        datetime updated_at
    }

    REPORTS {
        bigint id PK
        bigint interview_session_id FK,UK
        decimal overall_score
        json star_score_json
        json communication_score_json
        json technical_score_json
        text strengths
        text weaknesses
        varchar drive_file_url
        datetime created_at
        datetime updated_at
    }
```

## Decisions

- `user_accounts.email` is the common identity for local and Google login.
- `password_hash` is nullable for a Google-only account; `google_id` stores the immutable
  OpenID Connect `sub`. At least one login credential must be present.
- `questions.created_by` is nullable for system seed data and uses `ON DELETE SET NULL`.
- Deleting a user is restricted while interview history exists. Reports are dependent on
  their interview session and therefore use `ON DELETE CASCADE`.
- Round Module remains a design-only deliverable in Sprint 1.
