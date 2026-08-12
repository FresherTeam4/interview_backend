--liquibase formatted sql
--changeset nxt:014-seed-sample-questions labels:sprint-1
-- Initial hand-curated Question Bank required by Task 10.3. Liquibase runs this changeset
-- once and tracks its checksum. Requires changesets 001 through 002.
-- Task 10.3 — tối thiểu 30 câu hỏi, phân loại theo Tech Stack/Level (đúng assumption
-- trong proposal: "ngân hàng câu hỏi ban đầu xây thủ công trước khi mở rộng bằng AI").

-- =========================================================
-- 1. BEHAVIORAL (không thuộc tech stack cụ thể) — 6 câu
-- =========================================================
INSERT INTO questions (content_vi, content_en, tech_stack_id, level, question_type, difficulty) VALUES
('Hãy giới thiệu về bản thân và lý do bạn muốn ứng tuyển vị trí này.',
 'Tell me about yourself and why you want to apply for this position.',
 NULL, 'FRESHER', 'BEHAVIORAL', 'EASY'),
('Kể về một lần bạn gặp xung đột với đồng nghiệp và cách bạn giải quyết.',
 'Describe a time you had a conflict with a colleague and how you resolved it.',
 NULL, 'JUNIOR', 'BEHAVIORAL', 'EASY'),
('Hãy kể về một dự án bạn đã dẫn dắt và kết quả đạt được.',
 'Tell me about a project you led and the outcome.',
 NULL, 'MID', 'BEHAVIORAL', 'MEDIUM'),
('Bạn xử lý thế nào khi một thành viên trong team liên tục trễ deadline?',
 'How do you handle a team member who consistently misses deadlines?',
 NULL, 'SENIOR', 'BEHAVIORAL', 'MEDIUM'),
('Điểm mạnh và điểm yếu lớn nhất của bạn là gì?',
 'What are your greatest strengths and weaknesses?',
 NULL, 'JUNIOR', 'BEHAVIORAL', 'EASY'),
('Kể về một lần bạn thất bại trong công việc và bài học rút ra.',
 'Describe a time you failed at work and what you learned from it.',
 NULL, 'MID', 'BEHAVIORAL', 'MEDIUM');

-- =========================================================
-- 2. FRONTEND — 4 câu
-- =========================================================
INSERT INTO questions (content_vi, content_en, tech_stack_id, level, question_type, difficulty)
SELECT 'Sự khác biệt giữa let, const và var trong JavaScript là gì?',
       'What is the difference between let, const, and var in JavaScript?',
       id, 'FRESHER', 'TECHNICAL', 'EASY' FROM tech_stacks WHERE code = 'FRONTEND'
UNION ALL
SELECT 'Giải thích Virtual DOM trong React hoạt động như thế nào.',
       'Explain how the Virtual DOM works in React.',
       id, 'JUNIOR', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'FRONTEND'
UNION ALL
SELECT 'So sánh CSS Grid và Flexbox, khi nào nên dùng cái nào?',
       'Compare CSS Grid and Flexbox — when should each be used?',
       id, 'MID', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'FRONTEND'
UNION ALL
SELECT 'Làm thế nào để tối ưu hiệu năng của một ứng dụng React quy mô lớn?',
       'How would you optimize the performance of a large-scale React application?',
       id, 'SENIOR', 'TECHNICAL', 'HARD' FROM tech_stacks WHERE code = 'FRONTEND';

-- =========================================================
-- 3. BACKEND — 5 câu
-- =========================================================
INSERT INTO questions (content_vi, content_en, tech_stack_id, level, question_type, difficulty)
SELECT 'Sự khác nhau giữa HTTP GET và POST là gì?',
       'What is the difference between HTTP GET and POST?',
       id, 'FRESHER', 'TECHNICAL', 'EASY' FROM tech_stacks WHERE code = 'BACKEND'
UNION ALL
SELECT 'Giải thích khái niệm RESTful API và các nguyên tắc thiết kế cơ bản.',
       'Explain the concept of a RESTful API and its basic design principles.',
       id, 'JUNIOR', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'BACKEND'
UNION ALL
SELECT 'Sự khác biệt giữa SQL và NoSQL, khi nào nên chọn loại nào?',
       'What is the difference between SQL and NoSQL, and when would you choose each?',
       id, 'MID', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'BACKEND'
UNION ALL
SELECT 'Giải thích cơ chế transaction và tính chất ACID trong cơ sở dữ liệu quan hệ.',
       'Explain the transaction mechanism and ACID properties in relational databases.',
       id, 'MID', 'TECHNICAL', 'HARD' FROM tech_stacks WHERE code = 'BACKEND'
UNION ALL
SELECT 'Thiết kế một hệ thống rate limiting cho API có lượng truy cập lớn.',
       'Design a rate-limiting system for a high-traffic API.',
       id, 'SENIOR', 'TECHNICAL', 'HARD' FROM tech_stacks WHERE code = 'BACKEND';

-- =========================================================
-- 4. MOBILE — 3 câu
-- =========================================================
INSERT INTO questions (content_vi, content_en, tech_stack_id, level, question_type, difficulty)
SELECT 'Sự khác nhau giữa phát triển native và cross-platform là gì?',
       'What is the difference between native and cross-platform mobile development?',
       id, 'JUNIOR', 'TECHNICAL', 'EASY' FROM tech_stacks WHERE code = 'MOBILE'
UNION ALL
SELECT 'Giải thích vòng đời (lifecycle) của một Activity trong Android.',
       'Explain the lifecycle of an Activity in Android.',
       id, 'MID', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'MOBILE'
UNION ALL
SELECT 'Làm thế nào để tối ưu bộ nhớ và pin cho ứng dụng di động?',
       'How would you optimize memory and battery usage for a mobile app?',
       id, 'SENIOR', 'TECHNICAL', 'HARD' FROM tech_stacks WHERE code = 'MOBILE';

-- =========================================================
-- 5. DEVOPS — 4 câu
-- =========================================================
INSERT INTO questions (content_vi, content_en, tech_stack_id, level, question_type, difficulty)
SELECT 'Docker container khác Virtual Machine như thế nào?',
       'How does a Docker container differ from a Virtual Machine?',
       id, 'JUNIOR', 'TECHNICAL', 'EASY' FROM tech_stacks WHERE code = 'DEVOPS'
UNION ALL
SELECT 'Giải thích quy trình CI/CD và lợi ích của nó.',
       'Explain the CI/CD pipeline and its benefits.',
       id, 'MID', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'DEVOPS'
UNION ALL
SELECT 'Thiết kế chiến lược scale hệ thống khi lượng truy cập tăng đột biến.',
       'Design a scaling strategy for a system facing a sudden traffic spike.',
       id, 'SENIOR', 'TECHNICAL', 'HARD' FROM tech_stacks WHERE code = 'DEVOPS'
UNION ALL
SELECT 'Sự khác biệt giữa Blue-Green Deployment và Canary Deployment là gì?',
       'What is the difference between Blue-Green Deployment and Canary Deployment?',
       id, 'SENIOR', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'DEVOPS';

-- =========================================================
-- 6. DATA & AI — 4 câu
-- =========================================================
INSERT INTO questions (content_vi, content_en, tech_stack_id, level, question_type, difficulty)
SELECT 'Sự khác biệt giữa Supervised Learning và Unsupervised Learning là gì?',
       'What is the difference between supervised and unsupervised learning?',
       id, 'JUNIOR', 'TECHNICAL', 'EASY' FROM tech_stacks WHERE code = 'DATA_AI'
UNION ALL
SELECT 'Giải thích hiện tượng overfitting trong Machine Learning và cách khắc phục.',
       'Explain overfitting in Machine Learning and how to address it.',
       id, 'MID', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'DATA_AI'
UNION ALL
SELECT 'Thiết kế một data pipeline xử lý dữ liệu real-time cho hệ thống gợi ý.',
       'Design a real-time data pipeline for a recommendation system.',
       id, 'SENIOR', 'TECHNICAL', 'HARD' FROM tech_stacks WHERE code = 'DATA_AI'
UNION ALL
SELECT 'Prompt Engineering là gì và tại sao nó quan trọng khi làm việc với LLM?',
       'What is Prompt Engineering and why does it matter when working with LLMs?',
       id, 'MID', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'DATA_AI';

-- =========================================================
-- 7. QA — 3 câu
-- =========================================================
INSERT INTO questions (content_vi, content_en, tech_stack_id, level, question_type, difficulty)
SELECT 'Sự khác nhau giữa kiểm thử thủ công và kiểm thử tự động là gì?',
       'What is the difference between manual and automated testing?',
       id, 'JUNIOR', 'TECHNICAL', 'EASY' FROM tech_stacks WHERE code = 'QA'
UNION ALL
SELECT 'Giải thích các loại kiểm thử: Unit test, Integration test, và E2E test.',
       'Explain the types of testing: Unit test, Integration test, and E2E test.',
       id, 'MID', 'TECHNICAL', 'MEDIUM' FROM tech_stacks WHERE code = 'QA'
UNION ALL
SELECT 'Làm thế nào để xây dựng chiến lược kiểm thử cho một hệ thống microservices?',
       'How would you build a testing strategy for a microservices system?',
       id, 'SENIOR', 'TECHNICAL', 'HARD' FROM tech_stacks WHERE code = 'QA';

-- =========================================================
-- 8. CASE STUDY (system design tổng hợp, không gắn 1 tech stack) — 3 câu
-- =========================================================
INSERT INTO questions (content_vi, content_en, tech_stack_id, level, question_type, difficulty) VALUES
('Bạn được giao thiết kế hệ thống đặt vé xem phim, hãy mô tả các thành phần chính.',
 'You are asked to design a movie ticket booking system — describe the main components.',
 NULL, 'MID', 'CASE_STUDY', 'MEDIUM'),
('Thiết kế một hệ thống rút gọn URL (URL shortener) có khả năng mở rộng.',
 'Design a scalable URL shortener system.',
 NULL, 'SENIOR', 'CASE_STUDY', 'HARD'),
('Giả sử API của bạn đột nhiên chậm bất thường vào giờ cao điểm, bạn sẽ debug như thế nào?',
 'Suppose your API suddenly becomes unusually slow during peak hours — how would you debug it?',
 NULL, 'MID', 'CASE_STUDY', 'MEDIUM');

-- Tổng cộng: 6 + 4 + 5 + 3 + 4 + 4 + 3 + 3 = 32 câu (vượt yêu cầu tối thiểu 30 của task 10.3)
