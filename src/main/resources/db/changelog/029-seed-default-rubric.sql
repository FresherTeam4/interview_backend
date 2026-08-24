--liquibase formatted sql
--changeset team:029-seed-default-rubric labels:interview-core
--comment interview_sessions.rubric_version_id is NOT NULL, so at least one published rubric version must exist before any session can be created. This is that baseline.

INSERT INTO rubrics (code, name, description, created_at)
VALUES ('TECH_INTERVIEW_FRESHER',
        'Phỏng vấn kỹ thuật - Fresher',
        'Bộ tiêu chí mặc định cho phiên phỏng vấn thử vị trí fresher/junior.',
        CURRENT_TIMESTAMP(6));

INSERT INTO rubric_versions (rubric_id, version_no, is_current, published_at, change_note, created_at)
SELECT id, 1, TRUE, CURRENT_TIMESTAMP(6), 'Phiên bản khởi tạo.', CURRENT_TIMESTAMP(6)
FROM rubrics
WHERE code = 'TECH_INTERVIEW_FRESHER';

INSERT INTO rubric_criteria (rubric_version_id, code, name, description, weight, max_score, display_order)
SELECT rv.id, c.code, c.name, c.description, c.weight, 4, c.display_order
FROM rubric_versions rv
JOIN rubrics r ON r.id = rv.rubric_id
JOIN (
    SELECT 'TECHNICAL_DEPTH' AS code, 'Chiều sâu kỹ thuật' AS name,
           'Mức độ hiểu bản chất công nghệ đã dùng, không chỉ thuộc định nghĩa.' AS description,
           0.350 AS weight, 1 AS display_order
    UNION ALL SELECT 'PROBLEM_SOLVING', 'Tư duy giải quyết vấn đề',
           'Cách chia nhỏ vấn đề, xét trường hợp biên và cân nhắc đánh đổi.', 0.250, 2
    UNION ALL SELECT 'COMMUNICATION', 'Giao tiếp và diễn đạt',
           'Trình bày có cấu trúc, đúng trọng tâm câu hỏi, dùng thuật ngữ chính xác.', 0.250, 3
    UNION ALL SELECT 'CV_EVIDENCE', 'Bằng chứng từ CV và dự án',
           'Khả năng dẫn chứng cụ thể từ dự án đã ghi trong CV.', 0.150, 4
) c
WHERE r.code = 'TECH_INTERVIEW_FRESHER' AND rv.version_no = 1;

INSERT INTO rubric_criterion_levels (criterion_id, level_no, label, descriptor, score_value)
SELECT rc.id, lv.level_no, lv.label, lv.descriptor, lv.score_value
FROM rubric_criteria rc
JOIN rubric_versions rv ON rv.id = rc.rubric_version_id
JOIN rubrics r ON r.id = rv.rubric_id
JOIN (
    SELECT 'TECHNICAL_DEPTH' AS criterion_code, 1 AS level_no, 'Chưa đạt' AS label,
           'Trả lời sai khái niệm cốt lõi hoặc không trả lời được.' AS descriptor,
           1.00 AS score_value
    UNION ALL SELECT 'TECHNICAL_DEPTH', 2, 'Cơ bản',
           'Nêu đúng định nghĩa nhưng chưa giải thích được cách hoạt động.', 2.00
    UNION ALL SELECT 'TECHNICAL_DEPTH', 3, 'Tốt',
           'Giải thích đúng cơ chế và nêu được ưu nhược điểm.', 3.00
    UNION ALL SELECT 'TECHNICAL_DEPTH', 4, 'Xuất sắc',
           'Giải thích sâu, so sánh giải pháp thay thế và nêu rõ điều kiện áp dụng.', 4.00
    UNION ALL SELECT 'PROBLEM_SOLVING', 1, 'Chưa đạt',
           'Không đưa ra được hướng tiếp cận nào.', 1.00
    UNION ALL SELECT 'PROBLEM_SOLVING', 2, 'Cơ bản',
           'Có hướng tiếp cận nhưng bỏ qua ràng buộc và trường hợp biên.', 2.00
    UNION ALL SELECT 'PROBLEM_SOLVING', 3, 'Tốt',
           'Chia nhỏ vấn đề hợp lý, xét được các trường hợp biên chính.', 3.00
    UNION ALL SELECT 'PROBLEM_SOLVING', 4, 'Xuất sắc',
           'Đề xuất nhiều phương án, đánh giá đánh đổi và chọn phương án có lý do rõ ràng.', 4.00
    UNION ALL SELECT 'COMMUNICATION', 1, 'Chưa đạt',
           'Diễn đạt rời rạc, người nghe không nắm được ý.', 1.00
    UNION ALL SELECT 'COMMUNICATION', 2, 'Cơ bản',
           'Truyền đạt được ý chính nhưng lan man hoặc thiếu cấu trúc.', 2.00
    UNION ALL SELECT 'COMMUNICATION', 3, 'Tốt',
           'Trình bày có cấu trúc, đi đúng câu hỏi, dùng thuật ngữ chính xác.', 3.00
    UNION ALL SELECT 'COMMUNICATION', 4, 'Xuất sắc',
           'Trình bày ngắn gọn, rõ cấu trúc và chủ động xác nhận lại khi câu hỏi chưa rõ.', 4.00
    UNION ALL SELECT 'CV_EVIDENCE', 1, 'Chưa đạt',
           'Không dẫn được ví dụ nào từ dự án đã ghi trong CV.', 1.00
    UNION ALL SELECT 'CV_EVIDENCE', 2, 'Cơ bản',
           'Có nhắc tới dự án nhưng chỉ mô tả chung, không rõ phần việc của mình.', 2.00
    UNION ALL SELECT 'CV_EVIDENCE', 3, 'Tốt',
           'Nêu rõ vai trò, công việc cụ thể và kết quả của mình trong dự án.', 3.00
    UNION ALL SELECT 'CV_EVIDENCE', 4, 'Xuất sắc',
           'Dẫn chứng cụ thể kèm số liệu hoặc quyết định kỹ thuật mình trực tiếp đưa ra.', 4.00
) lv ON lv.criterion_code = rc.code
WHERE r.code = 'TECH_INTERVIEW_FRESHER' AND rv.version_no = 1;

--rollback DELETE FROM rubrics WHERE code = 'TECH_INTERVIEW_FRESHER';
