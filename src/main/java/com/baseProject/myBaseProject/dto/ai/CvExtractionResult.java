package com.baseProject.myBaseProject.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;
import java.util.List;

// kết quả bóc tách dữ liệu từ CV
public record CvExtractionResult(
        @JsonPropertyDescription("Họ và tên của ứng viên")
        String fullName,

        @JsonPropertyDescription("Email liên lạc của ứng viên")
        String email,

        @JsonPropertyDescription("Số điện thoại liên lạc")
        String phoneNumber,

        @JsonPropertyDescription("Tiêu đề hồ sơ nghề nghiệp, ví dụ: 'Backend Java Developer'")
        String headline,

        @JsonPropertyDescription("Tổng số năm kinh nghiệm ước tính (ví dụ: 1.5, 2.0, 3.0)")
        BigDecimal yearsExperience,

        @JsonPropertyDescription("Vị trí mong muốn ứng tuyển, ví dụ: 'Java Backend Engineer'")
        String targetPosition,

        @JsonPropertyDescription("Cấp bậc kinh nghiệm: INTERN, FRESHER, JUNIOR, MIDDLE, SENIOR, LEAD")
        String seniorityLevel,

        @JsonPropertyDescription("Tóm tắt bản thân hoặc mục tiêu nghề nghiệp")
        String summary,

        @JsonPropertyDescription("Danh sách kỹ năng bóc tách từ CV")
        List<SkillItem> skills,

        @JsonPropertyDescription("Danh sách dự án hoặc kinh nghiệm làm việc thực tế")
        List<ProjectItem> projects,

        @JsonPropertyDescription("Quá trình học vấn và bằng cấp")
        List<EducationItem> educations,

        String rawJson,
        String modelName,
        Long durationMs,
        Long totalTokens
) {
    public record SkillItem(
            @JsonPropertyDescription("Tên chuẩn hóa của kỹ năng, ví dụ: 'Java', 'Spring Boot', 'React'")
            String name,

            @JsonPropertyDescription("Phân loại: LANGUAGE, FRAMEWORK, DATABASE, TOOL, CLOUD, SOFT")
            String category
    ) {}

    public record ProjectItem(
            @JsonPropertyDescription("Tên dự án hoặc tên công ty/tổ chức từng làm việc")
            String name,

            @JsonPropertyDescription("Vai trò đảm nhiệm, ví dụ: 'Backend Developer', 'Team Leader'")
            String roleInProject,

            @JsonPropertyDescription("Danh sách công nghệ sử dụng, phân tách bởi dấu phẩy, ví dụ: 'Java, Spring Boot, MySQL, Docker'")
            String techStack,

            @JsonPropertyDescription("Mô tả chi tiết nhiệm vụ, kỹ thuật áp dụng và thành tựu (nguyên liệu cốt lõi để sinh câu hỏi phỏng vấn)")
            String description,

            @JsonPropertyDescription("Thời gian bắt đầu, định dạng 'YYYY-MM' hoặc 'YYYY-MM-DD'")
            String startDate,

            @JsonPropertyDescription("Thời gian kết thúc, định dạng 'YYYY-MM' hoặc 'YYYY-MM-DD', để null nếu vẫn đang làm việc")
            String endDate
    ) {}

    public record EducationItem(
            @JsonPropertyDescription("Tên trường đại học/cao đẳng hoặc tổ chức giáo dục")
            String school,

            @JsonPropertyDescription("Bằng cấp, ví dụ: 'Cử nhân', 'Kỹ sư', 'Thạc sĩ'")
            String degree,

            @JsonPropertyDescription("Chuyên ngành đào tạo, ví dụ: 'Công nghệ thông tin', 'Khoa học máy tính'")
            String fieldOfStudy,

            @JsonPropertyDescription("Năm bắt đầu (ví dụ: 2019)")
            Short startYear,

            @JsonPropertyDescription("Năm tốt nghiệp hoặc dự kiến tốt nghiệp (ví dụ: 2023)")
            Short endYear
    ) {}

    public CvExtractionResult withMetadata(String rawJson, String modelName, Long durationMs, Long totalTokens) {
        return new CvExtractionResult(
                this.fullName,
                this.email,
                this.phoneNumber,
                this.headline,
                this.yearsExperience,
                this.targetPosition,
                this.seniorityLevel,
                this.summary,
                this.skills != null ? this.skills : List.of(),
                this.projects != null ? this.projects : List.of(),
                this.educations != null ? this.educations : List.of(),
                rawJson,
                modelName,
                durationMs,
                totalTokens
        );
    }
}
