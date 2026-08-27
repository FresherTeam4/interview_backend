# Interview Engine MVP — Thiết kế kỹ thuật và API

> Trạng thái: M00–M04 đã approved; M05 implementation review candidate
> Tài liệu sản phẩm liên quan: [interview-engine-mvp-plan.md](./interview-engine-mvp-plan.md)  
> Baseline: Java 17, Spring Boot 4.1.x, Spring MVC, Spring Security, Spring Data JPA,
> MySQL, Liquibase, MinIO/S3, Spring AI và Gemini

Tài liệu này chuyển product flow đã thống nhất thành một backend contract có thể triển khai. Nội
dung bao gồm kiến trúc, database, state machine, API, transaction boundary, concurrency,
idempotency, AI contract, file/audio storage, cấu hình, lỗi, security, recovery và verification
strategy.

Các quyết định trong M00 là baseline kỹ thuật đã được người dùng phê duyệt bằng `APPROVED M00`.
Sau approval, thay đổi ảnh hưởng API/schema phải được nêu rõ và review như một
contract change. Hai lựa chọn provider STT/TTS được hoãn có chủ đích tới `M13`/`M15`; chúng không
block schema hoặc các module text. Realtime voice và barge-in không thuộc phạm vi tài liệu này.

---

## 1. Mục tiêu kỹ thuật

MVP phải bảo đảm các bất biến sau:

1. Database là nguồn sự thật duy nhất của session, script, conversation, voice draft và report.
2. Đóng tab, mất mạng hoặc application restart không làm mất lượt đã xác nhận.
3. Mỗi thao tác tạo session/turn/attempt là idempotent; mọi command thay đổi state đều chống được
   hai tab gửi đồng thời bằng optimistic version.
4. Không giữ transaction database mở trong lúc gọi AI, STT, TTS hoặc object storage.
5. Mọi lần đổi session status đều có transition log trong cùng transaction.
6. Question, context snapshot, rubric version và report cũ không đổi nghĩa khi dữ liệu nguồn được
   chỉnh sửa về sau.
7. Mọi resource do người dùng sở hữu được query bằng cả resource ID và user ID.
8. Provider output luôn được parse bằng versioned JSON schema và validate lại ở application layer.
9. Raw JD text, raw STT transcript và state transition log không bị ghi đè.
10. Application vẫn khởi động khi thiếu AI/STT/TTS credential; lỗi chỉ xuất hiện khi use case tương
    ứng được gọi.

### 1.1. Ngoài phạm vi

- WebRTC/WebSocket realtime audio.
- Streaming STT/LLM/TTS.
- Voice activity detection và end-of-speech realtime.
- Barge-in.
- Speech metrics, word timings, filler words và attitude/confidence scoring.
- Question bank dùng chung hoặc script tái sử dụng giữa nhiều session.
- Admin UI để quản lý rubric; MVP seed rubric bằng Liquibase.

---

## 2. Kiến trúc tổng thể

```text
HTTP Controller
      │
      ▼
Application Services
      ├── JobDescriptionService
      ├── InterviewSessionService
      ├── InterviewConversationService
      ├── VoiceAnswerService
      ├── InterviewScoringService
      └── SessionStateMachine
      │
      ├─────────────── Database repositories
      │
      └── Provider ports
            ├── InterviewQuestionGenerator
            ├── InterviewFollowUpDecider
            ├── InterviewScorer
            ├── SpeechToTextClient
            ├── TextToSpeechClient
            └── FileStorageService
```

### 2.1. Quy tắc phân lớp

- Controller xử lý HTTP, validation, authentication annotations, status code và DTO.
- Service xử lý ownership, state transition, orchestration, transaction và business invariant.
- Repository xử lý query; không để controller gọi repository trực tiếp.
- Mapper tạo response DTO; không expose JPA entity.
- AI/STT/TTS/MinIO nằm sau port để thay provider và cô lập failure boundary.
- Prompt và response schema nằm trong `src/main/resources/ai`, không nhúng prompt dài trong Java.
- Thời gian nghiệp vụ lấy từ `Clock`; persisted timestamp dùng `Instant`.

### 2.2. Package dự kiến

Giữ base package hiện tại `com.baseProject.myBaseProject`:

```text
controller/
  JobDescriptionController
  InterviewSessionController
  InterviewVoiceController
  InterviewReportController

dto/jd/
dto/interview/
dto/voice/
dto/report/

entity/
enums/
repository/
mapper/
service/
service/impl/

jd/extraction/
  JobDescriptionTextExtractor
  PdfJobDescriptionTextExtractor
  PlainTextJobDescriptionTextExtractor

interview/ai/
  InterviewQuestionGenerator
  InterviewFollowUpDecider
  InterviewScorer
  gemini/

interview/voice/
  SpeechToTextClient
  TextToSpeechClient
  provider-specific implementations/

scheduler/
  InterviewSessionExpiryJob
  InterviewWorkflowRecoveryJob
  InterviewAudioCleanupJob
```

Không tạo interface cho helper private. Các interface trên tồn tại vì chúng là boundary tới hệ
thống ngoài hoặc application use case.

---

## 3. Domain vocabulary và enum

### 3.1. Job Description

```text
JobDescriptionSourceType = TEXT | FILE
JobDescriptionStatus     = DRAFT | READY
```

- `DRAFT`: người dùng còn có thể sửa `confirmedText`.
- `READY`: người dùng đã xác nhận; không sửa đè trong MVP.
- File không trích xuất được bị từ chối ngay ở request, không tạo một hàng `FAILED` rác.

### 3.2. Session

```text
InterviewDifficulty = EASY | MEDIUM | HARD
SessionMode         = TEXT | VOICE_TURN_BASED

SessionStatus =
  CREATED | SCRIPT_GENERATING | READY | IN_PROGRESS | PAUSED |
  SCORING | COMPLETED | ABANDONED | FAILED

SessionEndReason =
  USER_COMPLETED | USER_COMPLETED_EARLY | USER_ABANDONED |
  TIMEOUT_24H | SYSTEM_ERROR

SessionFailureStage =
  SCRIPT_GENERATION | NEXT_TURN | SCORING

AwaitingAction =
  START_SESSION | CANDIDATE_ANSWER | TRANSCRIPT_CONFIRMATION |
  ENGINE_RESPONSE | ENGINE_RETRY | REPORT | NONE
```

`AwaitingAction` là workflow phase phục vụ resume/UI, tách khỏi `SessionStatus`. Ví dụ cả lúc chờ
candidate answer và lúc chờ AI follow-up, session đều đang `IN_PROGRESS` nhưng UI cần hiển thị
khác nhau.

### 3.3. Question và turn

```text
QuestionSourceType =
  CV_PROJECT | CV_SKILL | CV_JD_MATCH | JD_GAP | GENERAL_BEHAVIORAL

TurnRole      = INTERVIEWER | CANDIDATE
TurnInputMode = TEXT | VOICE_TURN_BASED
FollowUpDecision = FOLLOW_UP | NEXT_QUESTION | END_INTERVIEW
```

Follow-up không phải `session_question`. Nó là một interviewer `session_turn` trỏ về base question
và candidate turn mà nó đang đào sâu.

### 3.4. Voice và report

```text
VoiceAttemptStatus =
  RECORDED | TRANSCRIBING | TRANSCRIBED | CONFIRMED | DISCARDED | FAILED

AudioAssetStatus = PENDING | GENERATING | READY | FAILED

ReportResultStatus = SCORED | INSUFFICIENT_EVIDENCE
ReportHighlightType = STRENGTH | IMPROVEMENT | NEXT_ACTION
```

---

## 4. Database design

### 4.1. Quy ước chung

- Tên bảng/cột dùng `snake_case`.
- ID dùng `BIGINT AUTO_INCREMENT` và map `GenerationType.IDENTITY`.
- Timestamp dùng `DATETIME(6)` và map `Instant` UTC, nhất quán với migrations hiện tại.
- Chuỗi enum dùng `VARCHAR` + `CHECK`; Java dùng `EnumType.STRING`.
- Text do người dùng/AI cung cấp dùng `TEXT` hoặc `MEDIUMTEXT`, có giới hạn ở application layer.
- Mọi FK có index phục vụ lookup tương ứng.
- Schema thay đổi bằng migration mới từ `025`, không sửa changeset `001`–`024`.
- `created_at` do application ghi bằng `Clock`; không dùng `ON UPDATE CURRENT_TIMESTAMP`.

### 4.2. Thứ tự migration đã khóa trong M00

```text
025-create-job-descriptions.sql             [M01]
026-create-rubric-tables.sql                [M03]
027-seed-interview-rubric-v1.sql            [M03]
028-create-interview-session-foundation.sql [M04]
029-create-session-questions.sql            [M05]
030-create-session-turns.sql                [M08]
031-create-interview-report-tables.sql      [M10]
032-create-voice-answer-attempts.sql        [M12]
033-create-turn-audio-assets.sql            [M15]
```

Migration `028` tạo `interview_sessions`, `session_context_snapshots` và
`session_state_transitions` như một state-machine foundation nguyên khối. Migration `025` khóa đầy
đủ shape của resource JD, gồm các cột file nullable; `M02` chỉ mở hành vi file trên contract đó nên
không cần migration riêng. Mỗi file chỉ được tạo ở module sở hữu ghi bên trên, được include theo
thứ tự trong `db.changelog-master.yaml` và có rollback an toàn khi thực tế cho phép. Không tạo file
placeholder trước module và tuyệt đối không sửa changeset `001`–`024`.

### 4.3. `job_descriptions`

| Cột | Kiểu | Null | Ghi chú |
|---|---|---:|---|
| `id` | `BIGINT` | Không | PK |
| `user_id` | `BIGINT` | Không | Owner, FK `user_accounts`, cascade khi xóa account |
| `title` | `VARCHAR(200)` | Không | Tên hiển thị, không nhất thiết lấy từ AI |
| `source_type` | `VARCHAR(10)` | Không | `TEXT` hoặc `FILE` |
| `status` | `VARCHAR(10)` | Không | `DRAFT` hoặc `READY` |
| `original_filename` | `VARCHAR(255)` | Có | Chỉ có với source file |
| `storage_key` | `VARCHAR(500)` | Có | Không trả ra API |
| `content_type` | `VARCHAR(100)` | Có | Content type đã xác thực |
| `file_size_bytes` | `BIGINT` | Có | Chỉ có với source file |
| `checksum_sha256` | `CHAR(64)` | Không | Hash source text đã normalize lúc tạo; bất biến cùng `raw_text` |
| `raw_text` | `MEDIUMTEXT` | Không | Bản dán hoặc bản trích xuất đầu tiên, bất biến |
| `confirmed_text` | `MEDIUMTEXT` | Không | Bản dùng để sinh câu hỏi |
| `confirmed_at` | `DATETIME(6)` | Có | `NULL` khi còn draft |
| `is_active` | `BOOLEAN` | Không | Xóa mềm, mặc định `TRUE` |
| `created_at` | `DATETIME(6)` | Không | Clock UTC |
| `updated_at` | `DATETIME(6)` | Không | Clock UTC |

Index:

```text
(user_id, is_active, created_at)
(user_id, checksum_sha256)
```

Constraint application-level:

- `TEXT`: các field file phải `NULL`.
- `FILE`: `original_filename`, `storage_key`, `content_type`, `file_size_bytes` phải có.
- `READY` tương đương `confirmed_at != NULL`.
- Không update title/text sau khi đã `READY`; muốn thay đổi tạo JD mới/revision mới.
- Query dùng để tạo session phải là `findByIdAndUserIdAndActiveTrue`.

### 4.4. Rubric

#### `rubrics`

```text
id                 BIGINT PK
code               VARCHAR(50) NOT NULL UNIQUE
name               VARCHAR(150) NOT NULL
description        TEXT NULL
current_version_id BIGINT NULL
created_at         DATETIME(6) NOT NULL
```

#### `rubric_versions`

```text
id           BIGINT PK
rubric_id    BIGINT NOT NULL FK rubrics ON DELETE CASCADE
version_no   INT NOT NULL
change_note  VARCHAR(500) NULL
published_at DATETIME(6) NULL
created_at   DATETIME(6) NOT NULL
UNIQUE (rubric_id, version_no)
```

Sau khi tạo `rubric_versions`, migration thêm FK `rubrics.current_version_id -> rubric_versions.id`
với `ON DELETE RESTRICT`.

Không dùng unique `(rubric_id, is_current)`: trên MySQL, cách đó chỉ cho phép một version `false`
và một version `true`. Con trỏ `rubrics.current_version_id` biểu diễn chính xác “một current
version” hơn.

#### `rubric_criteria`

```text
id                BIGINT PK
rubric_version_id BIGINT NOT NULL FK rubric_versions ON DELETE CASCADE
code              VARCHAR(50) NOT NULL
name              VARCHAR(150) NOT NULL
description       TEXT NOT NULL
weight            DECIMAL(4,3) NOT NULL
max_score         SMALLINT NOT NULL DEFAULT 4
display_order     SMALLINT NOT NULL DEFAULT 0
UNIQUE (rubric_version_id, code)
CHECK (weight > 0 AND weight <= 1)
CHECK (max_score > 0)
```

Tổng weight bằng `1.000` được kiểm tra ở service và đối chiếu bằng query trên seed data khi review.

#### `rubric_criterion_levels`

```text
id           BIGINT PK
criterion_id BIGINT NOT NULL FK rubric_criteria ON DELETE CASCADE
level_no     SMALLINT NOT NULL
label        VARCHAR(80) NOT NULL
descriptor   TEXT NOT NULL
score_value  DECIMAL(4,2) NOT NULL
UNIQUE (criterion_id, level_no)
```

Rubric MVP v1 đã khóa trong M00:

| Code | Weight |
|---|---:|
| `TECHNICAL_ACCURACY` | 0.300 |
| `TECHNICAL_DEPTH` | 0.250 |
| `PROBLEM_SOLVING` | 0.200 |
| `RELEVANCE_AND_STRUCTURE` | 0.150 |
| `COMMUNICATION_CLARITY` | 0.100 |

Mỗi criterion có bốn level được viết thủ công. Không để AI tự sinh rubric seed.

### 4.5. `interview_sessions`

| Cột | Kiểu | Null | Ghi chú |
|---|---|---:|---|
| `id` | `BIGINT` | Không | PK |
| `user_id` | `BIGINT` | Không | Owner, FK user, cascade |
| `profile_id` | `BIGINT` | Không | FK profile, restrict |
| `job_description_id` | `BIGINT` | Không | FK JD, restrict |
| `rubric_version_id` | `BIGINT` | Không | Version chốt lúc tạo, restrict |
| `creation_key` | `VARCHAR(128)` | Không | Idempotency key bắt buộc của create request |
| `creation_request_hash` | `CHAR(64)` | Không | Phát hiện cùng key nhưng request khác |
| `difficulty` | `VARCHAR(10)` | Không | Easy/Medium/Hard |
| `mode` | `VARCHAR(30)` | Không | Text/voice turn-based |
| `language_code` | `VARCHAR(10)` | Không | MVP mặc định `vi` |
| `status` | `VARCHAR(30)` | Không | Session state |
| `awaiting_action` | `VARCHAR(40)` | Không | Resume/UI workflow phase |
| `current_question_ordinal` | `SMALLINT` | Có | Base question hiện tại |
| `next_turn_index` | `INT` | Không | Chỉ số sẽ cấp cho turn tiếp theo |
| `current_followup_depth` | `SMALLINT` | Không | 0–2 |
| `total_followup_count` | `SMALLINT` | Không | Giới hạn toàn phiên |
| `answered_question_count` | `SMALLINT` | Không | Số base question có answer xác nhận |
| `total_question_count` | `SMALLINT` | Không | 5–7 sau khi generate |
| `generation_seed` | `CHAR(36)` | Không | UUID phục vụ diversity/audit |
| `version` | `BIGINT` | Không | JPA `@Version` |
| `processing_stage` | `VARCHAR(30)` | Có | Script/next turn/scoring claim |
| `processing_token` | `CHAR(36)` | Có | UUID claim để tránh hai worker |
| `processing_started_at` | `DATETIME(6)` | Có | Recovery stale work |
| `processing_attempts` | `SMALLINT` | Không | Retry count |
| `next_retry_at` | `DATETIME(6)` | Có | Backoff |
| `failure_stage` | `VARCHAR(30)` | Có | Stage lỗi cuối |
| `status_message` | `VARCHAR(500)` | Có | Message an toàn cho người dùng |
| `end_reason` | `VARCHAR(40)` | Có | Lý do kết thúc |
| `overall_score` | `DECIMAL(5,2)` | Có | Denormalize từ report |
| `started_at` | `DATETIME(6)` | Có | Khi bấm bắt đầu |
| `last_activity_at` | `DATETIME(6)` | Không | Timeout 24 giờ |
| `completed_at` | `DATETIME(6)` | Có | Terminal completion |
| `created_at` | `DATETIME(6)` | Không | Clock UTC |
| `updated_at` | `DATETIME(6)` | Không | Clock UTC |

Index/unique:

```text
UNIQUE (user_id, creation_key)
INDEX  (user_id, status, last_activity_at)
INDEX  (user_id, completed_at)
INDEX  (profile_id)
INDEX  (job_description_id)
INDEX  (rubric_version_id)
INDEX  (status, processing_stage, next_retry_at)
INDEX  (last_activity_at)
```

`Idempotency-Key` là bắt buộc ở public create-session API. `creation_key` và request hash đều
`NOT NULL`; unique `(user_id, creation_key)` là concurrency-safe guard cuối cùng.

### 4.6. `session_context_snapshots`

```text
id                      BIGINT PK
session_id              BIGINT NOT NULL UNIQUE FK interview_sessions ON DELETE CASCADE
snapshot_schema_version VARCHAR(20) NOT NULL
profile_json            JSON NOT NULL
job_description_text    MEDIUMTEXT NOT NULL
job_description_hash    CHAR(64) NOT NULL
created_at              DATETIME(6) NOT NULL
```

`profile_json` chỉ chứa dữ liệu cần cho interview:

- Headline, target position, seniority, years of experience.
- Education summary khi liên quan.
- Skill IDs, names và categories.
- Project IDs, names, descriptions, role và tech stack.

Không đưa email, refresh token, storage key hoặc raw CV binary vào snapshot. Snapshot là bất biến.

### 4.7. `session_state_transitions`

```text
id          BIGINT PK
session_id  BIGINT NOT NULL FK interview_sessions ON DELETE CASCADE
from_status VARCHAR(30) NULL
to_status   VARCHAR(30) NOT NULL
actor       VARCHAR(20) NOT NULL
reason      VARCHAR(255) NULL
occurred_at DATETIME(6) NOT NULL
INDEX (session_id, occurred_at)
```

`actor`: `USER`, `SYSTEM`, `SCHEDULER`. Bảng append-only ở application layer.

### 4.8. `session_questions`

```text
id                  BIGINT PK
session_id          BIGINT NOT NULL FK interview_sessions ON DELETE CASCADE
ordinal             SMALLINT NOT NULL
question_text       TEXT NOT NULL
topic               VARCHAR(150) NOT NULL
competency          VARCHAR(100) NOT NULL
difficulty          SMALLINT NOT NULL
source_type         VARCHAR(30) NOT NULL
source_project_id   BIGINT NULL FK profile_projects ON DELETE SET NULL
source_skill_id     BIGINT NULL FK profile_skills ON DELETE SET NULL
source_jd_excerpt   TEXT NULL
question_signature  CHAR(64) NOT NULL
generation_seed     CHAR(36) NOT NULL
prompt_version      VARCHAR(20) NOT NULL
model_name          VARCHAR(100) NOT NULL
created_at          DATETIME(6) NOT NULL
UNIQUE (session_id, ordinal)
UNIQUE (session_id, question_signature)
CHECK (difficulty BETWEEN 1 AND 5)
```

Application validate project/skill thực sự thuộc profile snapshot của session. Không tin ID do AI
trả về.

### 4.9. `session_turns`

```text
id               BIGINT PK
session_id       BIGINT NOT NULL FK interview_sessions ON DELETE CASCADE
question_id      BIGINT NULL FK session_questions ON DELETE SET NULL
parent_turn_id   BIGINT NULL FK session_turns ON DELETE SET NULL
turn_index       INT NOT NULL
role             VARCHAR(20) NOT NULL
input_mode       VARCHAR(30) NOT NULL
content_text     MEDIUMTEXT NOT NULL
client_turn_id   VARCHAR(64) NULL
is_followup      BOOLEAN NOT NULL DEFAULT FALSE
followup_depth   SMALLINT NOT NULL DEFAULT 0
latency_ms       INT NULL
started_at       DATETIME(6) NOT NULL
ended_at         DATETIME(6) NULL
created_at       DATETIME(6) NOT NULL
UNIQUE (session_id, turn_index)
UNIQUE (session_id, client_turn_id)
INDEX (question_id)
INDEX (parent_turn_id)
CHECK (followup_depth BETWEEN 0 AND 2)
```

Quy tắc application:

- `client_turn_id` bắt buộc với `CANDIDATE`, `NULL` với `INTERVIEWER`.
- Follow-up chỉ áp dụng cho interviewer turn.
- Follow-up có `question_id` của base question và `parent_turn_id` của candidate answer vừa nhận.
- `parent_turn_id` và `question_id` phải thuộc cùng session.
- Không update content của turn đã persist.
- `next_turn_index` được tăng trong cùng transaction khi insert turn; không dùng `COUNT(*)`.

### 4.10. `voice_answer_attempts`

```text
id                  BIGINT PK
session_id          BIGINT NOT NULL FK interview_sessions ON DELETE CASCADE
question_id         BIGINT NOT NULL FK session_questions ON DELETE CASCADE
prompt_turn_id      BIGINT NOT NULL FK session_turns ON DELETE CASCADE
confirmed_turn_id   BIGINT NULL UNIQUE FK session_turns ON DELETE SET NULL
client_attempt_id   VARCHAR(64) NOT NULL
attempt_no          SMALLINT NOT NULL
status              VARCHAR(30) NOT NULL
version             BIGINT NOT NULL
storage_key         VARCHAR(500) NOT NULL
content_type        VARCHAR(100) NOT NULL
format              VARCHAR(20) NOT NULL
file_size_bytes     BIGINT NOT NULL
duration_ms         INT NOT NULL
checksum_sha256     CHAR(64) NOT NULL
raw_text            MEDIUMTEXT NULL
edited_text         MEDIUMTEXT NULL
stt_provider        VARCHAR(50) NULL
stt_confidence      DECIMAL(4,3) NULL
processing_token    CHAR(36) NULL
processing_started_at DATETIME(6) NULL
status_message      VARCHAR(500) NULL
audio_deleted_at    DATETIME(6) NULL
created_at          DATETIME(6) NOT NULL
transcribed_at      DATETIME(6) NULL
confirmed_at        DATETIME(6) NULL
UNIQUE (session_id, client_attempt_id)
UNIQUE (session_id, question_id, attempt_no)
```

`version` dùng optimistic locking riêng cho việc sửa transcript. Raw transcript bất biến sau khi
STT thành công. Confirm tạo candidate turn và nối `confirmed_turn_id` trong cùng transaction.

### 4.11. `turn_audio_assets`

Trong MVP, bảng này chỉ giữ audio TTS của interviewer turn. User recording nằm trong
`voice_answer_attempts`.

```text
id              BIGINT PK
turn_id         BIGINT NOT NULL UNIQUE FK session_turns ON DELETE CASCADE
status          VARCHAR(20) NOT NULL
storage_key     VARCHAR(500) NULL
format          VARCHAR(20) NULL
content_type    VARCHAR(100) NULL
duration_ms     INT NULL
file_size_bytes BIGINT NULL
tts_provider    VARCHAR(50) NULL
processing_token CHAR(36) NULL
processing_started_at DATETIME(6) NULL
status_message  VARCHAR(500) NULL
audio_deleted_at DATETIME(6) NULL
created_at      DATETIME(6) NOT NULL
ready_at        DATETIME(6) NULL
```

TTS failure không làm fail session. Question text luôn dùng được và người dùng có thể chuyển sang
text.

### 4.12. Scoring và report

#### `session_scores`

```text
id             BIGINT PK
session_id     BIGINT NOT NULL FK interview_sessions ON DELETE CASCADE
criterion_id   BIGINT NOT NULL FK rubric_criteria ON DELETE RESTRICT
criterion_code VARCHAR(50) NOT NULL
criterion_name VARCHAR(150) NOT NULL
score          DECIMAL(4,2) NOT NULL
max_score      DECIMAL(4,2) NOT NULL
level_no       SMALLINT NOT NULL
comment        TEXT NOT NULL
model_name     VARCHAR(100) NOT NULL
scored_at      DATETIME(6) NOT NULL
UNIQUE (session_id, criterion_id)
```

#### `score_evidences`

```text
id               BIGINT PK
session_score_id BIGINT NOT NULL FK session_scores ON DELETE CASCADE
turn_id          BIGINT NOT NULL FK session_turns ON DELETE CASCADE
quote_text       TEXT NOT NULL
start_offset     INT NOT NULL
end_offset       INT NOT NULL
INDEX (session_score_id)
INDEX (turn_id)
CHECK (start_offset >= 0 AND end_offset > start_offset)
```

#### `session_reports`

```text
id                 BIGINT PK
session_id         BIGINT NOT NULL UNIQUE FK interview_sessions ON DELETE CASCADE
result_status      VARCHAR(40) NOT NULL
overall_score      DECIMAL(5,2) NULL
is_partial         BOOLEAN NOT NULL
completion_ratio   DECIMAL(5,4) NOT NULL
assessed_weight    DECIMAL(4,3) NOT NULL
summary_text       TEXT NOT NULL
disclaimer         TEXT NOT NULL
language_code      VARCHAR(10) NOT NULL
model_name         VARCHAR(100) NULL
prompt_version     VARCHAR(20) NULL
duration_ms        INT NULL
generated_at       DATETIME(6) NOT NULL
```

`INSUFFICIENT_EVIDENCE` có `overall_score = NULL`, không có `session_scores` hoặc evidence giả.

Với partial session có ít nhất một answer:

- Chỉ lưu criterion mà provider có dẫn chứng hợp lệ.
- `assessed_weight` là tổng weight của các criterion đã chấm.
- Overall score được normalize trên phần weight đã đánh giá:

```text
overall = 100 × Σ((score / max_score) × weight) / assessed_weight
```

- `is_partial = true` và `completion_ratio = answered base questions / total base questions`.
- UI phải hiển thị đây là điểm tạm trên phần đã làm; không đưa vào biểu đồ so sánh full session
  nếu không gắn nhãn rõ.

#### `report_highlights`

```text
id            BIGINT PK
report_id     BIGINT NOT NULL FK session_reports ON DELETE CASCADE
type          VARCHAR(20) NOT NULL
content       TEXT NOT NULL
display_order SMALLINT NOT NULL
INDEX (report_id, type, display_order)
```

### 4.13. Immutability và delete behavior

| Dữ liệu | Quy tắc |
|---|---|
| JD `raw_text` | Không update |
| Confirmed JD | Không update trong MVP |
| Session context snapshot | Insert một lần, không update |
| Session question | Không update sau khi session `READY` |
| Session turn content | Append-only |
| State transition | Append-only |
| Voice raw transcript | Không update sau STT |
| Rubric version đã publish | Không update; tạo version mới |
| Score/evidence/report | Không update; scoring retry chỉ được chạy khi chưa có report |

Profile/JD/rubric dùng `RESTRICT` khi session còn tham chiếu. User-facing delete của CV/JD là xóa
mềm; lịch sử session vẫn đọc qua snapshot.

---

## 5. State machine contract

### 5.1. Transition hợp lệ

| From | Event | To | Actor | Awaiting action sau transition |
|---|---|---|---|---|
| `NULL` | Create row | `CREATED` | `USER` | `NONE` |
| `CREATED` | Dispatch generation | `SCRIPT_GENERATING` | `SYSTEM` | `NONE` |
| `SCRIPT_GENERATING` | Script persisted | `READY` | `SYSTEM` | `START_SESSION` |
| `SCRIPT_GENERATING` | Exhaust retries | `FAILED` | `SYSTEM` | `ENGINE_RETRY` |
| `READY` | Start | `IN_PROGRESS` | `USER` | `CANDIDATE_ANSWER` |
| `IN_PROGRESS` | Pause | `PAUSED` | `USER` | `NONE` |
| `PAUSED` | Resume | `IN_PROGRESS` | `USER` | Phục hồi từ turn/draft |
| `IN_PROGRESS` | All questions answered | `SCORING` | `SYSTEM` | `REPORT` |
| `IN_PROGRESS` | Complete early | `SCORING` | `USER` | `REPORT` |
| `READY/IN_PROGRESS/PAUSED` | Timeout | `SCORING` | `SCHEDULER` | `REPORT` |
| `SCORING` | Report committed | `COMPLETED` | `SYSTEM` | `NONE` |
| `SCORING` | Exhaust retries | `FAILED` | `SYSTEM` | `ENGINE_RETRY` |
| `READY/IN_PROGRESS/PAUSED` | Abandon | `ABANDONED` | `USER` | `NONE` |
| `FAILED` | Abandon retryable session | `ABANDONED` | `USER` | `NONE` |
| `FAILED` | Retry valid stage | Stage tương ứng | `USER` | Tùy stage |

Network disconnect không tự chuyển `PAUSED`, vì server không thể luôn phân biệt đóng tab, sleep và
mạng chập chờn. Session giữ `IN_PROGRESS`; resume đọc state từ DB. `PAUSED` chỉ dùng cho hành động
pause rõ ràng của người dùng. Timeout từ `READY` vẫn đi qua `SCORING`; do chưa có answer, workflow
tạo report `INSUFFICIENT_EVIDENCE` rồi chuyển `COMPLETED`. Cho phép abandon `FAILED` để lỗi provider
lặp lại không giữ vĩnh viễn một slot session mở của người dùng.

### 5.2. State transition transaction

`SessionStateMachine.transition(...)` là đường duy nhất được phép đổi status. Trong một transaction:

```text
SELECT/lock or verify @Version
  ↓
Validate transition table
  ↓
Update status, endReason, timestamps, awaitingAction
  ↓
Insert session_state_transitions
  ↓
Commit
```

Không đặt setter status public để service khác đổi trạng thái trực tiếp.

### 5.3. Awaiting action invariant

| Status | Awaiting action hợp lệ |
|---|---|
| `CREATED`, `SCRIPT_GENERATING` | `NONE` |
| `READY` | `START_SESSION` |
| `IN_PROGRESS` | `CANDIDATE_ANSWER`, `TRANSCRIPT_CONFIRMATION`, `ENGINE_RESPONSE`, `ENGINE_RETRY` |
| `PAUSED` | `NONE` |
| `SCORING` | `REPORT` |
| `FAILED` | `ENGINE_RETRY` |
| `COMPLETED`, `ABANDONED` | `NONE` |

Review package phải có checklist để đối chiếu ma trận này.

---

## 6. HTTP và API conventions

### 6.1. Authentication

- Toàn bộ endpoint trong tài liệu yêu cầu Bearer access token.
- Controller dùng `@IsUser` và lấy user bằng `@CurrentUser`; không tự decode JWT.
- Resource của user khác trả cùng mã `404 *_NOT_FOUND`, không trả `403` làm lộ sự tồn tại.
- Swagger dùng `@SecurityRequirement`, `@Tag` và `@Operation` như controller hiện tại.

### 6.2. Content types

- JSON endpoint: `application/json`.
- JD/audio upload: `multipart/form-data`.
- API không trả storage key hoặc provider error body.
- File/audio download trả presigned URL ngắn hạn qua JSON, không redirect mặc định.

### 6.3. Timestamp và enum

- Timestamp dùng ISO-8601 UTC, ví dụ `2026-08-26T07:30:00Z`.
- Enum serialize bằng tên uppercase trong tài liệu.
- Numeric score trả number, không trả chuỗi.
- Collection trả `[]`, không trả `null`.

### 6.4. Async response

Các thao tác gọi AI/STT/scoring trả `202 Accepted` sau khi trạng thái đầu vào đã được commit:

- Tạo session/script.
- Submit text answer.
- Upload voice attempt để STT.
- Confirm voice answer, sau đó sinh follow-up.
- Complete session để scoring.
- Retry workflow.

Frontend poll `GET /api/sessions/{id}`. MVP chưa cần SSE/WebSocket.

`Retry-After: 1` có thể được trả để gợi ý polling không nhanh hơn một giây.

### 6.5. Idempotency và optimistic concurrency

- `POST /api/sessions` bắt buộc header `Idempotency-Key`, trim 1–128 ký tự.
- Text answer nhận `clientTurnId` trong body.
- Voice upload nhận `clientAttemptId` trong metadata part.
- Mọi lệnh làm tiến session nhận `expectedVersion`.
- Transcript edit nhận `expectedAttemptVersion`.
- Trùng idempotency key trả lại resource cũ và cùng kết quả, không trả conflict.
- Version cũ trả `409 SESSION_VERSION_CONFLICT` kèm snapshot mới nhất qua lần GET tiếp theo.

### 6.6. Error response

Dùng nguyên contract `ApiError` hiện tại:

```json
{
  "timestamp": "2026-08-26T07:30:00Z",
  "status": 409,
  "code": "SESSION_INVALID_STATE",
  "message": "Phiên này không thể nhận câu trả lời ở trạng thái hiện tại",
  "path": "/api/sessions/42/answers",
  "fieldErrors": null
}
```

Không trả stack trace, SQL, provider message, prompt, storage key hoặc raw exception message.

### 6.7. Pagination

Danh sách JD/session dùng response riêng, không expose trực tiếp Spring `Page`:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Giới hạn `size` từ 1 tới 50, mặc định 20.

---

## 7. Job Description API

Base path: `/api/job-descriptions`.

Availability tại review M02: toàn bộ route JD trong mục này đã được triển khai. Hai route file dùng
parser cục bộ PDFBox/strict UTF-8; chưa gọi AI để parse requirement có cấu trúc.

### 7.1. Tạo JD từ text

```http
POST /api/job-descriptions/text
Content-Type: application/json
```

```json
{
  "title": "Fresher Java Developer — ABC Company",
  "text": "Responsibilities ... Requirements ..."
}
```

Validation:

- `title`: trim, 1–200 ký tự.
- `text`: trim, 100–50.000 ký tự theo contract M00.
- Nội dung chỉ có whitespace bị từ chối.

Response `201 Created`:

```json
{
  "id": 12,
  "title": "Fresher Java Developer — ABC Company",
  "sourceType": "TEXT",
  "status": "DRAFT",
  "originalFilename": null,
  "rawText": "Responsibilities ... Requirements ...",
  "confirmedText": "Responsibilities ... Requirements ...",
  "confirmedAt": null,
  "createdAt": "2026-08-26T07:30:00Z",
  "updatedAt": "2026-08-26T07:30:00Z"
}
```

Header `Location: /api/job-descriptions/12`.

### 7.2. Tạo JD từ file

```http
POST /api/job-descriptions/file
Content-Type: multipart/form-data
```

Parts:

```text
title: string, optional; fallback là tên file an toàn
file: PDF hoặc TXT, required
```

Validation mặc định:

- Tối đa 2 MB.
- PDF tối đa 20 trang, không encrypted, mở được bằng PDFBox, magic bytes `%PDF`.
- TXT phải decode UTF-8 nghiêm ngặt; invalid byte sequence bị từ chối.
- Text sau extract từ 100–50.000 ký tự.
- Không tin extension hoặc declared content type đơn lẻ.

Backend validate và extract trước, sau đó upload file gốc vào key:

```text
jd/{userId}/{uuid}.{validatedExtension}
```

Response `201 Created` có cùng shape với text response, thêm filename. `rawText` và
`confirmedText` ban đầu giống nhau.

Nếu object upload thành công nhưng DB insert thất bại, gọi storage delete theo best effort và log
chỉ key/id kỹ thuật, không log nội dung.

### 7.3. Danh sách và chi tiết

```http
GET /api/job-descriptions?page=0&size=20
GET /api/job-descriptions/{jobDescriptionId}
GET /api/job-descriptions/{jobDescriptionId}/file
```

List chỉ trả summary, không trả toàn bộ text để tránh response lớn. Response danh sách dùng
envelope chung:

```json
{
  "items": [
    {
      "id": 12,
      "title": "Fresher Backend Developer",
      "sourceType": "TEXT",
      "status": "DRAFT",
      "originalFilename": null,
      "confirmedAt": null,
      "createdAt": "2026-08-26T07:30:00Z",
      "updatedAt": "2026-08-26T07:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Summary tuyệt đối không chứa `rawText` hoặc `confirmedText`; hai field này chỉ có ở endpoint chi
tiết. Mặc định sắp xếp `createdAt DESC`, sau đó `id DESC` để pagination ổn định.

File endpoint trả:

```json
{
  "url": "https://...",
  "expiresAt": "2026-08-26T07:35:00Z"
}
```

Text-source JD gọi `/file` trả `409 JD_HAS_NO_FILE`.

### 7.4. Sửa draft

```http
PUT /api/job-descriptions/{jobDescriptionId}
Content-Type: application/json
```

```json
{
  "title": "Fresher Backend Developer",
  "confirmedText": "Edited requirements ..."
}
```

- Chỉ update JD `DRAFT` và active.
- Không sửa `rawText`.
- Không thay source/file ở endpoint này.
- Trả `200 OK` với full response.

### 7.5. Xác nhận

```http
POST /api/job-descriptions/{jobDescriptionId}/confirm
```

- Không body.
- Đặt status `READY`, `confirmedAt = now`.
- Idempotent: confirm lần hai trả cùng timestamp và `200 OK`.
- Sau confirm không sửa đè trong MVP.

### 7.6. Xóa mềm

```http
DELETE /api/job-descriptions/{jobDescriptionId}
```

- Trả `204 No Content`.
- Chỉ đặt `is_active = false`.
- Session cũ vẫn đọc snapshot.
- JD inactive không được dùng tạo session mới.
- File gốc được giữ theo retention trong khi còn session tham chiếu; cleanup vật lý là job riêng.

### 7.7. Concurrency và giới hạn M01

- Tối đa 30 JD active/user theo `app.jd.max-per-user`.
- Create khóa hàng `user_accounts` của current user trước khi count/insert để hai request đồng thời
  không cùng vượt quota.
- Update, confirm và delete lấy ownership-scoped JD bằng `PESSIMISTIC_WRITE`.
- Confirm lần hai không thay đổi `confirmedAt` hoặc `updatedAt`.
- Checksum là SHA-256 lowercase của source text sau khi strip và chuẩn hóa line ending; sửa
  `confirmedText` không thay đổi checksum/raw source.

---

## 8. Session API

Base path: `/api/sessions`.

### 8.1. Tạo session và sinh kịch bản

```http
POST /api/sessions
Idempotency-Key: 01J... (required)
Content-Type: application/json
```

```json
{
  "profileId": 7,
  "jobDescriptionId": 12,
  "difficulty": "MEDIUM",
  "mode": "VOICE_TURN_BASED",
  "languageCode": "vi"
}
```

Validation/service checks theo đúng thứ tự:

1. Profile active thuộc current user, nếu không có trả `404 PROFILE_NOT_FOUND`.
2. Profile đã confirm, nếu chưa trả `409 PROFILE_NOT_CONFIRMED`.
3. JD active thuộc current user, nếu không có trả `404 JD_NOT_FOUND`.
4. JD `READY`, nếu chưa trả `409 JD_NOT_CONFIRMED`.
5. Difficulty/mode/language được hỗ trợ.
6. Chưa vượt giới hạn năm session mở của user; kiểm tra concurrency-safe nằm trong transaction
   bên dưới.
7. Rubric code MVP có current published version; nếu không trả `503 RUBRIC_NOT_AVAILABLE`.

Transaction tạo session:

- Lock hàng `user_accounts` của current user bằng `PESSIMISTIC_WRITE` để serialize create-session
  của cùng một user.
- Re-check `(user_id, creation_key)` sau khi có lock; idempotent replay không bị tính quota lần nữa.
- Count các status thuộc `ACTIVE`; nếu đã có năm session thì trả `SESSION_LIMIT_REACHED`.
- Insert session và context snapshot.
- Ghi transition `NULL -> CREATED`.
- Ghi transition `CREATED -> SCRIPT_GENERATING`.
- Set processing claim cho script generation.
- Commit rồi mới dispatch AI work.

User-row lock chỉ được giữ trong transaction database ngắn này; tuyệt đối không giữ khi gọi AI.
Unique `(user_id, creation_key)` vẫn là guard cuối nếu một code path nội bộ bỏ qua serialization.

Response `202 Accepted`:

```json
{
  "id": 42,
  "status": "SCRIPT_GENERATING",
  "awaitingAction": "NONE",
  "version": 0,
  "createdAt": "2026-08-26T07:30:00Z"
}
```

Headers:

```text
Location: /api/sessions/42
Retry-After: 1
```

Thiếu/rỗng `Idempotency-Key` trả `400 IDEMPOTENCY_KEY_REQUIRED`. Nếu key đã tồn tại với cùng user,
trả lại session cũ theo cùng response shape và `Location`; frontend tiếp tục poll resource đó. Nếu
cùng key nhưng body có fingerprint khác, trả `409 IDEMPOTENCY_KEY_REUSED`.

### 8.2. Danh sách session

```http
GET /api/sessions?scope=ACTIVE&page=0&size=20
```

`scope`:

- `ACTIVE`: `CREATED`, `SCRIPT_GENERATING`, `READY`, `IN_PROGRESS`, `PAUSED`, `SCORING` và
  `FAILED`; đây cũng là các trạng thái được tính vào giới hạn năm session mở.
- `HISTORY`: `COMPLETED` và `ABANDONED`.
- `ALL`: tất cả.

Sắp xếp:

- Active: `lastActivityAt DESC`.
- History: `completedAt DESC`, fallback `updatedAt DESC`.

Summary response:

```json
{
  "id": 42,
  "profileId": 7,
  "profileHeadline": "Fresher Java Developer",
  "jobDescriptionId": 12,
  "jobDescriptionTitle": "Fresher Backend Developer",
  "difficulty": "MEDIUM",
  "mode": "VOICE_TURN_BASED",
  "status": "IN_PROGRESS",
  "awaitingAction": "CANDIDATE_ANSWER",
  "answeredQuestionCount": 2,
  "totalQuestionCount": 6,
  "overallScore": null,
  "lastActivityAt": "2026-08-26T07:40:00Z",
  "createdAt": "2026-08-26T07:30:00Z"
}
```

### 8.3. Chi tiết và resume

```http
GET /api/sessions/{sessionId}
```

Không trả các câu hỏi tương lai chưa được hỏi. Response:

```json
{
  "id": 42,
  "profile": {
    "id": 7,
    "headline": "Fresher Java Developer"
  },
  "jobDescription": {
    "id": 12,
    "title": "Fresher Backend Developer"
  },
  "difficulty": "MEDIUM",
  "mode": "VOICE_TURN_BASED",
  "languageCode": "vi",
  "status": "IN_PROGRESS",
  "awaitingAction": "CANDIDATE_ANSWER",
  "version": 8,
  "answeredQuestionCount": 2,
  "totalQuestionCount": 6,
  "currentPrompt": {
    "turnId": 205,
    "baseQuestionId": 103,
    "ordinal": 3,
    "text": "Trong dự án ...",
    "isFollowUp": false,
    "followUpDepth": 0,
    "audioStatus": "READY"
  },
  "turns": [
    {
      "id": 201,
      "turnIndex": 0,
      "role": "INTERVIEWER",
      "inputMode": "TEXT",
      "content": "...",
      "isFollowUp": false,
      "followUpDepth": 0,
      "createdAt": "2026-08-26T07:31:00Z"
    }
  ],
  "voiceDraft": null,
  "statusMessage": null,
  "lastActivityAt": "2026-08-26T07:40:00Z",
  "startedAt": "2026-08-26T07:31:00Z",
  "completedAt": null
}
```

`turns` nhỏ và bị giới hạn bởi số câu/follow-up của MVP nên trả toàn bộ, không cần pagination.

### 8.4. Xem rubric đã chốt

```http
GET /api/sessions/{sessionId}/rubric
```

Cho phép từ `READY` trở đi. Response gồm rubric name, version và danh sách criterion/level theo
display order. Không trả descriptor của rubric khác/current mới hơn.

### 8.5. Bắt đầu

```http
POST /api/sessions/{sessionId}/start
Content-Type: application/json
```

```json
{
  "expectedVersion": 2
}
```

Chỉ hợp lệ ở `READY`:

1. Chuyển `READY -> IN_PROGRESS`.
2. Chọn base question ordinal 1.
3. Tạo interviewer turn đầu tiên.
4. Set `currentQuestionOrdinal = 1`, `awaitingAction = CANDIDATE_ANSWER`.
5. Set `startedAt` và `lastActivityAt`.
6. Tạo TTS asset `PENDING` nếu mode voice.
7. Commit, sau đó dispatch TTS.

Trả `200 OK` với session detail. Gọi lại với session đã bắt đầu và cùng version cũ trả conflict;
frontend dùng detail mới nhất để resume, không tạo lại turn đầu tiên.

### 8.6. Pause và resume

```http
POST /api/sessions/{sessionId}/pause
POST /api/sessions/{sessionId}/resume
```

Body:

```json
{
  "expectedVersion": 8
}
```

- Pause chỉ từ `IN_PROGRESS`, không pause khi đang `ENGINE_RESPONSE` hoặc có voice attempt đang
  `TRANSCRIBING`.
- Resume chỉ từ `PAUSED`.
- Resume phục hồi `awaitingAction` từ last turn/voice attempt trong transaction.
- Cả hai cập nhật `lastActivityAt` và transition log.

### 8.7. Submit text answer

```http
POST /api/sessions/{sessionId}/answers
Content-Type: application/json
```

```json
{
  "promptTurnId": 205,
  "content": "Trong dự án đó em chọn Redis vì ...",
  "clientTurnId": "01J67X...",
  "expectedVersion": 8
}
```

Validation:

- `content`: trim, 1–10.000 ký tự.
- `promptTurnId` là interviewer turn hiện tại.
- Session `IN_PROGRESS` và đang `CANDIDATE_ANSWER`.
- Text mode luôn hợp lệ; voice turn-based cũng cho text fallback.

Transaction A:

1. Lock/verify session version.
2. Kiểm tra idempotency.
3. Cấp `nextTurnIndex`.
4. Insert candidate turn với `inputMode = TEXT`.
5. Set `awaitingAction = ENGINE_RESPONSE`, processing claim và `lastActivityAt`.
6. Commit.

Sau commit, worker gọi follow-up decider. Response ngay `202 Accepted`:

```json
{
  "sessionId": 42,
  "candidateTurnId": 206,
  "status": "IN_PROGRESS",
  "awaitingAction": "ENGINE_RESPONSE",
  "version": 9
}
```

Transaction B sau AI:

- Validate decision/evidence/limits.
- Tạo follow-up interviewer turn hoặc base question kế tiếp.
- Nếu hết câu, chuyển `IN_PROGRESS -> SCORING` và dispatch scoring sau commit.
- Clear processing claim và cập nhật awaiting action.

### 8.8. Complete sớm

```http
POST /api/sessions/{sessionId}/complete
```

```json
{
  "expectedVersion": 9
}
```

- Chỉ hợp lệ khi không có engine/STT operation đang chạy.
- Có candidate answer: `SCORING`, `endReason = USER_COMPLETED_EARLY`, trả `202`.
- Không có answer: tạo report `INSUFFICIENT_EVIDENCE`, chuyển `COMPLETED` trong cùng workflow.

### 8.9. Abandon

```http
POST /api/sessions/{sessionId}/abandon
```

- Dùng khi người dùng không muốn nhận report.
- Chuyển `READY/IN_PROGRESS/PAUSED/FAILED -> ABANDONED`.
- Không scoring.
- Idempotent nếu session đã `ABANDONED`; terminal state khác trả conflict.

### 8.10. Retry workflow

```http
POST /api/sessions/{sessionId}/retry
```

Body có `expectedVersion`. Cho phép khi:

- `FAILED` ở script generation.
- `FAILED` ở scoring.
- `IN_PROGRESS + ENGINE_RETRY` ở next-turn generation.

Server xác định stage từ persisted data, không cho client chọn stage. Trả `202 Accepted`.

---

## 9. Voice turn-based API

### 9.1. Upload recording và bắt đầu STT

```http
POST /api/sessions/{sessionId}/voice-attempts
Content-Type: multipart/form-data
```

Parts:

```text
metadata: application/json
file: audio file
```

Metadata:

```json
{
  "promptTurnId": 205,
  "clientAttemptId": "01J67Y...",
  "durationMs": 84200,
  "expectedVersion": 8
}
```

Validation đã khóa trong M00:

- Session mode `VOICE_TURN_BASED`, status `IN_PROGRESS`.
- Prompt turn là prompt hiện tại.
- Audio không rỗng, tối đa 15 MB và 300.000 ms.
- Chấp nhận browser-native format mà STT provider đã được xác nhận hỗ trợ trực tiếp:
  `audio/webm` Opus và `audio/mp4`/AAC; có thể thêm `audio/mpeg` hoặc `audio/ogg` sau khi xác minh
  tương thích thực tế.
- Không tin duration do client gửi; provider/metadata parser phải xác nhận lại khi có thể.

Storage key:

```text
interview-audio/{userId}/{sessionId}/answers/{uuid}.{validatedExtension}
```

Flow:

1. Validate/read bytes ngoài transaction.
2. Upload object.
3. Trong transaction tạo attempt `TRANSCRIBING`, update session
   `awaitingAction = TRANSCRIPT_CONFIRMATION`, update `lastActivityAt`.
4. Nếu DB insert fail, xóa object best effort.
5. Sau commit dispatch STT.

Response `202 Accepted`:

```json
{
  "id": 301,
  "status": "TRANSCRIBING",
  "version": 0,
  "rawText": null,
  "editedText": null,
  "durationMs": 84200,
  "statusMessage": null,
  "createdAt": "2026-08-26T07:42:00Z"
}
```

### 9.2. Đọc attempt

```http
GET /api/sessions/{sessionId}/voice-attempts/{attemptId}
```

- `TRANSCRIBING`: frontend tiếp tục spinner/poll.
- `TRANSCRIBED`: hiển thị raw/edited text để sửa.
- `FAILED`: hiển thị message an toàn, cho retry recording hoặc text fallback.
- Không trả storage key hay raw provider response.

### 9.3. Sửa transcript

```http
PUT /api/sessions/{sessionId}/voice-attempts/{attemptId}/transcript
```

```json
{
  "editedText": "Bản em đã sửa ...",
  "expectedAttemptVersion": 1
}
```

- Chỉ attempt `TRANSCRIBED`.
- Trim, 1–10.000 ký tự.
- Không update raw text.
- `editedText` giống raw sau normalize thì lưu `NULL`.
- Update `lastActivityAt` vì đây là hoạt động thực của user.
- Trả `200 OK` với attempt mới và version mới.

### 9.4. Ghi âm lại

Frontend gọi lại endpoint upload với `clientAttemptId` mới. Attempt cũ chưa bị xóa ngay. Khi
attempt mới STT thành công, service đánh dấu attempt draft cũ của cùng prompt là `DISCARDED`.

Nếu attempt mới thất bại, attempt `TRANSCRIBED` trước đó vẫn có thể được xác nhận. Quy tắc này
tránh mất một transcript tốt chỉ vì lần ghi lại lỗi.

### 9.5. Confirm voice answer

```http
POST /api/sessions/{sessionId}/voice-attempts/{attemptId}/confirm
```

```json
{
  "clientTurnId": "01J67Z...",
  "expectedSessionVersion": 9,
  "expectedAttemptVersion": 2
}
```

Trong một transaction:

1. Verify session/attempt ownership và versions.
2. Attempt phải `TRANSCRIBED` và thuộc prompt hiện tại.
3. Chọn final text: `editedText` nếu có, ngược lại `rawText`.
4. Tạo candidate turn `VOICE_TURN_BASED` idempotently.
5. Nối `confirmedTurnId`, chuyển attempt `CONFIRMED`.
6. Mark các attempt khác của prompt là `DISCARDED`.
7. Set session `ENGINE_RESPONSE` và processing claim.
8. Commit rồi dispatch follow-up.

Trả `202 Accepted` cùng shape với text answer response.

### 9.6. TTS audio

```http
GET  /api/sessions/{sessionId}/turns/{turnId}/audio
POST /api/sessions/{sessionId}/turns/{turnId}/audio/retry
```

Ready response:

```json
{
  "status": "READY",
  "url": "https://...",
  "expiresAt": "2026-08-26T07:50:00Z",
  "durationMs": 8400
}
```

Pending response trả `200` với `status = PENDING/GENERATING`, `url = null`; frontend vẫn hiển thị
question text. Retry chỉ hợp lệ khi asset `FAILED`.

---

## 10. Report API

```http
GET /api/sessions/{sessionId}/report
```

- Chỉ trả report của current user.
- Khi session `SCORING`, trả `409 REPORT_NOT_READY` hoặc frontend đọc session và chưa gọi endpoint.
- Khi scoring `FAILED`, trả `409 SESSION_RETRY_REQUIRED`.
- Khi completed, trả `200 OK`.

Response:

```json
{
  "sessionId": 42,
  "resultStatus": "SCORED",
  "overallScore": 76.5,
  "partial": false,
  "completionRatio": 1.0,
  "assessedWeight": 1.0,
  "summary": "...",
  "disclaimer": "Đây là kết quả từ công cụ luyện tập, không phải chứng nhận năng lực.",
  "criteria": [
    {
      "code": "TECHNICAL_DEPTH",
      "name": "Độ sâu kỹ thuật",
      "score": 3.0,
      "maxScore": 4.0,
      "level": 3,
      "comment": "...",
      "evidences": [
        {
          "turnId": 206,
          "quote": "em chọn Redis vì ...",
          "startOffset": 18,
          "endOffset": 41
        }
      ]
    }
  ],
  "strengths": [],
  "improvements": [],
  "nextActions": [],
  "generatedAt": "2026-08-26T08:00:00Z"
}
```

`INSUFFICIENT_EVIDENCE` trả `overallScore = null`, arrays rỗng hoặc lời hướng dẫn bắt đầu phiên
mới, không tạo criterion score.

---

## 11. AI contracts

### 11.1. Ports

```java
public interface InterviewQuestionGenerator {
    ScriptGenerationOutcome generate(ScriptGenerationInput input);
}

public interface InterviewFollowUpDecider {
    FollowUpOutcome decide(FollowUpInput input);
}

public interface InterviewScorer {
    ScoringOutcome score(ScoringInput input);
}

```

Các input/output là immutable records. Service không phụ thuộc trực tiếp
`GoogleGenAiChatModel`.

### 11.2. Versioned resources

```text
src/main/resources/ai/interview-script-prompt-v1.txt
src/main/resources/ai/interview-script-schema-v1.json
src/main/resources/ai/interview-followup-prompt-v1.txt
src/main/resources/ai/interview-followup-schema-v1.json
src/main/resources/ai/interview-score-prompt-v1.txt
src/main/resources/ai/interview-score-schema-v1.json
```

Prompt version được ghi vào question/report. Thay đổi incompatible phải tạo `v2`, không sửa nghĩa
của dữ liệu đã persist theo `v1`.

### 11.3. Script generation input

Input chỉ lấy từ session context snapshot:

```json
{
  "languageCode": "vi",
  "difficulty": "MEDIUM",
  "questionCount": 6,
  "generationSeed": "uuid",
  "profile": {
    "headline": "...",
    "skills": [{"id": 11, "name": "Spring Boot"}],
    "projects": [{"id": 18, "name": "...", "description": "..."}]
  },
  "jobDescription": "...",
  "excludedQuestionSignatures": []
}
```

Output tối thiểu:

```json
{
  "questions": [
    {
      "ordinal": 1,
      "questionText": "...",
      "topic": "Transaction management",
      "competency": "TECHNICAL_DEPTH",
      "difficulty": 3,
      "sourceType": "CV_JD_MATCH",
      "sourceProjectId": 18,
      "sourceSkillId": 11,
      "sourceJdExcerpt": "Experience with Spring Boot",
      "signatureConcept": "spring-transaction-boundary"
    }
  ]
}
```

Server validation:

- Đúng question count và ordinal liên tục từ 1.
- Question text/topic/competency không blank và nằm trong length limit.
- Difficulty 1–5.
- Source IDs tồn tại trong snapshot và thuộc selected profile.
- JD excerpt là substring sau normalize hoặc được bỏ nếu không khớp.
- Không có duplicate normalized text/signature.
- Diversity với ba session gần nhất đạt ngưỡng.
- Không có markdown/code fence trong field text sau parse.

#### 11.3.1. Runtime contract đã triển khai trong M05

- System message giữ instruction; user message chỉ chứa JSON profile/JD có nhãn untrusted. Prompt
  và schema được resolve theo `app.interview.ai.script-prompt-version` lúc startup.
- Provider call chạy sau read transaction chuẩn bị input và trước write transaction persist output.
- Source ID phải đồng thời xuất hiện trong immutable snapshot và còn thuộc profile đã chọn. DB FK
  không được dùng thay cho bước trust-boundary validation này.
- Server tự tạo SHA-256 signature từ source type/entity, competency và normalized concept. Seed chỉ
  phục vụ audit.
- Query diversity chỉ lấy tối đa ba session gần nhất cùng profile/JD hash. Không câu normalized nào
  được trùng ba session này; ít nhất 70% signature phải khác session gần nhất.
- Final write khóa profile cho diversity check đồng thời, rồi kiểm pessimistic session lock và
  processing token. Questions, question count, READY transition và optimistic version cùng commit
  hoặc cùng rollback.
- Chỉ diversity rejection được regenerate ngay một lần. Provider/malformed/validation failures đi
  ra bằng reason + retryable flag + message đã sanitize để M06 quyết định retry/session failure.

### 11.4. Follow-up input/output

Chỉ gửi context cần thiết:

- Base question.
- Tối đa các turn thuộc base question hiện tại.
- Candidate answer vừa nhận.
- Follow-up depth/count còn lại.
- Relevant CV/JD excerpt từ snapshot.

Không cần gửi toàn bộ lịch sử mọi câu nếu không phục vụ quyết định.

Output:

```json
{
  "decision": "FOLLOW_UP",
  "questionText": "...",
  "evidenceQuote": "...",
  "reason": "Câu trả lời chưa giải thích trade-off"
}
```

Server là authority cuối:

- Evidence quote phải nằm trong answer vừa gửi.
- Hết follow-up budget thì override thành `NEXT_QUESTION`.
- Nếu AI trả `END_INTERVIEW` khi còn base question, server chỉ chấp nhận khi business rule cho phép;
  mặc định MVP không chấp nhận.
- Provider timeout sau khi answer đã persist chuyển `awaitingAction = ENGINE_RETRY`, không mất turn.

### 11.5. Scoring input/output

Input:

- Đúng rubric version, criteria và level descriptor.
- Danh sách candidate turn với stable `turnId`/`turnIndex`.
- Base question/follow-up tương ứng.
- Completion ratio và end reason.

Output:

```json
{
  "criteria": [
    {
      "criterionCode": "TECHNICAL_DEPTH",
      "levelNo": 3,
      "score": 3.0,
      "comment": "...",
      "evidences": [
        {
          "turnId": 206,
          "quoteText": "..."
        }
      ]
    }
  ],
  "summary": "...",
  "strengths": ["..."],
  "improvements": ["..."],
  "nextActions": ["..."]
}
```

Validation:

- Criterion code thuộc locked rubric và không trùng.
- Score khớp level/allowed range.
- Turn là candidate turn của session.
- Quote là substring thật; server tự tính offset, không tin offset từ AI.
- Criterion không có evidence hợp lệ không được persist.
- Highlight được giới hạn số lượng/kích thước.

### 11.6. Prompt injection và privacy

- CV/JD/answer được đặt trong data delimiters rõ ràng.
- System prompt ghi rõ không làm theo instruction nằm trong CV/JD/answer.
- Không cho model tự quyết định database ID, state transition, score formula hoặc follow-up cap.
- Không log full prompt/model output.
- Log model, prompt version, duration, token usage và stable session ID khi cần vận hành.

---

## 12. Async workflow, retry và recovery

### 12.1. Nguyên tắc DB-first

```text
HTTP request
  ↓
Commit state/input/processing claim
  ↓
Dispatch background work
  ↓
Call provider ngoài transaction
  ↓
Commit validated output
```

Không dispatch provider trước khi input đã commit.

### 12.2. Processing claim

Worker chỉ chạy khi claim được session/attempt bằng atomic update và token UUID. Claim có timestamp.
Một worker chỉ commit nếu token trên row vẫn là token của nó.

Ví dụ logic khái niệm:

```sql
UPDATE interview_sessions
SET processing_token = :token,
    processing_started_at = :now
WHERE id = :id
  AND processing_stage = :stage
  AND (processing_token IS NULL OR processing_started_at < :staleBefore);
```

Affected rows bằng 0 nghĩa là worker khác đã claim.

### 12.3. Retry policy mặc định

| Workflow | Max attempt | Cấu hình timeout ban đầu | Backoff |
|---|---:|---:|---|
| Script generation | 2 | 12 giây/call | 1s |
| Next-turn decision | 3 | 8 giây/call | 1s, 3s |
| Scoring/report | 3 | 50 giây/call | 2s, 10s |
| STT | 2 | 7 giây/call | 1s |
| TTS | 2 | 5 giây/call | 1s |

Không retry lỗi validation, unsupported media, missing credential hoặc malformed output vô hạn.
Rate limit/5xx/network timeout có thể retry theo bảng.

### 12.4. Recovery

`InterviewWorkflowRecoveryJob` chạy khi application ready và định kỳ:

- Tìm `SCRIPT_GENERATING` có claim stale hoặc chưa claim.
- Tìm `IN_PROGRESS + ENGINE_RESPONSE/ENGINE_RETRY` mà candidate turn cuối chưa có interviewer turn
  sau nó.
- Tìm `SCORING` chưa có report.
- Tìm voice attempt `TRANSCRIBING` stale.
- Tìm TTS asset `PENDING/GENERATING` stale.

Recovery claim lại atomically. Không reset mọi work đang chạy khi một instance khác boot. Điều này
tránh điểm yếu của việc quét và đánh fail toàn bộ như một ứng dụng single-instance.

### 12.5. Executor

Tách executor theo workload:

```text
interviewAiExecutor   — script, follow-up, scoring
interviewVoiceExecutor — STT/TTS
```

Queue phải bounded. Queue full không mất work vì trạng thái/claim nằm trong DB; recovery job sẽ
dispatch lại. Không dùng executor hiện tại của CV parser làm hàng đợi chung để voice/scoring không
làm nghẽn CV parsing.

---

## 13. Transaction boundaries

| Use case | Transaction |
|---|---|
| Create text JD | Insert một transaction |
| Create file JD | Validate/extract/upload ngoài TX; insert trong TX; compensate object nếu insert fail |
| Edit/confirm JD | Mỗi command một TX |
| Create session | Session + snapshot + rubric lock + transitions + claim trong một TX |
| AI script call | Ngoài TX |
| Persist script | Questions + session state + transition trong một TX |
| Start session | Status + first interviewer turn + TTS pending row trong một TX |
| Submit text | Candidate turn + session cursor/claim trong một TX |
| Follow-up call | Ngoài TX |
| Persist next turn | Interviewer turn/counters/session phase trong một TX |
| Upload voice | Storage ngoài TX; attempt/session update trong TX; compensation khi cần |
| STT call | Ngoài TX |
| Persist raw transcript | Attempt status/raw text trong một TX |
| Confirm transcript | Attempt + candidate turn + session cursor/claim trong một TX |
| Scoring call | Ngoài TX |
| Persist report | Scores + evidences + report + highlights + overall + transition trong một TX |
| Expire | Mỗi session một TX, rồi scoring ngoài TX |

Pure query service dùng `@Transactional(readOnly = true)` khi cần lazy/batch mapping.

---

## 14. Storage và file handling

### 14.1. Mở rộng storage port

`FileStorageService` hiện có upload/download/presign. MVP cần thêm:

```java
void delete(String key);
```

Dùng cho compensation và audio retention. Delete object không tồn tại nên idempotent.

### 14.2. Object key

```text
cv/{userId}/{uuid}.pdf                                      (giữ hiện tại)
jd/{userId}/{uuid}.pdf|txt
interview-audio/{userId}/{sessionId}/answers/{uuid}.webm|mp4
interview-audio/{userId}/{sessionId}/tts/{turnId}.mp3
```

- Không dùng filename người dùng làm key.
- Không trả key ra API.
- Bucket private.
- Presigned GET TTL giữ ngắn, mặc định năm phút.

### 14.3. Upload limit toàn cục

Handler `MaxUploadSizeExceededException` trả mã chung `UPLOAD_TOO_LARGE`; validator từng feature
vẫn trả mã CV/JD cụ thể.

Trong M02, servlet giữ mức 6/8 MB để lớn hơn CV 5 MB và JD 2 MB:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 6MB
      max-request-size: 8MB
```

App limits:

- CV: giữ 5 MB.
- JD: 2 MB.
- Audio: 15 MB; M12 sẽ nâng servlet limit trước khi mở upload audio.

Global limit luôn lớn hơn hoặc bằng feature limit lớn nhất để request bình thường đi tới validator
nghiệp vụ.

### 14.4. Audio provider prerequisite

Trước chunk voice phải chọn STT provider thỏa mãn:

- Nhận trực tiếp WebM/Opus từ Chrome và MP4/AAC từ browser phù hợp.
- Không bắt JVM convert toàn bộ sang WAV cho MVP.
- Trả transcript tiếng Việt/tiếng Anh đủ tốt cho tập người dùng mục tiêu.
- Hoàn thành trong SLO với audio tối đa đã công bố.

Nếu provider không đáp ứng browser-native formats thì phải bổ sung một transcoding service như
FFmpeg; đây là thay đổi hạ tầng, không được giấu trong implementation detail.

---

## 15. Configuration properties

Tất cả là validated `@ConfigurationProperties`; không rải `@Value`.

```yaml
app:
  jd:
    max-file-size-bytes: ${JD_MAX_FILE_SIZE_BYTES:2097152}
    max-pages: ${JD_MAX_PAGES:20}
    min-text-chars: ${JD_MIN_TEXT_CHARS:100}
    max-text-chars: ${JD_MAX_TEXT_CHARS:50000}
    max-per-user: ${JD_MAX_PER_USER:30}

  interview:
    enabled: ${INTERVIEW_ENABLED:true}
    inactivity-timeout-hours: ${INTERVIEW_INACTIVITY_TIMEOUT_HOURS:24}
    expiry-cron: ${INTERVIEW_EXPIRY_CRON:0 */5 * * * *}
    recovery-cron: ${INTERVIEW_RECOVERY_CRON:30 */2 * * * *}
    processing-lease-seconds: ${INTERVIEW_PROCESSING_LEASE_SECONDS:90}
    max-active-per-user: ${INTERVIEW_MAX_ACTIVE_PER_USER:5}
    max-followups-per-question: ${INTERVIEW_MAX_FOLLOWUPS_PER_QUESTION:2}
    max-followups-per-session: ${INTERVIEW_MAX_FOLLOWUPS_PER_SESSION:5}
    max-answer-chars: ${INTERVIEW_MAX_ANSWER_CHARS:10000}
    questions:
      easy: ${INTERVIEW_EASY_QUESTIONS:5}
      medium: ${INTERVIEW_MEDIUM_QUESTIONS:6}
      hard: ${INTERVIEW_HARD_QUESTIONS:7}
    ai:
      model: ${INTERVIEW_AI_MODEL:${GEMINI_MODEL:gemini-3.5-flash-lite}}
      script-prompt-version: ${INTERVIEW_SCRIPT_PROMPT_VERSION:v1}
      followup-prompt-version: ${INTERVIEW_FOLLOWUP_PROMPT_VERSION:v1}
      scoring-prompt-version: ${INTERVIEW_SCORING_PROMPT_VERSION:v1}
      script-timeout-ms: ${INTERVIEW_SCRIPT_TIMEOUT_MS:12000}
      followup-timeout-ms: ${INTERVIEW_FOLLOWUP_TIMEOUT_MS:8000}
      scoring-timeout-ms: ${INTERVIEW_SCORING_TIMEOUT_MS:50000}

  voice:
    max-file-size-bytes: ${VOICE_MAX_FILE_SIZE_BYTES:15728640}
    max-duration-ms: ${VOICE_MAX_DURATION_MS:300000}
    audio-retention-days: ${VOICE_AUDIO_RETENTION_DAYS:30}
    stt-timeout-ms: ${VOICE_STT_TIMEOUT_MS:7000}
    tts-timeout-ms: ${VOICE_TTS_TIMEOUT_MS:5000}
```

Giá trị model mặc định đang theo cấu hình CV parser hiện tại nhưng có biến môi trường riêng để thay
đổi độc lập. Model cuối cùng phải được benchmark trước khi deploy. Credential không có default
production và không ghi trong repo.

Các record dự kiến:

```text
JobDescriptionProperties
InterviewProperties
InterviewAiProperties
VoiceProperties
```

---

## 16. Error codes

Các code mới được thêm vào `ErrorCode`, message người dùng vào `Message`, và ném qua
`DomainException` để giữ `ApiError` hiện tại.

### 16.1. JD

| HTTP | Code | Khi nào |
|---:|---|---|
| 400 | `JD_CONTENT_REQUIRED` | Không có text/file hoặc nội dung rỗng |
| 400 | `JD_INVALID_TEXT` | Text quá ngắn, quá dài hoặc TXT không phải UTF-8 hợp lệ |
| 400 | `JD_FILE_CORRUPTED` | PDF/TXT không đọc được hoặc PDF encrypted |
| 400 | `JD_TOO_MANY_PAGES` | PDF vượt giới hạn trang |
| 413 | `JD_FILE_TOO_LARGE` | Vượt app JD limit |
| 415 | `JD_INVALID_FILE_TYPE` | Không phải loại file được hỗ trợ |
| 404 | `JD_NOT_FOUND` | Không thuộc user hoặc đã xóa mềm |
| 409 | `JD_NOT_CONFIRMED` | Dùng draft để tạo session |
| 409 | `JD_ALREADY_CONFIRMED` | Cố sửa confirmed JD |
| 409 | `JD_HAS_NO_FILE` | Xin URL file của text-source JD |
| 409 | `JD_LIMIT_REACHED` | Vượt số JD active |

### 16.2. Session/conversation

| HTTP | Code | Khi nào |
|---:|---|---|
| 404 | `SESSION_NOT_FOUND` | Không thuộc user hoặc không tồn tại |
| 404 | `TURN_NOT_FOUND` | Turn không thuộc session/user |
| 409 | `SESSION_INVALID_STATE` | Command không hợp lệ với status hiện tại |
| 409 | `SESSION_VERSION_CONFLICT` | `expectedVersion` đã cũ |
| 409 | `SESSION_LIMIT_REACHED` | Đã có năm session mở |
| 409 | `SESSION_RETRY_NOT_ALLOWED` | Stage hiện tại không retry được |
| 409 | `SESSION_RETRY_REQUIRED` | Workflow đã fail và cần retry |
| 409 | `CURRENT_PROMPT_MISMATCH` | Answer nhắm prompt cũ/sai |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | Create session thiếu/rỗng idempotency key |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Cùng key nhưng request khác |
| 400 | `ANSWER_REQUIRED` | Text answer rỗng |
| 400 | `ANSWER_TOO_LONG` | Vượt giới hạn ký tự |
| 503 | `RUBRIC_NOT_AVAILABLE` | Không có current published rubric |
| 503 | `INTERVIEW_AI_UNAVAILABLE` | Provider/credential không dùng được |

Lỗi background không nhất thiết trả trực tiếp qua request đã kết thúc. Session lưu `statusMessage`
đã sanitize để GET hiển thị, tương tự cách CV parsing đang làm.

### 16.3. Voice/report/upload

| HTTP | Code | Khi nào |
|---:|---|---|
| 400 | `AUDIO_FILE_REQUIRED` | Audio thiếu/rỗng |
| 400 | `AUDIO_INVALID` | File không đọc được hoặc metadata sai |
| 413 | `AUDIO_FILE_TOO_LARGE` | Vượt audio size limit |
| 413 | `AUDIO_DURATION_TOO_LONG` | Vượt duration limit |
| 415 | `AUDIO_INVALID_FILE_TYPE` | Format không hỗ trợ |
| 404 | `VOICE_ATTEMPT_NOT_FOUND` | Attempt không thuộc session/user |
| 409 | `VOICE_ATTEMPT_INVALID_STATE` | Edit/confirm sai trạng thái |
| 409 | `VOICE_ATTEMPT_VERSION_CONFLICT` | Attempt version cũ |
| 400 | `TRANSCRIPT_REQUIRED` | Final transcript rỗng |
| 503 | `STT_UNAVAILABLE` | STT credential/provider lỗi |
| 503 | `TTS_UNAVAILABLE` | Retry audio vẫn không thực hiện được |
| 409 | `REPORT_NOT_READY` | Session chưa scoring xong |
| 413 | `UPLOAD_TOO_LARGE` | Servlet global safety net |

---

## 17. Security và privacy

### 17.1. Ownership

Repository methods phải scope ownership ngay trong query:

```text
findByIdAndUserIdAndActiveTrue(...)
findSessionDetailByIdAndUserId(...)
findAttemptByIdAndSessionIdAndSessionUserId(...)
findTurnByIdAndSessionIdAndSessionUserId(...)
```

Không fetch bằng ID đơn rồi mới trả/mutate sau ownership check.

### 17.2. Upload trust boundary

- Sanitize filename và chỉ dùng để hiển thị.
- Xác minh magic bytes/container format.
- PDFBox mở file, kiểm encryption/page count và extract text.
- TXT decode bằng `CharsetDecoder` ở chế độ REPORT khi byte lỗi.
- Audio kiểm signature/container khi thư viện/provider hỗ trợ; declared MIME chỉ là một tín hiệu.
- Không cho path traversal ảnh hưởng object key.

### 17.3. Dữ liệu nhạy cảm

- Không log JD, CV snapshot, answer, transcript, audio bytes hoặc presigned URL.
- Log stable IDs, provider code đã sanitize, duration và attempt count.
- Bucket private; URL có TTL.
- Audio cleanup sau retention nhưng giữ confirmed transcript theo vòng đời account/session.
- Khi xóa account, cascade DB và best-effort/background cleanup object storage theo prefix/index;
  không dựa vào cascade DB để tự xóa object.

### 17.4. AI boundary

- Treat CV, JD và answer là untrusted content.
- Không đưa credential/provider body vào client response.
- Không cho output model quyết định authorization, state machine hoặc database relationship.
- Validate quote/source IDs trước khi persist.

---

## 18. Query và performance

### 18.1. Session detail

Tránh một join khổng lồ tạo Cartesian product giữa questions, turns, attempts và scores.

Đề xuất query theo batch:

1. Load owned session + profile/JD summary.
2. Load asked turns theo session, order `turn_index`.
3. Load current question theo `(session_id, current_question_ordinal)`.
4. Load latest relevant voice attempt nếu có.
5. Load TTS assets theo turn IDs bằng một batch query.

Mapper ghép response trong memory với số lượng bounded tối đa khoảng vài chục row.

### 18.2. Home page

Query trực tiếp index `(user_id, status, last_activity_at)`, projection sang summary DTO. Không load
turns/questions cho danh sách.

### 18.3. Diversity

Chỉ nạp signature/normalized text của ba session gần nhất có cùng `profile_id` và
`job_description_hash`; không nạp toàn bộ conversation.

### 18.4. SLO đo lường

| Metric | Mốc đo |
|---|---|
| Script generation | Session `createdAt` tới transition `READY` |
| Next-turn latency | Candidate turn commit tới interviewer turn commit |
| STT latency | Attempt `TRANSCRIBING` tới `TRANSCRIBED` |
| TTS latency | Asset `PENDING` tới `READY` |
| Scoring latency | Transition `SCORING` tới `COMPLETED` |

p95 phải tính trên dữ liệu đủ mẫu và tách theo provider/model. Không dùng log thủ công trên một lần
demo làm bằng chứng SLO.

---

## 19. Scheduler

### 19.1. Expiry job

Mỗi năm phút:

1. Query ID của session `READY`, `IN_PROGRESS` hoặc `PAUSED` có
   `last_activity_at <= now - 24h` theo batch nhỏ.
2. Với mỗi ID, mở transaction riêng.
3. Re-read/lock và kiểm tra điều kiện lần nữa.
4. Không có answer: tạo insufficient-evidence report và complete.
5. Có answer: chuyển `SCORING`, set `TIMEOUT_24H`, claim scoring.
6. Dispatch scoring sau commit.

Poll GET không cập nhật activity. Script/scoring provider completion không được xem là user activity.

### 19.2. Recovery job

Chạy khi application ready và định kỳ hai phút theo mặc định. Claim lại work stale như phần 12.

### 19.3. Audio cleanup

Chạy ngoài request:

- Chọn voice attempt/TTS asset quá retention.
- Xóa chính xác storage key.
- Sau thành công, giữ storage key để audit nhưng set `audio_deleted_at`; API không phát URL cho
  asset đã xóa.
- Transcript/session turn vẫn giữ.
- Delete idempotent; lỗi storage được retry, không xóa DB reference trước object khi cần audit.

Hai bảng audio đã có `audio_deleted_at` nullable để cleanup idempotent và quan sát được trạng thái.

---

## 20. Manual verification strategy

### 20.1. Quy ước triển khai

Từ M03, Codex không tạo thêm unit test, controller test hoặc integration test cho từng module trừ
khi người dùng yêu cầu rõ. Các test M01/M02 đã có được giữ nguyên nhưng không phải mẫu bắt buộc cho
module sau. Mục tiêu review mặc định là:

- Production code compile được.
- Liquibase, entity và `ddl-auto=validate` đồng bộ khi có schema change.
- API có OpenAPI annotation và checklist Swagger rõ ràng.
- Người dùng tự chạy happy path và failure path trên Swagger.
- Module chưa public API có query/checklist database hoặc log để quan sát kết quả.

Không hướng dẫn lại authentication trong checklist Swagger.

### 20.2. Checklist Swagger cho module có API

Review package phải ghi cho từng endpoint:

- Method, path, content type và payload/parts mẫu.
- Thứ tự gọi endpoint nếu flow phụ thuộc resource tạo trước.
- HTTP status và các field response quan trọng cần đối chiếu.
- Các error code nghiệp vụ quan trọng.
- Dữ liệu nào cần kiểm tra lại bằng GET sau command.
- Bất biến idempotency/version cần thử bằng cách gửi lặp hoặc dùng stale version.
- Field nội bộ không được xuất hiện, như storage key, provider body hoặc lazy entity.

### 20.3. Kiểm tra module nền tảng và persistence

Khi module chưa có API, review package cung cấp câu lệnh/query quan sát phù hợp để kiểm tra:

- Liquibase changeset đã được apply và Hibernate validation thành công.
- Constraint, unique key, FK delete behavior và index đã tạo đúng.
- State/transition log, timestamps, version và processing claim đúng invariant.
- Scheduler/recovery có thể được kích hoạt bằng cấu hình thời gian ngắn với dữ liệu giả.

Không yêu cầu người dùng sửa dữ liệu production hoặc chạy lệnh phá hủy; chỉ dùng database local.

### 20.4. Kiểm tra AI/provider thủ công

AI là nondeterministic nên dùng bộ dữ liệu giả, không chứa CV/JD thật, gồm:

- CV/JD match rõ và trường hợp thiếu kỹ năng.
- Project description nghèo dữ liệu.
- Answer đầy đủ/chưa đầy đủ.
- Prompt injection nằm trong JD/CV/answer.
- Câu trả lời tiếng Việt có thuật ngữ tiếng Anh.
- Provider timeout/unavailable/missing credential nếu có thể mô phỏng bằng cấu hình local.

Khi gọi provider thật, review phải ghi model/prompt version và không log nội dung nhạy cảm.

### 20.5. Existing automated tests

Test đã tồn tại trước quy ước này không bị xóa, tắt hoặc làm yếu. Có thể chạy chúng khi cần kiểm tra
regression, nhưng Codex không mặc định viết thêm test mới hoặc biến test output thành điều kiện review
của từng module sau M02.

---

## 21. Observability

Log cho workflow dùng parameterized SLF4J và stable identifiers:

```text
sessionId, userId, stage, status, promptVersion, modelName,
attemptNumber, durationMs, providerStatusCodeSanitized
```

Không log content.

Metrics tối thiểu:

```text
interview.script.duration
interview.script.failure
interview.next_turn.duration
interview.next_turn.failure
interview.scoring.duration
interview.scoring.failure
interview.session.expired
interview.workflow.recovered
interview.stt.duration
interview.stt.failure
interview.tts.duration
interview.tts.failure
interview.voice.rerecord
```

Nếu chưa thêm Micrometer registry ngoài, vẫn persist `duration_ms` ở các bảng phù hợp và log
structured fields; không thêm dependency chỉ để có dashboard trong MVP.

---

## 22. File/code map dự kiến

```text
src/main/resources/db/changelog/025-033-*.sql
src/main/resources/db/changelog/db.changelog-master.yaml
src/main/resources/ai/interview-*-prompt-v1.txt
src/main/resources/ai/interview-*-schema-v1.json
src/main/resources/application.yaml

src/main/java/com/baseProject/myBaseProject/config/properites/
  JobDescriptionProperties.java
  InterviewProperties.java
  InterviewAiProperties.java
  VoiceProperties.java

src/main/java/com/baseProject/myBaseProject/entity/
  JobDescription.java
  Rubric.java
  RubricVersion.java
  RubricCriterion.java
  RubricCriterionLevel.java
  InterviewSession.java
  SessionContextSnapshot.java
  SessionStateTransition.java
  SessionQuestion.java
  SessionTurn.java
  VoiceAnswerAttempt.java
  TurnAudioAsset.java
  SessionScore.java
  ScoreEvidence.java
  SessionReport.java
  ReportHighlight.java

src/main/java/com/baseProject/myBaseProject/controller/
src/main/java/com/baseProject/myBaseProject/dto/{jd,interview,voice,report}/
src/main/java/com/baseProject/myBaseProject/repository/
src/main/java/com/baseProject/myBaseProject/service/
src/main/java/com/baseProject/myBaseProject/service/impl/
src/main/java/com/baseProject/myBaseProject/interview/
src/main/java/com/baseProject/myBaseProject/jd/
src/main/java/com/baseProject/myBaseProject/scheduler/

src/test/java/... chỉ chứa test đã có hoặc test được người dùng yêu cầu rõ
docs/interview-engine-mvp-api.md
```

Không tạo tất cả skeleton cùng lúc. Mỗi chunk chỉ thêm lớp thực sự dùng trong vertical slice đó.

---

## 23. Thứ tự triển khai kỹ thuật đã khóa

Thứ tự approval chính thức nằm ở kế hoạch sản phẩm và được lặp lại ở đây để API/schema không phát
triển theo một sequence khác:

```text
M00 → M01 → M02 → M03 → M04 → M05 → M06 → M07 → M08
    → M09 → M10 → M11 → M12 → M13 → M14 → M15 → M16
```

Mỗi module dừng ở review gate. Chỉ `APPROVED Mxx` mới cho phép bắt đầu module kế tiếp. Migration
được sở hữu theo module như sau:

| Module | Migration được tạo trong module | Ghi chú |
|---|---|---|
| `M00` | Không | Chỉ khóa contract/tài liệu |
| `M01` | `025-create-job-descriptions.sql` | Khóa đầy đủ shape JD text/file; M02 chưa mở API file |
| `M02` | Không | File ingestion dùng schema đã khóa ở M01 |
| `M03` | `026-create-rubric-tables.sql`, `027-seed-interview-rubric-v1.sql` | Tách schema và seed |
| `M04` | `028-create-interview-session-foundation.sql` | Session, context snapshot, transition log |
| `M05` | `029-create-session-questions.sql` | Script thuộc session |
| `M06–M07` | Không | Workflow/API trên schema hiện có |
| `M08` | `030-create-session-turns.sql` | Conversation append-only |
| `M09` | Không | Follow-up dùng session turns |
| `M10` | `031-create-interview-report-tables.sql` | Score, evidence, report, highlight |
| `M11` | Không | Timeout/recovery dùng session/report tables |
| `M12` | `032-create-voice-answer-attempts.sql` | Recording và transcript draft |
| `M13–M14` | Không | STT/edit/confirm dùng voice attempts |
| `M15` | `033-create-turn-audio-assets.sql` | TTS/replay |
| `M16` | Không | Hardening và release verification |

Không tạo skeleton hoặc migration của module tương lai. Focused verification chạy trong từng
module; full Maven suite, Liquibase/Hibernate validation, OpenAPI review, text/voice/timeout E2E và
provider evaluation là release gate của `M16`.

---

## 24. M00 decision log

Các dòng `LOCKED_M00` là contract đã được approve ngày 2026-08-26. `DEFERRED` là hoãn có chủ đích
tới đúng module gate, không phải blocker của M00.

| ID | Quyết định M00 | Kết quả | Trạng thái |
|---|---|---|---|
| `D-001` | Input tạo session | `profileId` của profile đã confirm + `jobDescriptionId` của JD `READY`; JD bắt buộc | `LOCKED_M00` |
| `D-002` | JD text/file | Text trực tiếp hoặc PDF/TXT UTF-8; raw và confirmed text tách biệt | `LOCKED_M00` |
| `D-003` | JD file gốc | Lưu MinIO; API không lộ storage key; delete vật lý theo retention | `LOCKED_M00` |
| `D-004` | Confirmed JD | Bất biến; muốn đổi phải tạo JD mới | `LOCKED_M00` |
| `D-005` | Public routes | `/api/job-descriptions` và `/api/sessions`; endpoint user dùng `@IsUser` | `LOCKED_M00` |
| `D-006` | Session mở | Cho phép nhiều phiên, tối đa 5/user; mọi status trong `ACTIVE`, kể cả `FAILED`, đều được tính | `LOCKED_M00` |
| `D-007` | Base questions | `EASY=5`, `MEDIUM=6`, `HARD=7` | `LOCKED_M00` |
| `D-008` | Follow-up budget | Tối đa 2 liên tiếp/base question và 5/toàn session | `LOCKED_M00` |
| `D-009` | Ngôn ngữ MVP | Chỉ `vi`; vẫn persist `languageCode` | `LOCKED_M00` |
| `D-010` | State machine/timeout | Không có `EXPIRED`; `READY/IN_PROGRESS/PAUSED` timeout qua `SCORING`, `endReason=TIMEOUT_24H` | `LOCKED_M00` |
| `D-011` | Resume phase | Persist `AwaitingAction`; GET/poll không cập nhật `lastActivityAt` | `LOCKED_M00` |
| `D-012` | Async contract | Slow workflow trả `202`; client poll tối thiểu mỗi 1 giây; chưa dùng SSE/WebSocket | `LOCKED_M00` |
| `D-013` | Concurrency | Create bắt buộc `Idempotency-Key`; turn/attempt bắt buộc client ID; state command dùng optimistic version; DB là nguồn sự thật | `LOCKED_M00` |
| `D-014` | Script model | `session_questions` thuộc trực tiếp session; không có reusable `interview_scripts` | `LOCKED_M00` |
| `D-015` | Partial scoring | Normalize trên assessed weight, luôn gắn nhãn partial; không answer thì `INSUFFICIENT_EVIDENCE`, score `NULL` | `LOCKED_M00` |
| `D-016` | Rubric v1 | Năm criterion với weight `0.300/0.250/0.200/0.150/0.100`, bốn level/criterion | `LOCKED_M00` |
| `D-017` | Voice draft | `voice_answer_attempts`; chỉ confirm transcript mới tạo candidate turn | `LOCKED_M00` |
| `D-018` | Audio boundary | 15 MB, 5 phút; WebM/Opus + MP4/AAC; retention 30 ngày | `LOCKED_M00` |
| `D-019` | Question visibility | Frontend/API không trả future questions chưa được hỏi | `LOCKED_M00` |
| `D-020` | Migration sequence | Dùng chính xác `025–033` ở §4.2/§23; không sửa `001–024` | `LOCKED_M00` |
| `D-021` | STT provider | Chọn và benchmark trước khi bắt đầu `M13` | `DEFERRED_M13` |
| `D-022` | TTS provider | Chọn và benchmark trước khi bắt đầu `M15` | `DEFERRED_M15` |

---

## 25. M00 review checklist

`[x]` dưới đây nghĩa là tài liệu đã giải quyết và đồng bộ mục đó; approval cuối vẫn là dòng riêng:

- [x] JD bắt buộc, PDF/TXT, lưu file gốc và confirmed JD bất biến.
- [x] `session_questions` thuộc trực tiếp session, không có script dùng chung.
- [x] State machine không có terminal `EXPIRED`; timeout là `endReason` và bao gồm `READY`.
- [x] `AwaitingAction` được persist để resume UI.
- [x] Base question 5/6/7; follow-up tối đa 2/câu gốc và 5/toàn phiên.
- [x] API async `202 + polling` và không trả future questions.
- [x] Optimistic version, idempotency keys/client IDs và ownership query đã thống nhất.
- [x] Voice dùng `voice_answer_attempts`; raw/edit/confirm tách biệt.
- [x] Partial scoring và score `NULL` khi không đủ evidence đã thống nhất.
- [x] Rubric v1 và năm criterion/weight đã cụ thể hóa.
- [x] Audio format, duration, size và retention đã cụ thể hóa.
- [x] STT/TTS provider có module gate rõ ràng và không block `M01–M12`.
- [x] Migration filename/owner `025–033` đã đồng bộ với module sequence.
- [x] Người dùng/team đã phát hành `APPROVED M00` ngày 2026-08-26.

Sau `APPROVED M00`, thiết kế database/API được xem là khóa cho MVP. Mọi thay đổi sau đó phải nêu
decision ID/module bị ảnh hưởng và đi bằng migration/API revision có chủ đích. M01–M04 đã được
approve; M05 đang ở implementation review gate và M06 chưa được phép bắt đầu.
