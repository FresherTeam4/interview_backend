package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * Một payload {@code PUT} chứa hai kỹ năng trùng tên.
 *
 * <p>Bắt ở tầng service vì collation {@code utf8mb4_unicode_ci} của cột coi "Java" và "java"
 * là một: không chặn trước thì {@code uq_profile_skills_profile_name} chặn, và người dùng
 * nhận một thông báo constraint thay vì biết mình gõ trùng chữ nào.
 */
public class DuplicateSkillNameException extends DomainException {

    public DuplicateSkillNameException(String skillName) {
        super(ErrorCode.DUPLICATE_SKILL_NAME, HttpStatus.CONFLICT,
                Message.DUPLICATE_SKILL_NAME.formatted(skillName));
    }
}
