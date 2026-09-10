# Frontend implementation guide

File này là brief triển khai. API payload chi tiết nằm trong các tài liệu domain và [openapi.yaml](./openapi.yaml).

## Mục tiêu chức năng

Frontend hoàn chỉnh cần hỗ trợ:

1. Register, login mật khẩu, Google login, restore session, logout và logout all.
2. Upload/list/preview/delete/retry CV; tự đi tiếp sang profile khi parse xong.
3. Review/edit/reorder/confirm profile, xử lý optimistic version conflict.
4. Nhập JD bằng PDF hoặc text; list/poll/retry/preview/read analysis.
5. Review/edit/confirm template; list template của mình và public; admin publish/unpublish; archive.
6. Chọn profile + template + option, tạo session idempotent, poll preparation và retry.
7. Start/resume interview, countdown theo server deadline, submit/retry answer idempotent, finish sớm.
8. Poll scoring, retry scoring và render report rút gọn gồm score, coverage, improvements, feedback và focus areas.

## Các route/screen đề xuất

| Route | Dữ liệu/API | Trạng thái chính |
|---|---|---|
| `/login`, `/register` | Auth APIs | idle/submitting/error |
| `/candidates` | `GET /cvs`, `GET /profiles` | list + processing cards |
| `/candidates/new` | `POST /cvs` | upload → poll |
| `/candidates/:profileId` | Profile detail/update/confirm + CV file URL | view/edit/version conflict |
| `/jobs` | `GET /job-descriptions`, template `mine` | source + template status |
| `/jobs/new` | Hai variant `POST /job-descriptions` | PDF/text → poll |
| `/templates/:templateId` | Template detail/update/actions | draft/confirmed/published/archived |
| `/templates/public` | Template list `scope=public` | paginated catalog |
| `/interviews/new` | Profiles + templates + options | selection/create |
| `/interviews/:sessionId/preparing` | Session status/retry/start | preparing/ready/failed |
| `/interviews/:sessionId` | Start/conversation/answer/finish | timer + turn loop |
| `/interviews/:sessionId/report` | Report/retry | scoring/failed/completed |

Backend chưa có endpoint list sessions; muốn có trang lịch sử tạm thời, chỉ có thể giữ danh sách `sessionId` trong local storage. Dữ liệu đó là tiện ích client, không phải nguồn sự thật đa thiết bị.

## API layer

Tạo một API client duy nhất có các trách nhiệm:

- Ghép `API_BASE_URL`.
- Luôn parse JSON success/error; bỏ qua body cho `204`.
- Gắn access Bearer cho protected endpoint.
- Luôn bật credentials để refresh cookie hoạt động.
- Refresh single-flight và retry request 401 đúng một lần.
- Không tự đặt multipart content type.
- Cho phép caller truyền `Idempotency-Key` ổn định.

Pseudocode:

```ts
let accessToken: string | null = null;
let refreshPromise: Promise<string> | null = null;

async function refreshOnce(): Promise<string> {
  refreshPromise ??= fetchJson<AuthResponse>("/api/auth/refresh", {
    method: "POST",
    credentials: "include",
    skipAuthRefresh: true,
  }).then((auth) => {
    accessToken = auth.accessToken;
    return auth.accessToken;
  }).finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function request<T>(path: string, init: RequestOptions = {}): Promise<T> {
  // credentials: include; gắn bearer; parse ApiError.
  // Nếu 401 AUTHENTICATION_REQUIRED và chưa retry: await refreshOnce(), retry 1 lần.
}
```

Không gọi refresh cho chính `/refresh`, login/register/Google; không refresh khi lỗi là `INVALID_CREDENTIALS` hoặc `INVALID_GOOGLE_TOKEN`.

## Query keys và invalidation

Nếu dùng TanStack Query, giữ key có cấu trúc:

```ts
const keys = {
  me: ["auth", "me"],
  cvs: ["cvs", "list"],
  cv: (id: number) => ["cvs", id],
  profiles: ["profiles", "list"],
  profile: (id: number) => ["profiles", id],
  jds: ["job-descriptions", "list"],
  jd: (id: number) => ["job-descriptions", id],
  jdAnalysis: (id: number) => ["job-descriptions", id, "analysis"],
  templates: (scope: "mine" | "public", page: number, size: number) =>
    ["interview-templates", scope, page, size],
  template: (id: number) => ["interview-templates", id],
  options: ["interview-session-options"],
  session: (id: number) => ["interview-sessions", id],
  conversation: (id: number) => ["interview-sessions", id, "conversation"],
  report: (id: number) => ["interview-sessions", id, "report"],
};
```

Invalidation tối thiểu:

| Mutation | Update/invalidate |
|---|---|
| Upload/retry/delete CV | CV list + CV detail; khi `PARSED` thêm profile list |
| Update/confirm profile | Profile detail + profile list + CV list |
| Create/retry/delete JD | JD list + JD detail; khi `READY` thêm template mine |
| Update/confirm/publish/archive template | Detail + mine/public lists + source JD detail |
| Create/retry preparation | Session detail |
| Start/answer/finish | Conversation + session detail; khi `SCORING` khởi động report query |
| Retry scoring | Report + session detail |

Mutation response là dữ liệu mới nhất; set cache ngay rồi invalidate các list liên quan.

## Polling rules

```ts
function processingPollInterval(status: string): number | false {
  return ["UPLOADED", "PARSING", "EXTRACTING", "ANALYZING", "PREPARING", "SCORING"]
    .includes(status)
    ? 1500
    : false;
}
```

- CV dừng ở `PARSED|FAILED`.
- JD dừng ở `READY|FAILED`.
- Preparation dừng khi khác `PREPARING`.
- Report dừng ở `COMPLETED|SCORING_FAILED`.
- Pause polling khi tab hidden nếu UX cho phép; refetch ngay khi focus lại.
- Không dùng polling cho answer đang submit; endpoint đó trả reply đồng bộ. Chỉ poll/reconcile conversation khi request bị mất kết quả hoặc turn còn `PROCESSING`.

## Form rules quan trọng

### Profile

Giữ một bản `CandidateProfile` làm form source. Khi submit:

- gửi đủ ba mảng;
- giữ ID item cũ, `null` cho item mới;
- array order là display order;
- dùng `version` mới nhất;
- sau `PROFILE_VERSION_CONFLICT`, fetch lại và yêu cầu user merge/review.

`confirmedAt` không khóa edit theo backend hiện tại. Hiện note “thay đổi áp dụng cho interview tạo sau”.

### Template

Summary list không có version; fetch detail trước mutation. Chỉ draft active (`!confirmed && !archivedAt`) có thể edit. Submit toàn bộ `content` và `expectedVersion`.

Sau confirm, ẩn edit controls. Publish/unpublish chỉ hiển thị cho `role === "ADMIN"` và template của chính user; API response không trả `ownerId`, vì vậy route đi từ `scope=mine` hoặc state điều hướng phải giữ context ownership.

### Upload

Validate client để phản hồi nhanh, nhưng vẫn render `ApiError` backend:

- CV PDF: <= 5 MiB, <= 10 trang (client không bắt buộc đọc page count).
- JD PDF: <= 5 MiB, <= 20 trang.
- JD text: <= 30.000 ký tự mặc định.

## Interview screen reducer

State render dựa vào server:

| Server state | Screen behavior |
|---|---|
| `PREPARING` | Redirect/hiện preparation screen |
| `PREPARATION_FAILED` | Error + retry preparation |
| `READY` | CTA Start; chưa chạy timer |
| `IN_PROGRESS`, remaining > 0 | Turns + answer composer + Finish |
| `IN_PROGRESS`, remaining = 0 | Khóa composer, refetch đến khi `SCORING` |
| `SCORING` | Report processing screen |
| `SCORING_FAILED` | Scoring error + retry |
| `COMPLETED` | Report |
| `CANCELLED|EXPIRED` | Terminal message |

Không tự thêm interviewer message khi finish/timeout. Chỉ render turns server trả về.

### Pending answer model

Lưu pending mutation theo session, ít nhất trong session storage:

```ts
interface PendingAnswer {
  requestId: string;
  expectedTurnIndex: number;
  answer: string;
  submittedAt: string;
}
```

Quy trình submit:

1. Lấy current interviewer turn từ history và dùng `turnIndex` của nó.
2. Sinh UUID, lưu `PendingAnswer`, render optimistic candidate bubble.
3. Submit với header key và body tương ứng; disable composer.
4. Thành công: merge `candidateTurn` + `interviewerTurn`, xóa pending.
5. HTTP/network error: fetch conversation; match `turn.requestId`.
6. `COMPLETED`: dùng server turns và xóa pending.
7. `FAILED`: giữ nút Retry dùng nguyên key/body.
8. `PROCESSING`: tiếp tục hiện spinner; không sinh key mới.

Nếu server trả `INTERVIEW_TURN_OUT_OF_SEQUENCE`, bỏ draft submit hiện tại, fetch conversation và đưa user về turn mới nhất. Nếu `INTERVIEW_TURN_IDEMPOTENCY_CONFLICT`, coi là client-state corruption, sinh key mới chỉ sau khi user xác nhận đây thật sự là answer mới.

## Countdown

Sau start/resume, tính từ `deadlineAt`, không giảm `remainingSeconds` cũ:

```ts
const remaining = Math.max(
  0,
  Math.ceil((Date.parse(deadlineAt) - Date.now()) / 1000),
);
```

Khi về 0: khóa composer ngay, clear pending draft submit chưa gửi, refetch conversation/session. Scheduler backend có độ trễ ngắn nên UI cần chấp nhận `IN_PROGRESS + 0`.

## Report UI

- Chỉ render score khi khác `null`.
- `overallScore=null` nghĩa là coverage chưa đủ, không phải 0 điểm.
- Hiện `coveragePercentage` cạnh confidence.
- Sort focus area theo `displayOrder`.
- `improvements` có tối đa 3 mục ngắn và không chứa dẫn chứng.
- Mỗi focus area chỉ hiển thị `summary` ngắn; report response không công khai dẫn chứng nội bộ.
- `improvements` và `focusAreas` an toàn để map trực tiếp vì backend trả `[]` khi rỗng.

## Route guards và bootstrap

App bootstrap:

1. Nếu chưa có access token trong memory, gọi `/api/auth/refresh` một lần.
2. Thành công thì gọi `/api/auth/me` và render protected app.
3. Refresh không hợp lệ thì render public auth routes.
4. Sau login/register/Google, set token + user từ auth response; `/me` bổ sung full name/avatar.

Route interview phải fetch server state trước khi quyết định màn hình; URL không chứng minh state hiện tại. Ví dụ user mở thẳng `/interviews/501`, conversation có thể đã `SCORING` và phải redirect report.

## Known integration blocker

Backend `SecurityConfig` hiện cho phép CORS headers `Authorization`, `Content-Type`, `Accept`, `Origin`, `X-Requested-With` nhưng thiếu `Idempotency-Key`. Browser cross-origin sẽ preflight và có thể chặn create session/submit answer.

Giải pháp frontend trong local dev là proxy `/api` qua cùng origin. Production cần backend thêm `Idempotency-Key` vào allowed headers; không bỏ header vì backend thực tế bắt buộc nó.

## Definition of done

- Không có API protected nào thiếu Bearer; mọi auth request bật credentials.
- Refresh là single-flight và chỉ retry request gốc một lần.
- CV/Profile và JD/Template được trình bày thành hai pipeline thống nhất.
- Mọi async pipeline resume được sau reload bằng ID/list endpoint.
- Profile/template update dùng version đúng và không mất child item.
- Create session và answer giữ idempotency key qua retry/mất mạng.
- Interview resume dùng server turns; countdown dùng `deadlineAt`.
- Không render composer ngoài `IN_PROGRESS` hoặc khi timer đã 0.
- Report xử lý đúng `overallScore=null`, empty arrays và scoring retry.
- `ApiError.code` quyết định UX; `message` chỉ là fallback.
- Role `USER`/`ADMIN`, owner/public và archived state đều có guard UI tương ứng.
