# API quản trị MVP

Các endpoint trong tài liệu này yêu cầu access token của tài khoản có role `ADMIN`.
Tài khoản thường nhận `403 ACCESS_DENIED`; request chưa xác thực nhận `401`.

Backend không cung cấp API nâng role. Ở môi trường phát triển, tạo một tài khoản qua
luồng đăng ký rồi cập nhật role bằng thao tác quản trị database có kiểm soát.

## Tổng quan

```http
GET /api/admin/overview?days=7
Authorization: Bearer <admin-access-token>
```

`days` mặc định là `7`, hợp lệ từ `1` đến `90`. Số phiên chỉ tính các phiên được tạo
trong kỳ. `completionRate` bằng `completed / totalInPeriod * 100`, làm tròn hai chữ số.

```json
{
  "generatedAt": "2026-09-14T08:00:00Z",
  "periodDays": 7,
  "users": {
    "total": 120,
    "enabled": 115,
    "newInPeriod": 18
  },
  "sessions": {
    "totalInPeriod": 80,
    "completed": 55,
    "inProgress": 8,
    "preparationFailed": 4,
    "scoringFailed": 3,
    "completionRate": 68.75
  },
  "templates": {
    "published": 12
  }
}
```

## Người dùng

### Danh sách

```http
GET /api/admin/users?keyword=minh&role=USER&enabled=true&page=0&size=20
```

Các filter đều không bắt buộc. `keyword` tìm không phân biệt hoa thường theo tên và
email. `size` hợp lệ từ `1` đến `100`; kết quả mặc định sắp xếp mới nhất trước.

```json
{
  "items": [
    {
      "id": 15,
      "fullName": "Nguyễn Văn Minh",
      "email": "minh@example.com",
      "role": "USER",
      "enabled": true,
      "createdAt": "2026-09-10T08:00:00Z",
      "updatedAt": "2026-09-10T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

### Chi tiết

```http
GET /api/admin/users/{userId}
```

Ngoài thông tin tài khoản, response có số CV/JD active, tổng số phiên và số phiên đã
hoàn thành. Backend không trả password hash hoặc Google ID.

### Khóa hoặc mở tài khoản

```http
PATCH /api/admin/users/{userId}/status
Content-Type: application/json

{
  "enabled": false
}
```

Endpoint chỉ thay đổi tài khoản role `USER`. Tài khoản `ADMIN` trả
`409 ADMIN_USER_STATUS_PROTECTED`. Khi khóa user, backend thu hồi mọi refresh token;
access token đã cấp cũng bị từ chối từ request tiếp theo. Gửi lại cùng trạng thái là
idempotent.

## Vận hành phiên phỏng vấn

### Danh sách

```http
GET /api/admin/interview-sessions?keyword=minh@example.com&status=SCORING_FAILED&mode=TURN_BASED&from=2026-09-01T00:00:00Z&to=2026-09-14T23:59:59Z&page=0&size=20
```

`keyword` tìm theo tên hoặc email chủ phiên. `from` và `to` lọc `createdAt` và dùng
ISO-8601. Nếu `from` sau `to`, backend trả `400 VALIDATION_FAILED`.

Danh sách chỉ trả metadata vận hành: owner, trạng thái, mode, tên template/profile,
mã lỗi và thời gian. Không trả CV, snapshot hoặc transcript.

### Chi tiết

```http
GET /api/admin/interview-sessions/{sessionId}
```

Response bổ sung error message, model/prompt/schema version, các mốc lifecycle và
`transitions` theo thứ tự thời gian tăng dần. Dữ liệu raw của template/profile và nội
dung hội thoại không nằm trong contract admin MVP.

### Retry preparation

```http
POST /api/admin/interview-sessions/{sessionId}/preparation/retry
```

Chỉ hợp lệ khi session đang `PREPARATION_FAILED`. Response `202` và session chuyển
sang `PREPARING`.

### Retry scoring

```http
POST /api/admin/interview-sessions/{sessionId}/scoring/retry
```

Chỉ hợp lệ khi session đang `SCORING_FAILED`. Response `202` và session chuyển sang
`SCORING`.

Hai thao tác retry khóa session trong transaction nên request đồng thời không thể
dispatch cùng một công việc hai lần. Transition được lưu với actor `ADMIN`.

## Template dành cho admin

Không có endpoint tạo template riêng cho admin. Admin dùng luồng hiện tại:

1. `POST /api/job-descriptions` để gửi JD text hoặc PDF.
2. Poll `GET /api/job-descriptions/{id}` đến `READY` và lấy `templateId`.
3. Sửa rồi confirm template.
4. Gọi `POST /api/interview-templates/{id}/publish` hoặc `/unpublish`.

Admin chỉ publish/unpublish template do chính mình sở hữu.
