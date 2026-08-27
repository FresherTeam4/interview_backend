--liquibase formatted sql
--changeset tvt:027-seed-interview-rubric-v1 labels:interview-engine-mvp
-- MySQL 8.0.16+ / Liquibase
-- Human-authored Vietnamese descriptors for the immutable fresher technical interview rubric v1.

INSERT INTO rubrics (code, name, description, current_version_id, created_at)
VALUES (
    'TECH_INTERVIEW_FRESHER',
    'Phỏng vấn kỹ thuật Fresher',
    'Rubric đánh giá câu trả lời trong phỏng vấn kỹ thuật dành cho ứng viên Fresher.',
    NULL,
    '2026-08-26 00:00:00.000000'
);

SET @rubric_id = LAST_INSERT_ID();

INSERT INTO rubric_versions (rubric_id, version_no, change_note, published_at, created_at)
VALUES (
    @rubric_id,
    1,
    'Phiên bản rubric đầu tiên cho Interview Engine MVP.',
    '2026-08-26 00:00:00.000000',
    '2026-08-26 00:00:00.000000'
);

SET @rubric_version_id = LAST_INSERT_ID();

INSERT INTO rubric_criteria (
    rubric_version_id,
    code,
    name,
    description,
    weight,
    max_score,
    display_order
)
VALUES
    (
        @rubric_version_id,
        'TECHNICAL_ACCURACY',
        'Độ chính xác kỹ thuật',
        'Đánh giá tính đúng đắn của khái niệm, thuật ngữ, cơ chế và kết luận kỹ thuật.',
        0.300,
        4,
        1
    ),
    (
        @rubric_version_id,
        'TECHNICAL_DEPTH',
        'Chiều sâu kỹ thuật',
        'Đánh giá khả năng giải thích cơ chế, nguyên nhân, giới hạn và đánh đổi kỹ thuật.',
        0.250,
        4,
        2
    ),
    (
        @rubric_version_id,
        'PROBLEM_SOLVING',
        'Tư duy giải quyết vấn đề',
        'Đánh giá cách phân tích vấn đề, xây dựng hướng tiếp cận và kiểm chứng giải pháp.',
        0.200,
        4,
        3
    ),
    (
        @rubric_version_id,
        'RELEVANCE_AND_STRUCTURE',
        'Mức độ liên quan và cấu trúc',
        'Đánh giá khả năng trả lời đúng trọng tâm và tổ chức nội dung theo trình tự hợp lý.',
        0.150,
        4,
        4
    ),
    (
        @rubric_version_id,
        'COMMUNICATION_CLARITY',
        'Độ rõ ràng khi trình bày',
        'Đánh giá mức dễ hiểu, nhất quán và chính xác trong cách diễn đạt.',
        0.100,
        4,
        5
    );

SET @technical_accuracy_id = (
    SELECT id FROM rubric_criteria
    WHERE rubric_version_id = @rubric_version_id AND code = 'TECHNICAL_ACCURACY'
);
SET @technical_depth_id = (
    SELECT id FROM rubric_criteria
    WHERE rubric_version_id = @rubric_version_id AND code = 'TECHNICAL_DEPTH'
);
SET @problem_solving_id = (
    SELECT id FROM rubric_criteria
    WHERE rubric_version_id = @rubric_version_id AND code = 'PROBLEM_SOLVING'
);
SET @relevance_structure_id = (
    SELECT id FROM rubric_criteria
    WHERE rubric_version_id = @rubric_version_id AND code = 'RELEVANCE_AND_STRUCTURE'
);
SET @communication_clarity_id = (
    SELECT id FROM rubric_criteria
    WHERE rubric_version_id = @rubric_version_id AND code = 'COMMUNICATION_CLARITY'
);

INSERT INTO rubric_criterion_levels (criterion_id, level_no, label, descriptor, score_value)
VALUES
    (
        @technical_accuracy_id,
        1,
        'Không đạt',
        'Câu trả lời chứa lỗi kiến thức cốt lõi hoặc dẫn tới kết luận kỹ thuật sai.',
        1.00
    ),
    (
        @technical_accuracy_id,
        2,
        'Cơ bản',
        'Nêu đúng phần kiến thức chính nhưng còn một số điểm thiếu chính xác hoặc nhầm thuật ngữ.',
        2.00
    ),
    (
        @technical_accuracy_id,
        3,
        'Khá',
        'Hầu hết nội dung chính xác; sai sót nhỏ không làm thay đổi bản chất câu trả lời.',
        3.00
    ),
    (
        @technical_accuracy_id,
        4,
        'Tốt',
        'Nội dung chính xác, dùng đúng thuật ngữ và nêu đúng điều kiện áp dụng của kết luận.',
        4.00
    ),
    (
        @technical_depth_id,
        1,
        'Không đạt',
        'Chỉ nhắc lại từ khóa hoặc định nghĩa rời rạc, không giải thích được cơ chế liên quan.',
        1.00
    ),
    (
        @technical_depth_id,
        2,
        'Cơ bản',
        'Giải thích được khái niệm và luồng cơ bản nhưng chưa làm rõ nguyên nhân hoặc giới hạn.',
        2.00
    ),
    (
        @technical_depth_id,
        3,
        'Khá',
        'Giải thích được cơ chế, liên hệ các thành phần và đưa ra ví dụ kỹ thuật phù hợp.',
        3.00
    ),
    (
        @technical_depth_id,
        4,
        'Tốt',
        'Phân tích rõ cơ chế bên trong, giới hạn, đánh đổi và tình huống nên hoặc không nên áp dụng.',
        4.00
    ),
    (
        @problem_solving_id,
        1,
        'Không đạt',
        'Không xác định được vấn đề chính hoặc đề xuất giải pháp không thể thực hiện hay kiểm chứng.',
        1.00
    ),
    (
        @problem_solving_id,
        2,
        'Cơ bản',
        'Đưa ra được một hướng xử lý nhưng các bước còn thiếu, dựa nhiều vào phỏng đoán chưa kiểm tra.',
        2.00
    ),
    (
        @problem_solving_id,
        3,
        'Khá',
        'Phân tích có trình tự, đề xuất giải pháp khả thi và nêu được cách kiểm tra kết quả.',
        3.00
    ),
    (
        @problem_solving_id,
        4,
        'Tốt',
        'Chia nhỏ vấn đề hợp lý, kiểm chứng giả thuyết, xét trường hợp biên và cân nhắc tối ưu hóa.',
        4.00
    ),
    (
        @relevance_structure_id,
        1,
        'Không đạt',
        'Phần lớn nội dung lệch câu hỏi, rời rạc hoặc không có kết luận có thể sử dụng.',
        1.00
    ),
    (
        @relevance_structure_id,
        2,
        'Cơ bản',
        'Có trả lời ý chính nhưng lẫn nhiều nội dung phụ và trình tự lập luận chưa rõ.',
        2.00
    ),
    (
        @relevance_structure_id,
        3,
        'Khá',
        'Trả lời đúng trọng tâm, các ý được sắp xếp hợp lý và có kết luận rõ ràng.',
        3.00
    ),
    (
        @relevance_structure_id,
        4,
        'Tốt',
        'Ưu tiên đúng thông tin quan trọng, trình bày súc tích và liên kết chặt chẽ từ lập luận đến kết luận.',
        4.00
    ),
    (
        @communication_clarity_id,
        1,
        'Không đạt',
        'Cách diễn đạt khó hiểu, mâu thuẫn hoặc dùng thuật ngữ khiến người nghe hiểu sai.',
        1.00
    ),
    (
        @communication_clarity_id,
        2,
        'Cơ bản',
        'Có thể hiểu ý chính nhưng còn câu mơ hồ, lặp ý hoặc thuật ngữ chưa được giải thích.',
        2.00
    ),
    (
        @communication_clarity_id,
        3,
        'Khá',
        'Diễn đạt rõ ràng, dùng thuật ngữ nhất quán và giải thích vừa đủ để theo dõi.',
        3.00
    ),
    (
        @communication_clarity_id,
        4,
        'Tốt',
        'Trình bày chính xác, tự nhiên, điều chỉnh mức chi tiết phù hợp và làm rõ điểm dễ nhầm.',
        4.00
    );

UPDATE rubrics
SET current_version_id = @rubric_version_id
WHERE id = @rubric_id;

--rollback UPDATE rubrics SET current_version_id = NULL WHERE code = 'TECH_INTERVIEW_FRESHER';
--rollback DELETE FROM rubrics WHERE code = 'TECH_INTERVIEW_FRESHER';
