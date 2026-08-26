# API — CV & Hồ sơ (bản 2)

Thiết kế API cho 3 user story nhóm "CV & JD", theo hướng **giữ nhiều CV, mỗi CV một hồ sơ,
người dùng tự chọn dùng cái nào lúc tạo phiên phỏng vấn**. Đọc kèm
`database_docs/schema-guide.md` mục "Nhóm 2 — CV & Hồ sơ".

**Chưa viết code** — file này để anh review lần hai rồi mới quyết định làm hay không.

## 0. Đổi gì so với bản 1

| | Bản 1 (một CV) | Bản 2 (nhiều CV) |
|---|---|---|
| Số CV giữ lại | 1 đang hoạt động, upload mới **ghi đè** | Giữ tất cả, upload mới **thêm vào** |
| Số hồ sơ | 1 hồ sơ / người | 1 hồ sơ / **CV** |
| Chọn dùng CV nào | Không có gì để chọn | Chọn lúc tạo phiên phỏng vấn |
| Sửa tay khi có CV mới | **Mất sạch**, `confirmed_at` bị reset | Không mất gì, hồ sơ cũ y nguyên |
| Database | `019`–`024` như đang có | **Phải sửa `021`** (mục 2) |
| Entity / repository | như đang có | 1 entity + 2 repository phải sửa (mục 2.3) |
| AI provider | để mở | **Gemini** `gemini-3.5-flash` qua Spring AI (mục 5) |
| Storage | để mở | **MinIO** qua docker compose (mục 11) |
| Rút text từ PDF | PDFBox `PDFTextStripper` | Gửi thẳng file PDF cho Gemini, PDFBox chỉ còn để validate |
| Đường dẫn | `/api/cv`, `/api/profile` | `/api/cvs`, `/api/profiles` (số nhiều, có `{id}`) |

Điểm cần anh biết rõ: tiêu chí nghiệm thu US-1 viết *"Mỗi người dùng giữ 1 CV đang hoạt động,
tải lên mới thì ghi đè"*. Bản 2 **cố tình làm khác** theo yêu cầu của anh. Nếu tiêu chí đó
đang là cam kết với ai khác trong team thì cần sửa lại tiêu chí trước, kẻo lúc nghiệm thu
bị tính là làm sai.

## 1. Phạm vi

| User story | Endpoint phụ trách |
|---|---|
| US-1 — Tải lên CV PDF | `POST /api/cvs`, `GET /api/cvs`, `GET /api/cvs/{cvId}`, `GET /api/cvs/{cvId}/file`, `DELETE /api/cvs/{cvId}` |
| US-2 — Bóc tách thành hồ sơ có cấu trúc | chạy nền sau US-1, `POST /api/cvs/{cvId}/parse` để thử lại |
| US-3 — Sửa lại thông tin bóc tách sai | `GET /api/profiles`, `GET /api/profiles/{id}`, `PUT /api/profiles/{id}`, `POST /api/profiles/{id}/confirm` |

**Phần "JD" trong tên epic không làm ở đây**: toàn bộ `dbdiagram.txt` không có bảng
`job_descriptions` nào, và cả 3 tiêu chí nghiệm thu đều chỉ nói về CV. Nếu JD có trong
sprint thì phải thiết kế bảng trước đã.

## 2. Thay đổi database bắt buộc

Đây là phần đắt nhất của hướng "nhiều CV". Không tránh được, vì DDL hiện tại **cấm** một
người có hai hồ sơ:

```
021-create-candidate-profiles.sql:21
    CONSTRAINT uq_candidate_profiles_user UNIQUE (user_id)
```

### 2.1. Sửa `021` tại chỗ, không viết `025`

`019`–`024` nằm trong commit `64b5b04`, nhưng commit đó **chưa push** (`main` đang ahead 1) và
changeset **chưa apply vào database nào** — `interview_db` vẫn đang ở `018`. Nghĩa là chưa ai
ngoài máy này có nó, nên sửa thẳng vào `021` là sạch nhất: không để lại một migration "thêm rồi
lại bỏ" trong lịch sử. Cụ thể trong `021`:

| Dòng | Đang là | Sửa thành |
|---|---|---|
| 9 | comment `'One live profile per user'` | `'Owner; a user may have many profiles, one per CV'` |
| 21 | `CONSTRAINT uq_candidate_profiles_user UNIQUE (user_id)` | `CONSTRAINT uq_candidate_profiles_cv_document UNIQUE (cv_document_id)` |
| 37–38 | comment + `CREATE INDEX idx_candidate_profiles_cv_document_id` | đổi thành `CREATE INDEX idx_candidate_profiles_user_id ON candidate_profiles (user_id)` |

Ba việc đó phải đi cùng nhau: bỏ `uq_candidate_profiles_user` thì `fk_candidate_profiles_user`
**mất index đỡ lưng** (chính comment ở dòng 37 đang nói vậy), MySQL sẽ báo lỗi 1553 nếu không
có index khác cho `user_id`. Còn `idx_candidate_profiles_cv_document_id` thành thừa vì
UNIQUE mới đã tự là index.

Nếu trước khi tôi làm mà commit `64b5b04` đã được push và có người pull về chạy app, thì **không
sửa `021` nữa** (Liquibase sẽ báo checksum lệch) mà viết `025` theo đúng thứ tự này:

```sql
CREATE INDEX idx_candidate_profiles_user_id ON candidate_profiles (user_id);
ALTER TABLE candidate_profiles DROP INDEX uq_candidate_profiles_user;
ALTER TABLE candidate_profiles
    ADD CONSTRAINT uq_candidate_profiles_cv_document UNIQUE (cv_document_id);
DROP INDEX idx_candidate_profiles_cv_document_id ON candidate_profiles;
```

Đảo thứ tự hai dòng đầu là lỗi ngay.

### 2.2. `cv_documents.is_active` đổi nghĩa

Cột này đang được comment là *"One active CV per user; a new upload flips the previous row to
false"* (`019:16`). Nghĩa đó chết theo bản 1. Bản 2 dùng lại đúng cột đó làm **cờ xóa mềm**:

- `true` — CV còn hiện trong danh sách, còn chọn được lúc tạo phiên.
- `false` — người dùng đã xóa; hàng vẫn còn trong DB để phiên phỏng vấn cũ không đứt liên kết.

Nên `019:16` phải sửa comment, và index `idx_cv_documents_user_active (user_id, is_active)`
vẫn đúng nguyên (giờ nó phục vụ "liệt kê CV chưa xóa của tôi").

Tôi **giữ tên `is_active`** thay vì đổi thành `is_deleted`: đổi tên thì phải đảo hết logic và
đổi cả tên field trong entity, mà "active = còn dùng được" vẫn đọc ra nghĩa. Nếu anh thấy tên
này gây hiểu nhầm thì đây là lúc rẻ nhất để đổi — chưa apply vào DB nào.

Đồng thời **biến mất một vấn đề của bản 1**: cái invariant "chỉ một hàng `is_active = true`
mỗi người" mà MySQL không có unique index lọc nên không cưỡng chế được, giờ không còn là
invariant nữa. Đề xuất changelog `025` thêm generated column trong bản 1 **bỏ luôn**, không
cần.

### 2.3. Entity và repository phải sửa

| File | Đang là | Sửa thành |
|---|---|---|
| `entity/CandidateProfile.java:54-56` | `@OneToOne` tới `user`, `unique = true` | `@ManyToOne`, bỏ `unique` |
| `entity/CandidateProfile.java:59-61` | `@ManyToOne` tới `cvDocument` | `@OneToOne`, `unique = true` |
| `entity/CandidateProfile.java:40-42` | `@Index(... "idx_candidate_profiles_cv_document_id")` | `@Index(... "idx_candidate_profiles_user_id", columnList = "user_id")` |
| `CvDocumentRepository:16` | `Optional<CvDocument> findByUserIdAndActiveTrue` | `List<CvDocument> findByUserIdAndActiveTrueOrderByUploadedAtDesc` |
| `CvDocumentRepository:34-41` | `deactivateAllByUserId` (bulk update) | bỏ hẳn — xóa mềm chỉ là load entity rồi `setActive(false)` trong transaction, không cần `@Modifying` |
| `CandidateProfileRepository:11` | `Optional<CandidateProfile> findByUserId` | `List<CandidateProfile> findByUserIdAndCvDocumentActiveTrueOrderByCreatedAtDesc` |
| `CandidateProfileRepository:13` | `existsByUserId` | bỏ, không còn ai gọi |
| `CandidateProfileRepository:19` | `existsByUserIdAndConfirmedAtIsNotNull` | bỏ, thay bằng finder ở dòng dưới |
| `CandidateProfileRepository` | — | thêm `Optional<CandidateProfile> findByIdAndUserIdAndCvDocumentActiveTrue(Long id, Long userId)` |
| `CvDocumentRepository` | — | thêm `countByUserIdAndActiveTrue`, `findByIdAndUserIdAndActiveTrue`, `findByStatus` |

Dòng thứ 5 là chỗ dễ bỏ sót nhất: `existsByUserIdAndConfirmedAtIsNotNull(userId)` giờ **sai về
mặt logic**, nó trả `true` khi người dùng có *bất kỳ* hồ sơ nào đã xác nhận, trong khi cổng chặn
cần biết **đúng hồ sơ được chọn** có xác nhận chưa. Thay bằng `findByIdAndUserIdAndCvDocumentActiveTrue`
rồi đọc `isConfirmed()` — một truy vấn, mà phân biệt được 404 với 409 (mục 7).

`findByIdAndUserId` (không lọc `active`) **vẫn giữ**, dùng đúng một chỗ: cấp link tải file. CV đã
xóa mềm thì mọi thao tác khác phải 404, nhưng phiên phỏng vấn cũ vẫn cần xem lại file của nó.

Và ba hàm `deleteAllByProfileId` ở `ProfileEducationRepository` / `ProfileSkillRepository` /
`ProfileProjectRepository` **mất hết chỗ dùng** — xem mục 6.2. Tôi sẽ nói trước ở chunk
service để anh quyết bỏ hay giữ.

## 3. Bảng endpoint

Tất cả đều cần `Authorization: Bearer <accessToken>` và role `USER`. Không endpoint nào nhận
`userId` từ client — luôn lấy từ `@CurrentUser CustomUserDetails`.

| Method | Path | Trả về | Mã thành công |
|---|---|---|---|
| POST | `/api/cvs` | `CvDocumentResponse` | 202 (200 nếu trùng file cũ) |
| GET | `/api/cvs` | `List<CvDocumentResponse>` | 200 |
| GET | `/api/cvs/{cvId}` | `CvDocumentResponse` | 200 |
| GET | `/api/cvs/{cvId}/file` | `CvFileUrlResponse` | 200 |
| POST | `/api/cvs/{cvId}/parse` | `CvDocumentResponse` | 202 |
| DELETE | `/api/cvs/{cvId}` | không body | 204 |
| GET | `/api/profiles` | `List<ProfileSummaryResponse>` | 200 |
| GET | `/api/profiles/{profileId}` | `CandidateProfileResponse` | 200 |
| PUT | `/api/profiles/{profileId}` | `CandidateProfileResponse` | 200 |
| POST | `/api/profiles/{profileId}/confirm` | `CandidateProfileResponse` | 200 |

10 endpoint, so với 8 của bản 1. Ba cái mới (`GET /api/cvs/{cvId}`, `DELETE /api/cvs/{cvId}`,
`GET /api/profiles`) đều là hệ quả trực tiếp của việc giữ nhiều CV; `GET /api/cv/active` của
bản 1 biến mất vì không còn "CV đang hoạt động" nào để trả.

`GET /api/cvs` là endpoint trung tâm của bản 2 — nó vừa là danh sách "CV của tôi", vừa là
nguồn dữ liệu cho ô chọn CV lúc tạo phiên, vừa là **đích poll** trạng thái bóc tách. Nên mỗi
phần tử mang kèm thông tin hồ sơ tương ứng:

```json
[
  {
    "id": 12,
    "originalFilename": "CV_NguyenVanA_Backend.pdf",
    "contentType": "application/pdf",
    "fileSizeBytes": 204800,
    "status": "PARSED",
    "statusMessage": null,
    "uploadedAt": "2026-08-24T03:00:00.123456Z",
    "parsedAt": "2026-08-24T03:00:18.900000Z",
    "profileId": 7,
    "profileConfirmed": true,
    "profileHeadline": "Java Backend Fresher"
  },
  {
    "id": 13, "originalFilename": "CV_NguyenVanA_Data.pdf", "status": "PARSING",
    "statusMessage": null, "parsedAt": null,
    "profileId": null, "profileConfirmed": false, "profileHeadline": null
  }
]
```

Nhờ ba trường `profile*` này, frontend hiển thị danh sách CV kèm nhãn "đã xác nhận / chưa xác
nhận" và gọi luôn `GET /api/profiles/{profileId}` mà không phải tra cứu hai bước.

`is_active` **không xuất hiện trong response** — danh sách chỉ trả CV chưa xóa, nên cờ đó
luôn `true`, trả ra chỉ gây thắc mắc. `storage_key` và `checksum_sha256` cũng không trả (mục 10).

Không có `ApiResponse<T>` bọc ngoài — giữ đúng kiểu `AuthController` hiện tại: thành công trả
DTO trần, lỗi trả `ApiError`.

## 4. Luồng upload và bóc tách

### 4.1. Chạy nền hay chặn request 30 giây

| | **Chạy nền + poll** (đề xuất) | Chặn request 30 giây |
|---|---|---|
| `status` trong DB | Có ý nghĩa: `UPLOADED → PARSING → PARSED/FAILED` | Vô dụng, không ai đọc |
| "Hiển thị trạng thái đang xử lý" | Poll `GET /api/cvs` | Chỉ có spinner, không biết đang ở bước nào |
| Nginx/proxy timeout mặc định 60s | Không chạm tới | Sát mép |
| Người dùng F5 giữa lúc chờ | Không mất gì | Mất kết quả, có thể parse lại lần 2 (tốn tiền) |
| Upload 2 CV liền nhau | Chạy song song được | Phải chờ lần lượt |
| Lượng code | Nhiều hơn: `@Async` + endpoint poll | Ít hơn |

Cột `status` + `status_message` chỉ có lý do tồn tại nếu chọn cột trái, nên tôi đi theo hướng đó.

### 4.2. Luồng đầy đủ

```
POST /api/cvs   (multipart/form-data, field "file")
   │
   ├─ 1. Chặn sớm, chưa ghi gì vào DB lẫn storage:
   │        đuôi .pdf · magic byte %PDF- · size ≤ 5MB · file không rỗng
   │        PDFBox mở được, không mã hóa, số trang ≤ 10
   │        sai → 415 / 413 / 400, không tạo hàng nào
   │
   ├─ 2. Đếm CV chưa xóa của user, ≥ 10 → 409 CV_LIMIT_REACHED
   │
   ├─ 3. Tính SHA-256 toàn bộ byte
   │        khớp một CV cũ đã PARSED → nhánh tái sử dụng (4.3), trả 200
   │
   ├─ 4. PUT lên MinIO, key = cv/{userId}/{uuid}.pdf
   │
   ├─ 5. INSERT cv_documents (status = UPLOADED, is_active = true)
   │        KHÔNG chạm tới CV cũ nào — đây là điểm khác bản 1
   │
   ├─ 6. Trả 202 + CvDocumentResponse(status = UPLOADED)   ← request kết thúc ở đây
   │
   └─ 7. @Async chạy tiếp:   status = PARSING
             │
             ├─ Tải file từ MinIO theo storage_key
             │     (không giữ 5MB byte trong RAM chờ hàng đợi, và nhánh
             │      "thử lại" ở 4.6 cũng phải tải lại y như vậy)
             │
             ├─ Gọi Gemini, timeout 25s, trả JSON theo schema v1 (mục 5)
             │     timeout / JSON sai schema / Gemini 4xx-5xx
             │     → FAILED + status_message đọc được, cho thử lại
             │
             ├─ INSERT cv_parse_results (raw_json, schema_version, model_name,
             │                           duration_ms, token_cost)     ← chỉ INSERT
             │
             ├─ INSERT candidate_profiles + 3 bảng con
             │     confirmed_at = NULL,  source = AUTO_PARSED
             │
             └─ status = PARSED, parsed_at = now
```

Frontend poll `GET /api/cvs` mỗi 2 giây khi trong danh sách còn phần tử `UPLOADED`/`PARSING`.

### 4.3. Tải lên đúng file cũ thì không parse lại

`checksum_sha256` + index `idx_cv_documents_user_checksum` có để làm việc này. Với
`findFirstByUserIdAndChecksumSha256OrderByUploadedAtDesc`:

| Hàng cũ tìm được | Xử lý | Mã |
|---|---|---|
| `PARSED`, `is_active = true` | Trả về chính CV đó, không upload, không gọi AI, không tạo hàng mới | 200 |
| `PARSED`, `is_active = false` (đã xóa mềm) | Bật lại `is_active = true`, hồ sơ cũ **kèm mọi chỉnh sửa tay** sống lại | 200 |
| `FAILED` hoặc không có | Đi tiếp luồng bình thường từ bước 4 | 202 |

Lý do không tạo hàng `cv_documents` mới cho cùng bộ byte: `cv_parse_results` là 1–1 với
document (`uq_cv_parse_results_document`), tạo hàng mới thì phải nhân đôi `raw_json` và ghi
`duration_ms` giả cho một lần parse chưa từng xảy ra, làm bẩn số liệu chi phí.

Nhánh này ở bản 2 **có giá trị hơn bản 1**: người dùng upload lại đúng file cũ sẽ lấy lại hồ
sơ đã sửa tay của mình, chứ không tạo thêm một hồ sơ trắng trùng nội dung nằm cạnh cái cũ.

Vẫn **bỏ được nếu anh muốn MVP gọn** — chỉ cần cắt bước 3.

### 4.4. Nhiều CV cùng tồn tại — điều gì xảy ra với cái cũ

**Không gì cả.** Đây là toàn bộ lý do đổi sang bản 2:

- CV cũ giữ nguyên `is_active = true`, vẫn nằm trong danh sách, vẫn chọn được.
- Hồ sơ cũ giữ nguyên nội dung, giữ nguyên `confirmed_at`, giữ nguyên mọi chỉnh sửa tay.
- CV mới sinh ra hồ sơ mới hoàn toàn độc lập, `confirmed_at = NULL`.

So với bản 1: mất hẳn cái bẫy *"tải CV mới xóa sạch chỉnh sửa tay"*, mất hẳn cảnh báo bắt
frontend phải hỏi lại trước khi chọn file, và mất hẳn việc reset `confirmed_at`.

Giá phải trả là **phải chặn số lượng**: bản 1 tự có giới hạn 1 CV vì ghi đè, bản 2 thì không.
Một người có thể upload 500 CV, mỗi cái tốn một lần gọi Gemini và một object trên MinIO. Nên
`app.cv.max-per-user` mặc định **10** → `409 CV_LIMIT_REACHED`, muốn thêm thì xóa cái cũ đi.
Con số 10 là tôi tự chọn, anh thấy ít/nhiều thì đổi một dòng cấu hình.

### 4.5. `DELETE /api/cvs/{cvId}` — xóa mềm

Bản 1 không có endpoint xóa vì chỉ có một CV và nó tự bị ghi đè. Bản 2 buộc phải có, không thì
người dùng đụng trần 10 CV là tắc.

**Xóa mềm, không xóa thật**: `UPDATE cv_documents SET is_active = false`. Không thể xóa thật vì
`candidate_profiles.cv_document_id` là `RESTRICT`, và sâu hơn nữa `interview_sessions.profile_id`
cũng `RESTRICT` — phiên phỏng vấn đã diễn ra phải giữ được liên kết về CV nó dựa trên.

| Trạng thái CV | Kết quả |
|---|---|
| `UPLOADED` / `PARSED` / `FAILED` | 204, biến khỏi danh sách |
| `PARSING` | `409 CV_PARSE_IN_PROGRESS` — job nền đang chạy sẽ ghi hồ sơ cho một CV vừa bị ẩn |
| `{cvId}` không thuộc người gọi | `404 CV_NOT_FOUND` |

Hệ quả kéo theo, cần nhất quán ở mọi truy vấn: hồ sơ của một CV đã xóa **không hiện trong
`GET /api/profiles`** và **không chọn được lúc tạo phiên** (mục 7). Cưỡng chế bằng cách mọi
truy vấn hồ sơ đều join qua `cvDocument.active = true` — đó là lý do
`findByUserIdAndCvDocumentActiveTrueOrderByCreatedAtDesc` ở mục 2.3 có tên dài như vậy.

Object trên MinIO **không xóa** cùng lúc. File chỉ vài trăm KB, mà xóa đi thì `GET
/api/cvs/{cvId}/file` của phiên cũ vỡ. Dọn rác thật là việc của một job định kỳ sau này.

### 4.6. Request / response cụ thể

**`POST /api/cvs`** — `multipart/form-data`, một field `file`.

```json
// 202 Accepted
{
  "id": 13,
  "originalFilename": "CV_NguyenVanA_Data.pdf",
  "contentType": "application/pdf",
  "fileSizeBytes": 204800,
  "status": "UPLOADED",
  "statusMessage": null,
  "uploadedAt": "2026-08-24T03:10:00.123456Z",
  "parsedAt": null,
  "profileId": null,
  "profileConfirmed": false,
  "profileHeadline": null
}
```

**`GET /api/cvs/{cvId}`** — 200, cùng shape một phần tử. Dùng khi frontend chỉ muốn poll đúng
một CV vừa upload thay vì cả danh sách. Khi `FAILED`:

```json
{
  "id": 13, "status": "FAILED",
  "statusMessage": "Gemini không trả về dữ liệu đúng định dạng, anh thử lại giúp em",
  "parsedAt": null, "profileId": null, "...": "..."
}
```

**`GET /api/cvs/{cvId}/file`** — 200:

```json
{ "url": "http://localhost:9000/interview-cv/cv/7/9f2c1e....pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=300&X-Amz-Signature=...",
  "expiresAt": "2026-08-24T03:15:00Z" }
```

URL presigned, sống 5 phút, bucket private. Truy vấn dùng `findByIdAndUserId` nên id của người
khác trả `404`, không phải `403` — không tiết lộ là id đó có tồn tại.

**`POST /api/cvs/{cvId}/parse`** — thử lại, **chỉ cho phép khi `status = FAILED`**.
`PARSING` → `409 CV_PARSE_IN_PROGRESS`. `PARSED` → `409 CV_PARSE_NOT_RETRYABLE` (đã có kết
quả, parse lại là đốt tiền API vô ích). Job nền tải lại file từ MinIO chứ không cần upload lại.

Không có "parse lại một CV đã `PARSED`" — nếu sau này prompt của mình tốt hơn và muốn dựng lại
hồ sơ, đó là endpoint khác và **là chỗ duy nhất `deleteAllByProfileId` sống lại**. Chưa làm.

**`DELETE /api/cvs/{cvId}`** — 204, không body.

## 5. Bóc tách bằng Gemini

### 5.1. Gửi thẳng file PDF, không rút text trước

Bản 1 định dùng PDFBox `PDFTextStripper` rút text rồi mới đưa cho AI. Bản 2 **gửi luôn file
PDF** cho Gemini, vì:

- CV thật hầu hết chia 2–3 cột. `PDFTextStripper` đọc theo dòng vật lý nên trộn lẫn cột trái
  với cột phải, ra một mớ chữ mà chính con người cũng không đọc nổi — rồi bắt AI bóc tách từ
  mớ đó. Gemini nhìn được layout.
- CV scan (ảnh) tự nhiên chạy được luôn nhờ OCR sẵn trong model, thay vì phải trả lời
  "file này là ảnh scan, chưa đọc được chữ" như bản 1.
- Bớt một chỗ có thể sai: không còn ngưỡng `min-text-length: 200` đoán mò.

PDFBox **vẫn giữ lại**, nhưng chỉ để validate ở bước 1 (mở được không, có mật khẩu không, bao
nhiêu trang) — bắt lỗi tại máy mình, không tốn một lượt gọi API để biết file hỏng. Giới hạn
inline của Gemini là 50MB cho PDF, mình chặn ở 5MB nên không chạm tới.

### 5.2. Request

```java
Media pdf = Media.builder()
        .mimeType(MediaType.APPLICATION_PDF)
        .data(pdfContent)
        .name("candidate-cv")
        .build();

GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
        .model(aiProperties.model())
        .outputSchema(responseSchema)
        .build();

ChatResponse response = chatModel.call(new Prompt(
        UserMessage.builder().text(prompt).media(pdf).build(), options));
```

`GoogleGenAiChatOptions.outputSchema(...)` gửi schema bằng structured output native của Gemini,
không chỉ ghép hướng dẫn định dạng vào prompt. Spring AI và Google GenAI SDK tự mã hóa `byte[]`
của PDF sang inline data và dựng request `generateContent`; code ứng dụng không còn tự tạo JSON,
header API key hay parse response của provider.

File schema trên đĩa vẫn là JSON Schema chuẩn. Trước khi đưa cho Spring AI 2.0, nullable dạng
`type: ["string", "null"]` được chuyển sang OpenAPI `type: "string", nullable: true`, vì kiểu
`Schema` của Google SDK dùng biểu diễn này. `additionalProperties` chưa có trường tương ứng trong
kiểu SDK nên được bỏ ở bản gửi provider; ứng dụng vẫn parse phòng thủ vào DTO và bỏ trường lạ.

### 5.3. Response và cách đọc

Spring AI chuẩn hóa response của provider thành `ChatResponse`:

| Cần gì | Lấy ở đâu | Lưu vào |
|---|---|---|
| JSON hồ sơ | `response.getResult().getOutput().getText()` | `cv_parse_results.raw_json` |
| Model thật đã chạy | `response.getMetadata().getModel()` | `cv_parse_results.model_name` |
| Số token | `response.getMetadata().getUsage().getTotalTokens()` | `cv_parse_results.token_cost` |
| Thời gian | tự đo bằng `System.nanoTime()` quanh lời gọi | `cv_parse_results.duration_ms` |

Text vẫn được parse lại bằng `JsonMapper` thành `CvParsedPayload`; response rỗng, JSON sai hoặc
không khớp DTO đều thành `CvParseFailedException.badResponse`. Lỗi 429/5xx, lỗi mạng và timeout
được phân loại riêng. Bean model để lazy nên thiếu API key không làm ứng dụng hoặc test khác
không liên quan tới CV chết lúc khởi động.

### 5.4. Schema JSON cố định — `v1`

Lưu nguyên văn vào `cv_parse_results.raw_json` với `schema_version = "v1"`. Đây là hợp đồng:
đổi cấu trúc thì tăng lên `v2`, không sửa tại chỗ. Cùng một object này vừa làm
structured-output schema gửi cho Gemini, vừa làm DTO parse về.

```json
{
  "headline": "Java Backend Fresher",
  "yearsExperience": 0.5,
  "targetPosition": "Backend Developer",
  "seniorityLevel": "FRESHER",
  "educations": [
    { "school": "Đại học Bách khoa Hà Nội", "degree": "Cử nhân",
      "fieldOfStudy": "Khoa học máy tính", "startYear": 2022, "endYear": 2026 }
  ],
  "skills": [
    { "name": "Java", "category": "LANGUAGE" },
    { "name": "Spring Boot", "category": "FRAMEWORK" }
  ],
  "projects": [
    { "name": "AI Mock Interview",
      "description": "Nền tảng luyện phỏng vấn bằng AI...",
      "roleInProject": "Backend Developer",
      "techStack": ["Java", "Spring Boot", "MySQL"],
      "startDate": "2026-06-01", "endDate": "2026-08-01" }
  ]
}
```

Mọi trường trừ `school`, `skills[].name`, `projects[].name` đều cho `null` — CV thật thiếu
thông tin là chuyện bình thường, ép AI bịa ra tệ hơn nhiều, nhất là khi cái nó bịa sẽ thành
câu hỏi phỏng vấn.

### 5.5. Map JSON sang cột

| JSON | Cột | Ghi chú khi map |
|---|---|---|
| `seniorityLevel` | `candidate_profiles.seniority_level` | `VARCHAR` trần, không validate theo enum. Giá trị lạ vẫn nhận |
| `skills[].category` | `profile_skills.category` | như trên |
| `skills[].name` | `profile_skills.name` | trim + gộp khoảng trắng; trùng không phân biệt hoa thường thì bỏ bản sau |
| `projects[].techStack` | `profile_projects.tech_stack` | mảng → nối bằng `,` (cột là `TEXT`) |
| thứ tự phần tử trong mảng | `display_order` | 0, 1, 2... theo đúng thứ tự Gemini trả về |
| — | `is_user_edited` | `false` cho mọi hàng do AI sinh |
| — | `candidate_profiles.source` | `AUTO_PARSED` |
| — | `candidate_profiles.confirmed_at` | `NULL` |

`yearsExperience` là `DECIMAL(3,1)` → tối đa **99.9**, một chữ số thập phân. Giá trị vượt thì
kẹp lại chứ đừng để `DataIntegrityViolationException` giết cả lần parse.

Số phần tử cũng phải kẹp theo mục 9 trước khi ghi: Gemini trả 300 kỹ năng thì cắt còn 100, chứ
đừng để hồ sơ phình ra rồi `PUT` mãi không qua validate.

## 6. Sửa hồ sơ và xác nhận

### 6.1. `GET /api/profiles` và `GET /api/profiles/{profileId}`

`GET /api/profiles` trả **bản rút gọn**, không kèm 3 danh sách con — người dùng có 10 CV, mỗi
hồ sơ 100 kỹ năng thì response thành vài trăm KB cho một cái danh sách chỉ để chọn:

```json
[
  { "id": 7, "cvDocumentId": 12, "cvOriginalFilename": "CV_NguyenVanA_Backend.pdf",
    "headline": "Java Backend Fresher", "targetPosition": "Backend Developer",
    "seniorityLevel": "FRESHER", "source": "USER_EDITED",
    "confirmedAt": "2026-08-24T04:10:00Z",
    "educationCount": 1, "skillCount": 12, "projectCount": 3,
    "createdAt": "2026-08-24T03:00:18Z", "updatedAt": "2026-08-24T04:09:40Z" }
]
```

Chỉ trả hồ sơ của CV chưa xóa (mục 4.5). Danh sách rỗng là `200 []`, **không phải 404** — chưa
upload CV nào là trạng thái bình thường của người mới, không phải lỗi.

`GET /api/profiles/{profileId}` trả đầy đủ, cả 3 danh sách con trong một lần gọi vì form sửa
cần toàn bộ; chia 4 request chỉ tạo trạng thái nửa vời trên UI:

```json
{
  "id": 7,
  "cvDocumentId": 12,
  "cvOriginalFilename": "CV_NguyenVanA_Backend.pdf",
  "headline": "Java Backend Fresher",
  "yearsExperience": 0.5,
  "targetPosition": "Backend Developer",
  "seniorityLevel": "FRESHER",
  "source": "AUTO_PARSED",
  "confirmedAt": null,
  "createdAt": "2026-08-24T03:00:18Z",
  "updatedAt": "2026-08-24T03:00:18Z",
  "educations": [
    { "id": 31, "school": "Đại học Bách khoa Hà Nội", "degree": "Cử nhân",
      "fieldOfStudy": "Khoa học máy tính", "startYear": 2022, "endYear": 2026,
      "userEdited": false, "displayOrder": 0 }
  ],
  "skills": [
    { "id": 55, "name": "Java", "category": "LANGUAGE", "userEdited": false, "displayOrder": 0 }
  ],
  "projects": [
    { "id": 18, "name": "AI Mock Interview", "description": "...",
      "roleInProject": "Backend Developer", "techStack": "Java,Spring Boot,MySQL",
      "startDate": "2026-06-01", "endDate": "2026-08-01",
      "userEdited": false, "displayOrder": 0 }
  ]
}
```

`404 PROFILE_NOT_FOUND` khi `profileId` không thuộc người gọi, hoặc thuộc một CV đã xóa mềm.

`raw_json` không xuất hiện ở bất kỳ endpoint người dùng nào — đó chính là cách thỏa mãn *"bản
đã sửa luôn được ưu tiên hơn bản tự động"*: không tồn tại đường nào ghi `raw_json` đè lên hồ
sơ, nên không cần cột "ưu tiên bản nào". Nếu sau này muốn nút "quay lại bản AI" thì phải bàn
lại, vì nó lật ngược đúng tiêu chí này.

`techStack` trả **nguyên chuỗi** như trong DB, không tách mảng. Tách ra rồi lúc `PUT` lại phải
nối vào, hai bên dễ lệch; để frontend `split(",")` khi cần hiển thị chip.

### 6.2. `PUT /api/profiles/{profileId}` — một request cho cả form

Body giống response 6.1, bỏ các trường server tự quản (`id` hồ sơ, `cvDocumentId`, `source`,
`confirmedAt`, `createdAt`, `updatedAt`, `userEdited`, `displayOrder`).

```json
{
  "headline": "Java Backend Fresher",
  "yearsExperience": 0.5,
  "targetPosition": "Backend Developer",
  "seniorityLevel": "FRESHER",
  "educations": [ { "id": 31, "school": "...", "startYear": 2022, "endYear": 2026 } ],
  "skills":     [ { "id": 55, "name": "Java", "category": "LANGUAGE" },
                  { "name": "Docker", "category": "TOOL" } ],
  "projects":   [ { "id": 18, "name": "AI Mock Interview", "description": "..." } ]
}
```

Quy tắc xử lý từng bảng con:

| Phần tử trong payload | Hành động |
|---|---|
| có `id`, và `id` thuộc hồ sơ này | `UPDATE` hàng đó |
| có `id`, nhưng không thuộc hồ sơ này | `404 PROFILE_ITEM_NOT_FOUND` — chặn sửa hàng của người khác |
| không có `id` | `INSERT`, `is_user_edited = true` |
| hàng trong DB mà `id` không xuất hiện trong payload | `DELETE` |

`display_order` = vị trí trong mảng, nên kéo thả sắp xếp lại cũng chỉ là một `PUT`.
`is_user_edited` bật `true` khi hàng có thay đổi thật; hàng gửi lên y nguyên thì giữ cờ cũ.

**Vì sao không xóa sạch rồi chèn lại**

Cách đó ngắn hơn nhiều và `deleteAllByProfileId` đã viết sẵn cho cả 3 bảng con. Nhưng:

```
profile_projects (1) ──< (N) session_questions.source_project_id   [set null]
profile_skills   (1) ──< (N) session_questions.source_skill_id     [set null]
```

Xóa hàng cũ là `SET NULL` toàn bộ câu hỏi của mọi phiên đã phỏng vấn — mất sạch liên kết *"câu
hỏi này sinh ra từ dự án nào"*, tức là mất chính thứ mà `schema-guide` gọi là điểm khác biệt
của sản phẩm. Chỉ vì người dùng sửa một chữ trong `headline`.

Ở bản 2, luồng parse lại CV (chỗ duy nhất việc mất liên kết là đúng ý) **không còn tồn tại** —
CV mới sinh hồ sơ mới. Nên `deleteAllByProfileId` ở cả 3 repository thành **không ai gọi**. Tôi
sẽ nêu lại lúc làm chunk service để anh chọn bỏ hay giữ chờ tính năng "dựng lại hồ sơ từ CV cũ".

`PUT` cũng đặt `source = USER_EDITED` (cột này chỉ để thống kê tỉ lệ AI bóc tách sai).

**`confirmed_at` sau khi sửa**: đề xuất **giữ nguyên**, không reset. Người đang sửa chính là
người phải kiểm tra, thao tác sửa tự nó đã là hành động kiểm tra; bắt bấm "Thông tin chính xác"
lại sau mỗi lần đổi một chữ chỉ gây khó chịu và người ta sẽ bấm cho xong. Ở bản 2 cũng không
còn luồng nào tự đóng cổng lại nữa, vì CV mới không đụng vào hồ sơ cũ.

### 6.3. `POST /api/profiles/{profileId}/confirm`

Không body. Đặt `confirmed_at = now` nếu đang `NULL`, idempotent — bấm hai lần không đổi mốc
thời gian đã ghi. Trả về hồ sơ đầy đủ như 6.1.

Xác nhận là **theo từng hồ sơ**. Người dùng có 3 CV thì phải xác nhận riêng từng hồ sơ, và chỉ
những hồ sơ đã xác nhận mới chọn được lúc tạo phiên. Đây là hệ quả trực tiếp của bản 2 và là
chỗ khác bản 1 nhiều nhất về mặt trải nghiệm.

## 7. Chọn CV / hồ sơ lúc tạo phiên phỏng vấn

Endpoint tạo phiên thuộc nhóm 4, **không viết ở đây**. Nhưng bản 2 làm nó thay đổi, nên chốt
sẵn hợp đồng để nhóm 4 không phải đoán:

```json
POST /api/sessions
{ "profileId": 7, "...": "các tham số khác của phiên" }
```

Ba bước kiểm, đúng thứ tự này:

| Bước | Kiểm | Không đạt |
|---|---|---|
| 1 | `findByIdAndUserIdAndCvDocumentActiveTrue(profileId, userId)` có hàng | `404 PROFILE_NOT_FOUND` |
| 2 | (đã gộp vào bước 1 — CV xóa mềm thì query trả rỗng) | `404 PROFILE_NOT_FOUND` |
| 3 | `profile.isConfirmed()` | `409 PROFILE_NOT_CONFIRMED` |

Frontend lấy danh sách để chọn từ `GET /api/cvs` (có sẵn `profileId` + `profileConfirmed`) hoặc
`GET /api/profiles`, rồi gửi `profileId` — **không gửi `cvDocumentId`**, vì FK trong DB là
`interview_sessions.profile_id`, nhận `cvDocumentId` chỉ thêm một lần tra cứu và thêm một mã lỗi.

Một truy vấn cho cả ba bước, mà vẫn phân biệt được 404 với 409. Đây là lý do repository trả
`Optional<CandidateProfile>` chứ không phải `boolean exists...` như bản 1 — xem mục 2.3.

## 8. Mã lỗi cần thêm vào `ErrorCode`

| Mã | HTTP | Khi nào |
|---|---|---|
| `CV_FILE_REQUIRED` | 400 | không có field `file`, hoặc file rỗng 0 byte |
| `CV_INVALID_FILE_TYPE` | 415 | không phải `.pdf`, hoặc 4 byte đầu không phải `%PDF` |
| `CV_FILE_TOO_LARGE` | 413 | > 5MB |
| `CV_FILE_CORRUPTED` | 400 | PDFBox không mở được, hoặc PDF có mật khẩu |
| `CV_TOO_MANY_PAGES` | 400 | > 10 trang, gần như chắc chắn không phải CV |
| `CV_LIMIT_REACHED` | 409 | đã có 10 CV chưa xóa (**mới ở bản 2**) |
| `CV_NOT_FOUND` | 404 | `{cvId}` không thuộc người gọi, hoặc đã xóa mềm |
| `CV_PARSE_IN_PROGRESS` | 409 | `POST .../parse` hoặc `DELETE` khi đang `PARSING` |
| `CV_PARSE_NOT_RETRYABLE` | 409 | thử lại khi `status` không phải `FAILED` |
| `PROFILE_NOT_FOUND` | 404 | `{profileId}` không thuộc người gọi, hoặc CV của nó đã xóa |
| `PROFILE_ITEM_NOT_FOUND` | 404 | `id` con trong payload `PUT` không thuộc hồ sơ này |
| `DUPLICATE_SKILL_NAME` | 409 | một payload `PUT` chứa `"Java"` và `"java"` |
| `PROFILE_NOT_CONFIRMED` | 409 | (nhóm 4 dùng) chưa bấm xác nhận hồ sơ được chọn |
| `STORAGE_UNAVAILABLE` | 503 | không kết nối được MinIO |

Ba mã `CV_FILE_CORRUPTED`, `CV_TOO_MANY_PAGES`, `CV_LIMIT_REACHED` là mới so với bản 1: hai cái
đầu vì giờ PDFBox validate ngay ở request (mục 5.1), cái thứ ba vì mất cơ chế ghi đè (mục 4.4).

Kèm `MaxUploadSizeExceededException` → thêm handler trong `GlobalExceptionHandler` map sang
`413 CV_FILE_TOO_LARGE`. Không có handler này thì Spring trả `500` trắng, vi phạm *"báo lỗi rõ
khi file hỏng"*.

Lỗi bóc tách **không trả qua HTTP** vì nó xảy ra sau khi request đã kết thúc — nó nằm ở
`status = FAILED` + `status_message`, đọc bằng endpoint poll. `status_message` viết cho người
dùng đọc, không đổ stacktrace hay message thô của Gemini vào đó.

## 9. Validate — bám sát DDL

Mọi giới hạn dưới đây chặn ở `@Valid` trước khi tới database, để người dùng nhận
`400 VALIDATION_FAILED` kèm `fieldErrors` chứ không phải `409` từ constraint.

| Trường | Ràng buộc |
|---|---|
| `headline` | ≤ 255 |
| `yearsExperience` | 0 – 99.9, `@Digits(integer = 2, fraction = 1)` |
| `targetPosition`, `degree`, `fieldOfStudy`, `roleInProject` | ≤ 150 |
| `seniorityLevel` | ≤ 30 |
| `educations[].school`, `projects[].name` | bắt buộc, không rỗng sau trim, ≤ 255 |
| `skills[].name` | bắt buộc, ≤ 80 |
| `skills[].category` | ≤ 50 |
| `startYear`, `endYear` | 1900 – 2100, `endYear >= startYear` |
| `startDate`, `endDate` | `endDate >= startDate` |
| `projects[].description` | ≤ 5000 — **giới hạn tôi tự chọn**, xem dưới |
| `projects[].techStack` | ≤ 500 — **giới hạn tôi tự chọn**, xem dưới |
| số phần tử mỗi mảng | educations ≤ 20, skills ≤ 100, projects ≤ 50 |

Ba ràng buộc năm/ngày và `TRIM()` đã có `CHECK` trong DDL — validate ở đây để lỗi đọc được,
không phải để thay thế.

**Hai giới hạn không có trong DDL.** `description` và `tech_stack` là cột `TEXT`, mà `TEXT` chứa
65535 **byte** chứ không phải ký tự — tiếng Việt tốn 3 byte một chữ, nên trần thật chỉ khoảng
21800 ký tự và MySQL báo bằng một `DataIntegrityViolationException` → `409` không nói được tên
trường nào sai. Chặn ở `@Valid` để lỗi đó thành `400 VALIDATION_FAILED`. Con số 5000 và 500 chọn
theo độ dài thực tế của CV, không phải theo giới hạn kỹ thuật.

Phía dữ liệu Gemini, `ProfileMapper` kẹp cùng hai con số đó, và kẹp thêm **tối đa 20 phần tử**
`techStack` cho một dự án (`MAX_TECH_ITEMS`) — mảng công nghệ AI trả về thỉnh thoảng lặp và dài
bất thường, giữ 20 cái đầu sau khi `distinct()` là đủ để hiển thị chip.

Cùng bộ giới hạn này áp cho cả dữ liệu Gemini trả về (mục 5.5), nhưng ở đó **kẹp lại** thay vì
báo lỗi: người dùng không làm gì sai, không có lý gì bắt họ nhận lỗi vì AI trả quá dài.

## 10. Bảo mật

- **IDOR**: mọi truy vấn theo id đều đi qua `findByIdAndUserId`. Không có chỗ nào `findById(id)`
  rồi mới so chủ sở hữu — quên một lần là lộ CV người khác. Bản 2 có nhiều id hơn hẳn bản 1
  (`cvId`, `profileId`, id của từng hàng con) nên rủi ro này tăng, phải soi kỹ ở review.
- **`storage_key` do server sinh**, `cv/{userId}/{uuid}.pdf`. Không lấy tên file người dùng gửi
  lên làm key: `../` sẽ ghi ra ngoài prefix, và tên trùng sẽ đè file người khác.
  `original_filename` chỉ để hiển thị lại, và phải escape khi render.
- **Không tin `Content-Type` của client** — nó do client tự khai. Kiểm 4 byte đầu `%PDF`.
- **Bucket private tuyệt đối**, không bật public read. Truy cập chỉ qua URL presigned 5 phút.
  URL presigned vẫn là bí mật mang được đi (ai có link, trong 5 phút đó, đọc được file) — nếu
  anh thấy vậy quá lỏng thì phương án khác là stream qua backend (`GET /api/cvs/{cvId}/file`
  trả thẳng bytes, `Content-Disposition: inline`): kín hơn, đổi lại băng thông đi qua server.
- **Giới hạn multipart đặt ở tầng container**: `max-file-size: 5MB`, `max-request-size: 6MB`.
  Nhờ vậy body 500MB bị chặn trước khi vào code, không buffer hết vào RAM.
- **`GEMINI_API_KEY` không bao giờ vào repo**, đọc từ biến môi trường và mặc định rỗng. Bean
  Spring AI để lazy; thiếu key thì riêng lần parse CV chuyển sang `FAILED`, app vẫn khởi động.
- **Không gửi gì ngoài file CV cho Gemini**: không kèm email, không kèm `userId`. File CV đã đủ
  nhạy cảm, và đây là dữ liệu cá nhân của người thật.
- `/api/cvs/**` và `/api/profiles/**` **không cần sửa `SecurityConfig`**: whitelist hiện tại chỉ
  mở `/api/auth/**` và `GET /api/events*`, còn lại đã `anyRequest().authenticated()`.
- **Chưa có rate limit** cho upload. Trần 10 CV/người chặn được spam vô hạn nhưng không chặn
  được xóa-rồi-upload liên tục để đốt quota Gemini. Ghi lại như việc cần làm.

## 11. Hạ tầng, thư viện và cấu hình

### 11.1. MinIO bằng docker compose

`docker-compose.yml` ở gốc project, chỉ một service `minio`. MySQL không đưa vào đây vì máy dev
đang chạy MySQL cài trực tiếp — hai bản cùng nghe cổng 3306 thì rắc rối hơn là tiện:

```yaml
services:
  minio:
    image: minio/minio:RELEASE.2025-09-07T16-13-09Z
    container_name: interview-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${STORAGE_ACCESS_KEY:-minioadmin}
      MINIO_ROOT_PASSWORD: ${STORAGE_SECRET_KEY:-minioadmin}
    ports:
      - "9000:9000"   # S3 API
      - "9001:9001"   # web console
    volumes:
      - minio-data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  minio-data:
```

Tag ghim `RELEASE.2025-09-07T16-13-09Z` — bản community mới nhất còn được đẩy lên Docker Hub
(sau đó MinIO chuyển sang AIStor, không có tag 2026 nào). Không dùng `latest`.

**Không có service `minio/mc` để tạo bucket.** Thay vào đó ứng dụng tự tạo bucket lúc khởi động
nếu chưa có (chunk hạ tầng service). Hai lý do: bớt một container và một tag phải ghim; và bucket
mới tạo bằng API mặc định đã là private, nên `mc anonymous set none` chỉ là xác nhận lại điều vốn
đã đúng. Khi deploy thật mà key của app không có quyền `CreateBucket` thì bước này chỉ log cảnh
báo rồi đi tiếp, vì bucket lúc đó đã do hạ tầng dựng sẵn.

### 11.2. Nói chuyện với MinIO bằng AWS SDK v2

Không dùng `io.minio:minio` mà dùng `software.amazon.awssdk:s3`, vì MinIO nói giao thức S3: cùng
một đoạn code chạy được cả MinIO local lẫn S3 thật khi deploy, chỉ đổi cấu hình. Ba thứ bắt buộc
khi trỏ SDK sang MinIO:

```java
S3Client.builder()
        .endpointOverride(URI.create(props.endpoint()))          // http://localhost:9000
        .region(Region.of(props.region()))                       // MinIO không quan tâm, SDK thì bắt buộc có
        .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
        .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)                    // MinIO không có virtual-host bucket
                .build())
        .build();
```

**Cái bẫy của URL presigned với MinIO**: chữ ký được tính trên cả hostname. Nếu app chạy trong
docker và gọi MinIO qua `http://minio:9000`, thì URL ký ra cũng mang host `minio` — trình duyệt
của người dùng không phân giải được tên đó. Nên có hai cấu hình:

| Cấu hình | Dùng cho | Dev (app chạy ngoài docker) |
|---|---|---|
| `app.storage.endpoint` | `S3Client` upload/download | `http://localhost:9000` |
| `app.storage.public-endpoint` | `S3Presigner` ký URL cho trình duyệt | `http://localhost:9000` |

Lúc dev hai giá trị giống nhau nên không thấy vấn đề; nó chỉ nổ khi đưa app vào docker compose.
Tách sẵn từ đầu rẻ hơn là đi tìm nguyên nhân sau.

### 11.3. Thư viện thêm vào `pom.xml`

| Việc | Thêm gì | Ghi chú |
|---|---|---|
| Validate PDF | `org.apache.pdfbox:pdfbox` `3.0.7` | chỉ mở file, kiểm mã hóa, đếm trang — không rút text |
| MinIO / S3 | `software.amazon.awssdk:s3` qua BOM `2.46.7` | kèm `S3Presigner` nằm trong cùng artifact |
| Gọi Gemini | `org.springframework.ai:spring-ai-google-genai` qua BOM `2.0.1` | `GoogleGenAiChatModel`, PDF media và native structured output |

Các version được ghim, không để range. Spring Boot không quản version cho AWS SDK, PDFBox hay
Spring AI nên mỗi hệ thư viện có một version/BOM rõ ràng trong `pom.xml`.

Artifact `s3` bị loại `netty-nio-client`: code chỉ dùng `S3Client` đồng bộ, client sync đi kèm là
`apache5-client`, nên Netty vào chỉ để nằm đó. Đã kiểm bằng `dependency:tree`.

### 11.4. `application.yaml`

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 6MB
      max-request-size: 8MB

app:
  cv:
    max-file-size-bytes: ${CV_MAX_FILE_SIZE_BYTES:5242880}
    max-pages: ${CV_MAX_PAGES:10}
    max-per-user: ${CV_MAX_PER_USER:10}
  storage:
    endpoint: ${STORAGE_ENDPOINT:http://localhost:9000}
    public-endpoint: ${STORAGE_PUBLIC_ENDPOINT:http://localhost:9000}
    bucket: ${STORAGE_BUCKET:interview-cv}
    region: ${STORAGE_REGION:us-east-1}
    access-key: ${STORAGE_ACCESS_KEY:minioadmin}
    secret-key: ${STORAGE_SECRET_KEY:minioadmin}
    presign-ttl-seconds: ${STORAGE_PRESIGN_TTL_SECONDS:300}
  ai:
    api-key: ${GEMINI_API_KEY:}
    model: ${GEMINI_MODEL:gemini-3.5-flash}
    schema-version: ${GEMINI_SCHEMA_VERSION:v1}
    timeout-ms: ${GEMINI_TIMEOUT_MS:25000}
```

Ba chỗ lệch so với bản nháp ở trên, có lý do:

**`multipart.max-file-size` là 6MB chứ không phải 5MB.** Đặt bằng đúng hạn mức của app thì file
5.1MB bị servlet chặn trước, người dùng nhận `MaxUploadSizeExceededException` thô. Để servlet nới
hơn một chút thì chính app trả `CV_FILE_TOO_LARGE` kèm câu tiếng Việt. Handler cho
`MaxUploadSizeExceededException` vẫn giữ, nhưng chỉ còn là lưới an toàn cho file cực lớn.

**`access-key` / `secret-key` mặc định `minioadmin`, không để rỗng.** Cặp này đã nằm trong
`docker-compose.yml` của repo rồi, nên bắt app chết lúc khởi động chỉ tạo thêm một bước thủ công
mà không giữ được bí mật nào cả. Deploy thật thì set biến môi trường như bình thường.

**`api-key` để rỗng nhưng app vẫn khởi động được.** Nếu bắt fail lúc khởi động thì không ai chạy
được test hay làm tính năng khác mà không có key Gemini. Thay vào đó thiếu key thì lần bóc tách
đầu tiên trả `FAILED` kèm lý do — vẫn đúng yêu cầu "báo lỗi rõ khi không xử lý được", và
`AiProperties.hasApiKey()` là chỗ duy nhất kiểm tra việc này.

**`parse-timeout-ms` bỏ khỏi `app.cv`, đổi thành `app.ai.timeout-ms`.** Hai chỗ cùng một con số
thì sớm muộn lệch nhau. Thứ thật sự cắt thời gian là timeout của lần gọi HTTP sang Gemini, nên nó
thuộc `app.ai`. 25 giây, thấp hơn mốc 30 giây của tiêu chí nghiệm thu, để còn chỗ tải file từ
MinIO và ghi DB.

Ngoài ra `AI_*` đổi tên biến môi trường thành `GEMINI_*` cho khớp với `GEMINI_API_KEY` — đọc
`docker compose`/CI config sẽ thấy cả bốn biến cùng một tiền tố.

Validate lúc khởi động không chỉ là `@NotBlank`: tên bucket phải khớp `[a-z0-9][a-z0-9-]{1,61}[a-z0-9]`
(sai thì cả path-style URL lẫn link presigned đều vỡ), `presign-ttl-seconds` không quá 604800 giây
(trần cứng của SigV4), `model` ≤ 100 và `schema-version` ≤ 20 ký tự cho vừa cột trong
`cv_parse_results`. Sai thì app không lên, kèm số dòng trong `application.yaml`.

Thêm `@EnableAsync` + một `ThreadPoolTaskExecutor` hàng đợi giới hạn cho việc parse. Điểm yếu đã
biết: app restart giữa lúc đang `PARSING` thì hàng đó treo mãi. Cách xử lý: một job chạy ở
`ApplicationReadyEvent`, quét toàn bộ `status = PARSING` và chuyển thành `FAILED` để người dùng
bấm thử lại được — không dùng ngưỡng thời gian, vì `POST /api/cvs/{id}/parse` đặt `PARSING` trên
một CV có `uploaded_at` cũ, ngưỡng 5 phút sẽ bắn chết một lần parse đang chạy hợp lệ. Lúc vừa
khởi động thì không thể có lần parse nào đang chạy, nên quét sạch là đúng. Đánh đổi: giả định chỉ
có một instance — instance thứ hai boot lên sẽ giết parse của instance đầu.

## 12. Chia chunk để review

Mỗi chunk dừng lại để anh đọc trước khi làm tiếp.

**Chunk 0 — sửa tầng DB sang nhiều CV.** Phải xong trước mọi thứ khác, vì cả 10 endpoint đều
dựa vào nó. Verify bằng `ddl-auto: validate` trên schema nháp rồi drop.
```
db/changelog/021-create-candidate-profiles.sql   ← mục 2.1
db/changelog/019-create-cv-documents.sql         ← chỉ sửa comment dòng 16
entity/CandidateProfile.java                     ← mục 2.3
repository/CvDocumentRepository.java  repository/CandidateProfileRepository.java
```

**Chunk 1 — hạ tầng ngoài code.** Chạy được MinIO và gọi thử Gemini bằng `curl` ở đây, trước khi
viết client, để xác nhận hai chỗ chưa chắc ở mục 5.3.
```
pom.xml   application.yaml   docker-compose.yml
config/properties/CvProperties.java  StorageProperties.java  AiProperties.java
```

**Chunk 2 — DTO và lỗi.** Không có logic, đọc nhanh.
```
dto/cv/CvDocumentResponse.java          dto/cv/CvFileUrlResponse.java
dto/profile/ProfileSummaryResponse.java dto/profile/CandidateProfileResponse.java
dto/profile/ProfileUpdateRequest.java   dto/profile/ProfileEducationDto.java
dto/profile/ProfileSkillDto.java        dto/profile/ProfileProjectDto.java
dto/ai/CvParsedPayload.java             ← schema v1 mục 5.4, dùng cho cả request và response
exception/ + các lớp theo bảng mục 8, thêm mã vào ErrorCode và Message
exception/GlobalExceptionHandler.java   ← thêm handler MaxUploadSizeExceededException
```

**Chunk 3 — hạ tầng service.** Ba thứ độc lập nhau, test riêng được.
```
config/AsyncConfig.java   config/S3Config.java
storage/FileStorageService.java  + storage/minio/MinioFileStorageService.java
cv/validation/CvFileValidator.java + cv/validation/PdfBoxCvFileValidator.java
cv/parsing/CvParserClient.java   + cv/parsing/gemini/GeminiCvParserClient.java
```

**Chunk 4 — service.** Chỗ chứa toàn bộ quyết định ở mục 4, 6.
```
service/CvDocumentService.java        + impl/CvDocumentServiceImpl.java
service/CvParsingService.java         + impl/CvParsingServiceImpl.java   (@Async)
service/CandidateProfileService.java  + impl/CandidateProfileServiceImpl.java
mapper/ProfileMapper.java
```

**Chunk 5 — controller.**
```
controller/CvController.java   controller/CandidateProfileController.java
```

**Chunk 6 — build và verify.** Compile, khởi động trên schema nháp, đối chiếu 10 endpoint trong
OpenAPI, drop schema nháp. Test tự động (repository round-trip, `PUT` diff, các nhánh lỗi upload)
là chunk 7 nếu anh muốn.

## 13. Quyết định tôi tự chọn khi làm

Anh đã bảo tiến hành nên tôi không chờ nữa, nhưng liệt kê ra đây để anh phủ quyết được ở đúng
chunk tương ứng — mỗi dòng đều là sửa một chỗ, không phải làm lại:

| # | Chỗ | Tôi chọn | Phương án khác |
|---|---|---|---|
| 1 | Sửa `021` hay viết `025` | Sửa `021` tại chỗ (2.1) | `025` nếu có người đã chạy `019`–`024` |
| 2 | Tên cột `is_active` | Giữ tên, đổi nghĩa thành xóa mềm (2.2) | Đổi thành `is_deleted` và đảo logic — giờ là lúc rẻ nhất |
| 3 | Trần số CV | 10 / người | Số khác, một dòng cấu hình |
| 4 | Đọc PDF | Gửi thẳng file cho Gemini, PDFBox chỉ validate (5.1) | PDFBox rút text như bản 1: rẻ hơn nhưng CV nhiều cột thì bóc tách kém |
| 5 | Model | `gemini-3.5-flash` | model khác qua `GEMINI_MODEL` nếu cần đổi cân bằng chất lượng/chi phí |
| 6 | Xem lại file CV | URL presigned 5 phút | Stream qua backend: kín hơn, tốn băng thông server |
| 7 | Sửa hồ sơ | Một `PUT` cho cả form, diff theo `id` (6.2) | REST chi tiết ~10 endpoint nếu UI sửa từng dòng |
| 8 | `confirmed_at` sau khi sửa | Giữ nguyên (6.2) | Reset về `NULL`, bắt xác nhận lại mỗi lần sửa |
| 9 | Bỏ qua parse khi trùng checksum | Có làm (4.3) | Bỏ cho MVP gọn, chấp nhận trả tiền API lần hai |
| 10 | `deleteAllByProfileId` ×3 | Giữ, chưa ai gọi | Xóa cho sạch, thêm lại khi cần |
| 11 | `GET /api/cvs/{cvId}/profile` | Không thêm, vì `GET /api/cvs` đã trả `profileId` | Thêm nếu frontend thấy tiện hơn |
| 12 | Object trên MinIO khi xóa CV | Không xóa (4.5) | Xóa luôn, đổi lại file của phiên cũ không xem lại được |

## 14. Ngoài phạm vi

- **JD** — không có bảng nào trong schema, chưa thiết kế được.
- **Endpoint tạo phiên** — thuộc nhóm 4. Ở đây chỉ chốt hợp đồng ở mục 7 và đảm bảo
  `confirmed_at` được ghi đúng.
- **Xem `raw_json`** — hữu ích để debug "AI hỏi về dự án em chưa làm", nhưng là màn hình admin.
- **Dựng lại hồ sơ từ một CV đã `PARSED`** — bản 2 không cần vì CV mới sinh hồ sơ mới. Nếu sau
  này prompt tốt hơn và muốn parse lại, đó là endpoint riêng và là chỗ duy nhất
  `deleteAllByProfileId` có ích.
- **Rate limit upload**, **dọn object rác trên MinIO**, **xóa cứng CV** — việc sau.

