# Interview Engine — kiến trúc và luồng runtime

> Phạm vi hiện tại: M03–M09. Script generation, text interview và adaptive follow-up đã có;
> scoring, timeout và voice vẫn thuộc các module sau.

Interview Engine cần xử lý nhiều hơn một lần gọi Gemini, nhưng không phải mọi phần của engine đều
cần một class hoặc một tầng abstraction riêng. Kiến trúc hiện tại giữ các boundary quan trọng và
gom những lớp chỉ chuyển tiếp hoặc lặp lại cùng một workflow.

## 1. Các nguyên tắc được giữ lại

- Không giữ database transaction trong lúc chờ Gemini.
- Database là nguồn sự thật cho session, script và conversation.
- Tạo session dùng `Idempotency-Key`; submit answer dùng `clientTurnId`.
- Mỗi mutation session kiểm tra ownership, version và trạng thái hợp lệ.
- Context CV/JD được snapshot khi tạo session.
- Source project/skill của question là ID logic trong snapshot, không phải quan hệ tới live profile.
- AI output luôn được validate trước khi persist.
- Follow-up tối đa hai lần trên một base question và năm lần trên toàn session.
- Worker dùng processing claim; scheduler có thể khôi phục work sau queue rejection hoặc restart.

## 2. Luồng tạo session và sinh script

```text
POST /api/sessions
  -> InterviewSessionCreationService
       -> validate ownership/profile/JD/rubric/idempotency
       -> SessionStateMachine tạo session + snapshot + transition log
       -> claim SCRIPT_GENERATION
       -> InterviewWorkflowDispatcher chạy sau commit
            -> InterviewScriptGenerationWorker
                 -> ScriptGenerationStore.prepare() [read-only transaction]
                 -> InterviewQuestionGenerator.generate() [không có transaction]
                 -> QuestionScriptValidator
                 -> ScriptGenerationStore.commit() [write transaction]
                      -> persist questions
                      -> session READY
```

`ScriptGenerationStore` là persistence boundary của use case. Việc một class có cả `prepare` và
`commit` không làm transaction kéo dài qua Gemini: hai method được gọi riêng qua Spring bean proxy,
còn provider call nằm giữa hai method.

Khi persist question, `source_project_snapshot_id` và `source_skill_snapshot_id` chỉ định item trong
`session_context_snapshots.profile_json`. Không có foreign key từ hai cột này tới profile hiện tại,
nên user sửa hoặc xóa project/skill sau đó không làm thay đổi ý nghĩa của question lịch sử.

## 3. Luồng submit answer và sinh turn tiếp theo

```text
POST /api/sessions/{id}/answers
  -> InterviewAnswerService
       -> lock session, validate version/current prompt/clientTurnId
       -> persist candidate turn
       -> claim NEXT_TURN
       -> InterviewWorkflowDispatcher chạy sau commit
            -> InterviewNextTurnWorker
                 -> NextTurnStore.prepare() [read-only transaction]
                 -> InterviewFollowUpDecider.decide() [không có transaction]
                 -> FollowUpDecisionValidator
                 -> NextTurnStore.commit() [write transaction]
                      -> follow-up, next base question hoặc SCORING
```

Server luôn kiểm tra lại processing token và pending candidate turn khi commit. Vì vậy kết quả của
một worker đã mất lease không thể ghi đè kết quả của worker mới.

## 4. Workflow và retry

`InterviewWorkflowDispatcher` dùng chung executor/scheduler cho script generation và next-turn.
`InterviewWorkflowCoordinator` dùng chung logic:

1. Kiểm tra worker còn sở hữu claim.
2. Kiểm tra số attempt.
3. Release claim và đặt `nextRetryAt` nếu lỗi retryable.
4. Chuyển session sang `FAILED` nếu hết retry hoặc lỗi permanent.

Recovery job chỉ tìm ID ứng viên rồi atomic-claim từng session. Nó không giữ transaction trong khi
gọi AI và không phụ thuộc queue trong memory để tồn tại qua application restart.

## 5. State hiện tại

Public contract vẫn giữ hai trường:

- `status`: lifecycle lớn của session.
- `awaitingAction`: hành động frontend cần thực hiện.

Các transition đang được sử dụng ở M09:

```text
CREATED -> SCRIPT_GENERATING -> READY -> IN_PROGRESS -> SCORING
                                  |           |
                                  |           +-> PAUSED -> IN_PROGRESS
                                  +-> FAILED <-+  (khi AI workflow thất bại)
```

`SCORING` hiện là điểm bàn giao cho M10; chưa có scoring worker nên chưa chuyển tiếp sang
`COMPLETED`. Các state dành cho timeout, report và voice trong schema/API contract chưa đồng nghĩa
với việc các module đó đã được triển khai.

## 6. Trách nhiệm của các class chính

| Class | Trách nhiệm |
|---|---|
| `SessionStateMachine` | Lock session, kiểm tra transition, cập nhật session và transition log |
| `ScriptGenerationStore` | Read/commit transaction của script generation |
| `NextTurnStore` | Read/commit transaction của adaptive next-turn |
| `InterviewWorkflowDispatcher` | Dispatch-after-commit, executor và delayed retry |
| `InterviewWorkflowCoordinator` | Claim inspection, retry và terminal failure |
| `QuestionScriptValidator` | Validate question count, source, content và signature |
| `QuestionDiversityPolicy` | Chặn câu trùng và enforce ngưỡng 70% signature mới |
| `FollowUpDecisionValidator` | Validate decision, evidence quote và server-side budget |
| Gemini adapters | Prompt/schema, provider call, parse response và phân loại lỗi |

## 7. Những phần chưa triển khai

- M10: scoring và report.
- M11: inactivity timeout 24 giờ.
- M12–M15: voice attempt, STT, transcript confirmation và TTS.
- M16: hardening và release verification.

Không nên mô tả các nhánh này là behavior đang chạy cho tới khi module tương ứng được hoàn thành.
