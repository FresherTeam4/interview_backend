# Hướng dẫn Database — AI Mock Interview Platform

> Pipeline JD và mẫu phỏng vấn hiện tại dùng [001-interview-templates.sql](migrations/001-interview-templates.sql). Phần tạo và chuẩn bị session dùng [002-interview-sessions.sql](migrations/002-interview-sessions.sql) cùng [interview-session-schema.dbml](interview-session-schema.dbml). Xem [API session đã triển khai](../docs/interview-session-api.md). Các mô tả interview/session rộng hơn bên dưới là thiết kế lịch sử, chưa phải schema của engine mới.

Tài liệu dành cho team dev. Mục tiêu: đọc xong hiểu được **vì sao** mỗi bảng tồn tại, chứ không chỉ biết nó có cột gì.

Schema đầy đủ nằm ở `schema.dbml` (dán vào dbdiagram.io để xem sơ đồ).

---

## Cách đọc tài liệu này

Mỗi nhóm bảng được trình bày theo cùng một khung:

| Mục | Trả lời câu hỏi |
|---|---|
| Nhóm này giải quyết việc gì | Vì sao nhóm tồn tại |
| Từng bảng | Bảng này giữ cái gì, cột nào đáng chú ý |
| Quan hệ | Nối vào đâu, ai phụ thuộc ai |
| Quyết định thiết kế | Vì sao làm thế này mà không làm cách khác |
| Bẫy thường gặp | Chỗ dễ làm sai khi code |

---

## Ba nguyên tắc xuyên suốt toàn schema

Hiểu ba điều này trước thì phần còn lại sẽ tự nhiên.

### 1. Dữ liệu bất biến và dữ liệu sống được tách ra

Có những thứ **không bao giờ được sửa** sau khi ghi: kết quả AI bóc tách CV, transcript gốc từ STT, nhật ký chuyển trạng thái, điểm đã chấm. Có những thứ **phải sửa được**: hồ sơ ứng viên, cài đặt người dùng.

Hai loại này luôn nằm ở hai bảng khác nhau, không trộn chung. Lý do: khi có sự cố ("AI hỏi sai", "sao em bị chấm thấp"), bạn cần một bản gốc để đối chiếu. Nếu người dùng sửa đè lên chính bản gốc thì không còn gì để điều tra.

### 2. Chụp ảnh thay vì trỏ khóa ngoại, khi dữ liệu là lịch sử

Báo cáo của một phiên phỏng vấn là **sự kiện đã xảy ra trong quá khứ**. Nó phải hiển thị đúng như lúc chấm, kể cả khi rubric, hồ sơ, hay câu hỏi sau này thay đổi.

Vì vậy `session_questions` lưu hẳn `question_text` (không trỏ tới bảng ngân hàng câu hỏi), `session_scores` lưu kèm `criterion_name`, `interview_sessions` chốt `rubric_version_id`. Nhìn qua tưởng dư thừa dữ liệu — thực ra là cố ý.

**Quy tắc phân biệt:** nếu sửa dữ liệu gốc mà làm sai lệch bản ghi cũ, thì phải chụp ảnh. Nếu sửa dữ liệu gốc mà bản ghi cũ *nên* cập nhật theo, thì dùng khóa ngoại.

### 3. Không bao giờ xóa cứng thứ mà lịch sử đang trỏ vào

CV cũ, hồ sơ cũ, dự án bị gỡ — tất cả đều có phiên phỏng vấn cũ trỏ vào. Cách xử lý trong schema:

- `is_active = false` thay cho xóa (bảng `cv_documents`)
- `delete: restrict` — chặn xóa ở tầng database (khóa ngoại tới `candidate_profiles`)
- `delete: set null` — cho xóa nhưng cắt liên kết, giữ bản ghi lịch sử (khóa ngoại tới `profile_projects`)

---

# Nhóm 1 — Tài khoản & Xác thực

**Bảng:** `user_accounts`, `refresh_tokens`, `email_verification_tokens`, `password_reset_tokens`, `user_preferences`

> ⚠️ **Lưu ý:** danh sách nhóm mà team gửi chỉ liệt kê 3 bảng, thiếu `email_verification_tokens` và `password_reset_tokens`. Hai bảng này bắt buộc phải có để đáp ứng tiêu chí nghiệm thu của story đăng ký email ("email xác thực hết hạn sau 24 giờ", "link reset hết hạn sau 1 giờ"). Nếu bỏ chúng thì phải bỏ luôn phần xác thực email và quên mật khẩu.

## Nhóm này giải quyết việc gì

Trả lời hai câu hỏi ở mọi request: **bạn là ai** và **bạn có còn quyền truy cập không**. Ngoài ra giữ các tùy chọn cá nhân của người dùng.

Đây là nhóm duy nhất đã có sẵn code (`UserAccount`, `RefreshToken`), nên tên cột giữ nguyên 100%.

## Từng bảng

### `user_accounts`

Danh tính gốc. Một người = một hàng, bất kể đăng nhập bằng Google hay email.

| Cột | Vì sao có |
|---|---|
| `password_hash` | **Cho phép NULL.** Người chỉ dùng Google thì không có mật khẩu. Nếu để `NOT NULL` bạn sẽ phải sinh mật khẩu rác cho họ — vừa vô nghĩa vừa là lỗ hổng bảo mật |
| `google_id` | **Cũng cho phép NULL,** và `unique`. Người chỉ đăng ký email thì không có |
| `enabled` | Khóa tài khoản mà không xóa dữ liệu |
| `email_verified_at` | `NULL` = chưa xác thực. Dùng kiểu timestamp thay vì boolean để biết luôn *khi nào* xác thực |
| `updated_at` | Entity đang để `insertable=false, updatable=false` → database tự cập nhật qua trigger hoặc `DEFAULT`. **Đừng gán giá trị này trong code Java**, sẽ bị bỏ qua |

**Quy tắc liên kết tài khoản (quan trọng, dễ làm sai):**

Người dùng đăng ký bằng `an@gmail.com` + mật khẩu. Tuần sau họ bấm "Đăng nhập với Google" cũng bằng `an@gmail.com`.

- ❌ Sai: tạo hàng mới → vi phạm `unique(email)`, hoặc tệ hơn là tạo được và người dùng có hai tài khoản với hai lịch sử phỏng vấn tách rời.
- ✅ Đúng: tìm theo email, thấy rồi thì ghi `google_id` vào **chính hàng đó**.

Luồng ngược lại (Google trước, đăng ký email sau) thì phải bắt họ xác nhận — nếu không, kẻ biết email của bạn có thể đăng ký đè lên tài khoản Google của bạn.

### `refresh_tokens`

Giữ phiên đăng nhập dài hạn. Access token sống ngắn (15 phút), refresh token sống dài (7–30 ngày).

| Cột | Vì sao có |
|---|---|
| `token_hash` | **Lưu hash, không lưu token thô.** Database bị lộ thì token vẫn không dùng được |
| `family_id` | Chống tái sử dụng token. Xem giải thích bên dưới |
| `revoked_at` | `NULL` = còn hiệu lực. Thu hồi bằng cách ghi thời điểm, không xóa hàng — cần giữ để điều tra |

**`family_id` là gì và vì sao cần:**

Mỗi lần refresh, hệ thống cấp token mới và thu hồi token cũ (gọi là token rotation). Cả chuỗi token sinh ra từ một lần đăng nhập chia sẻ cùng một `family_id`.

Nếu ai đó dùng lại một token **đã bị thu hồi**, chỉ có hai khả năng: token bị đánh cắp, hoặc có lỗi đồng bộ. Cách xử lý an toàn là thu hồi **toàn bộ family** — tức đá hết mọi phiên sinh ra từ lần đăng nhập đó. Không có `family_id` thì bạn chỉ chặn được đúng một token, kẻ tấn công vẫn giữ được các token khác trong chuỗi.

### `email_verification_tokens` và `password_reset_tokens`

Hai bảng cấu trúc giống hệt nhau, cố ý tách riêng vì thời hạn và ngữ nghĩa khác nhau (24 giờ so với 1 giờ). Gộp chung thành một bảng với cột `type` cũng được, nhưng tách ra thì mỗi luồng đọc code rõ ràng hơn.

| Cột | Vì sao có |
|---|---|
| `token_hash` | Cùng lý do như refresh token |
| `consumed_at` | `NULL` = chưa dùng. **Đây là cách enforce "chỉ dùng một lần"** — kiểm tra `consumed_at IS NULL` trước khi chấp nhận |
| Index `(user_id, created_at)` | Đếm số lần gửi trong 1 giờ để chống spam email. Không cần bảng đếm riêng |

**Bẫy:** đừng xóa hàng khi token được dùng xong. Nếu xóa, người dùng bấm lại link cũ sẽ nhận lỗi "token không tồn tại" — không phân biệt được với link giả. Giữ hàng lại và trả thông báo "link này đã được sử dụng" thì trải nghiệm tốt hơn nhiều.

### `user_preferences`

Tách khỏi `user_accounts` vì hai lý do: `user_accounts` là bảng đọc ở mọi request nên nên giữ gọn; và tùy chọn sẽ còn thêm nhiều cột về sau.

| Cột | Vì sao có |
|---|---|
| `barge_in_enabled` | Tiêu chí nghiệm thu ghi rõ "tắt được trong cài đặt nếu người dùng thấy phiền" |
| `preferred_mode` | Nhớ lựa chọn văn bản / giọng nói turn-based / realtime |
| `interview_language` | Chuẩn bị cho việc mở rộng tiếng Anh sau này |

Quan hệ `1–1` với `user_accounts` (ràng buộc `unique(user_id)`). Tạo hàng này ngay lúc đăng ký với giá trị mặc định, đừng để `NULL` rồi phải kiểm tra ở mọi chỗ đọc.

## Quan hệ

```
user_accounts (1) ──< (N) refresh_tokens              [cascade]
user_accounts (1) ──< (N) email_verification_tokens   [cascade]
user_accounts (1) ──< (N) password_reset_tokens       [cascade]
user_accounts (1) ─── (1) user_preferences            [cascade]
```

Tất cả đều `cascade`: xóa tài khoản thì mọi token và tùy chọn biến mất theo. Ở đây `cascade` an toàn vì không có gì mang giá trị lịch sử.

**Nhưng chú ý:** `user_accounts` còn được `cv_documents`, `candidate_profiles`, `interview_sessions` tham chiếu tới, cũng với `cascade`. Nghĩa là **xóa một tài khoản sẽ cuốn theo toàn bộ CV, phiên phỏng vấn và báo cáo của người đó.** Nếu sau này cần chức năng "xóa tài khoản nhưng giữ dữ liệu ẩn danh để phân tích", phải đổi thiết kế trước, không thể sửa sau khi đã xóa.

## Bẫy thường gặp

1. **Gán `updated_at` trong code Java** — bị bỏ qua do `insertable=false, updatable=false`. Phải cấu hình ở tầng database.
2. **Quên index `expires_at`** trên refresh token — job dọn dẹp sẽ quét toàn bảng. Index đã có sẵn trong entity, giữ nguyên.
3. **So sánh token bằng chuỗi thô** — phải hash chuỗi nhận được rồi mới tra bảng.
4. **Tạo `user_preferences` lười biếng** (chỉ tạo khi người dùng vào trang cài đặt) — dẫn tới `NULL` rải rác khắp nơi. Tạo ngay lúc đăng ký.

---

# Nhóm 2 — CV & Hồ sơ

**Bảng:** `cv_documents`, `cv_parse_results`, `candidate_profiles`, `profile_educations`, `profile_skills`, `profile_projects`

## Nhóm này giải quyết việc gì

Biến một file PDF thành **dữ liệu có cấu trúc mà AI dùng được để sinh câu hỏi**. Đây là nguyên liệu đầu vào của toàn bộ sản phẩm — CV bóc tách kém thì câu hỏi kém, và không có tính năng nào phía sau cứu được.

Luồng: `PDF` → `cv_documents` → tách hai nhánh → `cv_parse_results` (đông cứng) và `candidate_profiles` + 3 bảng con (sống, sửa được).

## Từng bảng

### `cv_documents`

Giữ **cái file**, không giữ nội dung file.

| Cột | Vì sao có |
|---|---|
| `storage_key` | Đường dẫn trên S3/MinIO. **Không nhét PDF vào database** — làm phình bản backup, chậm mọi truy vấn, và không stream được |
| `status` | Biến upload thành máy trạng thái nhỏ: `UPLOADED → PARSING → PARSED / FAILED`. Không có nó thì giao diện không biết nên hiện vòng xoay hay hiện lỗi |
| `status_message` | Thông báo lỗi hiển thị cho người dùng khi `FAILED` ("File này có vẻ là ảnh scan, chưa đọc được chữ") |
| `is_active` | Xem giải thích bên dưới |
| `checksum_sha256` | Người dùng tải lên đúng file đó lần thứ hai → bỏ qua bước parse, tiết kiệm một lần gọi API |
| `file_size_bytes` | Ghi lại để thống kê. Việc chặn 5MB làm ở tầng ứng dụng, không phải ràng buộc database |

**Vì sao `is_active` chứ không xóa:**

Tiêu chí nghiệm thu ghi "tải lên CV mới thì ghi đè". Nhưng nếu xóa thật hàng cũ, các phiên phỏng vấn đã thực hiện dựa trên CV đó sẽ trỏ vào khoảng không. Người dùng mở lại báo cáo cũ và thấy lỗi.

Cách làm đúng: `UPDATE cv_documents SET is_active = false WHERE user_id = ? AND is_active = true` rồi mới chèn hàng mới. Lịch sử nguyên vẹn, người dùng vẫn thấy đúng một CV đang dùng.

### `cv_parse_results`

Kết quả AI bóc tách, **quan hệ 1–1** với document và **không bao giờ sửa**.

| Cột | Vì sao có |
|---|---|
| `raw_json` | Nguyên văn JSON AI trả về. Bất biến |
| `schema_version` | Khi bạn đổi cấu trúc JSON, các bản cũ vẫn đọc được vì biết chúng theo schema nào |
| `model_name` | Đổi model thì so sánh được chất lượng trước/sau |
| `duration_ms`, `token_cost` | Theo dõi hiệu năng và chi phí thật, không phải ước lượng |

**Vì sao phải giữ bản gốc:**

Khi người dùng báo *"AI hỏi về dự án mà em chưa từng làm"*, có đúng hai khả năng:

1. Bóc tách sai — AI đọc nhầm CV
2. Bóc tách đúng nhưng sinh câu hỏi sai — lỗi ở prompt sinh kịch bản

Không có `raw_json` để đối chiếu thì hai khả năng này **không phân biệt được**, và bạn sẽ sửa nhầm chỗ. Đây là bảng rẻ tiền nhất để debug mà mọi người hay quên làm.

### `candidate_profiles`

Bản làm việc. Đây là nơi duy nhất người dùng chạm vào.

| Cột | Vì sao có |
|---|---|
| `user_id` `unique` | Mỗi người một hồ sơ đang dùng |
| `cv_document_id` | Hồ sơ này sinh ra từ CV nào. `delete: restrict` — không cho xóa CV khi còn hồ sơ trỏ vào |
| `confirmed_at` | **Cái cổng.** `NULL` = chưa bấm "Thông tin chính xác" → chặn bắt đầu phiên |
| `source` | `AUTO_PARSED` hay `USER_EDITED`. Dùng để thống kê tỉ lệ người dùng phải sửa |

### Ba bảng con: `profile_educations`, `profile_skills`, `profile_projects`

**Câu hỏi hay gặp: sao không để nguyên JSON cho gọn?**

Câu trả lời nằm ở bảng khác. `session_questions.source_project_id` là **khóa ngoại trỏ tới `profile_projects.id`**. Bạn không thể tạo khóa ngoại trỏ vào một phần tử bên trong khối JSON.

Mà chính khóa ngoại đó cho phép trả lời câu hỏi *"câu này sinh ra từ dự án nào trong CV"* — nền tảng của tính năng đào sâu dự án, tức là điểm khác biệt chính của sản phẩm.

Ngoài ra:

| Cột / ràng buộc | Tác dụng |
|---|---|
| `unique(profile_id, name)` trên `profile_skills` | Chặn trùng "React" và "react" (nhớ chuẩn hóa chữ thường trước khi ghi) |
| `is_user_edited` | Cho biết AI hay bóc tách sai ở đâu nhất — dữ liệu miễn phí để cải thiện prompt parse |
| `display_order` | Người dùng sắp xếp lại thứ tự ưu tiên |
| `profile_projects.description` | **Nguyên liệu chính** để sinh câu hỏi đào sâu. Cột quan trọng nhất trong cả nhóm |
| `profile_projects.tech_stack` | Để chuỗi phân tách dấu phẩy, chưa cần bảng nối. Đủ cho MVP; nâng cấp sau nếu cần lọc theo công nghệ |

## Quan hệ

```
user_accounts (1) ──< (N) cv_documents                        [cascade]
cv_documents  (1) ─── (1) cv_parse_results                    [cascade]
user_accounts (1) ─── (1) candidate_profiles                  [cascade]
cv_documents  (1) ──< (N) candidate_profiles                  [restrict]  ← chặn xóa CV
candidate_profiles (1) ──< (N) profile_educations             [cascade]
candidate_profiles (1) ──< (N) profile_skills                 [cascade]
candidate_profiles (1) ──< (N) profile_projects               [cascade]
```

Ra ngoài nhóm:

```
candidate_profiles (1) ──< (N) interview_sessions             [restrict]
profile_projects   (1) ──< (N) session_questions              [set null]
profile_skills     (1) ──< (N) session_questions              [set null]
```

**Ba kiểu `delete` ở đây có chủ đích khác nhau — đọc kỹ:**

- `cascade` từ hồ sơ xuống bảng con: xóa hồ sơ thì kỹ năng và dự án đi theo. Hợp lý, chúng không có ý nghĩa độc lập.
- `restrict` từ hồ sơ lên phiên: **không cho xóa hồ sơ khi còn phiên phỏng vấn trỏ vào.** Bảo vệ lịch sử.
- `set null` từ dự án xuống câu hỏi: cho phép người dùng xóa dự án, câu hỏi cũ mất liên kết nhưng **vẫn giữ nguyên nội dung** vì `question_text` đã được lưu sẵn. Báo cáo cũ không vỡ.

## Quyết định thiết kế cần nhớ

**Hồ sơ là trạng thái hiện tại, phiên phỏng vấn là ảnh chụp quá khứ.**

Người dùng sửa CV vào tuần sau → `candidate_profiles` thay đổi → nhưng báo cáo cũ **vẫn hiển thị đúng câu hỏi đã hỏi hôm đó**, vì `session_questions.question_text` là bản chụp. Chỉ có `source_project_id` có thể thành `NULL`.

Đây là hành vi cố ý. Đừng tưởng là bug rồi đi "sửa".

## Bẫy thường gặp

1. **Cho bắt đầu phiên khi `confirmed_at IS NULL`** — vi phạm tiêu chí nghiệm thu, và người dùng sẽ bị hỏi về thứ họ chưa kiểm tra lại.
2. **Sửa đè lên `cv_parse_results.raw_json`** — mất bản gốc, mất khả năng debug. Bảng này chỉ `INSERT`, không bao giờ `UPDATE`.
3. **Xóa cứng CV cũ khi upload mới** — vỡ lịch sử. Dùng `is_active`.
4. **Quên chuẩn hóa tên kỹ năng** — "React", "react", "ReactJS" thành ba dòng khác nhau, ràng buộc `unique` không cứu được.
5. **Parse lại mỗi lần người dùng mở trang** — tốn tiền API. Đã có `status = PARSED` thì đọc kết quả cũ.

---

# Nhóm 3 — Rubric

**Bảng:** `rubrics`, `rubric_versions`, `rubric_criteria`, `rubric_criterion_levels`

## Nhóm này giải quyết việc gì

Rubric là **bảng tiêu chí chấm điểm** — thứ mà người phỏng vấn thật cầm trên tay khi đánh giá ứng viên. Thay vì để AI tự phán "7/10", ta đưa cho nó bảng mô tả rõ từng mức điểm nghĩa là gì.

Ví dụ một dòng trong rubric:

| Tiêu chí | Mức 1 | Mức 2 | Mức 3 | Mức 4 |
|---|---|---|---|---|
| Độ sâu kỹ thuật | Nêu tên công nghệ nhưng không giải thích được | Giải thích được cách dùng, chưa nói được vì sao | Giải thích được lựa chọn và đánh đổi | Phân tích được cả phương án thay thế và giới hạn |

Không có bảng này thì hai lần chấm cùng một câu trả lời sẽ ra hai điểm khác nhau, và khi người dùng hỏi *"sao em được 6 mà không phải 8"* thì không ai trả lời được.

**Đây không phải nhóm phụ.** Tiêu chí "chấm theo bộ tiêu chí công khai kèm dẫn chứng" chính là thứ phân biệt sản phẩm này với việc hỏi ChatGPT một câu. Rubric kém thì phần lõi rỗng.

## Từng bảng

Bốn bảng lồng nhau theo đúng cấu trúc của bảng ví dụ ở trên:

```
rubrics                  →  bộ tiêu chí       ("Phỏng vấn kỹ thuật Fresher")
  └ rubric_versions      →  phiên bản         (v1, v2, v3...)
      └ rubric_criteria  →  từng DÒNG         ("Độ sâu kỹ thuật", "Diễn đạt")
          └ ..._levels   →  từng Ô mức điểm   (Mức 1 / 2 / 3 / 4)
```

### `rubrics`

Danh mục các bộ tiêu chí. Ở MVP chỉ có một hàng: `TECH_INTERVIEW_FRESHER`. Bảng này tồn tại để sau này thêm bộ cho vị trí khác mà không phải đổi cấu trúc.

| Cột | Vì sao có |
|---|---|
| `code` | Mã bất biến dùng trong code (`TECH_INTERVIEW_FRESHER`). Đừng tra cứu bằng `name` — tên là thứ sẽ đổi |

### `rubric_versions`

**Bảng quan trọng nhất nhóm này.** Mỗi lần sửa nội dung rubric là tạo một version mới, không sửa đè.

| Cột | Vì sao có |
|---|---|
| `version_no` | Số thứ tự tăng dần trong cùng một rubric |
| `is_current` | Version nào đang dùng cho phiên mới. Các version cũ vẫn tồn tại để phục vụ báo cáo cũ |
| `published_at` | `NULL` = bản nháp, chưa cho dùng |
| `change_note` | Ghi lại đã sửa gì so với version trước — cực kỳ hữu ích khi điểm số đột nhiên lệch |

**Vì sao phải có version — kể một tình huống cụ thể:**

Tuần 3 bạn phát hành rubric với tiêu chí "Diễn đạt", thang 4 mức. 200 người dùng đã phỏng vấn và nhận điểm.

Tuần 5 bạn thấy tiêu chí đó quá khắt khe, sửa lại mô tả các mức.

- **Không có version:** toàn bộ 200 báo cáo cũ giờ hiển thị mô tả mức điểm mới, trong khi điểm số của họ được chấm theo mô tả cũ. Báo cáo trở nên vô nghĩa mà **không ai phát hiện ra** — hệ thống vẫn chạy bình thường, chỉ có nội dung là sai.
- **Có version:** phiên cũ trỏ tới `rubric_version_id = 1`, phiên mới trỏ tới `2`. Mỗi báo cáo hiển thị đúng rubric đã dùng để chấm nó.

Đây là loại lỗi tệ nhất: im lặng, không có exception, chỉ phát hiện khi người dùng khiếu nại.

### `rubric_criteria`

Từng tiêu chí trong một version.

| Cột | Vì sao có |
|---|---|
| `code` | Mã ổn định (`TECHNICAL_DEPTH`). `session_scores` tham chiếu qua đây |
| `weight` | Trọng số khi tính điểm tổng. Tổng các trọng số nên bằng 1 — **enforce ở tầng ứng dụng**, database không kiểm tra được |
| `max_score` | Mặc định 4, khớp với 4 mức |
| `display_order` | Thứ tự hiển thị trên báo cáo |

### `rubric_criterion_levels`

Bốn ô mô tả cho mỗi tiêu chí. Đây là thứ **đưa thẳng vào prompt** khi gọi AI chấm điểm.

| Cột | Vì sao có |
|---|---|
| `descriptor` | Mô tả chi tiết mức này nghĩa là gì. Chất lượng của cột này quyết định chất lượng chấm điểm |
| `label` | Nhãn ngắn hiển thị cho người dùng ("Cơ bản", "Tốt") |
| `score_value` | Điểm số ứng với mức. Tách khỏi `level_no` để sau này có thể dùng thang không đều |

## Quan hệ

```
rubrics (1) ──< (N) rubric_versions            [cascade]
rubric_versions (1) ──< (N) rubric_criteria    [cascade]
rubric_criteria (1) ──< (N) ..._levels         [cascade]
```

Ra ngoài nhóm — **đây là hai đường quan trọng nhất:**

```
rubric_versions (1) ──< (N) interview_sessions  [restrict]
rubric_criteria (1) ──< (N) session_scores      [restrict]
```

Cả hai đều `restrict`: **không cho xóa rubric khi còn phiên hoặc điểm trỏ vào.** Đây là lá chắn cuối cùng bảo vệ lịch sử. Nếu ai đó viết migration xóa rubric cũ, database sẽ từ chối.

Chú ý `interview_sessions` trỏ tới `rubric_version_id`, **không** trỏ tới `rubric_id`. Nếu trỏ tới `rubric_id` thì toàn bộ cơ chế version trở nên vô dụng.

## Cách dùng trong luồng thật

1. Người dùng bấm "Bắt đầu phiên"
2. Backend tra `rubric_versions WHERE rubric_id = ? AND is_current = true`
3. Ghi `rubric_version_id` vào `interview_sessions` — **chốt tại đây, không đổi nữa**
4. Trước khi phiên bắt đầu, hiển thị toàn bộ tiêu chí và các mức cho người dùng xem (tiêu chí nghiệm thu: "rubric xem được TRƯỚC khi bắt đầu")
5. Khi chấm, nạp `rubric_criteria` + `..._levels` của **đúng version đã chốt** vào prompt
6. Ghi điểm vào `session_scores` với `criterion_id` tương ứng

## Bẫy thường gặp

1. **Sửa đè lên version đang được dùng** — lỗi im lặng, hỏng toàn bộ báo cáo cũ. Sửa nội dung = tạo version mới.
2. **Trỏ `interview_sessions` tới `rubric_id`** thay vì `rubric_version_id` — vô hiệu hóa hoàn toàn cơ chế version.
3. **Quên đặt `is_current = false` cho version cũ** khi phát hành version mới → hai version cùng `is_current`, truy vấn trả kết quả ngẫu nhiên.
4. **Tổng `weight` không bằng 1** — điểm tổng sai lệch. Viết test kiểm tra bất biến này.
5. **Soạn `descriptor` qua loa** — đây là việc không cần code nhưng quyết định chất lượng sản phẩm. Nên soạn xong **trước** khi viết dòng code chấm điểm đầu tiên.

---

# Nhóm 4 — Phiên phỏng vấn

**Bảng:** `interview_sessions`, `session_state_transitions`, `session_questions`, `session_turns`

## Nhóm này giải quyết việc gì

Đây là **trái tim của sản phẩm**. Nhóm này ghi lại toàn bộ diễn biến một buổi phỏng vấn: bắt đầu lúc nào, hỏi những gì, người dùng trả lời ra sao, đang ở bước nào, kết thúc vì lý do gì.

Yêu cầu khó nhất: *"phiên đang dở được giữ nguyên khi lỡ đóng tab hoặc mất mạng"*. Toàn bộ thiết kế nhóm này xoay quanh việc đó.

## Từng bảng

### `interview_sessions`

Một buổi phỏng vấn = một hàng.

| Cột | Vì sao có |
|---|---|
| `profile_id` | Hồ sơ dùng để sinh câu hỏi. `restrict` — không cho xóa hồ sơ khi còn phiên |
| `rubric_version_id` | **Chốt version rubric ngay lúc tạo phiên.** Xem nhóm 3 |
| `mode` | `TEXT` / `VOICE_TURN_BASED` / `VOICE_REALTIME`. Phiên có thể bắt đầu ở realtime rồi tụt xuống turn-based khi mất mạng |
| `status` | Trạng thái hiện tại của máy trạng thái |
| `current_turn_index` | **Chìa khóa của việc khôi phục.** Mở lại là biết đang dở ở lượt nào |
| `last_activity_at` | Job dọn dẹp quét cột này để tìm phiên bỏ quá 24 giờ |
| `end_reason` | Kết thúc bình thường, người dùng bỏ, hết hạn, hay lỗi hệ thống |
| `overall_score` | **Cố ý nhân bản** từ `session_reports`. Xem giải thích bên dưới |

**Vì sao nhân bản `overall_score`:**

Biểu đồ tiến bộ cần lấy điểm của 20 phiên gần nhất. Nếu không có cột này, mỗi lần vẽ biểu đồ phải `JOIN` sang `session_reports`. Với một cột đọc rất nhiều và gần như không đổi sau khi ghi, nhân bản là đánh đổi đúng.

**Điều kiện:** ghi cột này **cùng transaction** với lúc tạo `session_reports`. Nếu ghi tách rời, sớm muộn hai nơi sẽ lệch nhau.

**Các index và mục đích:**

| Index | Phục vụ truy vấn |
|---|---|
| `(user_id, status)` | Tìm phiên dang dở để hiện nút "Tiếp tục" trên trang chủ |
| `(user_id, completed_at)` | Danh sách lịch sử + biểu đồ điểm theo thời gian |
| `last_activity_at` | Job quét phiên quá hạn 24 giờ |

### `session_state_transitions`

Nhật ký mọi lần chuyển trạng thái. **Chỉ ghi thêm, không bao giờ sửa hay xóa.**

| Cột | Vì sao có |
|---|---|
| `from_status` / `to_status` | Chuyển từ đâu sang đâu. `from_status` là `NULL` ở lần đầu |
| `actor` | `USER` (bấm nút), `SYSTEM` (engine tự chuyển), `SCHEDULER` (job hết hạn) |
| `reason` | Mô tả ngắn, ví dụ "người dùng bấm kết thúc sớm" |
| `occurred_at` | Thời điểm chính xác |

**Vì sao cần bảng riêng thay vì chỉ giữ `status` hiện tại:**

Khi người dùng báo *"phiên của em tự nhiên kết thúc"*, cột `status = EXPIRED` chỉ cho biết kết quả, không cho biết **vì sao và lúc nào**. Có nhật ký thì bạn dựng lại được toàn bộ dòng thời gian và biết ngay là do job dọn dẹp chạy hay do lỗi engine.

Đây là bảng cứu bạn khi debug production. Chi phí ghi gần như bằng 0.

### `session_questions`

Kịch bản 5–7 câu, **sinh trước khi phiên bắt đầu**.

| Cột | Vì sao có |
|---|---|
| `ordinal` | Thứ tự câu hỏi. `unique(session_id, ordinal)` |
| `question_text` | **Lưu nguyên văn.** Đây là ảnh chụp lịch sử — báo cáo cũ phải hiển thị đúng câu đã hỏi |
| `source_project_id` | Câu này đào sâu vào dự án nào trong CV. `set null` khi dự án bị xóa |
| `source_skill_id` | Câu này hỏi về kỹ năng nào |
| `generation_seed` | Đảm bảo tiêu chí "chạy lại cùng CV phải ra bộ câu khác" |
| `difficulty` | Độ khó 1–5 |

**Phân biệt rất quan trọng:** bảng này chỉ chứa **câu hỏi trong kịch bản**. Câu follow-up phát sinh khi đang nói chuyện **không nằm ở đây** — chúng là `session_turns` có `is_followup = true`.

Lý do tách: kịch bản là thứ sinh một lần, có thể xem trước, có thể đếm được. Follow-up là thứ ứng biến, số lượng không biết trước.

### `session_turns`

Từng lượt nói trong hội thoại. Đây là bảng có nhiều hàng nhất hệ thống.

| Cột | Vì sao có |
|---|---|
| `turn_index` | Thứ tự trong toàn phiên. `unique(session_id, turn_index)` |
| `role` | `INTERVIEWER` hay `CANDIDATE` |
| `question_id` | Lượt này thuộc câu hỏi nào trong kịch bản. `NULL` với lời chào/kết thúc |
| `parent_turn_id` | **Tự tham chiếu.** Follow-up trỏ về lượt trả lời mà nó đào sâu vào |
| `is_followup`, `followup_depth` | Enforce quy tắc "tối đa 2 follow-up liên tiếp" |
| `content_text` | Với lượt giọng nói: đây là transcript **cuối cùng đã dùng để chấm** |
| `was_interrupted` | Lượt AI bị người dùng cắt ngang (tính năng barge-in) |
| `input_mode` | Lượt này diễn ra ở chế độ nào — phiên có thể đổi chế độ giữa chừng |
| `latency_ms` | Đo độ trễ thật để tính p95 |

**`parent_turn_id` hoạt động thế nào:**

```
turn 3  INTERVIEWER  "Kể về dự án quản lý thư viện của bạn"    question_id=2
turn 4  CANDIDATE    "Em dùng Spring Boot và MySQL..."          question_id=2
turn 5  INTERVIEWER  "Vì sao chọn MySQL mà không phải Postgres?"
        └ is_followup=true, followup_depth=1, parent_turn_id=4
turn 6  CANDIDATE    "Vì em quen hơn ạ"                         
turn 7  INTERVIEWER  "Nếu cần full-text search thì sao?"
        └ is_followup=true, followup_depth=2, parent_turn_id=6
```

Tới `followup_depth = 2` thì engine **bắt buộc** chuyển sang câu hỏi tiếp theo trong kịch bản. Quy tắc này được kiểm tra bằng dữ liệu trong bảng, không phải chỉ trông chờ vào prompt — vì prompt sẽ trôi.

**`content_text` với lượt giọng nói:**

Bảng `turn_transcripts` giữ cả bản gốc STT và bản người dùng sửa. Cột `content_text` ở đây giữ **bản cuối cùng** đã dùng để chấm. Nhân bản có chủ đích: mọi truy vấn đọc hội thoại chỉ cần một bảng, không phải `JOIN` và xử lý logic "lấy `edited_text` nếu có, không thì lấy `raw_text`" ở khắp nơi.

## Quan hệ

```
user_accounts      (1) ──< (N) interview_sessions        [cascade]
candidate_profiles (1) ──< (N) interview_sessions        [restrict]
rubric_versions    (1) ──< (N) interview_sessions        [restrict]

interview_sessions (1) ──< (N) session_state_transitions [cascade]
interview_sessions (1) ──< (N) session_questions         [cascade]
interview_sessions (1) ──< (N) session_turns             [cascade]

session_questions  (1) ──< (N) session_turns             [set null]
session_turns      (1) ──< (N) session_turns             [set null]  ← tự tham chiếu
profile_projects   (1) ──< (N) session_questions         [set null]
profile_skills     (1) ──< (N) session_questions         [set null]
```

`session_turns` là **tâm điểm của cả schema** — bốn nhóm khác trỏ vào nó:

```
session_turns (1) ─── (1) turn_transcripts
session_turns (1) ──< (N) turn_audio_assets
session_turns (1) ──< (N) score_evidences
session_turns (1) ─── (1) turn_speech_metrics
session_turns (1) ──< (N) turn_filler_occurrences
```

Nghĩa là: **thiết kế sai `session_turns` thì phải sửa lại năm nhóm.** Đây là bảng cần thống nhất kỹ nhất trong team trước khi code.

## Máy trạng thái

```
CREATED
   ↓  (sinh kịch bản xong)
SCRIPT_GENERATING
   ↓
IN_PROGRESS ⇄ PAUSED
   ↓                    ↓ (quá 24h)
SCORING              EXPIRED
   ↓                    ↓
COMPLETED  ←────────────┘  (vẫn chấm phần đã làm)
```

`ABANDONED` dành cho trường hợp người dùng chủ động hủy.

**Quy tắc bắt buộc:** mọi lần chuyển trạng thái phải (1) cập nhật `interview_sessions.status`, (2) ghi một hàng vào `session_state_transitions`, (3) cập nhật `last_activity_at` — **trong cùng một transaction**.

## Bẫy thường gặp

1. **Giữ trạng thái phiên trong bộ nhớ** (biến static, session của servlet, Redis không bền vững) — server restart là mất hết. Tiêu chí nghiệm thu ghi rõ "lưu xuống database sau MỖI lượt". Đây là lỗi làm sập demo trước mặt giảng viên.
2. **Quên cập nhật `last_activity_at`** — job dọn dẹp hiểu nhầm phiên đang chạy là phiên bỏ hoang, và kết thúc nó giữa chừng.
3. **Nhét follow-up vào `session_questions`** — làm hỏng `ordinal` và khiến việc đếm "còn mấy câu nữa" sai.
4. **Không enforce `followup_depth`** — AI xoáy vô tận vào một chủ đề, người dùng bỏ đi.
5. **`turn_index` sinh ở tầng ứng dụng bằng cách đếm** (`SELECT COUNT(*)`) — hai request đồng thời tạo ra hai lượt cùng chỉ số, vi phạm `unique`. Dùng chuỗi tăng dần theo phiên hoặc khóa lạc quan.
6. **Cho phép ghi vào phiên đã `COMPLETED`** — kiểm tra trạng thái trước mọi thao tác ghi.

---

# Nhóm 5 — Âm thanh & Transcript

**Bảng:** `turn_audio_assets`, `turn_transcripts`, `session_voice_connections`

## Nhóm này giải quyết việc gì

Ba việc: lưu file âm thanh, lưu kết quả chuyển giọng nói thành văn bản, và đo chất lượng kết nối realtime.

Nhóm này gánh **rủi ro lớn nhất của dự án**: chưa ai đo được STT hoạt động thế nào trên giọng tiếng Anh của sinh viên Việt Nam. Thiết kế bảng phải giả định rằng nhận dạng sẽ sai kha khá.

## Từng bảng

### `turn_audio_assets`

File âm thanh của từng lượt.

| Cột | Vì sao có |
|---|---|
| `kind` | `USER_RECORDING` (người dùng ghi âm) hay `AI_TTS` (tiếng AI đọc câu hỏi) |
| `storage_key` | Đường dẫn trên object storage. Không lưu binary trong database |
| `duration_ms` | Cần cho việc tính tốc độ nói ở nhóm 7 |
| `format`, `sample_rate` | Trình duyệt trả `webm/opus`, một số STT cần `wav 16kHz` — ghi lại để biết đã chuyển đổi hay chưa |

**Cân nhắc về lưu trữ:** ghi âm là loại dữ liệu tốn chỗ và nhạy cảm. Nên có job tự xóa file sau N ngày trong khi **vẫn giữ transcript** — người dùng vẫn xem lại được nội dung phiên cũ, còn bạn thì giảm chi phí lưu trữ và giảm rủi ro. Chưa cần làm ở MVP nhưng nên thiết kế sẵn trong đầu.

### `turn_transcripts`

Bảng quan trọng nhất nhóm này. Quan hệ **1–1** với `session_turns`.

| Cột | Vì sao có |
|---|---|
| `raw_text` | **Bản gốc từ STT. Không bao giờ ghi đè** |
| `edited_text` | Bản người dùng sửa. `NULL` = không sửa gì |
| `is_edited` | Cờ tiện lợi, tránh phải so sánh hai chuỗi |
| `stt_confidence` | Độ tin cậy do STT trả về. Thấp thì nhắc người dùng kiểm tra kỹ |
| `word_timings` | **Dấu thời gian mức từ.** Xem cảnh báo bên dưới |
| `stt_provider` | Đổi nhà cung cấp thì so sánh được chất lượng |

**Vì sao giữ cả hai bản:**

Tiêu chí nghiệm thu ghi "lưu cả bản gốc và bản đã sửa để đối chiếu". Đây không phải yêu cầu thừa — nó cho bạn **dữ liệu thật về chất lượng STT trên giọng Việt**, thứ mà lẽ ra phải đo ở Sprint 0 nhưng đã bị bỏ qua do rút ngắn thời gian.

Sau 100 phiên, chỉ cần một truy vấn là biết STT sai nhiều hay ít:

```sql
SELECT
  COUNT(*) FILTER (WHERE is_edited) * 100.0 / COUNT(*) AS ty_le_phai_sua,
  AVG(stt_confidence)                                  AS do_tin_cay_tb
FROM turn_transcripts;
```

Nếu tỉ lệ phải sửa trên 40%, bạn biết ngay là phải đổi nhà cung cấp STT — và biết được điều đó **bằng dữ liệu**, không phải bằng cảm giác.

> ⚠️ **Cột `word_timings` là điều kiện tiên quyết của cả nhóm 7.**
>
> Không có dấu thời gian mức từ thì không đo được khoảng lặng, không định vị được từ đệm, và toàn bộ tính năng phân tích giọng nói tắc. **Xác nhận nhà cung cấp STT có hỗ trợ trước khi vào Sprint 4**, đừng để tới lúc code mới phát hiện.

**Lưu ý về chế độ realtime:** tiêu chí nghiệm thu ghi rõ *"chỉ áp dụng cho chế độ turn-based; ở chế độ realtime không có bước xác nhận"*. Nghĩa là ở realtime, `edited_text` sẽ luôn `NULL` và người dùng **mất lớp bảo vệ khỏi lỗi STT**. Đây là đánh đổi có ý thức của tính năng realtime, không phải thiếu sót.

### `session_voice_connections`

Đo chất lượng phiên giọng nói. Một phiên có thể có nhiều hàng nếu người dùng mất kết nối rồi vào lại.

| Cột | Vì sao có |
|---|---|
| `p50_latency_ms`, `p95_latency_ms` | Tiêu chí nghiệm thu đặt mục tiêu p95 dưới 1200ms. **Cột này là cách chứng minh** |
| `fell_back_to_turn_based` | Đếm được bao nhiêu phiên phải tụt chế độ — chỉ số sức khỏe của tính năng realtime |
| `client_platform` | Chrome desktop và Safari iOS hành xử khác nhau, cần tách để so sánh |
| `disconnect_reason` | Mạng yếu, người dùng đóng tab, hay lỗi server |

**Đây là bảng cho ngày bảo vệ.** Khi bị hỏi *"độ trễ thực tế bao nhiêu?"*, câu trả lời "p95 là 950ms trên 47 phiên thật, trong đó 6 phiên phải tụt về turn-based" thuyết phục hơn hẳn "dạ nhanh ạ".

## Quan hệ

```
session_turns      (1) ──< (N) turn_audio_assets         [cascade]
session_turns      (1) ─── (1) turn_transcripts          [cascade]
interview_sessions (1) ──< (N) session_voice_connections [cascade]
```

Chú ý sự bất đối xứng: `turn_audio_assets` gắn với **lượt nói**, còn `session_voice_connections` gắn với **cả phiên**. Vì kết nối realtime là thứ tồn tại xuyên suốt nhiều lượt, không thuộc về lượt nào.

## Luồng dữ liệu — chế độ turn-based

```
1. AI sinh câu hỏi
   → session_turns (role=INTERVIEWER)
   → TTS đọc lên → turn_audio_assets (kind=AI_TTS)

2. Người dùng bấm ghi âm, nói xong
   → turn_audio_assets (kind=USER_RECORDING)

3. Gửi qua STT
   → turn_transcripts (raw_text, word_timings, confidence)

4. Hiển thị cho người dùng xem
   → sửa nếu cần → turn_transcripts.edited_text, is_edited=true

5. Bấm xác nhận
   → session_turns (role=CANDIDATE, content_text = bản cuối)
   → mới bắt đầu chấm
```

Bước 4 là lớp bảo vệ. **Đừng bỏ qua nó để tiết kiệm một màn hình** — nó là thứ duy nhất đứng giữa lỗi STT và điểm số oan.

## Bẫy thường gặp

1. **Ghi đè `raw_text` bằng bản đã sửa** — mất dữ liệu chẩn đoán chất lượng STT. Hai cột riêng biệt, luôn luôn.
2. **Bắt đầu chấm ngay khi STT trả về**, không đợi người dùng xác nhận — vi phạm tiêu chí nghiệm thu và tạo ra điểm oan.
3. **Không kiểm tra `word_timings` trước khi vào Sprint 4** — tắc giữa tuần cuối, không kịp xoay.
4. **Lưu file âm thanh trong database** dạng `BLOB` — phình backup, chậm mọi thứ.
5. **Quên chuẩn hóa định dạng** — trình duyệt trả `webm`, STT cần `wav`. Ghi lại `format` để biết đã chuyển hay chưa.
6. **Chỉ đo độ trễ bằng `console.log` khi dev** — không có dữ liệu thật để chứng minh khi bảo vệ. Ghi vào bảng.

---

# Nhóm 6 — Chấm điểm & Báo cáo

**Bảng:** `session_scores`, `score_evidences`, `session_reports`, `report_highlights`

## Nhóm này giải quyết việc gì

Biến một cuộc hội thoại thành **điểm số có thể bảo vệ được**. Từ khóa là "bảo vệ được": mỗi điểm phải giải thích được, và mỗi lời giải thích phải chỉ ra được câu nào trong transcript làm căn cứ.

Tiêu chí nghiệm thu: *"chấm theo bộ tiêu chí công khai kèm dẫn chứng"*. Vế "kèm dẫn chứng" chính là lý do tồn tại của bảng `score_evidences`.

## Từng bảng

### `session_scores`

Điểm của từng tiêu chí trong một phiên. Một phiên có 4–5 hàng, ứng với 4–5 tiêu chí trong rubric.

| Cột | Vì sao có |
|---|---|
| `criterion_id` | Trỏ tới `rubric_criteria` của **đúng version đã chốt trong phiên** |
| `score`, `max_score` | Lưu cả hai để hiển thị "3.0 / 4.0" mà không phải tra rubric |
| `level_no` | Rơi vào mức nào trong 4 mức |
| `comment` | **`NOT NULL` có chủ đích.** Không cho phép chấm mà không giải thích |
| `model_name` | Model nào đã chấm — cần khi so sánh chất lượng giữa các model |

**Vì sao `comment` là `NOT NULL`:**

Đây là ràng buộc ở tầng database để enforce một yêu cầu sản phẩm. Nếu để `NULL` được, sớm muộn sẽ có đường code nào đó ghi điểm mà quên phần giải thích, và người dùng nhận được con số trần trụi. Ràng buộc này biến lỗi thầm lặng thành lỗi ồn ào.

### `score_evidences`

Trích dẫn transcript làm căn cứ cho từng điểm.

| Cột | Vì sao có |
|---|---|
| `turn_id` | Dẫn chứng nằm ở lượt nói nào |
| `quote_text` | Nguyên văn đoạn được trích |
| `start_offset`, `end_offset` | Vị trí ký tự — để highlight trên giao diện |

**Vì sao đây là bảng riêng chứ không phải một cột text:**

Nếu nhét trích dẫn vào `session_scores.comment`, bạn có ba vấn đề: không kiểm chứng được trích dẫn có thật hay AI bịa; không highlight được lên transcript; không đếm được có bao nhiêu điểm được chấm mà không có căn cứ.

Với bảng riêng, bạn viết được ràng buộc: **mỗi `session_scores` phải có tối thiểu một `score_evidences`.** Không có nó thì tiêu chí "kèm dẫn chứng" chỉ là lời hứa trong prompt — và prompt thì trôi.

Truy vấn kiểm tra sức khỏe:

```sql
SELECT s.id, s.criterion_id, s.score
FROM session_scores s
LEFT JOIN score_evidences e ON e.session_score_id = s.id
WHERE e.id IS NULL;
```

Kết quả rỗng = mọi điểm đều có căn cứ. Kết quả có hàng = AI đang chấm khống, cần sửa prompt.

### `session_reports`

Báo cáo tổng hợp. Quan hệ **1–1** với phiên.

| Cột | Vì sao có |
|---|---|
| `overall_score` | Điểm tổng có trọng số. Được nhân bản sang `interview_sessions` |
| `summary_text` | Nhận xét chung bằng tiếng Việt |
| `disclaimer` | **`NOT NULL`.** Tiêu chí nghiệm thu: "ghi rõ đây là công cụ luyện tập, không phải chứng nhận năng lực" |
| `duration_ms` | Đo tiêu chí "sinh xong dưới 60 giây" |
| `model_name` | Truy vết khi nhận xét có vấn đề |

**Vì sao `disclaimer` lưu trong database chứ không hardcode ở frontend:**

Nội dung này có thể phải đổi vì lý do pháp lý. Nếu hardcode, báo cáo cũ sẽ hiển thị nội dung mới — cùng lỗi im lặng như trường hợp rubric. Lưu kèm mỗi báo cáo thì mỗi bản giữ đúng nội dung tại thời điểm sinh.

### `report_highlights`

Các điểm mạnh, điểm cần cải thiện, gợi ý hành động.

| Cột | Vì sao có |
|---|---|
| `type` | `STRENGTH` / `IMPROVEMENT` / `NEXT_ACTION` |
| `content` | Nội dung từng mục |
| `display_order` | Thứ tự hiển thị |

Tách hàng thay vì nhồi vào một cột text để: đếm được (tiêu chí ghi "3 điểm mạnh, 3 điểm cần cải thiện"), sắp xếp lại được, và sau này thống kê được điểm yếu nào hay lặp lại ở nhiều phiên.

## Quan hệ

```
interview_sessions (1) ──< (N) session_scores      [cascade]
rubric_criteria    (1) ──< (N) session_scores      [restrict]
session_scores     (1) ──< (N) score_evidences     [cascade]
session_turns      (1) ──< (N) score_evidences     [cascade]
interview_sessions (1) ─── (1) session_reports     [cascade]
session_reports    (1) ──< (N) report_highlights   [cascade]
```

Chú ý `score_evidences` có **hai khóa ngoại**: một tới điểm số, một tới lượt nói. Nó là bảng nối giữa nhóm 4 và nhóm 6 — chính chỗ này biến "dẫn chứng" từ ý tưởng thành dữ liệu kiểm chứng được.

## Luồng chấm điểm

```
1. Phiên chuyển sang SCORING
2. Nạp rubric_criteria + levels của rubric_version đã chốt
3. Nạp toàn bộ session_turns của phiên
4. Gọi AI chấm, yêu cầu trả về JSON: mỗi tiêu chí có điểm, giải thích, và trích dẫn
5. Ghi session_scores + score_evidences  ─┐
6. Sinh session_reports + report_highlights │ cùng transaction
7. Cập nhật interview_sessions.overall_score ┘
8. Chuyển trạng thái sang COMPLETED
```

Bước 5–7 phải **cùng transaction**. Nếu tách, sẽ có phiên có điểm mà không có báo cáo, hoặc `overall_score` lệch với báo cáo.

## Bẫy thường gặp

1. **Ghi `session_scores` mà không ghi `score_evidences`** — vi phạm tiêu chí nghiệm thu một cách âm thầm. Viết kiểm tra tự động.
2. **Tính `overall_score` mà quên trọng số** — cộng trung bình đơn giản trong khi rubric có `weight` khác nhau.
3. **Chấm bằng rubric hiện hành** thay vì version đã chốt trong phiên — sai lệch khi rubric đã đổi.
4. **Cập nhật `interview_sessions.overall_score` ở transaction khác** — hai nơi lệch nhau, biểu đồ tiến bộ hiển thị sai.
5. **Tin trích dẫn AI trả về mà không kiểm tra** — AI có thể bịa câu người dùng chưa từng nói. Nên đối chiếu `quote_text` có thực sự xuất hiện trong `content_text` của lượt đó không; không khớp thì loại bỏ dẫn chứng đó và ghi log.

---

# Nhóm 7 — Phân tích giọng nói

**Bảng:** `filler_word_dictionary`, `turn_speech_metrics`, `turn_filler_occurrences`, `session_speech_metrics`

## Nhóm này giải quyết việc gì

Đo **cách nói**, tách biệt hoàn toàn khỏi **nội dung nói**. Tốc độ nói, số từ đệm, khoảng lặng, tỉ lệ thời gian người dùng chiếm sóng.

> ⚠️ **Phụ thuộc cứng:** cả nhóm này chỉ chạy được nếu `turn_transcripts.word_timings` có dữ liệu. Xác nhận nhà cung cấp STT hỗ trợ dấu thời gian mức từ **trước khi bắt đầu**.

## Nguyên tắc đạo đức đã được mã hóa vào schema

Nhìn kỹ các cột: `words_per_minute`, `filler_count`, `silence_ms`, `user_talk_ratio`. Tất cả đều là **đại lượng đo được**.

Cố ý **không có** các cột kiểu `confidence_score`, `attitude_score`, `professionalism_score`.

Lý do: suy diễn tính cách từ giọng nói là vùng nhạy cảm và dễ thiên vị. Người nói giọng miền Trung, người nói chậm do đang suy nghĩ kỹ, người có tật nói lắp — tất cả sẽ bị hệ thống chấm là "thiếu tự tin" một cách oan uổng. Với sản phẩm nhắm vào sinh viên khắp các vùng miền, đây là rủi ro thật.

Ranh giới: **báo cáo con số, để người dùng tự diễn giải.** "Bạn nói 180 từ/phút, khoảng tham chiếu cho phỏng vấn là 130–160" là hữu ích. "Bạn nói quá nhanh, thể hiện sự lo lắng" là suy diễn.

Nếu sau này có ai đề xuất thêm cột `confidence_score`, hãy quay lại đọc đoạn này.

## Từng bảng

### `filler_word_dictionary`

Danh sách từ đệm theo ngôn ngữ.

| Cột | Vì sao có |
|---|---|
| `language_code` | Tiếng Việt có "ừm", "à", "kiểu như", "thì là mà"; tiếng Anh có "um", "like", "you know" |
| `word` | Từ hoặc cụm từ |
| `is_active` | Tắt một từ mà không xóa — hữu ích khi phát hiện từ nào gây báo động giả |

**Vì sao để trong database thay vì hardcode:** danh sách này sẽ được bổ sung liên tục khi bạn xem transcript thật. Để trong database thì thêm từ mới không cần deploy lại.

**Bẫy:** "à" là từ đệm, nhưng "à" trong "à quên, em còn muốn nói thêm" thì không hẳn. Và "like" trong "I like this framework" hoàn toàn không phải từ đệm. Cần xét ngữ cảnh, đừng chỉ đếm chuỗi khớp — nếu không, người nói tiếng Anh tốt sẽ bị phạt oan vì dùng động từ "like".

### `turn_speech_metrics`

Chỉ số của từng lượt. Quan hệ **1–1** với `session_turns`.

| Cột | Vì sao có |
|---|---|
| `words_per_minute` | Tốc độ nói |
| `filler_count` | Số từ đệm trong lượt này |
| `speaking_ms`, `silence_ms` | Nói bao lâu, im bao lâu |
| `longest_pause_ms` | Khoảng lặng dài nhất — dấu hiệu bị bí |

Chỉ tính cho lượt `role = CANDIDATE`. Lượt của AI không cần đo.

### `turn_filler_occurrences`

Từng lần xuất hiện của từ đệm, kèm mốc thời gian.

Bảng này cho phép **highlight từ đệm ngay trên transcript** — người dùng thấy được chính xác mình đã "ừm" ở đâu, hữu ích hơn nhiều so với việc chỉ đưa con số 23.

Đây là bảng có thể cắt nếu thiếu thời gian. Chỉ cần `turn_speech_metrics.filler_count` là đã đáp ứng được tiêu chí nghiệm thu tối thiểu.

### `session_speech_metrics`

Tổng hợp cả phiên. Quan hệ **1–1** với phiên.

| Cột | Vì sao có |
|---|---|
| `filler_per_100_words` | **Chỉ số chuẩn hóa.** Quan trọng hơn `filler_count` thô, vì người nói nhiều tất nhiên có nhiều từ đệm hơn |
| `user_talk_ratio` | Tỉ lệ thời gian người dùng nói. Fresher thường nói quá ít |
| `reference_source` | **`NOT NULL`.** Xem giải thích bên dưới |

**Vì sao `reference_source` là `NOT NULL`:**

Tiêu chí nghiệm thu ghi *"so sánh với khoảng tham chiếu và nêu rõ khoảng đó lấy từ nguồn nào"*.

Nếu bạn hiển thị "tốc độ lý tưởng là 130–160 từ/phút" mà không nói lấy từ đâu, đó là con số bịa. Ràng buộc `NOT NULL` buộc lập trình viên phải điền một nguồn cụ thể — dù chỉ là "trung bình của 200 phiên trên hệ thống này" thì cũng trung thực hơn là im lặng.

## Quan hệ

```
session_turns      (1) ─── (1) turn_speech_metrics
session_turns      (1) ──< (N) turn_filler_occurrences
interview_sessions (1) ─── (1) session_speech_metrics
```

Tất cả `cascade`. Nhóm này là dữ liệu dẫn xuất — xóa phiên thì xóa theo, và tính lại được từ `word_timings` nếu cần.

`filler_word_dictionary` không có khóa ngoại nào — nó là bảng tra cứu độc lập.

## Luồng tính toán

```
1. Phiên chuyển sang SCORING
2. Với mỗi lượt CANDIDATE:
   - Đọc turn_transcripts.word_timings
   - Đếm từ, tính khoảng cách giữa các từ → silence_ms
   - Đối chiếu với filler_word_dictionary → filler_count
   - Ghi turn_speech_metrics (+ turn_filler_occurrences nếu làm)
3. Tổng hợp toàn phiên → session_speech_metrics
4. Đưa vào báo cáo
```

Chạy **sau khi transcript đã cố định** (người dùng đã xác nhận). Tính trên bản chưa sửa sẽ ra kết quả sai.

Có thể chạy bất đồng bộ sau khi báo cáo đã hiện — không nên bắt người dùng chờ thêm.

## Bẫy thường gặp

1. **Đếm từ đệm bằng khớp chuỗi thuần** — "like" trong "I like Spring Boot" bị tính là từ đệm. Xét ngữ cảnh hoặc chấp nhận sai số và nói rõ với người dùng.
2. **Tính khoảng lặng gồm cả lúc AI đang nói** — chỉ tính khoảng lặng trong lượt của người dùng.
3. **Thêm cột suy diễn tính cách** — đọc lại phần nguyên tắc đạo đức ở đầu nhóm.
4. **So sánh với khoảng tham chiếu bịa** — điền `reference_source` cho tử tế.
5. **Bắt đầu code mà chưa xác nhận `word_timings`** — rủi ro tắc lớn nhất của Sprint 4.
6. **Dùng `filler_count` thô để so sánh giữa các phiên** — dùng `filler_per_100_words`.

---

# Phụ lục A — Thứ tự triển khai

Bảng phải tạo theo thứ tự phụ thuộc. Sơ đồ rút gọn:

```
user_accounts
     ↓
cv_documents → cv_parse_results
     ↓
candidate_profiles → profile_{educations, skills, projects}
     ↓
rubrics → rubric_versions → rubric_criteria → rubric_criterion_levels
     ↓
interview_sessions
     ↓
session_questions → session_turns
     ↓                    ↓
session_scores      turn_transcripts, turn_audio_assets
     ↓                    ↓
score_evidences     turn_speech_metrics
session_reports
report_highlights
```

Gợi ý theo sprint:

| Sprint | Bảng cần có |
|---|---|
| Tuần 1 | `user_accounts`, `refresh_tokens`, `email_verification_tokens`, `password_reset_tokens`, `user_preferences`, `cv_documents`, `cv_parse_results`, `candidate_profiles`, 3 bảng `profile_*` |
| Tuần 2 | `interview_sessions`, `session_state_transitions`, `session_questions`, `session_turns` |
| Tuần 3 | `turn_audio_assets`, `turn_transcripts`, toàn bộ nhóm rubric, `session_scores`, `score_evidences`, `session_reports`, `report_highlights` |
| Tuần 4 | `session_voice_connections`, `filler_word_dictionary`, `turn_speech_metrics`, `turn_filler_occurrences`, `session_speech_metrics` |

Nhóm rubric nằm ở tuần 3 nhưng **nội dung rubric phải soạn tay xong từ tuần 1**. Đây là việc không cần code, ai trong team cũng làm được, nhưng thiếu nó thì tuần 3 tắc.

---

# Phụ lục B — Bảng tra nhanh

## Bảng nào bất biến (chỉ INSERT, không UPDATE)

| Bảng | Lý do |
|---|---|
| `cv_parse_results` | Bản gốc AI bóc tách, để đối chiếu khi debug |
| `session_state_transitions` | Nhật ký, sửa là mất giá trị điều tra |
| `session_questions` | Ảnh chụp câu hỏi đã hỏi |
| `turn_transcripts.raw_text` | Bản gốc STT (riêng `edited_text` thì sửa được) |
| `score_evidences` | Dẫn chứng đã trích |

## Bảng nào là dữ liệu dẫn xuất (tính lại được)

`turn_speech_metrics`, `session_speech_metrics`, `turn_filler_occurrences`, và cột `interview_sessions.overall_score`.

Mất thì tính lại từ dữ liệu gốc. Nhưng phải đảm bảo tính lại ra **đúng kết quả cũ** — nếu thuật toán đã đổi, đừng tính lại đè lên bản cũ.

## Ba cột `NOT NULL` có chủ đích sản phẩm

| Cột | Enforce điều gì |
|---|---|
| `session_scores.comment` | Không cho chấm mà không giải thích |
| `session_reports.disclaimer` | Luôn có ghi chú "đây là công cụ luyện tập" |
| `session_speech_metrics.reference_source` | Không cho so sánh với khoảng tham chiếu bịa |

Ba ràng buộc này biến yêu cầu sản phẩm thành ràng buộc kỹ thuật. Đây là cách rẻ nhất để tiêu chí nghiệm thu không bị bỏ quên khi code gấp.

## Ý nghĩa của từng kiểu `delete`

| Kiểu | Dùng khi | Ví dụ |
|---|---|---|
| `cascade` | Bảng con không có ý nghĩa độc lập | `candidate_profiles` → `profile_skills` |
| `restrict` | Bản ghi lịch sử đang trỏ vào, không được xóa | `candidate_profiles` ← `interview_sessions` |
| `set null` | Cho xóa nhưng giữ lịch sử, vì nội dung đã được chụp ảnh | `profile_projects` ← `session_questions` |

## Năm bảng phụ thuộc vào `session_turns`

`turn_transcripts`, `turn_audio_assets`, `score_evidences`, `turn_speech_metrics`, `turn_filler_occurrences`.

**Thiết kế sai `session_turns` thì phải sửa lại năm nhóm.** Đây là bảng cần cả team thống nhất kỹ nhất trước khi viết dòng code đầu tiên.
