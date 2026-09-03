Khi mới nhìn vào tính năng **Mock Interview**, chúng ta rất dễ nghĩ rằng nó chỉ đơn giản là:
> *User bấm bắt đầu ➔ Hệ thống gọi AI sinh câu hỏi ➔ User gõ câu trả lời ➔ Hệ thống gửi câu trả lời lên AI xem có cần hỏi thêm (follow-up) không ➔ Hết buổi thì chấm điểm.*

Nếu chỉ làm theo kiểu đồ án hoặc prototype (gọi AI đồng bộ ngay trong Controller), toàn bộ phần này thực sự chỉ cần **2 - 3 file** và khoảng **300 dòng code**.

Nhưng trong codebase này, **Interview Engine được thiết kế theo chuẩn Enterprise / Production-grade** (tương tự kiến trúc xử lý thanh toán của ngân hàng hoặc hệ thống đặt vé). Khi đó, bài toán không còn là *"gọi API của AI như thế nào"*, mà là: **Làm sao để hệ thống chạy ổn định khi mạng chập chờn, user thao tác liên tục, và AI trả lời thất thường.**

Dưới đây là lời giải thích chi tiết tại sao nó lại có nhiều file và code đến như vậy.

---

### 1. Code đang phải xử lý những gì trong Interview Engine?

Interview Engine thực chất đang gánh **6 hệ thống con chạy ngầm**:

#### ① Hệ thống chống nghẽn Connection Database (Non-blocking Leased Claim Pattern)
* **Vấn đề**: Gọi Gemini sinh cả bộ câu hỏi có thể mất **5 – 15 giây**. Nếu bạn mở một `@Transactional` trong Database rồi đứng chờ Gemini trả lời, Database Connection Pool (HikariCP) sẽ bị chiếm giữ suốt 15 giây đó. Chỉ cần 20 người cùng phỏng vấn, toàn bộ server sẽ sập vì hết connection pool.
* **Code đã xử lý**:
  - `ScriptGenerationPreparationReader`: Mở transaction đọc nhanh dữ liệu rồi **đóng kết nối ngay**.
  - `InterviewQuestionGenerator`: Gọi Gemini hoàn toàn **bên ngoài Database Transaction**.
  - `ScriptGenerationCommitter`: Khi Gemini trả lời xong, mới mở transaction ghi kết quả vào DB.
  - `SessionProcessingClaimService`: Cơ chế "thuê quyền xử lý" (lease token). Nếu worker đang xử lý mà bị crash, `InterviewWorkflowRecoveryJob` sẽ tự động tìm các session bị "bỏ rơi" để chạy tiếp.

#### ② Máy trạng thái nghiêm ngặt (Pessimistic Lock & State Machine)
* **Vấn đề**: User có thể bấm đúp chuột (double click), F5 trang web, mở 2 tab cùng lúc, hoặc mất mạng rồi submit lại.
* **Code đã xử lý** (`lifecycle`):
  - Kiểm soát luồng trạng thái nghiêm ngặt: `CREATED` ➔ `SCRIPT_GENERATING` ➔ `READY` ➔ `IN_PROGRESS` (từng turn) ➔ `PAUSED` ⇄ `RESUMED` ➔ `SCORING` / `FAILED`.
  - Cơ chế **Idempotency Key** và **Client Turn ID**: Khi user gửi lại câu trả lời cũ do lag mạng, hệ thống nhận biết được ngay và không tạo ra câu hỏi trùng lặp.
  - Sử dụng khóa bi quan (`SELECT ... FOR UPDATE`) và kiểm tra version (`expectedVersion`) để ngăn chặn việc ghi đè trạng thái.

#### ③ Cơ chế đóng băng dữ liệu (Snapshotting)
* **Vấn đề**: User tạo phỏng vấn dựa trên CV và JD hiện tại. Nhưng ngày mai, user vào trang Profile sửa lại toàn bộ kinh nghiệm, hoặc xóa một Project trong CV.
* **Code đã xử lý** (`snapshot`):
  - Khi tạo phỏng vấn, toàn bộ thông tin Profile, Project, Skill, JD và cả phiên bản Rubric (tiêu chí chấm điểm) đều được chụp ảnh lại (`SessionContextSnapshot`).
  - Toàn bộ buổi phỏng vấn diễn ra dựa trên "ảnh chụp" bất biến đó, độc lập hoàn toàn với việc người dùng chỉnh sửa Profile sau này.

#### ④ Kiểm định AI & Chống lặp câu hỏi (Diversity & Hallucination Guardrails)
* **Vấn đề**:
  - AI thường xuyên bị "ảo giác" (hallucination): tự bịa ra một Project ID không hề có trong CV của ứng viên.
  - AI thường lặp lại câu hỏi: Nếu phỏng vấn Java 2 lần liên tiếp, AI rất hay hỏi lại đúng những câu quen thuộc (như *"OOP là gì?", "HashMap hoạt động thế nào?"*).
* **Code đã xử lý** (`generation`, `QuestionScriptValidator`, `QuestionDiversityPolicy`):
  - Băm nhỏ câu hỏi thành chữ ký SHA-256 (`QuestionSignatureFactory`).
  - So sánh với lịch sử 3 buổi phỏng vấn gần nhất: **Bắt buộc ít nhất 70% câu hỏi phải có chữ ký khác biệt** và không được trùng nội dung đã chuẩn hóa.
  - Nếu AI vi phạm độ đa dạng, hệ thống **tự động loại bỏ và yêu cầu AI sinh lại lần 2** mà user không hề hay biết.
  - Kiểm tra tính xác thực: Mọi câu hỏi gắn nhãn `CV_PROJECT` hay `CV_SKILL` đều phải khớp chính xác với `id` có trong snapshot.

#### ⑤ Quản lý ngân sách câu hỏi và Context Bounded (`turn`)
* **Vấn đề**: Nếu người dùng trả lời mập mờ, AI có thể liên tục hỏi follow-up vô tận, làm tốn token và làm buổi phỏng vấn kéo dài quá lâu. Nếu nhét toàn bộ lịch sử trò chuyện vào prompt, độ dài context sẽ bùng nổ.
* **Code đã xử lý** (`turn`):
  - Giới hạn cứng ngân sách: Tối đa 2 câu hỏi follow-up cho 1 câu hỏi chính, tối đa 6 follow-up cho toàn bộ buổi phỏng vấn.
  - **Server-side Override**: Nếu AI đòi hỏi follow-up nhưng hệ thống thấy đã hết "ngân sách", hệ thống sẽ tự động ghi đè quyết định của AI thành `NEXT_QUESTION`.
  - Cắt tỉa context: Chỉ gửi các lượt trao đổi của câu hỏi hiện tại cho AI, không gửi toàn bộ lịch sử của cả buổi phỏng vấn.

#### ⑥ Xử lý lỗi nhà cung cấp mạng/AI (Fault Tolerance)
* **Vấn đề**: Gemini có thể trả về HTTP 429 (Rate limit), 408 (Timeout), 503 (Server error), hoặc trả về chuỗi Markdown có bọc ` ```json ` làm vỡ trình parse JSON.
* **Code đã xử lý** (`ai/gemini`):
  - Bóc tách code fence tự động (`stripCodeFences`).
  - Phân loại lỗi chính xác (`GeminiFailureClassifier`): Lỗi nào là tạm thời (để worker tự lên lịch retry), lỗi nào là vĩnh viễn (sai API key, prompt không hợp lệ).
  - Chuẩn hóa JSON Schema sang OpenAPI Schema tương thích riêng với Spring AI và Google GenAI.

---

### 2. Tại sao đã tách CV và JD Extractor rồi mà Interview Engine vẫn dài?

CV Extractor và JD Extractor chỉ giải quyết khâu: **"File PDF / Text thô ➔ JSON có cấu trúc"** (đây là tầng chuẩn bị dữ liệu đầu vào).

Còn Interview Engine là **trái tim điều hành toàn bộ một phiên phỏng vấn tương tác nhiều vòng (multi-turn interactive flow)**:
- Nó không chỉ đọc text một lần, mà nó quản lý **trạng thái sống** của người dùng theo thời gian thực.
- Nó phải xử lý việc người dùng tạm dừng (pause), tiếp tục (resume), trả lời từng câu, sinh câu hỏi phụ, chuyển câu hỏi tiếp theo, và kiểm soát worker chạy bất đồng bộ (async).

Do đó, lượng code lớn trong Interview Engine **không nằm ở phần trích xuất thông tin**, mà nằm ở **State Management, Concurrency, Async Coordination và AI Guardrails**.

---

### 3. Có chỗ nào bị "thừa" không?

Câu trả lời phụ thuộc vào **mục tiêu của dự án**:

| Thành phần | Nếu là bài toán Prototype / Hackathon | Nếu là bài toán Production / Doanh nghiệp thực tế |
| :--- | :--- | :--- |
| **Worker / Leased Claim** (`workflow`) | **Có thể coi là thừa**: Có thể gọi AI trực tiếp ngay trong Controller. | **Rất cần thiết**: Ngăn chặn sập connection pool của Database và treo request của người dùng khi AI phản hồi chậm. |
| **Diversity Policy & SHA-256** (`generation`) | **Có thể coi là thừa**: Cứ để AI sinh tự nhiên, lặp câu hỏi cũng được. | **Rất cần thiết**: Tránh trải nghiệm người dùng tệ hại khi phỏng vấn lại mà cứ gặp các câu hỏi y hệt buổi trước. |
| **Snapshot Profile & Rubric** (`snapshot`) | **Có thể coi là thừa**: Cứ lấy trực tiếp từ bảng Profile. | **Rất cần thiết**: Đảm bảo tính toàn vẹn dữ liệu khi người dùng chỉnh sửa hồ sơ giữa buổi phỏng vấn. |
| **Server-side Budget Override** (`turn`) | **Có thể coi là thừa**: Tin tưởng hoàn toàn vào câu trả lời của AI. | **Rất cần thiết**: Kiểm soát chi phí gọi AI (Token cost) và chặn việc AI bị kẹt vào một vòng lặp hỏi dồn dập. |

**Về mặt kỹ thuật**: 
Hiện tại trong package `interview` **không có dòng code nào là dead code (code rác/thừa không dùng đến)**. Từng class đều phục vụ một trách nhiệm duy nhất (Single Responsibility Principle) theo đúng kiến trúc phân lớp sạch sẽ.

### Tóm lại
Lý do code dài và nhiều file không phải vì tính năng phỏng vấn bị viết cồng kềnh, mà là vì **engine này đã được cài đặt sẵn tất cả các cơ chế bảo vệ (resilience, anti-cheating, anti-hallucination, concurrency safety, async worker)** để có thể chịu tải và chạy ổn định trên môi trường thực tế, thay vì chỉ là một ứng dụng wrapper API Gemini đơn giản.
