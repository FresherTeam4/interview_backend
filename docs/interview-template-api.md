# Pipeline JD và Interview Template

JD là tài nguyên đầu vào. Backend nhận PDF hoặc text, extract text, phân tích bằng AI và tạo một `InterviewTemplate` ở trạng thái draft. Người dùng chỉnh draft rồi confirm; sau confirm nội dung bị khóa và có thể được dùng trực tiếp bởi interview session.

Schema: [SQL](../database_docs/migrations/001-interview-templates.sql) và [DBML](../database_docs/interview-template-schema.dbml).

## Luồng xử lý

```text
PDF/text JD
  -> JobDescriptionDocument
  -> extract text
  -> AI analysis
  -> JobDescriptionAnalysisResult (immutable)
  -> InterviewTemplate (draft)
  -> edit
  -> confirm (freeze)
```

Trạng thái JD: `UPLOADED`, `EXTRACTING`, `ANALYZING`, `READY`, `FAILED`. Với text input, pipeline bỏ qua `EXTRACTING`. Khi thất bại, `failureStage` cho biết lỗi ở `DISPATCH`, `EXTRACTION` hay `ANALYSIS`.

## API Job Description

Upload PDF:

```http
POST /api/job-descriptions
Content-Type: multipart/form-data

file=<PDF>
```

Gửi text:

```http
POST /api/job-descriptions
Content-Type: application/json

{
  "title": "Java Backend JD",
  "text": "..."
}
```

Hai endpoint trả `202 Accepted` khi tạo job mới và `200 OK` nếu checksum trùng với một JD đang có template draft của cùng owner. Template đã confirm không được tái sử dụng theo cách này; gửi lại cùng JD sẽ tạo draft mới. Frontend poll `GET /api/job-descriptions/{id}`; khi `status=READY`, response có `templateId`.

Các endpoint khác:

```text
GET    /api/job-descriptions
GET    /api/job-descriptions/{id}
GET    /api/job-descriptions/{id}/file
GET    /api/job-descriptions/{id}/analysis
POST   /api/job-descriptions/{id}/retry
DELETE /api/job-descriptions/{id}
```

## Dữ liệu AI extract

`JobAnalysis` gồm:

- `sufficientJobContext`: Trạng thái đủ ngữ cảnh (boolean).
- `sourceLanguage`: Ngôn ngữ nguồn (ví dụ: "vi", "en").
- `jobTitle`: Chức danh công việc.
- `targetSeniority`: Cấp bậc yêu cầu (INTERN, FRESHER, JUNIOR, MIDDLE, SENIOR, LEAD, MANAGER).
- `domain`: Lĩnh vực / chuyên ngành (Fintech, E-commerce, Logistics...).
- `summary`: Tóm tắt vai trò công việc dạng Markdown.
- `keySkills`: Danh sách các kỹ năng / yêu cầu trọng tâm gồm `name`, `level` (`MUST_HAVE`, `NICE_TO_HAVE`), `description`.

Kết quả AI gốc nằm trong `job_description_analysis_results` và không bị sửa. Người dùng có thể chỉnh sửa nội dung trên template draft trước khi confirm.

## API Interview Template

```text
GET  /api/interview-templates?scope=mine|public
GET  /api/interview-templates/{id}
PUT  /api/interview-templates/{id}
POST /api/interview-templates/{id}/confirm
POST /api/interview-templates/{id}/publish
POST /api/interview-templates/{id}/unpublish
POST /api/interview-templates/{id}/archive
```

Update gửi `title`, toàn bộ `content` theo schema `JobAnalysis` và `expectedVersion`. Confirm, publish, unpublish và archive cũng dùng optimistic version.

Confirm đặt `confirmedAt` và khóa nội dung. Muốn thay đổi nội dung sau confirm phải tạo một JD/template mới. Publish chỉ dành cho admin, yêu cầu template đã confirm. Archive tự gỡ publish.

Interview session mới phải tham chiếu trực tiếp `interview_templates.id` và chỉ được tạo khi `confirmed_at IS NOT NULL` và `archived_at IS NULL`.
