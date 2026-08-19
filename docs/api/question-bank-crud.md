# Question Bank CRUD và bộ lọc đa lựa chọn

## 1. Phạm vi

API cho phép `ADMIN` tạo, xem, sửa, vô hiệu hóa và lọc ngân hàng câu hỏi.
Mọi endpoint bên dưới đều cần access token có role `ADMIN`:

```http
Authorization: Bearer <access-token>
```

Question Bank dùng hai lớp phân loại độc lập:

- `techStacks`: nhóm nghiệp vụ rộng, ví dụ `BACKEND`, `DEVOPS`, `FRONTEND`.
- `technologies`: công nghệ cụ thể, chia theo `LANGUAGE`, `FRAMEWORK`, `DATABASE`,
  `CLOUD`, `PLATFORM`, `TOOL`; ví dụ Java, C#, Spring Boot, Docker.

Một câu hỏi có thể thuộc nhiều Tech Stack và nhiều Technology. Ví dụ một câu hỏi
triển khai Spring Boot bằng Docker có thể thuộc `BACKEND` + `DEVOPS` và được gắn
`JAVA` + `SPRING_BOOT` + `DOCKER`.

## 2. Endpoint

| Method | Path | Chức năng |
|---|---|---|
| `POST` | `/api/admin/questions` | Tạo câu hỏi |
| `GET` | `/api/admin/questions/{id}` | Xem một câu hỏi |
| `GET` | `/api/admin/questions` | Phân trang, tìm kiếm và lọc |
| `PUT` | `/api/admin/questions/{id}` | Thay thế toàn bộ dữ liệu câu hỏi |
| `DELETE` | `/api/admin/questions/{id}` | Vô hiệu hóa câu hỏi (soft delete) |
| `GET` | `/api/admin/tech-stacks?activeOnly=true` | Danh sách nhóm Tech Stack cho form/filter |
| `GET` | `/api/admin/technologies?activeOnly=true&type=LANGUAGE` | Danh sách công nghệ cho form/filter |

`page` bắt đầu từ 0; `size` nằm trong khoảng 1–50.

## 3. Tìm kiếm và checkbox filter

Frontend có thể gửi một tham số nhiều lần:

```http
GET /api/admin/questions?keyword=spring&active=true&techStackIds=2&techStackIds=4&technologyIds=1&technologyIds=12&levels=JUNIOR&levels=MID&questionTypes=TECHNICAL&difficulties=MEDIUM&difficulties=HARD&page=0&size=20
```

Spring cũng chấp nhận dạng phân tách bằng dấu phẩy, ví dụ
`techStackIds=2,4`; frontend nên thống nhất một cách gửi.

| Tham số | Kiểu | Ý nghĩa |
|---|---|---|
| `keyword` | string | Tìm chuỗi con không phân biệt hoa/thường trong `contentVi`, `contentEn`; tối đa 200 ký tự |
| `active` | boolean | Lọc câu hỏi đang hoạt động/đã vô hiệu hóa |
| `techStackIds` | danh sách positive integer | Các nhóm Tech Stack được chọn |
| `unclassified` | boolean | `true`: chỉ lấy câu hỏi chưa có Tech Stack |
| `technologyIds` | danh sách positive integer | Ngôn ngữ/framework/database/cloud/platform/tool được chọn |
| `levels` | danh sách enum | `FRESHER`, `JUNIOR`, `MID`, `SENIOR` |
| `questionTypes` | danh sách enum | `BEHAVIORAL`, `TECHNICAL`, `CASE_STUDY` |
| `difficulties` | danh sách enum | `EASY`, `MEDIUM`, `HARD` |
| `page` | integer | Trang bắt đầu từ 0 |
| `size` | integer | Số phần tử, từ 1 đến 50 |

Quy tắc kết hợp:

- Các giá trị trong cùng một nhóm dùng `OR`: chọn `BACKEND` và `DEVOPS` trả về
  câu hỏi thuộc ít nhất một trong hai nhóm.
- Các nhóm khác nhau dùng `AND`: `BACKEND` + `JAVA` + `JUNIOR` yêu cầu câu hỏi
  đồng thời khớp cả ba nhóm.
- Mỗi danh sách tối đa 20 giá trị.
- Không được gửi đồng thời `techStackIds` và `unclassified=true`.

API trả mỗi câu hỏi một lần dù câu hỏi khớp nhiều lựa chọn.

## 4. Tạo câu hỏi

```json
{
  "contentVi": "Làm thế nào triển khai Spring Boot bằng Docker?",
  "contentEn": "How do you deploy Spring Boot with Docker?",
  "techStackIds": [2, 4],
  "technologyIds": [1, 12, 28],
  "level": "JUNIOR",
  "questionType": "TECHNICAL",
  "difficulty": "MEDIUM",
  "companyRef": null,
  "active": true
}
```

- `contentVi`: bắt buộc, không được chỉ chứa khoảng trắng.
- `contentEn`: tùy chọn; đây là bản dịch tiếng Anh, không phải ngôn ngữ lập trình.
- `techStackIds`: các nhóm nghiệp vụ; câu hỏi `TECHNICAL` bắt buộc có ít nhất một
  Tech Stack đang hoạt động.
- `technologyIds`: công nghệ cụ thể; có thể rỗng nhưng mọi id được gửi phải tồn tại
  và đang hoạt động.
- `level`, `questionType`, `difficulty`: bắt buộc.
- `companyRef`: nguồn tham khảo công ty, tối đa 150 ký tự.
- `active`: mặc định `true` nếu không gửi.
- `createdById`: backend lấy từ tài khoản đăng nhập, frontend không được gửi.

Tạo thành công trả `201 Created`, header `Location` và dữ liệu vừa tạo.

## 5. Response

```json
{
  "id": 41,
  "contentVi": "Làm thế nào triển khai Spring Boot bằng Docker?",
  "contentEn": "How do you deploy Spring Boot with Docker?",
  "techStacks": [
    {"id": 2, "code": "BACKEND", "nameVi": "Backend", "nameEn": "Backend", "active": true},
    {"id": 4, "code": "DEVOPS", "nameVi": "DevOps", "nameEn": "DevOps", "active": true}
  ],
  "technologies": [
    {"id": 28, "code": "DOCKER", "nameVi": "Docker", "nameEn": "Docker", "type": "TOOL", "active": true},
    {"id": 1, "code": "JAVA", "nameVi": "Java", "nameEn": "Java", "type": "LANGUAGE", "active": true},
    {"id": 12, "code": "SPRING_BOOT", "nameVi": "Spring Boot", "nameEn": "Spring Boot", "type": "FRAMEWORK", "active": true}
  ],
  "level": "JUNIOR",
  "questionType": "TECHNICAL",
  "difficulty": "MEDIUM",
  "companyRef": null,
  "createdById": 10,
  "active": true,
  "version": 0,
  "createdAt": "2026-08-17T10:00:00Z",
  "updatedAt": "2026-08-17T10:00:00Z"
}
```

## 6. Cập nhật và chống ghi đè

`PUT` là cập nhật toàn bộ, vì vậy frontend phải gửi lại tất cả trường bắt buộc,
hai danh sách phân loại, `active` và `version` mới nhất:

```json
{
  "contentVi": "Làm thế nào triển khai Spring Boot bằng Docker trong CI/CD?",
  "contentEn": "How do you deploy Spring Boot with Docker in CI/CD?",
  "techStackIds": [2, 4],
  "technologyIds": [1, 12, 28, 31],
  "level": "MID",
  "questionType": "TECHNICAL",
  "difficulty": "HARD",
  "companyRef": null,
  "active": true,
  "version": 0
}
```

Nếu dữ liệu đã được người khác cập nhật, API trả `409` với code
`QUESTION_VERSION_CONFLICT`. Frontend cần tải lại bản mới trước khi thử lại.

## 7. Xóa mềm

`DELETE` trả `204 No Content` và đổi `is_active=false`, không xóa vật lý. Điều này
giữ nguyên lịch sử phỏng vấn có tham chiếu câu hỏi. Có thể kích hoạt lại bằng
`PUT` với `active=true`.

## 8. Mã phản hồi quan trọng

| Status | Ý nghĩa |
|---|---|
| `200` | Đọc/cập nhật thành công |
| `201` | Tạo thành công |
| `204` | Vô hiệu hóa thành công |
| `400` | Body, enum, filter, paging hoặc quy tắc nghiệp vụ không hợp lệ |
| `401` | Thiếu/sai access token |
| `403` | Tài khoản không có role `ADMIN` |
| `404` | Không tìm thấy câu hỏi, người tạo, Tech Stack hoặc Technology đang hoạt động |
| `409` | Xung đột version hoặc constraint database |

Swagger UI: `/swagger-ui.html` khi OpenAPI được bật.

## 9. Thay đổi không tương thích cần frontend lưu ý

- Request: `techStackId` đổi thành `techStackIds` (mảng).
- Response: `techStack` đổi thành `techStacks` (mảng).
- Filter số ít `techStackId`, `level`, `questionType`, `difficulty` đổi thành các
  tham số số nhiều tương ứng.
- Thêm `technologyIds`, response `technologies` và endpoint lấy danh mục công nghệ.

Frontend và backend phải được deploy cùng phiên bản cho thay đổi này.
