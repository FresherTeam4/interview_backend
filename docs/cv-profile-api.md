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
| AI provider | để mở | **Gemini** `gemini-3.6-flash` (mục 5) |
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

`019`–`024` **chưa commit và chưa apply vào `interview_db`** (schema đó vẫn đang ở `018`).
Vì vậy sửa thẳng vào `021` là sạch nhất: không để lại một migration "thêm rồi lại bỏ" trong
lịch sử. Cụ thể trong `021`:

| Dòng | Đang là | Sửa thành |
|---|---|---|
| 9 | comment `'One live profile per user'` | `'Owner; a user may have many profiles, one per CV'` |
| 21 | `CONSTRAINT uq_candidate_profiles_user UNIQUE (user_id)` | `CONSTRAINT uq_candidate_profiles_cv_document UNIQUE (cv_document_id)` |
| 37–38 | comment + `CREATE INDEX idx_candidate_profiles_cv_document_id` | đổi thành `CREATE INDEX idx_candidate_profiles_user_id ON candidate_profiles (user_id)` |

Ba việc đó phải đi cùng nhau: bỏ `uq_candidate_profiles_user` thì `fk_candidate_profiles_user`
**mất index đỡ lưng** (chính comment ở dòng 37 đang nói vậy), MySQL sẽ báo lỗi 1553 nếu không
có index khác cho `user_id`. Còn `idx_candidate_profiles_cv_document_id` thành thừa vì
UNIQUE mới đã tự là index.

Nếu trong lúc tôi làm mà có người trong team đã chạy app với `019`–`024` rồi, thì **không sửa
`021` nữa** (Liquibase sẽ báo checksum lệch) mà viết `025` theo đúng thứ tự này:

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
| `CvDocumentRepository:34-41` | `deactivateAllByUserId` | `int deactivateByIdAndUserId(Long id, Long userId)` — xóa mềm một CV |
| `CandidateProfileRepository:11` | `Optional<CandidateProfile> findByUserId` | `List<CandidateProfile> findByUserIdAndCvDocumentActiveTrueOrderByCreatedAtDesc` |
| `CandidateProfileRepository:13` | `existsByUserId` | bỏ, không còn ai gọi |
| `CandidateProfileRepository:19` | `existsByUserIdAndConfirmedAtIsNotNull` | `existsByIdAndUserIdAndConfirmedAtIsNotNull(Long id, Long userId)` |
| `CandidateProfileRepository` | — | thêm `Optional<CandidateProfile> findByIdAndUserId(Long id, Long userId)` |

Dòng thứ 8 là chỗ dễ bỏ sót nhất: `existsByUserIdAndConfirmedAtIsNotNull(userId)` giờ **sai
về mặt logic**, nó trả `true` khi người dùng có *bất kỳ* hồ sơ nào đã xác nhận, trong khi cổng
chặn cần biết **đúng hồ sơ được chọn** có xác nhận chưa.

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

<!-- APPEND-7 -->
