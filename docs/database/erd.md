# ERD — Database hiện tại và kiến trúc mở rộng

> Nguyễn Xuân Tùng — US-04, US-10 và phần chuẩn bị US-11
> `user_accounts`, `refresh_tokens` do Thái Văn Trường phụ trách nhưng được thể hiện
> vì các bảng core tham chiếu đến tài khoản.

```mermaid
erDiagram
    USER_ACCOUNTS ||--o{ REFRESH_TOKENS : "owns"
    USER_ACCOUNTS o|--o{ QUESTIONS : "created by"
    USER_ACCOUNTS ||--o{ INTERVIEW_SESSIONS : "starts"
    QUESTIONS ||--o{ QUESTION_TECH_STACKS : "classified by"
    TECH_STACKS ||--o{ QUESTION_TECH_STACKS : "contains"
    QUESTIONS ||--o{ QUESTION_TECHNOLOGIES : "tagged with"
    TECHNOLOGIES ||--o{ QUESTION_TECHNOLOGIES : "contains"
    INTERVIEW_SESSIONS ||--o| REPORTS : "produces"

    USER_ACCOUNTS {
        bigint id PK
        varchar full_name
        varchar email UK
        varchar password_hash "nullable"
        varchar google_id UK "nullable, Google sub"
        varchar avatar_url
        varchar role "ADMIN or USER"
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

    TECHNOLOGIES {
        int id PK
        varchar code UK
        varchar name_vi
        varchar name_en
        varchar technology_type
        boolean is_active
        datetime created_at
        datetime updated_at
    }

    QUESTIONS {
        bigint id PK
        text content_vi
        text content_en
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

    QUESTION_TECH_STACKS {
        bigint question_id PK,FK
        int tech_stack_id PK,FK
    }

    QUESTION_TECHNOLOGIES {
        bigint question_id PK,FK
        int technology_id PK,FK
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

Các bảng trong sơ đồ trên được Liquibase quản lý ở Sprint 1. Sơ đồ kế tiếp là
phần thiết kế tổng thể của Round/Interview/Analysis để team thống nhất hướng mở
rộng; chúng **chưa phải bảng đã được master changelog tạo**.

```mermaid
erDiagram
    INTERVIEW_SESSIONS ||--o{ ROUNDS : "contains"
    ROUNDS ||--o{ ROUND_QUESTIONS : "asks"
    QUESTIONS o|--o{ ROUND_QUESTIONS : "source from bank"
    ROUND_QUESTIONS o|--o{ ROUND_QUESTIONS : "follow-up of"
    ROUND_QUESTIONS ||--o{ ANSWER_ATTEMPTS : "answered by"
    ANSWER_ATTEMPTS ||--o{ ANSWER_MEDIA : "has media"
    ANSWER_ATTEMPTS ||--o{ CODE_SUBMISSIONS : "has code runs"
    ANSWER_ATTEMPTS ||--o{ ANALYSIS_RESULTS : "is analyzed by"

    ROUNDS {
        bigint id PK
        bigint interview_session_id FK
        varchar round_type
        smallint round_order
        varchar status
    }

    ROUND_QUESTIONS {
        bigint id PK
        bigint round_id FK
        bigint question_id FK "nullable"
        bigint parent_round_question_id FK "nullable"
        varchar source
        smallint asked_order
        text content_snapshot
        json evaluation_guide_snapshot
    }

    ANSWER_ATTEMPTS {
        bigint id PK
        bigint round_question_id FK
        smallint attempt_no
        text transcript
        varchar status
    }

    ANSWER_MEDIA {
        bigint id PK
        bigint answer_attempt_id FK
        varchar media_type
        varchar storage_url
    }

    CODE_SUBMISSIONS {
        bigint id PK
        bigint answer_attempt_id FK
        varchar language
        varchar judge_status
    }

    ANALYSIS_RESULTS {
        bigint id PK
        bigint answer_attempt_id FK
        varchar analysis_type
        decimal score
        json result_json
    }
```

Chi tiết snapshot, follow-up và nhiều lần trả lời nằm trong
`docs/database/round-module-design.md`.

## Giải thích thiết kế phân loại

- `tech_stacks` trả lời câu hỏi “thuộc mảng nào?”: Backend, Frontend, DevOps,
  Mobile, Data & AI, Testing.
- `technologies` trả lời câu hỏi “dùng công nghệ nào?”: Java, C#, Spring Boot,
  React, MySQL, Docker… Trường `technology_type` giúp frontend chia checkbox theo
  nhóm `LANGUAGE`, `FRAMEWORK`, `DATABASE`, `CLOUD`, `PLATFORM`, `TOOL`.
- `question_tech_stacks` và `question_technologies` dùng khóa chính kép để ngăn
  gắn trùng cùng một nhãn cho một câu hỏi.
- Không lưu danh sách id dưới dạng JSON/chuỗi trong `questions`, vì bảng nối bảo
  đảm khóa ngoại, truy vấn lọc/index tốt hơn và dễ mở rộng.
- Câu hỏi `TECHNICAL` phải có ít nhất một Tech Stack ở tầng service. Technology
  cụ thể là tùy chọn vì vẫn có câu hỏi kỹ thuật tổng quát.
- `content_vi`/`content_en` là ngôn ngữ hiển thị của nội dung; không dùng hai trường
  này để biểu diễn Java, C# hoặc ngôn ngữ lập trình khác.

## Các quyết định core giữ nguyên

- `user_accounts.email` là danh tính chung cho local login và Google login.
- `password_hash` có thể null với tài khoản chỉ dùng Google; `google_id` lưu OIDC `sub`.
- `questions.created_by` có thể null cho dữ liệu seed và dùng `ON DELETE SET NULL`.
- Reports phụ thuộc Interview Session nên dùng `ON DELETE CASCADE`.
- Round Module được thể hiện trong ERD tổng thể nhưng vẫn là deliverable thiết kế
  trong Sprint 1; chưa có migration/entity/API tương ứng.
