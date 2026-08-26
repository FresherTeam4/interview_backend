# Interview Engine MVP — Kịch bản sản phẩm và kế hoạch triển khai

> Trạng thái: Bản nháp để review lần cuối trước khi viết code  
> Ngày cập nhật: 2026-08-26  
> Phạm vi: Interview Engine, JD, text interview, voice turn-based và scoring

Tài liệu này chốt lại luồng sản phẩm, phạm vi MVP, quy tắc nghiệp vụ, state machine, mô hình dữ
liệu dự kiến và hợp đồng API. Hai tính năng realtime voice và barge-in được giữ lại làm các giai
đoạn sau, không nằm trong MVP này.

---

## 1. Các quyết định chính

### 1.1. Phạm vi phát hành

MVP lần này gộp cả phần phỏng vấn bằng văn bản và voice turn-based:

- Chọn một CV thông qua `CandidateProfile` đã được người dùng xác nhận.
- Cung cấp Job Description bằng cách dán text hoặc upload file.
- Cho người dùng xem và sửa nội dung JD đã trích xuất trước khi sử dụng.
- Sinh kịch bản 5–7 câu dựa trên cả CV/Profile và JD.
- Phỏng vấn bằng text hoặc voice turn-based.
- AI tự quyết định hỏi follow-up hay chuyển sang câu hỏi tiếp theo.
- Tối đa hai follow-up liên tiếp trên một câu hỏi gốc.
- Lưu toàn bộ phiên xuống database sau mỗi lượt đã được xác nhận.
- Khôi phục đúng vị trí khi reload, đóng tab, mất mạng hoặc đăng nhập trên thiết bị khác.
- Cho phép xem và sửa transcript trước khi gửi câu trả lời voice.
- Kết thúc và chấm điểm theo rubric, kèm dẫn chứng từ transcript.
- Tự kết thúc sau 24 giờ không hoạt động và chấm phần người dùng đã hoàn thành.
- Hiển thị các phiên đang dở trên trang chủ với hành động `Tiếp tục`.

Không nằm trong MVP này:

- Voice realtime hai chiều liên tục.
- Tự phát hiện người dùng dứt lời trong luồng realtime.
- Tự động fallback từ realtime sang turn-based.
- Barge-in/ngắt lời AI.
- Phân tích tốc độ nói, khoảng lặng, từ đệm hoặc suy diễn thái độ.

Thứ tự sau MVP:

1. Stretch goal 1 — voice realtime.
2. Stretch goal 2 — barge-in.

### 1.2. Tài nguyên đầu vào

Backend nhận `profileId`, không nhận đồng thời cả `cvDocumentId` và `profileId`.

- Một `CandidateProfile` đã gắn với đúng một CV.
- Profile phải thuộc người dùng hiện tại.
- CV đứng sau profile phải chưa bị xóa mềm.
- Profile phải có `confirmedAt` trước khi được dùng để tạo phiên.

JD là một tài nguyên riêng có chủ sở hữu. Phiên nhận `jobDescriptionId`, không mang file JD trực
tiếp trong request sinh kịch bản. Việc upload/trích xuất JD có thể thất bại hoặc retry độc lập với
việc tạo phiên.

### 1.3. Quan hệ khái niệm

```text
CandidateProfile đã xác nhận ─┐
                              ├─> InterviewSession
JobDescription đã xác nhận ───┘          │
                                         ├─> SessionQuestion (kịch bản 5–7 câu)
                                         ├─> SessionTurn (hội thoại)
                                         ├─> VoiceAnswerAttempt (bản ghi/transcript nháp)
                                         └─> SessionReport + SessionScore
```

Không tạo bảng `interview_scripts` riêng trong MVP. Một kịch bản chỉ thuộc một phiên, vì vậy kịch
bản được lưu trực tiếp trong `session_questions`. Chỉ tách `interview_scripts` nếu sau này có yêu
cầu tái sử dụng hoặc chia sẻ cùng một kịch bản cho nhiều phiên.

---

## 2. Luồng người dùng hoàn chỉnh

### 2.1. Điều kiện trước khi tạo phiên

1. Người dùng đã đăng nhập.
2. Người dùng có ít nhất một CV đã parse thành công.
3. Người dùng đã kiểm tra, chỉnh sửa nếu cần và xác nhận Candidate Profile tương ứng.
4. Người dùng có một Job Description đã được xác nhận.

Nếu chưa có profile hợp lệ, frontend đưa người dùng về luồng upload/parse/xác nhận CV. Nếu JD chưa
được xác nhận, backend không cho tạo session.

### 2.2. Tạo Job Description

Người dùng chọn đúng một trong hai cách:

- Dán nội dung JD dạng text.
- Upload file JD.

Luồng file:

```text
Upload file
   ↓
Validate kích thước/định dạng/nội dung
   ↓
Trích xuất text
   ↓
Hiển thị text trong textarea
   ↓
Người dùng sửa nếu cần
   ↓
Xác nhận JD
```

`rawText` giữ nguyên kết quả trích xuất đầu tiên. `confirmedText` là bản cuối cùng được dùng để
sinh câu hỏi. Không ghi đè `rawText` bằng nội dung người dùng sửa.

Đề xuất mặc định cho MVP:

- Hỗ trợ text được dán trực tiếp.
- Hỗ trợ file PDF và TXT UTF-8.
- DOCX để sau nếu chưa muốn thêm parser/dependency mới.
- Một request chỉ được có file hoặc text, không được có cả hai hoặc không có gì.

### 2.3. Cấu hình và sinh kịch bản

Người dùng chọn:

- Candidate Profile.
- Job Description.
- Độ khó: `EASY`, `MEDIUM` hoặc `HARD`.
- Chế độ: `TEXT` hoặc `VOICE_TURN_BASED`.
- Ngôn ngữ phỏng vấn nếu sản phẩm hỗ trợ nhiều ngôn ngữ.

Khi gửi yêu cầu:

1. Backend kiểm tra quyền sở hữu profile và JD.
2. Backend kiểm tra profile và JD đã được xác nhận.
3. Backend chốt rubric version đang được phát hành.
4. Backend tạo session ở trạng thái `SCRIPT_GENERATING`.
5. Backend lưu snapshot của profile và JD dùng cho lần sinh này.
6. AI sinh structured output gồm 5–7 câu hỏi.
7. Backend kiểm tra output và lưu toàn bộ `session_questions`.
8. Session chuyển sang `READY`.

Frontend có thể poll trạng thái session. Không hiển thị trước toàn bộ kịch bản để tránh người dùng
chuẩn bị sẵn câu trả lời. Trước khi bắt đầu chỉ hiển thị cấu hình phiên, số câu dự kiến và rubric
sẽ dùng để chấm.

### 2.4. Phỏng vấn bằng text

```text
READY
  ↓ người dùng bấm Bắt đầu
IN_PROGRESS
  ↓
Hiển thị câu hỏi hiện tại
  ↓
Người dùng nhập và xác nhận câu trả lời
  ↓
Lưu candidate turn xuống DB
  ↓
AI quyết định FOLLOW_UP hoặc NEXT_QUESTION
  ↓
Lưu interviewer turn tiếp theo xuống DB
  ↓
Lặp lại cho tới khi hết kịch bản hoặc người dùng kết thúc
```

Nội dung người dùng đang gõ nhưng chưa bấm gửi không được xem là một lượt đã hoàn thành. Nếu muốn
giữ cả bản nháp đang gõ thì đó là một yêu cầu autosave riêng, không thuộc tiêu chí “lưu sau mỗi
lượt” của MVP này.

### 2.5. Phỏng vấn bằng voice turn-based

```text
AI đặt câu hỏi
  ↓
TTS đọc câu hỏi; người dùng có thể phát lại
  ↓
Người dùng bấm ghi âm và thấy waveform/thời lượng
  ↓
Upload audio và chạy STT
  ↓
Hiển thị raw transcript dạng sửa được
  ├─> Ghi âm lại: tạo attempt mới
  └─> Xác nhận: tạo candidate turn từ transcript cuối
  ↓
Chỉ sau khi xác nhận mới gọi AI để follow-up/chuyển câu
```

Trong phiên `VOICE_TURN_BASED`, mỗi lượt vẫn có thể được trả lời bằng text nếu micro hỏng. Chế độ
của phiên không đổi, nhưng `session_turns.input_mode` của lượt đó là `TEXT`.

### 2.6. Resume phiên

Khi reload hoặc mở trên thiết bị khác, backend trả về:

- Trạng thái hiện tại.
- Phiên bản dùng để kiểm soát concurrent update.
- Toàn bộ các lượt đã xác nhận.
- Câu hỏi hiện tại.
- Voice attempt/transcript nháp chưa xác nhận, nếu có.
- Hành động tiếp theo mà client cần thực hiện.

Các giá trị `awaitingAction` dự kiến:

```text
START_SESSION
CANDIDATE_ANSWER
TRANSCRIPT_CONFIRMATION
ENGINE_RESPONSE
REPORT
NONE
```

`currentTurnIndex` một mình không đủ để khôi phục giao diện, vì không phân biệt được đang chờ câu
trả lời, chờ xác nhận transcript hay chờ engine sinh câu tiếp theo.

### 2.7. Kết thúc và scoring

Phiên chuyển sang scoring khi:

- Người dùng đã trả lời hết kịch bản.
- Người dùng chủ động kết thúc sớm và chọn nhận báo cáo phần đã làm.
- Session không có hoạt động hợp lệ trong 24 giờ.

Quy tắc:

- Có ít nhất một candidate turn đã xác nhận: chấm phần đã làm.
- Không có câu trả lời nào: hoàn tất với `INSUFFICIENT_EVIDENCE`, không sinh điểm giả.
- Mỗi điểm theo tiêu chí phải có giải thích.
- Mỗi điểm phải có ít nhất một dẫn chứng trỏ tới candidate turn thật.
- Trích dẫn do AI trả về phải tồn tại trong transcript; dẫn chứng không khớp bị từ chối.
- `session_scores`, `score_evidences`, `session_report`, `report_highlights` và
  `interview_sessions.overall_score` được ghi trong cùng transaction.

---

## 3. State machine

### 3.1. Trạng thái

```text
CREATED
   ↓
SCRIPT_GENERATING
   ↓
READY
   ↓
IN_PROGRESS ⇄ PAUSED
   ↓ finish hoặc timeout
SCORING
   ↓
COMPLETED
```

Nhánh kết thúc khác:

```text
IN_PROGRESS/PAUSED → ABANDONED
Bất kỳ bước xử lý hợp lệ nào → FAILED
```

Không dùng `EXPIRED` làm trạng thái terminal nếu phiên hết hạn vẫn phải scoring. Trường hợp hết
hạn được biểu diễn bằng:

```text
status = SCORING hoặc COMPLETED
endReason = TIMEOUT_24H
```

### 3.2. Quy tắc chuyển trạng thái

Mọi lần chuyển trạng thái phải thực hiện ba việc trong cùng một transaction:

1. Cập nhật `interview_sessions.status`.
2. Thêm một hàng vào `session_state_transitions`.
3. Cập nhật timestamp liên quan.

Transition log chỉ được thêm mới, không sửa hoặc xóa.

`lastActivityAt` chỉ được cập nhật bởi hoạt động nghiệp vụ có ý nghĩa, ví dụ bắt đầu phiên, xác
nhận câu trả lời, pause/resume hoặc kết thúc. Poll `GET /api/sessions/{id}` không được kéo dài thời
hạn 24 giờ.

### 3.3. Timeout 24 giờ

- Mốc timeout tính từ `lastActivityAt`.
- Scheduler chỉ quét các trạng thái có thể hết hạn.
- Việc expire và submit câu trả lời đồng thời phải được bảo vệ bằng optimistic locking hoặc câu
  lệnh update có điều kiện.
- Scheduler xử lý từng session trong transaction riêng, không khóa toàn bộ danh sách.
- Timeout ghi transition với actor `SCHEDULER` và `endReason = TIMEOUT_24H`.
- Session sau đó được scoring bằng các câu trả lời đã xác nhận.

---

## 4. Quy tắc sinh câu hỏi

### 4.1. Nguồn câu hỏi

Mỗi câu được phân loại bằng một `sourceType`:

| Source type | Ý nghĩa |
|---|---|
| `CV_PROJECT` | Đào sâu vào dự án cụ thể trong CV |
| `CV_SKILL` | Kiểm tra một kỹ năng được khai báo trong CV |
| `CV_JD_MATCH` | Kỹ năng/kinh nghiệm xuất hiện trong cả CV và JD |
| `JD_GAP` | JD yêu cầu nhưng CV chưa thể hiện |
| `GENERAL_BEHAVIORAL` | Câu hỏi hành vi hoặc tình huống chung |

Với `JD_GAP`, câu hỏi phải nói rõ đây là kỹ năng JD yêu cầu nhưng CV chưa thể hiện; không được giả
định người dùng đã có kinh nghiệm đó.

Mỗi câu lưu tối thiểu:

- `questionText` — ảnh chụp nguyên văn câu đã sinh.
- `sourceType`.
- `sourceProjectId` hoặc `sourceSkillId` khi phù hợp.
- `sourceJdExcerpt` khi dựa trên JD.
- `competency`.
- `difficulty`.
- `generationSeed`.
- `promptVersion` và `modelName`.

### 4.2. Số lượng và độ khó

Giá trị mặc định đề xuất:

| Độ khó | Số câu gốc |
|---|---:|
| `EASY` | 5 |
| `MEDIUM` | 6 |
| `HARD` | 7 |

Độ khó phải thay đổi cả chiều sâu câu hỏi, không chỉ thay đổi số lượng.

Ngoài giới hạn tối đa hai follow-up liên tiếp trên mỗi câu gốc, nên có thêm ngân sách tối đa năm
follow-up cho toàn phiên để thời lượng không tăng tới 15–21 câu hỏi.

### 4.3. Độ đa dạng

`generationSeed` chỉ phục vụ truy vết, không tự đảm bảo bộ câu hỏi khác nhau. Backend phải kiểm tra:

- Không có câu trùng sau khi normalize text với các phiên gần đây của cùng profile/JD.
- Ít nhất 70% `questionSignature` khác phiên gần nhất.
- Signature có thể tạo từ `sourceType + source entity + competency + concept`.
- Nếu output vi phạm, generator retry tối đa một lần.
- Nếu vẫn không hợp lệ, session chuyển `FAILED` với lỗi có thể retry; không lưu kịch bản dở dang.

### 4.4. Hiệu năng

- Mục tiêu p95 từ lúc session được chấp nhận tới khi toàn bộ câu hỏi đã persist và session chuyển
  `READY` là dưới 15 giây.
- Timeout của lần gọi AI phải thấp hơn giới hạn API để còn thời gian validate và ghi DB.
- Thời gian thực tế được ghi lại để đo bằng dữ liệu, không chỉ kiểm tra thủ công.

---

## 5. Quy tắc follow-up

Sau một candidate turn, AI trả structured output thay vì chỉ trả một chuỗi:

```json
{
  "decision": "FOLLOW_UP",
  "questionText": "Bạn vừa nói rằng ... Bạn có thể giải thích rõ hơn không?",
  "evidenceQuote": "...",
  "reason": "Câu trả lời chưa giải thích trade-off"
}
```

`decision` chỉ nhận:

- `FOLLOW_UP`.
- `NEXT_QUESTION`.
- `END_INTERVIEW` khi đã hết kịch bản.

Quy tắc server-side:

- `evidenceQuote` phải xuất hiện trong candidate answer vừa gửi.
- Tối đa hai follow-up liên tiếp trên cùng câu hỏi gốc.
- Tối đa ngân sách follow-up toàn phiên.
- Khi đạt giới hạn, server bắt buộc chuyển câu, bất kể AI đề nghị gì.
- Câu trả lời phải được persist trước khi gọi AI.
- Nếu AI lỗi, câu trả lời không bị mất và tác vụ sinh lượt tiếp theo có thể retry idempotently.
- Không giữ transaction database mở trong lúc chờ AI.
- Prompt yêu cầu giọng điệu chuyên nghiệp; chất lượng được kiểm tra thêm bằng evaluation dataset,
  không dựa hoàn toàn vào unit test hoặc prompt.

---

## 6. Persistence, idempotency và concurrency

### 6.1. Sau mỗi lượt

“Lưu toàn bộ lịch sử sau mỗi lượt” có nghĩa là thêm lượt mới vào database và cập nhật con trỏ
session; không rewrite toàn bộ transcript.

Khi xác nhận một câu trả lời:

1. Xác thực session thuộc người dùng.
2. Kiểm tra session đang nhận câu trả lời.
3. Kiểm tra câu hỏi đúng với lượt hiện tại.
4. Kiểm tra `expectedVersion`.
5. Lưu candidate turn và cập nhật session trong cùng transaction.
6. Sau commit mới gọi AI.
7. Lưu interviewer turn tiếp theo trong transaction mới.

Nếu server chết giữa bước 5 và bước 7, resume nhận biết candidate turn cuối chưa có interviewer
turn tương ứng và tiếp tục tác vụ an toàn.

### 6.2. Idempotency

- Mỗi câu trả lời từ client mang `clientTurnId` hoặc `Idempotency-Key`.
- `(session_id, client_turn_id)` là unique.
- Double-click hoặc retry cùng key trả lại kết quả cũ, không tạo hai turn.
- Session có optimistic-lock `version` để ngăn hai tab cùng tiến phiên.
- `turnIndex` không được sinh bằng `SELECT COUNT(*)` mà không có locking.

### 6.3. Nguồn sự thật

Database là nguồn sự thật cho:

- Session status.
- Kịch bản.
- Current question/turn.
- Toàn bộ hội thoại.
- Voice draft/transcript.
- Transition log.
- Scoring/report.

Executor trong process chỉ là cơ chế kích hoạt công việc. Session ở `SCRIPT_GENERATING`,
`ENGINE_RESPONSE` hoặc `SCORING` phải có khả năng được phát hiện và retry sau khi application
restart; không được phụ thuộc duy nhất vào một queue trong bộ nhớ.

---

## 7. Voice turn-based và transcript

### 7.1. Voice answer attempt

Không gắn audio/transcript nháp trực tiếp vào một candidate turn chưa được xác nhận. Thêm một thực
thể `voice_answer_attempts`:

```text
voice_answer_attempts
- id
- session_id
- question_id
- attempt_no
- status: RECORDED | TRANSCRIBING | TRANSCRIBED | CONFIRMED | DISCARDED | FAILED
- storage_key
- content_type
- duration_ms
- size_bytes
- raw_text
- edited_text
- stt_provider
- stt_confidence
- created_at
- confirmed_at
```

Điều này cho phép:

- Lưu audio trước khi candidate turn tồn tại.
- Reload ngay tại màn hình xác nhận transcript.
- Ghi âm lại nhiều lần mà không ghi đè raw transcript cũ.
- Chỉ attempt được xác nhận mới sinh `session_turns(role = CANDIDATE)`.

### 7.2. Transcript

- `rawText` không bao giờ bị ghi đè.
- `editedText = null` nếu người dùng không sửa.
- `contentText` của candidate turn là bản cuối đã xác nhận.
- Chỉ sau confirm mới sinh follow-up/chuyển câu và dùng câu trả lời để scoring.
- Confirm và tạo candidate turn là idempotent.
- Ghi âm lại tạo attempt mới; attempt cũ chuyển `DISCARDED` theo chính sách lưu trữ.

### 7.3. TTS và STT

- Mỗi interviewer turn có thể có một TTS audio asset để phát và phát lại.
- Người dùng thấy trạng thái ghi âm, waveform và thời lượng ở frontend.
- Mục tiêu p95 cho STT dưới 8 giây phải được đo với một giới hạn audio cụ thể.
- Khi TTS/STT lỗi, phiên và câu hỏi hiện tại không bị mất.
- Người dùng luôn có thể trả lời bằng text.
- Audio và transcript là dữ liệu nhạy cảm; không ghi nội dung vào application log.
- File audio nằm trên object storage, không lưu BLOB trong MySQL.

---

## 8. Scoring và báo cáo

MVP dùng một rubric version được chốt khi tạo session. Rubric được hiển thị cho người dùng trước
khi bắt đầu và không được sửa đè sau khi đã có session tham chiếu.

Luồng scoring:

```text
SCORING
  ↓
Nạp đúng rubric version đã chốt
  ↓
Nạp các candidate turn đã xác nhận
  ↓
AI trả structured scores + comment + evidence
  ↓
Validate điểm, criterion và quote
  ↓
Ghi scores/evidences/report/highlights/overallScore cùng transaction
  ↓
COMPLETED
```

Các nguyên tắc:

- Điểm tổng tính theo trọng số rubric.
- Mọi tiêu chí có `comment`.
- Mọi điểm phải có dẫn chứng hợp lệ.
- Không dùng profile hoặc rubric “hiện tại” để chấm lại phiên cũ.
- Báo cáo có disclaimer đây là công cụ luyện tập, không phải chứng nhận năng lực.
- AI/provider lỗi để session ở trạng thái retry được; không tạo báo cáo một phần.
- Chỉ chấm nội dung đã được người dùng xác nhận.

---

## 9. Mô hình dữ liệu dự kiến

### 9.1. Bảng mới thuộc MVP

```text
job_descriptions
rubrics
rubric_versions
rubric_criteria
rubric_criterion_levels
interview_sessions
session_context_snapshots
session_state_transitions
session_questions
session_turns
voice_answer_attempts
turn_audio_assets
session_scores
score_evidences
session_reports
report_highlights
```

`session_context_snapshots` giữ bản profile/JD đã dùng khi sinh câu hỏi. Snapshot là bất biến và
không chứa file binary. Mục đích là giải thích được phiên cũ kể cả khi người dùng sửa profile hoặc
tạo revision JD sau đó.

### 9.2. Quan hệ chính

```text
user_accounts      1 ── N job_descriptions
user_accounts      1 ── N interview_sessions
candidate_profiles 1 ── N interview_sessions       [restrict]
job_descriptions   1 ── N interview_sessions       [restrict]
rubric_versions    1 ── N interview_sessions       [restrict]

interview_sessions 1 ── 1 session_context_snapshots
interview_sessions 1 ── N session_state_transitions
interview_sessions 1 ── N session_questions
interview_sessions 1 ── N session_turns
interview_sessions 1 ── N voice_answer_attempts
interview_sessions 1 ── 1 session_reports
```

FK từ `session_questions` tới project/skill dùng `ON DELETE SET NULL`, vì question text và source
excerpt đã là snapshot. FK từ session tới profile/JD/rubric version dùng `RESTRICT` để không làm
mất ngữ cảnh lịch sử.

---

## 10. API contract dự kiến

Tên public resource giữ ngắn gọn là `/api/sessions`, phù hợp với hợp đồng đã định hướng ở phần
CV/Profile.

### 10.1. Job Description

```http
POST /api/job-descriptions/text
POST /api/job-descriptions/file
GET  /api/job-descriptions/{jobDescriptionId}
PUT  /api/job-descriptions/{jobDescriptionId}
POST /api/job-descriptions/{jobDescriptionId}/confirm
```

### 10.2. Session

```http
POST /api/sessions
GET  /api/sessions/{sessionId}
GET  /api/sessions?status=ACTIVE
POST /api/sessions/{sessionId}/start
POST /api/sessions/{sessionId}/pause
POST /api/sessions/{sessionId}/resume
POST /api/sessions/{sessionId}/complete
POST /api/sessions/{sessionId}/abandon
```

Tạo session:

```json
{
  "profileId": 7,
  "jobDescriptionId": 12,
  "difficulty": "MEDIUM",
  "mode": "VOICE_TURN_BASED",
  "languageCode": "vi"
}
```

Phản hồi ban đầu:

```json
{
  "id": 42,
  "status": "SCRIPT_GENERATING"
}
```

Snapshot trả về khi GET/resume:

```json
{
  "id": 42,
  "status": "IN_PROGRESS",
  "mode": "VOICE_TURN_BASED",
  "version": 8,
  "awaitingAction": "CANDIDATE_ANSWER",
  "currentPrompt": {},
  "turns": [],
  "voiceDraft": null
}
```

### 10.3. Text answer

```http
POST /api/sessions/{sessionId}/answers
```

```json
{
  "promptTurnId": 205,
  "content": "...",
  "clientTurnId": "01J...",
  "expectedVersion": 8
}
```

### 10.4. Voice turn-based

```http
POST /api/sessions/{sessionId}/voice-attempts
PUT  /api/sessions/{sessionId}/voice-attempts/{attemptId}/transcript
POST /api/sessions/{sessionId}/voice-attempts/{attemptId}/confirm
GET  /api/sessions/{sessionId}/turns/{turnId}/audio
```

Ghi âm lại tạo `voice-attempts` mới. Confirm request mang `expectedVersion` và idempotency key.

### 10.5. Report

```http
GET /api/sessions/{sessionId}/report
```

Tất cả endpoint lấy session/JD/profile theo cả resource ID và current user ID. Không fetch chỉ theo
ID rồi mới trả dữ liệu trước khi kiểm tra ownership.

---

## 11. Acceptance criteria theo nhóm

### IE-01 — JD và tạo session

- Người dùng cung cấp JD bằng file hoặc text, đúng một nguồn mỗi lần.
- Nội dung trích xuất được hiển thị và sửa trước khi xác nhận.
- Không tạo session từ profile/JD của người khác hoặc chưa xác nhận.
- Session chốt profile, JD snapshot và rubric version ngay lúc tạo.

### IE-02 — Sinh kịch bản

- Sinh đúng 5/6/7 câu theo cấu hình đã chốt.
- Câu hỏi nhắc tới project, skill hoặc requirement cụ thể khi có dữ liệu.
- Mỗi câu có source metadata và được persist trước khi session `READY`.
- Bộ câu mới đạt quy tắc diversity với các phiên gần đây.
- Thời gian sinh p95 dưới 15 giây.
- Output sai schema không tạo kịch bản một phần.

### IE-03 — Text, persistence và resume

- Sau mỗi lượt đã xác nhận, reload trả đúng lịch sử và câu hỏi hiện tại.
- Mở trên thiết bị khác trả cùng trạng thái.
- Double-submit cùng idempotency key không tạo lượt trùng.
- Hai tab cập nhật phiên bản cũ nhận conflict thay vì làm tiến phiên hai lần.
- Phiên đang dở xuất hiện trong danh sách `ACTIVE` với hành động `Tiếp tục`.

### IE-04 — Follow-up

- Follow-up dẫn chiếu một nội dung cụ thể từ câu trả lời vừa gửi.
- Evidence quote tồn tại trong candidate answer.
- Server giới hạn tối đa hai follow-up liên tiếp mỗi câu gốc.
- Server giới hạn ngân sách follow-up toàn phiên.
- AI lỗi không làm mất candidate answer và tác vụ có thể retry.
- Nội dung giữ giọng điệu chuyên nghiệp, không mỉa mai hoặc công kích.

### IE-05 — Timeout

- Phiên không có hoạt động hợp lệ trong 24 giờ được scheduler kết thúc.
- Mỗi timeout có transition log với actor `SCHEDULER`.
- Phần đã trả lời được scoring.
- Phiên chưa có câu trả lời kết thúc với `INSUFFICIENT_EVIDENCE`.
- Poll trạng thái không reset `lastActivityAt`.

### IE-06 — Scoring

- Scoring dùng đúng rubric version đã chốt.
- Mỗi điểm có comment và dẫn chứng thật từ transcript.
- Điểm tổng đúng trọng số và nhất quán với report.
- Báo cáo được persist nguyên khối hoặc không ghi gì nếu transaction thất bại.
- Báo cáo cũ không thay đổi khi profile, JD hoặc rubric mới được cập nhật.

### VO-01 — Voice turn-based

- Câu hỏi được đọc bằng TTS và có thể phát lại.
- Người dùng thấy waveform và thời lượng khi ghi.
- Audio được lưu ở object storage, không lưu BLOB trong database.
- STT p95 dưới 8 giây trong giới hạn audio đã công bố.
- Khi micro/STT lỗi, người dùng chuyển sang text mà không mất câu hỏi.

### VO-02 — Transcript

- Raw transcript xuất hiện ở dạng sửa được sau STT.
- Người dùng có thể ghi âm lại và mỗi lần là một attempt riêng.
- Reload khi đang sửa transcript khôi phục đúng attempt nháp.
- Raw transcript không bị ghi đè bởi edited transcript.
- Chỉ confirm mới tạo candidate turn và kích hoạt follow-up/scoring.
- Confirm lặp lại không tạo hai candidate turn.

---

## 12. Security, privacy và vận hành

- Profile, JD, session, audio và report đều được scope theo current user.
- Không log CV, JD, transcript, audio, presigned URL hoặc provider response body chứa dữ liệu người
  dùng.
- Prompt phải phân cách rõ system instruction với CV/JD do người dùng cung cấp; coi nội dung CV/JD
  là untrusted data để hạn chế prompt injection.
- File JD được kiểm tra kích thước, magic bytes, cấu trúc và encryption; không tin filename hoặc
  declared content type.
- Audio có chính sách retention và cơ chế xóa object sau thời hạn; transcript có thể được giữ lâu
  hơn audio.
- Mọi provider nằm sau port riêng: question generator, follow-up decider, scorer, TTS và STT.
- Prompt và JSON schema được version hóa trong `src/main/resources/ai`.
- Unit test mock toàn bộ AI, STT, TTS và object storage; không dùng mạng thật.
- Các latency SLO được ghi thành metric/duration trong dữ liệu hoặc monitoring.

---

## 13. Chia MVP thành các module để triển khai và review

### 13.1. “Module” trong kế hoạch này nghĩa là gì

Các module dưới đây là **đơn vị triển khai và review trong cùng Spring Boot monolith**, không phải
Maven multi-module và không tách thành microservice.

Mỗi module chỉ giải quyết một nhóm hành vi đủ nhỏ để:

- Đọc được diff mà không phải hiểu đồng thời toàn bộ Interview Engine.
- Chạy focused test và quan sát được kết quả riêng.
- Review được schema, API và business rule liên quan trong một lần.
- Có điểm dừng rõ ràng để approve, yêu cầu sửa hoặc từ chối.
- Không buộc module sau phải sửa lại contract đã được approve của module trước.

Một số module nền tảng chưa tạo ra đầy đủ luồng người dùng khi đứng riêng, nhưng sau mỗi module code
phải compile, test liên quan phải pass và application không được có dependency bắt buộc chưa tồn
tại. MVP chỉ được coi là phát hành được sau module `M16`.

### 13.2. Quy trình review và approval bắt buộc

Chỉ triển khai **một module tại một thời điểm** theo quy trình:

```text
Chốt scope module
   ↓
Implement production code + migration + test + docs liên quan
   ↓
Chạy focused verification và broader verification phù hợp
   ↓
Gửi review package
   ↓
DỪNG — chờ APPROVED hoặc CHANGES_REQUESTED
   ↓
Chỉ khi APPROVED mới bắt đầu module tiếp theo
```

Review package cuối mỗi module phải có:

1. Hành vi đã hoàn thành và hành vi cố ý chưa làm.
2. Danh sách file thay đổi, tách rõ production/test/docs/migration.
3. API hoặc database contract mới/thay đổi.
4. Các lệnh verification đã thực sự chạy và kết quả.
5. Demo request/response hoặc cách kiểm tra thủ công nếu module có API.
6. Rủi ro hoặc quyết định còn mở.
7. Tóm tắt module kế tiếp nhưng chưa triển khai nó.

Quy ước approval:

- `APPROVED Mxx`: khóa contract của module và cho phép sang module tiếp theo.
- `CHANGES_REQUESTED Mxx`: chỉ sửa trong scope module hiện tại rồi gửi lại review.
- Nếu cần thay đổi một module đã approved, phải nói rõ contract nào bị ảnh hưởng trước khi sửa.
- Không gộp “tiện tay” code của module kế tiếp vào diff hiện tại.

### 13.3. Definition of Done chung cho mỗi module

Một module chỉ đủ điều kiện gửi review khi:

- Scope và acceptance criteria riêng của module đã hoàn thành.
- Entity và Liquibase đồng bộ nếu có schema change.
- Migration mới có constraint, index, FK action và rollback phù hợp.
- API dùng DTO/mapper, không expose entity.
- Ownership được enforce trong repository/service query.
- Expected business failures đi qua `DomainException`, `ErrorCode`, `Message` và `ApiError`.
- External provider được mock trong unit test; không cần mạng, Gemini, MinIO, STT hoặc TTS thật.
- Focused tests của module pass.
- Existing tests không bị xóa, tắt hoặc làm yếu.
- `git diff --check` pass và không có generated/local/secret file.
- OpenAPI và tài liệu được cập nhật cùng module nếu contract đã public.
- Không có code chết được tạo trước “để dành” cho module sau.

### 13.4. Bản đồ module và dependency

| ID | Module | Kết quả review được | Phụ thuộc |
|---|---|---|---|
| `M00` | Khóa contract MVP | Quyết định mở, API, schema sequence được chốt | Không |
| `M01` | JD text lifecycle | Tạo/sửa/xác nhận/liệt kê/xóa mềm JD text | `M00` |
| `M02` | JD file ingestion | Upload PDF/TXT, extract, lưu/xem file an toàn | `M01` |
| `M03` | Rubric v1 | Rubric versioned và seed có thể dùng để chấm | `M00` |
| `M04` | Session state foundation | Session persistence, snapshot, state machine, locking | `M01`, `M03` |
| `M05` | Question generation core | Lưu script, AI contract, validation và diversity | `M04` |
| `M06` | Create/generate session API | Tạo session async, poll tới `READY`, retry/recovery | `M02`, `M05` |
| `M07` | Start, read và resume | Start/pause/resume, current prompt, home active list | `M06` |
| `M08` | Text answer core | Lưu answer idempotent và đi qua base questions | `M07` |
| `M09` | Adaptive follow-up | AI quyết định follow-up, evidence và hard limits | `M08` |
| `M10` | Scoring và report | Complete/scoring/report có rubric và evidence | `M09` |
| `M11` | Timeout và workflow recovery | Timeout 24h, partial scoring, race/restart recovery | `M10` |
| `M12` | Voice recording foundation | Upload và lưu nhiều voice attempt an toàn | `M08` |
| `M13` | STT và transcript draft | Transcribe, edit, re-record và resume draft | `M12` |
| `M14` | Confirm voice answer | Transcript confirm đi vào conversation pipeline | `M09`, `M13` |
| `M15` | TTS và replay | Đọc/phát lại interviewer turn, failure không chặn phiên | `M14` |
| `M16` | MVP hardening và release gate | Retention, security audit, E2E, OpenAPI và full verify | `M11`, `M15` |

Thứ tự triển khai và approval chính thức là tuyến tính, luôn theo ID:

```text
M00 → M01 → M02 → M03 → M04 → M05 → M06 → M07 → M08
    → M09 → M10 → M11 → M12 → M13 → M14 → M15 → M16
```

Dependency logic vẫn được giữ trong bảng phía trên: voice foundation về kỹ thuật có thể bắt đầu từ
`M08`, nhưng kế hoạch review cố ý hoàn tất text engine tới `M11` trước khi bước sang voice. Không có
hai module chạy song song trong quy trình approval này.

Các milestone dễ quan sát:

| Milestone | Module | Có thể demo |
|---|---|---|
| A — Input ready | `M00–M03` | JD text/file đã xác nhận và rubric v1 |
| B — Text engine ready | `M04–M11` | Full text interview, resume, follow-up, timeout, report |
| C — Voice turn-based ready | `M12–M15` | Record, STT, edit, confirm, TTS/replay, text fallback |
| D — MVP release candidate | `M16` | Full combined MVP đủ verification và tài liệu |

Migration numbering trong tài liệu technical hiện là draft `025–031`. Vì module nhỏ hơn migration
draft ban đầu, số changeset chính thức sẽ được khóa ở `M00` và có thể mở rộng tới `032/033`; tuyệt
đối không sửa changeset `001–024` đã có.

---

### M00 — Khóa contract và implementation sequence

**Mục tiêu**

Biến hai tài liệu MVP thành contract được approve trước khi tạo schema hoặc production code.

**Bao gồm**

- Duyệt toàn bộ mục 14 của file này.
- Duyệt mục “Open decisions” và checklist trong `interview-engine-mvp-api.md`.
- Chốt tên endpoint `/api/sessions` và `/api/job-descriptions`.
- Chốt JD bắt buộc, loại file, số câu, follow-up budget, active-session limit và ngôn ngữ.
- Chốt state machine, `AwaitingAction`, async `202 + polling`, partial scoring policy.
- Chốt rubric criteria/weight v1.
- Chốt thứ tự và filename migration mới.
- Ghi decision log ngắn ngay trong hai tài liệu.

**Không bao gồm**

- Không sửa Java, YAML, migration hoặc prompt/schema.
- Chưa cần chọn STT/TTS provider; quyết định đó chỉ block `M13/M15`.

**Điều kiện approve**

- Không còn quyết định mở nào ảnh hưởng schema của `M01–M11`.
- API/product docs không mâu thuẫn về route, enum, status hoặc scoring.
- Team đồng ý thứ tự review module.

**Verification**

- `git diff --check`.
- Maven không cần chạy vì chỉ thay đổi documentation.

**Điểm dừng:** chờ `APPROVED M00`.

---

### M01 — Job Description text lifecycle

**Kết quả người dùng**

Người dùng có thể tạo JD bằng text, xem, sửa bản draft, xác nhận, liệt kê và xóa mềm.

**Phạm vi kỹ thuật**

- Migration `job_descriptions` với source `TEXT`, ownership, raw/confirmed text và soft delete.
- Entity, enum, repository, DTO, mapper, service và controller JD.
- Typed `JobDescriptionProperties` cho giới hạn text/số JD.
- API:
  - `POST /api/job-descriptions/text`.
  - `GET /api/job-descriptions`.
  - `GET /api/job-descriptions/{id}`.
  - `PUT /api/job-descriptions/{id}`.
  - `POST /api/job-descriptions/{id}/confirm`.
  - `DELETE /api/job-descriptions/{id}`.
- Raw text bất biến; confirmed JD không sửa đè.
- Error codes và OpenAPI tương ứng.

**Cố ý chưa làm**

- Chưa nhận file.
- Chưa tạo interview session.
- Chưa gọi AI.

**Review tập trung vào**

- Schema/nullability/index/soft-delete.
- Ownership query và không lộ JD của user khác.
- Confirm idempotent và raw text không bị ghi đè.
- Validation title/text/limit.

**Acceptance để approve**

- CRUD lifecycle text hoạt động qua controller test.
- Confirm lần hai giữ nguyên `confirmedAt`.
- Update confirmed JD trả conflict.
- List không gây N+1 và không trả full text ngoài endpoint detail.
- Focused service/controller/entity tests pass.

**Điểm dừng:** chờ `APPROVED M01`.

---

### M02 — Job Description file ingestion

**Kết quả người dùng**

Người dùng upload JD PDF/TXT, nhận text trích xuất để review và có thể xem lại file qua URL có hạn.

**Phạm vi kỹ thuật**

- `JobDescriptionTextExtractor` port và PDF/TXT implementations.
- File validation: size, extension, magic bytes, encryption, page count và strict UTF-8.
- `POST /api/job-descriptions/file` và `GET /api/job-descriptions/{id}/file`.
- Object key `jd/{userId}/{uuid}.{validatedExtension}`.
- Mở rộng `FileStorageService.delete` cho compensation/cleanup.
- Generalize servlet-level upload error từ mã CV-only sang `UPLOAD_TOO_LARGE`.
- Cấu hình multipart toàn cục và feature-specific JD limit.
- Storage unavailable/partial failure translation.

**Cố ý chưa làm**

- Chưa hỗ trợ DOCX.
- Chưa dùng AI để tóm tắt hoặc parse requirement.
- Chưa xóa file vật lý ngay khi soft-delete JD.

**Review tập trung vào**

- Không tin filename/declared MIME.
- Validate/extract trước upload.
- Upload thành công nhưng DB fail phải delete object best effort.
- Không trả storage key hoặc presigned URL trong log.
- Không làm thay đổi behavior CV ngoài mã lỗi global đã được chốt.

**Acceptance để approve**

- PDF/TXT hợp lệ tạo JD `DRAFT` với raw/confirmed text giống nhau.
- File hỏng, encrypted, quá trang, sai UTF-8, quá size hoặc sai magic bị từ chối đúng code.
- Text source gọi file URL trả `JD_HAS_NO_FILE`.
- Mock storage tests bao phủ upload failure và compensation.

**Điểm dừng:** chờ `APPROVED M02`.

---

### M03 — Rubric v1 foundation

**Kết quả sản phẩm**

Hệ thống có một rubric công khai, versioned và bất biến để mọi session mới chốt đúng phiên bản.

**Phạm vi kỹ thuật**

- Migration cho `rubrics`, `rubric_versions`, `rubric_criteria`, `rubric_criterion_levels`.
- Seed `TECH_INTERVIEW_FRESHER` v1 với năm criterion và bốn level/criterion.
- Dùng `rubrics.current_version_id`, không dùng unique `(rubric_id, is_current)`.
- Entities/repositories/read service.
- Test tổng weight bằng `1.000`, version published/current và đủ level.

**Cố ý chưa làm**

- Không có admin CRUD rubric.
- Chưa gọi AI scoring.
- Public session-rubric endpoint được thêm ở `M07` khi đã có session lock version.

**Review tập trung vào**

- Nội dung descriptor do con người soạn.
- Version immutability và FK `RESTRICT` dự kiến.
- Weight, max score và display order.

**Acceptance để approve**

- Seed load đúng một current published version.
- Mỗi criterion có đúng bốn level hợp lệ.
- Repository lấy current version deterministically.
- Không có đường code sửa đè published version.

**Điểm dừng:** chờ `APPROVED M03`.

---

### M04 — Session persistence và state-machine foundation

**Kết quả kỹ thuật**

Có nền tảng session bền vững trong DB, state transition hợp lệ, context snapshot, optimistic lock và
processing claim; chưa public luồng tạo session.

**Phạm vi kỹ thuật**

- Migration `interview_sessions`, `session_context_snapshots`, `session_state_transitions`.
- Session/status/awaiting/end/failure enums.
- Entities/repositories và `@Version`.
- `SessionStateMachine` là đường duy nhất đổi status.
- Processing claim/lease primitives cho async work và recovery.
- Query active/expired/stale work bằng projection/ID.
- Transition + session update + timestamp trong cùng transaction.

**Cố ý chưa làm**

- Chưa có session controller.
- Chưa có question/turn.
- Chưa gọi AI.

**Review tập trung vào**

- Transition matrix và `AwaitingAction` invariant.
- Snapshot bất biến, không chứa secret/storage key.
- Index phục vụ home, history, timeout và recovery.
- Hai worker không claim cùng work.

**Acceptance để approve**

- Test toàn bộ transition hợp lệ/bất hợp lệ.
- Mỗi transition ghi đúng một log và dùng fixed `Clock`.
- Stale version bị từ chối.
- Claim/reclaim stale work có kiểm soát.
- Query của user không thấy session user khác.

**Điểm dừng:** chờ `APPROVED M04`.

---

### M05 — Question generation core

**Kết quả kỹ thuật**

Engine có thể nhận một immutable profile/JD snapshot, gọi question-generator port và persist một
kịch bản hợp lệ; chưa mở create-session API cho frontend.

**Phạm vi kỹ thuật**

- Migration `session_questions`.
- `InterviewQuestionGenerator` port và Gemini adapter.
- `interview-script-prompt-v1.txt` và `interview-script-schema-v1.json`.
- Structured input/output records.
- Server-side validation source project/skill/JD excerpt, ordinal, count và difficulty.
- Question signature, exact duplicate và diversity check với ba session gần nhất.
- Timeout/provider/malformed-output translation.
- Persist toàn bộ script hoặc không persist gì.

**Cố ý chưa làm**

- Chưa public create-session endpoint.
- Chưa sinh follow-up.
- Chưa tạo conversation turns.

**Review tập trung vào**

- Prompt coi CV/JD là untrusted data.
- Model không được tự quyết định DB relationship.
- `generationSeed` không bị nhầm với cơ chế guarantee diversity.
- Không giữ transaction lúc gọi AI.

**Acceptance để approve**

- Mock response hợp lệ persist đúng 5/6/7 questions.
- Unknown source ID, duplicate, sai count/schema rollback toàn bộ.
- Same context vi phạm diversity retry tối đa một lần.
- Missing key/429/timeout/5xx được map thành failure an toàn.

**Điểm dừng:** chờ `APPROVED M05`.

---

### M06 — Create session, async generation và polling

**Kết quả người dùng**

Người dùng tạo phiên bằng confirmed profile + confirmed JD, nhận `202`, poll trạng thái và cuối cùng
nhận session `READY` hoặc lỗi retry được.

**Phạm vi kỹ thuật**

- `POST /api/sessions` với `Idempotency-Key`.
- `GET /api/sessions/{id}` ở các trạng thái pre-interview.
- Validate profile/JD ownership/confirmation và current rubric.
- Tạo immutable context snapshot.
- Transaction tạo session + transition + processing claim.
- Dispatch generation sau commit.
- Persist script rồi `SCRIPT_GENERATING -> READY`.
- Startup/periodic recovery riêng cho script generation stale.
- `POST /api/sessions/{id}/retry` cho failure stage script.
- Đo generation duration và trả status message an toàn.

**Cố ý chưa làm**

- Chưa start hoặc trả lời phỏng vấn.
- GET chưa có turns/current prompt.

**Review tập trung vào**

- Create idempotency: same key/same body trả session cũ; same key/body khác trả conflict.
- Client disconnect không hủy generation đã persist.
- Queue full/restart không làm session treo vĩnh viễn.
- Không expose future questions qua API.

**Acceptance để approve**

- Happy path đi `SCRIPT_GENERATING -> READY` và lưu transition.
- Ownership/confirmation/rubric errors đúng contract.
- Generation failure/retry/recovery deterministic với mocked provider.
- p95 chưa cần benchmark provider thật nhưng duration boundary được đo đúng.

**Điểm dừng:** chờ `APPROVED M06`.

---

### M07 — Start, đọc trạng thái, pause/resume và home list

**Kết quả người dùng**

Người dùng bắt đầu phiên, nhìn thấy câu hỏi đầu tiên, reload hoặc mở máy khác vẫn đúng chỗ; trang
chủ hiển thị phiên đang dở.

**Phạm vi kỹ thuật**

- Migration `session_turns` với unique turn index/client turn ID.
- `POST /api/sessions/{id}/start`.
- `POST /api/sessions/{id}/pause` và `/resume`.
- Mở rộng `GET /api/sessions/{id}` thành resume snapshot.
- `GET /api/sessions?scope=ACTIVE|HISTORY|ALL` có pagination.
- `GET /api/sessions/{id}/rubric` trả đúng locked rubric version.
- Start tạo interviewer turn đầu tiên atomically.
- Resume suy ra/phục hồi đúng `AwaitingAction`.

**Cố ý chưa làm**

- Chưa nhận answer.
- Chưa có TTS audio.

**Review tập trung vào**

- Không trả future questions.
- Start/retry request không tạo hai first turns.
- Active list chỉ dùng projection, không load conversation.
- Network disconnect không tự pause.

**Acceptance để approve**

- Start chỉ từ `READY`, tạo đúng một turn index 0.
- Reload/detail trả current prompt và locked rubric.
- Pause/resume ghi transition và giữ current prompt.
- Session user khác trả `SESSION_NOT_FOUND`.

**Điểm dừng:** chờ `APPROVED M07`.

---

### M08 — Text answer và base-question progression

**Kết quả người dùng**

Người dùng trả lời bằng text; câu trả lời được lưu đúng một lần và engine chuyển tuần tự qua các base
questions. Module này tạo đường hội thoại text ổn định trước khi thêm AI follow-up.

**Phạm vi kỹ thuật**

- `POST /api/sessions/{id}/answers`.
- `promptTurnId`, `clientTurnId`, `expectedVersion`.
- Transaction append candidate turn, cấp next index và cập nhật activity.
- Trong module này, policy tạm thời luôn chọn base question tiếp theo.
- Khi hết base questions, chuyển `SCORING`; report được hoàn thiện ở `M10`.
- Resume ở mọi boundary: trước answer, sau candidate commit, sau interviewer commit.
- Text fallback đã sẵn cho session mode voice sau này.

**Cố ý chưa làm**

- Chưa có AI follow-up.
- Chưa có scoring output.
- Không autosave text đang gõ nhưng chưa submit.

**Review tập trung vào**

- Answer persist trước mọi xử lý tiếp theo.
- Double-click/retry không tạo duplicate.
- Hai tab/stale prompt không làm tiến phiên hai lần.
- Không dùng `SELECT COUNT(*)` để cấp turn index.

**Acceptance để approve**

- Submit trả `202`, candidate turn tồn tại trước next turn.
- Same `clientTurnId` trả kết quả cũ; cùng ID/nội dung khác trả conflict.
- Wrong prompt/version/state bị từ chối.
- Recovery tạo đúng một interviewer turn nếu crash sau candidate commit.

**Điểm dừng:** chờ `APPROVED M08`.

---

### M09 — Adaptive follow-up

**Kết quả người dùng**

AI dựa vào câu trả lời vừa nhận để hỏi sâu hoặc chuyển câu, nhưng server kiểm soát giới hạn và không
cho AI làm hỏng state machine.

**Phạm vi kỹ thuật**

- `InterviewFollowUpDecider` port và Gemini adapter.
- Prompt/schema follow-up v1.
- Structured decision `FOLLOW_UP | NEXT_QUESTION | END_INTERVIEW`.
- Evidence quote validation.
- Max hai follow-up/câu gốc và năm follow-up/toàn phiên theo default.
- Persist `parentTurnId`, `isFollowUp`, `followUpDepth`.
- Retry/recovery cho `ENGINE_RESPONSE` và `ENGINE_RETRY`.
- Evaluation fixtures cho đầy đủ/chưa đầy đủ/prompt injection/professional tone.

**Cố ý chưa làm**

- Chưa scoring.
- Chưa voice input.

**Review tập trung vào**

- Server override AI khi hết budget.
- Follow-up gắn đúng base question và candidate parent turn.
- Chỉ gửi context cần thiết, không gửi toàn bộ history vô hạn.
- Provider failure không làm mất answer.

**Acceptance để approve**

- Evidence quote không phải substring bị từ chối/fallback an toàn.
- Follow-up depth 2 bắt buộc chuyển base question tiếp.
- Tổng follow-up không vượt global budget.
- Timeout/restart đưa session về trạng thái retry được, không sinh turn trùng.

**Điểm dừng:** chờ `APPROVED M09`.

---

### M10 — Scoring, report và kết thúc chủ động

**Kết quả người dùng**

Hoàn thành toàn bộ hoặc kết thúc sớm đều tạo báo cáo theo locked rubric, có giải thích và dẫn chứng
thật từ transcript.

**Phạm vi kỹ thuật**

- Migration `session_scores`, `score_evidences`, `session_reports`, `report_highlights`.
- `InterviewScorer` port, prompt/schema v1 và Gemini adapter.
- `POST /api/sessions/{id}/complete` và `/abandon`.
- `GET /api/sessions/{id}/report`.
- Evidence quote/turn/criterion/score validation.
- Weighted score, partial flag, completion ratio và assessed weight.
- `INSUFFICIENT_EVIDENCE` khi không có confirmed answer.
- Atomic persist score/evidence/report/highlight/overall/transition.
- Retry/recovery riêng cho scoring.

**Cố ý chưa làm**

- Chưa tự timeout 24 giờ.
- Chưa voice.

**Review tập trung vào**

- Chấm đúng rubric version của session.
- Fake quote không được persist.
- Partial score không bị hiển thị như full score.
- `abandon` không scoring; `complete early` có scoring.

**Acceptance để approve**

- Full và partial report đúng công thức được approve.
- Không answer tạo report score `null`, không tạo score/evidence giả.
- Scoring transaction fail không để dữ liệu một phần.
- Báo cáo cũ không đổi khi rubric/profile/JD mới thay đổi.

**Điểm dừng:** chờ `APPROVED M10`.

---

### M11 — Timeout 24 giờ và durable workflow recovery

**Kết quả người dùng**

Phiên bị bỏ dở tự kết thúc và được chấm phần đã làm; restart hoặc worker failure không để workflow
treo vĩnh viễn.

**Phạm vi kỹ thuật**

- `InterviewSessionExpiryJob` quét batch và xử lý mỗi session bằng transaction riêng.
- Timeout cho `READY`, `IN_PROGRESS`, `PAUSED` theo quyết định cuối.
- `endReason = TIMEOUT_24H`, actor `SCHEDULER`.
- Dispatch scoring hoặc insufficient-evidence report.
- Hoàn thiện `InterviewWorkflowRecoveryJob` cho script, next-turn và scoring.
- Atomic claim/reclaim stale work, bounded retry/backoff.
- Race test timeout vs answer/complete/pause.

**Cố ý chưa làm**

- Chưa audio cleanup; làm ở `M16`.

**Review tập trung vào**

- GET/poll không cập nhật `lastActivityAt`.
- Recheck điều kiện sau lock trước khi expire.
- Không boot instance mới rồi đánh fail work hợp lệ của instance khác.
- Scheduler không giữ một transaction lớn qua cả batch.

**Acceptance để approve**

- Fixed-clock tests chứng minh đúng mốc 24 giờ.
- Answer thắng race hợp lệ không bị expire nhầm.
- Stale work được xử lý đúng một lần dù recovery chạy đồng thời.
- Transition/reason/report đúng cho session có và không có answer.

**Điểm dừng:** chờ `APPROVED M11`.

---

### M12 — Voice recording và attempt storage foundation

**Kết quả người dùng**

Frontend có thể upload một hoặc nhiều bản ghi cho current prompt; backend lưu chúng thành voice
attempt riêng mà chưa biến chúng thành câu trả lời chính thức.

**Điều kiện bắt đầu**

- Chốt audio format, max size/duration và retention.
- Chưa bắt buộc chốt STT nếu module chỉ dừng ở `RECORDED`.

**Phạm vi kỹ thuật**

- Migration `voice_answer_attempts`.
- Audio validator và object key theo user/session.
- `POST /api/sessions/{id}/voice-attempts`.
- `GET /api/sessions/{id}/voice-attempts/{attemptId}`.
- Client attempt idempotency, attempt numbering và optimistic version.
- Storage compensation nếu DB insert fail.
- Resume response có voice draft metadata.

**Cố ý chưa làm**

- Chưa gọi STT.
- Chưa sửa/confirm transcript.
- Chưa TTS.

**Review tập trung vào**

- File trust boundary, size/duration/content type.
- User recording gắn với attempt, không gắn trực tiếp vào candidate turn chưa tồn tại.
- Không lộ storage key/audio của user khác.

**Acceptance để approve**

- Upload hợp lệ tạo đúng một attempt `RECORDED`.
- Retry cùng `clientAttemptId` không upload/tạo row lần hai.
- Wrong prompt/session/mode bị từ chối.
- Storage failure/DB failure được dịch và compensate đúng.

**Điểm dừng:** chờ `APPROVED M12`.

---

### M13 — STT, transcript draft và re-record

**Kết quả người dùng**

Recording được chuyển thành transcript; người dùng reload, sửa hoặc ghi lại mà chưa gửi answer đi
chấm.

**Điều kiện bắt đầu**

- STT provider và browser-native audio formats đã được benchmark/chốt.

**Phạm vi kỹ thuật**

- `SpeechToTextClient` port và provider adapter.
- Attempt lifecycle `RECORDED -> TRANSCRIBING -> TRANSCRIBED|FAILED`.
- Raw transcript/provider/confidence/duration persistence.
- `PUT /api/sessions/{id}/voice-attempts/{attemptId}/transcript`.
- Re-record tạo attempt mới; attempt tốt cũ chỉ bị discard sau khi attempt mới thành công.
- Resume trả đúng active draft và trạng thái transcribing/failed.
- STT retry/recovery sau restart.

**Cố ý chưa làm**

- Chưa tạo candidate turn khi transcript xuất hiện.
- Chưa TTS.

**Review tập trung vào**

- Raw transcript không bị ghi đè.
- Edit dùng attempt version và không có lost update giữa hai thiết bị.
- STT failure không làm mất recording hoặc session.
- Không log raw transcript/provider body.

**Acceptance để approve**

- Mock STT success/failure/timeout/malformed response đầy đủ.
- Edited text giống raw sau normalize được lưu thành `null`.
- Re-record failure vẫn cho confirm transcript tốt trước đó ở `M14`.
- Reload ở mọi attempt state trả đúng dữ liệu.

**Điểm dừng:** chờ `APPROVED M13`.

---

### M14 — Confirm voice answer và conversation integration

**Kết quả người dùng**

Chỉ khi bấm xác nhận, transcript cuối mới trở thành candidate answer và đi qua cùng follow-up/scoring
pipeline như text.

**Phạm vi kỹ thuật**

- `POST /api/sessions/{id}/voice-attempts/{attemptId}/confirm`.
- Chọn `editedText` nếu có, ngược lại `rawText`.
- Atomic attempt confirm + candidate turn + session processing claim.
- `confirmedTurnId` và discard các attempt còn lại của prompt.
- Confirm idempotency và session/attempt optimistic version.
- Text fallback trong session `VOICE_TURN_BASED` dùng endpoint M08.
- Follow-up/scoring không phân biệt content đến từ text hay confirmed voice.

**Review tập trung vào**

- Không có candidate turn trước confirm.
- Confirm lặp không tạo hai turn.
- Attempt/prompt stale không thể gửi vào câu hỏi mới.
- Raw/edited/final content giữ đúng vai trò.

**Acceptance để approve**

- Raw-only và edited transcript đều tạo đúng final content.
- Confirm transaction rollback không để attempt `CONFIRMED` nhưng thiếu turn hoặc ngược lại.
- Follow-up nhận đúng candidate answer đã confirm.
- Text fallback giữ nguyên current prompt/context.

**Điểm dừng:** chờ `APPROVED M14`.

---

### M15 — TTS và phát lại câu hỏi

**Kết quả người dùng**

Mọi interviewer turn trong voice mode được đọc bằng TTS và có thể phát lại; TTS lỗi không chặn việc
đọc câu hỏi hoặc trả lời bằng text.

**Điều kiện bắt đầu**

- TTS provider, output format và latency expectation đã được chốt.

**Phạm vi kỹ thuật**

- Migration `turn_audio_assets`.
- `TextToSpeechClient` port và provider adapter.
- Tạo asset `PENDING` cùng transaction tạo interviewer turn.
- Sinh/upload TTS sau commit và recovery stale assets.
- `GET /api/sessions/{id}/turns/{turnId}/audio`.
- `POST /api/sessions/{id}/turns/{turnId}/audio/retry`.
- Presigned URL ngắn hạn và playback metadata.

**Cố ý chưa làm**

- Không streaming audio.
- Không barge-in.
- Không realtime connection.

**Review tập trung vào**

- TTS failure độc lập với session state.
- Không sinh hai asset cho cùng turn khi retry/restart.
- URL/storage key/privacy.

**Acceptance để approve**

- Text mode không tạo TTS asset.
- Voice mode tạo/phát lại asset khi provider success.
- Pending/failed/retry response đúng contract.
- Người dùng luôn nhìn thấy question text và dùng được text fallback khi TTS lỗi.

**Điểm dừng:** chờ `APPROVED M15`.

---

### M16 — MVP hardening, privacy và release gate

**Kết quả**

Toàn bộ MVP text + resume + follow-up + scoring + voice turn-based đủ điều kiện phát hành nội bộ và
tích hợp frontend.

**Phạm vi kỹ thuật**

- Audio retention/cleanup và audit `audioDeletedAt`.
- Security/ownership/privacy/logging audit toàn module.
- Query review cho active list/session detail, tránh N+1/Cartesian product.
- Concurrency scenarios xuyên module.
- Failure recovery xuyên script/follow-up/scoring/STT/TTS.
- OpenAPI hoàn chỉnh và frontend integration guide.
- Đồng bộ product/API docs với code thực tế.
- Full test suite và MySQL/Liquibase/Hibernate validation khi môi trường hỗ trợ.
- Manual smoke test dùng dữ liệu giả, không dùng CV/JD/audio thật.

**Không bao gồm**

- Không thêm feature mới.
- Không bắt đầu realtime hoặc barge-in trong cùng diff.
- Không refactor rộng code CV/auth không liên quan.

**Review tập trung vào**

- Không còn secret/debug/generated file.
- Không có session state hoặc work item có thể treo vĩnh viễn mà không recovery.
- Standard `ApiError`, authorization và OpenAPI nhất quán.
- Mọi acceptance criteria IE-01–IE-06 và VO-01–VO-02 có test/evidence tương ứng.

**Acceptance để approve MVP**

- Full Maven suite pass, hoặc blocker hạ tầng được ghi chính xác cùng những test đã pass.
- Liquibase chạy sạch từ schema `024` và `ddl-auto=validate` pass.
- Text E2E: create → generate → start → answer/follow-up → complete → report → resume/history.
- Voice E2E: TTS → record → STT → edit/re-record → confirm → follow-up/report.
- Timeout E2E chấm partial answer và không chấm khống session rỗng.
- Frontend team duyệt API/OpenAPI và resume contract.

**Điểm dừng:** chờ `APPROVED M16`; sau đó mới coi combined MVP hoàn tất và lập kế hoạch realtime.

---

## 14. Các mục cần duyệt lần cuối

Các mục dưới đây là mặc định được đề xuất, chưa nên coi là bất biến cho tới khi review xong:

| Mục | Mặc định đề xuất |
|---|---|
| JD có bắt buộc không? | Bắt buộc cho MVP này; CV-only có thể bổ sung sau |
| Định dạng JD file | PDF và TXT UTF-8; DOCX để sau |
| Có lưu file JD gốc không? | Có, lưu MinIO; database chỉ giữ storage key và text |
| Số câu | Easy 5, Medium 6, Hard 7 |
| Follow-up toàn phiên | Tối đa 5, ngoài giới hạn 2/câu gốc |
| Nhiều phiên đang dở | Cho phép nhiều; trang chủ sắp xếp theo `lastActivityAt` |
| Audio tối đa mỗi answer | 5 phút; cần đối chiếu chi phí/provider trước khi code |
| Retention audio | 30 ngày; transcript giữ theo vòng đời session/account |
| Phiên timeout chưa có answer | `COMPLETED` + `INSUFFICIENT_EVIDENCE`, không có điểm |
| Ngôn ngữ MVP | Tiếng Việt; giữ `languageCode` để mở rộng |
| Public API path | `/api/sessions` |
| Realtime/barge-in | Không nằm trong MVP, làm tuần tự sau MVP |

Sau khi các mục này được duyệt, schema và API contract mới được coi là đã khóa để bắt đầu migration.
