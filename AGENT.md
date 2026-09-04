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
