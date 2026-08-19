# Liquibase: quyền sở hữu và thứ tự thực thi

Master changelog chạy authentication trước các bảng core và taxonomy:

1. `001-create-user-accounts.sql` — Thái Văn Trường
2. `002-create-refresh-tokens.sql` — Thái Văn Trường
3. `010-create-tech-stacks.sql` — Nguyễn Xuân Tùng
4. `011-create-questions.sql` — Nguyễn Xuân Tùng
5. `012-create-interview-sessions.sql` — Nguyễn Xuân Tùng
6. `013-create-reports.sql` — Nguyễn Xuân Tùng
7. `014-seed-sample-questions.sql` — Nguyễn Xuân Tùng
8. `015-create-question-taxonomy.sql` — Nguyễn Xuân Tùng
9. `016-migrate-question-taxonomy.sql` — Nguyễn Xuân Tùng
10. `017-use-extensible-session-status.sql` — Nguyễn Xuân Tùng

Changeset 015 tạo danh mục `technologies` và hai bảng nối nhiều-nhiều.
Changeset 016 sao chép mọi `questions.tech_stack_id` cũ sang
`question_tech_stacks`, gắn công nghệ cho một số câu seed đã xác định rõ, rồi bỏ
cột phân loại đơn cũ. Vì vậy dữ liệu phân loại Tech Stack hiện có không bị mất.

Changeset 017 chỉ chuyển `interview_sessions.status` từ MySQL `ENUM` sang
`VARCHAR(30)`. State Machine ở US-19 có thể thêm trạng thái ứng dụng mà không
phải thay đổi kiểu cột sau mỗi lần mở rộng. Java vẫn kiểm soát giá trị bằng
`InterviewSessionStatus`.


## Quy tắc vận hành

- Liquibase là nguồn duy nhất quản lý cấu trúc database.
- `spring.jpa.hibernate.ddl-auto=validate` chỉ kiểm tra entity khớp schema; không
  đổi lại thành `update`.
- Không sửa nội dung changeset đã chạy trên database dùng chung. Nếu cần thay đổi,
  tạo changeset mới.
- Khi chạy lần đầu, nên dùng database sạch. Với database cũ không do Liquibase
  quản lý, team cần thống nhất baseline thay vì chạy các lệnh `CREATE TABLE` chồng lên.
- Trước khi merge/deploy, chạy ứng dụng trên một database test sạch, sau đó chạy
  `docs/database/verify-schema.sql`.

Round Module được trình bày đầy đủ trong ERD và `round-module-design.md` để team
nhìn được kiến trúc tổng thể. Các bảng Round vẫn là thiết kế dự kiến, chưa được
master changelog tạo trong Sprint 1.

Các endpoint Google OAuth nằm ngoài thay đổi taxonomy này. Taxonomy Question Bank
không phụ thuộc việc Google OAuth đã hoàn thành hay chưa; nó chỉ phụ thuộc cơ chế
xác thực cung cấp principal có role `EVENT_ADMIN` khi gọi API admin.
