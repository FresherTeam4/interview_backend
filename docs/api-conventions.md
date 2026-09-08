# Quy ước API dùng chung

## Base URL và content type

Ví dụ local: `http://localhost:8080`. Các path trong tài liệu đều bắt đầu bằng `/api`.

- JSON request: `Content-Type: application/json`.
- Upload: `multipart/form-data`, field bắt buộc có tên `file`. Không tự đặt header `Content-Type`; browser phải sinh boundary.
- Timestamp là ISO-8601 UTC, ví dụ `2026-09-06T08:00:00Z`.
- Date của project là `YYYY-MM-DD`.
- Các field không có dữ liệu được serialize thành `null`; các collection trong conversation/report luôn là `[]` khi rỗng.

## Xác thực

Access token nằm trong JSON của register/login/Google/refresh. Gửi token cho API protected:

```http
Authorization: Bearer <accessToken>
```

Refresh token nằm trong cookie `HttpOnly`; JavaScript không đọc và không tự gắn giá trị cookie. Mọi request auth, đặc biệt `/login`, `/register`, `/google`, `/refresh`, `/logout`, phải bật credentials:

```ts
fetch(`${API_URL}/api/auth/refresh`, {
  method: "POST",
  credentials: "include",
});
```

Nếu dùng Axios, đặt `withCredentials: true`. Access token nên giữ trong memory; có thể khôi phục phiên bằng `/refresh` khi app bootstrap. Không log access token, Google ID token hoặc response `Set-Cookie`.

### Refresh interceptor

Khi một API protected trả `401 AUTHENTICATION_REQUIRED`:

1. Chỉ cho phép **một** request `/api/auth/refresh` chạy tại một thời điểm.
2. Các request 401 khác chờ cùng promise đó.
3. Refresh thành công: thay access token rồi retry request gốc đúng một lần.
4. Refresh trả `401 MISSING_REFRESH_TOKEN` hoặc `INVALID_REFRESH_TOKEN`: xóa auth state và chuyển về login.

Refresh token được rotate sau mỗi lần dùng. Nếu gọi nhiều refresh song song bằng cùng cookie, backend có thể xem đó là token reuse và thu hồi cả token family; vì vậy single-flight là bắt buộc.

## Idempotency-Key

Hai endpoint bắt buộc header này dù annotation controller đánh dấu header là optional:

```http
Idempotency-Key: <chuỗi không rỗng, tối đa 100 ký tự>
```

- `POST /api/interview-sessions`: một key đại diện cho đúng một bộ option tạo session.
- `POST /api/interview-sessions/{id}/answers`: một key đại diện cho đúng một câu trả lời tại đúng turn.

Dùng UUID. Tạo key trước request, giữ nó cùng draft/pending mutation, và tái sử dụng key khi retry do timeout/mất mạng. Chỉ tạo key mới cho một hành động mới của người dùng.

## Error response

Mọi lỗi do backend xử lý có dạng:

```json
{
  "timestamp": "2026-09-07T08:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/profiles/21",
  "fieldErrors": {
    "name": "Profile name is required"
  }
}
```

```ts
export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors: Record<string, string> | null;
}
```

`fieldErrors` chỉ có giá trị cho lỗi Bean Validation; lỗi nghiệp vụ thường trả `null`. Frontend nên quyết định hành vi theo `code`, dùng `message` làm fallback và map `fieldErrors` vào form.

| HTTP | Code thường gặp | Xử lý frontend |
|---:|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST` | Giữ form, hiện lỗi field/message |
| 401 | `AUTHENTICATION_REQUIRED` | Thử refresh single-flight rồi retry một lần |
| 401 | `INVALID_CREDENTIALS`, `INVALID_GOOGLE_TOKEN`, `ACCOUNT_DISABLED` | Hiện lỗi đăng nhập, không retry tự động |
| 401 | `MISSING_REFRESH_TOKEN`, `INVALID_REFRESH_TOKEN` | Xóa auth state; backend cũng clear cookie |
| 403 | `ACCESS_DENIED` | Hiện trang/notification không đủ quyền |
| 404 | `*_NOT_FOUND` | Resource không tồn tại hoặc không accessible |
| 409 | `*_VERSION_CONFLICT` | Fetch bản mới, cho user review trước khi submit lại |
| 409 | `*_IN_PROGRESS`, `INTERVIEW_TURN_PROCESSING` | Tiếp tục poll/chờ; không tạo mutation mới |
| 409 | `*_IDEMPOTENCY_CONFLICT` | Bug phía client: key cũ đã gắn với payload khác |
| 413 | `UPLOAD_TOO_LARGE`, `CV_FILE_TOO_LARGE`, `JD_FILE_TOO_LARGE`, `JD_TEXT_TOO_LONG` | Báo giới hạn input |
| 415 | `CV_INVALID_FILE_TYPE`, `JD_INVALID_FILE_TYPE` | Chỉ nhận PDF hợp lệ |
| 422 | Lỗi parse/processing/scoring | Hiện retry nếu state cho phép |
| 500/503/504 | `AI_*`, `INTERNAL_ERROR`, `STORAGE_*` | Giữ context/idempotency key để retry an toàn |

## Polling và request dài

- Poll CV/JD/preparation/report mỗi 1 giây trong vài lần đầu, sau đó 2–3 giây. Dừng khi vào terminal state hoặc unmount.
- Không giả định response đầu của API async vẫn ở initial state; worker có thể hoàn tất trước khi response trả về.
- `POST /answers` gọi AI đồng bộ và có thể mất hàng chục giây. UI cần trạng thái “AI đang trả lời”, chặn double submit và đặt timeout client lớn hơn timeout backend.
- Abort request ở browser không hủy việc backend đã bắt đầu. Khi không chắc kết quả, đọc resource/conversation hoặc retry bằng cùng idempotency key.

## Quyền

| Nhóm | Quyền |
|---|---|
| Public | Auth endpoints, trừ `/me` và `/logout-all` |
| `USER` | CV và Candidate Profile |
| Authenticated | JD, đọc/template owner hoặc public, interview session của chính mình |
| `ADMIN` | Publish/unpublish template **do chính admin sở hữu** |

Các endpoint owner-scoped thường trả `404` thay vì `403` khi ID thuộc người khác.

