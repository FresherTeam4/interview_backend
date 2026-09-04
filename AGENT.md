# AI Agent Guidelines & Project Conventions

Tài liệu này chứa các quy tắc và quy ước phát triển cho các AI agent làm việc trên dự án này trong các phiên hiện tại và tiếp theo.

---

## 1. Repository & SQL/JPQL Query Conventions

Mọi query viết trong repository (thông qua `@Query`, native query hoặc JPQL) phải tuân thủ nghiêm ngặt các quy tắc sau:

### 1.1. Viết hoa toàn bộ từ khóa SQL / JPQL (UPPERCASE Keywords)
Tất cả các keyword của SQL/JPQL **bắt buộc phải viết hoa (UPPERCASE)**. Tuyệt đối không viết thường hoặc hỗn hợp (lowercase/camelCase) cho các từ khóa SQL.

Các từ khóa phổ biến:
- DML: `SELECT`, `INSERT`, `INTO`, `VALUES`, `UPDATE`, `SET`, `DELETE`
- Mệnh đề & Điều kiện: `FROM`, `WHERE`, `AND`, `OR`, `NOT`, `IN`, `LIKE`, `BETWEEN`, `EXISTS`
- Null check: `IS NULL`, `IS NOT NULL`
- Joins: `JOIN`, `INNER JOIN`, `LEFT JOIN`, `RIGHT JOIN`, `FULL JOIN`, `ON`
- Sắp xếp & Nhóm: `ORDER BY`, `GROUP BY`, `HAVING`, `ASC`, `DESC`
- Khác: `AS`, `DISTINCT`, `LIMIT`, `OFFSET`, `CASE`, `WHEN`, `THEN`, `ELSE`, `END`

### 1.2. Entity và Thuộc tính giữ nguyên định dạng Java (camelCase / PascalCase)
- Tên Entity giữ nguyên theo tên class Java (PascalCase): ví dụ `RefreshToken`, `ProfileEducation`, `UserAccount`.
- Tên trường / thuộc tính giữ nguyên theo định dạng Java entity (camelCase): ví dụ `t.tokenHash`, `e.profile.id`, `t.revokedAt`.
- Tham số đặt tên theo cú pháp Spring Data JPA (`:paramName` kết hợp `@Param("paramName")`).

### 1.3. Spring Data Repository Practices
- Ưu tiên sử dụng Query Methods suy luận theo quy ước của Spring Data JPA (`findBy...`, `existsBy...`, `countBy...`) nếu logic đơn giản.
- Khi cần `@Query` cho các thao tác cập nhật/xóa, luôn gắn kèm `@Modifying` (và cân nhắc `flushAutomatically = true` / `clearAutomatically = true` nếu cần đồng bộ persistence context).

---

## 2. Code Commenting Conventions

- **Định dạng comment**: Ưu tiên sử dụng comment ngắn gọn một dòng dưới dạng `// ...`. Không viết Javadoc (`/** ... */`) rườm rà cho các method/class nội bộ trừ khi thật sự cần thiết.
- **Tính chọn lọc & súc tích**:
  - Chỉ comment ở những đoạn logic thật sự quan trọng, các xử lý đặc biệt hoặc lý do tại sao phải dùng cấu hình/tham số cụ thể (ví dụ: `// sắp xếp theo vị trí để đọc đúng thứ tự các cột`).
  - Tuyệt đối không comment vào những đoạn code hiển nhiên (self-explanatory).
  - Không viết comment quá dài dòng, lan man hoặc comment quá thường xuyên trên từng dòng code.

---

## 3. Exception Handling Conventions

Dự án áp dụng mô hình xử lý lỗi tập trung thông qua `DomainException` và `GlobalExceptionHandler`:

### 3.1. Quy tắc khai báo mã lỗi (`ErrorCode`)
- Khi có một lỗi mới cần xử lý, **bắt buộc phải khai báo mã lỗi trong enum [`ErrorCode`](file:///D:/Work/experiences/FresherFPT/TechTrain/TeamProject/my-interview/src/main/java/com/baseProject/myBaseProject/exception/ErrorCode.java)**.
- Mỗi enum value trong `ErrorCode` phải liên kết với:
  - `HttpStatus` phù hợp (ví dụ: `HttpStatus.BAD_REQUEST`, `HttpStatus.UNAUTHORIZED`, `HttpStatus.SERVICE_UNAVAILABLE`, `HttpStatus.UNPROCESSABLE_CONTENT`...). *Lưu ý: Với HTTP 422 dùng `HttpStatus.UNPROCESSABLE_CONTENT` (tránh dùng `UNPROCESSABLE_ENTITY` đã bị deprecated từ Spring Framework 7.0)*.
  - Message mặc định (định nghĩa hằng số trong [`Message.java`](file:///D:/Work/experiences/FresherFPT/TechTrain/TeamProject/my-interview/src/main/java/com/baseProject/myBaseProject/constant/Message.java) để tái sử dụng).

### 3.2. Quy tắc ném ngoại lệ (Throwing Exceptions)
- **Không tạo tràn lan các class subclass riêng lẻ** (các subclass kiểu cũ như `DuplicateEmailException`, `ResourceNotFoundException`... đã được xóa bỏ để chuẩn hóa).
- Sử dụng trực tiếp `new DomainException(...)` với mã `ErrorCode` tương ứng:
  ```java
  throw new DomainException(ErrorCode.INVALID_CREDENTIALS);
  throw new DomainException(ErrorCode.CV_PARSE_FAILED, "PDF file contains no renderable pages");
  throw new DomainException(ErrorCode.STORAGE_ERROR, "Failed to upload file", cause);
  ```
- Với exception chuyên biệt của từng module (như [`AiException`](file:///D:/Work/experiences/FresherFPT/TechTrain/TeamProject/my-interview/src/main/java/com/baseProject/myBaseProject/exception/AiException.java)), bắt buộc kế thừa `DomainException` và có các constructor nhận `ErrorCode` tương tự:
  ```java
  throw new AiException(ErrorCode.AI_TIMEOUT, cause);
  ```

### 3.3. Nguyên tắc sử dụng `try-catch` tại Service
- Vì đã có [`GlobalExceptionHandler`](file:///D:/Work/experiences/FresherFPT/TechTrain/TeamProject/my-interview/src/main/java/com/baseProject/myBaseProject/exception/GlobalExceptionHandler.java) bắt tự động `DomainException` và `Exception.class`, các method trong Service nên viết logic phẳng, để ngoại lệ tự nhiên lan truyền.
- **Không** bọc `try-catch` tại Service chỉ để bắt rồi re-throw (`catch (DomainException ex) { throw ex; }`).
- **Chỉ** sử dụng `try-catch` khi làm nhiệm vụ **dịch ngoại lệ (Exception Translation)**: Bắt checked exception (như `IOException`) hoặc ngoại lệ kỹ thuật của thư viện bên ngoài (Spring AI, RestClient, DB Driver...) để chuyển đổi thành `DomainException` / `AiException` mang đúng `ErrorCode`.
